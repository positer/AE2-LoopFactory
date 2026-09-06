# AE2LO — AE2-lightoptimizer

Official source repository: <https://github.com/positer/AE2-lightoptimizer>

Current release: **[0.0.4](https://github.com/positer/AE2-lightoptimizer/releases/tag/v0.0.4)**.

## Description

Applied Energistics 2 Lightweight Optimization (AE2LO) is a NeoForge addon for Applied Energistics 2. It adds universal Loop Storage Cells that share one byte budget across every AE2-registered key type, two network services that split cycle solving from acyclic plan acceleration, and a Crafting Ripper that validates and executes supported crafting chains in one server tick.

Loop Storage Cells require only AE2. The family covers a housing, ten cores, ten finite cells, and one infinite cell. The 1k-256k tiers keep AE2-equivalent capacity and the 63-type limit; the 1M-256M tiers use one aggregate pool equal to 63 times the original single-type ceiling; the infinite tier removes both amount and type limits. Every cell accepts items, fluids, FE, mana, source, soul, and any other registered AE2 addon key, charging the native amount-per-byte each key defines. Eleven portable cells reuse AE2's terminal, battery, four AE2-compatible upgrade slots, charge rate, idle drain, and powered insert or extract. Fifty-three acquisition recipes and eleven disassembly declarations cover all tiers through ordered and shapeless routes; the infinite cell requires the explosion transform of 64 256M cores plus one housing.

The Crafting Ripper provides four rows of AE2 pattern-provider slots for crafting-table, smithing and stonecutting patterns. A Loop Card switches it to automatic recipe discovery; the same card lets portable cells convert their stored FE into AE charge. A portable cell with both stored FE and positive AE charge also exposes its FE through the generation's native energy capability. Create milling and Mekanism crushing recipes are additive through the common `c:gems/loop_crystal` tag and never become dependencies. Two isolated NeoForge generations are maintained: 1.21.1 on Java 21 and 26.1.2 on Java 25. Minecraft 1.20.1 is not supported.

## 简介

应用能源 2 轻量优化（AE2LO）是一个面向应用能源 2（Applied Energistics 2）的 NeoForge 附属模组。它新增通用循环存储磁盘——所有注册到 AE2 的存储键类型共享同一字节预算——并提供两个独立负责循环图解算与无环计划加速的网络服务，以及在一个服务端 tick 内验证并执行受支持合成链的合成撕裂者。

循环存储磁盘只依赖 AE2。系列包含一个外壳、十个核心、十个有限磁盘和一个无限磁盘。1k 至 256k 档沿用 AE2 容量和 63 类限制；1M 至 256M 档使用等于原单类上限 63 倍的聚合池；无限档取消数量与种类限制。磁盘可存储物品、流体、FE、魔力、魔源、灵魂涌动及其他已注册附属键，并按各键类型自身的每字节占用计费。十一档便携磁盘复用 AE2 终端、电池、与 AE2 一致的四格升级卡插槽、充能速率、待机耗电和通电存取。共 53 个获取配方与 11 个拆解声明覆盖全部档位及有序、无序路线；无限磁盘必须通过 64 个 256M 核心加一个外壳的爆炸转换获得。

合成撕裂者提供四排 AE2 样板供应器风格的样板槽，接受工作台、锻造台与切石机样板。循环卡使其切换为自动配方发现，也能让便携磁盘把已存储的 FE 转换成自身 AE 电量。磁盘内同时有 FE 和非零 AE 电量时，还会通过对应世代原生能量能力作为随身电容器提供电量查询与提取。机械动力磨粉和 Mekanism 粉碎通过通用 `c:gems/loop_crystal` 标签按模组加载条件生效，均不是前置。维护两个隔离的 NeoForge 世代：1.21.1（Java 21）与 26.1.2（Java 25），不支持 Minecraft 1.20.1。

## Supported versions

| Minecraft | NeoForge | AE2 | Java |
| --- | --- | --- | --- |
| 1.21.1 | 21.1.235 | 19.2.17 | 21 |
| 26.1.2 | 26.1.2.94 | 26.1.10-beta | 25 |

Both maintained generations are independent Gradle projects and are fully isolated from ImmortalStorage. This repository has its own Git history and GitHub remote; it does not use the ImmortalStorage repository as an upstream, subtree, or submodule.

## Current release contents

AE2LO 0.0.4 retains the complete 0.0.3 surface and adds:

- Crafting Ripper: 36 pattern slots, AE2's native pattern-provider controls and priority page, whole-chain recipe validation, checked 64-bit batch execution, and Recipe Ring Solver Terminal compatibility. Idle and working drain is 5 AE/t; each committed whole-chain rip costs an additional 50 AE.
- Loop Card: one advanced card plus one Loop Crystal, shapeless. On the ripper it disables pattern insertion while retaining removable installed patterns and automatically advertises encodable crafting, smithing and stonecutting recipes; removing it restores the physical patterns. On a portable cell it enables stored-FE-to-AE charging.
- Portable capacitor capability: stored FE is visible and extractable through NeoForge's native energy API while both FE and AE charge are positive. The 1.21.1 adapter saturates the legacy integer query at its API limit; the 26.1.2 adapter uses transactional transfers and native long amount queries.

The retained features include:

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
- Version-separated NeoForge 1.21.1 and 26.1.2 artifacts, bilingual AE2 GuideME pages, Mixin takeover validation, and non-destructive provisioning tooling.

## Network blocks

### Crafting Ripper

`ae2lightoptimizer:crafting_ripper` (`合成撕裂者`) opens an AE2 Pattern Provider interface whose main area contains only four rows of nine pattern slots, with the player inventory below and native upgrade/control panels beside it. Install encoded crafting-table, smithing-table or stonecutter patterns; other processing patterns are rejected. It requires an active powered AE2 channel. The original provider's priority, blocking, visibility and configuration workflows are reused.

Submit a crafting job through AE2 as usual. Before initial material extraction, the ripper validates the complete selected chain. Execution rechecks live recipes and replays the chain against private CPU inventory using checked `long` balances, then commits the result within one server tick. Repeated operations remain compressed, including ring-terminal schedules; container returns follow the selected recipes and cyclic seeds follow the ring terminal's seed-reserve policy. Calculation and delivery are distinct: a requester that cannot accept output can delay delivery without charging another 50 AE or rerunning the recipes.

Its shaped recipe is:

| Iron Ingot | Quantum Entangled Singularity | Iron Ingot |
| --- | --- | --- |
| Formation Core | 4M Loop Storage Core | Annihilation Core |
| Iron Ingot | Quantum Entangled Singularity | Iron Ingot |

A Loop Card switches the provider to automatically discovered, recipe-backed patterns. All 36 installed pattern slots turn grey and reject new insertions, including through the Pattern Access Terminal; existing patterns remain removable. Removing the card restores normal pattern insertion and advertising. Automatic discovery only publishes a concrete pattern when a real recipe and valid assembled output can be demonstrated; it does not invent fixed outputs for recipes with no encodable representative input.

Discovery covers the three recipe types independently of recipe-book visibility or concrete recipe class, including AE2 and addon storage upgrades, dyeing, fireworks, books, banners and maps. Exact component variants are derived from real network inputs and refreshed when new keys arrive. Arbitrary text and every possible dye mixture are not globally pre-generated. The 26.1.2 adapter also preserves genuinely empty optional smithing slots.

The active server recipe manager is authoritative for modpacks: custom namespaces, data-pack overrides, tag changes and recipe removal are reflected after reload. Native ingredient predicates and complete item components remain authoritative during selection and execution, including nested custom data and its numeric tag types. Component alternatives cannot substitute a different assembled result. AE2 quartz-cutting recipes are classified from the actual selected tool's components before batch execution, including Unbreaking durability effects.

Map expansion and AE2 quartz-cutting recipes requiring native random durability handling use a separate native continuation, one recipe operation per tick. It runs the normal crafting callbacks and keeps real products and their changing identities inside the originating CPU job; no external assembler is required. This explicit exception to instant ripping preserves world data and native tool behavior. The continuation retains 5 AE/t device drain and charges 50 AE once for the committed job, with progress, payment and output identity saved together.

### Loop Card and portable energy

`ae2lightoptimizer:loop_card` (`循环卡`) is crafted shapelessly from an AE2 **Advanced Card** and a Loop Crystal. Its texture retains the native acceleration-card shell, contacts and status pixels, replacing only the central emblem with circular arrows.

Install one in a portable Loop Storage Cell's existing upgrade slots to charge its AE battery from stored FE, using AE2's configured FE/AE conversion and native charging limit. The FE storage key comes from an installed AE2 energy-storage addon; the card does not introduce a new hard dependency. Charging can restart an empty AE battery when the card and stored FE are present.

With stored FE and positive AE charge, the portable cell exposes FE to other mods through NeoForge's item energy capability. This capacitor behavior does not require a Loop Card. Zero AE charge hides the capacitor until it is recharged. Simulated queries do not consume energy, and portable terminal caches refresh after external FE changes so stale contents cannot restore already-consumed energy.

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

The two original service blocks have distinct offline and active-network 16x16 textures derived from an AE2 crafting-CPU visual language. The retained ring terminal uses a cyan closed-loop mark. The optimizer uses a hash-grid compute core whose face is pixel-identical after every 90-degree rotation; its connected state lights cyan-white double rails, four white compute intersections, and eight amber endpoints while the offline state keeps the same geometry dim. Block state changes follow the managed AE2 node's active state, so a disconnected or inactive node never renders as connected.

The NeoForge mod icon is a transparent 64x64 isometric render of the connected recipe-ring terminal. Three contiguous faces use the real block texture and only necessary face lighting; the cube itself has no added silhouette border, seam stroke, or cyan outline. It sits inside a separate complete square viewport frame made from dark steel, metal-grey, cyan signal, and dark inner-edge layers. Every pixel along all four outer PNG edges is opaque while the framed interior remains transparent. Both generations package the same pixel-identical icon through `logoFile="ae2lightoptimizer.png"`.

The Crafting Ripper shares the existing metal frame and uses a nine-cell crafting grid. Its active state lights the same grid cyan; the offline state dims it without moving any pixels.

## AE2 GuideME documentation

All three network blocks, the Loop Card and the complete Loop Storage family are documented inside AE2's existing GuideME guide in English and Simplified Chinese. Hover any indexed item and hold AE2's guide key (`G` by default) to open its page directly; the pages also appear under AE2's **Items, Blocks and Machines** section.

- **Recipe Ring Solver Terminal** explains independent cycle-only responsibility, SCC solving, the 16-cycle material reserve rule, network/channel requirements, and the safe runtime boundary.
- **Supercomputing Crafting Optimizer Interface** explains acyclic-plan acceleration, compressed tera/peta-scale calculation, ring-terminal cooperation, network requirements, and safe fallback to AE2.
- **Crafting Ripper / Loop Card** explain the four-row provider interface, card locking, recipe-backed automatic mode, energy costs, portable charging and capacitor capability.
- **Loop Storage Cells** indexes the housing, ten cores, eleven stationary cells, and eleven portable cells; documents k-tier limits, M-tier 63× aggregate capacity, the unlimited aggregate infinite tier, dynamic AE2 key-type compatibility, 53 acquisition recipes, native portable power/disassembly behavior, and the exact 64-core TNT transform.

The addon contributes pages to AE2's `ae2guide` resource tree through GuideME's cross-namespace loading. It does not register a separate guide, guide item, screen, or client key handler.

## Scale and verification

The 0.0.4 release builds pass **138 tests on 1.21.1** and **136 tests on 26.1.2**, with no failures, errors or skips. Each JAR contains 76 recipes, with 116 AE2LO classes on 1.21.1 and 122 on 26.1.2, and no bundled mod JARs or gameplay-test helper classes. Real AE2 startup and transformed-bytecode checks cover whole-chain preflight, atomic execution, paid-state persistence, 64-bit task counters and native provider-lock callbacks. The same artifacts are installed in the two independent AE2LO PCL instances.

Both actual clients completed an order for **3,000 256M Loop Storage Cores** with the Loop Card, supercomputing interface and ring terminal online. The 13 selected recipes represent **188,681,000 applications**, compressed into 38 batches. The entire native CPU job completed through one Ripper call in one server tick, consumed exactly **50 AE** beyond its **5 AE/t** base draw, preserved one crystal seed and matched every raw-material and intermediate balance. No lower core, fragment or netherite ingot was preloaded; powders, singularities and other materials made by unsupported machines were explicit external inputs. A separate replay of the captured in-game catalog verifies optimizer-only acyclic material planning with the ring terminal offline. The [isolated acceptance harness](tools/runtime-ripper-probe/README.md) documents these boundaries and reproduction steps.

双版本已实机完成 **3000 个 256M 循环存储核心**的整单验收：循环卡、超算和环终端同时在线，十级核心及水晶循环共 **188,681,000 次配方操作**在一个 tick、一次撕裂调用内完成，整单额外消耗 **50 AE**，基础耗电 **5 AE/t**。未预放下级核心、碎片或下界合金锭；机器加工的基础材料按明确边界供应。另用实机捕获的配方图验证了无环任务由超算独立进行材料解算。界面已实机检查四排样板、单升级槽、灰色锁定及无返回槽。

Both installed clients also pass two precise-component orders of 3,000 book copies, one with manual patterns and one with a Loop Card. Deep custom-data mismatches remain unconsumed, missing precise input rejects before execution, and each completed order uses one CPU tick/call and 50 AE. Three actual data-pack reloads per client verify recipe/tag replacement, removal and restoration. Native map continuations survive a complete CPU binary save/read after their first operation and produce three independent real map IDs with one total 50 AE fee; this is a live-world CPU persistence test, not a full server restart.

双版本精准 NBT 实测分别通过手动样板、循环卡两笔 3000 件订单：深层数据错误的同类材料不被消耗，缺少正确材料时在执行前拒绝；每笔仍为一个 tick、一次调用、50 AE。每个客户端另完成三次真实数据包重载，验证配方和标签变更、删除与恢复。地图续作在首步后完成整个 CPU 的二进制保存恢复，最终产生三张独立新地图且整单只收费一次。

The final twelve installed-client scenarios pass on the deployed artifacts. Temporary test mods are removed; both instances retain their six production/dependency mod JARs, and all 335 original non-production baseline files remain unchanged. Evidence and precise test boundaries are retained in `archive/2026-09-06-loop-card-catalog/`.

This gameplay fixture does not cover requester backpressure or third-party portable equipment. A 1.21.1 test client intermittently stalled in native chunk unloading after a completed pre-save; the evidence retains its thread dump and narrowly scoped process stop, without attributing the stall to a JDK or mod cause. The corresponding 26.1.2 acceptance completed normal saving and exit.

Automated coverage also includes solver and global-planner stress, compressed schedules, takeover policy, recipe/resource contracts, portable power delegation, grouped storage-capacity accounting, asset hashes and dynamic key registration. Historical release measurements are kept separately in [OVERVIEW.md](OVERVIEW.md#historical-003-verification-baseline).

Stress coverage includes complex irreducible SCCs, dead 1:1 rings, multiple producer routes, shared acyclic dependencies, and peta-scale total material. The peta-scale three-pattern SCC represents `1.5×10^15` applications using 94 balance iterations and 96 compressed schedule batches, a measured work ratio of about `7.89×10^12`. A 64-component/128-route fixture plans a total of `10^15` raw items without false missing stock. Tests enforce explicit work-ratio bounds and a two-second planning deadline.

A separate scale envelope represents `10^12` distinct material types, `10^12` irreducible nodes, `3×10^12` edges, and `10^15` total material without allocating that object graph in a unit test. It reports an `Ω(V+E)` lower bound of `5×10^12` visits and a conservative minimum of 80 TB for 16-byte references alone, but does not gate live takeover. Exact planning cannot be constant-time at that distinct-node scale because AE2's result itself requires one `patternTimes` entry per selected pattern; repeated quantities on the graph are compressed into `long` counters.

Mixin takeover is fail-fast rather than optional: mod initialization forces AE2's calculation, plan, executing-job, CPU-logic, and elapsed-time targets to load, and required injections use `require = 1, expect = 1`. `tools/verify_global_takeover.ps1` launches both generations and checks transformed bytecode for calculation cancellation, schedule attachment, current-batch selection, operation limiting, successful-push advancement, cyclic output locking, CPU inventory recycling, elapsed-time accounting, and schedule persistence.

## Build

From the repository root:

```powershell
.\build-1.21.1.ps1
.\build-26.1.2.ps1
.\tools\verify_global_takeover.ps1
.\tools\verify_crafting_ripper_runtime.ps1
```

Each launcher uses `.gradle-user-home/<version>` so dependency caches and daemons do not leak into ImmortalStorage. Set `AE2LIGHTOPTIMIZER_JAVA_HOME` to override the Gradle runtime JDK 21 and `AE2LIGHTOPTIMIZER_JAVA25_HOME` to expose an installed JDK 25 toolchain. Otherwise the launchers discover the current user's Gradle JDK cache and then use `JAVA_HOME`; Foojay remains the final toolchain-download fallback. Both adapters force UTF-8 resource handling and expand templates only in `META-INF/neoforge.mods.toml`; JSON, GuideME Markdown, PNG, and every other resource pass through without filtering. Implement loader-independent algorithms in `shared/`; keep Minecraft, NeoForge, AE2, Mixin, resources, and persistence code inside the exact version directory.

Release history is recorded in [`CHANGELOG.md`](CHANGELOG.md). GitHub release assets are generation-qualified; install exactly one JAR matching the target Minecraft version.
