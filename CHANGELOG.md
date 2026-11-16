# Changelog

## 0.0.8

### English

- The loop card no longer locks the ripper's pattern directory: manual and third-party uploads, including ECO and EAEP, keep working with the card installed, while patterns already in the directory are never overwritten.
- Added a deployment section to the in-game guide describing the wireless factory network structure, its placement rules and the channel and tagging boundaries.
- Reworked the Loop Factory network terminal model: hand-drawn side and back textures, with the bottom reusing the top.

### 简体中文

- 循环卡不再锁定撕裂者的样板目录：装卡状态下手动与第三方上传（含 ECO、EAEP）照常写入，目录里已有的样板不会被覆盖。
- 游戏内指导书新增部署章节，说明无线工厂网络结构、放置规则以及频道与打标签的边界。
- 重做循环工厂网络终端模型：侧面与背面使用手绘贴图，底面复用顶面。
## 0.0.7

### English

- Added the Loop Factory wireless structure: an omnidirectional connector whose base face rests on a Loop Factory network terminal's top face, or on a factory pattern provider's subnet face, joins the selected channel to that machine. The channel carries only the machine's main AE network, and a provider's subnet never joins it. The structure alone also extends the factory network to the connector's chunk, so machines there can be discovered and tagged without selecting a channel.
- Factory terminals and factory pattern providers never join a network wirelessly: they must be cabled, and a connector mounted on one never wires its base face into that machine. Every other block in the chunk stays discoverable and taggable, and nothing is scanned, joined or moved before a tag binds a machine.
- Added a white ring particle that orbits inside the connector's own block while the structure is formed, aligned with the base face normal and completing a full circle.
- Factory tags are now one store per factory network: a tag marked on one terminal is the same tag on every other terminal of that network, and saving a pattern no longer unbinds the tags another pattern of the same provider declares.
- Reworked the Loop Factory network terminal model: the front and top keep their textures, the sides use a striped texture, the back has its own texture, and the bottom reuses the top.
- Removed a stray README file from the shipped item texture folder that made Minecraft report an invalid datapack path.

### 简体中文

- 新增循环工厂无线结构：全向连接器基座面贴在循环工厂网络终端顶面，或贴在工厂样板供应器的子网（朝向）面时，把所选频道与该机器相连。频道只承载机器的主网，供应器子网永远不接入；同时结构本身把工厂网络扩展到连接器所在区块，不选频道也能被该网络发现并打标签。
- 工厂终端与工厂样板供应器永远不通过无线接入：必须接线，压在其上的连接器也不会把基座面接进该机器。区块内其余方块保持可发现、可打标签；打标签之前不扫描、不入网、不搬运任何物品。
- 结构成型时，连接器在自身单方块内显示白色环绕粒子，环绕平面与基座法线对齐并完整绕行一周。
- 工厂标签改为每个工厂网络一份：在一个终端标记的 tag，同网络其他终端看到同一份绑定；保存一个样板不再解绑同一供应器里其他样板声明的同名 tag。
- 重做循环工厂网络终端模型：正面与顶面保留原贴图，侧面使用条纹贴图，背面使用独立贴图，底面复用顶面。
- 删除物品贴图目录里误入的 README 文件，Minecraft 不再报告无效数据包路径。

## 0.0.6

### English

- Added private and public omnidirectional connector channels: creation picks the visibility, public channels are visible to every player, private channels only to their creator, and channels saved before this release load as public.
- Added AE2's native lock toggle to the connector screen; the channel list, its paging, the selected-channel line and the join action only ever use channels the current player can see, and the server re-checks every selection.
- Changed the Crafting Ripper to draw no idle power and charge no AE for a whole-chain rip; it is treated as infinite parallel crafting, so a chain commits in one tick regardless of the CPU's co-processors.
- Made the ripper's pattern slots safe for pattern upload and migration by other AE2 addons: a slot index past a shrunk tail row grows the library back instead of failing, unsupported patterns are returned instead of stored, and accepted patterns are advertised in the same server tick.
- Added an optional Thunderbolt bridge so the supercomputer's single channel doubling also reaches AE2 Lightning Tech overloaded and advanced overloaded controllers; the bridge applies nothing when that mod is absent.
- Changed the Crafting Ripper recipe to use a 256M Loop Storage Core instead of the 4M core.
- Restored ordered recipes for four Loop Factory machines — network terminal, pattern provider, pattern encoding panel and handheld encoder — with exactly the same materials.
- Fixed the connector language file that failed strict JSON parsing and was skipped at runtime, which had replaced every connector string with its raw translation key.
- Fixed the ripper's pattern import button reporting an available network when the connected ME network had no power.

