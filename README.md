# AE2-LoopFactory (AE2LF)

AE2-LoopFactory is a NeoForge addon for Applied Energistics 2 (AE2). It provides programmable logistics, high-capacity universal storage, recipe-aware crafting services, and tools for writing, previewing, and executing factory programs. Each supported Minecraft generation has an isolated adapter, while language and planning contracts remain shared and loader-independent.

AE2LF is a standalone project. AE2 is the required platform. Create, Mekanism, Applied Flux, JEI, EMI, and other addon mods are optional integrations; AE2LF never bundles or requires them.

## Features

### Loop Factory network

The Loop Factory includes patterns, pattern providers, a handheld encoder, a pattern encoding panel, network terminals, and interface cables.

- Recipe-free terminals execute the indentation-based Loop Factory language on the server.
- Encoding panels edit factory code alongside ordinary AE2 pattern data.
- Handheld encoders bind to a factory network and provide the same workflow away from a panel.
- Interface cables connect factory machines to AE2 networks without consuming an AE2 channel.
- A provider can create a directed factory subnet. AE power crosses the provider boundary; storage, labels, declarations, and execution remain scoped to the connected subnet.
- Server-side jobs save their continuation, waits, function frames, quotas, buffers, and recovery records with the block entity.

### Factory language

Programs use four-space indentation and support:

- `import`, `get`, `put`, `has`, `if`, `else`, `while`, `wait`, `redstone`, `func`, `done`, `break`, and `channel`;
- item, fluid, FE, chemical, mana, source, soul, and other identities exposed by AE2 or native capabilities;
- exact IDs, tags, wildcards, exclusions, comparisons, boolean conditions, and explicit machine faces;
- nested functions and saved continuations with bounded recursion;
- recipe references such as `P1`, `P2`, `O1`, `O2`, and aggregate `P` and `O` sets;
- `//` comments through the end of a physical line. Quoted text remains text, and supported legacy comment forms continue to work.

Quantities use checked signed 64-bit arithmetic. Invalid syntax, overflow, unavailable capabilities, and unsupported selectors fail explicitly instead of fabricating a transfer.

### Channel scopes and isolation

`channel` is a lexical logistics scope, not a second inventory or a parallel thread.

- GET declarations and their remaining quotas belong to their declaration channel.
- PUT can consume only declarations visible in the same channel.
- Ordinary function calls inherit the caller's channel, including nested calls and suspended or restored `must` operations.
- An explicit nested `channel` block creates a separate lexical scope.
- Two channels that name the same physical container still compete for that container's real contents. Tags identify machines; they do not copy inventories.
- A blocked or waiting job pauses its own continuation. Independent jobs continue when their owners are eligible to tick.

See [`docs/loop-factory-channels.md`](docs/loop-factory-channels.md) for the routing contract.

### Transfer behavior and large orders

Normal transfers advance by the amount a capability accepts, including partial or zero acceptance. A `must` transfer retains its unfinished amount in the owning job until fulfilled or explicitly cancelled.

Large orders use compressed batches instead of one instruction per item. Each job keeps its own backpressure, pending amount, and recovery state. The scheduler yields across ticks so one saturated destination does not serialize unrelated jobs. Item, fluid, FE, and supported native-capability routes use the generation's real simulation and execution APIs.

Unloaded target chunks are unavailable. A job waits for a valid tickable owner or reports a recoverable capability error; AE2LF does not load chunks, renew tickets, or invent inventories.

### Terminal refresh and redstone

Recipe-free terminal execution refreshes when the installed program changes, a pattern is replaced or removed, or the connected factory network receives a redstone rising edge. The refreshed program starts at its first instruction on the next eligible server tick.

- A held redstone signal does not restart the program every tick.
- Saving identical code or editing only an unsaved draft preserves the current continuation.
- Refresh clears old waits, `must` debt, declarations, function returns, errors, and pending output pulses.
- Actual buffered resources and opaque native rollback records move to a persistent recovery queue before cancellation. Expected output debt is never materialized as cargo, and delivered resources are never rolled back.
- A pending refresh is saved with the host, so unloading the chunk does not lose the request.

