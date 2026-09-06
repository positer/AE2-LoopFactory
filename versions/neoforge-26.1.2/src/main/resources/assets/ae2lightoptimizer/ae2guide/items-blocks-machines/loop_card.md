---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Loop Card
  icon: ae2lightoptimizer:loop_card
  position: 905
categories:
- devices
item_ids:
- ae2lightoptimizer:loop_card
---

# Loop Card

<ItemImage id="ae2lightoptimizer:loop_card" scale="4" />

<RecipeFor id="ae2lightoptimizer:loop_card" />

Craft one Advanced Card and one Loop Crystal together in any arrangement.
The Loop Card fits a [Crafting Ripper](crafting_ripper.md) or any
[Portable Loop Storage Cell](loop_storage_cells.md). Only one is needed per device.

## Crafting Ripper

The card publishes automatically discovered, encodable crafting, smithing and
stonecutting recipes. Installed patterns remain in the four grey rows and may
be removed, but new patterns cannot be inserted until the card is removed.
Removing the card immediately restores the retained physical patterns.

Special crafting recipes are included. Component-dependent variants, such as
written books and dyed equipment, are discovered from real network contents and
refresh when new item keys arrive. Recipes with world postprocessing, including
map expansion, run through the ripper's native continuation instead of instant ripping.

## Portable charging

Install the card in a portable cell's existing upgrade slots. Stored FE is then
converted into its AE battery using AE2's configured unit conversion and native
charge limit. It can recharge an empty AE battery. No stored FE is created, and
charging stops when the battery is full or the card is removed.

An AE2 energy-storage addon must provide the FE storage key. AE2LO does not add
a new required mod. For example, store FE in a portable cell, install a Loop Card
and carry it: the card consumes the stored FE as the terminal battery recharges.

## Portable capacitor

A portable cell with **stored FE and positive AE charge** also exposes its FE
through NeoForge's item energy capability. Other mods that query or extract from
this standard capability can use it as a carried energy source. This does not
require a Loop Card; the card supplies automatic AE recharging.

At zero AE charge the capacitor is unavailable until the cell is recharged.
Legacy integer energy queries saturate at their API maximum while the real stored
amount remains 64-bit; the newer energy interface reports long amounts directly.
