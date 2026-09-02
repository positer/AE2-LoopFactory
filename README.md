# AE2LO — AE2-lightoptimizer

Official source repository: <https://github.com/positer/AE2-lightoptimizer>

Current release: **0.0.3**

## Description

Applied Energistics 2 Lightweight Optimization (AE2LO) is a UI-free NeoForge addon for Applied Energistics 2. It adds universal Loop Storage Cells that share one byte budget across every AE2-registered key type, plus two network blocks that split cycle solving from acyclic plan acceleration while leaving unsupported recipe semantics to AE2.

Loop Storage Cells require only AE2. The family covers a housing, ten cores, ten finite cells, and one infinite cell. The 1k-256k tiers keep AE2-equivalent capacity and the 63-type limit; the 1M-256M tiers use one aggregate pool equal to 63 times the original single-type ceiling; the infinite tier removes both amount and type limits. Every cell accepts items, fluids, FE, mana, source, soul, and any other registered AE2 addon key, charging the native amount-per-byte each key defines. Eleven portable cells reuse AE2's terminal, battery, four AE2-compatible upgrade slots, charge rate, idle drain, and powered insert or extract. Fifty-three acquisition recipes and eleven disassembly declarations cover all tiers through ordered and shapeless routes; the infinite cell requires the explosion transform of 64 256M cores plus one housing.

Loop Crystal materials and the two processing blocks complete the release. Create milling and Mekanism crushing recipes are additive through the common `c:gems/loop_crystal` tag and never become dependencies. Two isolated NeoForge generations are maintained: 1.21.1 on Java 21 and 26.1.2 on Java 25. Minecraft 1.20.1 is not supported. Generation-qualified release JARs, bilingual GuideME pages, Mixin takeover verification, and non-destructive PCL tooling are published from this repository.

## 简介

应用能源 2 轻量优化（AE2LO）是一个面向应用能源 2（Applied Energistics 2）的无界面 NeoForge 附属模组。它新增通用循环存储磁盘——所有注册到 AE2 的存储键类型共享同一字节预算——并提供两个网络方块，把循环图解算与无环计划加速分开，不支持的合成语义仍安全回退给 AE2。

循环存储磁盘只依赖 AE2。系列包含一个外壳、十个核心、十个有限磁盘和一个无限磁盘。1k 至 256k 档沿用 AE2 容量和 63 类限制；1M 至 256M 档使用等于原单类上限 63 倍的聚合池；无限档取消数量与种类限制。磁盘可存储物品、流体、FE、魔力、魔源、灵魂涌动及其他已注册附属键，并按各键类型自身的每字节占用计费。十一档便携磁盘复用 AE2 终端、电池、与 AE2 一致的四格升级卡插槽、充能速率、待机耗电和通电存取。共 53 个获取配方与 11 个拆解声明覆盖全部档位及有序、无序路线；无限磁盘必须通过 64 个 256M 核心加一个外壳的爆炸转换获得。

循环水晶系列与两个处理方块构成其余内容。机械动力磨粉和 Mekanism 粉碎通过通用 `c:gems/loop_crystal` 标签按模组加载条件生效，均不是前置。维护两个隔离的 NeoForge 世代：1.21.1（Java 21）与 26.1.2（Java 25），不支持 Minecraft 1.20.1。世代限定发布 JAR、双语 GuideME 页面、Mixin 接管校验与非破坏式 PCL 工具均发布于本仓库。

## Supported versions

| Minecraft | NeoForge | AE2 | Java |
| --- | --- | --- | --- |
| 1.21.1 | 21.1.235 | 19.2.17 | 21 |
| 26.1.2 | 26.1.2.94 | 26.1.10-beta | 25 |

Both maintained generations are independent Gradle projects and are fully isolated from ImmortalStorage. This repository has its own Git history and GitHub remote; it does not use the ImmortalStorage repository as an upstream, subtree, or submodule.

## Current release contents

AE2LO 0.0.3 includes the following complete surface:

