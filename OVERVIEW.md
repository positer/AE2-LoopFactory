# AE2-LoopFactory (AE2LF) Project Overview

## 2026-11-16 发布 0.0.7：循环工厂无线结构（工厂网络成员 + 主网传输）

- 两代 `omni/OmniFactoryWireless.java`：结构判定（正下方是终端，或 `getFront()==UP` 的工厂样板供应器）。
- 两代 `omni/OmniFactoryNetworks.java`：频道 id → 工厂网络成员（机器实体 + 维度/区块），供 `FactoryServer.contains/members` 查询；成员持实体而非 `IGrid` 快照，避免 AE 合并后实例失效；同一机器可作为多个工厂网络成员，成员按频道独立增删。
- 两代 `omni/OmniConnectorBlockEntity.java`：`refreshFactoryLink()` 同时做「工厂网络成员登记（`factoryGrid()`，供应器为子网）」与「AE 传输（只接机器主节点）」；`getGridConnectableSides` 排除底面，避免下方机器通过相邻关系短接。
- 两代 `factory/FactoryServer.java`：`contains`/`members` 接受成员区块内的位置；`factory/FactoryBlockEntity.onReady()` 就绪时通知上方连接器；`omni/OmniContent.java` 每秒复核结构。
- 两代 `omni/OmniConnectorBlock.java` + `client/OmniOrbitParticle.java` + `particles/omni_orbit.json`：`wireless` 方块状态与单方块内环绕粒子。
- 删除 `versions/neoforge-26.1.2/.../textures/item/README.txt`，消除 `Invalid path in datapack` 告警。
- 探针：两代 `FactoryWirelessProbe` 与 `factory_wireless` 链（25 条断言，含「一个机器同时属于两个工厂网络」场景），`verify-background.ps1` 同步链白名单、链名、截图豁免与断言。
- 验收：单测 281／279；两代 `factory_wireless` 各 25 条断言通过；制品 806,437 B `ce27e119…7482e5`／824,871 B `ee072dd1…f5b291bb`；已推送 `v0.0.7` 并更新 GitHub Release，未部署 PCL。归档见枢纽 `../Minecraft Forge Workspace/archive/20261116-134500-ae2lf-factory-network-multi/`。
## 2026-11-15 发布 0.0.6

- 版本 0.0.6 已构建并打标签 `v0.0.6`：两代制品 1.21.1 794,553 B `84af9216…36ab`、26.1.2 813,220 B `a47d131e…72c7`；单测 281／279 全通过；1.21.1 `-Chain template` 原生链通过（零合成费、一次 tick 整链、材料精确）。
- 发布内容：连接器私有／公开频道与原生锁形开关、撕裂者零待机耗电／零整链费用／无限并行、第三方样板槽位兼容、超算翻倍接入闪电科技、撕裂者 256M 配方、四个工厂机器配方恢复有序，以及连接器语言文件与导入按钮的两处修复。
- 发布留痕归属枢纽 `../Minecraft Forge Workspace/archive/20261115-191500-ae2lf-omni-access-ripper-ae2lt/`；`main` 与 `v0.0.6` 已推送到 `origin`，并创建 GitHub Release `AE2LF 0.0.6`（两代 JAR 为发布附件）。
- 部署：0.0.6 已装入 PCL `AE2-lightoptimizer-26.1.2`（原 0.0.5 备份），实机启动到主菜单并正常关闭；1.21.1 实例仍是上一版。全量检查：1.21.1 `omni_access`／`ae2lt_capacity`／`template` 与 26.1.2 `template` 全通过，单测 281／279。

## 2026-11-15 频道私有/公开、撕裂者零自身耗电与闪电科技兼容

- `shared/.../network/OmniLinkDirectory.java`：`Link` 增加 `owner`（UUID 字符串）与 `visibility`（`PUBLIC`/`PRIVATE`），名称索引按可见性分命名空间，新增 `visibleTo`/`canSee`/`sortedVisible`；旧存档字段缺失时按公开加载。
- 两代 `omni/OmniLinkSavedData.java`：编解码新增可选 `owner`/`visibility` 字段；`create(name, visibility, owner)` 重载。两代 `omni/OmniConnectorMenu.java`：`createPrivate`/`selectedPrivate` 同步字段、`omniCreatePrivate` 客户端动作、可见性过滤与服务端二次校验。
- 两代 `client/OmniConnectorScreen.java` 与 `assets/ae2/screens/ae2lightoptimizer_omni_connector.json`：原生 `ToggleButton`（LOCKED/UNLOCKED）可见性开关、行文本私有标记、名称框宽度让位；双语语言文件新增 `omni.private`、`omni.visibility`、`omni.visibility.hint` 并缩短 `omni.links`。
- 两代 `block/CraftingRipperBlockEntity.java`：节点待机功耗 5.0 → 0；同日后继改动又取消了每次整链 50 AE 的费用（见下条）。两代 `block/RipperPatternInventory.java`：`insertItem` 对超出收缩后尾部的槽位重新生长库存，写入后批量触发一次变更；不接受的样板原样返回。
- 两代 `compat/OptimizerFlowContext.java`、`compat/OptionalCompatMixinPlugin.java`、`mixin/compat/ThunderboltBorrowedCapacityMixin.java`、`mixin/compat/ThunderboltSupplyMixin.java`、`resources/ae2lightoptimizer.compat.mixins.json`、`META-INF/neoforge.mods.toml`：可选的 Thunderbolt 最大流桥接，缺该模组时按类资源缺失整份不应用。
- `tools/runtime-ripper-probe/1.21.1/.../OmniAccessProbe.java`、`OmniAccessClient.java`、`Ae2LtCapacityProbe.java`：新链 `omni_access`（可见性、菜单拒绝、撕裂者零耗电与第三方槽位写入、双语界面截图）与 `ae2lt_capacity`（过载控制器 128→256→128）；`verify-background.ps1` 扩展链白名单、截图门禁、链名映射与耗电断言；`tools/verify_omni_saved_data.py` 支持 `omni_access` 的所有者／可见性磁盘回读。
- 追加（同日）：合成撕裂者配方 `C` 键由 4M 循环存储核心改为 256M 循环存储核心，生成器与配方契约测试同步；指南页耗电文案改为零待机耗电。
- 追加（同日）：四个循环工厂机器配方（网络终端、样板供应器、编码面板、手持编码器）由无序改为有序，材料不变；新增 `shared/.../crafting/LoopFactoryRecipeShapeTest.java` 锁定形状与结构性约束。
- 追加（同日）：取消合成撕裂者每次整链 50 AE 的费用（`CraftingRipperExecutor` 与 `RipperNativeCrafting` 不再扣电，`RIP_ENERGY` 删除）；`ExecutingCraftingJobMixin.ae2lightoptimizer$limitOperations` 对撕裂者任务返回整批剩余量，撕裂者视为无限并行合成；探针与门禁的费用／待机断言同步为 0。
- 版本 0.0.5 → 0.0.6（两代 `gradle.properties`），`CHANGELOG.md` 增加 0.0.6 双语文案；`docs/omni-connector.md`、`docs/crafting-ripper-patterns.md`、`docs/optimizer-channel-capacity.md` 同步合同与边界。
- 验收：1.21.1／26.1.2 各 278／276 项单测零失败；1.21.1 `-Chain omni_access` 与 `-Chain ae2lt_capacity`（thunderbolt 2.0.3 + ae2lt 2.1.3）原生通过，制品 794,886 B `144cfc3e…`／813,566 B `1be91d8b…`。证据归属枢纽 `../Minecraft Forge Workspace/archive/20261115-191500-ae2lf-omni-access-ripper-ae2lt/`；26.1.2 未跑原生客户端，未部署 PCL。
## 2026-11-12 全向连接器一次性破坏版 PCL 部署

