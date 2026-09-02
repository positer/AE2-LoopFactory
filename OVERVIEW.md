# AE2LO — AE2-lightoptimizer Project Overview

English name: Applied Energistics 2 Lightweight Optimization (AE2LO).  
中文名称：应用能源 2 轻量优化（AE2LO）。

## Goal and status

Provide a clean, dual-generation workspace for `AE2-lightoptimizer`, a UI-free AE2 addon whose universal Loop Storage cells share capacity across every dynamically registered AE2 key type and whose network blocks solve crafting cycles and accelerate eligible global crafting calculations. The workspace is isolated from ImmortalStorage and hard-separates incompatible Minecraft generations.

The canonical upstream is the standalone GitHub repository `https://github.com/positer/AE2-lightoptimizer`. Its Git root, history, branches, tags, and remote are independent from ImmortalStorage; no repository nesting, subtree, submodule, or shared worktree is used.

Release 0.0.3 supports both maintained generations. It adds stationary and portable Loop Storage Cell families, dynamic compatibility with every AE2-registered storage key type, exact upgrade/explosion recipes, and immutable supplied core assets while retaining the 0.0.2 Loop Crystal and planner surface. Minecraft 1.20.1 is intentionally not maintained.

Optional compatibility data is additive: a NeoForge `mod_loaded(create)` conditional Create Milling recipe and a `mod_loaded(mekanism)` conditional Mekanism Crushing recipe both use the common `c:gems/loop_crystal` item tag. Each is ignored independently when its platform is absent; neither external mod is a dependency or class reference.

Loop Storage Cells have only AE2 as a mod prerequisite. Version adapters enumerate `AEKeyTypes.getAll()` and use each key type's native `getAmountPerByte()`; optional storage addons are therefore compatibility inputs, never compile-time or metadata dependencies.

## Runtime flow

### Crafting takeover

1. Mod initialization forces AE2's `CraftingCalculation` target to load so required Mixin injections are validated immediately.
2. `CraftingCalculationMixin` intercepts the network-wide `runCraftAttempt` entry.
3. `Ae2GlobalCraftingOptimizer` queries AE2's active-machine index for the two service block entities.
4. `CraftingTakeoverPolicy` exits before graph discovery when no service is online.
5. The version adapter collects every reachable producer without fixed resource, node, or edge admission limits; the shared planner condenses SCCs and classifies the plan.
6. The policy accepts acyclic plans only for an active optimizer interface and cyclic plans only for an active ring terminal.
7. Accepted plans write compressed stock, missing, emitted, and pattern-count entries into AE2's native child simulation state.
8. The returned AE2 plan carries an owner-tagged compressed schedule: cyclic schedules belong to the ring terminal and acyclic schedules belong to the optimizer interface.
9. At CPU submission the schedule becomes a persistent execution cursor. AE2 dispatch sees only the current batch, and the cursor advances by the exact successful push count.
10. A ring-owned job routes matching final output into its crafting CPU inventory until its schedule is complete, locking seed/carrier stock away from other network jobs.
11. Completion releases only the amount above the persisted final-output seed reserve, including backlog left before a non-power-of-two final batch, and finishes only after all dispatched outputs return.
12. Returned net output reduces AE2's outstanding request even when a standalone terminal job has no requester and `CraftingLink.insert` routes zero directly; unrouted output and the retained seed remain in CPU inventory until native `finishJob` returns them to network storage.
13. Optimizer-owned and unmarked jobs never enter the cyclic output branch; parallel CPU jobs keep independent owner, schedule, cursor, and reserve state.
14. Plans without an attached schedule retain AE2's original task-map execution unchanged.

The no-service gate precedes pattern traversal and stock access. The graph-ownership gate precedes simulation-mode mutation, child state creation, extraction, missing/emitted writes, pattern counts, and callback cancellation.

### Loop Storage