- Two UI-free AE2 network services: the Recipe Ring Solver Terminal for cyclic graphs and the Supercomputing Crafting Optimizer Interface for acyclic graphs.
- Four registered Loop Crystal materials with bilingual names, models, textures, common `c:` tags, creative-tab exposure, conversion recipes, and Mysterious Cube bonus drop.
- Ordered Loop Crystal growth, fragment recombination, direct decomposition, block compression/decompression, and powder conversion recipes.
- Generic SCC planning for self-growth, multi-recipe, nested, and 16-node ring graphs with compressed executable schedules.
- Additive optional Create Milling and Mekanism Crushing compatibility; each uses `c:gems/loop_crystal`, activates only when its own mod is loaded, and adds no external dependency.
- Complete Loop Storage Cell family: housing, 1k/4k/16k/64k/256k and 1M/4M/16M/64M/256M cores and cells, plus the infinite cell. Cells accept every AE2-registered key type; k tiers retain AE2-equivalent capacity and 63 types, M tiers use one DISK-style pool equal to 63 times their original single-type ceiling, and the infinite tier uses the same aggregate model without amount or type limits. M and infinite cells do not display a separate type counter.
- Loop Storage Cells advertise acceptance from their current per-key capacity. When one cell is full, the same item, fluid, FE, mana, source, soul, or addon key continues into the next compatible Loop Storage Cell instead of being limited to one disk.
- Eleven Portable Loop Storage Cells cover every finite tier and the infinite tier. They inherit AE2's portable terminal, battery, four upgrade slots, energy-card capacity multiplier, charge rate, idle drain, and powered insertion/extraction. Creative mode exposes both empty and fully charged AE-power variants.
- Portable-cell tinting follows each generation's native AE2 color contract; the 1.21.1 adapter explicitly restores opaque alpha so its layered housing, screen, LED, and side textures cannot render transparent.
- Portable Loop Storage Cell upgrade slots expose the same cards as AE2 portable item/fluid cells: fuzzy, inverter, equal distribution, void, and energy card x2. The cells register these through AE2's `Upgrades` API, so the slot hover hint and insertion validation match AE2, and the loop cell inventory applies partition filtering, inverter mode, void overflow, equal distribution, and the energy-card charge multiplier.
- Exactly 53 Loop Storage acquisition recipes per generation: the original 32 housing/core/cell recipes plus 20 finite portable-cell shapeless recipes and one infinite portable recipe. Every finite portable tier accepts either `ME Chest + Energy Cell + matching Loop Storage Cell` or `ME Chest + Energy Cell + housing + matching core`; because no infinite core exists, the infinite portable tier has only `ME Chest + Energy Cell + Infinite Loop Storage Cell`. Eleven AE2-native disassembly declarations additionally let empty portable cells return their construction parts and transfer remaining AE power back into the Energy Cell.
- Version-separated NeoForge 1.21.1 and 26.1.2 artifacts, bilingual AE2 GuideME pages, Mixin takeover validation, and non-destructive PCL tooling. Both isolated PCL test instances currently run the matching 0.0.3 artifact.

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

## Network-block crafting recipes

Both blocks use shaped 3x3 recipes built entirely from stable AE2 components. The Recipe Ring Solver Terminal places four calculation processors and opposing formation/annihilation cores around a crafting unit, representing cyclic input and output analysis. The Supercomputing Crafting Optimizer Interface places four calculation processors and four engineering processors around a crafting accelerator, reflecting its higher-cost global planning and dispatch role.

| Recipe Ring Solver Terminal | Supercomputing Crafting Optimizer Interface |
| --- | --- |
| `F C A` | `C E C` |
| `C U C` | `E A E` |
| `A C F` | `C E C` |

Ring symbols: `F` = Formation Core, `A` = Annihilation Core, `C` = Calculation Processor, `U` = Crafting Unit. Optimizer symbols: `C` = Calculation Processor, `E` = Engineering Processor, `A` = Crafting Accelerator. JEI discovers both standard shaped recipes automatically, and each GuideME page embeds its recipe.

