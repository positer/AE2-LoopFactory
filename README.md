# AE2 LoopTech (AE2LpT) · 应用能源：循环科技

## English

AE2 LoopTech adds programmable factories, cross-dimensional ME connections, shared storage cells and cyclic crafting to Applied Energistics 2. Start with a powered ME network and attach a terminal to request crafting or inspect storage. A small network can run on an Energy Acceptor and cables. Machines that need a channel still follow AE2’s allocation rules.

### Loop Crystals and materials

- Combine four Loop Crystal Fragments in any arrangement to make one Loop Crystal. Break down one crystal to recover four fragments. A second recipe alternates four Certus Quartz Crystals in the corners and four Loop Crystal Fragments on the edges of a crafting grid, with the center empty, to make four Loop Crystals.
- Arrange four Loop Crystals in a 2×2 square to make a Loop Crystal Block. Process a crystal in an Inscriber to make Loop Crystal Powder. Create milling and Mekanism crushing also produce the powder when available.

### Shared and portable storage

Insert a Loop Storage Cell in an ME Drive or compatible ME Chest. A cell shares its byte budget among registered AE2 resources. Items and fluids can coexist, as can FE, mana, source or other resources supplied by compatible addons. Each resource uses its registered amount-per-byte scale.

| Tiers | Capacity rule |
| --- | --- |
| 1k, 4k, 16k, 64k, 256k | The same nominal byte capacity as the matching AE2 item cell, with up to 63 distinct types across all resources |
| 1M, 4M, 16M, 64M, 256M | A pooled capacity of 66,060,288 to 16,911,433,728 bytes, with all registered resources sharing the pool |
| Infinite | Unlimited shared capacity and types |

A finite cell can be made from its matching Loop Storage Core and a Loop Storage Cell Housing. To make an Infinite Loop Storage Cell, drop 64 **256M Loop Storage Cores** and one **Loop Storage Cell Housing** together and expose them to a TNT explosion.

The housing uses glass in the four corners, iron ingots at middle left and right, and Loop Crystal Powder at top and bottom center. Leave its center empty. To make the 1k core, keep iron at the corners, put Certus Quartz in the center and put fragments at all four edge centers. For 4k through 256k, put three cores of the previous tier at top center, middle left and middle right, with powder at bottom center. For 1M through 256M, use netherite corners, a Singularity at the center, a Loop Crystal at bottom center, and three previous-tier cores in the remaining edge positions.

For portable access, combine an ME Chest and an Energy Cell with the matching Loop Storage Cell. Finite tiers also accept the matching core and housing in place of the assembled cell. The portable cell opens its own ME storage screen and uses a rechargeable AE battery. Energy Cards expand the battery. Sneak-use an empty portable cell to disassemble it and recover the parts and remaining charge. Where FE storage is available, install a Loop Card to use stored FE to recharge the portable cell. A Loop Card is a shapeless combination of an Advanced Card and a Loop Crystal.

### Crafting orders

- **Supercomputing Crafting Optimizer Interface.** Connect it to the grid that serves your crafting CPU. The interface uses one channel and 8 AE/t for its active crafting service. It plans acyclic orders in dependency order and accounts for shared ingredients, byproducts and container returns. The grid gets one capacity bonus even with multiple interfaces. Under default rules, the controllerless limit and ordinary cable capacity rise from 8 to 16, and dense cable and controller-face capacity rise from 32 to 64. The network still needs power.
- **Recipe Ring Solver Terminal.** Connect it to the crafting grid to handle supported cyclic recipes, such as duplicating a smithing template. Keep an initial template in stock and request the desired gain through the ME Crafting Terminal. The solver reserves the seed and external ingredients, then tracks batches and results on the originating CPU. It uses one channel and 2 AE/t and works through the normal crafting request screen.
- **Crafting Ripper.** Open it and insert crafting-table, smithing or stonecutting patterns. Its four-row window scrolls as the inventory grows. Request an item through the ME Crafting Terminal. The Ripper checks the whole chain and real inputs, then executes supported chains in one tick with container returns intact. It occupies a channel and charges no separate AE for the chain. For example, encode planks from logs and sticks from planks, then request sticks using logs already in storage.
- **Automatic patterns and imports.** Install a Loop Card in the Ripper to advertise supported recipes automatically. A manual pattern controls the exact output it encodes, including item components. If that route lacks materials, the manual selection remains in force. The down-arrow button moves supported encoded patterns from ME storage and installed slots on the same network. Leave the screen open until the import finishes. Already moved patterns stay in the Ripper if the operation stops.

