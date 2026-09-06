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
priority page with four rows of nine pattern slots. Insert crafting-table,
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

The machine needs **5 AE/t** while idle or working and **50 additional AE** for
each complete chain execution. It waits for this energy before committing.
Delivery can wait when the requester is full; it does not repeat the craft or
charge again. The face is cyan while its grid node is active and dim while offline.

## Automatic patterns

Insert a [Loop Card](loop_card.md) into the upgrade slot to advertise encodable
crafting-table, smithing and stonecutting recipes from the server's recipe list.
The 36 pattern slots turn grey: existing patterns stay in place and can be removed,
but cannot be inserted again until the card is removed. The same rule applies to
the Pattern Access Terminal. Remove the card to restore normal pattern use.

Automatic patterns are backed by real matching inputs and assembled outputs.
Special recipes and component variants are resolved from native recipe inputs and
real network contents. New component keys refresh the catalog automatically;
arbitrary text and every possible color mixture are not globally pre-generated.

Map expansion and non-deterministic item effects run one native recipe operation
per tick, without an external assembler. Real products, progress and the single
50 AE job payment stay attached to the original CPU job and survive saving.

For example, install a plank pattern and a stick pattern, then request sticks.
The ripper validates both steps and consumes the available logs for the final
sticks in one execution. Adding the ring terminal allows supported template
growth chains to preserve their seed while delivering only the requested gain.
