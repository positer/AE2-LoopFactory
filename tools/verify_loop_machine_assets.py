"""Verify exact AE2 texture/model reuse; restore the condenser's unchanged outer frame.

python tools/verify_loop_machine_assets.py --ae2-jar <native.jar> --generation 1.21.1 [--restore-frame]
The restore operation rebuilds the native frame and the explicit grayscale core palette.
"""
import argparse
import io
import json
import zipfile
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('--ae2-jar', type=Path, required=True)
p.add_argument('--generation', choices=['1.21.1', '26.1.2'], required=True)
p.add_argument('--restore-frame', action='store_true')
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
assets = root / f'versions/neoforge-{a.generation}/src/main/resources/assets/ae2lightoptimizer'
with zipfile.ZipFile(a.ae2_jar) as z:
    native = Image.open(io.BytesIO(z.read('assets/ae2/textures/block/condenser.png'))).convert('RGBA')
    path = assets / 'textures/block/loop_condenser.png'
    current = Image.open(path).convert('RGBA')
    assert native.size == current.size == (16, 16)
    outer = [(x, y) for x in range(16) for y in range(16) if not (3 <= x <= 12 and 3 <= y <= 12)]
    # Exact palette, rather than a lossy whole-sprite color transform. Every outer pixel is native.
    core_gray = {(242,242,242):242, (232,232,234):232, (112,140,186):188,
                 (125,169,210):216, (65,63,84):71, (105,109,136):157,
                 (77,77,103):121, (135,143,165):196, (156,211,255):255,
                 (218,255,255):255, (145,93,205):170, (226,163,227):251,
                 (90,71,158):129}
    expected = native.copy()
    for x in range(3, 13):
        for y in range(3, 13):
            old = native.getpixel((x,y))
            gray = core_gray[old[:3]]
            expected.putpixel((x,y), (gray,gray,gray,old[3]))
    if a.restore_frame:
        expected.save(path)
        current = expected
    assert list(current.getdata()) == list(expected.getdata()), 'native frame/core palette differs'
    assert all(native.getpixel(xy) == current.getpixel(xy) for xy in outer), 'outer frame differs'
    for x in range(3, 13):
        for y in range(3, 13):
            old, new = native.getpixel((x,y)), current.getpixel((x,y))
            if old != new:
                assert new[0] == new[1] == new[2] and new[3] == old[3], 'core must be grayscale'
    for name in ['loop_ticker', 'loop_ticker_sides']:
        original_name = 'export_bus' + ('_sides' if name.endswith('_sides') else '')
        original = Image.open(io.BytesIO(z.read('assets/ae2/textures/part/'+original_name+'.png'))).convert('RGBA')
        changed = Image.open(assets / ('textures/block/'+name+'.png')).convert('RGBA')
        assert original.size == changed.size
        for old, new in zip(original.getdata(), changed.getdata()):
            if old != new:
                assert old[2] > old[0] and old[2] > old[1], 'a non-blue native pixel changed'
                assert new[1] > new[0] and new[1] > new[2] and old[3] == new[3], 'blue must become green'
    model = json.loads((assets/'models/block/loop_ticker.json').read_text(encoding='utf-8'))
    original_model = json.loads(z.read('assets/ae2/models/part/export_bus_base.json'))
    assert original_model['elements'] == model['elements'], 'world model geometry differs'
    item = json.loads((assets/'models/item/loop_ticker.json').read_text(encoding='utf-8'))
    original_item = json.loads(z.read('assets/ae2/models/item/export_bus.json'))
    assert item['elements'] == original_item['elements'] and item['parent'] == original_item['parent'], 'item model differs'
    for language in ['en_us', 'zh_cn']:
        names = json.loads((assets/('lang/'+language+'.json')).read_text(encoding='utf-8'))
        for machine in ['loop_ticker', 'loop_condenser']:
            assert names['block.ae2lightoptimizer.'+machine], 'machine name is missing'
    if a.generation == '26.1.2':
        for machine in ['loop_ticker', 'loop_condenser']:
            entry = json.loads((assets/('items/'+machine+'.json')).read_text(encoding='utf-8'))
            assert entry['model'] == {'type':'minecraft:model','model':'ae2lightoptimizer:item/'+machine}
    print(json.dumps({'generation':a.generation,'outerFramePixelsIdentical':len(outer),
                      'coreGrayscale':True,'tickerOnlyBlueToGreen':True,'nativeWorldGeometry':True,'nativeItemGeometry':True}))