### Loop machines

- **Loop Tick Accelerator.** Aim the block’s green top at the target. It can face any of the six directions. Craft the block alone to turn it into a cable part, or craft the part alone to get the block back. A mounted part targets the block directly in front of its mounted face. Right-click with an empty hand to open the rate controls. An online accelerator adds 1–4 calls per tick by default. Each Acceleration Card doubles the upper limit, up to 64 with four cards. Multiple block or part accelerators add their selected rates. Two units set to 2 and 3 add five calls to an eligible target each tick alongside its normal ticking. Actual throughput also depends on the target’s inputs, power and internal limits.
- **Loop Matter Condenser.** Use a Cell Workbench to partition a Loop Storage Cell with the items you want to process. Place the cell in the condenser’s template slot, connect an active ME channel and choose the native matter-ball, singularity or trash mode. It takes matching items from ME storage every server tick and returns products to the network. An Inverter Card on the template cell excludes marked items and a Fuzzy Card enables fuzzy matching. If the network cannot take a product, the condenser retains its matter credit for later. You can also use the native manual inputs.

### Programmable Loop Factory

A Blank Pattern and one Loop Crystal Powder make a Loop Factory Pattern. One Quartz Fiber, four iron ingots and two powder make eight Interface Cables. Both recipes are shapeless.

1. **Choose a controller for your program.** Use a Loop Factory Network Terminal for recipe-free logistics. For an on-demand processing recipe, connect a Loop Factory Pattern Provider to the main ME network with its arrows pointing toward an independent factory subnet. AE power crosses the provider boundary. Each side keeps its own storage and channels. Loop Factory Interface Cables connect the factory machines without consuming a device channel.
2. **Encode your pattern.** Use the Loop Factory Pattern Encoding Panel to switch between the recipe and code pages, or use the Handheld Loop Factory Encoder to edit code. The handheld binds to a factory network when used on its cable, controller or terminal. Use it on a provider to bind the subnet on the arrow side. Sneak-use the encoder to clear its binding, or sneak-use the pattern itself to clear its recipe and code.
3. **Tag the machines.** Declare names with `import`, then hold Tab and scroll with the bound encoder to choose a tag. Use the encoder on a machine to bind it. Ctrl-use also marks connected machines of the same block type. A tag can address multiple machines, and a transfer’s quantity applies across the entire group.
4. **Save and run.** Put a recipe-free pattern in the Network Terminal, or put a recipe-bound pattern in the Pattern Provider. The panel can upload the physical pattern to a provider on the main network. Fix any editor error before replacing a saved program. A changed terminal program or a redstone rising edge restarts terminal execution. Waits and unfinished transfers can resume when chunks reload.

This recipe-free example transfers up to 64 iron ingots from machines tagged `Input` to machines tagged `Output`. Give each tag at least one reachable inventory before running it.

```text
import Input,Output
get 64 minecraft:iron_ingot from Input
put 64 minecraft:iron_ingot into Output
done
```

- `get` declares a source and its quota. `put` performs the transfer. `storage` selects the accessible ME storage. Add `on up`, `on down`, `on north`, `on south`, `on east` or `on west` to target a particular face. Omit the quantity to move all matching resources within the source quota.
- `must` waits for the full required quantity across partial transfers. An ordinary transfer moves what it can and continues. Use `wait 20 tick` to delay a program and `redstone Tag 20 tick` to pulse a tagged group. The indentation language also supports conditions, loops, functions and `channel` blocks. Each block has its own source declarations and quotas. Groups that address the same machine still share its physical inventory.
- In a recipe-bound provider job, `source` holds the CPU’s allocated inputs. Send those inputs to the machine, wait for the actual processing output and return it with `put ... into source`. Use `P1` and `O1` for the first recipe ingredient and product, or `P` and `O` for the complete input and output sets. Blocking mode starts the next batch once the current program and its material settlement finish.
- Programs can route items, fluids, FE and supported machine resources. Select by exact resource ID, tags such as `#minecraft:planks`, or wildcards. The editor supports syntax highlighting and `//` line comments in both its indentation and SFM modes. Supported JEI or EMI entries can be dragged into the code. With Applied Flux, an induction card provides up to 1,000,000 FE in the provider’s source.