- 已安装代码：`80db0cf93ad7bf1cc29ee1591172a2068be16ee2`／`v0.0.5-omni-one-hit.20261112`，两代对应实例均回读一致（1.21.1 785,621 B `BA29A0D7…`，26.1.2 804,271 B `5A85A33D…`）。
- 旧包保存在各实例 `_superseded/20261112-130407-ae2lf-pcl-omni-one-hit-deploy/`，以 `.jar.bak` 结尾；每个实例仍只有一个活动 AE2LF JAR。
- 部署与独立回读证据位于 `../Minecraft Forge Workspace/archive/20261112-130407-ae2lf-pcl-omni-one-hit-deploy/`，含 720／1007 个原文件清单、1,571 个存档文件保持字节与时间戳、安装与备份哈希。
- 本次只更新部署文档，没有重新构建、没有启动游戏，也没有改动配置与依赖。

## 2026-11-12 全向连接器一次性破坏

- 两代 `omni/OmniConnectorBlock.java`：`updateBlockStateFromBlockEntity` 增加“方块已不在该坐标时返回当前状态”的守卫，避免拆除过程中 AE2 的 `markForUpdate` 把空气写回连接器；其余行为与资源不变。
- 两代 `tools/runtime-ripper-probe/.../OmniProbe.java`：新增 `case 9/10` 单端带电频道一次性破坏场景（真实 `player.gameMode.destroyBlock`），原 `case 9..11` 顺延为 `11..13`；`tools/runtime-ripper-probe/verify-background.ps1` 的 `-Chain omni` 必需项加入四条新检查。
- 验收：1.21.1／26.1.2 各 274／272 项单测零失败；`-Chain omni` 原生链两代通过，且用“回退两行守卫”的制品做过反向对照（`linked_connector_breaks_in_one_hit` 失败）。制品 785,621 B `BA29A0D7…`（1.21.1）与 804,271 B `5A85A33D…`（26.1.2）。
- 证据归属枢纽 `../Minecraft Forge Workspace/archive/20261112-123226-ae2lf-omni-one-hit-break/`；未部署 PCL。

## 2026-11-11 低色差线缆版 PCL 部署

- 已安装代码：`655a4287baf850eaf428b22a29a4945b4fcbce7c`／`v0.0.5-cable-restore.20261111`，两代对应实例均回读一致。
- `../Minecraft Forge Workspace/archive/20261111-205934-ae2lf-pcl-cable-deploy/` 保存最终制品约束、全部原文件清单、旧包备份位置及独立安装回验。
- 本次只修改部署文档，没有重新构建或启动游戏。

## 2026-11-11 工厂网络线缆材质还原

- `design/loop_factory/references/loop_factory_cable_original.png`：0.0.5 发布提交 `1ea5964` 的原始低色差贴图，双代共用的唯一还原来源。
- 两代 `textures/block/loop_factory_cable.png` 与 `design/loop_factory/<版本>/loop_factory_interface_cable.bbmodel`：运行图和编辑源内嵌图同步；完整立方体、UV、物品模型与 blockstate 不变。
- `tools/align_ae2_visuals.py --only-cable`：只恢复线缆；完整生成与 `tools/blockbench/ae2lf_assets.js` 导出也使用原始图，避免高对比网格重新覆盖。
- `tools/runtime-factory-probe/verify-background.ps1 -CableOnly`：仅运行原生物品／世界两场景，支持不可覆盖的只读回验。两代 274／272 单测、视觉和包内差异检查通过；产品 JAR 仅一张贴图发生变化。

## 2026-11-11 全向连接器 PCL 部署

- 最终两代制品已安装至对应 PCL 专用实例，代码提交 `be4d0fd451a890e68806617a6d447ba5b54ae29c`。
- 部署证据位于 `../Minecraft Forge Workspace/archive/20261111-163146-ae2lf-pcl-omni-deploy/`，含完整原文件清单、旧包位置、安装哈希与独立只读回验；每个实例仍只有一个活动 AE2LF JAR。
- 本次仅更新部署文档，未重建或改写代码；未启动游戏。

## 2026-11-11 全向连接器

