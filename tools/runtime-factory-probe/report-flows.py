"""Require fresh full, guide and separate-process evidence before writing the flow report."""
from pathlib import Path
import json,hashlib,zipfile,html,os,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2];out=root/'archive/2026-09-09-factory-flows'
sources={'1.21.1':'flow1-1.21.1','26.1.2':'flow1-26.1.2'}
summary={'generations':{},'limitations':['Advanced SFM clauses remain unsupported','One run is sequential; separate admitted jobs progress independently','Two wildcard must-selector overlap is conservative','Uninstalled addon handlers and arbitrary machine implementations are not exhaustively tested']};cards=[]
for gen,name in sources.items():
 base=root/f'archive/2026-09-08-background-full-audit/{name}'
 guide=root/f'archive/2026-09-08-background-full-audit/flow-guide{2 if gen=="1.21.1" else 5}-{gen}'
 assert json.loads((guide/'visual-review.json').read_text(encoding='utf-8'))['status']=='accepted_visual_review'
 if gen=='26.1.2':
  for locale in ('en_us','zh_cn'):
   assert json.loads((guide/f'guide-layout-{locale}.json').read_text(encoding='utf-8'))['status']=='passed'
 records={'fullEvidence':str(base.relative_to(root)),'guideEvidence':str(guide.relative_to(root))}
 for directory in (base,guide):
  seen_frames=set()
  audit=json.loads((directory/'ui-item-block-audit.json').read_text(encoding='utf-8'))
  assert audit['status']=='completed' and (directory/'normal-shutdown.txt').is_file()
  assert all(r['passed'] and r.get('windowVisible')==0 for r in audit['results'])
  records['guideRows' if directory==guide else 'fullRows']=len(audit['results'])
  for row in audit['results']:
   frame=directory/'screenshots'/f"{row['id']}.png"
   assert frame.is_file()
   if not row['id'].startswith(('ui-','model-','block-','part-','arrows-','complex-')):continue
   fingerprint=hashlib.sha256(frame.read_bytes()).hexdigest()
   if directory==guide and fingerprint in seen_frames:continue
   seen_frames.add(fingerprint)
   rel=os.path.relpath(frame,out).replace('\\','/')
   cards.append(f'<article data-name="{gen} {html.escape(row["id"])}"><h3>{gen} · {html.escape(row["id"])}</h3><a href="{rel}"><img loading="lazy" src="{rel}"></a></article>')
 for report in ('factory-report','native-cpu-report','compatibility-audit','topology-report','terminal-report','induction-report','multi-tag-report','stress-report','complex-flow-report'):
  data=json.loads((base/f'{report}.json').read_text(encoding='utf-8'));assert data['status']=='passed',report
  records[report]=data
 assert len(records['complex-flow-report']['orders'])==4
 assert sum(x['batches'] for x in records['complex-flow-report']['orders'])==12
 for phase,status in (('prepare','restart_ready'),('resume','passed')):
  restart=root/f'archive/2026-09-08-loop-factory-0.0.5/flow-restart1-{gen}-{phase}'
  assert (restart/'normal-shutdown.txt').exists()
  for report in ('native-cpu-report','restart-extras'):
   assert json.loads((restart/f'{report}.json').read_text(encoding='utf-8'))['status']==status
 assert json.loads((root/f'archive/2026-09-08-loop-factory-0.0.5/flow-restart1-{gen}-summary.json').read_text(encoding='utf-8-sig'))['status']=='passed'
 totals={k:0 for k in ('tests','failures','errors','skipped')}
 for file in (root/f'versions/neoforge-{gen}/build/test-results/test').glob('TEST-*.xml'):
  attrs=ET.parse(file).getroot().attrib
  for k in totals:totals[k]+=int(attrs.get(k,0))
 assert totals['tests']>190 and not any(totals[k] for k in ('failures','errors','skipped'))
 records['tests']=totals
 jar=root/f'versions/neoforge-{gen}/build/libs/ae2lf-neoforge-mc{gen}-0.0.5.jar'
 records['jarSha256']=hashlib.sha256(jar.read_bytes()).hexdigest()
 frozen=json.loads((out/'artifacts.json').read_text(encoding='utf-8'))
 assert records['jarSha256']==frozen[gen]
 with zipfile.ZipFile(jar) as z:
  assert not any('ae2lfprobe' in n or 'immortalstorage' in n.lower() or n.endswith('.jar') for n in z.namelist())
  for locale in ('','_zh_cn/'):
   assert '{/* factory-examples:start */}' in z.read(f'assets/ae2lightoptimizer/ae2guide/{locale}items-blocks-machines/loop_factory.md').decode('utf-8')
 summary['generations'][gen]=records