For on-demand smelting, encode one cobblestone into one stone as a processing recipe, tag a fueled furnace as `Furnace`, and put this pattern in the factory provider. A crafting request sends the input and waits for the resulting stone.

```text
import Furnace
get P1 from source
put P1 into Furnace on up
get must 1 O1 from Furnace on down
put O1 into source
done
```

In SFM mode, the following recipe-free program checks the tagged containers once per second and moves one iron ingot when available.

```text
NAME "Iron transfer"
EVERY 20 TICKS DO
    INPUT 1 minecraft:iron_ingot FROM Input
    OUTPUT 1 minecraft:iron_ingot TO Output
END
```

### Omnidirectional connections

1. Shapelessly combine a Wireless Access Point, a Loop Crystal and a Quantum Entangled Singularity to make four Omnidirectional Connectors.
2. Place a connector at each endpoint and attach the desired ME network. Right-click, enter a name and create or join a channel. The lock switch chooses visibility. Public channels are open to everyone, and private channels are available to their creator.
3. Select the same channel at another loaded endpoint, including one in another dimension of the same world. The connectors join the loaded ME grids and follow normal channel routing and controller rules. They consume no idle AE and require no device channel. An unloaded endpoint drops out until it loads again. Disconnecting one connector leaves the channel available to the others.
4. For wireless factory access, place a connector directly above a Loop Factory Network Terminal or above a Pattern Provider whose subnet arrows point up. The selected wireless channel links to the machine’s main grid. The provider’s subnet keeps its independent channels and storage. An AE2 wrench picks up a connector with its channel binding for reuse within the same world.

## 中文

**应用能源：循环科技（AE2LpT）** 为应用能源 2 加入可编程工厂、跨维度 ME 连接、共享存储磁盘和循环合成。先给 ME 网络供电，再接入终端查看库存或提交合成。小型网络可以用能源接收器和线缆直接组网，需要频道的设备遵循 AE2 的频道分配。

### 循环水晶与材料

- 四个循环水晶碎片无序合成一个循环水晶，一个水晶可拆回四个碎片。另一种工作台配方在四角放赛特斯石英水晶，四边中点放循环水晶碎片，中央留空，一次合成四个循环水晶。
- 四个循环水晶排成 2×2 可合成循环水晶块。用压印器处理一个循环水晶可得到一份循环水晶粉末。机械动力的研磨和通用机械的粉碎也可制作粉末。

### 共享存储与便携磁盘

将循环存储磁盘放进 ME 驱动器或支持它的 ME 箱子。单个磁盘的字节预算由 AE2 已注册资源共用，可以同时存物品、流体，以及由附属提供的 FE、魔力、源质等资源。各资源按自己的每字节容量换算。

| 档位 | 容量规则 |
| --- | --- |
| 1k、4k、16k、64k、256k | 字节容量与同档 AE2 物品磁盘相同，所有资源合计最多 63 种 |
| 1M、4M、16M、64M、256M | 所有已注册资源共用一个容量池，容量从 66,060,288 字节到 16,911,433,728 字节 |
| 无限 | 共用容量和类型数均无限 |

有限档可用对应循环存储核心与循环存储磁盘外壳合成。制作无限循环存储磁盘时，将 64 个 **256M 循环存储核心**和一个**循环存储磁盘外壳**一起丢在地上，用 TNT 爆炸完成转化。

