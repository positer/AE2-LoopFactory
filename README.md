# AE2 LoopTech · 应用能源：循环科技

**AE2LT** 是 Applied Energistics 2 的 NeoForge 附属。你可以用一张样板给工厂编写物流程序，把跨维度的 ME 网络连在一起，或让合成 CPU 批量完成需要保留种子的循环配方。

[中文介绍](#中文介绍) · [English](#english) · [机器配方 / Machine recipes](docs/loop-machines.md) · [更新记录 / Changelog](CHANGELOG.md)

## 中文介绍

### 从一个 ME 网络开始

安装与你的 Minecraft 版本相符的 NeoForge、AE2 和一份 AE2LT。给网络供电后，即可从无控制器的小型网络开始；需要频道的设备仍遵循 AE2 原生分配。两代制品分别构建。此前使用 AE2LF 制品的实例，更新时只安装一份对应版本的 AE2LT JAR。存档和数据包继续使用注册域 `ae2lightoptimizer`。

| Minecraft | 实测 NeoForge | 实测 AE2 | Java | 本地构建制品 |
| --- | --- | --- | --- | --- |
| 1.21.1 | 21.1.235 | 19.2.17 | 21 | `ae2lt-neoforge-mc1.21.1-0.0.9.jar` |
| 26.1.2 | 26.1.2.126 | 26.1.13-beta | 25 | `ae2lt-neoforge-mc26.1.2-0.0.9.jar` |

### 合成与循环机器

| 设备 | 用途 |
| --- | --- |
| 超算合成优化接口 | 为普通无环订单选择配方、核算副产物和容器返还，并按依赖顺序生成批次。接入后，本网络的默认普通线缆容量由 8 增至 16，致密线缆与控制器单面由 32 增至 64。多台接口共享一次加成。 |
| 配方环解算终端 | 处理相互依赖或能自我增殖的配方。例如复制模板时，保留第一份模板作种子，再按批次向 CPU 交付。批次进度、预留物料与输出归属可随存档恢复。 |
| 合成撕裂者与循环卡 | 撕裂者接受工作台、锻造台和切石机样板。插入循环卡后可自动发现可编码配方；有手动样板时，以该精确产物对应的手动选方为准。执行前复核实际材料、组件、返还物和种子。 |
| 循环物质聚合器 | 将分区标记好的循环磁盘放入模板槽，每个服务端 tick 从在线网络取料，并按原生模式生成物质球、奇点或销毁输入。反相卡排除标记物品，模糊卡扩大匹配范围。网络暂时拒收时，未交付的产物保留为物质余额。 |
| 循环进刻器 | 工作台默认合成完整方块，单件合成可在方块与线缆部件之间互换。方块的绿色顶面或部件的安装面指向唯一加速目标。默认每 tick 追加 1–4 次调用，四张加速卡可将上限提高至 64。多台对准同一目标时，各自追加所设倍率。 |

[机器合成表与操作](docs/loop-machines.md) · [合成撕裂者](docs/crafting-ripper-patterns.md) · [频道扩容](docs/optimizer-channel-capacity.md) · [逐 tick 成本与算法实测](docs/loop-machine-performance.md)

### 工厂网络与编程

用循环工厂样板编码面板或绑定网络的手持编码器编辑程序，把样板装入工厂网络终端或样板供应器。程序可按标签与指定面访问机器，处理物品、流体、FE 和受支持的原生能力。工厂接口线缆连接设备，供应器可划出独立子网。

工厂语言支持变量、条件、函数、等待、红石触发及配方输入输出引用。`get` 和 `put` 描述物流，`must` 保留未完成数量，`channel` 限定声明可见范围。等待的作业可跨卸载恢复。编辑器提供语法高亮和光标处插入 ID；安装受支持的 JEI 或 EMI 时，可拖入物品或流体条目。

[工厂频道与子网](docs/loop-factory-channels.md) · [编辑器与语法高亮](docs/loop-factory-highlighting.md)

### 存储与无线连接

- **循环存储磁盘**共用一个字节预算来容纳 AE2 已注册的资源类型。K 级保留 63 种类型限制，M 级采用总容量池，另有无限型。便携版使用 AE2 终端、电池及升级槽；搭配循环卡与 FE 存储支持可为便携盘补充 AE 电量。
- **全向连接器**把同一存档内、同名频道的已加载端点跨维度接入 ME 网。公开频道供所有玩家加入，私有频道由创建者使用。连接器本身零耗电，无需区块强加载。压在工厂网络终端或子网面朝上的工厂样板供应器上时，还可用于无线工厂寻址。[连接器与安装方式](docs/omni-connector.md)
- **循环水晶、碎片和粉末**构成工厂设备与存储核心的材料链。Create、Mekanism、Applied Flux、JEI、EMI 等按版本提供可选配方、能力或界面集成。

## English

AE2 LoopTech is a NeoForge addon for Applied Energistics 2. Program factory logistics, link loaded ME endpoints across dimensions, and plan large crafting orders with shared ingredients, byproducts or cyclic seeds. Install one AE2LT JAR for your Minecraft version alongside AE2. The table above lists the tested loader, AE2 and Java versions. To update an existing AE2LF installation, replace its JAR with the matching AE2LT build. Saves and data packs continue to use the `ae2lightoptimizer` registry namespace. A powered controllerless ME network can operate the machines within AE2’s normal channel limits.

### Crafting and machines

| Machine | What it does |
| --- | --- |
| Supercomputing Crafting Optimizer Interface | Plans acyclic orders, accounts for byproducts and returned containers, and schedules batches in dependency order. On its grid, the default cable limit doubles from 8 to 16 and dense cable/controller-face capacity from 32 to 64. The capacity bonus applies once per network. |
| Recipe Ring Solver Terminal | Handles cycles such as template duplication. It retains a seed, reserves the required materials and tracks batches on the originating crafting CPU across reloads. |
| Crafting Ripper and Loop Card | Runs supported crafting-table, smithing and stonecutting patterns. A Loop Card discovers encodable recipes. Manual patterns select the route for their exact output. The Ripper checks ingredients, components, remainders and seeds before committing a chain. |
| Loop Matter Condenser | Reads a partitioned Loop Storage Cell every server tick and feeds marked items into AE2’s matter-ball, singularity or trash mode. Inverter and fuzzy upgrades change the filter. If the network refuses an output, the corresponding matter credit remains available for retry. |
| Loop Tick Accelerator | The shaped recipe produces the full block. Craft it alone to obtain the cable part, or craft the part alone to recover the block. The green top or mounted face accelerates only its adjacent target. Select 1–4 extra ticks by default or up to 64 with four acceleration cards. Accelerators aimed at one target contribute independently. |

### Programmable logistics

Write a factory pattern in the encoding panel or a bound handheld encoder, then install it in a network terminal or pattern provider. Interface cables connect factory machines; directional providers can create factory subnets. Address machines by tag and face, and route items, fluids, FE or supported native resources.

The language supports variables, conditions, functions, waits, redstone triggers and recipe input/output references. A `must` transfer tracks its unfulfilled amount; a `channel` scopes the declarations a transfer can use. Jobs retain their continuation across unloads. The native editor highlights code, inserts resource IDs at the caret, and accepts item or fluid entries from supported JEI/EMI integrations.

### Storage, links and materials

Loop Storage Cells share one byte budget across registered AE2 key types. K-tier cells retain the 63-type limit, M-tier cells pool capacity, and an infinite tier is available. Portable cells use AE2’s terminal, battery and upgrade slots. With FE storage support, a Loop Card can convert stored FE into portable AE charge.

The Omnidirectional Connector joins loaded endpoints on a named public or creator-private channel across dimensions within one save. It consumes no idle AE and adds no chunk tickets. Mount it on a factory terminal or an upward-facing provider subnet port for wireless factory addressing. Loop Crystals, fragments and powder feed the machine and storage-core recipes. Integrations with Create, Mekanism, Applied Flux and recipe viewers are optional and version-specific.

### Build and reference

Run `./build-1.21.1.ps1` or `./build-26.1.2.ps1` from the repository root. Each adapter writes to its own `versions/neoforge-<version>/build/libs/` folder. The in-game AE2 guide covers machine use. The repository includes [machine recipes](docs/loop-machines.md), [factory logistics](docs/loop-factory-channels.md), [wireless links](docs/omni-connector.md), [algorithm measurements](docs/loop-machine-performance.md) and the [project overview](OVERVIEW.md).