- 最终验收：274／272 项单测、双代原生连接与粒子检查、各 178 项 UI 场景、20 张人工视觉复核及制品冻结后的只读回验通过；开发 mods 恢复初始十文件哈希清单。最终轮次为 `pulse-r2`。
- `docs/omni-connector.md`：行为边界、逐文件职责、原生校验含义与操作说明。
- `shared/.../network/OmniLinkDirectory.java`：命名频道的稳定标识、规范化与分页排序缓存；`OmniWhitePalette.java`：保留透明度及明暗的全白色板。
- 两代 `omni/`：方块与实体、全服存档频道目录、每个频道唯一的原生虚拟枢纽、原生菜单与内容注册。生命周期移除虚拟边，不新增区块票据。
- 两代 `client/OmniPulseParticle.java`、`particles/omni_pulse.json`：原生白色精灵与单块范围慢速外扩；服务端派生 `online` 状态控制启停。
- 两代 `client/OmniConnectorScreen.java` 与 `OmniWhiteSprite.java`：原生 AE2 配置页与依赖材质运行时白光适配；`mixin/Omni*EnergyMixin.java`：仅免除连接器节点与虚拟连接的频道能耗。
- 两代新增 blockstate、原生父模型引用、atlas 源、三材料无序配方、掉落表、双语名称与 GuideME 页面；`tools/generate_omni_connector_resources.py` 可重新生成资源，`verify_omni_resources.py` 只读对照原生状态与模型。
- `tools/runtime-ripper-probe/{generation}/.../Omni*`：独立的原生网络、实际机器中继、卸载重连、配方拆放与双语 UI 校验助手，随 `-Chain omni` 运行，不打入产品。
- 验收证据归属枢纽 `../Minecraft Forge Workspace/archive/20261110-205415-ae2lf-omni-connector/`；最终状态以该目录完成记录为准。

## 2026-11-10 编码面板 UI 专项

- 两代 `assets/ae2/screens/ae2lightoptimizer_factory_panel_recipe.json`：直接继承原版共用“样板编码”标题；保留现有加工限定与附加代码页签。
- 两代 `ae2lightoptimizer_factory_editor.json` 与 `client/FactoryEditorScreen.java`：原生标题／背包锚点、加工返回图标及贴框水平页签；共用编辑器输入行为不变。
- `tools/runtime-factory-probe/verify-background.ps1 -PanelUiOnly`：只运行相关配方／代码／上传与双语原版对照，保留完整覆盖、加载身份和正常退出门禁。
- `BackgroundAudit.java`：本专项记录实际窗口和槽位坐标，并通过原生页签回调返回配方页。
- 证据：`../Minecraft Forge Workspace/archive/20261110-100300-ae2lf-panel-ui/`，含修改前截图、原版对照和像素检查脚本。
- 验收：262／260 项单测、两代各 44 项中英文客户端场景、四组原版坐标与标题像素对照及独立回读通过；开发目录助手已恢复。


## 2026-11-10 AE2 原生视觉资源

- `docs/ae2-visual-style.md`：固定 AE2 参考版本、资源来源、方块／面板保护边界与界面约定。
- `tools/align_ae2_visuals.py`：读取各版本自身 AE2 依赖，生成三类机器外框、终端和完整立方体线缆材质，同步三类 Blockbench 源文件；插件导出后自动收口。
- `tools/verify_ae2_visual_style.py`：按显式 Git 基线检查存储／面板受保护素材、核心像素、完整线缆模型、依赖贴图与重复生成一致性。
- `design/loop_factory/<generation>/`：线缆保持完整立方体，终端区分正面／外壳，面板源文件包含原生外壳、后部连接结构及三层遮罩。
- 两代 `client/FactoryUploadScreen.java`：居中的原生 AE2 背景与按钮列表；`FactoryEditorScreen.java` 和 screen JSON：原生页签与为字数计数器预留宽度的状态栏。
- `tools/runtime-factory-probe/verify-background.ps1`：新增 `-StyleOnly` 聚焦真实 UI／物品／方块／面板场景，`-EvidenceRoot` 允许直接写入枢纽留痕；原有模式与验收要求继续保留。
- 本轮证据位于 `../Minecraft Forge Workspace/archive/20261110-090500-ae2lf-visual-style/`。开发制品与公开的 `0.0.5` 发布分开记录。
- 最终验证：两代 262／260 项单测及各 172 项客户端专项通过，独立回读通过；测试助手恢复原状，未部署或发布。


## 2026-09-12 editor scroll coordinate correction

Both `FactoryEditorHighlightMixin.java` adapters now draw in the native parent's content coordinates and inherit its viewport scissor. Previously they subtracted scroll twice and established another clip inside an already translated pose, producing blank space below prematurely hidden text. Visibility checks retain the exact native scroll value; the caret uses the native cursor-line index.

`tools/runtime-factory-probe/<generation>/.../EditorScrollAudit.java` adds ten native scenarios for top/down/end/up movement, fractional wheel scrolling, click positioning, selection, wrapped content, shorter text and empty text. `archive/2026-09-12-editor-scroll/` preserves the old JARs, native parent-source excerpts, before/fix source snapshots, the identical pre/post render checks, screenshots and deployment evidence. The old production JARs reproduce seven failed scroll scenarios in each generation.

Both corrected native runs pass all 20 scroll scenarios, the existing 22 syntax screenshot checks and channel/MUST/function logistics regressions. Full build/test gates pass with 262/260 tests. The accepted JARs (`3d5ae037...` / `64dabb18...`) are installed in both PCL instances with previous JAR backups; 665/905 save files and all other existing instance files retain bytes and timestamps. All ten original development run/mods files were restored. [Final report](archive/2026-09-12-editor-scroll/REPORT.md).

## 2026-09-13 README current-capability rewrite

`README.md` now presents the current AE2LF feature set in complete English followed by complete Simplified Chinese. It identifies 0.0.5 as the public release and covers Loop Factory execution refresh, redstone edges, lexical channels, comments/highlighting, recipe previews, ID insertion, unloaded chunks, crafting services, Loop Storage, and compatibility limits. Its bulk-logistics section describes compressed batches, checked signed-64-bit accounting, independent job backpressure/recovery, and the bounded 4,096-round / 2,304-batch regression evidence without making universal throughput claims. The documentation-only change is recorded in [archive/2026-09-13-readme-rewrite/REPORT.md](archive/2026-09-13-readme-rewrite/REPORT.md).

## 2026-09-12 complete editor highlighting and slash comments

`shared/.../factory/FactorySyntaxHighlighter.java` lexes whole source into UTF-16 spans, including both supported languages, Unicode names, resource tags, logical/comparison operators, aggregate and indexed recipe references, strings, and comments. Its range intersection preserves token context across native soft-wrapped visual lines. Both adapters' `FactoryEditorHighlightMixin.java` cache spans only after a text change and restrict the render override to `FactoryEditorScreen`.