外壳配方四角放玻璃，中间左右放铁锭，上下居中放循环水晶粉末，中央留空。1k 核心四角放铁锭，中央放赛特斯石英，四个边中点放碎片。4k 到 256k 核心保留铁锭四角和赛特斯石英中央，在上中、中左、中右放三颗上一档核心，下中放循环水晶粉末。1M 到 256M 改用下界合金锭四角、奇点居中、循环水晶放下中，其余三个边中点仍放上一档核心。

便携版用 ME 箱子、能源元件和对应的循环存储磁盘合成。有限档也可以用同档核心和外壳代替成品磁盘。便携磁盘有独立的 ME 界面和可充电的 AE 电池，能量卡可以扩容。潜行使用空盘可拆回部件并回收剩余电量。具有 FE 存储支持时，可以插入循环卡，让盘内 FE 为便携磁盘充电。循环卡由高级卡和循环水晶无序合成。

### 自动合成订单

- **超算合成优化接口。** 接入合成 CPU 所在网络。在线合成服务占一个频道，消耗 8 AE/t，按依赖顺序规划无环订单，并计入共用原料、副产物和返还容器。默认模式下，无控制器网络的总频道数和普通线缆容量由 8 增至 16，致密线缆和控制器单面由 32 增至 64。每个网络获得一次加成，设备仍需正常供电。
- **配方环解算终端。** 用于复制锻造模板等循环配方。先在网络内备好一份起始模板，再通过 ME 合成终端下单。环解算保留种子并预留外部原料，在发起订单的 CPU 上跟踪批次和交付。它占一个频道，消耗 2 AE/t，通过普通 ME 合成终端提交订单即可。
- **合成撕裂者。** 打开机器，放入工作台、锻造台或切石机样板。四行可见窗口可滚动，样板库存随使用增长。通过 ME 合成终端下单后，撕裂者检查整条链和实际投入，在一个服务端 tick 内完成受支持的配方链，保留返还物。机器占一个频道，执行整链不额外收费。例如编码原木转木板和木板转木棍，再用网络库存里的原木下单合成木棍。
- **自动样板与导入。** 在撕裂者内装入循环卡即可自动发布受支持的配方。手动样板优先用于它编码的精确产物，匹配时包含物品组件，缺料时仍保留该选方。样板区旁的向下箭头会从 ME 存储和同网设备槽位搬入受支持的已编码样板。导入期间保持界面开启，已搬入的样板会留在机器里。

### 循环机器

- **循环进刻器。** 将方块的绿色顶面对准目标，支持六向旋转。把方块单独放入合成格可换成线缆部件，把部件单独合成可换回方块。线缆部件加速安装面正前方的相邻方块。空手右键打开倍率界面。在线时默认每 tick 追加 1–4 次调用，每张加速卡让倍率上限翻倍，四张最高 64。多台方块或部件对准同一目标时，追加次数相加。例如分别设置为 2 和 3，每 tick 共追加 5 次，目标本身照常运行。实际产量还受机器的原料、电力和内部限制影响。
- **循环物质聚合器。** 先在元件工作台为循环磁盘标记要处理的物品，再把磁盘放入模板槽。给机器接上可用的 ME 频道，选择物质球、奇点或销毁模式。聚合器逐 tick 从网络抽取匹配物品，并将产物送回网络。在模板磁盘上装反相卡可排除标记物品，装模糊卡可使用模糊匹配。网络暂时装不下产物时会保留物质余额，等有空间后再结算。也可以使用机器的原生手动输入。

### 可编程循环工厂

空白样板和一份循环水晶粉末无序合成一张循环工厂样板。一份石英纤维、四个铁锭和两份粉末无序合成八根循环工厂接口线缆。

