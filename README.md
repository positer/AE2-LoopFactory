# AE2LO — AE2-lightoptimizer

**English:** Applied Energistics 2 Lightweight Optimization (**AE2LO**)

**简体中文：** 应用能源 2 轻量优化（**AE2LO**）

`AE2-lightoptimizer` (简称 **AE2LO**) is a lightweight Applied Energistics 2 addon for accelerating server-side crafting calculations without adding screens or menus. Its two AE2 network blocks separate cycle solving from plan optimization so unsupported recipe semantics can fall back to AE2 safely.

Official source repository: <https://github.com/positer/AE2-lightoptimizer>

Current release: **0.0.2**

Release 0.0.2 adds Loop Crystal materials, AE2-compatible conversion recipes, and solver regression coverage for growth and no-growth multi-recipe cycles.

Optional machine compatibility is additive: Create provides a conditional `create:milling` recipe and Mekanism provides a conditional `mekanism:crushing` recipe. Both use the common `c:gems/loop_crystal` item tag and convert one tagged Loop Crystal into one Loop Crystal Powder. Each recipe activates independently when its platform is loaded; neither Create nor Mekanism is an AE2LO dependency.

| Minecraft | NeoForge | AE2 | Java |
| --- | --- | --- | --- |
| 1.21.1 | 21.1.235 | 19.2.17 | 21 |
| 26.1.2 | 26.1.2.94 | 26.1.10-beta | 25 |

Minecraft 1.20.1 is intentionally not maintained. Both supported generations are independent Gradle projects and are fully isolated from ImmortalStorage. This repository has its own Git history and GitHub remote; it does not use the ImmortalStorage repository as an upstream, subtree, or submodule.

## Network blocks

### Recipe Ring Solver Terminal

`ae2lightoptimizer:recipe_ring_solver_terminal` (`配方环解算终端`) handles cyclic crafting-tree analysis only. It requires an AE2 channel, consumes 2 AE/t, exposes its grid node on every side, and has no UI.

For self-growth rings, the 16-cycle value is execution seed metadata; it does not reduce the current order's usable inventory:

- Cyclic output seeds remain reserved by the executing CPU until dispatch completes.
- External inputs are checked against the current order in full, so finite orders never report a fixed reserve as missing.
- The resulting demand is the exact net material balance for the requested order.
- All arithmetic uses checked `long`; overflow makes the fast path decline the request and AE2 retains control.

The live AE2 bridge now collects the reachable network pattern graph and hands cyclic strongly connected components to the terminal. It supports single-pattern growth and multi-pattern nested growth without expanding every application. A terminal can operate independently for cyclic jobs and deliberately leaves acyclic jobs to AE2 or an optimizer interface.

Solved cyclic plans retain their ordered compressed batch schedule through AE2 job submission. The ring terminal owns a strict CPU dispatch cursor: only the current batch can be sent, a blocked batch cannot be skipped, and the cursor advances only by patterns AE2 actually pushed. The cursor and its remaining `long` repetition count persist with the crafting CPU across world reloads.

Ring-owned jobs also lock cyclic final-output seeds inside the selected crafting CPU. Provider outputs enter CPU inventory first and cannot be consumed by another network job. While any scheduled ring operation remains, none of that final-output carrier is delivered to the requester. Once dispatch is complete, the CPU releases only stock above the persisted initial seed reserve and flushes surplus accumulated before the last batch. Completion requires the compressed cursor to be finished, requested net output to reach zero, and in-flight final output to return; unrelated waiting keys cannot deadlock completion. Standalone crafting-terminal submissions have no AE2 requester, so returned net output satisfies `remainingAmount` independently of direct `CraftingLink` routing; AE2's normal finalization then returns that output, the retained seed, and other CPU inventory to network storage before the CPU becomes reusable.

### Supercomputing Crafting Optimizer Interface

`ae2lightoptimizer:supercomputing_crafting_optimizer_interface` (`超算合成优化接口`) independently accelerates ordinary acyclic plans. It requires an AE2 channel, consumes 8 AE/t, exposes its grid node on every side, and has no UI.

The Mixin hook intercepts AE2's network-wide `CraftingCalculation.runCraftAttempt` entry. It collects all reachable producers, selects listed substitutes against the inventory snapshot, models byproducts and container remainders, condenses SCCs, combines multiple producer routes, and writes compressed `usedItems`, `missingItems`, `emittedItems`, and `patternTimes` back into AE2's native `CraftingPlan`.

The adapter declines before mutation when checked arithmetic overflows or an executable integer schedule cannot be proven; AE2 then retains control. Multiple producers are split according to available raw stock instead of being treated as ambiguous. Live graph discovery has no fixed resource, pattern, or edge admission ceiling: every graph AE2 can enumerate remains eligible, and the planner's safety budget grows with the reachable graph. The project does not claim a universal mathematical optimum for every modded recipe semantic.

