---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Loop Storage Cells
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

# Loop Storage Cells

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

Loop Storage Cells use one shared byte budget for every storage key type registered with AE2. Items, fluids, energy, mana, source, soul, and storage types supplied by other AE addons can coexist in the same cell when their addon is loaded. AE2 is the only required mod; optional storage addons are discovered through AE2's key-type registry and are never required dependencies.

The accounting follows each registered key type's native amount-per-byte value. With the standard AE2 scale, one item-equivalent occupies the same space as 1,000 units of fluid, FE, mana, source, or soul. Other addons control the scale of their own registered types.

## Capacity and types

| Cell | Byte capacity | Type limit |
| --- | ---: | ---: |
| 1k | 1,024 | 63 |
| 4k | 4,096 | 63 |
| 16k | 16,384 | 63 |
| 64k | 65,536 | 63 |
| 256k | 262,144 | 63 |
| 1M | 66,060,288 shared | Not tracked |
| 4M | 264,241,152 shared | Not tracked |
| 16M | 1,056,964,608 shared | Not tracked |
| 64M | 4,227,858,432 shared | Not tracked |
| 256M | 16,911,433,728 shared | Not tracked |
| Infinite | Unlimited | Unlimited |

The k-tier cells match the byte capacity and 63-type limit of the corresponding AE2 item cell. M tiers use a DISK-style aggregate pool: their total budget is 63 times the tier's original single-type ceiling, and every registered key shares that total without category partitions or a displayed type counter. The infinite cell uses the same aggregate model with unlimited amount and types and likewise shows no separate type statistic.

## Housing and finite cells

<RecipeFor id="ae2lightoptimizer:loop_storage_cell_housing" />

Each finite cell has two recipes: a shaped recipe around its matching Loop Storage Core, and a shapeless recipe combining that core with one Loop Storage Cell Housing.

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

## Storage cores

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

## Infinite cell

The Infinite Loop Storage Cell is made with an AE2 explosion transform. Drop exactly 64 <ItemLink id="ae2lightoptimizer:256m_loop_storage_core" />s and one <ItemLink id="ae2lightoptimizer:loop_storage_cell_housing" /> together, then expose them to a TNT explosion. The transformation consumes those 65 inputs and produces one Infinite Loop Storage Cell.

<RecipeFor id="ae2lightoptimizer:infinite_loop_storage_cell" />

## Portable Loop Storage Cells

Every Loop Storage tier also has a portable form. It uses AE2's native portable-cell terminal, battery, energy-card expansion, idle drain, and powered insertion/extraction behavior while retaining the matching Loop Storage capacity and dynamic key-type support. Creative mode lists both an empty-AE-power stack and a fully charged stack.

Each finite portable cell can be shapelessly assembled from an ME Chest, an Energy Cell, and either the matching Loop Storage Cell or the matching Loop Storage Core plus a Loop Storage Cell Housing. Because the infinite tier has no core, the Infinite Portable Loop Storage Cell has only the ME Chest + Energy Cell + Infinite Loop Storage Cell recipe.

AE2's native empty-cell disassembly is also enabled. Sneak-use an empty portable cell to recover its ME Chest, Energy Cell, housing/core parts (or the Infinite Loop Storage Cell), with remaining AE power transferred back into the recovered Energy Cell.