Recipe-provider jobs have their own lifecycle and are not reset by terminal-only refresh rules.

### Editor and ID insertion

The native editor highlights keywords, strings, quantities, comments, labels, functions, operators, resources, faces, recipe references, SFM triggers, and wrapped text. Its caret, selection, scrolling, and viewport use the native text-widget coordinate system, keeping the visible source range aligned with the actual source range.

When the code area is focused, a carried GUI stack can insert resource IDs at the caret:

- Ordinary items insert their own item ID for either mouse button.
- A container's left click inserts the container ID.
- A container's right click inserts IDs for its visible non-empty contents.
- Only the item currently carried by the GUI is inspected. It is not consumed, drained, or modified.
- Container contents are read through guarded, read-only native capabilities and deduplicated before insertion.

JEI and EMI item or fluid entries can be dragged into the code area when the corresponding optional viewer is loaded. Viewer handlers share the same caret replacement and source-size limits.

### Recipe-aware patterns and crafting services

Factory patterns can carry ordinary AE2 recipe data and factory code together. When a recipe is present, the pattern exposes its normal AE2 target preview and recipe output even if factory code is absent or invalid. Factory code can refer to recipe inputs and outputs through indexed or aggregate `P` and `O` selectors.

**Crafting Ripper** is a pattern-provider variant for crafting-table, smithing-table, and stonecutting patterns. It validates the complete selected recipe chain before extracting material, rechecks live recipes at execution time, accounts for components, container returns, byproducts, and cyclic seeds, then commits the result once to the originating AE2 CPU job.

**Loop Card** enables automatic discovery of encodable crafting, smithing, and stonecutting recipes in the Ripper while keeping installed patterns removable. In a portable Loop Storage Cell it can convert stored FE into AE charge; when both FE and AE charge are available, the cell exposes its FE through the generation's native item-energy capability.

**Recipe Ring Solver Terminal** handles cyclic crafting graphs. It condenses strongly connected components, reserves cyclic seeds in the selected CPU, and dispatches compressed batches in dependency order. A blocked batch cannot be skipped, and its cursor, repetitions, seed reserve, and output ownership persist across reloads.

**Supercomputing Crafting Optimizer Interface** accelerates ordinary acyclic graphs. It discovers reachable producers, combines routes, models byproducts and container remainders, and declines before mutation when overflow or a valid integer plan cannot be proven, allowing AE2 to retain control.

### Loop Storage Cells

Loop Storage Cells use one byte budget across AE2-registered storage key types. The family includes a housing, finite K and M tiers, an infinite tier, and matching portable cells.

- K tiers retain AE2-style capacity and the 63-type limit.
- M tiers use an aggregate pool based on the original single-type ceiling.
- The infinite tier removes amount and type limits.
- Items, fluids, FE, mana, source, soul, and other registered addon keys use their native amount-per-byte contract.
- Portable cells reuse the AE2 portable terminal, battery, charge behavior, idle drain, four upgrade slots, and powered insertion/extraction.
- Fuzzy, inverter, equal-distribution, void, and energy upgrades follow corresponding AE2 portable-storage behavior.
- Storage, portable-cell, disassembly, and optional integration recipes are data-driven. Create milling and Mekanism crushing are added only when those mods are loaded.

## Compatibility model

AE2LF keeps version-specific code in separate adapters and shares loader-independent contracts, planners, selectors, and tests. The active server recipe manager, live tags, complete item components, and native capability APIs are authoritative.

- Install exactly one AE2LF artifact matching the target Minecraft and NeoForge generation.
- Install the matching AE2 release and satisfy its platform requirements.
- Optional integrations activate only when their owning mod and compatible API are present.
- Explicit machine faces are honored; a missing face capability is not silently redirected.
- A machine may belong to multiple tags. Tags and channels remain separate concepts, so overlapping tags can name one physical inventory.
- Unsupported SFM clauses, arbitrary third-party capability semantics, and behavior absent from the current guide are reported as unsupported rather than approximated.

