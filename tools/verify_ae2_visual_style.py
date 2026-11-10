"""Verify native dependency references and protected art against an explicit Git baseline."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
from zipfile import ZipFile
from PIL import Image
import xml.etree.ElementTree as ET
from audit_runtime_resources import referenced_texture_keys

ROOT = Path(__file__).resolve().parents[1]


def original(commit, path):
    return subprocess.check_output(['git', 'show', f'{commit}:{path}'], cwd=ROOT)


def verify(gen, version, baseline):
    prefix = f'versions/neoforge-{gen}/src/main/resources/assets/ae2lightoptimizer/'
    assets = ROOT / prefix
    jar = next((ROOT / f'.gradle-user-home/{gen}/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/{version}').rglob(f'appliedenergistics2-{version}.jar'))
    for name in ('recipe_ring_solver_terminal', 'supercomputing_crafting_optimizer_interface', 'crafting_ripper'):
        for suffix in ('', '_connected'):
            path = prefix + f'textures/block/{name}{suffix}.png'
            before = Image.open(io.BytesIO(original(baseline, path))).convert('RGBA')
            after = Image.open(ROOT / path).convert('RGBA')
            assert after.size == (16, 16)
            assert before.crop((3, 3, 13, 13)).tobytes() == after.crop((3, 3, 13, 13)).tobytes(), path
    for path in ('models/block/loop_factory_interface_cable.json', 'models/item/loop_factory_interface_cable.json',
                 'blockstates/loop_factory_interface_cable.json'):
        assert json.loads((assets/path).read_bytes()) == json.loads(original(baseline, prefix+path)), path
    protected = subprocess.check_output(['git', 'ls-files', prefix+'textures/item', prefix+'textures/part',
                                         prefix+'models/part'], cwd=ROOT, text=True).splitlines()
    for path in protected:
        before, after = original(baseline, path), (ROOT/path).read_bytes()
        if path.endswith('.png'):
            assert before == after, path
        elif path.endswith('.json'):
            assert json.loads(before) == json.loads(after), path
        else:
            assert before.replace(b'\r\n', b'\n') == after.replace(b'\r\n', b'\n'), path
    references = set()
    with ZipFile(jar) as ae:
        for path in (assets/'models').rglob('*.json'):
            data = json.loads(path.read_bytes())
            target = path.relative_to(assets/'models').with_suffix('').as_posix()
            used = referenced_texture_keys(ae, assets.parents[1], 'ae2lightoptimizer', target,
                                           not target.startswith('item/'))
            # Vanilla's generated-item and cube templates own layer0/all/side keys.
            if data.get('parent', '').startswith('minecraft:'):
                used.update(data.get('textures', {}))
            for key, ref in data.get('textures', {}).items():
                if key not in used:
                    continue
                if ref.startswith('#'):
                    continue
                ns, name = ref.split(':')
                if ns == 'minecraft':
                    continue
                target = f'assets/{ns}/textures/{name}.png'
                png = ae.read(target) if ns == 'ae2' else (assets.parents[1]/target).read_bytes()
                image = Image.open(io.BytesIO(png))
                assert image.width > 0 and image.height > 0, target
                references.add(target)
            if data.get('parent', '').startswith('ae2:'):
                assert 'assets/ae2/models/'+data['parent'].split(':')[1]+'.json' in ae.namelist(), path
        panel = json.loads((ROOT/f'design/loop_factory/{gen}/loop_factory_pattern_encoding_panel.bbmodel').read_bytes())
        native = json.loads(ae.read('assets/ae2/models/part/display_base.json'))
        assert len(panel['elements']) == len(native['elements']) + 3
        for editable, source in zip(panel['elements'], native['elements']):
            assert editable['from'] == source['from'] and editable['to'] == source['to']
            for face, details in editable['faces'].items():
                assert (details['texture'] is not None) == (face in source['faces'])
        for element in panel['elements']:
            for face in element['faces'].values():
                assert face['texture'] is None or (isinstance(face['texture'], int)
                                                 and 0 <= face['texture'] < len(panel['textures']))
    tests = [ET.parse(path).getroot() for path in (ROOT/f'versions/neoforge-{gen}/build/test-results/test').glob('TEST-*.xml')]
    return dict(generation=gen, nativeReference=dict(jar=jar.name, sha256=hashlib.sha256(jar.read_bytes()).hexdigest()),
                resolvedTextures=len(references), protectedFiles=len(protected), serviceCoresUnchanged=True,
                fullCubeCableGeometryUnchanged=True, editablePanelMatchesNativeGeometry=True,
                tests={key: sum(int(t.attrib.get(key, 0)) for t in tests) for key in ('tests', 'failures', 'errors', 'skipped')})


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline', required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    results = [verify(gen, ver, args.baseline) for gen, ver in [('1.21.1', '19.2.17'), ('26.1.2', '26.1.10-beta')]]
    generated = []
    for gen in ('1.21.1', '26.1.2'):
        prefix = f'versions/neoforge-{gen}/src/main/resources/assets/ae2lightoptimizer/'
        names = [name+suffix for name in ('recipe_ring_solver_terminal', 'supercomputing_crafting_optimizer_interface',
                 'crafting_ripper') for suffix in ('', '_connected')]
        names += ['loop_factory_terminal', 'loop_factory_terminal_top', 'loop_factory_cable']
        generated.extend(prefix+f'textures/block/{name}.png' for name in names)
        generated.append(prefix+'models/block/loop_factory_network_terminal.json')
        generated.extend(f'design/loop_factory/{gen}/{name}.bbmodel' for name in (
            'loop_factory_network_terminal', 'loop_factory_interface_cable', 'loop_factory_pattern_encoding_panel'))
    hashes = lambda: {p: hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in generated}
    before = hashes()
    subprocess.run(['python', str(ROOT/'tools/align_ae2_visuals.py')], cwd=ROOT, check=True)
    assert before == hashes(), 'generator is not idempotent'
    report = dict(baseline=args.baseline, results=results, generatorIdempotent=True, generatedFiles=len(generated))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