`FactorySourceComments.java` masks unquoted `//` comments without changing offsets or line numbers. `FactoryCompiler.java` uses the masked text for indentation parsing; `SfmSyntax.java` uses it for dialect recognition and tokenization. Original program source remains editable and persisted. `FactoryCompletion.java` discovers Unicode declarations and ignores trailing slash comments. `FactorySyntaxHighlighterTest.java` adds 11 classification/wrapping/maximum-source tests; `FactorySlashCommentTest.java` adds 8 compiler, quotation, line-number and coloring regressions. Both full suites pass (262/260).

`tools/runtime-factory-probe/<generation>/.../SyntaxHighlightAudit.java` supplies native widget fixtures, verifies render-cache refresh and captures 11 unmodified framebuffer pages per generation. `ChannelFunctionAudit.java` exercises commented function calls and SFM transfers with real containers. `docs/loop-factory-highlighting.md` contains bilingual syntax and comment examples. `archive/2026-09-12-complete-highlighting/` retains builds, focused test XML, frozen accepted JARs, artifact verification, native and pixel-check scripts, runtime-mod backups and exact-hash PCL deployment tooling.

Final native runs pass on both generations, including all 22 original screenshot checks and the added SFM comment transfer ending at A/B/Dst = 0/0/15. Both PCL instances now contain the accepted JARs (`91db1f1f...` / `937e6830...`); all 1,570 save files and other instance bytes/timestamps remain unchanged. Ten original development run/mods files were restored after normal client exit. See [the dated report](archive/2026-09-12-complete-highlighting/REPORT.md) for exact hashes, scopes and evidence locations.

## 2026-09-12 channel highlighting and function isolation

`FactoryCompiler.RESERVED` now includes `channel`, so the native editor's shared highlighter classifies it correctly and tag/function names cannot shadow it. CALL instructions retain their lexical channel in their previously unused target field. `FactoryMachine.effectiveChannel` derives inherited function routing from the existing return-address stack; no instruction indexes or persisted snapshot fields change. Both adapters' `FactoryJob.transfer(GET, ..., channel)` also retain the requested channel.

`FactoryChannelIsolationTest.java` adds six regressions that failed on the previous code and pass after the correction. Native `ChannelFunctionAudit.java` in each isolated helper verifies real container balances, nested function MUST resume, the direct channel-aware GET API and independent jobs. The legacy helper now includes the channel scene in its native tick lifecycle; screenshot capture waits for the editor to render. Final targeted native runs and screenshot checks pass on both generations, alongside 243/241 full unit tests. `docs/loop-factory-channels.md` defines the routing and shared-inventory boundaries. `archive/2026-09-12-channel-isolation/` retains red results, failed helper attempts, explicit final native verification, production-byte audits, runtime-mod restoration, and the PCL deployment/backup evidence.

## 2026-09-12 PCL test deployment

Following the completed native campaign, the user requested local installation for manual testing. Both accepted 0.0.5 JARs are now installed in the existing `AE2-lightoptimizer-1.21.1` and `AE2-lightoptimizer-26.1.2` PCL instances. Only each production JAR changed; all 1,570 save files and every other existing file retain their bytes and timestamps. No client was launched as part of deployment.

`archive/2026-09-12-pcl-final-deployment/` contains `deploy.py` (exact artifact and target guards, previous-JAR backups, complete before/after inventories), `deployment.json` (installed hashes and preservation counts), `<generation>-before.json` / `-after.json` (every instance file), `backups/<generation>/` (the two previous production JARs), and `REPORT.md` (deployment scope and manual test handoff). This follow-up supersedes the earlier campaign's no-install status; its original completion evidence remains immutable.

## 2026-09-12 expanded logistics and native pattern preview

The current development update repairs channel-local unquantified MUST accounting, old inflated pending-count migration, native recipe target previews independent of factory-code validity, resource-only machine removal recovery and native chunk tick eligibility. It adds GUI-carried item/container ID insertion plus optional JEI/legacy EMI dragging. Final acceptance is complete for the frozen current JARs: 179/182 full native scenes, five input profiles (127 cases / 266 frames), separate-JVM restart, legacy Mekanism 2,304 batches, and all 18 final7 Ripper chains. Strict collectors pass 77 factory checks, 5/5 input profiles and 18/18 Ripper runs; unit tests pass 237/235. All ten original run/mods files are restored exactly. Scope, immutable failures and evidence live in `archive/2026-09-12-expanded-background-qa/REPORT.md`; this does not publish or install a new release.

New and changed source/tool responsibilities:

