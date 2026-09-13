from pathlib import Path
import json
root=Path(__file__).resolve().parents[2]
out=root/'archive/2026-09-08-full-stress'
s=json.loads((out/'summary.json').read_text(encoding='utf-8'))
a=s['generations']['1.21.1'];b=s['generations']['26.1.2']
report=f"""# AE2LF hidden native regression and pressure evidence — 2026-09-08–09

This is a bounded acceptance report for the current development implementation, **not complete 0.0.5/SFM compatibility or a release claim**. Both generations ran serially as real invisible Minecraft clients. Original framebuffers are linked in [the searchable gallery](index.html); [machine-readable results](summary.json) record exact counts, key types and artifact hashes. No published release or PCL installation was replaced.

## Results

| Gate | 1.21.1 | 26.1.2 |
| --- | --- | --- |
| Native UI/model/feature rows | {a['steps']} passed | {b['steps']} passed |
| Separate bilingual guide rows | {a['guideSteps']} passed | {b['guideSteps']} passed |
| Unit/regression suite | {a['tests']['tests']} passed | {b['tests']['tests']} passed |
| Actual independent transfer networks | 8 | 8 |
| Actual round trips | 4096 | 4096 |
| Job continuation codec restores | 8192 | 8192 |
| Real stress ticks | 1024 | 1024 |
| Maximum measured audit-body time per tick | {a['stress']['maxAuditTickMillis']:.3f} ms | {b['stress']['maxAuditTickMillis']:.3f} ms |
| Separate-process restart | passed | passed |
| Every observed window invisible / normal exit | passed | passed |

The gallery contains {s['screenshots']} original visual frames. Preparation/assertion frames remain in each raw evidence directory. Item model cards use Minecraft's native renderer in a test view; they are not product UI. Menu actions and packets are real, but physical mouse/keyboard replay is not claimed. Audit-body time is not full server tick cost or a TPS benchmark.

## Implementation and native assertions

- Bufferless GET declarations, partial PUT transfers, exclusion selectors and Pn preserve exact material balances. Eight actual networks repeat 512 round trips each; shared tests also exercise 100000 yielding tick/restores and 100000 source redeclarations. A reproduced pathological wildcard regex hang was replaced by constant-stack matching; its [thread dump](wildcard-hang-thread.txt) is retained.
- A single label binds multiple machines; HAS sums the group and quantities are shared across members. Seven actual machines verify many-to-many, one-to-many, many-to-one, full/partial destinations, overlapping labels, removal/replacement, redstone to all members, and 128 group round trips. Endpoint identity prevents self-transfer quota loss; per-key initial availability prevents a later group source from re-exporting newly received stock in that PUT. These are numeric ceilings, not physical caches.
- Ordinary zero-result transfers wait; partial success may continue. Must quantities retain exact remaining demand across snapshots. A real group accepts 40, waits, then accepts 24 while an independent task completes. The terminal advances all admitted tasks, not only the queue head. A separate-process fixture saves after 24 and resumes only the remaining 40.
- Functions are program-scoped declarations anywhere, including after calls/imports, in unexecuted branches, loops and other functions. Native group transfer calls a function declared in a skipped branch after the caller. Forward references and nested wait/restore also have unit tests; duplicate/reserved names and unmatched end remain errors.
- Native AE2 CPU order: two real furnace batches, one dispatch until the code fully ends and physical resources are settled. Byproducts, partial returns and unrelated main storage do not release blocking; even a completed primary cannot admit the next batch while the prior code tail runs. O2 selects the byproduct and O1 the primary output.
- Supported SFM timers, global offsets, pulses, INPUT/OUTPUT, FORGET, quoted labels, exclusions and Boolean conditions execute in the durable VM. Recursion errors recover, ten later pulses run, timers execute twenty times, and held redstone triggers once.
- Invalid code shows line/reason in the selected language. Native client save cases cover unknown commands, bad indentation, missing sources, invalid selectors, Pn without a recipe and zero wait. Rejected saves preserve the exact physical pattern slot. Invalid recipe loops and uploads are rejected. Runtime recursive failure is visible in red; correcting code restores saving.
- Three kinds of duplicate network service disconnect physically, recover after winner removal and re-elect deterministically. Subnet providers take precedence over terminals. Fourteen topology assertions run per generation.
- Actual AppliedFlux induction cards fill a bounded 1000000 FE source cache; code transfers it, removal returns unused energy and 64 card changes conserve the total. Optional dependencies are test inputs, never packaged production dependencies.
- Native terminal/provider destruction returns the block, physical encoded pattern and allocated item input exactly once. Non-item destruction recovery is not established by those item assertions.
- Loaded key types ae2:i, ae2:f and appflux:flux pass native storage cells, reversed transfers, job restore and real provider subnet/main return. The FE test compares initial stock left by prior tests, rather than assuming an empty subnet.
- Maximum 65536-character Chinese/emoji code survives client packets, item components and binary NBT. Separate JVM restart restores maximum drafts and 6000 synthetic tag positions and three actual source-machine bindings per terminal, plus custom wait state, SFM elapsed/held-pulse state and FE cache. Synthetic positions test serialization, not 6000 live machines.
- Toolbar icons distinguish recipe/upload/save, language resources are isolated, phone/grey provider/dyeable panel frames are retained, and the missing native panel particle reference is fixed. Provider lists page server-side at 32 rows; greater-than-32-provider world pressure is not established by the current chooser screenshot.

## Artifact and evidence identity

| Generation | Final JAR SHA-256 |
| --- | --- |
| 1.21.1 | `{a['jarSha256']}` |
| 26.1.2 | `{b['jarSha256']}` |

Both final JARs have exactly the same compiled class hashes as their restart7 artifacts. Both full feature runs use those classes. Separate bilingual native guide captures are included in the gallery. Production archive inspection rejects helper classes, ImmortalStorage classes and nested JARs. Artifacts use the development filename/version 0.0.5; no 0.0.5 release was published.

Raw full runs: `{a['evidenceDirectory']}` and `{b['evidenceDirectory']}`. Restart pairs: `archive/2026-09-08-loop-factory-0.0.5/full-stress-restart7-<generation>-prepare/resume` and their summaries. The final runner requires every independent report to pass, every row invisible and successful, normal shutdown and exit code zero.

Earlier `induction-sfm1` / `recovery-stress1` rows did not enforce compatibility-report success; a nonzero initial FE stock caused a false failure in that independent fixture. These runs are superseded, not evidence of a fully passing compatibility gate. Strict `final-stress1` passed after that fixture correction; `final-stress2-26.1.2` correctly failed its runtime-error UI test because the decorative terminal was unpowered. The powered fixture passes in the current runs. Full feature evidence now uses `final-stress7` for both generations. Modern full4 failed because a test selector included cobblestone filler; group-debug2 proved exactly 4 iron plus 60 cobblestone moved. Both excluded types are now checked. Modern full6 passed must/parallel/full-blocking assertions but predates On output references and remains historical evidence.

## Remaining boundaries

Advanced SFM RETAIN/EACH/WITH/WITHOUT, slot ranges, round-robin, relative sides, full set predicates and resource OR are unsupported. Arbitrary uninstalled addon handlers, long-duration chunk unload/rejoin, non-item destruction recovery and large provider-page world pressure remain unverified. Overlap detection between two wildcard source selectors is conservative and can make unrelated mandatory declarations wait; concrete selectors and tested Pn/On cases do not establish exact general wildcard overlap. Initial-availability ceilings apply within a source group, not across all aliased source declarations. This report does not establish every legacy crafting/planner/portable-cell feature under real-world pressure; their existing regression suites ran, and their registered models/menus were captured. Do not label the whole request or 0.0.5 universally complete.

# 中文验收记录

本报告记录当前开发实现的**有界后台实际验收，不代表 0.0.5 或完整 SFM 全量兼容已完成**。两版真实客户端串行运行，全程隐藏窗口，正常保存退出。共 {s['screenshots']} 张可检索原始展示截图，另保留准备与断言截图；物品卡片是原生渲染器的测试视图，菜单操作经真实客户端动作／数据包执行，不宣称物理键鼠回放。

1.21.1／26.1.2 分别通过 {a['steps']}／{b['steps']} 条实际记录，单元测试 {a['tests']['tests']}／{b['tests']['tests']} 项。每版 8 个实际网络各运行 512 次往返，总计每版 4096 次，8192 次任务恢复，持续 1024 个真实服务端 tick。另有 10 万次循环恢复和 10 万次来源声明的共享逻辑压力测试。表中耗时仅为测试逻辑本身，不是完整服务端 tick 或 TPS。

非法保存按行号和原因报错，保留草稿与原样板；未知指令、缩进、来源、反选、无配方 Pn、零等待、配方死循环及非法上传都有原生菜单断言。递归运行错误在编码界面以红字显示，修正代码后可重新保存。中文和英文分别解析，不拼接显示。

同一标签对多机器的数量是整组共享额度，has 汇总整组。七台实际机器验证了多对多、一对多、多对一、满载和部分容量、重叠标签、移除替换、整组红石及 128 次往返。端点身份避免自转运消耗额度，每次 put 的来源数量上限避免新收资源在同一来源组中重复发出，未引入实物缓存。

函数声明不要求位置，可以在调用之后、未执行分支、循环或其他函数内；名称为程序级，声明不执行函数体。实际机器测试使用了调用之后未执行分支中的函数声明，单元测试也覆盖了前向引用、嵌套等待与恢复；重名、保留字和 end 错误仍拒绝。

阻挡模式由真实 AE2 CPU 和熔炉验证：代码完整结束且资源结清前不发下一批，副产物、部分返回和无关库存均不放行。即使主产物足量返回，只要代码尾部仍运行或等待，也不能放行下一批。O1/O2 分别验证主副产物顺序。循环、来源声明、反选和材料数量均检查守恒。

普通数量完全受阻时保留当前指令，部分成功可继续；must 在所属任务中累计足量后才继续。实际多机器组先完成 40、受阻，再完成 24；等待期间另一任务可完成。独立 JVM 重启另验证了先完成 24，存档后只补剩余 40，不重复转运。双语指南另有每版 {a['guideSteps']} 项后台截图记录，并修复了旧版指南排版器导致的中文长句右侧截断。

新增实际验证覆盖三种重复服务物理断网与恢复、供应器子网优先级、终端递归错误后的红石任务恢复、SFM 定时与持续高电平不重放、感应卡 100 万 FE 缓存及 64 次取放守恒、终端／供应器拆除时物品原料和实体样板只返还一次。已安装的物品、流体、AppliedFlux FE 类型通过真实存储及主／子网物流检查。

最大 65536 字符中文／表情代码经过客户端传输、实体样板与二进制 NBT 往返；新 JVM 还恢复了完整长草稿、每终端 6000 条合成的标签位置及同标签三台实际机器的绑定、等待进度、SFM 计时／电平状态和 FE 缓存。这里的标签位置只验证大数据存档，不能当作 6000 台机器性能成绩。

供应器灰白黑背面与六向箭头、手机、可染色面板和双语按钮截图保留在图册。修复了编码面板破坏粒子的缺失引用。最终 JAR 与 restart7 重启测试的所有编译类哈希一致；两版 final-stress7 全套界面和功能记录使用这些类，另行采集了中英文原生指南。未打入测试模组、其他模组类或嵌套 JAR，未覆盖 PCL 安装或发布新版本。

仍未完成：高级 SFM 语法、任意未安装附属能力、长时间区块卸载／重连、非物品拆除恢复及超过 32 个供应器的实际翻页压力。两个通配选择器的交集判定仍偏保守，可能使无关 must 来源额外等待；来源数量上限按单个来源组计算，未证明跨全部别名来源声明的统一上限。旧版全部合成规划／便携存储功能只运行了既有回归并截图，不能称每项都经过本轮世界压力测试。完整范围与原始日志以上述英文逐项记录及 JSON 为准。
"""
(out/'REPORT.md').write_text(report,encoding='utf-8')
print(out/'REPORT.md')