### 简体中文

- 全向连接器新增私有／公开频道：创建时选择可见性，公开频道对所有玩家可见，私有频道仅创建者可见；本版之前保存的频道按公开加载。
- 连接器界面新增 AE2 原生锁形开关；频道列表、翻页、当前频道行与加入动作只使用当前玩家可见的频道，服务端对每次选择二次校验。
- 合成撕裂者取消自身待机耗电，也不再收取整链合成费用；它被视为无限并行合成，整条链不受 CPU 协处理器数量限制，在一个 tick 内提交。
- 撕裂者样板槽位对其它 AE2 附属的样板上传与迁移安全：收缩后的尾部槽位索引会重新生长而非失败，不支持的样板原样返回而非写入，接收的样板在同一次服务端处理中即可见。
- 新增可选的闪电科技桥接：超算的单次频道翻倍同样作用于 AE2 闪电科技的过载与高级过载控制器；该模组不存在时不应用任何桥接。
- 合成撕裂者配方由 4M 循环存储核心改为 256M 循环存储核心。
- 四个循环工厂机器配方（网络终端、样板供应器、样板编码面板、手持编码器）恢复为有序配方，材料与数量完全不变。
- 修正连接器语言文件严格 JSON 解析失败、运行时被跳过的问题：此前界面上所有连接器文案都退化成原始翻译键。
- 修正撕裂者在所连 ME 网络无电时，样板导入按钮仍显示网络可用的问题。

## 0.0.5

### English

- Added the Loop Factory language's lexical `channel` scopes, including function inheritance and channel-local GET quotas.
- Added terminal execution refresh on installed-code changes, pattern replacement/removal, and redstone rising edges, while preserving ordinary reload continuation.
- Added persistent recovery for buffered resources when an execution is refreshed or cancelled.
- Added recipe-aware `P1`/`P2` and `O1`/`O2` references for factory patterns, with normal AE2 recipe target previews retained when factory code is absent or invalid.
- Added complete syntax highlighting for factory and supported SFM code, including strings, resources, functions, operators, faces, recipe references, and `//` end-of-line comments; quoted slash text and existing comment forms remain supported.
- Added code-area ID insertion from the carried GUI item: ordinary items use their own ID, and containers can insert their own ID or their visible contents.
- Added optional JEI and EMI item/fluid drag insertion into the code editor.
- Improved caret, selection, wrapping, and scrolling behavior so the visible editor range matches the actual source range.
- Improved unloaded-chunk handling with resumable waits and explicit capability errors.
- Improved large logistics with compressed batches, checked signed-64-bit quantities, independent job backpressure, and recoverable partial transfers.
- Improved crafting services with persistent cyclic schedules, recipe-aware acyclic planning, container returns, byproducts, and safe fallback when a plan cannot be proven.
- Added the complete Loop Storage Cell and portable-cell families with AE2-compatible upgrades and optional Create/Mekanism recipe integrations.
- Fixed the omnidirectional connector being placed back after a single break when its linked channel isolates it from a powered network hub: the break now leaves air, and the virtual edge is removed as before.

### 简体中文

