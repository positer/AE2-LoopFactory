---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: 配方环解算终端
  icon: ae2lightoptimizer:recipe_ring_solver_terminal
  position: 900
categories:
- devices
item_ids:
- ae2lightoptimizer:recipe_ring_solver_terminal
---

# 配方环解算终端

<BlockImage id="ae2lightoptimizer:recipe_ring_solver_terminal" scale="6" />

## 合成

<RecipeFor id="ae2lightoptimizer:recipe_ring_solver_terminal" />

配方环解算终端是服务于 AE2 成环合成计算的无界面网络设备。将它接入拥有空闲频道和电力的 ME 网络；托管网络节点进入活动状态后，方块正面的闭环标记会亮起青色。

本方块会独立接管可达的成环合成图并解算其中的强连通分量，同时有意不接管普通无环任务。[超算合成优化接口](supercomputing_crafting_optimizer_interface.md)可独立加速无环任务；两方块同时在线时职责互补。

## 材料保留规则

16 次循环数值仅作为执行期间的种子元数据，不会从当前订单可用库存中扣除。

- 循环输出种子在调度完成前由执行中的 CPU 保留。
- 环外输入按当前订单完整检查，有限订单不会把固定预留量误报为缺少材料。
- 所有数量使用带溢出检查的 64 位整数；发生溢出或遇到不受支持的配方结构时，交还 AE2 原有计算流程。

当前实机接管单配方增长环与多配方嵌套增长强连通分量，并且不会逐次展开模板应用。可达成环图不会因为资源、配方或边数量达到固定阈值而退出，规划容量会随已发现图规模增长。1:1 死环、算术溢出以及无法证明存在可执行调度的图会安全交还 AE2。

## 网络需求

- 1 个 AE2 频道
- 待机功耗 2 AE/t
- 任意面均可连接
- 无界面、无菜单、无本地设置
