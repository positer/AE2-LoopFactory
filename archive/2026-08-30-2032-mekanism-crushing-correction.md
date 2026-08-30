# 2026-08-30-2032 - Mekanism crushing correction

- Removed the incorrect optional Create create:milling integration.
- Added the actual Mekanism recipe type mekanism:crushing, based on the installed Mekanism 10.7.19 schema.
- Input uses the common c:gems/loop_crystal item tag; output is one e2lightoptimizer:loop_crystal_powder.
- NeoForge mod_loaded(mekanism) keeps Mekanism optional with no required dependency or foreign code reference.
- Updated bilingual docs and contract tests. Both generations built/tested successfully and PCL instances were refreshed.
- Deployed hashes: 1.21.1 41ACF77D33BC09CFC5DD990EDE69B40E88BC47334A35E7C0DC3CE22D82845822; 26.1.2 753260E1EF2D20AEFB5CDF048AECEB2610A2059D889B91A2F6A8E23926FC6A84.
