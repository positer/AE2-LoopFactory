"""Summarize native evidence without changing any screenshot pixels."""
from pathlib import Path
import collections
import hashlib
import html
import json
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[2]
out = root / 'archive/2026-09-08-background-full-audit'
summary = {'scope': 'Current implemented content; full 0.0.5 acceptance incomplete', 'generations': {},
           'missing': ['Handheld encoder gameplay and registration', 'Dyeable encoding panel and upload',
                       'Complete SFM execution', 'Induction-card CPU FE allocation',
                       'Uninstalled addon resources and arbitrary world capability handlers',
                       'Duplicate-service disconnection and topology recovery']}
cards = []
for gen in ['1.21.1', '26.1.2']:
    base = out / f'final-{gen}'
    audit = json.loads((base / 'ui-item-block-audit.json').read_text(encoding='utf-8'))
    compatibility = json.loads((base / 'compatibility-audit.json').read_text(encoding='utf-8'))
    for report in ['factory-report.json', 'native-cpu-report.json']:
        assert json.loads((base / report).read_text(encoding='utf-8'))['status'] == 'passed'
    assert audit['status'] == 'completed'
    assert (base / 'normal-shutdown.txt').exists()
    log_bytes = (base / 'client.log').read_bytes()
    log_text = log_bytes.decode('utf-16' if log_bytes.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8', errors='replace')
    assert 'BUILD SUCCESSFUL' in log_text
    rows = {r['id']: (r, base) for r in audit['results']}
    latest = out / f'provider-final-{gen}'
    if latest.exists():
        refresh = json.loads((latest / 'ui-item-block-audit.json').read_text(encoding='utf-8'))
        assert refresh['status'] == 'completed' and (latest / 'normal-shutdown.txt').exists()
        rows.update({r['id']: (r, latest) for r in refresh['results']})
    counts = collections.Counter()
    for name, (row, source) in rows.items():
        category = ('UI' if name.startswith('ui-') and name != 'ui-close' else
                    'Item model' if name.startswith('model-') else
                    'Placed block' if name.startswith('block-') else
                    'Provider direction' if name.startswith('arrows-') else 'Preparation/check')
        counts[category] += 1
        if category == 'Preparation/check':
            continue
        image = source / 'screenshots' / f'{name}.png'
        assert image.is_file(), image
        rel = image.relative_to(out).as_posix()
        cards.append(f'<article data-gen="{gen}" data-category="{category}" data-name="{html.escape(name)}">'
                     f'<h3>{html.escape(name)}</h3><p>{gen} · {category} · {"PASS" if row["passed"] else "FAIL"}</p>'
                     f'<a href="{rel}"><img loading="lazy" src="{rel}" alt="{html.escape(name)}"></a></article>')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for p in (root / f'versions/neoforge-{gen}/build/test-results/test').glob('TEST-*.xml'):
        e = ET.parse(p).getroot()
        for k in totals:
            totals[k] += int(e.attrib.get(k, 0))
    jar = root / f'versions/neoforge-{gen}/build/libs/ae2lf-neoforge-mc{gen}-0.0.5.jar'
    with zipfile.ZipFile(jar) as z:
        foreign = [n for n in z.namelist() if 'ae2lfprobe' in n or 'immortalstorage' in n.lower() or n.endswith('.jar')]
        assert not foreign
        assert 'assets/ae2lightoptimizer/textures/block/loop_factory_provider_back.png' in z.namelist()
    summary['generations'][gen] = {
        'steps': len(rows), 'failed': [n for n, (r, _) in rows.items() if not r['passed']],
        'allRecordedWindowsInvisible': all(r.get('windowVisible') == 0 for r, _ in rows.values()),
        'counts': dict(counts), 'registeredKeyTypes': compatibility['registeredKeyTypes'],
        'compatibility': compatibility, 'unitTests': totals, 'nativeCpu': 'passed', 'normalShutdown': True,
        'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'foreignOrNestedEntries': foreign}
(out / 'summary.json').write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding='utf-8')
page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>AE2LF 后台实测截图</title><style>body{font:16px system-ui;background:#151b24;color:#e5eaf2;margin:32px}
p{line-height:1.6}a{color:#88caff}input,select{padding:10px;margin:8px;background:#263344;color:white;border:1px solid #64758d}
main{display:grid;grid-template-columns:repeat(auto-fit,minmax(360px,1fr));gap:18px}article{background:#202a38;padding:16px;border-radius:10px;overflow:hidden}
article[hidden]{display:none}article h3{font-size:15px;overflow-wrap:anywhere}img{width:100%;image-rendering:pixelated}article p{font-size:13px;color:#bcc9db}
header{max-width:1100px;margin-bottom:24px}</style><header><h1>AE2LF 后台实测截图</h1>
<p>真实 Minecraft 帧缓冲截图。窗口创建时禁止显示与抢焦点。模型展示页使用原生物品渲染器，是测试视图，不是产品 UI。点击截图查看原图。</p>
<p><strong>完整 0.0.5 验收仍未通过：</strong>手持编码器、可染色编码面板和完整 SFM 执行未实现。FE 验证真实 AppliedFlux 存储键、磁盘和供应器返回路径，未涵盖感应卡 CPU 分配或未安装附属。</p>
<p><a href="summary.json">机器可读结果</a> · <a href="REPORT.md">验查记录与范围</a></p>
<input id="search" placeholder="筛选名称"><select id="gen"><option value="">全部版本</option><option>1.21.1</option><option>26.1.2</option></select>
<select id="category"><option value="">全部类型</option><option>UI</option><option>Item model</option><option>Placed block</option><option>Provider direction</option></select></header><main>'''
page += ''.join(cards)
page += '''</main><script>function filter(){const q=document.querySelector('#search').value.toLowerCase(),g=document.querySelector('#gen').value,c=document.querySelector('#category').value;document.querySelectorAll('article').forEach(a=>a.hidden=!(a.dataset.name.toLowerCase().includes(q)&&(!g||a.dataset.gen===g)&&(!c||a.dataset.category===c)))}document.querySelectorAll('input,select').forEach(e=>e.addEventListener('input',filter));</script></html>'''
(out / 'index.html').write_text(page, encoding='utf-8')
print(json.dumps({g: {'steps': v['steps'], 'failed': v['failed'], 'counts': v['counts'],
                      'compatibility': v['compatibility']['status']} for g, v in summary['generations'].items()}, indent=2))
