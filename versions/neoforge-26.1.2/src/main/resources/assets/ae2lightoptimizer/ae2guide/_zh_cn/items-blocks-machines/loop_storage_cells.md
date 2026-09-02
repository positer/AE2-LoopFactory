---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: 循环存储磁盘
  icon: ae2lightoptimizer:1k_loop_storage_cell
  position: 920
categories:
- tools
item_ids:
- ae2lightoptimizer:loop_storage_cell_housing
- ae2lightoptimizer:1k_loop_storage_core
- ae2lightoptimizer:4k_loop_storage_core
- ae2lightoptimizer:16k_loop_storage_core
- ae2lightoptimizer:64k_loop_storage_core
- ae2lightoptimizer:256k_loop_storage_core
- ae2lightoptimizer:1m_loop_storage_core
- ae2lightoptimizer:4m_loop_storage_core
- ae2lightoptimizer:16m_loop_storage_core
- ae2lightoptimizer:64m_loop_storage_core
- ae2lightoptimizer:256m_loop_storage_core
- ae2lightoptimizer:1k_loop_storage_cell
- ae2lightoptimizer:4k_loop_storage_cell
- ae2lightoptimizer:16k_loop_storage_cell
- ae2lightoptimizer:64k_loop_storage_cell
- ae2lightoptimizer:256k_loop_storage_cell
- ae2lightoptimizer:1m_loop_storage_cell
- ae2lightoptimizer:4m_loop_storage_cell
- ae2lightoptimizer:16m_loop_storage_cell
- ae2lightoptimizer:64m_loop_storage_cell
- ae2lightoptimizer:256m_loop_storage_cell
- ae2lightoptimizer:infinite_loop_storage_cell
- ae2lightoptimizer:portable_1k_loop_storage_cell
- ae2lightoptimizer:portable_4k_loop_storage_cell
- ae2lightoptimizer:portable_16k_loop_storage_cell
- ae2lightoptimizer:portable_64k_loop_storage_cell
- ae2lightoptimizer:portable_256k_loop_storage_cell
- ae2lightoptimizer:portable_1m_loop_storage_cell
- ae2lightoptimizer:portable_4m_loop_storage_cell
- ae2lightoptimizer:portable_16m_loop_storage_cell
- ae2lightoptimizer:portable_64m_loop_storage_cell
- ae2lightoptimizer:portable_256m_loop_storage_cell
- ae2lightoptimizer:portable_infinite_loop_storage_cell
---

# 循环存储磁盘

<Column>
  <Row>
    <ItemImage id="ae2lightoptimizer:1k_loop_storage_cell" scale="4" />
    <ItemImage id="ae2lightoptimizer:4k_loop_storage_cell" scale="4" />
    <ItemImage id="ae2lightoptimizer:16k_loop_storage_cell" scale="4" />
    <ItemImage id="ae2lightoptimizer:64k_loop_storage_cell" scale="4" />
    <ItemImage id="ae2lightoptimizer:256k_loop_storage_cell" scale="4" />
  </Row>
  <Row>
    <ItemImage id="ae2lightoptimizer:1m_loop_storage_cell" scale="4" />
    <ItemImage id="ae2lightoptimizer:4m_loop_storage_cell" scale="4" />
    <ItemImage id="ae2lightoptimizer:16m_loop_storage_cell" scale="4" />
    <ItemImage id="ae2lightoptimizer:64m_loop_storage_cell" scale="4" />
    <ItemImage id="ae2lightoptimizer:256m_loop_storage_cell" scale="4" />
    <ItemImage id="ae2lightoptimizer:infinite_loop_storage_cell" scale="4" />
  </Row>
</Column>

循环存储磁盘使用同一份字节预算存放所有已注册到 AE2 的存储键类型。物品、流体、FE、植物魔法魔力、新生魔艺魔源、灵魂涌动，以及其他 AE 附属提供的存储类型，都可以在对应附属已加载时共存于同一张磁盘。模组只要求 AE2 作为前置；所有可选存储附属均通过 AE2 的键类型注册表动态发现，不会成为前置依赖。

占用量采用每种已注册键类型自己的原生“每字节数量”。按 AE2 标准比例，1 个物品与 1,000 单位流体、FE、魔力、魔源或灵魂涌动占用相同空间；其他附属类型的比例由其自身注册信息决定。

## 容量与种类