- Both adapters' `client/FactoryEditorScreen.java`: GUI-carried-only left/right ID insertion, exact native caret/selection replacement, atomic 4096-selector/65536-source limits, and shared viewer drop bounds.
- Both adapters' `client/ContainerResourceIds.java`: read-only copies queried through native fluid/energy/item capabilities and AE cell inventories; deduplicated actual content IDs, canonical FE selector, and guarded legacy chemical lookup.
- Both adapters' `client/FactoryEditorJeiPlugin.java` and legacy `client/FactoryEditorEmiPlugin.java`: independent optional public viewer plugins for typed item/fluid dragging into the code area. Viewer APIs are compile-only and never bundled.
- `tools/runtime-factory-probe/<generation>/.../{ItemIdInsertionAudit,NativeIdInput,ViewerIdInput,IdInsertionJeiObserver}.java`: actual native menu/inventory synchronization, synthetic native GUI callbacks, real viewer sidebar drags, client/server resource snapshots, and framebuffer captures.
- `tools/runtime-factory-probe/verify-id-insertion-background.ps1` and `verify-id-insertion.py`: exact viewer isolation/restoration and independently reconstructed per-case text/caret/resource/save/framebuffer gates.
- `tools/runtime-factory-probe/verify-background.ps1 -ValidateExisting`: reuse every normal postcondition against immutable completed native evidence, verify current artifacts, and write a separate revalidation manifest; launch failures remain archived separately.
- Both adapters' `FactoryServer.java`: tick factory hosts only when the existing native chunk holder is eligible to tick, without loading chunks or renewing tickets; this prevents waiting jobs from keeping their own FULL chunks loaded through save notifications.
- Shared `FactoryMachine.java`: pass the executing instruction's channel into required-transfer accounting.
- Both adapters' `FactoryJob.java`: channel-filtered outstanding MUST quotas and migration of persisted inflated pending remainders without replay.
- Shared `FactoryChannelMustTest.java`: six compiler/VM/routing and migration behavioral regressions.
- Both adapters' `FactoryPatternItem.java`: delegate output preview and native tooltip to the embedded ordinary AE encoded pattern, with a non-nested native recipe boundary.
- Both adapters' `FactoryRecovery.java`: versioned resource-only machine cargo, segmented generic-resource quantities, pending opaque native recovery records, and accepted-amount accounting during ME reinsertion.
- Both adapters' `FactoryBlock.java`, `FactoryBlockEntity.java`, `FactoryProviderLogic.java`: native removal/placement lifecycle, exactly-once physical buffer ownership, memory-card copy exclusion, separately saved return queue and native pattern/upgrade drops.
- Legacy `mixin/MinecraftShutdownMixin.java` and its common Mixin registration: intercept only `MinecraftServer.stopServer`'s unique native chunk tick call, apply a one-millisecond unload budget and attempt up to 32 native task polls. Normal gameplay, original completion conditions, queued work and final saving remain native. Three retained shutdown failures precede a successful native 3,000-core run with the transformed server method independently observed.
- `tools/runtime-factory-probe/<generation>/.../HugeQuantityAudit.java`: 21 real item/water/FE transfers, signed-long boundaries, missing-stock and saturated-destination continuation, 33 job codecs and 108 binary cell NBT round trips.
- `tools/runtime-factory-probe/<generation>/.../RecoveryAudit.java`: actual world/wrench removal and native item placement, resource conservation above `Long.MAX_VALUE` in separate segments, MUST cancellation, induction resources and 64 maximum-length queued programs.
- `tools/runtime-factory-probe/<generation>/.../ChunkLifecycleAudit.java`: real remote/whole-owner/partial-cargo unload and reload, native ticket-level diagnostics, new block-entity identity, persisted remainders and exact resource accounting. Its 33 chunks lie between x=2404 and x=2916, beyond earlier fixture ticket influence; native FULL inaccessibility alone never counts as completed unloading.
- `tools/runtime-factory-probe/<generation>/.../PatternPreviewAudit.java`, `.../mixin/PatternPreviewInputMixin.java`, `ae2lf_preview_probe.mixins.json`: isolated QA screens, scoped test modifier input, and native render/tooltip comparisons. None belong to production JARs.
- `tools/runtime-factory-probe/verify-preview-pixels.py`: compare regions of original normal/Shift/cleared framebuffer images without modifying source screenshots.
- `tools/runtime-factory-probe/verify-runtime-identity.py`: complete class/resource inventory and byte equality between the frozen production JAR and ModDev's actual main source set.
- `tools/runtime-factory-probe/verify-background.ps1`, `verify-restart.ps1`, `verify-focused-background.ps1`: requested load scale, exactly-once scene coverage, strict independent report/normal-exit gates and version-isolated optional machine fixtures.
- `tools/runtime-ripper-probe/verify-background.ps1`: nine separate hidden native chain gates, exact tested production-version metadata, native CPU/fee evidence and restoration of the incoming mod manifest.
- Legacy Ripper `ShutdownReadiness.java`: read-only native holder/queue readiness snapshots before ticket removal, after ticket removal and after a real flush save. Readiness is useful diagnostics but did not itself repair the retained shutdown loop; the production fix is independently required by the legacy runtime gate.
- `archive/2026-09-12-expanded-background-qa/run-final-matrix.ps1`: generation-specific full/restart/legacy-MEK and nine-chain Ripper sequencing, immutable per-gate logs and immediate failure propagation.
- `archive/2026-09-12-expanded-background-qa/campaign/run-final-ui-matrix.ps1`: fresh final source/helper hash freeze, full3, separate-JVM finalui restart, legacy Mek final3, and nine final7 Ripper chains per generation.
- `archive/2026-09-12-expanded-background-qa/campaign/resume-modern-final-ui-matrix.ps1`: narrowly resume remaining modern gates only after the completed full3 evidence passes the full independent revalidation; preserve the original PowerShell Channel-variable failure and reject changed artifacts or existing future output.
- `archive/2026-09-12-expanded-background-qa/new-id-insertion/verify-final-id-input.py`, `campaign/verify-final-factory.py`, and `collect-final-ripper.py`: exact named-run input/factory/Ripper collectors with no automatic latest-run fallback; final reports retain raw failures and version/feature boundaries.
- `archive/2026-09-12-expanded-background-qa/restore-campaign-mods.ps1`: verifies native clients have exited, rejects unknown files and links, checks original backups and known changed helpers, archives those helpers and restores exact initial file names, bytes and hashes. `final-run-mods-restoration.json` records all ten files.
- The campaign archive also contains initial manifests, current artifact audits, bounded JFR data from the final full3 native processes, payload-limit analysis, independently reviewed screenshots, root-cause notes for failed fixtures, and machine-readable run inventories. `completion.json` binds the final strict reports to the two production JAR hashes.

English name: AE2-LoopFactory (AE2LF).
中文名称：应用能源 2 循环工厂（AE2LF）。