## Installation

Download the artifact for the target Minecraft generation from [GitHub Releases](https://github.com/positer/AE2-LoopFactory/releases). Put it in the instance's `mods/` directory with the matching AE2 and NeoForge files. Keep generation-specific artifacts in separate instances; never install two generations together.

Create, Mekanism, Applied Flux, JEI, EMI, and other integrations are optional. When an integration is absent, its recipes and viewer hooks are unavailable while the core factory, storage, and AE2 services remain loadable.

## Documentation

- [`docs/loop-factory-channels.md`](docs/loop-factory-channels.md): channel scope, function inheritance, quotas, and physical-inventory boundaries.
- [`docs/loop-factory-highlighting.md`](docs/loop-factory-highlighting.md): syntax highlighting, comments, caret behavior, and editor range rules.
- Generation-specific native capability references under [`docs`](docs/).
- AE2 GuideME pages shipped with each adapter: in-game reference for blocks, patterns, language examples, and supported boundaries.
- [`CHANGELOG.md`](CHANGELOG.md): release-specific additions, fixes, and compatibility notes.

## Development layout

```text
shared/                         Loader-independent language, planners, storage contracts, and tests
versions/neoforge-*/             Version-specific NeoForge adapters, resources, recipes, and artifacts
docs/                            Stable behavior and compatibility references
tools/                           Build, provisioning, native probes, and verification utilities
archive/                         Dated engineering evidence and historical reports
OVERVIEW.md                      Detailed file-tree and project responsibility map
taste.md                         Cross-version implementation and evidence conventions
```

Build each maintained generation with its own wrapper from the repository root:

```powershell
powershell -NoProfile -File .\build-<generation>.ps1 --offline --no-daemon build
```

The project aims for deterministic server-authoritative execution, explicit capability boundaries, persistent job ownership, and measured performance improvements without promising unlimited throughput or compatibility with arbitrary third-party behavior.

## 中文说明

AE2-LoopFactory（AE2LF）是 Applied Energistics 2（AE2）的 NeoForge 附属模组，提供可编程物流、高容量通用存储、配方感知合成服务，以及用于编写、预览和执行工厂程序的工具。项目为每个受支持的 Minecraft 世代维护独立适配层，同时共享与加载器无关的规划器和语言逻辑。

AE2LF 是独立项目。AE2 是核心前置；Create、Mekanism、Applied Flux、JEI、EMI 及其他附属模组均为可选兼容。AE2LF 不打包第三方模组，也不要求这些模组才能加载。

## 功能

### 循环工厂网络

循环工厂由样板、样板供应器、手持编码器、样板编码面板、网络终端和接口线缆组成。

- 无配方终端在服务端执行四空格缩进的循环工厂语言。
- 样板编码面板在普通 AE2 样板配方数据旁编辑工厂代码。
- 手持编码器绑定工厂网络，在面板之外提供相同的编辑流程。
- 接口线缆连接工厂机器与 AE2 网络，但不占用 AE2 频道。
- 样板供应器可沿箭头方向建立工厂子网。AE 电力可以跨供应器边界传递，存储、标签、声明和工厂执行状态仍限定在各自子网。
- 执行状态属于服务端主机，并随方块实体保存；续点、等待、函数栈、额度、缓存和回收记录可跨存档保存与恢复。

### 工厂语言

程序使用四个空格缩进，支持：

- `import`、`get`、`put`、`has`、`if`、`else`、`while`、`wait`、`redstone`、`func`、`done`、`break`、`channel`；
- 当前 AE2 或原生能力系统提供的物品、流体、FE、化学品、魔力、魔源、灵魂及其他资源身份；
- 精确资源 ID、标签、通配符、反选、比较、布尔条件和指定机器面；
- 嵌套函数与保存后的续点，递归深度有上限；
- `P1`、`P2`、`O1`、`O2` 以及聚合 `P`、`O` 配方引用；
- `//` 注释本行后续内容。引号内的文本仍是字符串，已支持的旧注释形式继续有效。

资源数量使用带检查的有符号 64 位算术。语法错误、溢出、能力不可用和不支持的选择器都会明确失败，不会伪造物流。

### channel 作用域与隔离

`channel` 是词法物流作用域，不是第二个库存，也不是并行线程。

- GET 声明及其剩余额度属于声明所在的 channel。
- PUT 只能消费同一 channel 中可见的声明。
- 普通函数调用继承调用方 channel，包括嵌套调用以及暂停/恢复后的 `must` 操作。
- 显式嵌套的 `channel` 块建立独立词法作用域。
- 两个 channel 若指定同一个真实容器，仍会竞争该容器的实际内容。标签用于识别机器，不会复制库存。
- 等待或阻塞的任务只暂停自身续点；其他任务在其所有者满足 tick 条件时继续执行。

详见 [`docs/loop-factory-channels.md`](docs/loop-factory-channels.md)。

### 物流行为与大宗订单

普通物流按能力实际接受量推进，包括部分接受或零接受。`must` 物流会把未完成数量保留在所属任务，直到需求完成或任务被明确取消。

大宗订单使用压缩批次，不会为每件物品展开一条指令。每个任务独立保存背压、欠额和回收状态；一个目的地饱和不会让无关任务串行等待。物品、流体、FE 和已支持原生能力路线使用对应世代真实的模拟与执行接口。

未加载目标区块被视为不可用。任务会等待可执行的所有者或报告可恢复的能力错误；AE2LF 不会加载区块、续租票据或凭空创建库存。

### 终端刷新与红石

无配方终端在已安装程序变化、样板替换/移除或工厂网络收到红石上升沿时刷新执行。刷新后的程序在下一个有效服务端 tick 从第一条指令开始。

- 持续高电平不会每 tick 重启程序。
- 保存相同代码或只修改未保存草稿会保留当前续点。
- 刷新会清除旧等待、`must` 欠额、声明、函数返回、错误和待处理输出脉冲。
- 实际缓存资源和不透明原生回滚记录会在取消前进入持久化回收区；预期输出欠额不会变成实物，已经送达的资源不会回滚。
- 待刷新请求会随主机保存，区块卸载不会丢失。

带配方的供应器任务拥有独立生命周期，不受终端专属刷新规则重置。

### 编辑器与 ID 填入

原生编辑器为关键字、字符串、数量、注释、标签、函数、运算符、资源、方向、配方引用、SFM 触发器和自动折行文本着色。光标、选区、滚动和视口遵循原生文本控件坐标，显示源码范围与实际源码范围保持一致。

代码区获得焦点时，可从 GUI 鼠标拿起的物品在光标处填入资源 ID：

- 普通物品左右键都填入本体物品 ID。
- 容器左键填入容器 ID。
- 容器右键填入其中实际可见且非空内容的 ID。
- 只检查当前 GUI 拿起的物品，不消耗、不抽取、不修改物品。
- 容器内容通过受保护的只读原生能力读取，插入前去重。

加载对应可选查看器后，可将 JEI 或 EMI 的物品/流体条目拖入代码区。查看器处理与普通 ID 填入共用光标替换和源码长度限制。

### 配方样板与合成服务

工厂样板可以同时保存普通 AE2 配方数据和工厂代码。存在配方时，即使工厂代码缺失或非法，样板仍显示普通 AE2 合成目标预览和配方产物。工厂代码可以使用索引或聚合 `P`、`O` 选择器引用配方输入和输出。

**合成撕裂者**是支持工作台、锻造台和切石机样板的样板供应器变体。它在取料前验证完整配方链，在执行时重新检查实时配方，处理组件、容器返还、副产物和循环种子，并将结果一次提交到原 AE2 CPU 任务。

**循环卡**让合成撕裂者自动发现可编码的工作台、锻造台和切石机配方，同时保留已安装样板的移除能力。安装到便携循环存储磁盘后，可将存储 FE 转换为 AE 电量；当 FE 与 AE 电量同时存在时，磁盘通过对应世代的原生物品能量能力提供 FE。

**配方环解算终端**处理循环合成图，压缩强连通分量，在选定 CPU 中保留循环种子，并按依赖顺序派发压缩批次。阻塞批次不能跳过，游标、重复次数、种子储备和产物归属可跨重载保存。

**超算合成优化接口**加速普通无环合成图，发现可达供应器、合并物流路线、建模副产物和容器返还；若无法证明无溢出或有效整数计划，则在修改前放弃，让 AE2 接管。

### 循环存储磁盘

循环存储磁盘让所有注册到 AE2 的存储键共享一个字节预算，包含外壳、有限 K/M 档、无限档和对应便携磁盘。

- K 档保留 AE2 风格容量和 63 种类型限制。
- M 档使用基于原单类上限的聚合池。
- 无限档取消数量和种类限制。
- 物品、流体、FE、魔力、魔源、灵魂及其他附属键按自身原生每字节数量计费。
- 便携磁盘复用 AE2 便携终端、电池、充能行为、待机耗电、四个升级槽和通电存取。
- 模糊、反向、平均分配、虚空和能量升级遵循对应 AE2 便携存储行为。
- 存储、便携磁盘、拆解和可选联动配方均为数据驱动；Create 磨粉与 Mekanism 粉碎仅在对应模组加载时添加。

## 兼容模型

AE2LF 将版本相关代码放在独立适配层，只共享与加载器无关的契约、规划器、选择器和测试。服务端实时配方管理器、实时标签、完整物品组件和原生能力 API 是权威来源。

- 只安装与目标 Minecraft 和 NeoForge 世代匹配的 AE2LF 文件。
- 同时安装匹配的 AE2，并满足 AE2 自身的平台要求。
- 可选联动仅在对应模组及兼容 API 存在时启用。
- 显式指定机器面会严格遵守；指定面没有能力时不会静默改用其他面。
- 一台机器可以属于多个标签。标签与 channel 仍是不同概念，重叠标签可能指向同一个真实库存。
- 未列入当前指南的高级 SFM 子句、任意第三方能力语义和行为会明确报告为不支持，不会近似执行。

## 安装

从 [GitHub Releases](https://github.com/positer/AE2-LoopFactory/releases) 下载目标 Minecraft 世代的文件，将其放入实例的 `mods/` 目录，并同时放入匹配的 AE2 与 NeoForge 文件。不同世代必须使用不同实例，不要在同一实例中安装两个世代的 AE2LF。

Create、Mekanism、Applied Flux、JEI、EMI 及其他联动均为可选。缺少联动时，对应配方和查看器入口不可用，但核心工厂、存储和 AE2 服务仍可加载。

## 文档

- [`docs/loop-factory-channels.md`](docs/loop-factory-channels.md)：channel 作用域、函数继承、额度和真实库存边界。
- [`docs/loop-factory-highlighting.md`](docs/loop-factory-highlighting.md)：代码高亮、注释、光标行为和编辑器范围规则。
- [`docs`](docs/)：各世代原生能力说明。
- 各适配层随包提供的 AE2 GuideME 页面：方块、样板、语言示例和支持边界的游戏内参考。
- [`CHANGELOG.md`](CHANGELOG.md)：逐版本新增内容、修复和兼容说明。

## 开发结构

```text
shared/                         与加载器无关的语言、规划器、存储契约和测试
versions/neoforge-*/             各世代 NeoForge 适配器、资源、配方和产物
docs/                            稳定行为与兼容说明
tools/                           构建、部署、原生 probe 和验证工具
archive/                         按日期保存的工程证据和历史报告
OVERVIEW.md                      详细文件树和项目职责
taste.md                         跨世代实现与证据约定
```

在仓库根目录使用各世代自己的封装脚本构建：

```powershell
powershell -NoProfile -File .\build-<generation>.ps1 --offline --no-daemon build
```

项目目标是服务端权威执行、明确的能力边界、可持久化的任务归属和经过测量的性能改进；不承诺无限吞吐，也不承诺任意第三方行为都能兼容。
