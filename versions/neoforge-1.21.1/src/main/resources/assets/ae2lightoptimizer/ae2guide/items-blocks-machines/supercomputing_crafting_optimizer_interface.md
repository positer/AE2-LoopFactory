---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Supercomputing Crafting Optimizer Interface
  icon: ae2lightoptimizer:supercomputing_crafting_optimizer_interface
  position: 910
categories:
- devices
item_ids:
- ae2lightoptimizer:supercomputing_crafting_optimizer_interface
---

# Supercomputing Crafting Optimizer Interface

<BlockImage id="ae2lightoptimizer:supercomputing_crafting_optimizer_interface" scale="6" />

## Crafting

<RecipeFor id="ae2lightoptimizer:supercomputing_crafting_optimizer_interface" />

The Supercomputing Crafting Optimizer Interface is the execution and acceleration service of AE2-LoopFactory. Attach it to an ME Network with an available channel and power. Its compute lanes light blue and amber when the managed grid node is active.

For non-cyclic crafting graphs, the interface takes over AE2's network-wide calculation entry, aggregates shared dependencies globally, and keeps pattern counts compressed instead of expanding every application. Tera- and peta-scale repetition counts therefore remain 64-bit counters rather than trillions of tree operations.

The interface works independently for ordinary acyclic jobs. A [Recipe Ring Solver Terminal](recipe_ring_solver_terminal.md) is only required when the reachable graph contains a cycle; the terminal independently owns SCC solving while this interface continues to provide global acceleration when both blocks are online.

## Safe fallback

Multiple producers, listed substitute inputs, byproducts, and container remainders are included in the global model. Reachable acyclic graphs remain takeover candidates regardless of resource, pattern, or edge count, and planning capacity grows with graph size. Large repeated quantities stay compressed; distinct graph elements retain their unavoidable linear traversal cost. Checked arithmetic overflow or failure to prove an executable integer schedule still falls back to AE2 before mutation.

## Network requirements

- 1 AE2 channel
- 8 AE/t idle power
- Connection from any face
- No screen, menu, or local configuration
