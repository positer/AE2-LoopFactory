# 2026-08-28 - Supercomputing interface, ring reserve policy, and assets

## Scope

Completed the first integrated architecture for `AE2-lightoptimizer` on Minecraft 1.21.1 and 26.1.2. Minecraft 1.20.1 remains cancelled. No ImmortalStorage source, runtime directory, build output, or dependency state was copied into this workspace.

## Implemented

- Registered the UI-free `recipe_ring_solver_terminal` and `supercomputing_crafting_optimizer_interface` blocks in both generations.
- Added managed AE2 grid nodes, channels, six-sided capability exposure, owner assignment, persistence, and active-state synchronization.
- Assigned cycle analysis exclusively to the ring terminal and crafting-plan acceleration exclusively to the supercomputing interface.
- Added checked compressed single-pattern planning for tera/peta-scale quantities.
- Added a Mixin at AE2 `CraftingCalculation.runCraftAttempt` and a safe version-local fast path.
- Added Kahn topological demand aggregation for uniquely defined acyclic graphs, including shared dependencies.
- Added closed-form single-pattern self-growth cooperation between the optimizer and an active ring terminal.
- Added a 16-cycle material reserve policy: preserve all stock below the threshold, preserve exactly the threshold above it, and credit only surplus stock against loop demand.
- Added explicit fallback for ambiguous producers, alternative inputs, remainders, unsupported byproducts, cycles outside the integrated subset, and arithmetic overflow.
- Added two visual states per block and generation: offline and connected. Textures are deterministic 16x16 opaque PNG assets derived from an AE2 crafting-CPU visual language while retaining distinct cyan-ring and blue/amber compute-bus identities.
- Retained the no-UI and no-recipe constraints.

## Test evidence before final build

- 21 tests passed independently in each generation.
- Complex productive and impossible SCC fixtures passed.
- The relevance filter handled 10,000 unrelated recipes within the state limit.
- Tera-scale and peta-scale compressed planning remained constant-size.
- The peta-scale single-input fixture modeled at most 12 optimized operations versus at least 11 quadrillion expanded operations.
- The 12-input peta-scale fixture modeled at most 33 operations with a reduction ratio above `10^12`.
- Resource audit found four referenced textures and zero issues in each version.
- Both data environments loaded the mod, pinned AE2, and Mixin 0.8.7 with the appropriate Java compatibility level.

## Honest boundary

The shared solver handles bounded multi-pattern nested cycles, but the live AE2 crafting bridge currently takes over only a safe single-pattern self-growth cycle. More complex runtime cycle execution falls back to AE2. The ordinary DAG fast path also falls back whenever semantics cannot be proven equivalent. Consequently, this implementation provides dramatic reductions for its qualified fixtures but does not promise acceleration for every recipe or mathematical optimality across all inputs.

## Pre-final acceptance checklist

Run both full builds, inspect final JAR contents and processed metadata, repeat texture and log audits, and perform an eventual in-game end-to-end AE2 network submission for the self-growth path.

## Final verification addendum

- `gradlew.bat build --rerun-tasks` succeeded for both NeoForge projects with all six build tasks executed.
- Minecraft 1.21.1: 5 suites, 21 tests, 0 failures, 0 errors; final JAR size 63,202 bytes; SHA-256 `0631D8C6AB8E93C0EB3736F7BA1E1E40DCA1ECF283851D20A5CF3402D254CB02`.
- Minecraft 26.1.2: 5 suites, 21 tests, 0 failures, 0 errors; final JAR size 63,475 bytes; SHA-256 `44AD571AB21520189BFBCD22A05EC9C69D8876BC5B09500577B4021F62ED8E65`.
- Both final JARs contain `ae2lightoptimizer.mixins.json`, `CraftingCalculationMixin.class`, `Ae2SinglePatternFastPath.class`, both blockstates, and both connected textures.
- Both processed `neoforge.mods.toml` files contain the Mixin declaration, mod ID `ae2lightoptimizer`, and display name `AE2-lightoptimizer`.
- Each resource tree references four block textures; every texture exists, is 16x16, and is fully opaque. Audit issues: zero.
- Latest data-environment logs identify the correct AE2 versions and Mixin compatibility levels (`JAVA_21` and `JAVA_25`). Targeted searches found zero Mixin errors, missing textures, exceptions, or failures.
- `git diff --check` completed without whitespace errors. The repository is a newly isolated, currently uncommitted workspace, so its project files remain untracked until the user chooses the initial commit boundary.

The only remaining acceptance item is a real in-game AE2 network crafting submission; it is not represented as completed by the build and data-start gates.
