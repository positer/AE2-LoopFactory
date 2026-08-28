# 2026-08-28 - Bilingual AE2 GuideME pages

## Scope

Added English and Simplified Chinese GuideME documentation for both `AE2-lightoptimizer` service blocks in the independently isolated Minecraft 1.21.1 and 26.1.2 projects.

## Implementation

- Contributed pages to AE2's existing `ae2guide/items-blocks-machines` tree through GuideME's cross-namespace resource loading.
- Added matching `_zh_cn/items-blocks-machines` mirrors for Simplified Chinese.
- Declared `item_ids` for `recipe_ring_solver_terminal` and `supercomputing_crafting_optimizer_interface`, enabling AE2's native hold-`G` hint and direct item-to-page navigation.
- Attached both pages to AE2's Items, Blocks and Machines index.
- Added block previews and reciprocal links between the solver and optimizer pages.
- Documented channel/power requirements, active-network visuals, UI-free operation, responsibility separation, 16-cycle reserve accounting, compressed DAG planning, tera/peta-scale behavior, and safe fallback boundaries.
- Did not create a standalone GuideME guide, guide item, screen, key handler, or Mixin for documentation.

## Contract coverage

Each generation now has two GuideME documentation contract tests. They verify page presence, bilingual path mirroring, item indexing, AE2 parent navigation, block previews, reciprocal links, key policy text, and absence of a standalone `guideme_guides/guide.json`.

## Final verification

- `gradlew.bat build --rerun-tasks` succeeded in both projects with all six tasks executed.
- Minecraft 1.21.1: 6 suites, 23 tests, 0 failures, 0 errors, 0 skipped. Final JAR: 68,543 bytes; SHA-256 `270899583B292B2A62CE258B71CFBAB48F0E33667B90D6A40029FE1256AAE9CE`.
- Minecraft 26.1.2: 6 suites, 23 tests, 0 failures, 0 errors, 0 skipped. Final JAR: 68,816 bytes; SHA-256 `04A25B89ED106232B733F82927306BF2B0E7FA99624C0A3BEBDB9F2B8BB0BE88`.
- Each final JAR contains the two English pages and two `_zh_cn` pages at the expected `assets/ae2lightoptimizer/ae2guide` paths.
- All four corresponding source pages are byte-identical between versions. SHA-256 values: English ring `96F66B922FBE4176769160254422628AEB77BBB03A6C77921B7A8A1252B792F0`; English optimizer `4A305AA7EB20C838B6A4408CEFCBF3188280F8D168FDDC07716FEC05F8A2C584`; Chinese ring `32E77936CFA81978A9B52ED2DF447E2B0BCE1FC7413CA11F050343F8034A18DC`; Chinese optimizer `BCF60B2D5A8705AA9D69B421C49530764653ADB048052402D74E4D278520D895`.
- Latest data-run logs contain GuideME, AE2, and addon load evidence. Targeted searches for malformed `item_ids`, unknown items, failed guide-page loading, missing pages, and exceptions returned zero matches in both generations.

The remaining visual acceptance item is opening both pages through the hold-`G` item shortcut in a real client under both languages.
