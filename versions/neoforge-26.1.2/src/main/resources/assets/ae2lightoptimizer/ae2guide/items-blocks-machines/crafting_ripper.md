---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Crafting Ripper
  icon: ae2lightoptimizer:crafting_ripper
  position: 904
categories:
- devices
item_ids:
- ae2lightoptimizer:crafting_ripper
---

# Crafting Ripper

<BlockImage id="ae2lightoptimizer:crafting_ripper" scale="6" />

<RecipeFor id="ae2lightoptimizer:crafting_ripper" />

Connect the Crafting Ripper to an ME Network with an available channel and power,
then right-click it. Its interface reuses AE2's Pattern Provider controls and
priority page with a scrollable four-row window. Pattern storage grows as needed,
with one completely empty row after the last occupied row. Insert crafting-table,
smithing-table or stonecutter patterns; other processing patterns are rejected.

## Whole-chain crafting

Request the final item through your ME Crafting Terminal as usual. The ripper
checks every recipe in the selected chain before reserving ingredients, checks
the real selected inputs again before execution, and produces the chain's result
in one server tick. Container returns and other recipe remainders are retained.

The [Recipe Ring Solver Terminal](recipe_ring_solver_terminal.md) still owns
cyclic planning. Its compressed execution order and initial seeds are preserved
when the ripper executes the chain. Quantities use checked signed 64-bit values.
Unsupported or stale recipes cannot partially consume a chain.

The machine draws no idle power of its own, like a native AE2 pattern provider, and
charges **50 AE** for each complete chain execution. It waits for this energy before
committing.
Delivery can wait when the requester is full; it does not repeat the craft or
charge again. The face is cyan while its grid node is active and dim while offline.

## Automatic patterns

Insert a [Loop Card](loop_card.md) into the upgrade slot to advertise encodable
crafting-table, smithing and stonecutting recipes from the server's recipe list.
Manual patterns remain editable, including through the Pattern Access Terminal.
For each exact output covered by an installed pattern, only the given patterns
are advertised: missing ingredients never trigger another automatic recipe for
that output. All other encodable outputs retain their automatic recipes.

Automatic patterns are backed by real matching inputs and assembled outputs.
Dynamic recipes with no finite encodable representative, such as arbitrary dye
or component combinations, still require a concrete encoded pattern.

For example, install a plank pattern and a stick pattern, then request sticks.
The ripper validates both steps and consumes the available logs for the final
sticks in one execution. Adding the ring terminal allows supported template
growth chains to preserve their seed while delivering only the requested gain.

## Import network patterns

Click the down-arrow button beside the pattern area to move supported encoded patterns from ME storage and installed pattern slots on the same network, including other rippers. Processing, invalid and stale patterns stay in their source. Existing patterns in this ripper stay in place. Imports move the real items in batches; no patterns are created. Keep the screen open until import finishes. Closing the screen or losing the network stops further transfers; completed transfers remain in the ripper.