1. **选择运行设备。** 无配方物流程序用循环工厂网络终端。按需生产用循环工厂样板供应器，箭头指向独立的工厂子网，其余面接 ME 主网。两侧共享 AE 供电，各自分配频道和存储。循环工厂接口线缆可连接子网机器，自身不占设备频道。
2. **写入样板。** 循环工厂样板编码面板可以在配方页和代码页间切换，手持循环工厂编码器可编辑代码。对工厂线缆、控制器或终端使用手持编码器可绑定网络，对供应器使用则绑定箭头一侧的子网。潜行使用手持编码器解除绑定，潜行使用样板清除该样板的配方和代码。
3. **给机器打标签。** 在代码中用 `import` 声明标签。手持已绑定的编码器，按住 Tab 滚动选择标签，再对机器使用。按住 Ctrl 使用可同时标记相连的同类机器。一个标签可以指向多台机器，一条语句的搬运数量由整组机器共用。
4. **保存并执行。** 将无配方样板安装到网络终端，或将含配方样板装入样板供应器。也可从编码面板把实体样板上传给主网供应器。终端已安装代码发生变化，或工厂接收到红石上升沿时，程序会从头运行。等待和未完成的搬运可在区块重载后继续。

下面的无配方程序把最多 64 个铁锭从「输入」搬至「输出」。运行前给两个标签分别绑定可访问的容器。

```text
import 输入,输出
get 64 minecraft:iron_ingot from 输入
put 64 minecraft:iron_ingot into 输出
done
```

- `get` 声明来源及数量，`put` 执行搬运，`storage` 表示可访问的 ME 存储。可以用 `on up`、`on down`、`on north`、`on south`、`on east` 或 `on west` 指定机器面。省略数量时，可搬走来源额度内的所有匹配资源。
- `must` 会累计已搬的数量，凑足后才继续。普通搬运会转移当前可转移的数量，然后继续执行。用 `wait 20 tick` 暂停，用 `redstone 标签 20 tick` 向标签下的机器发送脉冲。缩进语言支持条件、循环、函数和 `channel` 作用域。每个作用域有自己的来源和额度，指向同一台机器的标签依然共用实物库存。
- 在供应器的配方任务中，`source` 持有 CPU 分配的投入。将投入送入机器，等待实际产物，再用 `put ... into source` 交回。用 `P1`、`O1` 选择第一项投入或产物，用 `P`、`O` 选择整组。阻塞模式在当前批次的程序结束并完成物料结算后接收下一批。
- 程序可搬运物品、流体、FE 和受支持的机器资源，可按准确 ID、`#minecraft:planks` 一类标签或通配符选择。原生编辑器支持缩进和 SFM 两种语法的高亮和 `//` 行注释。受支持的 JEI 或 EMI 条目可拖入代码。搭配 Applied Flux 时，感应卡可在供应器的来源中提供最多 1,000,000 FE。

要做按需熔炼，可编码一份圆石换一份石头的处理配方，将备好燃料的熔炉标记为 `Furnace`，再把下列样板装入工厂供应器。收到订单后，程序送入圆石，等熔炉产出石头再交回。

```text
import Furnace
get P1 from source
put P1 into Furnace on up
get must 1 O1 from Furnace on down
put O1 into source
done
```

切换到 SFM 模式后，下面的无配方程序每秒检查一次标签对应的容器，有铁锭时搬一个。

```text
NAME "铁锭搬运"
EVERY 20 TICKS DO
    INPUT 1 minecraft:iron_ingot FROM Input
    OUTPUT 1 minecraft:iron_ingot TO Output
END
```

### 全向连接器

1. 用无线访问点、循环水晶和缠绕态奇点无序合成四个全向连接器。
2. 在各端点放置连接器并接入要互联的 ME 网络。右键打开频道界面，输入名称并创建或加入频道。锁形开关选择可见性，公开频道供所有玩家加入，私有频道供创建者加入。
3. 在另一端选择同名频道。端点可以跨同一存档的不同维度，双方均加载时加入同一个 ME 网络，按原生规则分配频道和处理控制器冲突。连接器自身零待机耗电，也不占用设备频道。卸载的端点会暂时离开，区块重新加载后恢复。解绑一端时，频道仍供其他端点使用。
4. 为循环工厂建立无线连接时，将连接器贴在循环工厂网络终端正上方，或贴在子网箭头朝上的工厂样板供应器正上方。所选频道接入机器的主网，供应器子网仍独立分配频道和存储。用 AE2 扳手拆下连接器后，同一存档内重新放置可沿用频道绑定。
