# Changelog

## 0.0.2

### English

- Standardized the bilingual product identity as Applied Energistics 2 Lightweight Optimization (AE2LO).
- Added Loop Crystal, Loop Crystal Fragment, Loop Crystal Block, and Loop Crystal Powder with complete item models, names, recipes, and a dedicated creative tab.
- Breaking AE2's Mysterious Cube additionally drops one Loop Crystal.
- Added exact decomposition/recombination and externally fueled growth-loop solver coverage.
- Added additive optional machine compatibility: Create Milling and Mekanism Crushing each convert the common `c:gems/loop_crystal` input tag into Loop Crystal Powder when their respective mod is loaded; neither is a required dependency.

### 简体中文

- 统一双语产品名称为应用能源 2 轻量优化（AE2LO）。
- 新增循环水晶、循环水晶碎片、循环水晶块和循环水晶粉，包含完整物品模型、名称、配方与独立创造模式分类。
- 破坏 AE2 神秘方块时额外掉落一个循环水晶。
- 新增分解复合不增长环与外部材料驱动增殖环的解算测试。
- 新增叠加式可选机器兼容：机械动力磨粉与 Mekanism 粉碎分别使用通用 `c:gems/loop_crystal` 输入标签转为循环水晶粉，加载哪个平台就独立启用哪个配方，均不设为前置依赖。

## 0.0.1

### English

- Added the **Recipe Ring Solver Terminal**, which takes over cyclic AE2 crafting graphs, solves self-growth and irreducible multi-recipe loops, protects seed materials, and drives cyclic jobs through correct completion and CPU release.
- Added the **Supercomputing Crafting Optimizer Interface**, which accelerates non-cyclic AE2 crafting calculation and dispatch through compressed planning for complex, large-quantity crafting trees.

### 简体中文

- 新增**配方环解算终端**，用于接管 AE2 成环合成图，解算单配方自增长循环与多配方不可约环，保护种子材料，并正确驱动循环任务完成及释放合成 CPU。
- 新增**超算合成优化接口**，通过压缩式规划加速 AE2 非成环合成的计算与发配，适用于复杂且大宗量的合成树。