summary['frames']=len(cards)
(out/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
(out/'index.html').write_text('''<!doctype html><meta charset="utf-8"><title>AE2LF 工厂流程实机验证</title><style>body{font:16px system-ui;background:#17202b;color:#eef;margin:24px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(400px,1fr));gap:18px}article{background:#253347;padding:12px}img{width:100%}h3{font-size:14px}input{padding:12px;width:420px}</style><h1>工厂流程与指南原始截图</h1><p>真实隐藏客户端。指南含连续滚动帧，末端可能重复；不是独立场景数量。</p><input placeholder="按版本或名称筛选" oninput="document.querySelectorAll('article').forEach(e=>e.hidden=!e.dataset.name.includes(this.value))"><main>'''+''.join(cards)+'</main>',encoding='utf-8')
rows='\n'.join(f"| {gen} | {r['fullRows']} | {r['guideRows']} | {r['tests']['tests']} | {r['complex-flow-report']['terminalCodecRestores']} |" for gen,r in summary['generations'].items())
orders='\n'.join(f"| {gen} | {x['example']} | {x['batches']} | {x['blocking']} | {x['maxConcurrentJobs']} | {x['ticks']} |" for gen,r in summary['generations'].items() for x in r['complex-flow-report']['orders'])
report=f'''# Factory flow acceptance — 2026-09-09

Eight identical, compilable examples are published in both native language guides. The native fixture reads the same catalogue. A real persistence bug was fixed: restoring FactoryRoutes discarded the mandatory-source flag before the first PUT. The regression now restores GET must during its explicit wait, accepts only 24 of 64, lets a later gold task finish, then resumes the remaining 40 when space opens.

| Generation | Full native rows | Guide rows | Unit tests | Complex terminal codec restores |
| --- | --- | --- | --- | --- |
{rows}

## Native CPU orders through fueled vanilla furnaces

| Generation | Example | Batches | Blocking | Maximum admitted jobs | Actual ticks |
| --- | --- | --- | --- | --- | --- |
{orders}

Each order uses actual AE2 calculation, submission, provider dispatch, furnace processing and main-network returns. Inputs, primary products, byproducts and furnace buffers are checked numerically. The two-stage pipeline makes smooth stone from cobblestone, including an intermediate stone transfer. The multi-output recipe routes P1/P2 into separate furnace groups and returns O2 before O1. Recipe-free patterns are rejected by providers, recipe patterns by terminal slots, and wrong network bindings by providers.

Six real terminal networks also check ordinary partial/excluded transfers, mandatory recovery before first PUT, independent blocked and ready runs, a continuously yielding nested pipeline receiving late replenishment, a finite forward-function pipeline, two SFM triggers, and 32 actual redstone-triggered runs. The continuous program remains alive when empty; the finite program ends. Queued jobs use independent continuations. Code within one run remains sequential; this does not claim parallel EVERY branches inside a single program.

Fresh full regressions additionally include 4096 world round trips per generation, native UI/model frames, supported item/fluid/AppliedFlux FE, network ownership, induction caches, invalid-code errors and source conservation. Separate JVM prepare/resume pairs validate recipe continuation, pending must quantities, Unicode, group bindings and pulse state. All client ticks enforce invisible windows; every run requires normal shutdown. Test helpers and addon JARs are not packaged in production.

See [raw results](summary.json) and [original frames](index.html). Guide scroll frames can overlap or repeat at the end and are not counted as distinct scenarios. Earlier build failures are retained: the example test first needed CRLF normalization, then caught unquoted SFM labels Input/Output; the guides now quote those labels.

The expanded guides exposed two rendering issues caught in original screenshots: HTML comments are invalid in GuideME MDX, and GuideME 26.1.10-alpha fails to reset accumulated line width after explicit newlines. The markers now use MDX comments; a modern-only client mixin resets that width. The final modern guide run checks actual text-run horizontal bounds and short-example height in both languages, in addition to manual screenshot review. Earlier guide1/modern guide2/3 are rejected evidence. Full factory runs precede only these guide/client corrections; the factory execution implementation is unchanged. Final artifacts are separately validated by guide and fresh JVM restart runs.

## Boundaries

These finite tests are evidence for the listed flows, not a proof of every possible program or arbitrary external machine. Advanced SFM clauses remain unsupported; general wildcard-must overlap remains conservative. Long-duration chunk lifecycle, uninstalled addon handlers and non-item destruction recovery are not exhausted. No release or PCL installation was replaced.

# 中文验收

四份原生指南同步加入八组完整示例， 编译测试与实际测试读取同一份代码。 修复了来源声明恢复丢失 must 的问题： 在 get must 后等待期间恢复， 首次 put 只接收 24 个时仍等待， 后来的金锭任务可以独立完成， 腾出容量后只补剩余 40 个。

每版实际 AE2 CPU 完成四组、 共十二批配方订单： 两级圆石→石头→平滑石链分别验证阻挡与并行模式； 双输入双产物验证 P1/P2 与先 O2 后 O1； SFM 配方完成材料及产物结算。 核验真实燃料熔炉、 缓存清空、 产物数量和 CPU 结束状态。 无配方样板不能进配方供应器， 带配方样板不能进普通工厂终端， 错绑网络不能接收。

六个真实终端还验证普通部分转运、 反选、 must 恢复、 受阻任务与后续任务独立推进、 持续嵌套流程后续补料、 有限后置函数、 SFM 定时与红石组合， 以及三十二次连续红石派发。 持续程序空料时仍让出并等待补料， 有限程序按条件结束。 单个任务内部顺序执行， 不将多个 EVERY 块宣称为同一任务内的并行分支。

指南实拍还查出并修复了 MDX 注释解析错误和 26.1.2 GuideME 显式换行宽度不清零造成的裁切。新版客户端兼容修复后，重新检查双语截图、文字边界和短代码块高度；早期错误帧明确拒绝验收。工厂完整运行后仅修改指南与客户端排版，工厂执行代码不变，最终构建另经指南和独立进程重启验证。

结果表、 原始 JSON、 日志与截图共同构成证据。 每版还重新运行完整功能回归、 4096 次世界往返及独立进程重启。 全程隐藏客户端， 正常保存退出。 不能据此保证任意代码与所有外部机器都正确； 高级 SFM、 通配 must 交集等剩余范围见上文。 未发布新版或覆盖 PCL 安装。
'''
(out/'REPORT.md').write_text(report,encoding='utf-8')
print(json.dumps({g:{'full':r['fullRows'],'guide':r['guideRows'],'tests':r['tests'],'orders':r['complex-flow-report']['orders']} for g,r in summary['generations'].items()},ensure_ascii=False,indent=2));print(out/'REPORT.md')
