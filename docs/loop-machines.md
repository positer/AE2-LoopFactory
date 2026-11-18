# 循环聚合器与循环进刻器

2026-11-17 的机器验收使用基于 `8ad30f3` 的独立工作树。2026-11-18 的进刻器修复以主仓库 `cdd3649` 为基线。两代代码各自使用对应 Minecraft、NeoForge、AE2 API。

## 循环物质聚合器

- 继承 AE2 原生聚合器，沿用原生菜单、输入、流体输入、输出模式和离线操作。原生模板槽仅在循环聚合器中额外接受循环磁盘；普通聚合器的槽位规则保持原生。
- 节点使用 `REQUIRE_CHANNEL`、自身待机功率 `0 AE/t`。原生聚合器没有网络节点生命周期，因此显式通过 `clearRemoved → scheduleInit → onReady` 创建节点，并在拆除、区块卸载时销毁节点，随方块实体保存节点数据。
- 在线且所在区块可 tick 时，每 5 个服务端 tick 执行一次。模板槽必须是已分区标记的循环磁盘；空槽、普通存储组件和未标记磁盘都不触发网络转换。
- 普通模式匹配标记物品；反相卡改为排除标记物品。仅安装模糊卡时启用模糊匹配。循环磁盘提供两个原生持久化升级槽，支持原生工作台编辑。
- 网络物料折算采用 AE2 原生 `amount / amountPerOperation`，先记入原生内部余额，再按当前原生模式的额度结算并在本次调用内回网。网络拒收的产物不扣余额，之后重试；已接收部分按实际数量扣余额。TRASH 模式按原生语义销毁输入。
- 自动网络转换不调用会裁剪容量并填充实体输出槽的 `addPower`。原生手动输入仍走原生处理。
- 贴图只在 `x=3..12, y=3..12` 的核心区变灰白；其余 156 个像素逐像素与对应 AE2 原版一致。

## 循环进刻器

- 部件沿用原生输出总线的几何和安装面选区。空手右键打开倍率界面，四个槽位接受加速卡。方块使用完整立方体和元件工作台材质，顶面与侧面的蓝色灯带替换为绿色。放置时顶面对准被点击的目标，支持 AE2 六向旋转。
- 两种形态都通过 AE2 原生节点的活动状态判断在线。无控制器网络可以供电，需要频道的机器继续遵循原生频道分配。每服务端 tick 对朝向目标追加当前倍率次数的方块实体 tick 和原生 randomTick；世界原有 tick、JDT 与火把调度不被取消或改写。
- 默认无卡范围 `1x–4x`，默认跟随最大值；四张原生加速卡分别将上限乘 2，最高 `64x`。界面使用 AE2 原生背景、背包、升级面板、去除原版边框覆盖的 `AETextField`，以及横向使用的 `Scrollbar.BIG` 和原生样板管理终端轨道。`LoopRateSlider` 同时旋转原生绘制与鼠标坐标，不另造滑块材质。页面为紧凑的 `176×136`，控制行与背包标题约留 4px。滑条按 1x 步进；页面只有输入框这一处倍率数字，滑条不另绘数字或范围，两者共享同一份倍率并即时互相同步，包括输入框仍聚焦时。
- 用户选择按安装卡片的倍率归一化保存：例如 `3x → 加一张卡 6x → 移除卡 3x`。小于 1 的暂存比例在显示时至少取 1，保留原比例供增卡恢复；旧整数存档迁移为相同比例。四张卡均在拆卸时回收。
- 每次追加调用后重新获取方块状态与实体，不继续使用已被替换的实体；不加载目标区块、不加票据。线程局部重入保护在 `finally` 中释放，避免循环递归。

## 复现命令

在独立仓库根目录执行。初始化脚本必须传绝对路径，避免 Gradle 按版本子工程解析相对路径。

```powershell
./build-1.21.1.ps1 --offline --no-daemon -I "$PWD/tools/runtime-ripper-probe/probe.init.gradle" ripperProbeJar
./build-26.1.2.ps1 --offline --no-daemon -I "$PWD/tools/runtime-ripper-probe/probe.init.gradle" ripperProbeJar
powershell -NoProfile -File tools/runtime-ripper-probe/verify-background.ps1 -Generation 1.21.1 -Chain machine_audit -EvidenceRoot '../Minecraft Forge Workspace/archive/20261117-220000-ae2lf-machine-audit-worktree/evidence' -EvidenceName ripper-machines-1.21.1-new
powershell -NoProfile -File tools/runtime-ripper-probe/verify-background.ps1 -Generation 26.1.2 -Chain machine_audit -EvidenceRoot '../Minecraft Forge Workspace/archive/20261117-220000-ae2lf-machine-audit-worktree/evidence' -EvidenceName ripper-machines-26.1.2-new
```

`machine_audit` 使用真实物理相邻的 AE2 网络、真实循环磁盘、原生槽位、熔炉与作物。共存场景要求在该独立树对应代的 `run/mods/` 同时放入 JDT 和 Torcherino，另建一台机器同时叠加三种加速。缺少这两个可选模组时只做基础场景，不宣称共存通过。每次使用新证据名；报告、窗口截图、制品身份、正常退出、原 mods 恢复分别验收。

启动器中的旧链名 `loop_condenser` 与 `loop_ticker` 均转到完整的 `machine_audit`，避免继续使用没有正确建网、配置输出模式或测试槽位的早期弱夹具。

像素与几何检查：

```powershell
python tools/verify_loop_machine_assets.py --ae2-jar '<该代 AE2 原版 JAR>' --generation 1.21.1
python tools/verify_loop_machine_assets.py --ae2-jar '<该代 AE2 原版 JAR>' --generation 26.1.2
```

最终验收状态、失败轮次、制品哈希与合并说明见枢纽归档 `../Minecraft Forge Workspace/archive/20261117-220000-ae2lf-machine-audit-worktree/README.md`。