- 新增循环工厂语言的词法 `channel` 作用域，支持函数继承和通道内 GET 额度隔离。
- 新增已安装代码变化、样板替换/移除以及红石上升沿触发的终端执行刷新，并保留普通重载后的执行续点。
- 新增执行刷新或取消时的实际缓存资源持久化回收。
- 为工厂样板新增配方感知的 `P1`/`P2` 与 `O1`/`O2` 引用；工厂代码缺失或非法时仍保留普通 AE2 配方目标预览。
- 补全工厂语言和已支持 SFM 语法的代码高亮，覆盖字符串、注释、资源、函数、运算符、方向、配方引用和 `//` 行尾注释；双引号中的斜线文本及原有注释形式继续保留。
- 新增从 GUI 鼠标拿起物品向代码区填入 ID：普通物品填入本体 ID，容器可填入本体 ID 或其中可见内容。
- 新增可选 JEI 与 EMI 物品/流体拖入代码区功能。
- 修正光标、选区、自动折行和滚动，使编辑器显示范围与实际源码范围一致。
- 改进未加载区块处理，支持可恢复等待并报告明确的能力错误。
- 改进大宗物流，使用压缩批次、带检查的有符号 64 位数量、独立任务背压和可恢复的部分转运。
- 改进合成服务，支持持久化循环调度、配方感知无环规划、容器返还、副产物，以及无法证明计划时安全交回 AE2。
- 完善循环存储磁盘和便携磁盘系列，兼容 AE2 升级卡，并提供可选 Create/Mekanism 配方联动。
- 修正全向连接器在已连接频道、且拆除会使其脱离带电网络枢纽时被挡回原位的问题：现在一次破坏即留下空气，虚拟边照常移除。

## 0.0.4

### English

- Fixed automatic-catalog planning that repeatedly compressed and decompressed material without net growth. Existing compressed stock and legitimate upstream crafting routes remain usable.
- Kept supercomputing acceleration compatible with automatic recipe discovery: unused reverse recipes no longer force ordinary crafting plans to require a ring terminal; ownership follows the recipes selected for the order.
- Added the Crafting Ripper with a four-row AE2 Pattern Provider interface for crafting-table, smithing and stonecutting patterns, native provider controls and priority configuration.
- The ripper's main area contains only its 36 pattern slots; the compact native interface keeps the player inventory and upgrade controls visible at normal GUI scales.
- Fixed compressed schedules that consumed an intermediate cycle seed before running its upstream growth recipes, causing false missing-material results for complete layered jobs.
- Added whole-chain recipe preflight and one-tick execution using checked 64-bit quantities, compressed ring-terminal schedules, exact remainders and the ring terminal's seed-reserve policy. The ripper uses 5 AE/t and an additional 50 AE for each committed whole-chain rip.
- Added the Loop Card, crafted shapelessly from an Advanced Card and a Loop Crystal. Its native acceleration-card body is preserved with a circular repeat emblem.
- Installing a Loop Card in the ripper advertises automatically discovered, encodable recipes and greys out all pattern slots. Existing patterns remain removable; removing the card restores normal pattern use.
- Fixed automatic discovery skipping special and custom crafting recipes, including storage upgrades, dyeing, fireworks, books, banners and maps. Network component variants now refresh automatically; 26.1.2 also supports empty optional smithing slots and correct shapeless alternatives.
- Added native continuations for map expansion and stochastic quartz-knife durability effects. Real output identities remain private to the original CPU job, with persistent progress and one 50 AE payment; these operations are exempt from instant execution.
- Recipe discovery follows the live server recipe manager across modpack namespaces, data-pack replacements, tag changes and removal. Precise selection preserves complete components, nested NBT and numeric tag types; alternatives must assemble the exact requested result.
- Classified quartz-knife durability effects from the actual selected knife, so a substitute with random durability effects cannot enter deterministic batch execution. Preserved map transformation markers across CPU persistence and prevented completed map identities from being reused as fuzzy input aliases.
- Installing a Loop Card in a portable Loop Storage Cell enables charging its AE battery from stored FE using AE2's configured conversion and charge limits.
- Portable cells with both stored FE and positive AE charge expose their energy through NeoForge's native item energy API. Portable inventory caches refresh after external energy changes to keep stored quantities consistent.

### 简体中文

