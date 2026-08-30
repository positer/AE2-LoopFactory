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

The 16-cycle value is execution seed metadata; it never reduces the current order's usable inventory.

- Cyclic output seeds remain reserved by the executing CPU until dispatch completes.
- External inputs are checked against the current order in full, so finite orders never report a fixed reserve as missing.
- Checked 64-bit arithmetic is used. An overflow or unsupported recipe structure falls back to AE2's normal calculation.

The live integration handles single-pattern growth and multi-pattern nested growth SCCs without expanding every application. Reachable cycles remain takeover candidates regardless of node, pattern, or edge count, and planning capacity scales with the discovered graph. Dead 1:1 rings, overflow, and graphs for which an executable schedule cannot be proven fall back safely.

## Network requirements

- 1 AE2 channel
- 2 AE/t idle power
- Connection from any face
- No screen, menu, or local configuration