| 磁盘 | 字节容量 | 种类上限 |
| --- | ---: | ---: |
| 1k | 1,024 | 63 |
| 4k | 4,096 | 63 |
| 16k | 16,384 | 63 |
| 64k | 65,536 | 63 |
| 256k | 262,144 | 63 |
| 1M | 66,060,288（共享） | 不统计 |
| 4M | 264,241,152（共享） | 不统计 |
| 16M | 1,056,964,608（共享） | 不统计 |
| 64M | 4,227,858,432（共享） | 不统计 |
| 256M | 16,911,433,728（共享） | 不统计 |
| 无限 | 无限制 | 无限制 |

k 级磁盘的字节容量和 63 种类型上限与对应 AE2 物品磁盘相同。M 级采用类似 AE2Things 深度磁盘的聚合池：总量为该档原单类上限的 63 倍，所有已注册存储键共同占用，不划分类别分区，也不单独显示种类统计。无限循环存储磁盘使用相同聚合模型，但总量和种类均无限，同样不显示单独的种类统计。

## 外壳与有限磁盘

<RecipeFor id="ae2lightoptimizer:loop_storage_cell_housing" />

每张有限磁盘都有两种配方：用对应循环存储核心组成的有序配方，以及将该核心与一个循环存储磁盘外壳组合的无序配方。

<Column>
  <Row>
    <RecipeFor id="ae2lightoptimizer:1k_loop_storage_cell" />
    <RecipeFor id="ae2lightoptimizer:4k_loop_storage_cell" />
    <RecipeFor id="ae2lightoptimizer:16k_loop_storage_cell" />
    <RecipeFor id="ae2lightoptimizer:64k_loop_storage_cell" />
    <RecipeFor id="ae2lightoptimizer:256k_loop_storage_cell" />
  </Row>
  <Row>
    <RecipeFor id="ae2lightoptimizer:1m_loop_storage_cell" />
    <RecipeFor id="ae2lightoptimizer:4m_loop_storage_cell" />
    <RecipeFor id="ae2lightoptimizer:16m_loop_storage_cell" />
    <RecipeFor id="ae2lightoptimizer:64m_loop_storage_cell" />
    <RecipeFor id="ae2lightoptimizer:256m_loop_storage_cell" />
  </Row>
</Column>

## 循环存储核心

<Column>
  <Row>
    <RecipeFor id="ae2lightoptimizer:1k_loop_storage_core" />
    <RecipeFor id="ae2lightoptimizer:4k_loop_storage_core" />
    <RecipeFor id="ae2lightoptimizer:16k_loop_storage_core" />
    <RecipeFor id="ae2lightoptimizer:64k_loop_storage_core" />
    <RecipeFor id="ae2lightoptimizer:256k_loop_storage_core" />
  </Row>
  <Row>
    <RecipeFor id="ae2lightoptimizer:1m_loop_storage_core" />
    <RecipeFor id="ae2lightoptimizer:4m_loop_storage_core" />
    <RecipeFor id="ae2lightoptimizer:16m_loop_storage_core" />
    <RecipeFor id="ae2lightoptimizer:64m_loop_storage_core" />
    <RecipeFor id="ae2lightoptimizer:256m_loop_storage_core" />
  </Row>
</Column>

## 无限磁盘

无限循环存储磁盘使用 AE2 爆炸转换配方。将正好 64 个 <ItemLink id="ae2lightoptimizer:256m_loop_storage_core" /> 与 1 个 <ItemLink id="ae2lightoptimizer:loop_storage_cell_housing" /> 放在一起，再使其受到 TNT 爆炸；转换会消耗这 65 个输入并生成 1 张无限循环存储磁盘。

<RecipeFor id="ae2lightoptimizer:infinite_loop_storage_cell" />

## 便携循环存储磁盘

每个循环存储档位都有对应便携版本。它直接复用 AE2 原生便携磁盘的终端、电池、能源卡扩容、待机耗电和带供能存取逻辑，同时保留对应循环磁盘的容量与动态键类型兼容。创造模式同时注册空 AE 能量与满 AE 能量两种物品栈。

每个有限级便携磁盘均可无序合成：ME 箱子、能源元件与对应循环存储磁盘；或者 ME 箱子、能源元件、循环存储磁盘外壳与对应循环存储核心。无限级没有核心，因此无限便携循环存储磁盘只有“ME 箱子 + 能源元件 + 无限循环存储磁盘”这一条配方。

同时启用 AE2 原生空便携磁盘拆解：潜行使用空便携循环磁盘可取回 ME 箱子、能源元件和外壳/核心部件（无限级取回无限循环存储磁盘），剩余 AE 能量会回灌到取回的能源元件。
