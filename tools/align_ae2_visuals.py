"""Reproducible AE2-native housings for both adapters.

Reads the pinned runtime dependency, never a different generation's assets.
Only the outer three pixels of service faces change; their core art is retained.
"""
from pathlib import Path
from zipfile import ZipFile
from PIL import Image, ImageDraw
import base64
import io
import json
import uuid

ROOT = Path(__file__).resolve().parents[1]
MOD = 'ae2lightoptimizer'
FACES = ('down', 'up', 'north', 'south', 'west', 'east')


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + '\n', encoding='utf-8')


def cube(name, start, end, texture, uv=None):
    return dict(name=name, **{'from': start, 'to': end}, faces={
        f: dict(texture=texture, **({'uv': uv} if uv else {})) for f in FACES})


def native_uv(element, face):
    x0, y0, z0 = element['from']
    x1, y1, z1 = element['to']
    return {'down': [x0, 16-z1, x1, 16-z0], 'up': [x0, z0, x1, z1],
            'north': [16-x1, 16-y1, 16-x0, 16-y0], 'south': [x0, 16-y1, x1, 16-y0],
            'west': [z0, 16-y1, z1, 16-y0], 'east': [16-z1, 16-y1, 16-z0, 16-y0]}[face]


def blockbench(gen, name, model, resources, ae):
    """Export the exact production geometry, with embedded textures for offline editing."""
    textures, keys = [], {}
    for key, ref in model['textures'].items():
        if key == 'particle':
            continue
        ns, asset = ref.split(':')
        entry = f'assets/{ns}/textures/{asset}.png'
        raw = ae.read(entry) if ns == 'ae2' else (resources / entry).read_bytes()
        keys[key] = len(textures)
        textures.append(dict(id=str(keys[key]), name=asset.split('/')[-1]+'.png', namespace=ns,
                             folder=asset.rsplit('/', 1)[0], uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, ref)),
                             width=16, height=16, uv_width=16, uv_height=16,
                             source='data:image/png;base64,'+base64.b64encode(raw).decode()))
    elements = []
    for i, element in enumerate(model['elements']):
        uid = str(uuid.uuid5(uuid.NAMESPACE_URL, f'{gen}/{name}/{i}'))
        elements.append(dict(name=element.get('name', name), type='cube', uuid=uid,
                             **{'from': element['from'], 'to': element['to']},
                             faces={f: (dict(uv=element['faces'][f].get('uv', native_uv(element, f)),
                                             texture=keys[element['faces'][f]['texture'][1:]],
                                             tint=element['faces'][f].get('tintindex', -1))
                                        if f in element['faces'] else dict(uv=[0, 0, 0, 0], texture=None))
                                    for f in FACES}))
    write_json(ROOT/f'design/loop_factory/{gen}/{name}.bbmodel', dict(
        meta=dict(format_version='5.0', model_format='java_block', box_uv=False),
        name=name, resolution=dict(width=16, height=16), elements=elements,
        outliner=[e['uuid'] for e in elements], textures=textures))


def restore_cable(gen, resources, ae):
    # Exact low-contrast texture from the project's 0.0.5 release, not a newly recolored image.
    assets = resources/f'assets/{MOD}'
    original = ROOT/'design/loop_factory/references/loop_factory_cable_original.png'
    (assets/'textures/block/loop_factory_cable.png').write_bytes(original.read_bytes())
    model = json.loads((assets/'models/block/loop_factory_interface_cable.json').read_bytes())
    blockbench(gen, 'loop_factory_interface_cable', model, resources, ae)


def generate(gen, version, only_cable=False):
    resources = ROOT/f'versions/neoforge-{gen}/src/main/resources'
    assets = resources/f'assets/{MOD}'
    jar = next((ROOT/f'.gradle-user-home/{gen}/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/{version}').rglob(f'appliedenergistics2-{version}.jar'))
    with ZipFile(jar) as ae:
        if only_cable:
            restore_cable(gen, resources, ae)
            print(f'{gen}: restored original low-contrast full-cube cable')
            return
        shell = Image.open(io.BytesIO(ae.read('assets/ae2/textures/block/interface.png'))).convert('RGBA')
        # Native bevel, violet-grey steel and dark gasket; never change protected core pixels.
        for name in ('recipe_ring_solver_terminal', 'supercomputing_crafting_optimizer_interface', 'crafting_ripper'):
            for suffix in ('', '_connected'):
                path = assets/f'textures/block/{name}{suffix}.png'
                face = Image.open(path).convert('RGBA')
                for y in range(16):
                    for x in range(16):
                        if x < 3 or x > 12 or y < 3 or y > 12:
                            face.putpixel((x, y), shell.getpixel((x, y)))
                face.save(path)

        terminal = shell.copy()
        d = ImageDraw.Draw(terminal)
        d.rectangle((3, 3, 12, 12), fill='#151b21')
        for y, lengths in ((5, (3, 2)), (7, (2, 3)), (9, (4, 1))):
            a, b = lengths
            d.line((4, y, 3+a, y), fill='#62c6ef')
            d.line((5+a, y, 4+a+b, y), fill='#3187b8')
        terminal.save(assets/'textures/block/loop_factory_terminal.png')
        # Back/top now read as AE2 metal, instead of SFM's featureless black lid.
        top = shell.copy()
        for y in range(3, 13):
            for x in range(3, 13):
                r, g, b, a = top.getpixel((x, y))
                grey = round((r+g+b)/3 * .75)
                top.putpixel((x, y), (grey, grey, grey, a))
        top.save(assets/'textures/block/loop_factory_terminal_top.png')
        model = dict(parent='minecraft:block/block', textures={
            'front': f'{MOD}:block/loop_factory_terminal',
            'case': f'{MOD}:block/loop_factory_terminal_top',
            'particle': f'{MOD}:block/loop_factory_terminal_top'},
            elements=[cube('AE2 terminal housing', [0, 0, 0], [16, 16, 16], '#case', [0, 0, 16, 16])])
        model['elements'][0]['faces']['north']['texture'] = '#front'
        write_json(assets/'models/block/loop_factory_network_terminal.json', model)
        blockbench(gen, 'loop_factory_network_terminal', model, resources, ae)

        restore_cable(gen, resources, ae)
        # The editable panel must show the same 12px housing and 8px neck as the real part.
        # Production continues to reference AE2 directly, including dye and status rendering.
        panel = json.loads(ae.read('assets/ae2/models/part/display_base.json'))
        lights = json.loads((assets/'models/part/loop_factory_panel_on.json').read_text())
        panel['textures'].update(lights['textures'])
        panel['elements'].extend(lights['elements'])
        panel['textures'] = {k: v for k, v in panel['textures'].items() if not v.startswith('#')}
        blockbench(gen, 'loop_factory_pattern_encoding_panel', panel, resources, ae)
    print(f'{gen}: native housings, terminal and full-cube cable materials generated')


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--only-cable', action='store_true')
    args = parser.parse_args()
    for gen, version in (('1.21.1', '19.2.17'), ('26.1.2', '26.1.10-beta')):
        generate(gen, version, args.only_cable)
