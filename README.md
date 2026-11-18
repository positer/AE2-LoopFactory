# AE2 LoopTech (AE2LpT) · 应用能源：循环科技

[English](#english) · [中文](#中文)

## English

**AE2LpT** extends Applied Energistics 2 with programmable factories, cross-dimensional ME links, shared storage and cyclic crafting. Start by powering an ME network. A small network can use an Energy Acceptor and cables, and machines that need channels follow AE2’s normal limits.

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

The Omnidirectional Connector joins loaded endpoints on a named public or creator-private channel across dimensions within one save. It consumes no idle AE and adds no chunk tickets. Mount it on a factory terminal or an upward-facing provider subnet port for wireless factory addressing. Loop Crystals, fragments and powder feed the machine and storage-core recipes.

With AE2 Crystal Science installed, craft one Loop Crystal Powder with one sand to obtain two Loop Crystal Seeds. Grow them in water or in Crystal Science’s growth chamber to obtain Purified Loop Crystals. Mix ordinary and purified crystals in any recipe that consumes Loop Crystals, including the 2×2 storage block. The Crystal Aggregator turns eight powder and 32 sand into 32 seeds for 51,200 AE. The Crystal Pulverizer grinds either crystal into one powder for 8,000 AE.

[Machine recipes](docs/loop-machines.md) · [Factory channels](docs/loop-factory-channels.md) · [Omnidirectional links](docs/omni-connector.md)

## 中文

**AE2LpT** 为应用能源 2 加入可编程工厂、跨维度 ME 连接、共享存储和循环合成。给 ME 网络供电后即可启动机器。小型网络可直接使用能源接收器和线缆，需要频道的设备遵循 AE2 的频道上限。

### 合成与循环机器

| 设备 | 用途 |
| --- | --- |
| 超算合成优化接口 | 为普通无环订单选择配方、核算副产物和容器返还，并按依赖顺序生成批次。接入后，本网络的默认普通线缆容量由 8 增至 16，致密线缆与控制器单面由 32 增至 64。多台接口共享一次加成。 |
| 配方环解算终端 | 处理相互依赖或能自我增殖的配方。例如复制模板时，保留第一份模板作种子，再按批次向 CPU 交付。批次进度、预留物料与输出归属可随存档恢复。 |
| 合成撕裂者与循环卡 | 撕裂者接受工作台、锻造台和切石机样板。插入循环卡后可自动发现可编码配方；有手动样板时，以该精确产物对应的手动选方为准。执行前复核实际材料、组件、返还物和种子。 |
| 循环物质聚合器 | 将分区标记好的循环磁盘放入模板槽，每个服务端 tick 从在线网络取料，并按原生模式生成物质球、奇点或销毁输入。反相卡排除标记物品，模糊卡扩大匹配范围。网络暂时拒收时，未交付的产物保留为物质余额。 |
| 循环进刻器 | 工作台默认合成完整方块，单件合成可在方块与线缆部件之间互换。方块的绿色顶面或部件的安装面指向唯一加速目标。默认每 tick 追加 1–4 次调用，四张加速卡可将上限提高至 64。多台对准同一目标时，各自追加所设倍率。 |

[机器合成表与操作](docs/loop-machines.md) · [合成撕裂者](docs/crafting-ripper-patterns.md) · [频道扩容](docs/optimizer-channel-capacity.md)

### 工厂网络与编程

用循环工厂样板编码面板或绑定网络的手持编码器编辑程序，把样板装入工厂网络终端或样板供应器。程序可按标签与指定面访问机器，处理物品、流体、FE 和受支持的原生能力。工厂接口线缆连接设备，供应器可划出独立子网。

工厂语言支持变量、条件、函数、等待、红石触发及配方输入输出引用。`get` 和 `put` 描述物流，`must` 保留未完成数量，`channel` 限定声明可见范围。等待的作业可跨卸载恢复。编辑器提供语法高亮和光标处插入 ID；安装受支持的 JEI 或 EMI 时，可拖入物品或流体条目。

[工厂频道与子网](docs/loop-factory-channels.md) · [编辑器与语法高亮](docs/loop-factory-highlighting.md)

### 存储与无线连接

- **循环存储磁盘**共用一个字节预算来容纳 AE2 已注册的资源类型。K 级保留 63 种类型限制，M 级采用总容量池，另有无限型。便携版使用 AE2 终端、电池及升级槽；搭配循环卡与 FE 存储支持可为便携盘补充 AE 电量。
- **全向连接器**把同一存档内、同名频道的已加载端点跨维度接入 ME 网。公开频道供所有玩家加入，私有频道由创建者使用。连接器本身零耗电，无需区块强加载。压在工厂网络终端或子网面朝上的工厂样板供应器上时，还可用于无线工厂寻址。[连接器与安装方式](docs/omni-connector.md)
- **循环水晶、碎片和粉末**构成工厂设备与存储核心的材料链。

安装水晶科技（AE2 Crystal Science）后，1 个循环水晶粉末与 1 份沙可合成 2 颗循环水晶种子。将种子放入水中或使用水晶科技的生长设备，成熟后得到高纯循环水晶。高纯与普通水晶可混合用于循环水晶块及其他消耗循环水晶的配方。水晶聚合器可用 8 粉末、32 沙和 51,200 AE 批量制作 32 颗种子；粉碎器消耗 8,000 AE 将任一种循环水晶还原为 1 粉末。
