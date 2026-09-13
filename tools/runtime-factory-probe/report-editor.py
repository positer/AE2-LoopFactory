"""Collect fresh native frames without modifying screenshot pixels."""
from pathlib import Path
import collections
import hashlib
import html
import json
import os
import zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
out = root / 'archive/2026-09-08-factory-encoder-panel'
out.mkdir(parents=True, exist_ok=True)
summary = {'scope': 'Encoder/panel/localization acceptance; overall 0.0.5 incomplete',
           'generations': {}, 'missing': ['Complete SFM execution and scheduling',
           'Induction-card-specific FE behavior', 'Physical duplicate-service disconnection and topology recovery',
           'Terminal/destruction recovery', 'Uninstalled addon capabilities and arbitrary world handlers']}
cards = []
for gen in ['1.21.1', '26.1.2']:
    base = root / f'archive/2026-09-08-background-full-audit/localized-verified-{gen}'
    audit = json.loads((base / 'ui-item-block-audit.json').read_text(encoding='utf-8'))
    assert audit['status'] == 'completed' and (base / 'normal-shutdown.txt').is_file()
    assert all(r['passed'] and r.get('windowVisible') == 0 for r in audit['results'])
    for name in ['factory-report.json', 'native-cpu-report.json']:
        assert json.loads((base / name).read_text(encoding='utf-8'))['status'] == 'passed'
    compat = json.loads((base / 'compatibility-audit.json').read_text(encoding='utf-8'))
    panel_base = root / f'archive/2026-09-08-background-full-audit/panel-verified-{gen}'
    panel_audit = json.loads((panel_base / 'ui-item-block-audit.json').read_text(encoding='utf-8'))
    assert panel_audit['status'] == 'completed' and (panel_base / 'normal-shutdown.txt').is_file()
    assert len(panel_audit['results']) == 2
    assert all(r['passed'] and r['windowVisible'] == 0 for r in panel_audit['results'])
    counts = collections.Counter()
    for row in audit['results']:
        name = row['id']
        category = ('界面' if name.startswith('ui-') and name != 'ui-close' else
                    '物品模型' if name.startswith('model-') else
                    '实际方块/部件' if name.startswith(('block-', 'part-')) else
                    '供应器朝向' if name.startswith('arrows-') else '准备与断言')
        counts[category] += 1
        if category == '准备与断言':
            continue
        image_base = panel_base if name.startswith('part-factory-panel-') else base
        image = image_base / 'screenshots' / f'{name}.png'
        assert image.is_file()
        rel = os.path.relpath(image, out).replace('\\', '/')
        cards.append(f'<article data-name="{html.escape(gen + " " + name + " " + category)}">'
                     f'<h3>{gen} · {html.escape(name)}</h3><p>{category} · 通过</p>'
                     f'<a href="{rel}"><img loading="lazy" src="{rel}" alt="{html.escape(name)}"></a></article>')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for p in (root / f'versions/neoforge-{gen}/build/test-results/test').glob('TEST-*.xml'):
        e = ET.parse(p).getroot()
        for k in totals:
            totals[k] += int(e.attrib.get(k, 0))
    assert totals['failures'] == totals['errors'] == 0
    jar = root / f'versions/neoforge-{gen}/build/libs/ae2lf-neoforge-mc{gen}-0.0.5.jar'
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        assert not any('ae2lfprobe' in n or 'immortalstorage' in n.lower() or n.endswith('.jar') for n in names)
        for cls in ['FactoryEncoderItem', 'FactoryEncodingPanel', 'FactoryEncoderAction']:
            assert f'com/example/ae2lightoptimizer/factory/{cls}.class' in names
        en = json.loads(z.read('assets/ae2lightoptimizer/lang/en_us.json'))
        zh = json.loads(z.read('assets/ae2lightoptimizer/lang/zh_cn.json'))
        keys = {k for k in en if k.startswith('gui.ae2lightoptimizer.factory.')}
        assert keys and keys <= set(zh)
        assert all(not any('\u4e00' <= c <= '\u9fff' for c in en[k]) for k in keys)
    summary['generations'][gen] = {'steps': len(audit['results']), 'categories': dict(counts),
        'allPassed': True, 'allWindowsInvisible': True, 'tests': totals,
        'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'compatibility': compat}
summary['screenshots'] = len(cards)
(out / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
page = '''<!doctype html><meta charset="utf-8"><title>AE2LF 后台验收</title>
<style>body{font:16px system-ui;background:#171b23;color:#e5e8ee;margin:24px}input{padding:12px;width:420px;max-width:85vw}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(380px,1fr));gap:20px}article{background:#252b36;border-radius:8px;padding:12px}h3{font-size:14px;overflow-wrap:anywhere}img{width:100%;height:auto}p{color:#bbc5d5}</style>
<h1>AE2LF 界面与编码器后台验收</h1><p>真实隐藏 Minecraft 客户端原始帧。物品模型卡是验收视图；编辑和转运通过真实菜单或数据包执行。完整 0.0.5 尚有未完成项，详见 REPORT.md。</p>
<input placeholder="筛选版本、界面、部件或模型" oninput="document.querySelectorAll('article').forEach(e=>e.hidden=!e.dataset.name.includes(this.value))"><main>'''
(out / 'index.html').write_text(page + ''.join(cards) + '</main>', encoding='utf-8')
print(json.dumps({g: {'steps': v['steps'], 'tests': v['tests'], 'categories': v['categories']}
                  for g, v in summary['generations'].items()}, ensure_ascii=False, indent=2))
print(out / 'index.html')