1. Each version registers one housing, ten immutable supplied cores, ten finite cells, and one infinite cell. Common setup adds one `LoopStorageCellHandler` to AE2; a `Dist.CLIENT` entry point registers the eleven native drive models without loading client classes on a dedicated server.
2. When AE2 opens a cell, the handler constructs a version-local `LoopStorageCellInventory` over `AEComponents.STORAGE_CELL_INV`. Stored entries remain AE2 `GenericStack` values, so the item carries its inventory through normal AE2 component persistence.
3. Insertions accept an `AEKey` only when its type is currently present in `AEKeyTypes.getAll()` and reports a positive native `getAmountPerByte()`. No Botania, Ars Nouveau, FE, Soul Energy, or other optional-addon class is imported or declared as a dependency.
4. The adapter passes each key, key-type token, amount, and native amount-per-byte value into the loader-independent `LoopStorageAccounting` model. Accounting groups entries by key type and accumulates quotient/remainder pairs before rounding, so several partial entries share their key type's trailing byte without overflow or per-entry overcounting.
5. Every distinct `AEKey` consumes one type slot only on k tiers. The 1k through 256k tiers retain their AE2-equivalent byte capacities, per-type overheads, and 63-type limit. Each 1M through 256M tier keeps its original single-type ceiling as metadata and exposes one shared aggregate budget exactly 63 times that ceiling, with zero per-type overhead and no type counter. The infinite tier uses the same aggregate model but removes both limits explicitly instead of multiplying into `long` overflow.
6. Finite insertion uses a monotonic binary search for the largest amount whose recomputed grouped usage fits. Non-empty nested storage cells are rejected, simulated operations do not persist, and mutating operations notify the AE2 save provider or persist directly when no host exists.
7. AE2 cell state is derived from both remaining capacity and the ability to add a new type: k tiers may report `TYPES_FULL`, while M and infinite tiers report only aggregate empty/not-empty/full state and omit separate type statistics. Both adapters read idle drain from the single shared `LoopStorageTier.idleDrain()` table.
8. `PortableLoopStorageCellItem` subclasses AE2's `AbstractPortableCell`, so AE2 owns terminal opening, battery persistence, four upgrade slots, energy-card multiplication, charging, idle power, and powered insert/extract. Both adapters register the AE2 portable card set (fuzzy, inverter, equal distribution, void, energy card x2) for every portable tier through `Upgrades.add`, and the loop inventory applies partition filtering, inverter mode, void overflow, equal distribution, and the energy-card charge multiplier. The item implements only the shared tier provider; the same universal handler and inventory back both stationary and portable forms.
9. Creative registration emits an empty and a fully charged stack for every portable tier. Ten finite tiers have both requested shapeless construction routes; infinite has only the stationary-cell route because no infinite core is registered. Eleven `ae2:storage_cell_disassembly` declarations activate AE2's empty-cell disassembly and remaining-power return path.
10. The 1.21.1 client adapter makes AE2 portable-cell RGB tint results explicitly opaque; this preserves the supplied/generated pixel layers under NeoForge's ARGB item-color contract.
11. Both adapters persist every successful mount/content change back into the item-stack `STORAGE_CELL_INV` component before notifying the cell host, so drives rebuilding a cell inventory from that component always read the live amount.
8. `isPreferredStorageFor` performs a one-unit simulated insertion against the current cell. A full or incompatible cell declines while the next compatible cell with capacity accepts, preserving same-key spillover for AE2 and key-type-aware addon insertion paths.
9. The infinite-cell recipe remains data-driven: an `ae2:transform` with the `explosion` circumstance consumes exactly 64 256M cores and one housing. No custom explosion event handler or optional mod runtime is involved.

## Root structure

- `README.md`: public project description, capability boundaries, version matrix, scale evidence, and build entry points.
- `CHANGELOG.md`: user-facing release history and compatibility notes; automated contracts prohibit all date and time information.
- `OVERVIEW.md`: architecture, ownership, and maintained file-tree guide.
- `taste.md`: persistent implementation, performance, integration, and design conventions.
- `AGENTS.md`: session protocol and isolation rules.
- `versions.json`: machine-readable supported-version manifest.
- `build-1.21.1.ps1`, `build-26.1.2.ps1`: isolated build launchers with separate Gradle homes.
- `tools/provision_pcl_instances.ps1`: non-destructive, whitelist-only managed-file refresh for two independent PCL test instances, with save manifests checked before and after deployment.
- `tools/test_pcl_provision_preserves_runtime_state.ps1`: temporary-instance regression proving saves, configs, screenshots, resource packs, logs, and options survive deployment byte-for-byte.
- `tools/generate_loop_storage_assets.ps1`, `tools/StrictPngRecolor.java`: hash-locked core copying, exact LUT recoloring, native AE2 drive-model copying, and cross-generation item/model generation.
- `tools/generate_loop_storage_recipes.ps1`: deterministic generation of 53 acquisition recipes plus eleven AE2 portable disassembly declarations per adapter.
- `.gitignore`: local Gradle, generated build/run, IDE, and inspection exclusions.
- `archive/`: dated decisions, implementation records, and validation evidence.

