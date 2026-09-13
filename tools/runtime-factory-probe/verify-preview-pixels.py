"""Compare unmodified native screenshot regions from the three preview frames."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops

parser = argparse.ArgumentParser()
parser.add_argument('report_dir', type=Path)
args = parser.parse_args()
report = json.loads((args.report_dir / 'pattern-preview-report.json').read_text(encoding='utf-8-sig'))
assert report['status'] == 'passed', 'Preview behavior gate did not pass'
modes = report['modes']
images = {mode: Image.open(args.report_dir / 'screenshots' / f'factory-pattern-preview-{mode}.png').convert('RGB')
          for mode in ('normal', 'shift', 'cleared')}


def crop(mode, region):
    scale = modes[mode]['framebuffer']['guiScale']
    x, y = round(region['x'] * scale), round(region['y'] * scale)
    w, h = round(region['width'] * scale), round(region['height'] * scale)
    assert 0 <= x < x+w <= images[mode].width and 0 <= y < y+h <= images[mode].height
    return images[mode].crop((x, y, x+w, y+h))


def equal(left, right):
    return left.size == right.size and ImageChops.difference(left, right).getbbox() is None


rows = []
for mode in ('normal', 'shift', 'cleared'):
    reference_region = next(row['factory'] for row in modes[mode]['iconRegions'] if row['case'] == 'no-recipe')
    reference = crop(mode, reference_region)
    for row in modes[mode]['iconRegions']:
        native, factory = crop(mode, row['native']), crop(mode, row['factory'])
        same = equal(native, factory)
        wrapper_icon = equal(factory, reference)
        if mode == 'shift' and row['case'] != 'no-recipe':
            assert same, f"{mode}/{row['case']}: native and factory target pixels differ"
            assert not wrapper_icon, f"{mode}/{row['case']}: target still has the factory icon"
        elif mode in ('normal', 'cleared') or row['case'] == 'no-recipe':
            assert wrapper_icon, f"{mode}/{row['case']}: expected the original factory icon"
        rows.append(dict(mode=mode, case=row['case'], nativeFactoryPixelsEqual=same,
                         factoryPixelsEqualBlankFactory=wrapper_icon))

output = dict(status='passed', imageSource='Original native framebuffer screenshots',
              comparisons=rows, comparedRegions=len(rows))
(args.report_dir / 'pattern-preview-pixels.json').write_text(json.dumps(output, indent=2)+'\n', encoding='utf-8')
print(json.dumps(output, indent=2))
