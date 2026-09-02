# 2026-09-02 Portable tint and continuous FE fix

## Reported behavior

- All 1.21.1 Portable Loop Storage Cell bodies were invisible while their AE power bars still rendered.
- A creative energy source feeding an Applied Flux accessor stopped after roughly 47 displayed bytes and resumed only after a world reload or cell remount.

## Root causes and changes

- The 1.21.1 item-color registration returned AE2's 24-bit portable RGB directly. NeoForge 1.21.1 consumes ARGB, so the zero alpha made every tinted model layer transparent. The adapter now applies `FastColor.ARGB32.opaque` exactly as AE2's native registration does. No PNG was redrawn or modified.
- Applied Flux reads AE2's cached network inventory while exposing FE and Mekanism strict-energy capabilities. Loop-cell writes could leave that view stale until the storage mount was rebuilt. Optional `@Pseudo` Mixins now invalidate the AE2 storage cache after external energy writes through both Applied Flux capability paths.
- The compatibility classes do not add Applied Flux, Mekanism, or any other addon to `neoforge.mods.toml`; AE2 remains the sole storage-mod prerequisite.

## Verification and deployment

- `gradlew test build --no-daemon` passed for NeoForge 1.21.1 and 26.1.2.
- A 1.21.1 development client containing only AE2LO, AE2, GuideME, Minecraft, and NeoForge reached resource loading successfully; absent optional Mixin targets produced warnings only and did not become prerequisites.
- Both final JARs contain the two compatibility Mixins and retain all portable models/textures.
- JAR SHA-256: 1.21.1 `B03EADD625125104E3504E51D54232DF7526C178891F061597C94600340921B3`; 26.1.2 `C1E217DFA4A67AF13DC9C4DD54C5CB619A2E49C83C5D3C5D236D35645E639199`.
- The generation-matched JARs replaced AE2LO in `AE2-lightoptimizer-1.21.1`, `AE2-lightoptimizer-26.1.2`, `ImmortalStorage-1.21.1`, and `ImmortalStorage-26.1.2`. No save or configuration file was changed.