## Shared algorithms

`shared/src/main/java/com/example/ae2lightoptimizer/solver/` owns loader-independent cycle solving:

- `RecipeRingSolver`: closed-form single-recipe solver plus relevance-filtered, dominance-pruned bounded search for nested rings.
- `RingRecipe`: validated integer input/output transition.
- `RingSolveRequest`, `RingSolveBudget`: target, stock, recipes, and explicit test/search limits.
- `RecipeApplication`: run-length-compressed application step.
- `RingSolveStatus`, `RingSolveResult`: solved, no-growth, and budget-exhausted results with metrics.

`shared/src/main/java/com/example/ae2lightoptimizer/crafting/` owns compressed global planning:

- `CraftingTakeoverPolicy`: authoritative two-service by two-graph-kind ownership matrix.
- `GlobalCraftingPlanner`: reverse relevance traversal, Tarjan SCC detection, checked integer balance solving, stock-aware multi-producer allocation, and compressed executable scheduling.
- `GlobalPattern`, `GlobalPlanRequest`, `GlobalPlanningBudget`: loader-free reachable graph and planning input.
- `GlobalCraftingPlan`, `GlobalPlanStatus`, `PatternBatch`: compressed counts, executable schedule, extraction/emission/missing maps, reserve data, and work metrics.
- `GlobalGraphScaleEnvelope`, `GlobalGraphScaleEstimator`, `GlobalGraphScaleAssessment`: allocation-free physical-cost diagnostics; they never gate live takeover.
- `SinglePatternBatchPlanner`, `SinglePatternBatchPlan`, `BatchOptimizationMode`: checked constant-size plans for large single-pattern work.
- `CompressedBatchCursor`: constant-state dispatch progress over `long` batch sizes, including exact save/load restoration.
- `RingOutputLock`: constant-state decision for withholding cyclic final output until dispatch completion and releasing only net growth above the seed reserve.
- `RingCompletionGate`: completes a ring job only after schedule completion, zero remaining request, and zero in-flight final output; unrelated waiting keys cannot hold the CPU open.
- `RingMaterialReservePolicy`, `RingMaterialReservePlan`: checked 16-cycle execution-seed metadata; global planning charges current-order external inputs without subtracting that metadata.

`shared/src/main/java/com/example/ae2lightoptimizer/storage/` owns loader-independent universal-cell contracts:

- `LoopStorageTier`: stable tier IDs, finite byte capacities, k-tier per-type overhead and limits, explicit infinity, and the shared idle-drain table.
- `LoopStorageKeyUsage`: one version-adapter entry containing opaque key and key-type tokens plus checked amount and native amount-per-byte data.
- `LoopStorageAccounting`: checked per-key-type quotient/remainder accumulation, byte/type fitting, and exact `NOT_EMPTY`/`TYPES_FULL` boundary predicates.
- `LoopStorageUsage`: validated aggregate type, content-byte, overhead-byte, total-byte, and partial-tail state.

`shared/src/test/` contains:

- `CraftingTakeoverPolicyTest`: all eight service-state and graph-kind combinations.
- `GlobalCraftingPlannerTest`: DAG, producer-route, peta-material, productive/dead SCC, and tera irreducible-cycle stress cases.
- `GlobalGraphScaleEstimatorTest`: T-distinct/P-quantity physical-envelope diagnostics.
- `SinglePatternBatchPlannerTest`, `RingMaterialReservePolicyTest`, `RecipeRingSolverTest`: batch, reserve, and standalone solver contracts.
- `storage/LoopStorageTierTest`, `LoopStorageKeyUsageTest`, `LoopStorageAccountingTest`: tier metadata, invalid input, overflow, mixed-key-type conversion, shared-tail, type-limit, status-boundary, and idle-drain contracts.
- `storage/LoopStorageAssetContractTest`: ten core SHA-256 locks, exact per-pixel LUT recolor comparisons, dimensions/alpha/geometry preservation, and byte-identical AE2 drive models.

## Version projects

