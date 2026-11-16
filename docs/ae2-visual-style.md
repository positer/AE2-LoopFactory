# AE2LF 方块与界面视觉规范

## 对照依据

两代分别读取自身固定依赖：1.21.1 对照 AE2 `19.2.17`，26.1.2 对照 AE2 `26.1.10-beta`。
依据为依赖 JAR 内的实际资源及类接口，不使用其他模组的机器外观替代 AE2 风格。

| 对照资源 / 接口 | 用途 |
| --- | --- |
| `assets/ae2/textures/block/interface.png` | 三层金属边框、灰紫钢板、内侧暗边与像素明暗关系 |
| `assets/ae2/models/part/display_base.json` | 面板 12×12×2 的前部外壳与 8×8×1 的后部连接结构 |
| `assets/ae2/models/item/display_base.json` | 物品栏面板模型与原生显示变换 |
| `BackgroundGenerator.draw`、`AE2Button` | 上传页窗口边框、按钮常态／悬停／禁用状态 |
| `TabButton.Style.HORIZONTAL` | 编辑页与配方页之间的原生 22×22 页签 |

AE2 资源归原项目所有；依赖的许可证声明为 `See GitHub repository for details`。
此规范只记录参考来源，不能改变上游资源的授权。

## 方块与不完整方块

- **接口线缆必须是完整立方体**。保持原有 blockstate、物品模型、碰撞和网络逻辑。按 2026-11-11 用户修正，材质恢复 0.0.5 初版低色差浅灰贴图，不再套用后来新增的高对比金属网格。
- 环解算、超算接口、合成撕裂者的三像素外框使用对应世代的 AE2 金属边框；内部 10×10 像素保持原有图形。离线与在线的几何及外框相同，状态只改变核心亮度。
- 网络终端正面为代码显示区，其他面为灰色设备外壳，避免四侧重复显示屏。保留全部历史 `facing` 状态；新放置方向仍由既有代码处理。
- 2026-11-16：网络终端左右面使用三道散热栅与检修板，背面独立引用 `loop_factory_terminal_back.png` 的凹入式接口；保持 16×16、不透明与 AE2 原生外框。正面、顶底面和几何不变。手持编码终端采用 32×32 透明底浅灰金属机身、蓝色缩进代码屏与底部按键；物品模型与显示变换不变。
- 编码面板继续直接引用 AE2 的薄型外壳，叠加既有透明核心遮罩。物品模型、世界模型、染色索引、亮度和状态灯沿用原生合同。
- 不重绘或重新编码存储核心、存储元件／便携元件、晶体素材和现有面板遮罩。

## 界面

- 合成撕裂者继续继承原生样板供应器页面；便携元件继续使用原生存储终端。
- 编辑页使用原生背景、槽位、图标按钮与横向页签；状态文字按编辑区可用宽度截断，为原生字数计数器预留空间，并保留完整悬停提示。
- 上传页使用原生背景生成器与 `AE2Button`，按可见窗口尺寸和实际条目数居中布局；未命名供应器显示本地化机器名称，UUID 只用于提交。长名称使用按钮原生文字处理并提供完整提示；空列表禁用行，只有一个页面时禁用翻页。
- 1.21.1 在 `renderBackground` 中先调用原生背景再绘制窗口／标题，避免后续模糊覆盖；26.1.2 使用自身的渲染状态提取流程，不共享 Minecraft 绘制 API。
- 保留服务端提供者列表、UUID 上传、原生文本框、光标、选择、高亮和拖入 ID 的既有行为。

## 编码面板与原版加工页

