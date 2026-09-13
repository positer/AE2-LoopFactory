# AE2-LoopFactory (AE2LF)

AE2-LoopFactory is a NeoForge addon for Applied Energistics 2. It adds a programmable logistics layer, high-capacity universal storage, cyclic and acyclic crafting services, and a recipe-aware crafting provider. The project is maintained as two isolated Minecraft generations and keeps all optional integrations conditional: AE2LF never bundles another mod and never requires Create, Mekanism, Applied Flux, JEI, or EMI.

项目地址：<https://github.com/positer/AE2-LoopFactory>

当前公开发行版为 **[0.0.5](https://github.com/positer/AE2-LoopFactory/releases/tag/v0.0.5)**。完整验证与部署记录见 [2026-09-13 终端刷新报告](archive/2026-09-13-channel-face/REPORT.md)。

## What It Adds

### Loop Factory logistics

The Loop Factory consists of a Pattern, Pattern Provider, Handheld Encoder, Pattern Encoding Panel, Network Terminal, and Interface Cable.

- The recipe-free Network Terminal runs the indentation-based Loop Factory language.
- The Pattern Encoding Panel edits code and recipe data for ordinary AE2 patterns.
- The Handheld Encoder binds to a factory network and edits code without replacing the native AE2 editor workflow.
- The Interface Cable connects factory machines and AE2 networks without consuming an AE2 channel.
- A provider creates an isolated factory subnet in the direction of its arrows. Only AE power crosses the provider boundary automatically; storage, tags, and factory execution remain separate.

The terminal and provider use a real server-side execution state. Every job owns its VM continuation, source declarations, quotas, waits, function stack, and buffered output. Jobs are saved and restored with the block entity and use signed 64-bit quantities with checked arithmetic.

### Terminal execution rules

For a recipe-free terminal:

- Saving a different installed program, replacing the pattern, or removing the pattern cancels the old execution and starts the current program from its first instruction on the next eligible owner tick.
- A redstone **rising edge** performs the same refresh. A held signal does not restart the program every tick.
- Saving identical code and editing only an unsaved draft preserve the current continuation.
- Old WAIT and MUST debt, source declarations, function returns, error state, and pending output pulses are cleared during refresh.
- Physical input/output buffers and opaque native rollback records are moved to the persistent recovery queue before cancellation. Expected output debt is never treated as physical cargo, and already delivered resources are never rolled back.
- `FactoryTerminalRestartPending` is saved in NBT, so a refresh requested before an unload is not lost. Loading a pattern from NBT establishes the comparison baseline and is not treated as a code edit.

Recipe-provider jobs retain their independent lifecycle and are not reset by terminal-only rules.

### The code language

The indentation language uses four spaces. It supports:

- `import`, `get`, `put`, `has`, `if`, `else`, `while`, `wait`, `redstone`, `func`, `done`, `break`, and `channel`;
- item, fluid, FE, chemical, mana, source, soul, and other registered resource identities;
- wildcard and tag selectors, explicit machine faces, exclusions, boolean conditions, and comparisons;
- nested functions and saved continuations, with a 64-frame recursion limit;
- recipe references `P1`, `P2`, `O1`, `O2`, and aggregate `P`/`O` references when a pattern carries a recipe;
- `//` comments through the end of the physical line. A quoted `//` remains text. Existing `#` and SFM `--` comments remain supported.

`channel` is a lexical logistics scope. GET declarations and their remaining quotas belong to one channel; PUT can consume only declarations in that channel. Ordinary function statements inherit the caller's channel, including nested calls and saved MUST waits. An explicitly nested `channel` block keeps its own lexical scope. Channels do not create separate inventories or parallel threads: two channels that name the same physical container still compete for that container's real contents. See [channel semantics](docs/loop-factory-channels.md).

Normal transfers advance after partial or zero acceptance. `must` transfers retain the unfinished quantity in the owning job until the complete amount is settled. A wait or blocked MUST pauses that job only; independent jobs continue.

### Bulk logistics and stability

Large orders use compressed batches and checked signed 64-bit quantities instead of expanding into one instruction per item. Each job advances by its exact accepted amount, keeps its own backpressure and recovery state, and yields while another job can make progress. This supports high-volume item, fluid, and FE routes across ticks, including real capability backpressure and destination saturation. Verified campaigns include 4,096-round storage exchanges, 2,304 Mekanism batches, and signed-long boundary transfers; these are measured regression workloads, not a promise of unlimited TPS or arbitrary third-party machine behavior.

The native editor highlights keywords, strings, quantities, comments, labels, functions, operators, resources, recipe references, SFM triggers, and soft-wrapped text. The viewport and caret use the native `MultiLineEditBox` coordinate system, so shortening or scrolling code does not create phantom rows. See [highlighting and comments](docs/loop-factory-highlighting.md).

### Crafting and network services

**Crafting Ripper**

The Crafting Ripper is an AE2 Pattern Provider with 36 slots arranged in four rows. It accepts crafting-table, smithing-table, and stonecutting patterns. Before extraction it validates the complete selected recipe chain, rechecks live recipes, executes against private CPU inventory using checked `long` balances, and commits the result once. Recipe outputs, container returns, component data, byproducts, and cyclic seeds remain tied to the originating CPU job.

**Loop Card**

The Loop Card is made from an AE2 Advanced Card and one Loop Crystal. On the Ripper it enables automatic discovery of encodable crafting, smithing, and stonecutting recipes while installed patterns remain removable. In a portable cell it converts stored FE to AE charge. A portable cell with positive AE charge and stored FE also exposes its FE through the generation's native energy capability.

**Recipe Ring Solver Terminal**

This UI-free service solves cyclic crafting graphs. It condenses strongly connected components, keeps cyclic seeds reserved in the selected CPU, and submits compressed batches in strict order. A blocked batch cannot be skipped. Cursor position, remaining repetition counts, seed reserves, and final output ownership survive world reloads.

**Supercomputing Crafting Optimizer Interface**

This independent UI-free service accelerates ordinary acyclic graphs. It discovers reachable producers, combines routes, models byproducts and container remainders, and writes a compressed AE2 crafting plan. It declines before mutation when overflow or a valid integer schedule cannot be proven, allowing AE2 to retain control.

### Loop Storage Cells

Loop Storage Cells share one byte budget across AE2-registered key types. The family contains a housing, 1k/4k/16k/64k/256k tiers, 1M/4M/16M/64M/256M tiers, and an infinite tier.

- K tiers keep AE2-equivalent capacity and the 63-type limit.
- M tiers use an aggregate pool equal to 63 times the original single-type ceiling.
- The infinite tier removes amount and type limits.
- Items, fluids, FE, mana, source, soul, and other registered addon keys use the native amount-per-byte contract of their key type.
- Eleven portable cells reuse AE2's portable terminal, battery, four upgrade slots, charge rate, idle drain, and powered insertion/extraction.
- Portable-cell upgrades follow AE2 behavior for fuzzy, inverter, equal distribution, void, and energy cards.
- There are 53 acquisition recipes and 11 AE2-native disassembly declarations. Optional Create milling and Mekanism crushing recipes use `c:gems/loop_crystal` and are added only when those mods are loaded.

## Supported Versions

| Minecraft | NeoForge | AE2 | Java |
| --- | --- | --- | --- |
| 1.21.1 | 21.1.235 | 19.2.17 | 21 |
| 26.1.2 | 26.1.2.94 | 26.1.10-beta | 25 |

Minecraft 1.20.1 is not supported. The two generations are separate Gradle projects with separate adapters, caches, artifacts, and runtime validation. ImmortalStorage is a different project and is not an upstream, dependency, bundled class set, or submodule.

## Installation

Use the JAR matching the Minecraft generation:

```text
ae2lf-neoforge-mc1.21.1-0.0.5.jar
ae2lf-neoforge-mc26.1.2-0.0.5.jar
```

Copy the file into the instance's `mods/` directory together with the matching AE2 and NeoForge versions. Create, Mekanism, Applied Flux, JEI, and EMI remain optional. Do not install both generation-specific AE2LF JARs into one instance.

The verified local instances are:

```text
C:/Users/12252/Desktop/Files/Minecraft/PCL/.minecraft/versions/AE2-lightoptimizer-1.21.1
C:/Users/12252/Desktop/Files/Minecraft/PCL/.minecraft/versions/AE2-lightoptimizer-26.1.2
```

The 0.0.5 release JARs are deployed there. Deployment replaced only the matching AE2LF JAR; save files, configuration, dependencies, and other instance files were byte- and timestamp-checked.

## Building and Testing

Run the generation-specific wrapper from the repository root:

```powershell
powershell -NoProfile -File .\build-1.21.1.ps1 --offline --no-daemon build
powershell -NoProfile -File .\build-26.1.2.ps1 --offline --no-daemon build
```

The accepted 0.0.5 release artifacts were checked against their compiled classes and resources, with no embedded JARs, probe classes, or unrelated mod namespaces.

Latest evidence:

| Check | 1.21.1 | 26.1.2 |
| --- | ---: | ---: |
| Unit tests | 262 passed | 260 passed |
| Failures / errors / skipped | 0 / 0 / 0 | 0 / 0 / 0 |
| Native terminal-refresh assertions | 67 | 67 |
| Repeated redstone rising-edge checks | 32 | 32 |
| Persisted old jobs replaced in one refresh | 18 | 18 |
| Native syntax pages checked by framebuffer pixels | 11 | 11 |
| Native editor scroll/caret cases checked by pixels | 10 | 10 |

The terminal lifecycle tests use a real hidden Minecraft client and integrated server with real chests, redstone blocks, AE2 grids, binary NBT round trips, and production `FactoryJob` instances. Some cargo cases intentionally inject saved buffers to test ownership and serialization. They do not claim a physical mouse/keyboard session, arbitrary third-party capability coverage, or a new long-duration stress campaign. Full evidence is indexed in [archive/2026-09-13-channel-face/REPORT.md](archive/2026-09-13-channel-face/REPORT.md).

## Compatibility and Boundaries

- Machine transport resolves exact faces through the generation's native item, fluid, FE, and supported chemical capabilities. An explicit face never silently falls back to another face.
- Unloaded target chunks cause a recoverable pause or explicit error; the implementation does not fabricate inventories for unloaded areas.
- Recipe execution uses the active server recipe manager, complete item components, live tags, and checked 64-bit arithmetic.
- Advanced SFM clauses such as `RETAIN`, `EACH`, `WITH`, `WITHOUT`, slot ranges, polling, and relative-face syntax remain outside the supported language unless listed in the current guide.
- A physical machine may belong to more than one tag. Tags and channels are different concepts: channel isolation protects declarations and quotas, while an overlapping tag still names the same physical inventory.
- In the inspected user save, the output chest at `(2,-60,8)` was also included in `Furnance`, and 18 old terminal jobs were still persisted. The current JAR clears those old jobs on a code change or redstone rising edge, but the accidental `Furnance` binding must still be removed in-game while retaining `out`.

## Repository Layout

```text
shared/                         Loader-independent factory, storage, compiler and tests
versions/neoforge-1.21.1/       NeoForge 21.1.235 adapter and artifact
versions/neoforge-26.1.2/       NeoForge 26.1.2.94 adapter and artifact
docs/                            Channel, native-capability and editor behavior references
tools/                           Build, provisioning, native probe and verification scripts
archive/                         Immutable test evidence, deployment records and cold data
OVERVIEW.md                      Full file-tree and module responsibilities
taste.md                         Cross-version implementation and evidence contracts
```

## Project Status

The public release is 0.0.5. Source changes, artifacts, PCL deployment, save preservation, and known limitations are recorded in dated archive reports so that later reviews can distinguish implemented behavior from candidate or unverified compatibility.

AE2LF is distributed as a standalone project under its repository's own history and remote. Contributions should preserve the hard split between `shared`, the two NeoForge adapters, optional integrations, and test-only probe code.

## 中文简介

AE2-LoopFactory（AE2LF）是面向 Applied Energistics 2 的 NeoForge 附属模组，提供可编程物流、高容量通用存储、循环与无环合成服务，以及配方感知的合成供应器。项目维护两个完全隔离的 Minecraft 世代；Create、Mekanism、Applied Flux、JEI 与 EMI 都是可选兼容，AE2LF 不打包其他模组，也不把它们列为硬依赖。

当前公开发行版为 **0.0.5**。完整的终端刷新、存档排查、构建和部署证据见 [2026-09-13 报告](archive/2026-09-13-channel-face/REPORT.md)。

## 新增内容与核心逻辑

### 循环工厂物流

循环工厂由样板、样板供应器、手持编码器、样板编码面板、网络终端和网络接口线缆组成。

- 无配方网络终端运行四空格缩进的循环工厂语言。
- 样板编码面板同时处理普通 AE2 样板的配方页和代码页。
- 手持编码器绑定工厂网络并编辑代码，沿用 AE2 原生编辑工作流。
- 网络接口线缆连接工厂机器与 AE2 网络，但不占用 AE2 频道。
- 供应器箭头指向独立工厂子网；边界只自动传递 AE 电力，存储、标签与工厂执行状态保持隔离。

终端与供应器使用服务端真实执行状态。每个任务分别拥有虚拟机续点、来源声明、数量额度、等待、函数栈和输出缓存；任务随方块实体保存，所有数量使用带溢出检查的有符号 64 位整数。

### 终端执行刷新

无配方终端遵循以下规则：

- 保存不同的已安装代码、更换样板或移除样板时，取消旧执行，并在下一次有效归属 tick 从当前代码第一条指令重新运行。
- 工厂网络收到红石**上升沿**时执行同样的刷新；持续高电平不会每 tick 重启。
- 保存相同代码或只修改未保存草稿时，保留当前续点。
- 刷新会清除旧 WAIT、MUST 欠额、来源声明、函数返回、错误状态和待处理输出脉冲。
- 实际输入/输出缓存和不透明的原生回滚记录会先转入持久化回收区；预期输出欠额不会伪装成实物资源，已经送达的资源不会回滚。
- `FactoryTerminalRestartPending` 随 NBT 保存，区块卸载不会丢失待刷新请求；从 NBT 读取样板只建立比较基线，不会误判为代码编辑。

带配方的供应器任务保持自身生命周期，不套用终端专属刷新规则。

### 代码语言与编辑器

缩进语言使用四个空格，支持：

- `import`、`get`、`put`、`has`、`if`、`else`、`while`、`wait`、`redstone`、`func`、`done`、`break`、`channel`；
- 物品、流体、FE、化学品、魔力、魔源、灵魂及其他已注册资源身份；
- 通配符、资源标签、指定机器面、反选、布尔条件和比较；
- 嵌套函数与保存后的续点，递归深度上限 64 层；
- 带配方样板中的 `P1`、`P2`、`O1`、`O2` 与聚合 `P`/`O` 引用；
- `//` 注释本行后续内容。双引号内的 `//` 保留为字符串，原有 `#` 与 SFM `--` 注释继续支持。

`channel` 是词法物流作用域。GET 来源声明及其剩余额度属于单独通道，PUT 只能消费同一通道的声明。函数普通语句继承调用方通道，嵌套调用和保存后的 MUST 等待也保留归属；函数内部显式书写的 `channel` 块使用自己的词法作用域。通道不创建独立物理库存，也不启动并行线程；两个通道若指定同一个真实容器，仍会竞争该容器的实际内容。详见[通道语义](docs/loop-factory-channels.md)。

普通物流在部分接受或零接受后继续执行；`must` 物流把未完成数量保留在所属任务，直到足量结清。等待或阻塞的 MUST 只暂停当前任务，其他任务继续。

### 大宗物流与稳定性

大宗订单使用压缩批次和带检查的有符号 64 位数量，不会为每件物品展开一条指令。每个任务按实际接受量推进，独立保存背压与回收状态；一个任务等待时，其他可执行任务仍能继续。该机制覆盖跨 tick 的高数量物品、流体和 FE 物流，也处理真实能力背压与目的地饱和。已验证场景包括 4,096 轮存储往返、2,304 批 Mekanism 订单和 signed-long 边界转运；这些是回归压力负载，不代表无限 TPS 或任意第三方机器行为。

原生编辑器为关键字、字符串、数量、注释、标签、函数、运算符、资源、配方引用、SFM 触发器和自动折行文本着色。视口与光标使用原生 `MultiLineEditBox` 坐标体系，缩短或滚动代码不会产生虚假行。详见[代码高亮与注释](docs/loop-factory-highlighting.md)。

### 合成与网络服务

**合成撕裂者**：AE2 样板供应器界面，四排共 36 个槽位，接受工作台、锻造台和切石机样板。取料前验证完整配方链，执行前重新检查实时配方，以私有 CPU 库存和带检查的 `long` 数量一次提交结果。产物、容器返还、组件数据、副产物和循环种子都归属于原 CPU 任务。

**循环卡**：由 AE2 高级卡和一个循环晶体无序合成。在合成撕裂者上启用可编码的工作台、锻造台和切石机配方自动发现，已安装样板仍可移除；安装到便携磁盘后，可把存储 FE 转换为 AE 电量。便携磁盘同时有正 AE 电量和 FE 时，通过对应世代原生能量能力提供 FE。

**配方环解算终端**：无界面循环图服务，压缩强连通分量，锁定 CPU 内循环种子，严格按顺序提交批次；阻塞批次不能跳过，游标、剩余重复次数、种子储备和最终产物归属跨存档保留。

**超算合成优化接口**：独立的无界面无环图加速服务，发现可达供应器、合并物流路线、建模副产物与容器返还，并写入压缩的 AE2 合成计划。若溢出或整数计划无法证明，则在修改前放弃，让 AE2 接管。

### 循环存储磁盘

循环存储磁盘让所有注册到 AE2 的存储键共享一个字节预算，包含外壳、1k/4k/16k/64k/256k、1M/4M/16M/64M/256M 和无限档。

- K 档保留 AE2 等价容量和 63 类限制。
- M 档使用相当于原单类上限 63 倍的聚合池。
- 无限档取消数量和种类限制。
- 物品、流体、FE、魔力、魔源、灵魂及其他注册附属键按自身原生每字节数量计费。
- 十一个便携档复用 AE2 便携终端、电池、四个升级槽、充能速率、待机耗电和通电存取。
- 模糊、反向、平均分配、虚空和能量卡遵循 AE2 便携存储行为。
- 每代有 53 个获取配方和 11 个 AE2 原生拆解声明；Create 磨粉与 Mekanism 粉碎只在对应模组加载时通过 `c:gems/loop_crystal` 增加。

## 支持版本

| Minecraft | NeoForge | AE2 | Java |
| --- | --- | --- | --- |
| 1.21.1 | 21.1.235 | 19.2.17 | 21 |
| 26.1.2 | 26.1.2.94 | 26.1.10-beta | 25 |

不支持 Minecraft 1.20.1。两个世代使用独立 Gradle 工程、适配器、缓存、产物和运行验证。ImmortalStorage 是另一个项目，不是 AE2LF 的上游、依赖、打包类集合或子模块。

## 安装

按 Minecraft 世代选择对应文件：

```text
ae2lf-neoforge-mc1.21.1-0.0.5.jar
ae2lf-neoforge-mc26.1.2-0.0.5.jar
```

将文件放入对应实例的 `mods/`，同时安装匹配的 AE2 和 NeoForge。Create、Mekanism、Applied Flux、JEI、EMI 均为可选。一个实例不要同时安装两个世代的 AE2LF JAR。

已验证的 PCL 实例：

```text
C:/Users/12252/Desktop/Files/Minecraft/PCL/.minecraft/versions/AE2-lightoptimizer-1.21.1
C:/Users/12252/Desktop/Files/Minecraft/PCL/.minecraft/versions/AE2-lightoptimizer-26.1.2
```

这两个实例已部署 0.0.5 发布 JAR；部署只替换对应 AE2LF 文件，并逐项核对存档、配置、依赖和其他实例文件的字节与时间戳。

## 构建与测试

在仓库根目录运行对应封装脚本：

```powershell
powershell -NoProfile -File .\build-1.21.1.ps1 --offline --no-daemon build
powershell -NoProfile -File .\build-26.1.2.ps1 --offline --no-daemon build
```

已验收的 0.0.5 发布产物已与编译类和资源逐项比对，未包含嵌套 JAR、测试 probe 类或无关模组命名空间。

最新验证数据：

| 验证 | 1.21.1 | 26.1.2 |
| --- | ---: | ---: |
| 单元测试 | 262 通过 | 260 通过 |
| 失败 / 错误 / 跳过 | 0 / 0 / 0 | 0 / 0 / 0 |
| 原生终端刷新断言 | 67 | 67 |
| 连续红石上升沿检查 | 32 | 32 |
| 单次刷新替换的旧存档任务 | 18 | 18 |
| 原生高亮 framebuffer 检查页 | 11 | 11 |
| 原生滚动/光标像素检查 | 10 | 10 |

终端生命周期测试使用隐藏的真实 Minecraft 客户端和集成服务器、真实箱子、红石方块、AE2 网络、二进制 NBT 重载及生产 `FactoryJob`。部分资源回收场景会人工注入已保存缓存，以检查归属和序列化；这不等同于物理键鼠操作、任意第三方能力覆盖或新的长期压力活动。完整证据见 [archive/2026-09-13-channel-face/REPORT.md](archive/2026-09-13-channel-face/REPORT.md)。

## 兼容性与边界

- 机器物流按对应世代原生物品、流体、FE 和已支持化学品能力解析指定面；显式指定面不会静默回退到其他面。
- 未加载目标区块会进入可恢复等待或报告明确错误，不会伪造库存。
- 配方执行以服务端实时配方管理器、完整物品组件、实时标签和带检查的 64 位算术为准。
- `RETAIN`、`EACH`、`WITH`、`WITHOUT`、槽位范围、轮询、相对面等高级 SFM 子句，除非当前指南明确列出，否则不属于支持语法。
- 一台实体机器可以属于多个标签。标签和 channel 是不同概念：channel 隔离来源声明和额度，重叠标签仍然指向同一个真实库存。
- 已检查的用户存档中，输出箱 `(2,-60,8)` 同时属于 `Furnance`，且终端持久化了 18 个旧任务。当前 JAR 会在代码变化或红石上升沿清掉旧任务，但仍需在游戏中移除该输出箱的 `Furnance` 标签并保留 `out`。

## 仓库结构

```text
shared/                         与加载器无关的工厂、存储、编译器和测试
versions/neoforge-1.21.1/       NeoForge 21.1.235 适配器与产物
versions/neoforge-26.1.2/       NeoForge 26.1.2.94 适配器与产物
docs/                            通道、原生能力与编辑器行为说明
tools/                           构建、部署、原生 probe 和验证脚本
archive/                         不可变测试证据、部署记录和冷数据
OVERVIEW.md                      完整文件树与模块职责
taste.md                         跨世代实现和证据契约
```

## 项目状态

当前公开发行版为 0.0.5。源代码、产物、PCL 部署、存档保护和已知限制都记录在按日期归档的报告中，便于区分已实现行为、候选行为和未验证兼容性。

AE2LF 是独立仓库，使用自身 Git 历史和远端。后续贡献应保持 `shared`、两个 NeoForge 适配器、可选集成和测试 probe 之间的硬隔离。