`versions/neoforge-1.21.1/` targets Minecraft 1.21.1, NeoForge 21.1.235, AE2 19.2.17, and Java 21. `versions/neoforge-26.1.2/` targets Minecraft 26.1.2, NeoForge 26.1.2.94, AE2 26.1.10-beta, and Java 25.

Each standalone project owns:

- Gradle wrapper, `settings.gradle`, `build.gradle`, and `gradle.properties`: pinned version-local build configuration.
- `Ae2LightOptimizer.java`: NeoForge entry point, registrations, fail-fast AE2 target loading, and common-side storage-cell handler installation.
- `block/ModBlocks.java`, `ModBlockEntities.java`: block and block-entity registration.
- `block/RecipeRingSolverTerminalBlock.java` and `...BlockEntity.java`: 2 AE/t channel-required cyclic service, persistence, and active-state synchronization.
- `block/SupercomputingCraftingOptimizerInterfaceBlock.java` and `...BlockEntity.java`: 8 AE/t channel-required acyclic service, persistence, and active-state synchronization.
- `item/ModItems.java`: service-block items, Loop Crystal materials, housing, ten cores, eleven cell items, and creative-tab placement without menus.
- `storage/LoopStorageCellItem.java`, `LoopStorageCellHandler.java`, `LoopStorageCellInventory.java`: version-native item tooltip, AE2 handler registration, dynamic-key inventory, persistence, nested-cell guard, and shared-capacity enforcement.
- `client/Ae2LightOptimizerClient.java`: `Dist.CLIENT`-isolated registration of eleven drive models; 1.21.1 also binds AE2 cell-state tinting through the item color handler while 26.1.2 uses its native item descriptor tint.
- `client/jei/InfiniteLoopStorageJeiPlugin.java` (26.1.2): optional JEI presentation adapter that replaces only the infinite transform's expanded 65-slot display with a 64-count core stack, one housing, and one output while leaving AE2's real explosion recipe unchanged. AE2 19.2.17 has no corresponding JEI transform category.
- `integration/Ae2GlobalCraftingOptimizer.java`: reachable AE2 pattern conversion, substitute selection, byproduct/container modeling, inventory/emitter snapshots, policy gating, and native plan construction.
- `integration/CraftingExecutionSchedule`, `ScheduledCraftingPlan`, `ScheduledCraftingJob`, and version-local codec: owner-tagged ordered batches, persisted final-output reserve, and generation-specific persistence.
- `mixin/CraftingCalculationMixin.java`: required global interception, delayed handled-path simulation state, and handled-only cancellation.
- `mixin/CraftingPlanMixin`, `ExecutingCraftingJobMixin`, `ExecutingCraftingJobPersistenceMixin`, `CraftingCpuLogicMixin`, and `ElapsedTimeTrackerAccessor`: schedule transport, per-job cursor state, save/load, current-batch dispatch, cyclic output locking/release, native progress accounting, and successful-push advancement.
- `resources/ae2lightoptimizer.mixins.json`, `META-INF/neoforge.mods.toml`: Mixin declaration and loader metadata.
- `src/test/.../RecipeRingSolverTerminalContractTest.java`: ring block, reserve service, and cyclic ownership contracts.
- `src/test/.../SupercomputingOptimizerContractTest.java`: optimizer, Mixin, zero-side-effect ordering, graph planner, resource, and registration contracts.
- `src/test/.../GuideMeDocumentationContractTest.java`: bilingual mirror, item indexing, AE2 navigation, previews, cross-links, and no-standalone-guide contracts.
- `src/test/.../storage/LoopStorageCellContractTest.java`: registrations, handler/model wiring, dynamic key discovery, idle drain, recipes, metadata dependency boundaries, languages, and resource packaging.
- `src/test/.../storage/LoopStorageCellInventoryTest.java` (1.21.1): direct item/fluid shared-capacity, insert/extract, simulation, type-limit, status, persistence, and nested-cell inventory behavior against the pinned AE2 API.

The 1.21.1 adapter uses `CompoundTag` plus `HolderLookup.Provider`. The 26.1.2 adapter uses `ValueInput`/`ValueOutput`, ID-aware registration helpers, the `clientData` run type, and generation-specific client item descriptors. Minecraft/NeoForge/AE2 API source is never placed in `shared/`.

## Resources

Each version contains block states keyed by `connected=false/true`, offline and connected block models, item models, bilingual names, self-drop loot, a pickaxe tag, and distinct opaque 16x16 PNG texture pairs for:

