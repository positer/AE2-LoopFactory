---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: 循环物质聚合器
  icon: ae2lpt:loop_condenser
  position: 936
categories:
- devices
item_ids:
- ae2lpt:loop_condenser
---

# 循环物质聚合器

<BlockImage id="ae2lpt:loop_condenser" scale="5" />
<RecipeFor id="ae2lpt:loop_condenser" />

用元件工作台给循环存储磁盘标记物品，再把磁盘放入聚合器的模板槽。将聚合器接入有电且有可用频道的 ME 网络，在界面中选择物质球、奇点或销毁。

每个服务端 tick 会从 ME 存储抽取符合条件的物品并将产物送回网络。模板磁盘装反相卡可排除标记物，装模糊卡可启用模糊匹配。网络拒收时会先保留物质余额，下次再尝试结算。也可使用聚合器原生输入槽手动处理。
