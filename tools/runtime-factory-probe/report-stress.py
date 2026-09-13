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
out = root / 'archive/2026-09-08-full-stress'
out.mkdir(parents=True, exist_ok=True)
summary = {'scope': 'Hidden native full inventory and feature regression with bounded stress; not universal compatibility',
           'generations': {}, 'missing': ['Advanced SFM RETAIN/EACH/WITH/WITHOUT/slots/round-robin/relative-side clauses',
           'Uninstalled addon capabilities and arbitrary third-party world handlers',
           'Actual long-duration chunk unload/rejoin and non-item destruction recovery',
           'Conservative overlap of two wildcard must selectors can overconstrain unrelated declarations',
           'Cross-declaration alias ceilings and greater-than-32-provider world paging pressure']}
restart_artifacts = json.loads((out / 'restart7-artifacts.json').read_text(encoding='utf-8'))
cards = []
for gen in ['1.21.1', '26.1.2']:
    run_name = f'final-stress7-{gen}'
    base = root / f'archive/2026-09-08-background-full-audit/{run_name}'
    audit = json.loads((base / 'ui-item-block-audit.json').read_text(encoding='utf-8'))
    assert audit['status'] == 'completed' and (base / 'normal-shutdown.txt').is_file()
    assert all(r['passed'] and r.get('windowVisible') == 0 for r in audit['results'])
    for name in ['factory-report.json', 'native-cpu-report.json']:
        assert json.loads((base / name).read_text(encoding='utf-8'))['status'] == 'passed'
    compat = json.loads((base / 'compatibility-audit.json').read_text(encoding='utf-8'))
    for name in ['stress-report.json', 'topology-report.json', 'terminal-report.json', 'induction-report.json', 'multi-tag-report.json']:
        assert json.loads((base / name).read_text(encoding='utf-8'))['status'] == 'passed', name
    assert compat['status'] == 'passed'
    restart = root / f'archive/2026-09-08-loop-factory-0.0.5/full-stress-restart7-{gen}-summary.json'
    assert json.loads(restart.read_text(encoding='utf-8-sig'))['status'] == 'passed'
    extras = root / f'archive/2026-09-08-loop-factory-0.0.5/full-stress-restart7-{gen}-resume/restart-extras.json'
    assert json.loads(extras.read_text(encoding='utf-8'))['status'] == 'passed'
    ui_base=base
    ui_rows={}
    counts = collections.Counter()
    for row in audit['results']:
        name = row['id']
        category = ('界面' if name.startswith('ui-') and name != 'ui-close' else
                    '物品模型' if name.startswith('model-') else
                    '实际方块/部件' if name.startswith(('block-', 'part-')) else
                    '供应器朝向' if name.startswith('arrows-') else '多机器标签' if name == 'multi-tag-machine-groups' else '准备与断言')
        counts[category] += 1
        if category == '准备与断言':
            continue
        image_base = ui_base if name in ui_rows else base
        image = image_base / 'screenshots' / f'{name}.png'
        assert image.is_file()
        rel = os.path.relpath(image, out).replace('\\', '/')
        cards.append(f'<article data-name="{html.escape(gen + " " + name + " " + category)}">'
                     f'<h3>{gen} · {html.escape(name)}</h3><p>{category} · 通过</p>'
                     f'<a href="{rel}"><img loading="lazy" src="{rel}" alt="{html.escape(name)}"></a></article>')
    guide_base=root/f'archive/2026-09-08-background-full-audit/guide9-{gen}'
    guide_audit=json.loads((guide_base/'ui-item-block-audit.json').read_text(encoding='utf-8'))
    assert guide_audit['status']=='completed' and (guide_base/'normal-shutdown.txt').is_file()
    assert all(r['passed'] and r.get('windowVisible')==0 for r in guide_audit['results'])
    for row in guide_audit['results']:
        if row['id'].startswith('ui-guide-language-'):continue
        screenshot=guide_base/'screenshots'/f"{row['id']}.png"
        assert screenshot.is_file()
        rel=os.path.relpath(screenshot,out).replace('\\','/')
        cards.append(f'<article data-name="{gen} {row["id"]}"><h3>{gen} · {row["id"]}</h3><p>Native guide</p><a href="{rel}"><img loading="lazy" src="{rel}"></a></article>')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for p in (root / f'versions/neoforge-{gen}/build/test-results/test').glob('TEST-*.xml'):
        e = ET.parse(p).getroot()
        for k in totals:
            totals[k] += int(e.attrib.get(k, 0))
    assert totals['failures'] == totals['errors'] == 0
    jar = root / f'versions/neoforge-{gen}/build/libs/ae2lf-neoforge-mc{gen}-0.0.5.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==restart_artifacts[gen]['sha256'], 'Packaged resources changed after restart artifact freeze'
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        current_classes={n:hashlib.sha256(z.read(n)).hexdigest() for n in names if n.endswith('.class')}
        assert current_classes == restart_artifacts[gen]['classes'], 'Runtime classes changed after restart evidence'
        for panel_model in ('loop_factory_panel_off.json', 'loop_factory_panel_on.json'):
            particle=json.loads(z.read('assets/ae2lightoptimizer/models/part/' + panel_model))['textures']
            assert particle.get('particle') == '#lightsMedium'

        assert not any('ae2lfprobe' in n or 'immortalstorage' in n.lower() or n.endswith('.jar') for n in names)
        for cls in ['FactoryEncoderItem', 'FactoryEncodingPanel', 'FactoryEncoderAction']:
            assert f'com/example/ae2lightoptimizer/factory/{cls}.class' in names
        en = json.loads(z.read('assets/ae2lightoptimizer/lang/en_us.json'))
        zh = json.loads(z.read('assets/ae2lightoptimizer/lang/zh_cn.json'))
        keys = {k for k in en if k.startswith('gui.ae2lightoptimizer.factory.')}
        assert keys and keys <= set(zh)
        assert all(not any('\u4e00' <= c <= '\u9fff' for c in en[k]) for k in keys)
    summary['generations'][gen] = {'evidenceDirectory': str(base.relative_to(root)), 'uiEvidenceDirectory': str(ui_base.relative_to(root)), 'uiRecaptureSteps': len(ui_rows), 'multiTag': json.loads((base/'multi-tag-report.json').read_text(encoding='utf-8')), 'guideEvidenceDirectory':str(guide_base.relative_to(root)), 'guideSteps':len(guide_audit['results']), 'steps': len(audit['results']), 'categories': dict(counts),
        'allPassed': True, 'allWindowsInvisible': True, 'tests': totals,
        'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'restartJarSha256': restart_artifacts[gen]['sha256'], 'restartClassesMatch': True, 'compatibility': compat, 'stress': json.loads((base / 'stress-report.json').read_text(encoding='utf-8'))}
summary['screenshots'] = len(cards)
(out / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
page = '''<!doctype html><meta charset="utf-8"><title>AE2LF 后台验收</title>
<style>body{font:16px system-ui;background:#171b23;color:#e5e8ee;margin:24px}input{padding:12px;width:420px;max-width:85vw}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(380px,1fr));gap:20px}article{background:#252b36;border-radius:8px;padding:12px}h3{font-size:14px;overflow-wrap:anywhere}img{width:100%;height:auto}p{color:#bbc5d5}</style>
<h1>AE2LF 后台实际测试与压力回归</h1><p>真实隐藏 Minecraft 客户端原始帧。物品模型卡是验收视图；编辑和转运通过真实菜单或数据包执行。高级 SFM 语法和未安装附属仍有覆盖缺口，详见 REPORT.md。</p>
<input placeholder="筛选版本、界面、部件或模型" oninput="document.querySelectorAll('article').forEach(e=>e.hidden=!e.dataset.name.includes(this.value))"><main>'''
(out / 'index.html').write_text(page + ''.join(cards) + '</main>', encoding='utf-8')
print(json.dumps({g: {'steps': v['steps'], 'tests': v['tests'], 'categories': v['categories']}
                  for g, v in summary['generations'].items()}, ensure_ascii=False, indent=2))
print(out / 'index.html')