- 配方页直接继承原版样板编码终端的背景、标题、搜索、排序、加工输入／输出与空白／已编码样板槽位；保留“样板编码”分区标题，不把共享标题误认为工作台专属文本。
- 仍只开放加工模式；工厂代码页签占用原版工作台页签位置，其他未实现模式继续隐藏。
- 共用代码页标题沿用原生 `top=6`；背包与快捷栏直接继承 `common/player_inventory.json`：`left=8`，底部偏移 84／26，背包标题底部偏移 95。
- 代码页返回配方页的页签使用 `TAB_PROCESSING`，22×22、水平样式，与右边框重叠 3px，顶部对齐编辑区。配方页打开代码页仍保留既有 `TAB_CRAFTING` 合同。
- `-PanelUiOnly` 在两代、两语言下打开真正的原版与工厂页面，记录实际窗口／槽位坐标、截图及原生页签回调。对照原版标题像素与背包底部锚点；旧缺失标题截图必须被检出。

### 编码面板专项验收（2026-11-10）

两代各 44 个客户端场景通过并正常退出，262／260 项单测通过。四组版本／语言组合的六类槽位与原版一致；“样板编码”标题和代码页背包标题像素差异均为 0。旧缺失标题截图作为反例被检出。证据位于 `../Minecraft Forge Workspace/archive/20261110-100300-ae2lf-panel-ui/`，本轮未部署或发布。

## 生成与验证

从仓库根目录执行，先运行需要的既有生成器，最后收口：

```powershell
python tools/align_ae2_visuals.py
python tools/verify_ae2_visual_style.py --baseline 34e94a4 --report ../"Minecraft Forge Workspace"/archive/<本次目录>/after/static-verification.json
powershell -NoProfile -ExecutionPolicy Bypass -File build-1.21.1.ps1 --offline
powershell -NoProfile -ExecutionPolicy Bypass -File build-26.1.2.ps1 --offline
```

`align_ae2_visuals.py` 同步两代 PNG、终端模型与三类 Blockbench 文件；重复生成必须字节一致。
Blockbench 插件导出后自动运行该收口步骤，防止旧外壳重新覆盖产品资源。

只重新生成本轮两类终端：`python tools/align_ae2_visuals.py --only-terminal`。此路径不改正面和顶底面；同时更新两代侧背贴图、手持贴图、终端模型与 Blockbench 内嵌图。手持像素源统一在 `handheld_encoder()`，Blockbench 导出不再维护另一份绘制代码。专项检查：`python tools/verify_ae2_visual_style.py --terminal-only --baseline <修改前提交> --report <报告路径>`，覆盖模型分面、历史状态、受保护贴图、透明度、编辑源与重复生成。

客户端使用 `tools/runtime-factory-probe/verify-background.ps1 -StyleOnly`：检查真实物品渲染、世界方块／面板及现有 UI 场景，保留逐场景截图、资源身份与正常退出证据。
`-EvidenceRoot` 可将结果直接写入枢纽归档。原生夹具中的菜单操作是自动化回调／数据包，不等同于人工鼠标验收。

## 本轮验收（2026-11-10）

- 1.21.1：262 项单测、172 项真实客户端专项通过。
- 26.1.2：260 项单测、172 项真实客户端专项通过。
- 两代均通过逐场景覆盖、真实 framebuffer、资源／类与最终 JAR 一致性、正常退出及独立证据回读。
- 1.21.1 上传页另用 2688 个无遮挡边框像素对照新版：修正后差异为 0，旧模糊反例差异为 2688，防止只检查屏幕类名而漏掉绘制顺序问题。
- 空列表与多条长名称列表是明确标注的客户端显示夹具；恢复真实服务器列表后再验证实际上传。
- 10 个开发 `run/mods` 原有文件已恢复并逐一回读哈希。本轮未部署 PCL、未推送或发布。
- 结果与截图：`../Minecraft Forge Workspace/archive/20261110-090500-ae2lf-visual-style/`。

## 2026-11-11 工厂网络线缆还原

`design/loop_factory/references/loop_factory_cable_original.png` 为 `1ea5964` 发布版本的原始贴图；双代运行资源与 Blockbench 内嵌纹理均从此逐字节复制。`python tools/align_ae2_visuals.py --only-cable` 只收口线缆；完整生成和 Blockbench 导出也采用同一来源。原版完整立方体与 UV 保持不变。