- `recipe_ring_solver_terminal`
- `supercomputing_crafting_optimizer_interface`

Each version also packages the same root-level `ae2lightoptimizer.png`: a transparent 64x64 isometric three-face render of the connected ring terminal, inset within a fully closed square PNG viewport frame. The four-layer frame uses dark steel, metal-grey, cyan signal, and a dark inner edge; all four outer image edges are pixel-opaque while the interior retains transparency. `META-INF/neoforge.mods.toml` binds it as the NeoForge `logoFile`.

Each version contains 74 recipe JSON files: two service-block recipes, eight Loop Crystal recipes, and 64 Loop Storage recipes/declarations. The Loop Storage set contains the original 32 stationary recipes, two shapeless portable recipes for each of ten finite tiers, one cell-based infinite portable recipe, and eleven native disassembly declarations. The infinite portable tier intentionally has no housing-plus-core recipe because no infinite core exists. The 1.21.1 adapter uses object-form ingredients and `ae2:chest`, while 26.1.2 uses string-form ingredients and `ae2:me_chest`; structured tests lock both layouts and ingredient IDs.

Each version carries 36 Loop Storage texture files and 33 item models: the original ten byte-identical user cores, housing and stationary cells, plus three portable housing palettes and eleven portable side layers. Finite portable side layers are copied byte-for-byte from the matching native AE2 tier; portable housings use the approved k/M/infinite shell LUTs, and only the infinite side uses the approved light-purple core LUT. Native portable LED and screen layers remain external AE2 references. Dimensions, alpha, silhouette, coordinates, and unmapped pixels are unchanged. Eleven stationary drive models remain byte-identical native AE2 models. Minecraft 26.1.2 additionally carries 33 generation-native `assets/.../items/` descriptors.

Neither version contains menus, screens, or local configuration UI. Each contributes the same GuideME tree to AE2's existing guide:

The optimizer face is an opaque 16x16 hash-grid core with identical offline/connected geometry. Connected rails and intersections use cyan-white illumination with eight amber endpoints; the offline state uses the same pixels in a dim palette. The generator enforces exact 90-degree rotational invariance. Ring-terminal textures remain unchanged.

```text
assets/ae2lightoptimizer/ae2guide/
|-- items-blocks-machines/
|   |-- recipe_ring_solver_terminal.md
|   |-- supercomputing_crafting_optimizer_interface.md
|   `-- loop_storage_cells.md
`-- _zh_cn/items-blocks-machines/
    |-- recipe_ring_solver_terminal.md
    |-- supercomputing_crafting_optimizer_interface.md
    `-- loop_storage_cells.md