Accepted acyclic plans also retain the planner's compressed execution batches. The optimizer interface drives AE2's CPU directly from the current batch instead of rescanning the entire unordered task map on every dispatch attempt. AE2 still performs provider availability, input extraction, energy, waiting-output, cancellation, security, and completion handling.

## Independent takeover contract

The two blocks are independently gated by AE2's active-machine index and each graph class has one owner:

| Ring terminal | Optimizer interface | Acyclic graph | Cyclic graph |
| --- | --- | --- | --- |
| Offline | Offline | Original AE2 | Original AE2 |
| Online | Offline | Original AE2 | Ring terminal |
| Offline | Online | Optimizer interface | Original AE2 |
| Online | Online | Optimizer interface | Ring terminal |

When neither block is active, the hook returns before reachable-graph discovery, inventory extraction, child simulation state creation, missing/emitted item writes, pattern-count writes, schedule attachment, or callback cancellation. When one block is active but the discovered graph belongs to the other module, ownership is declined before the same AE2 state-mutation boundary. Unmarked jobs execute through AE2's original unordered task dispatch without a cursor, so a disconnected installation retains both original calculation and execution behavior.

When both blocks are online, ownership and execution state remain per job: optimizer-owned acyclic jobs never enter the ring output-lock branch, while ring-owned cyclic jobs retain their own schedule, cursor, and seed reserve on their selected crafting CPU. Parallel jobs do not share mutable cursor or recycling state.

## Visual state

Both blocks have distinct offline and active-network 16x16 textures derived from an AE2 crafting-CPU visual language. The retained ring terminal uses a cyan closed-loop mark. The optimizer uses a hash-grid compute core whose face is pixel-identical after every 90-degree rotation; its connected state lights cyan-white double rails, four white compute intersections, and eight amber endpoints while the offline state keeps the same geometry dim. Block state changes follow the managed AE2 node's active state, so a disconnected or inactive node never renders as connected.

The NeoForge mod icon is a transparent 64x64 isometric render of the connected recipe-ring terminal. Three contiguous faces use the real block texture and only necessary face lighting; the cube itself has no added silhouette border, seam stroke, or cyan outline. It sits inside a separate complete square viewport frame made from dark steel, metal-grey, cyan signal, and dark inner-edge layers. Every pixel along all four outer PNG edges is opaque while the framed interior remains transparent. Both generations package the same pixel-identical icon through `logoFile="ae2lightoptimizer.png"`.

## Crafting recipes

Both blocks use shaped 3x3 recipes built entirely from stable AE2 components. The Recipe Ring Solver Terminal places four calculation processors and opposing formation/annihilation cores around a crafting unit, representing cyclic input and output analysis. The Supercomputing Crafting Optimizer Interface places four calculation processors and four engineering processors around a crafting accelerator, reflecting its higher-cost global planning and dispatch role.

| Recipe Ring Solver Terminal | Supercomputing Crafting Optimizer Interface |
| --- | --- |
| `F C A` | `C E C` |
| `C U C` | `E A E` |
| `A C F` | `C E C` |

Ring symbols: `F` = Formation Core, `A` = Annihilation Core, `C` = Calculation Processor, `U` = Crafting Unit. Optimizer symbols: `C` = Calculation Processor, `E` = Engineering Processor, `A` = Crafting Accelerator. JEI discovers both standard shaped recipes automatically, and each GuideME page embeds its recipe.

## AE2 GuideME documentation

Both blocks are documented inside AE2's existing GuideME guide in English and Simplified Chinese. Hover either block item and hold AE2's guide key (`G` by default) to open its indexed page directly; the pages also appear under AE2's **Items, Blocks and Machines** section.

- **Recipe Ring Solver Terminal** explains independent cycle-only responsibility, SCC solving, the 16-cycle material reserve rule, network/channel requirements, and the safe runtime boundary.
- **Supercomputing Crafting Optimizer Interface** explains acyclic-plan acceleration, compressed tera/peta-scale calculation, ring-terminal cooperation, network requirements, and safe fallback to AE2.

The addon contributes pages to AE2's `ae2guide` resource tree through GuideME's cross-namespace loading. It does not register a separate guide, guide item, screen, or client key handler.

## Scale and verification

Each generation runs the same 57-test suite, including the solver, global-planner stress, graph-scale envelope, compressed batch, 16-cycle reserve, takeover-policy, block, Mixin/resource, shaped-recipe JSON, mod-icon, bilingual GuideME, time-free changelog, and completion-gate contracts. Execution regressions replay a 1,000-operation smithing-template growth job, a three-node irreducible ring, seed survival, pre-completion output locking, standalone terminal completion with no requester, P-scale release arithmetic, final-output transit draining, and independent parallel job cursors.

