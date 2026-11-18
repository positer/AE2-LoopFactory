---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Supercomputing Crafting Optimizer Interface
  icon: ae2lpt:supercomputing_crafting_optimizer_interface
  position: 910
categories:
- devices
item_ids:
- ae2lpt:supercomputing_crafting_optimizer_interface
---

# Supercomputing Crafting Optimizer Interface

<BlockImage id="ae2lpt:supercomputing_crafting_optimizer_interface" scale="6" />

## Crafting

<RecipeFor id="ae2lpt:supercomputing_crafting_optimizer_interface" />

The Supercomputing Crafting Optimizer Interface is the execution and acceleration service of AE2 LoopTech. Attach it to an ME Network with an available channel and power. Its compute lanes light blue and amber when the managed grid node is active.

For non-cyclic crafting graphs, the interface takes over AE2's network-wide calculation entry, aggregates shared dependencies globally, and keeps pattern counts compressed instead of expanding every application. Tera- and peta-scale repetition counts therefore remain 64-bit counters rather than trillions of tree operations.

The interface works independently for ordinary acyclic jobs. A [Recipe Ring Solver Terminal](recipe_ring_solver_terminal.md) is only required when the reachable graph contains a cycle; the terminal independently owns SCC solving while this interface continues to provide global acceleration when both blocks are online.

## Safe fallback

Multiple producers, listed substitute inputs, byproducts, and container remainders are included in the global model. Reachable acyclic graphs remain takeover candidates regardless of resource, pattern, or edge count, and planning capacity grows with graph size. Large repeated quantities stay compressed; distinct graph elements retain their unavoidable linear traversal cost. Checked arithmetic overflow or failure to prove an executable integer schedule still falls back to AE2 before mutation.

## Channel capacity

While this interface is attached, its ME network receives double the native cable and per-face channel capacity. In the default channel mode, ordinary cables carry 16 channels instead of 8; dense cables and each controller face carry 64 instead of 32. The controller-free network limit also becomes 16, including the interface's own channel.

The capacity bonus is passive during boot and channel overload. Crafting acceleration still requires the node to be active, with its existing 1-channel and 8 AE/t requirements. Additional interfaces do not stack the bonus. Detaching, removing or unloading the interface removes the bonus from its old network, and split/merged networks rebuild their own capacity. Other networks are unaffected.

Configured finite channel modes are doubled relative to their native values; infinite mode and non-carrying nodes keep their original behavior. Smart cables retain AE2's native proportional channel display.

## Network requirements

- 1 AE2 channel
- 8 AE/t idle power
- Connection from any face
- No screen, menu, or local configuration