```

English pages are canonical and Chinese pages mirror the same path under `_zh_cn`. `item_ids` enables AE2's native hold-`G` link. No standalone guide registration exists.

## Tools and generated state

- `tools/generate_block_textures.ps1`: deterministic generator for both blocks' offline and connected textures plus the 64x64 isometric ring-terminal mod icon and its pixel-exact square viewport frame. The icon's three contiguous material faces have lighting only and no added cube outline or seam strokes. The generator also enforces strict 90-degree pixel-rotation symmetry for the optimizer faces.
- `tools/generate_loop_storage_assets.ps1`: verifies the ten supplied core hashes, copies those PNG bytes unchanged into both adapters, performs only the requested strict RGB LUT substitutions against pinned AE2 cell PNGs, writes item resources, and copies the matching native AE2 drive models unchanged.
- `tools/StrictPngRecolor.java`: decodes a source PNG from the pinned AE2 JAR, preserves alpha and coordinates, applies only an explicit source-RGB-to-target-RGB table, and rejects any required opaque source color that lacks a mapping.
- `tools/generate_loop_storage_recipes.ps1`: emits each generation's native ingredient shape, 53 acquisition recipes, eleven portable disassembly declarations, and the data-driven 64-core explosion transform.
- `tools/verify_global_takeover.ps1`: starts both real data environments, disassembles five transformed AE2 classes, and proves calculation, compressed dispatch, cyclic output locking, and elapsed-time takeover.
- `tools/provision_pcl_instances.ps1`: stages local launch metadata safely and refreshes only version metadata plus the exact AE2/GuideME/JEI/addon whitelist. Existing instance directories and unmanaged runtime state are never removed; save files are hash-guarded before and after refresh.
- `tools/test_pcl_provision_preserves_runtime_state.ps1`: creates two disposable fixtures under a strictly named system-temp directory and verifies representative runtime files retain path, size, timestamp, and SHA-256 after a managed refresh.
- `.gradle-user-home/<version>/`: ignored version-specific Gradle dependencies and daemons.
- `versions/*/build/`: compiled classes, reports, and JARs.
- `versions/*/run/`: NeoForge runtime state.
- `versions/*/src/generated/`: data-generator output when present.

Generated directories are local build state, not source ownership boundaries.

## PCL instance isolation

The optional PCL tool targets two addon-only instances when explicitly invoked:

- `<PCL_ROOT>\.minecraft\versions\AE2-lightoptimizer-1.21.1`
- `<PCL_ROOT>\.minecraft\versions\AE2-lightoptimizer-26.1.2`

Its contract requires `VersionArgumentIndieV2:True`, exactly six managed mod JARs (AE2, GuideME, JEI, AE2LO, Applied Flux, and Glodium), no ImmortalStorage artifact, no filesystem link, and no copied mutable directories. Applied Flux and Glodium exist only in the test-instance whitelist to verify third-party FE key discovery; they are not release dependencies. PCL creates future instance state under the matching target directory. Existing ImmortalStorage PCL instances remain separate and unchanged. Release 0.0.3 and the compatibility-test pair were deployed to both matching isolated instances on 2026-09-01; the provisioning script verified that their save manifests were unchanged.

## Isolation contract

Neither version references the ImmortalStorage workspace, packages, artifacts, generated sources, caches, or run directories. No compiled class is shared between generations. Only API-free source under `shared/` is compiled into both builds; compatibility work is duplicated explicitly in each adapter.

## Verification state

- NeoForge 1.21.1 passes 102 tests and NeoForge 26.1.2 passes 101 tests, with zero failures, errors, or skips.
- Both real AE2/GuideME/Mixin data environments start successfully.
- Calculation, plan, executing-job, CPU-logic, and elapsed-time classes pass transformed-bytecode takeover verification in both generations.
- Each adapter packages exactly 64 Loop Storage recipe JSON files and 74 AE2LO recipe JSON files in total, two localized Loop Storage GuideME pages, 33 Loop Storage items, 36 Loop Storage textures, 33 item models, and eleven native drive models.
- The ten core textures in each adapter match the user-supplied SHA-256 values byte-for-byte. Every finite/infinite cell recolor passes exact pixel comparison, and every drive model matches the pinned AE2 source bytes.
- Both packaged `neoforge.mods.toml` files decode as strict UTF-8, report version 0.0.3, and declare no optional storage addon as a dependency. Both Gradle adapters set `processResources.filteringCharset` to UTF-8 and expand only `META-INF/neoforge.mods.toml`; JSON, GuideME Markdown, PNG, and all other resources remain unfiltered.
- Final release JAR SHA-256: 1.21.1 `8DC0C2854ADCE448B34BB99F77F3B30FB666F5876896C3DBCADE3BD94C531A8A`; 26.1.2 `6A9FC6A0A58F1B34DB3A3E54932DC4582F32F39049E5A814F88AAB4852061A93`.
- Both PCL test instances contain exactly the expected six JARs with the matching 0.0.3 hash, no stale 0.0.2 addon, no filesystem reparse point, and independent mode enabled. The deployment preservation regression and the real-instance save-manifest checks passed.
- The 1,000-template execution model delivers exactly 1,000 net templates and returns one locked seed; a 500-round three-node SCC delivers the same net growth without skipping an unavailable node.
- Parallel ring and optimizer jobs retain separate cursor state, and optimizer-owned final outputs bypass the ring-only recycling branch.
- The two global adapters are byte-identical; the two Mixin sources are byte-identical.
- Both JARs contain `CraftingTakeoverPolicy`, `Ae2GlobalCraftingOptimizer`, and `CraftingCalculationMixin`.
- Peta SCC stress represents `1.5 x 10^15` applications in 94 balance iterations and 96 compressed batches.
- P-total-material stress covers 64 component types and 128 producer routes.
- T-distinct diagnostics report the unavoidable `Omega(V + E)` traversal and minimum reference cost without imposing a live threshold.

A persistent in-game network submission and hold-`G` rendering remain final release acceptance checks.
