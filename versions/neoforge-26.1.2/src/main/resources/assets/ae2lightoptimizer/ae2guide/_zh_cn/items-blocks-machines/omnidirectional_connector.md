---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: 全向连接器
  icon: ae2lightoptimizer:omnidirectional_connector
  position: 920
categories:
- devices
item_ids:
- ae2lightoptimizer:omnidirectional_connector
---

# 全向连接器

<BlockImage id="ae2lightoptimizer:omnidirectional_connector" scale="6" />

<RecipeFor id="ae2lightoptimizer:omnidirectional_connector" />

右键打开频道配置页，创建或选择命名连接频道。同一频道内所有已加载的连接器连接到一个 AE2 原生虚拟节点，支持跨维度。连接面、放置方向和几何形状沿用无线访问点，外壳、灯光、物品和粒子中的紫色细节全部改为白色。

连接器不消耗 AE 能量，也不占用设备频道；传递的频道仍由 AE2 原生寻路与容量规则分配。网络接入超算合成优化接口后，容量翻倍一次，不随超算数量叠加。连接器不会强制加载区块，卸载或拆除时断开对应虚拟连接。

无线访问点、循环水晶与缠绕态奇点无序合成，产出四个连接器。使用 AE2 扳手拆卸时，返回的连接器物品保留已选择的连接频道。


已连接频道且处于 AE2 原生在线状态时，连接器发出缓慢向外扩散的白色小粒子，轨迹限制在自身单方块内；解绑或离线后停止。该特效不增加 AE 能耗。

## 循环工厂无线结构

把连接器的底面直接压在循环工厂网络终端上，或压在子网面朝上的工厂样板供应器上，结构即成型：所选频道网络与该机器的**主网**相连；供应器的子网不会被并入，工厂侧保持隔离。成型时连接器在自身方块内显示一圈环绕白色粒子，并且仍可正常打开频道配置界面。

底面是工厂安装位，不暴露物理接线，因此下方机器不会通过相邻关系把频道网络或供应器子网短接。拆除机器后桥接与环绕粒子一并消失。
