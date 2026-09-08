---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: 超算合成优化接口
  icon: ae2lightoptimizer:supercomputing_crafting_optimizer_interface
  position: 910
categories:
- devices
item_ids:
- ae2lightoptimizer:supercomputing_crafting_optimizer_interface
---

# 超算合成优化接口

<BlockImage id="ae2lightoptimizer:supercomputing_crafting_optimizer_interface" scale="6" />

## 合成

<RecipeFor id="ae2lightoptimizer:supercomputing_crafting_optimizer_interface" />

超算合成优化接口是 AE2-LoopFactory 的计划执行与加速设备。将它接入拥有空闲频道和电力的 ME 网络；托管网络节点进入活动状态后，方块正面的并行计算线路会亮起蓝白与琥珀色。

对于无环合成图，本接口会接管 AE2 的网络全局计算入口，统一聚合共享依赖，并以压缩计数保存模板次数，不逐次展开每一次应用。因此 T 级和 P 级重复次数始终作为 64 位计数处理，而不会变成数万亿次合成树操作。

本接口可独立处理普通无环任务。只有可达配方图中存在环时才需要[配方环解算终端](recipe_ring_solver_terminal.md)；环终端独立负责 SCC 解算，两方块同时在线时，本接口继续负责全局加速。

## 安全回退

全局模型会纳入多生产者、已列出的替代输入、副产物和容器返还物。可达无环图不会因为资源、配方或边数量达到固定阈值而退出，规划容量会随图规模增长。大宗重复数量保持压缩；不同图元素仍具有不可避免的线性遍历成本。发生带检查算术溢出或无法证明存在可执行整数调度时，会在修改状态前交还 AE2。

## 网络需求

- 1 个 AE2 频道
- 待机功耗 8 AE/t
- 任意面均可连接
- 无界面、无菜单、无本地设置