Loop Storage contributes 53 acquisition recipes and eleven portable disassembly declarations in each generation. Every finite stationary tier supports both the requested shaped recipe and the shapeless core-plus-housing assembly; the infinite stationary tier is an `ae2:transform` recipe that consumes exactly 64 256M cores and one housing under an explosion. The ten finite portable tiers each add both requested shapeless routes, while the infinite portable tier adds only the cell-based route because no infinite core exists. Together with the two network blocks and eight Loop Crystal recipes, each generation packages 74 AE2LO recipe JSON files.

## AE2 GuideME documentation

Both service blocks and the complete Loop Storage family are documented inside AE2's existing GuideME guide in English and Simplified Chinese. Hover any indexed item and hold AE2's guide key (`G` by default) to open its page directly; the pages also appear under AE2's **Items, Blocks and Machines** section.

- **Recipe Ring Solver Terminal** explains independent cycle-only responsibility, SCC solving, the 16-cycle material reserve rule, network/channel requirements, and the safe runtime boundary.
- **Supercomputing Crafting Optimizer Interface** explains acyclic-plan acceleration, compressed tera/peta-scale calculation, ring-terminal cooperation, network requirements, and safe fallback to AE2.
- **Loop Storage Cells** indexes the housing, ten cores, eleven stationary cells, and eleven portable cells; documents k-tier limits, M-tier 63× aggregate capacity, the unlimited aggregate infinite tier, dynamic AE2 key-type compatibility, 53 acquisition recipes, native portable power/disassembly behavior, and the exact 64-core TNT transform.

The addon contributes pages to AE2's `ae2guide` resource tree through GuideME's cross-namespace loading. It does not register a separate guide, guide item, screen, or client key handler.

## Scale and verification

The NeoForge 1.21.1 build runs 102 tests and the NeoForge 26.1.2 build runs 101 tests, all with zero failures, errors, or skips. Their common coverage includes the solver, global-planner stress, graph-scale envelope, compressed batch, 16-cycle reserve, takeover policy, block and Mixin contracts, shaped-recipe JSON, portable recipes and native power delegation, mod icon, bilingual GuideME, time-free changelog, storage tiers, grouped quotient/remainder capacity accounting, immutable and strict-recolor asset hashes, dynamic key registration, and full/status boundaries. The 1.21.1 adapter also has direct inventory tests for its generation-specific storage implementation. Execution regressions replay a 1,000-operation smithing-template growth job, a three-node irreducible ring, seed survival, pre-completion output locking, standalone terminal completion with no requester, P-scale release arithmetic, final-output transit draining, and independent parallel job cursors.

Stress coverage includes complex irreducible SCCs, dead 1:1 rings, multiple producer routes, shared acyclic dependencies, and peta-scale total material. The peta-scale three-pattern SCC represents `1.5×10^15` applications using 94 balance iterations and 96 compressed schedule batches, a measured work ratio of about `7.89×10^12`. A 64-component/128-route fixture plans a total of `10^15` raw items without false missing stock. Tests enforce explicit work-ratio bounds and a two-second planning deadline.

A separate scale envelope represents `10^12` distinct material types, `10^12` irreducible nodes, `3×10^12` edges, and `10^15` total material without allocating that object graph in a unit test. It reports an `Ω(V+E)` lower bound of `5×10^12` visits and a conservative minimum of 80 TB for 16-byte references alone, but does not gate live takeover. Exact planning cannot be constant-time at that distinct-node scale because AE2's result itself requires one `patternTimes` entry per selected pattern; repeated quantities on the graph are compressed into `long` counters.

Both data environments load the pinned AE2 and Mixin generations. Each 0.0.3 JAR packages the global bridge and Mixins, storage handler/inventory, all 33 Loop Storage items (ten cores, eleven stationary cells, eleven portable cells, and the housing), ten byte-locked core textures, strict portable texture layers, eleven drive models, 64 storage recipe JSON files, and the bilingual Loop Storage GuideME page. Interactive in-game network submission, portable-terminal use, and drive interaction remain release acceptance checks because the automated suite does not launch a persistent client world.

