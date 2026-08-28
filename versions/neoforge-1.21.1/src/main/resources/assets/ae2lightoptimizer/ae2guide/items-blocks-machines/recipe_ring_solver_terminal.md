---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Recipe Ring Solver Terminal
  icon: ae2lightoptimizer:recipe_ring_solver_terminal
  position: 900
categories:
- devices
item_ids:
- ae2lightoptimizer:recipe_ring_solver_terminal
---

# Recipe Ring Solver Terminal

<BlockImage id="ae2lightoptimizer:recipe_ring_solver_terminal" scale="6" />

## Crafting

<RecipeFor id="ae2lightoptimizer:recipe_ring_solver_terminal" />

The Recipe Ring Solver Terminal is a UI-free service for cyclic AE2 crafting calculations. Attach it to an ME Network with an available channel and power. Its face lights cyan when the managed grid node is active.

This block independently takes over reachable cyclic crafting graphs and solves their strongly connected components. It deliberately leaves ordinary acyclic jobs untouched. A [Supercomputing Crafting Optimizer Interface](supercomputing_crafting_optimizer_interface.md) can independently accelerate those acyclic jobs and complements the terminal when both are online.

## Material reserve

Self-growth calculations preserve enough existing material for up to 16 additional cycles.

- If existing stock cannot cover 16 cycles, all of it is reserved and none of it reduces the calculated demand.
- If stock exceeds 16 cycles, the 16-cycle reserve remains untouched and only the surplus reduces demand.
- Checked 64-bit arithmetic is used. An overflow or unsupported recipe structure falls back to AE2's normal calculation.

The live integration handles single-pattern growth and multi-pattern nested growth SCCs without expanding every application. Reachable cycles remain takeover candidates regardless of node, pattern, or edge count, and planning capacity scales with the discovered graph. Dead 1:1 rings, overflow, and graphs for which an executable schedule cannot be proven fall back safely.

## Network requirements

- 1 AE2 channel
- 2 AE/t idle power
- Connection from any face
- No screen, menu, or local configuration
