---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: 循环卡
  icon: ae2lightoptimizer:loop_card
  position: 905
categories:
- devices
item_ids:
- ae2lightoptimizer:loop_card
---

# 循环卡

<ItemImage id="ae2lightoptimizer:loop_card" scale="4" />

<RecipeFor id="ae2lightoptimizer:loop_card" />

将一个高级卡与一个循环水晶任意摆放，即可无序合成循环卡。
循环卡可安装在[合成撕裂者](crafting_ripper.md)或任意档位的
[便携循环存储磁盘](loop_storage_cells.md)中，每台设备只需一张。

## 合成撕裂者

安装后发布自动发现、可编码的工作台、锻造台与切石机配方。
原样板保留在四排灰色槽中，可以取出；移除循环卡前不能放入新样板。
取出循环卡后立即恢复剩余实体样板的正常使用。

自动发现包含特殊工作台配方。成书、染色装备等带组件变体从网络真实库存中获取，
新物品键入库后自动刷新。地图扩大等需要世界后处理的配方使用撕裂者自身的
原生逐步续作，不执行瞬间撕裂。

## 便携充能

在便携磁盘已有的升级槽安装循环卡后，已存储的 FE 会按 AE2 配置的转换比例
与原生充能上限转成自身 AE 电量，也可以从空 AE 电池开始充电。
不会凭空产生 FE；电池充满或卡被取出后停止转换。

需要由 AE2 能量存储附属提供 FE 存储键，AE2LO 不新增硬前置。
例如先在便携磁盘中存入 FE，再安装循环卡并随身携带，卡会消耗内部 FE 为终端电池补电。

## 随身电容器

便携磁盘在**存有 FE 且 AE 电量大于零**时，通过 NeoForge 原生物品能量能力暴露 FE。
其他模组使用这一标准能力查询或提取电量时，可以把它作为随身能源。
此能力本身不要求循环卡，循环卡负责自动补充 AE 电量。

AE 电量归零后，电容器暂不可用，重新充电即可恢复。
旧版整数能量接口的查询值在其上限截断，但真实存储仍保持 64 位数量；
新版能量接口直接报告 long 数量。