- 修复自动配方目录中反复压缩、解压材料却没有净增长而耗尽规划预算的问题；已有压缩材料与合法的上游合成路线仍可使用。
- 保持超算材料解算与自动配方发现兼容：未选中的逆向配方不再让普通合成计划强制要求环终端，按订单实际采用的配方确定服务归属。
- 新增合成撕裂者，使用四排 AE2 样板供应器界面，接受工作台、锻造台和切石机样板，并复用供应器原生控件与优先级设置。
- 撕裂者主体仅显示 36 个样板槽，紧凑界面在常用 GUI 缩放下完整保留玩家背包和升级控件。
- 修复多层合成任务中，下游配方先消耗循环种子、上游增殖配方后执行导致的材料充足却误报缺料问题。
- 新增整条合成链配方预检与一个 tick 内执行，使用带溢出检查的 64 位数量，兼容环解算终端压缩调度，精确结算返还物并遵循环终端种子保留策略。撕裂者消耗 5 AE/t，每次整链撕裂提交执行时额外消耗 50 AE。
- 新增循环卡，由高级卡与循环水晶无序合成；保留原版加速卡卡体，仅将图案替换为循环箭头。
- 为撕裂者安装循环卡后，自动发布可编码的真实配方并将全部样板槽变灰。原样板可取出，移除循环卡后恢复正常样板使用。
- 修复自动发现跳过特殊及自定义工作台配方的问题，覆盖存储升级、染色、烟火、书籍、旗帜和地图；网络组件变体自动刷新，26.1.2 同时补齐可空锻造槽与无序合成替代输入。
- 地图扩大及石英刀随机耐久效果使用原生逐步续作，真实产物身份仅关联原 CPU 任务，进度和一次 50 AE 收费共同保存；这类操作不受瞬间执行限制。
- 配方发现跟随服务端实际配方管理器，响应整合包命名空间、数据包替换、标签变更及配方删除。精准选料保留完整组件、深层 NBT 和数值标签类型，替代材料必须合成完全一致的目标产物。
- 按实际选中的石英刀识别耐久效果，避免带有随机耐久效果的替代刀具进入确定性批量执行；CPU 保存恢复保留地图变换标记，并防止已完成地图通过模糊输入别名被重复加工。
- 为便携循环存储磁盘安装循环卡后，可按 AE2 的转换比例与充能速率，将已存储的 FE 转换成自身 AE 电量。
- 同时存有 FE 与非零 AE 电量的便携磁盘通过 NeoForge 原生物品能量接口提供随身电容器能力；外部能量变化后刷新便携库存缓存，保持数量一致。

## 0.0.3

### English

- Added the complete Loop Storage Cell family: one housing, ten storage cores, ten finite universal cells, and one infinite cell.
- Added 1k through 256k cells with AE2-equivalent capacities and 63-type limits; 1M through 256M cells use a shared DISK-style total equal to 63 times the original single-type ceiling, and the infinite cell uses an unlimited aggregate pool. M and infinite cells do not show a separate type count.
- Added dynamic compatibility with every key type registered in AE2, including items, fluids, FE, mana, source, soul, and other addon keys. Optional storage addons are detected through AE2 only and are not dependencies.
- Added multi-cell spillover so a full cell routes the same key into the next compatible Loop Storage Cell instead of blocking that key on one disk.
- Added eleven portable cells covering every finite tier and the infinite tier. They reuse AE2's portable terminal, battery, charge rate, idle drain, powered insert or extract, and four upgrade slots with the same card set as AE2 portable cells: fuzzy, inverter, equal distribution, void, and energy card x2. Card hints, insertion validation, and card effects match AE2 portable behavior. Creative mode exposes empty and fully charged variants.
- Added all ordered and shapeless acquisition routes plus eleven disassembly declarations, and the exact explosion transform of 64 256M cores plus one housing for the infinite cell.
- Preserved the ten supplied core textures byte-for-byte and generated only the requested deterministic shell and cell recolors; portable layers follow each generation's native AE2 color contract.
- Kept mounted cell contents durable across grid refreshes and remounts, and kept AE2 as the only mod prerequisite in both maintained generations.
- Integrated the infinite explosion transform into AE2's native world-interaction recipe category and aggregated the 64 256M cores into one slot.

### 简体中文

