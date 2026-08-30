# AE2LO — AE2-lightoptimizer Project Overview

English name: Applied Energistics 2 Lightweight Optimization (AE2LO).  
中文名称：应用能源 2 轻量优化（AE2LO）。

## Goal and status

Provide a clean, dual-generation workspace for `AE2-lightoptimizer`, a UI-free AE2 addon whose network blocks solve crafting cycles and accelerate eligible global crafting calculations. The workspace is isolated from ImmortalStorage and hard-separates incompatible Minecraft generations.

The canonical upstream is the standalone GitHub repository `https://github.com/positer/AE2-lightoptimizer`. Its Git root, history, branches, tags, and remote are independent from ImmortalStorage; no repository nesting, subtree, submodule, or shared worktree is used.

Release 0.0.2 supports both maintained generations. It adds the complete four-part Loop Crystal material family, AE2 Mysterious Cube integration, exact recipes, dedicated creative-tab exposure, and multi-recipe growth/no-growth regression coverage. Minecraft 1.20.1 is intentionally not maintained.

Optional compatibility data is additive: a NeoForge `mod_loaded(create)` conditional Create Milling recipe and a `mod_loaded(mekanism)` conditional Mekanism Crushing recipe both use the common `c:gems/loop_crystal` item tag. Each is ignored independently when its platform is absent; neither external mod is a dependency or class reference.

## Runtime flow

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

`shared/src/test/` contains:

- `CraftingTakeoverPolicyTest`: all eight service-state and graph-kind combinations.
- `GlobalCraftingPlannerTest`: DAG, producer-route, peta-material, productive/dead SCC, and tera irreducible-cycle stress cases.
- `GlobalGraphScaleEstimatorTest`: T-distinct/P-quantity physical-envelope diagnostics.
- `SinglePatternBatchPlannerTest`, `RingMaterialReservePolicyTest`, `RecipeRingSolverTest`: batch, reserve, and standalone solver contracts.

## Version projects

`versions/neoforge-1.21.1/` targets Minecraft 1.21.1, NeoForge 21.1.235, AE2 19.2.17, and Java 21. `versions/neoforge-26.1.2/` targets Minecraft 26.1.2, NeoForge 26.1.2.94, AE2 26.1.10-beta, and Java 25.

Each standalone project owns:

- Gradle wrapper, `settings.gradle`, `build.gradle`, and `gradle.properties`: pinned version-local build configuration.
- `Ae2LightOptimizer.java`: NeoForge entry point, registrations, and fail-fast AE2 target loading.
- `block/ModBlocks.java`, `ModBlockEntities.java`: block and block-entity registration.
- `block/RecipeRingSolverTerminalBlock.java` and `...BlockEntity.java`: 2 AE/t channel-required cyclic service, persistence, and active-state synchronization.
- `block/SupercomputingCraftingOptimizerInterfaceBlock.java` and `...BlockEntity.java`: 8 AE/t channel-required acyclic service, persistence, and active-state synchronization.
- `item/ModItems.java`: two block items and creative-tab placement without menus.
- `integration/Ae2GlobalCraftingOptimizer.java`: reachable AE2 pattern conversion, substitute selection, byproduct/container modeling, inventory/emitter snapshots, policy gating, and native plan construction.
- `integration/CraftingExecutionSchedule`, `ScheduledCraftingPlan`, `ScheduledCraftingJob`, and version-local codec: owner-tagged ordered batches, persisted final-output reserve, and generation-specific persistence.
- `mixin/CraftingCalculationMixin.java`: required global interception, delayed handled-path simulation state, and handled-only cancellation.
- `mixin/CraftingPlanMixin`, `ExecutingCraftingJobMixin`, `ExecutingCraftingJobPersistenceMixin`, `CraftingCpuLogicMixin`, and `ElapsedTimeTrackerAccessor`: schedule transport, per-job cursor state, save/load, current-batch dispatch, cyclic output locking/release, native progress accounting, and successful-push advancement.
- `resources/ae2lightoptimizer.mixins.json`, `META-INF/neoforge.mods.toml`: Mixin declaration and loader metadata.
- `src/test/.../RecipeRingSolverTerminalContractTest.java`: ring block, reserve service, and cyclic ownership contracts.
- `src/test/.../SupercomputingOptimizerContractTest.java`: optimizer, Mixin, zero-side-effect ordering, graph planner, resource, and registration contracts.
- `src/test/.../GuideMeDocumentationContractTest.java`: bilingual mirror, item indexing, AE2 navigation, previews, cross-links, and no-standalone-guide contracts.

The 1.21.1 adapter uses `CompoundTag` plus `HolderLookup.Provider`. The 26.1.2 adapter uses `ValueInput`/`ValueOutput`, ID-aware registration helpers, the `clientData` run type, and generation-specific client item descriptors. Minecraft/NeoForge/AE2 API source is never placed in `shared/`.

## Resources

Each version contains block states keyed by `connected=false/true`, offline and connected block models, item models, bilingual names, self-drop loot, a pickaxe tag, and distinct opaque 16x16 PNG texture pairs for:

- `recipe_ring_solver_terminal`
- `supercomputing_crafting_optimizer_interface`

Each version also packages the same root-level `ae2lightoptimizer.png`: a transparent 64x64 isometric three-face render of the connected ring terminal, inset within a fully closed square PNG viewport frame. The four-layer frame uses dark steel, metal-grey, cyan signal, and a dark inner edge; all four outer image edges are pixel-opaque while the interior retains transparency. `META-INF/neoforge.mods.toml` binds it as the NeoForge `logoFile`.

Each version contains two standard shaped recipe JSON files. The 1.21.1 adapter uses object-form ingredients while 26.1.2 uses the generation's string-form ingredients; structured tests lock both layouts and ingredient IDs. Neither version contains menus, screens, or local configuration UI. Each contributes the same GuideME tree to AE2's existing guide:

The optimizer face is an opaque 16x16 hash-grid core with identical offline/connected geometry. Connected rails and intersections use cyan-white illumination with eight amber endpoints; the offline state uses the same pixels in a dim palette. The generator enforces exact 90-degree rotational invariance. Ring-terminal textures remain unchanged.

```text
assets/ae2lightoptimizer/ae2guide/
|-- items-blocks-machines/
|   |-- recipe_ring_solver_terminal.md
|   `-- supercomputing_crafting_optimizer_interface.md
`-- _zh_cn/items-blocks-machines/
    |-- recipe_ring_solver_terminal.md
    `-- supercomputing_crafting_optimizer_interface.md
```

English pages are canonical and Chinese pages mirror the same path under `_zh_cn`. `item_ids` enables AE2's native hold-`G` link. No standalone guide registration exists.

## Tools and generated state

- `tools/generate_block_textures.ps1`: deterministic generator for both blocks' offline and connected textures plus the 64x64 isometric ring-terminal mod icon and its pixel-exact square viewport frame. The icon's three contiguous material faces have lighting only and no added cube outline or seam strokes. The generator also enforces strict 90-degree pixel-rotation symmetry for the optimizer faces.
- `tools/verify_global_takeover.ps1`: starts both real data environments, disassembles five transformed AE2 classes, and proves calculation, compressed dispatch, cyclic output locking, and elapsed-time takeover.
- `tools/provision_pcl_instances.ps1`: stages local launch metadata safely and refreshes only version metadata plus the exact AE2/GuideME/JEI/addon whitelist. Existing instance directories and unmanaged runtime state are never removed; save files are hash-guarded before and after refresh.
- `tools/test_pcl_provision_preserves_runtime_state.ps1`: creates two disposable fixtures under a strictly named system-temp directory and verifies representative runtime files retain path, size, timestamp, and SHA-256 after a managed refresh.
- `.gradle-user-home/<version>/`: ignored version-specific Gradle dependencies and daemons.
- `versions/*/build/`: compiled classes, reports, and JARs.
- `versions/*/run/`: NeoForge runtime state.
- `versions/*/src/generated/`: data-generator output when present.

Generated directories are local build state, not source ownership boundaries.

## PCL instance isolation

The local PCL root contains two addon-only instances:

- `<PCL_ROOT>\.minecraft\versions\AE2-lightoptimizer-1.21.1`
- `<PCL_ROOT>\.minecraft\versions\AE2-lightoptimizer-26.1.2`

Each instance has `VersionArgumentIndieV2:True`, exactly four mod JARs (AE2, GuideME, JEI, and the addon), no ImmortalStorage artifact, no filesystem link, and no copied mutable directories. PCL creates future instance state under the matching target directory. Existing ImmortalStorage PCL instances remain separate and unchanged.

## Isolation contract

Neither version references the ImmortalStorage workspace, packages, artifacts, generated sources, caches, or run directories. No compiled class is shared between generations. Only API-free source under `shared/` is compiled into both builds; compatibility work is duplicated explicitly in each adapter.

## Verification state

- 57 tests pass in each generation with zero failures and zero errors.
- Both real AE2/GuideME/Mixin data environments start successfully.
- Calculation, plan, executing-job, CPU-logic, and elapsed-time classes pass transformed-bytecode takeover verification in both generations.
- The 1,000-template execution model delivers exactly 1,000 net templates and returns one locked seed; a 500-round three-node SCC delivers the same net growth without skipping an unavailable node.
- Parallel ring and optimizer jobs retain separate cursor state, and optimizer-owned final outputs bypass the ring-only recycling branch.
- The two global adapters are byte-identical; the two Mixin sources are byte-identical.
- Both JARs contain `CraftingTakeoverPolicy`, `Ae2GlobalCraftingOptimizer`, and `CraftingCalculationMixin`.
- Peta SCC stress represents `1.5 x 10^15` applications in 94 balance iterations and 96 compressed batches.
- P-total-material stress covers 64 component types and 128 producer routes.
- T-distinct diagnostics report the unavoidable `Omega(V + E)` traversal and minimum reference cost without imposing a live threshold.

A persistent in-game network submission and hold-`G` rendering remain final release acceptance checks.
