# 2026-09-02 — Portable Loop Storage Cells

## Scope

- Added portable variants for 1k, 4k, 16k, 64k, 256k, 1M, 4M, 16M, 64M, 256M, and infinite Loop Storage Cells in both maintained NeoForge adapters.
- Preserved AE2 as the only hard mod prerequisite and kept Applied Flux/Glodium confined to PCL compatibility testing.

## Runtime design

- Added shared `LoopStorageTierProvider`; stationary and portable items now enter the same universal `LoopStorageCellHandler` and `LoopStorageCellInventory`.
- `PortableLoopStorageCellItem` subclasses AE2 `AbstractPortableCell`. AE2 continues to own terminal opening, battery data, four upgrade slots, energy-card power multiplication, charge rate, idle power, and powered insertion/extraction.
- The portable class intentionally does not implement `IBasicCellItem`, preventing AE2's single-key basic-cell handler from taking ownership before the universal handler.
- Every portable item is registered in creative mode twice: one empty AE-power stack and one stack charged to its AE2-native maximum.

## Recipes

- Each of the ten finite tiers has two shapeless recipes:
  - ME Chest + Energy Cell + matching Loop Storage Cell.
  - ME Chest + Energy Cell + Loop Storage Cell Housing + matching Loop Storage Core.
- Infinite has exactly one shapeless recipe: ME Chest + Energy Cell + Infinite Loop Storage Cell. No infinite core or `_from_parts` recipe exists.
- Added eleven `ae2:storage_cell_disassembly` declarations so AE2's native empty-portable-cell disassembly works and returns remaining charge to the recovered Energy Cell. Infinite disassembles to ME Chest, Energy Cell, and the Infinite Loop Storage Cell, never an invented core.
- Generation-native identifiers remain separated: 1.21.1 uses `ae2:chest`; 26.1.2 uses `ae2:me_chest`.
- Each adapter now contains 53 Loop Storage acquisition recipes, eleven portable disassembly declarations, and 74 AE2LO recipe JSON files total.

## Assets

- Reused AE2's native four-layer portable-cell construction.
- Recolored only the portable housing with the already approved k grey, M grey-black, and infinite purple-black LUTs.
- Copied every finite tier side layer byte-for-byte from AE2; recolored only the infinite 1k side with the approved light-purple core LUT.
- Referenced AE2's original LED and screen layers. No geometry, detail, core source, or extra artwork was authored.
- Fixed the storage asset generator's stationary item-model closing brace; every emitted model now parses as valid JSON.

## Verification and deployment

- NeoForge 1.21.1: 101 tests, zero failures/errors/skips; full build passed.
- NeoForge 26.1.2: 100 tests, zero failures/errors/skips; full build passed.
- Both real data environments loaded successfully and the calculation/execution Mixin takeover verifier passed.
- Texture audit found no invalid JSON after regeneration. Its remaining reports are expected dependency-namespace references to AE2 textures because the audit accepts only one local resource root; pixel-exact contract tests independently verified all portable recolors and byte-identical side layers.
- PCL preservation regression passed, then both existing isolated instances were refreshed without changing saves.
- Installed artifacts match their build SHA-256 values:
  - 1.21.1: `E75EAEFF571048A3B8E21830BC8B12F12D537CD1764243DE418449B263A3C39B`
  - 26.1.2: `AAF8D87C2AB6D2ACD76BC1F0F798F2D9844EA357129EB576154A20C431A2767E`
- Each instance contains the six expected managed JARs. Packaged AE2LO artifacts contain 32 portable recipe/declaration files and eleven portable models, with no AE2, Applied Flux, Glodium, Create, Mekanism, or other foreign classes bundled.
