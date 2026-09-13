"""Accept only complete native, parallel-order, UI, stress and separate-JVM evidence."""
from pathlib import Path
import argparse,json,hashlib,zipfile,xml.etree.ElementTree as ET,html,os
parser=argparse.ArgumentParser()
for name in ['native','regression','modern','restart','modern-restart']:parser.add_argument('--'+name,required=True)
parser.add_argument('--modern-native')
a=parser.parse_args();root=Path(__file__).resolve().parents[2];out=root/'archive/2026-09-09-mekanism-bulk'
base=root/'archive/2026-09-08-background-full-audit'
folders={key:base/value for key,value in [('native',a.native),('regression',a.regression),('modern',a.modern)]+([('modernNative',a.modern_native)] if a.modern_native else [])}
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
artifacts={};tests={}
for gen,minimum in [('1.21.1',203),('26.1.2',201)]:
 project=root/f'versions/neoforge-{gen}';jar=project/f'build/libs/ae2lf-neoforge-mc{gen}-0.0.5.jar'
 artifacts[gen]=digest(jar)
 with zipfile.ZipFile(jar) as z:
  assert 'com/example/ae2lightoptimizer/factory/FactoryNativeTransfers.class' in z.namelist()
  assert not any(n.startswith(('mekanism/','me/ramidzkh/','com/example/ae2lfprobe/')) or 'immortalstorage' in n.lower() or n.endswith('.jar') for n in z.namelist())
 counts={k:0 for k in ['tests','failures','errors','skipped']}
 for path in (project/'build/test-results/test').glob('TEST-*.xml'):
  attrs=ET.parse(path).getroot().attrib
  for key in counts:counts[key]+=int(attrs.get(key,0))
 assert counts['tests']>=minimum and not any(counts[k] for k in ['failures','errors','skipped'])
 tests[gen]=counts
rows={};all_frames={}
for key,folder in folders.items():
 gen='26.1.2' if key.startswith('modern') else '1.21.1'
 audit=read(folder/'ui-item-block-audit.json');assert audit['status']=='completed' and (folder/'normal-shutdown.txt').exists()
 assert all(row['passed'] and row['windowVisible']==0 for row in audit['results']),folder
 assert read(folder/'artifact.json')['productionSha256']==artifacts[gen],folder
 rows[key]=len(audit['results']);frames=sorted((folder/'screenshots').glob('*.png'));assert len(frames)==rows[key]
 all_frames[key]=frames
assert rows['native']==6 and rows['regression']>=149 and rows['modern']>=151 and ('modernNative' not in rows or rows['modernNative']==3)
common=['factory-report','native-cpu-report','compatibility-audit','topology-report','terminal-report','induction-report','multi-tag-report','stress-report','complex-flow-report']
for key in ['regression','modern']:
 folder=folders[key]
 for name in common:assert read(folder/f'{name}.json')['status']=='passed',(key,name)
 assert read(folder/'stress-report.json')['completedRoundTrips']==4096
 cpu=read(folder/'native-cpu-report.json')
 assert cpu['full_primary_return_keeps_gate_closed_until_tail_finishes'] and cpu['next_recipe_rejected_while_previous_tail_runs']
 assert read(folder/'complex-flow-report.json')['blocking_never_admits_two_pipeline_batches']
 assert read(folder/'multi-tag-report.json')['ordinary_fully_blocked_skips_without_consuming_source']
 group=read(folder/'multi-tag-report.json')
 assert group['batch_membership_matches_live_single_position_checks'] and group['membership_benchmark_same_seven_world_machines'] and group['single_member_fast_path_matches_live_membership']
 ids={row['id'] for row in read(folder/'ui-item-block-audit.json')['results']}
 assert {'ui-runtime-ordinary-skip','ui-runtime-wait'}<=ids
assert read(folders['regression']/'compatibility-audit.json')['native_FE_bridge_rejects_GTEU']
assert read(folders['modern']/'compatibility-audit.json')['native_FE_bridge_accepts_FE']
mek=read(folders['native']/'mekanism-bulk-report.json');native=read(folders['native']/'native-capability-report.json')
assert mek['status']==native['status']=='passed'
def parallel(report,batches,output):
 assert report['status']=='passed' and report['active']==0 and report['peakJobs']==16
 assert [r['resource'] for r in report['orders']]==['iron','gold','copper']
 assert all(r['admitted']==r['completed']==r['batches']==batches and r['active']==0 and r['output']==output and not r['blocking'] for r in report['orders'])
 assert report['returned']==[output]*3 and report['timeline'][-1]['active']==[0]*3
 assert report['three_order_types_simultaneously_active'] and report['primary_return_keeps_code_tail_active']
 for row in report['timeline']:
  assert all(x-y==z for x,y,z in zip(row['admitted'],row['completed'],row['active'])) and sum(row['active'])<=16
