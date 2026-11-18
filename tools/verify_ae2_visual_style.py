"""Verify native dependency references and protected art against an explicit Git baseline."""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import subprocess
from zipfile import ZipFile
from PIL import Image
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def original(commit, path):
    return subprocess.check_output(['git', 'show', f'{commit}:{path}'], cwd=ROOT)


def verify_terminal(gen, baseline):
    prefix = f'versions/neoforge-{gen}/src/main/resources/assets/ae2lpt/'
    model_path = prefix+'models/block/loop_factory_network_terminal.json'
    model = json.loads((ROOT/model_path).read_bytes())
    old = json.loads(original(baseline, model_path))
    assert len(model['elements']) == len(old['elements']) == 1
    for key in ('from', 'to'):
        assert model['elements'][0][key] == old['elements'][0][key]
    expected = dict(north='front', south='back', east='side', west='side', up='case', down='case')
    for face, texture in expected.items():
        details = model['elements'][0]['faces'][face]
        assert details['texture'] == '#'+texture
        assert details['uv'] == old['elements'][0]['faces'][face]['uv']
    for name in ('loop_factory_terminal', 'loop_factory_terminal_top', 'loop_factory_terminal_case'):
        path = prefix+f'textures/block/{name}.png'
        assert (ROOT/path).read_bytes() == original(baseline, path), path
    for path in ('blockstates/loop_factory_network_terminal.json', 'models/item/loop_factory_network_terminal.json'):
        assert (ROOT/(prefix+path)).read_bytes() == original(baseline, prefix+path), path
    for name in ('loop_factory_terminal_side', 'loop_factory_terminal_back'):
        art = Image.open(ROOT/(prefix+f'textures/block/{name}.png')).convert('RGBA')
        assert art.size == (16, 16)
        assert set(art.getchannel('A').getdata()) == {255}
    project = json.loads((ROOT/f'design/loop_factory/{gen}/loop_factory_network_terminal.bbmodel').read_bytes())
    for tex in project['textures']:
        path = ROOT/(prefix+'textures/'+tex['folder']+'/'+tex['name'])
        assert base64.b64decode(tex['source'].split(',', 1)[1]) == path.read_bytes(), path
    for face, texture in expected.items():
        index = project['elements'][0]['faces'][face]['texture']
        assert project['textures'][index]['name'] == model['textures'][texture].split('/')[-1]+'.png'
    return dict(terminalFacesAndEditableSources=True, frontLidAndSavedStatesUnchanged=True,
                **verify_handheld(gen, baseline))


def verify_handheld(gen, baseline):
    prefix = f'versions/neoforge-{gen}/src/main/resources/assets/ae2lpt/'
    name = 'handheld_loop_factory_encoder'
    path = ROOT/(prefix+f'textures/item/{name}.png')
    with Image.open(path) as image:
        assert image.size == (16, 16)
        alpha = image.convert('RGBA').getchannel('A')
        assert set(alpha.getdata()) == {0, 255}
        contours = [alpha.crop((0, y, 16, y+1)).getbbox() for y in (1, 2, 3, 12, 13, 14)]
        assert [(b[0], b[2]) for b in contours] == [(5, 11), (4, 12), (3, 13), (3, 13), (4, 12), (5, 11)]
    project = json.loads((ROOT/f'design/loop_factory/{gen}/{name}.bbmodel').read_bytes())
    texture = project['textures'][0]
    assert (texture['width'], texture['height']) == (16, 16)
    assert (texture['uv_width'], texture['uv_height']) == (16, 16)
    assert texture['namespace'] == 'ae2lpt'
    assert base64.b64decode(texture['source'].split(',', 1)[1]) == path.read_bytes()
    for model in (f'models/item/{name}.json', f'items/{name}.json'):
        if (ROOT/(prefix+model)).exists():
            assert (ROOT/(prefix+model)).read_bytes() == original(baseline, prefix+model), model
    return dict(handheldTransparent16px=True, handheldBeveledSilhouette=True)


def verify(gen, version, baseline):
    from audit_runtime_resources import referenced_texture_keys
    prefix = f'versions/neoforge-{gen}/src/main/resources/assets/ae2lpt/'
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
        if path == prefix+'textures/item/handheld_loop_factory_encoder.png':
            continue  # Explicitly redesigned; checked with the terminal's source and alpha contract below.
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
            used = referenced_texture_keys(ae, assets.parents[1], 'ae2lpt', target,
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
                terminal=verify_terminal(gen, baseline),
                tests={key: sum(int(t.attrib.get(key, 0)) for t in tests) for key in ('tests', 'failures', 'errors', 'skipped')})


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline', required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--terminal-only', action='store_true', help='Check only the redesigned terminal and handheld assets')
    args = parser.parse_args()
    results = [(dict(generation=gen, **verify_terminal(gen, args.baseline)) if args.terminal_only
                else verify(gen, ver, args.baseline))
               for gen, ver in [('1.21.1', '19.2.17'), ('26.1.2', '26.1.10-beta')]]
    generated = []
    for gen in ('1.21.1', '26.1.2'):
        prefix = f'versions/neoforge-{gen}/src/main/resources/assets/ae2lpt/'
        names = [name+suffix for name in ('recipe_ring_solver_terminal', 'supercomputing_crafting_optimizer_interface',
                 'crafting_ripper') for suffix in ('', '_connected')]
        names += ['loop_factory_terminal', 'loop_factory_terminal_top', 'loop_factory_terminal_side',
                  'loop_factory_terminal_back', 'loop_factory_cable']
        generated.extend(prefix+f'textures/block/{name}.png' for name in names)
        generated.append(prefix+'textures/item/handheld_loop_factory_encoder.png')
        generated.append(prefix+'models/block/loop_factory_network_terminal.json')
        generated.extend(f'design/loop_factory/{gen}/{name}.bbmodel' for name in (
            'loop_factory_network_terminal', 'loop_factory_interface_cable', 'loop_factory_pattern_encoding_panel',
            'handheld_loop_factory_encoder'))
    hashes = lambda: {p: hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in generated}
    before = hashes()
    subprocess.run(['python', str(ROOT/'tools/align_ae2_visuals.py')]
                   + (['--only-terminal'] if args.terminal_only else []), cwd=ROOT, check=True)
    assert before == hashes(), 'generator is not idempotent'
    for asset in ('block/loop_factory_terminal_side', 'block/loop_factory_terminal_back', 'item/handheld_loop_factory_encoder'):
        files = [ROOT/f'versions/neoforge-{g}/src/main/resources/assets/ae2lpt/textures/{asset}.png'
                 for g in ('1.21.1', '26.1.2')]
        assert files[0].read_bytes() == files[1].read_bytes(), asset
    report = dict(baseline=args.baseline, results=results, generatorIdempotent=True, generatedFiles=len(generated),
                  terminalArtMatchesAcrossGenerations=True, terminalOnly=args.terminal_only)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