- 新增完整循环存储磁盘系列：一个磁盘外壳、十个存储核心、十个有限通用磁盘和一个无限磁盘。
- 1k 至 256k 磁盘采用与 AE2 对应磁盘相同的容量和 63 种类型限制；1M 至 256M 磁盘使用总量为原单类上限 63 倍的深度磁盘式共享池，无限磁盘使用无限聚合池。M 级与无限级不单独显示种类数量。
- 自动兼容所有注册到 AE2 的存储键类型，包括物品、流体、FE、魔力、魔源、灵魂涌动及其他附属键；可选存储附属仅通过 AE2 动态识别，不作为依赖。
- 新增多磁盘溢写：同一种键在磁盘满后继续进入下一块兼容循环存储磁盘，不再被单块磁盘限制。
- 新增十一档便携磁盘，覆盖全部有限档与无限档。它们复用 AE2 的便携终端、电池、充能速率、待机耗电、通电存取，以及 AE2 便携元件相同的四格升级卡槽：模糊、反转、均衡分配、虚空、能量卡 x2。卡牌提示、准入判断与卡牌效果与 AE2 便携元件一致。创造模式提供空电与满电两种物品。
- 新增全部有序和无序获取配方、十一份拆解声明，以及精确消耗 64 个 256M 核心加一个外壳的无限磁盘爆炸转换配方。
- 十张用户提供的核心材质逐字节原样复制，只对明确要求的外壳和磁盘部分执行确定性换色。
- 便携磁盘各层遵循对应世代的 AE2 原生取色约定；磁盘内容在网格刷新与重新挂载后保持稳定。
- 两个维护版本均只将 AE2 作为模组前置。
- 将无限磁盘爆炸转换整合进 AE2 原生世界交互合成分类，并将 64 个 256M 核心聚合为一格显示。

## 0.0.2

### English

- Added the AE2LO bilingual identity: Applied Energistics 2 Lightweight Optimization / 应用能源 2 轻量优化.
- Added Loop Crystal, Loop Crystal Fragment, Loop Crystal Block, and Loop Crystal Powder, including models, bilingual names, recipes, tags, and creative-tab registration.
- Added an extra Loop Crystal drop to AE2's Mysterious Cube.
- Added Loop Crystal decomposition, recombination, shaped growth, and powder/block conversion recipes.
- Fixed cyclic planning for two-node self-growth rings, multi-recipe rings, and nested-ring schedules; added 16-node pressure coverage.
- Added independent optional Create Milling and Mekanism Crushing integrations using the common `c:gems/loop_crystal` tag. Neither external mod is required.
- Added isolated NeoForge 1.21.1 and 26.1.2 release artifacts and PCL deployment verification.

### 简体中文

- 统一 AE2LO 双语名称：Applied Energistics 2 Lightweight Optimization / 应用能源 2 轻量优化。
- 新增循环水晶、循环水晶碎片、循环水晶块和循环水晶粉，包含模型、双语名称、配方、通用标签与创造模式分类注册。
- 破坏 AE2 神秘方块时额外掉落一个循环水晶。
- 新增循环水晶分解、复合、有序增长以及水晶粉/水晶块转换配方。
- 修复双节点自增长环、多配方环和嵌套环的解算与调度，并加入 16 节点压力测试。
- 新增独立可选的机械动力磨粉和 Mekanism 粉碎兼容，统一使用 `c:gems/loop_crystal` 标签；两者均不是前置依赖。
- 新增 NeoForge 1.21.1 与 26.1.2 隔离发布产物及 PCL 部署校验。

## 0.0.1

### English

- Added the Recipe Ring Solver Terminal for cyclic AE2 crafting graphs, seed protection, compressed execution, and safe fallback.
- Added the Supercomputing Crafting Optimizer Interface for acyclic AE2 crafting plans, shared dependency optimization, and compressed dispatch.
- Added independent module ownership, version-local NeoForge adapters, Mixin takeover checks, bilingual GuideME pages, and isolated PCL instances.

### 简体中文

- 新增配方环解算终端，用于 AE2 循环合成图、种子保护、压缩执行与安全回退。
- 新增超算合成优化接口，用于 AE2 无环合成计划、共享依赖优化与压缩派发。
- 新增模块独立归属、版本专用 NeoForge 适配层、Mixin 接管校验、双语 GuideME 页面和隔离 PCL 实例。