parallel(mek,96,1152)
assert native['directions']==32 and native['codecRestores']>0 and native['only_item_and_fluid_AE_key_types'] and native['no_AE2_extra_storage_addons']
assert native['native_HAS_chemical_without_AE_key'] and native['native_HAS_FE_without_AE_key'] and len(native['registeredAEKeyTypes'])==2
modern_details={}
for key in ['modern']+(['modernNative'] if 'modernNative' in folders else []):
 folder=folders[key];transactions=read(folder/'native-transactions-report.json');orders=read(folder/'parallel-orders-report.json')
 assert transactions['status']=='passed' and transactions['directions']==32 and transactions['codecRestores']>0
 for check in ['destination_refusal_rolls_back_native_source_transaction','explicit_wrong_face_does_not_fallback','unsided_readonly_falls_back_to_available_faces','custom_registered_resource_partial_must_and_exclusion','native_FE_partial_must_is_independent','ordinary_native_custom_resource_fully_blocked_skips','native_HAS_uses_registered_resource_and_energy_handlers']:assert transactions[check]
 parallel(orders,12,12);modern_details[key]={'transactions':transactions,'orders':orders}
restart_base=root/'archive/2026-09-08-loop-factory-0.0.5'
for gen,prefix in [('1.21.1',a.restart),('26.1.2',a.modern_restart)]:
 assert read(restart_base/f'{prefix}-summary.json')['status']=='passed'
 for phase,status in [('prepare','restart_ready'),('resume','passed')]:
  folder=restart_base/f'{prefix}-{phase}';assert (folder/'normal-shutdown.txt').exists()
  assert read(folder/'artifact.json')['productionSha256']==artifacts[gen]
  for name in ['native-cpu-report','restart-extras']:assert read(folder/f'{name}.json')['status']==status
# Use the targeted camera capture for the three modern native world views; keep raw full-run frames intact.
overrides={path.name:path for path in all_frames.get('modernNative',[])}
images=all_frames['native']+all_frames['regression']+[overrides.get(path.name,path) for path in all_frames['modern']]
performance={}
for gen,key,baseline in [('1.21.1','regression','mek-regression3-1.21.1'),('26.1.2','modern','native-sync1-26.1.2')]:
 group=read(folders[key]/'multi-tag-report.json');stress=read(folders[key]/'stress-report.json');before=read(base/baseline/'stress-report.json')
 performance[gen]={'membershipOriginalMedianMicros':group['membershipOriginalMedianMicros'],'membershipBatchMedianMicros':group['membershipBatchMedianMicros'],'membershipCandidates':group['membershipCandidates'],'stressBefore':before,'stressAfter':stress,'timingScope':'Diagnostic samples on this machine, not a TPS guarantee; same-server paired membership test uses seven real machines and512 stale positions.'}