Mixin takeover is fail-fast rather than optional: mod initialization forces AE2's calculation, plan, executing-job, CPU-logic, and elapsed-time targets to load, and required injections use `require = 1, expect = 1`. `tools/verify_global_takeover.ps1` launches both generations and checks transformed bytecode for calculation cancellation, schedule attachment, current-batch selection, operation limiting, successful-push advancement, cyclic output locking, CPU inventory recycling, elapsed-time accounting, and schedule persistence.

## Build

From the repository root:

```powershell
.\build-1.21.1.ps1
.\build-26.1.2.ps1
.\tools\verify_global_takeover.ps1
```

Each launcher uses `.gradle-user-home/<version>` so dependency caches and daemons do not leak into ImmortalStorage. Set `AE2LIGHTOPTIMIZER_JAVA_HOME` to override the Gradle runtime JDK 21 and `AE2LIGHTOPTIMIZER_JAVA25_HOME` to expose an installed JDK 25 toolchain. Otherwise the launchers discover the current user's Gradle JDK cache and then use `JAVA_HOME`; Foojay remains the final toolchain-download fallback. Both adapters force UTF-8 resource handling and expand templates only in `META-INF/neoforge.mods.toml`; JSON, GuideME Markdown, PNG, and every other resource pass through without filtering. Implement loader-independent algorithms in `shared/`; keep Minecraft, NeoForge, AE2, Mixin, resources, and persistence code inside the exact version directory.

Release history is recorded in [`CHANGELOG.md`](CHANGELOG.md). GitHub release assets are generation-qualified; install exactly one JAR matching the target Minecraft version.

## Isolated PCL instances

The repository includes non-destructive provisioning support for two existing isolated PCL development instances. **AE2LO 0.0.3 was deployed to both matching instances on 2026-09-01 and refreshed with the portable-tint plus mounted-cell persistence fix on 2026-09-02**, with saves and configuration left unchanged.

- `AE2-lightoptimizer-1.21.1`: NeoForge 21.1.235, AE2 19.2.17, GuideME 21.1.17, JEI 19.37.0.363, Applied Flux 2.1.5, Glodium 2.2, and the generation-matched AE2LO JAR.
- `AE2-lightoptimizer-26.1.2`: NeoForge 26.1.2.94, AE2 26.1.10-beta, GuideME 26.1.12-beta, JEI 29.21.0.68, Applied Flux 1.0.1, Glodium 1.2, and the generation-matched AE2LO JAR.

Applied Flux is installed only in these development instances to exercise automatic discovery of a third-party FE storage key type. Glodium is its required library. Neither is declared as an AE2LO dependency or included in the release artifact; AE2 remains the only storage-mod prerequisite.

Both set PCL's `VersionArgumentIndieV2:True`. Their game directories, future configs, saves, logs, screenshots, and resource packs therefore remain inside their own version folders. They do not contain ImmortalStorage or inherit any mutable directory from its existing instances.

Refresh only the managed launch metadata and six pinned mod JARs with:

```powershell
.\tools\provision_pcl_instances.ps1 -PclRoot 'D:\PCL' -RefreshManagedFiles
```

Alternatively, set `PCL_ROOT` and omit `-PclRoot`.

The script never deletes or recreates an existing instance directory. It preserves `saves`, `config`, `defaultconfigs`, `screenshots`, `resourcepacks`, `logs`, `options.txt`, and all other unmanaged runtime state. Before and after deployment it records every save file's relative path, size, timestamp, and SHA-256 and fails if any value changes; a locked world also makes deployment stop before mutation. The legacy `-Recreate` flag remains only as a safe compatibility alias and has the same non-destructive behavior.

The managed `mods` directory uses a strict six-JAR whitelist. Stale AE2, GuideME, JEI, AE2LO, Applied Flux, and Glodium JARs may be refreshed, but an unknown user-added JAR makes the script stop instead of deleting it. The script downloads only explicitly pinned artifacts when they are not locally staged, and validates independent mode, absence of ImmortalStorage artifacts, and absence of filesystem links after deployment.