Stress coverage includes complex irreducible SCCs, dead 1:1 rings, multiple producer routes, shared acyclic dependencies, and peta-scale total material. The peta-scale three-pattern SCC represents `1.5×10^15` applications using 94 balance iterations and 96 compressed schedule batches, a measured work ratio of about `7.89×10^12`. A 64-component/128-route fixture plans a total of `10^15` raw items without false missing stock. Tests enforce explicit work-ratio bounds and a two-second planning deadline.

A separate scale envelope represents `10^12` distinct material types, `10^12` irreducible nodes, `3×10^12` edges, and `10^15` total material without allocating that object graph in a unit test. It reports an `Ω(V+E)` lower bound of `5×10^12` visits and a conservative minimum of 80 TB for 16-byte references alone, but does not gate live takeover. Exact planning cannot be constant-time at that distinct-node scale because AE2's result itself requires one `patternTimes` entry per selected pattern; repeated quantities on the graph are compressed into `long` counters.

Both data environments load the pinned AE2 and Mixin generations, and both JARs package the global bridge, Mixin, blocks, textures, and bilingual GuideME pages. Interactive in-game network submission remains a release acceptance check because the automated suite does not launch a persistent client world.

Mixin takeover is fail-fast rather than optional: mod initialization forces AE2's calculation, plan, executing-job, CPU-logic, and elapsed-time targets to load, and required injections use `require = 1, expect = 1`. `tools/verify_global_takeover.ps1` launches both generations and checks transformed bytecode for calculation cancellation, schedule attachment, current-batch selection, operation limiting, successful-push advancement, cyclic output locking, CPU inventory recycling, elapsed-time accounting, and schedule persistence.

## Build

From the repository root:

```powershell
.\build-1.21.1.ps1
.\build-26.1.2.ps1
.\tools\verify_global_takeover.ps1
```

Each launcher uses `.gradle-user-home/<version>` so dependency caches and daemons do not leak into ImmortalStorage. Set `AE2LIGHTOPTIMIZER_JAVA_HOME` to override the Gradle runtime JDK 21 and `AE2LIGHTOPTIMIZER_JAVA25_HOME` to expose an installed JDK 25 toolchain. Otherwise the launchers discover the current user's Gradle JDK cache and then use `JAVA_HOME`; Foojay remains the final toolchain-download fallback. Implement loader-independent algorithms in `shared/`; keep Minecraft, NeoForge, AE2, Mixin, resources, and persistence code inside the exact version directory.

Release history is recorded in [`CHANGELOG.md`](CHANGELOG.md). GitHub release assets are generation-qualified; install exactly one JAR matching the target Minecraft version.

## Isolated PCL instances

Two clean development instances are installed under the local PCL version root:

- `AE2-lightoptimizer-1.21.1`: NeoForge 21.1.235, AE2 19.2.17, GuideME 21.1.17, JEI 19.37.0.363, and the matching lightoptimizer JAR.
- `AE2-lightoptimizer-26.1.2`: NeoForge 26.1.2.94, AE2 26.1.10-beta, GuideME 26.1.12-beta, JEI 29.21.0.68, and the matching lightoptimizer JAR.

Both set PCL's `VersionArgumentIndieV2:True`. Their game directories, future configs, saves, logs, screenshots, and resource packs therefore remain inside their own version folders. They do not contain ImmortalStorage or inherit any mutable directory from its existing instances.

Refresh only the managed launch metadata and four pinned mod JARs with:

```powershell
.\tools\provision_pcl_instances.ps1 -PclRoot 'D:\PCL' -RefreshManagedFiles
```

Alternatively, set `PCL_ROOT` and omit `-PclRoot`.

The script never deletes or recreates an existing instance directory. It preserves `saves`, `config`, `defaultconfigs`, `screenshots`, `resourcepacks`, `logs`, `options.txt`, and all other unmanaged runtime state. Before and after deployment it records every save file's relative path, size, timestamp, and SHA-256 and fails if any value changes; a locked world also makes deployment stop before mutation. The legacy `-Recreate` flag remains only as a safe compatibility alias and has the same non-destructive behavior.

The managed `mods` directory uses a strict four-JAR whitelist. Stale AE2, GuideME, JEI, and addon JARs may be refreshed, but an unknown user-added JAR makes the script stop instead of deleting it. The script downloads only the pinned JEI artifact from its author Maven when it is not locally staged, and validates independent mode, absence of ImmortalStorage artifacts, and absence of filesystem links after deployment.

- 0.0.2 texture correction: attached crystal and fragment images are now the canonical item textures in both generations.