Current release: **[0.0.5](https://github.com/positer/AE2-LoopFactory/releases/tag/v0.0.5)**.

## 2026-09-12 complete recipe sets P and O

- `shared/src/main/java/com/example/ae2lightoptimizer/factory/FactorySelector.java`: new reserved operands `P` (every material the invocation allocated) and `O` (every product the recipe declares). A new `RecipeSet` resolver carries them; `validateParameters` rejects them without a recipe, and `membersMayOverlap` stays conservative.
- `versions/neoforge-*/.../factory/FactoryResourceSelector.java` and `FactoryJob.java`: the provider job resolves `P` from its allocated `parameters` and `O` from the decoded recipe outputs, caches both sets per job, and feeds them to every GET, PUT, HAS, exclusion, `must` and native-capability selector. `resourceType` and `matchesKey` are shared so a set lookup compares canonical identity.
- `FactoryCompiler` and `FactoryTags`: `P` and `O` are reserved words; `func P`, `import O` and `name O` are rejected, and `[PO][0-9]*` cannot be a tag or function name.
- Guide trees (both languages, both generations) document the two new operand rows, the complete-set semantics, and the recipe-free error.
- Real in-game fixture `recipe-set6-1.21.1-20260912` / `recipe-set6-26.1.2-20260912`: a real provider pattern with a two-material recipe moved the whole input set in one tick (`ticksToMoveCompleteInputSet=1`, machine contents `[1,1]`), rejected the same code on a recipe-free pattern with `P and O require a recipe-bound pattern`, and returned exactly one product through `while F has O < 1` / `get O` / `put O into source` with no machine residue. Unit gate: `FactoryRecipeSetTest` plus the existing suite, 231 tests on 1.21.1.
- `tools/runtime-factory-probe/*/ComplexFlowAudit.java`: the complex-flow fixture now ends with twelve blocking and twelve parallel dispatch rounds that share one provider, each writing its own ledger row (ticks, primary delta, peak provider jobs, blocking-gate hold ticks, codec restores, duplicate and lost returns) and requiring exact output, exact input consumption and zero provider/CPU/furnace residue at the end.

## 2026-09-12 full retest, soul capability and shutdown repair

- Fresh hidden-window full audits on the current 0.0.5 sources: 1.21.1 `full-final-1.21.1-20260912` 172 rows / 0 failed / 0 visible windows / normal shutdown, 26.1.2 `full-final-26.1.2-20260912` 174 rows / 0 failed / 0 visible windows / normal shutdown. Unit gates: 1.21.1 225 tests, 26.1.2 223 tests, zero failures. Per-item results: `archive/2026-09-12-full-retest/REPORT.md`.
- `versions/neoforge-1.21.1/src/main/java/com/example/ae2lightoptimizer/factory/FactoryNativeTransfers.java`: adds a reflective Industrial Foregoing Souls port (`industrialforegoingsouls::soul`) guarded by mod presence, plus `soulCapabilityPresent()`. Souls stay a NeoForge block capability, so they move machine-to-machine; the provider AE cache still holds AE keys only.
- `tools/runtime-factory-probe/1.21.1/.../ClientBootstrap.java`: the exit path halts the integrated server directly and waits across normal ticks instead of calling `Minecraft.disconnect`, which stopped firing client ticks and hung the 1.21.1 harness after every full audit since the channel build. A ten-minute watchdog records `shutdown-timeout.txt` rather than hanging. All three 1.21.1 shutdowns after the change are normal.
- `tools/runtime-factory-probe/*/CompatibilityAudit.java`: enumerates `AEKeyTypes.getAll()` and samples every type it can construct, samples registered item/fluid tags per loaded mod namespace, and runs a real tag-selector transfer. Unknown key types are reported as unsampled.
- Addon fixture `addon-compat10-1.21.1-20260912` (Ars Nouveau + Ars Énergistique, Industrial Foregoing + Souls, Patchouli, Curios, GeckoLib): FE, `ars_nouveau:source`, items, fluids and 102 mod-tag members all pass the cell, VM, provider subnet cache and main-network return path; a real code round moves 128 souls through the capability system.
- Mana remains blocked upstream: appbot 1.6.0-alpha.3 throws `NullPointerException: capability` in AE2's `RegisterPartCapabilitiesEvent` and aborts the mod load (`addon-compat3-1.21.1-20260912`). The in-game `channel` fixture still reports `missing-owner` on 26.1.2 while unit and editor coverage stay green; both are open items.
- `tools/runtime-factory-probe/verify-background.ps1`: new `-AddonCompat`, `-Channel` and `-ChannelOnly` switches with staged addon dependencies and bounded-shutdown acceptance.

## 2026-09-09 render and pattern-terminal repair

- `versions/neoforge-*/src/main/java/com/example/ae2lightoptimizer/factory/FactoryBlock.java`: terminal orientation now keeps AE2's six-direction property for legacy saves, forces new terminal placements horizontal, and leaves legacy vertical states non-inverted. Provider and cable behavior are unchanged.
- `versions/neoforge-*/src/main/resources/assets/ae2lightoptimizer/models/part/loop_factory_panel_{off,on}.json`: the panel preserves AE2's 100-pixel native mask union as the colored face footprint and overlays the 32-pixel Recipe Ring Solver core on the last rendered layer with alpha 96/160/255.
- `versions/neoforge-*/src/main/resources/assets/ae2lightoptimizer/models/item/loop_factory_pattern_encoding_panel.json`: the item model is exactly AE2's native pattern-terminal `display_base` structure and substitutes only `front_bright` / `front_medium` / `front_dark` with the three core masks. The 26.1.2 descriptor keeps the native five `fluix` tints; custom item-base, empty, and GUI-light overrides are absent.
- `versions/neoforge-*/src/main/java/com/example/ae2lightoptimizer/client/FactoryEditorScreen.java`: consumes the configured inventory key while editing; Escape remains the only close key.
- `versions/neoforge-*/src/main/java/com/example/ae2lightoptimizer/factory/FactoryPanelRecipeMenu.java` plus the custom screen JSON: forces processing mode, removes the crafting/smithing/stonecutting pages from view and places a native 22×22 horizontal `TabButton` at the original crafting tab position.
- `tools/generate_factory_panel_masks.py`: extracts only the Recipe Ring Solver core shape, emits three transparent tint masks, writes the native panel/item models and 26.1.2 item descriptor, validates dimensions/alpha/counts, updates both editable Blockbench projects, and writes `archive/2026-09-09-render-fix/panel-mask-report.json`.
- `tools/blockbench/ae2lf_assets.js`: Blockbench exporter now embeds/exports only the three panel masks and writes the native panel/item models plus the 26.1.2 part and item descriptors.
- `tools/runtime-factory-probe/*/BackgroundAudit.java`: adds a real client `QUICK_MOVE` insertion row for the unmodified AE2 pattern terminal and a legacy `facing=up` block-entity row. `-UiOnly` now excludes the heavy model/stress/native-flow scenes; `-PanelVisualOnly` also captures the panel item model.
- `tools/runtime-factory-probe/*/ClientBootstrap.java`: shutdown now waits across ticks for the integrated server to stop instead of throwing on the first asynchronous disconnect.
- `tools/provision_pcl_instances.ps1`: accepts existing `.bak` backups and compares only active `.jar` files during the managed whitelist check.
- `shared/.../factory/FactoryCompletion.java` and `FactorySyntaxHighlighter.java`: shared Tab / Shift+Tab indentation, indentation-preserving newline, and token classification for the editor. Predictive completion is intentionally absent.

`shared/.../factory/FactorySelector.java` parses a selector into a left-to-right expression tree over operands. `&` merges, `!` subtracts, parentheses regroup, `*` and `?` are wildcards, and `#namespace:path` resolves through a generation-supplied `TagLookup` that reads the item tag registry. The same tree serves GET, PUT, HAS, must quantities and exclusion lists, keeps `Pn`/`On` recipe validation, and is highlighted as operators. Evidence: `archive/2026-09-11-ampersand-union/REPORT.md`.

Both `loop_factory.md` guide trees now document the language as tables: selector operands, logical operators, statements, recipe references, and a separate boolean/comparison operator section in the control-flow chapter. Guide-only real-client runs capture 60 frames per language per generation, and the 26.1.2 runner walks the laid-out document to reject text outside the 404 px viewport (164 paragraphs, 622 `en_us` / 582 `zh_cn` text runs, one mandatory example paragraph).

`FactorySelector.TagLookup` is resolved per key type by `FactoryResourceSelector.RESOURCE_TAGS`, so `#tag` operands follow the item and fluid tag registries and pick up mod/datapack tags live. `FactoryExpression` accepts `has resource in Tag` and a bare `has resource`, `FactoryTags.expression` splits `A&B` machine groups for `get`/`put`/`redstone`/`has`, and `FactoryCompiler` patches `break` to the instruction after the innermost loop. Unit tests: 221 (1.21.1) and 219 (26.1.2).
- `versions/neoforge-*/.../mixin/FactoryEditorHighlightMixin.java`: renders the highlighted editor text inside the native `MultiLineEditBox` text pass; `MultiLineEditBoxAccess` exposes cursor/text-field operations without duplicating the editor.
- `versions/neoforge-*/.../client/FactoryEncoderClient.java` and `FactoryEncoderView`: project every visible machine's tag list to the HUD for through-block labels, with Shift+right-click single removal and Ctrl+Shift+right-click connected same-type removal.

Actual verification is recorded in `archive/2026-09-09-render-fix/REPORT.md`: 98 focused UI/encoder rows per generation, full runs of 172 rows (1.21.1) and 174 rows (26.1.2), unit tests 210/208, syntax highlighting and through-block tag captures, and byte-identical PCL deployment of the two 0.0.5 JARs.

Factory control nodes now set no channel flags and zero idle power. Ownership election preserves an earlier redstone edge, and `FactoryJob`, provider dispatch, induction return and provider upload do not gate on `getMainNode().isActive()`; a cable + terminal network therefore operates without an AE energy source.

`FactoryServer.signal` reads redstone for a terminal across its whole factory grid, so a signal fed into the interface cable admits a round the same way a signal on the terminal body does; other factory hosts keep their previous block-level semantics and provider dispatch is unchanged. `FactoryBlockEntity.editingFactory()` returns the terminal itself, so saving code binds the pattern without depending on the ownership election. Evidence: `archive/2026-09-11-minimal-redstone/REPORT.md`.

A player-world investigation (`archive/2026-09-11-minimal-redstone/USER-WORLD-FINDING.md`) proved the terminal button path works and isolated the remaining "nothing happens" case to selectors: `minecraft:item` is a registry ID that matches nothing, while `minecraft::item` is the item resource type. Both language guides now list that check under troubleshooting.

## Goal and status

Provide a clean, dual-generation workspace for `AE2-LoopFactory`, an AE2 addon whose universal Loop Storage cells share capacity across every dynamically registered AE2 key type and whose network blocks solve crafting cycles, accelerate eligible global crafting calculations and execute validated crafting chains. The workspace is isolated from ImmortalStorage and hard-separates incompatible Minecraft generations.

The canonical upstream is the standalone GitHub repository `https://github.com/positer/AE2-LoopFactory`. Its Git root, history, branches, tags, and remote are independent from ImmortalStorage; no repository nesting, subtree, submodule, or shared worktree is used.

Release 0.0.4 adds the Crafting Ripper and Loop Card in both maintained generations. The complete stationary and portable storage families, dynamic storage-key compatibility, recipes and existing solver services are retained. Minecraft 1.20.1 is intentionally not maintained.

Optional compatibility data is additive: a NeoForge `mod_loaded(create)` conditional Create Milling recipe and a `mod_loaded(mekanism)` conditional Mekanism Crushing recipe both use the common `c:gems/loop_crystal` item tag. Each is ignored independently when its platform is absent; neither external mod is a dependency or class reference.

Loop Storage Cells have only AE2 as a mod prerequisite. Version adapters enumerate `AEKeyTypes.getAll()` and use each key type's native `getAmountPerByte()`; optional storage addons are therefore compatibility inputs, never compile-time or metadata dependencies.

## Runtime flow

### Crafting Ripper and Loop Card

1. `CraftingRipperBlockEntity` reuses AE2's provider host contract and exposes a channel-requiring managed grid node with 5 AE/t drain.
2. `CraftingRipperLogic` 使用稀疏动态 `RipperPatternInventory`，末尾保留完整空行并兼容原 `patterns` 存档。循环卡不限制编辑；完整目标产物键匹配的手动样板独占该产物的配方选择，其他产物继续使用自动目录。
3. `CraftingRipperMenu` and `CraftingRipperScreen` subclass the pinned AE2 provider implementations. 界面尺寸为 194×210，使用 AE2 原生滚动条显示四行窗口，实际库存按需扩容；保留原生背包与升级控制面板。 Native return slots are disabled on both menu sides and hidden from the screen. 原生升级槽可安装一张循环卡，插卡后样板槽仍可正常编辑。
4. `CraftingRipperPatterns` validates real recipe identity, selected input matching, assembled results and remainders. Automatic mode enumerates encodable concrete recipes; dynamic recipes without an enumerable representative are not advertised as fabricated static outputs.
   `CraftingRipperCatalog` resolves real recipe inputs, native display/placement data and observed network component keys, with per-recipe coverage diagnostics. The server recipe manager and reload events control namespace-independent recipe additions, replacements, removals and tag changes. `CatalogCraftingPattern` preserves and validates all nine component candidate lists in the pinned 26.1.2 adapter; `CatalogSmithingPattern` provides a validated persistent definition for genuinely absent optional smithing slots.
5. `CraftingRipperExecutor` preflights the entire selected CPU job before initial stock extraction, then validates again against private CPU stock before its single 50 AE commit. `InstantCraftingBatch` performs checked, compressed resource transitions; ring schedules and seed reserves retain their existing ownership.
6. Completed products remain in the CPU for delivery and native finalization. Requester backpressure must never repeat material consumption or the 50 AE charge; reload/cancellation must preserve stock and completion state.
   `RipperNativeCrafting` handles map expansion and AE2 quartz-cutting recipes requiring native random durability handling one operation per tick. Its CPU-job-local state persists real-output identity credits, remaining operations and the single 50 AE payment for the committed job. It never changes global key equality or writes a planned placeholder as a physical result.
   `RipperMapSerialization` preserves vanilla's network-only map postprocessing marker beside normal native codecs for task definitions, final targets and private identity credits. Logical output credits can satisfy only the exact declared subsequent input, preventing fuzzy matching from consuming already completed maps. Quartz-cutting tool classification examines selected component keys before reading random remainders.
7. Portable `inventoryTick` invokes `PortableLoopEnergy` to convert stored FE into AE charge only with a Loop Card, using AE2's conversion and native charge limits.
8. `PortableLoopEnergy` registers version-local item energy capabilities: 1.21.1 uses the saturating legacy integer API, 26.1.2 uses transactional transfers and native long amount queries. Stored FE is available as a capacitor only while both FE and AE charge are positive.
9. Portable inventory operations compare the current `STORAGE_CELL_INV` component to their cached snapshot and refresh after external FE changes, preventing stale terminal contents from restoring consumed energy.

### Crafting takeover

1. Mod initialization forces AE2's `CraftingCalculation` target to load so required Mixin injections are validated immediately.
2. `CraftingCalculationMixin` intercepts the network-wide `runCraftAttempt` entry.
3. `Ae2GlobalCraftingOptimizer` queries AE2's active-machine index for the two service block entities.
4. `CraftingTakeoverPolicy` exits before graph discovery when no service is online.
5. The version adapter collects every reachable producer without fixed resource, node, or edge admission limits; the shared planner condenses SCCs and classifies the plan.
6. The policy accepts acyclic plans only for an active optimizer interface and cyclic plans only for an active ring terminal.
7. Accepted plans write compressed stock, missing, emitted, and pattern-count entries into AE2's native child simulation state.
8. The returned AE2 plan carries an owner-tagged compressed schedule: cyclic schedules belong to the ring terminal and acyclic schedules belong to the optimizer interface.
9. At CPU submission the schedule becomes a persistent execution cursor. AE2 dispatch sees only the current batch, and the cursor advances by the exact successful push count.
10. A ring-owned job routes matching final output into its crafting CPU inventory until its schedule is complete, locking seed/carrier stock away from other network jobs.
11. Completion releases only the amount above the persisted final-output seed reserve, including backlog left before a non-power-of-two final batch, and finishes only after all dispatched outputs return.
12. Returned net output reduces AE2's outstanding request even when a standalone terminal job has no requester and `CraftingLink.insert` routes zero directly; unrouted output and the retained seed remain in CPU inventory until native `finishJob` returns them to network storage.
13. Optimizer-owned and unmarked jobs never enter the cyclic output branch; parallel CPU jobs keep independent owner, schedule, cursor, and reserve state.
14. Plans without an attached schedule retain AE2's original task-map execution unchanged.

The no-service gate precedes pattern traversal and stock access. The graph-ownership gate precedes simulation-mode mutation, child state creation, extraction, missing/emitted writes, pattern counts, and callback cancellation.

### Loop Storage

1. Each version registers one housing, ten immutable supplied cores, ten finite cells, and one infinite cell. Common setup adds one `LoopStorageCellHandler` to AE2; a `Dist.CLIENT` entry point registers the eleven native drive models without loading client classes on a dedicated server.
2. When AE2 opens a cell, the handler constructs a version-local `LoopStorageCellInventory` over `AEComponents.STORAGE_CELL_INV`. Stored entries remain AE2 `GenericStack` values, so the item carries its inventory through normal AE2 component persistence.
3. Insertions accept an `AEKey` only when its type is currently present in `AEKeyTypes.getAll()` and reports a positive native `getAmountPerByte()`. No Botania, Ars Nouveau, FE, Soul Energy, or other optional-addon class is imported or declared as a dependency.
4. The adapter passes each key, key-type token, amount, and native amount-per-byte value into the loader-independent `LoopStorageAccounting` model. Accounting groups entries by key type and accumulates quotient/remainder pairs before rounding, so several partial entries share their key type's trailing byte without overflow or per-entry overcounting.
5. Every distinct `AEKey` consumes one type slot only on k tiers. The 1k through 256k tiers retain their AE2-equivalent byte capacities, per-type overheads, and 63-type limit. Each 1M through 256M tier keeps its original single-type ceiling as metadata and exposes one shared aggregate budget exactly 63 times that ceiling, with zero per-type overhead and no type counter. The infinite tier uses the same aggregate model but removes both limits explicitly instead of multiplying into `long` overflow.
6. Finite insertion uses a monotonic binary search for the largest amount whose recomputed grouped usage fits. Non-empty nested storage cells are rejected, simulated operations do not persist, and mutating operations notify the AE2 save provider or persist directly when no host exists.
7. AE2 cell state is derived from both remaining capacity and the ability to add a new type: k tiers may report `TYPES_FULL`, while M and infinite tiers report only aggregate empty/not-empty/full state and omit separate type statistics. Both adapters read idle drain from the single shared `LoopStorageTier.idleDrain()` table.