summary={'status':'passed','evidence':{k:str(v.relative_to(root)) for k,v in folders.items()},'rows':rows,'artifacts':artifacts,'tests':tests,'mekanism':mek,'nativeCapabilities':native,'modernNative':modern_details,'restart':{'1.21.1':a.restart,'26.1.2':a.modern_restart},'performance':performance,'intermediateModernStress':read(base/'perf-full1-26.1.2/stress-report.json'),'galleryFrames':len(images),'rawFrames':sum(map(len,all_frames.values())),'cameraOverrides':[str(p.relative_to(root)) for p in overrides.values()]}
(out/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
(out/'artifacts.json').write_text(json.dumps(artifacts,indent=2),encoding='utf-8')
cards=''.join(f'<article><h2>{html.escape(p.parent.parent.name+" / "+p.stem)}</h2><img loading="lazy" src="{os.path.relpath(p,out).replace(chr(92),"/")}" style="max-width:100%"></article>' for p in images)
(out/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Native factory QA</title><style>body{background:#18202a;color:#eee;font:16px system-ui;margin:24px}article{margin-bottom:28px}h2{font-size:17px}</style><h1>Original hidden-client frames</h1>'+cards,encoding='utf-8')
table='\n'.join(f"| {r['resource']} | {r['admitted']} | {r['completed']} | {r['active']} | {r['output']} |" for r in mek['orders'])
report=f"""# Native factory acceptance — 2026-09-09

Ordinary zero/partial logistics continue; must obligations accumulate until fulfilled, including obligations declared by GET must. Each admitted task retains its own continuation. Both native editor variants check ordinary skip separately from must waiting. Recipe loops must still finish one round; an ordinary refusal cannot hide a zero-tick infinite loop.

## Actual 1.21.1 Mekanism processing

Pinned Mekanism 10.7.19.85 metadata and SHA512 are in inputs/. Three real AE2 CPUs submit iron/gold/copper orders simultaneously through one nonblocking provider and one shared subnet. Each recipe consumes one raw block and produces twelve ingots through unmodified native enrichment and smelting. Eight actual factories have normal placed-item components, sorting and eight native speed/energy upgrades; no recipe or processing duration is changed. Observed machines used: {mek['machinesUsed']}.

| Order | Admitted | Completed | Active at end | Ingots |
| --- | --- | --- | --- | --- |
{table}

Total: 288 invocations, 288 raw blocks, 3456 returned ingots. All three types coexist; the shared 16-task cap is reached and never exceeded. Every tick validates admitted minus completed equals active per order. Primary returns do not remove jobs during the five-tick code tail. Capacity/power refusal preserves must remainders; final machine inputs, dust, outputs, provider jobs and CPUs settle. Every recipe starts with an unavailable ordinary diamond route, which must skip for production to proceed. Final recorded tick: {mek['ticks']}.

## Direct chemical and FE capabilities

No Applied Mekanistics or AppliedFlux is loaded in the dedicated native gate; AE2 registers only item/fluid key types. Transport calls `{native['chemicalCapability']}` and `{native['energyCapability']}` directly. Two oxygen sources, excluded hydrogen and constrained recipients verify native HAS, group quotas and partial must. Initial acceptance is 1000 chemical units and 4096 FE; after capacity opens, 30000 oxygen and 100000 FE finish exactly. Thirty-two subsequent alternating directions per resource verify both endpoints each time; {native['codecRestores']} actual job codec restores. Cumulative movement 990000 chemical units / 3300000 FE counts repeated transport, not generated resources.

## Synchronized 26.1.2 fixes

Modern machines use NeoForge transactional ResourceHandler/EnergyHandler capabilities. RegisteredResource identities allow additional compatible handlers to be discovered without AE storage keys. Extraction and insertion share a transaction; changed destination acceptance rolls back both. Explicit faces stay exact; omitted faces can use exposed directions when the unsided handler is read-only. Unsupported resource objects without registered identities need adapters.

The modern fixture uses explicitly test-only registered capabilities backed by persistent world barrel data. It checks rollback, directional fallback, exclusion, native HAS, ordinary rejection skip, partial must and 32 alternating transfers with codec restoration. This is interface-contract evidence, not a claim of running modern Mekanism. A separate real vanilla blast-furnace fixture uses three native CPUs on the same nonblocking subnet: 12 iron, 12 gold and 12 copper invocations all complete; shared peak 16 and final zero active. The official [Modrinth query](inputs/mekanism-26.1.2-versions.json) returned no 26.1.2 Mekanism artifact.

Both selectors and native FE bridges reuse the established appflux:flux/appflux:fe identity rule. Real older AppliedFlux GTEU is excluded from FE; modern AppliedFlux FE is accepted. Whole-family Flux classification is no longer used.

## Full regression, restart and visual evidence

Final artifacts pass {rows['regression']} old-generation and {rows['modern']} modern full hidden-client rows, including all independent factory, native CPU, topology, terminal, induction, group and complex-flow reports. Each generation completes 4096 world round trips. Both generations additionally pass separate-JVM prepare/resume pairs. Unit tests: {tests['1.21.1']['tests']} / {tests['26.1.2']['tests']}, no failures/errors/skips. All accepted clients remain invisible and exit normally. Runtime/phase manifests match the final artifact hashes; JARs contain no test-helper, MEK, unrelated-mod classes or nested JARs.

[Original screenshot gallery](index.html): {len(images)} views. Modern native world views use their actual fixture camera. Optional focused replacements, if any, are recorded in summary.json; raw full-run files remain unchanged. [Raw accepted results](summary.json) include timelines and manifests. Earlier failed/superseded runs are retained and excluded. Notable corrections: invalid initial fixture CPU/placement assumptions, an obsolete ordinary-wait UI expectation, incorrect provisional FE key identity and a report refresh issue.

SHA256 1.21.1: `{artifacts['1.21.1']}`

SHA256 26.1.2: `{artifacts['26.1.2']}`

## World performance

Both adapters now resolve fallback faces on demand and snapshot source inventories only. Capability views live for one operation, so replacement and topology changes remain visible. Modern registered capability metadata is resolved once; older MEK reflective methods are resolved once. Batch tag membership traverses the live grid once per tag instead of once per position; single-machine tags retain a direct path without a batch set. Four shared lazy-lookup tests verify one successful unsided query, ordered fallback, full refusal and fresh-operation resolution. Live group tests compare batch results with the original per-position oracle before/after removal and replacement.

Paired membership medians (old traversal / new batch, microseconds): 1.21.1 {performance['1.21.1']['membershipOriginalMedianMicros']} / {performance['1.21.1']['membershipBatchMedianMicros']}; 26.1.2 {performance['26.1.2']['membershipOriginalMedianMicros']} / {performance['26.1.2']['membershipBatchMedianMicros']}. Each uses seven real barrels plus512 stale saved positions, alternating order, four warmups and twelve samples. An initial modern optimized run passed all correctness checks but recorded4675.633ms versus the3206.3329ms baseline; it is retained explicitly. A subsequent refinement restores the direct single-machine membership path and measures job execution separately from codec/restore work. End-to-end stress measurements before/after are preserved in summary.json; they include compilation/codec overhead and are diagnostic, not a TPS guarantee. No wait frequency, concurrency limit or transport quantity is reduced.

## Boundaries

Actual MEK tests are 1.21.1 only. Modern custom endpoint fixtures and vanilla processing are named explicitly. This does not prove compatibility with every uninstalled mod, aliases exposing one inventory through multiple capability names, malicious legacy handlers, non-item legacy destruction recovery or exhaustive chunk lifecycle scenarios. No release or PCL installation is replaced. See the [1.21.1 guide](../../docs/loop-factory-native-capabilities-1.21.1.md) and [26.1.2 guide](../../docs/loop-factory-native-capabilities-26.1.2.md).

# 中文验收

两代均已实现：普通物流零接受或部分成功后继续，must累计足量才继续；get must义务随任务保存。普通跳过不能掩盖零tick死循环。界面分别验证普通请求不等待、must请求显示等待。

1.21.1使用真实MEK机器，在同一个非阻挡供应器子网同时提交铁、金、铜订单，各96批，共288批；每种准确返回1152个锭。三种订单同时活动，共享峰值16；逐tick计数守恒，最终活动数全部归零，机器及供应器资源结清。化学品／FE直接通过NeoForge能力转运，不依赖额外AE2存储类型；各32次交替方向与保存恢复通过。

26.1.2同步原生事务式物流、方向回退、FE身份判别及同子网计数逻辑。测试附属注册的持久化世界端点用于接口／回滚测试；真实原版高炉执行铁、金、铜各12批并行订单并全部结清。当前没有可用的26.1.2MEK包，因此没有把这些现代版检查称为MEK实测。

世界性能方面，两代改为按需查询接口方向、只快照来源库存、一次遍历检查整个标签；单机器标签保留直接路径。接口对象与库存数量不跨操作缓存。新版缓存固定能力注册信息，旧版缓存MEK反射方法。真实拆除与替换前后，批量／单点归属检查均与原逻辑一致。

同场景归属检查中位耗时（微秒，原逐点／新批量）：1.21.1为{performance['1.21.1']['membershipOriginalMedianMicros']}／{performance['1.21.1']['membershipBatchMedianMicros']}；26.1.2为{performance['26.1.2']['membershipOriginalMedianMicros']}／{performance['26.1.2']['membershipBatchMedianMicros']}。场景包含7台真实机器与512个失效保存位置。初版新版压力耗时更高的4675.633毫秒样本仍保留；最终压力数据另列物流任务与保存恢复开销。这些是本机局部诊断，不能直接换算为所有世界的TPS提升。

最终两代完整实机回归分别{rows['regression']}／{rows['modern']}项，各4096次物流往返，独立JVM重启均通过；单元测试分别{tests['1.21.1']['tests']}／{tests['26.1.2']['tests']}项通过。画廊提供{len(images)}张原始截图；失败及中间过程保留，不计入验收。生产JAR隔离、实际测试文件哈希与正常后台退出均已核验。
"""
(out/'REPORT.md').write_text(report,encoding='utf-8')
print(json.dumps({'status':'passed','rows':rows,'tests':tests,'galleryFrames':len(images),'output':3456},ensure_ascii=False))
