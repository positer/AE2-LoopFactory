# Isolated Crafting Ripper runtime acceptance probe

This directory builds a separate **test mod**, `ae2lo_runtime_probe`. Its classes and resources must never be included in an AE2LO production JAR or ordinary installation. The probe depends on the real generation-matched AE2 and AE2LO artifacts, and invokes their existing storage, recipe decoder, planner, CPU submission and execution APIs.

The client helper creates a new `AE2LO-Ripper-Probe-*` world and refuses an existing save path. The server helper builds a physical 64-block 256k crafting-storage CPU (80 blocks for the layered fixture, 3,375 for the complete core fixture), an ME Drive containing an Infinite Loop Storage Cell, a finite Dense Energy Cell, a Recipe Ring Solver Terminal, and a Crafting Ripper. No CPU capacity field, recipe result, energy result or inventory result is overridden.

For the native Netherite Upgrade Smithing Template duplication recipe, each run requests **1,000,000 net templates** from one retained seed. Initial diamond and netherrack stocks are **3,000,000,000 each**, above signed 32-bit range. Assertions require exact seven-diamond/one-netherrack consumption per operation, survival of the one-template seed, native ring-terminal ownership, a submitted plan fitting the real CPU capacity, exactly one native CPU tick and one synchronous Ripper call, and exactly **50 AE** consumed inside that call. The probe records wall-clock execution time separately from game ticks.

By default a second run removes the first delivered million templates while retaining one seed, installs the Loop Card through the real upgrade inventory, verifies disabled pattern insertion, and repeats the same order through the automatic recipe catalog. The removed first output is explicitly recorded in the report. Set `ae2lo.probe.loopCard=false` to isolate the first run.

The optional `ae2lo.probe.chain=smithing` fixture keeps the same physical CPU and native template growth recipe, adds the real vanilla Netherite Pickaxe smithing recipe and one million diamond pickaxes/netherite ingots, and requests one million final Netherite Pickaxes. It runs once and validates exact consumption against the actual native plan. An intermediate template seed may be consumed by the final smithing operation: the existing ring contract reserves the final output carrier, not every intermediate carrier after the order completes. A plan with 999,999 growth operations and one million smithing operations must therefore finish with zero templates and consume 6,999,993 diamonds and 999,999 netherrack; a plan which explicitly retains one template must account for one million growth operations instead. The probe does not add a seed or other material to repair a planning failure. It records plan missing-material results before asserting submission readiness.

The optional `ae2lo.probe.chain=layered` fixture submits one complete five-recipe job for **1,000,000 Netherite Pickaxes**. It starts with one template seed, 3,000,000,000 each of diamonds, netherrack and oak logs, and 1,000,000 netherite ingots. There are no prepared planks, sticks or diamond pickaxes. All five encoded patterns come from actual vanilla recipe holders and their assembled outputs:

| Native recipe | Required applications |
| --- | ---: |
| Oak log → 4 oak planks | 250,000 |
| 2 oak planks → 4 sticks | 500,000 |
| 3 diamonds + 2 sticks → diamond pickaxe | 1,000,000 |
| Template + 7 diamonds + netherrack → 2 templates | 999,999 |
| Template + diamond pickaxe + netherite ingot → netherite pickaxe | 1,000,000 |

This fixture uses **80 real 256k storage blocks**, arranged as a 5×4×4 CPU with **20,971,520 bytes**. It runs once in manual pattern mode. Assertions cover the entire submitted job: production preflight and native submission must succeed, all five recipes must finish in exactly one CPU tick and one Ripper invocation, and the total extra fee must be 50 AE. Final network stock must contain 1,000,000 Netherite Pickaxes, 2,990,000,007 diamonds, 2,999,000,001 netherrack and 2,999,750,000 oak logs, with zero templates, planks, sticks, diamond pickaxes and netherite ingots. No intermediate result is inserted by the probe or delivered by a separate task.

The optional `ae2lo.probe.chain=core256m` fixture requests **3,000 256M Loop Storage Cores** from the real Loop Card recipe catalog. A 15×15×15 CPU contains **3,375 real 256k storage blocks**, providing **884,736,000 bytes**. The expected compressed plan requires approximately **769,888,153 bytes**; the exact live `plan.bytes()` must fit before native submission. The pinned AE2 calculator permits at most 17 blocks on each axis, so this physical fixture remains below its native maximum of 1,287,913,472 bytes. No capacity override is used.

The core fixture uses three adjacent finite Dense Energy Cells. The probe verifies all three nodes share the physical grid, sums their real capacities, and simulates acceptance before charging the network once with **4,500,000 AE**. This finite reserve supports the large CPU through screenshot and shutdown waits; there is no continuous or infinite power source. Other fixtures retain one cell and their original 1,500,000 AE charge.

This fixture also places a real Supercomputing Crafting Optimizer Interface next to the Ring Solver Terminal. Planning waits for the Ripper and both services to be active on the same grid. Their simultaneous presence is recorded; the cyclic core job must still have the `RING_TERMINAL` schedule owner.

`ReplayPlannerCapture.java` additionally reuses the actual captured recipe graph for an optimizer-only, ring-offline request for one million netherite ingots. It requires an acyclic plan, supercomputing ownership and exactly four million each of gold ingots and scrap. This separate production-planner replay verifies material calculation against the real catalog; it does not claim another native CPU submission or execution. Compile it with the matching generation's production JAR, Gson and JDK, then pass the successful run's evidence directory.

No lower-tier core, fragment or netherite ingot is preloaded. Initial storage has one Loop Crystal seed and 3,000,000,000 each of iron ingots, certus quartz crystals, fluix crystals, gold ingots, netherite scrap, singularities and Loop Crystal Powder. These are the explicit external material boundary: powder uses the inscriber or optional machine processing, singularities use condensation, and smelted ingots/scrap and fluix production also require processes outside the Ripper's supported recipe types. Netherite ingots have a valid crafting-table recipe, so the job must manufacture all 1,452,000 of them from gold and scrap. The core recipes do not require AE2 processors.

| Core tier, lowest to highest | Applications |
| --- | ---: |
| 1k | 59,049,000 |
| 4k | 19,683,000 |
| 16k | 6,561,000 |
| 64k | 2,187,000 |
| 256k | 729,000 |
| 1M | 243,000 |
| 4M | 81,000 |
| 16M | 27,000 |
| 64M | 9,000 |
| 256M | 3,000 |

The same submitted job must also perform 19,804,000 crystal-growth recipes, 78,853,000 crystal-to-fragment recipes and 1,452,000 netherite-ingot recipes: **188,681,000 recipe applications in total**. Exact net consumption is 352,836,000 iron ingots, 167,425,000 certus quartz, 19,804,000 fluix crystals, 5,808,000 gold ingots, 5,808,000 netherite scrap, 363,000 singularities and 29,160,000 powder. Final output is 3,000 256M cores and the original one-crystal seed; every lower core, fragment and netherite ingot must be zero. The whole job must pass production preflight and complete through one native CPU tick, one Ripper call and **50 AE total**, with exact network stock accounting.

The probe reads `ripper.getMainNode().getNode().getIdlePowerUsage()` directly and requires the Ripper's own base draw to remain exactly 5 AE/t before and after work. Network-wide idle power is recorded separately and is never substituted for that block-specific assertion.

`CpuTickObserver` and `RipperExecutionObserver` only observe method entry/return. They never cancel a callback, alter an argument, replace a result, invoke the executor directly, or bypass any production gate. The executor observer measures real available energy with `extractAEPower(1e9, SIMULATE, ONE)` immediately before and after execution, before native CPU finalization, so grid standby power does not get misreported as the per-order fee. AE2's `getStoredPower()` is a cached estimate which injection does not immediately update; it is recorded for diagnosis, never used to assert the fee. The first real launch exposed this distinction while successfully completing the million-operation recipe and exact material balances; its original failed report is retained.

## Component, catalog and continuation contracts

The following describe implemented acceptance checks and reproduction procedures. They are **not a claim that an unfinished run has passed**. Read the generation-specific run's final `report.json` together with its fixture report; record the actual artifact hashes and retain earlier failures. Compiling a fixture establishes API compatibility, not gameplay acceptance.

| `-Chain` | Main fixture evidence | Scope |
| --- | --- | --- |
| `exact_components` | `exact-components-report.json` | Missing exact input refusal, followed by two real orders of 3,000 exact book copies: manual pattern and Loop Card |
| `pack_catalog` | `pack-catalog-report.json` | Real datapack insertion, recipe/tag replacement, removal and restoration; actual network publication and component input validation |
| `tool_components` | `tool-components-report.json` | Ordinary quartz knife instant control and actual Unbreaking III substitute using native continuation |
| `native_catalog` | `native-catalog-report.json`, `native-continuation-roundtrip.json`, `native-cpu-first-commit.nbt` | Three real map extensions with full CPU serialization/restoration after the first operation |
| `catalog_audit` | `catalog-report.json` | Independent finite recipe witnesses, actual published inputs, late component refresh and the Loop Card lifecycle |

### Exact full components: two orders of 3,000

`exact_components` uses the live vanilla book-copy recipe. Source A contains `CUSTOM_NAME`, `WRITTEN_BOOK_CONTENT` and deeply nested `CUSTOM_DATA`, including the case-sensitive string `AbC_Keep_CASE`, an integer array, a required nested field and the long value `9007199254740993L`, beyond exact double precision. B is the same item with different book/name/data components. C retains A's name and book content but changes only deep custom data and omits the required field. The expected copied book comes from native `matches`/`assemble`, preserves the exact custom data and has the native incremented generation.

The negative case publishes a legal manual A pattern while storing only B, C and 3,000 writable books. It checks that the actual published pattern rejects both wrong variants, the real AE2 calculation reports A missing, and actual submission is rejected without material changes, an executor call or an immediate execution fee. Detached input-selection checks are labelled separately from the native submission.

After inserting A, the first real order must produce exactly 3,000 copies of the full A output key, consume exactly 3,000 writable books, and retain A1, B1 and C1. The full network key/count map is compared, not just the target item ID or a subset of its tags. Completion requires one native CPU call, one Ripper call, execution within one tick and exactly 50 AE. The fixture then explicitly removes only the first order's 3,000 delivered outputs, clears the manual patterns, installs the Loop Card and supplies another 3,000 writable books. The automatic catalog must publish the distinct A/B/C output variants and reject unsafe substitutions before a second real order is submitted under the same strict material, tick and fee conditions. A successful first order alone does not establish the second order's result.

### Live pack reload and typed SNBT

`pack_catalog` creates a datapack only beneath its fresh test world's `datapacks` directory. It uses the real server pack repository and `reloadResources`, then waits for native provider updates. Its phases establish the original recipes, load custom shaped/shapeless/smithing/stonecutting and component ingredients, replace output quantities and item tags, override a vanilla recipe, remove/filter recipes, and finally restore the original selected packs. Each phase compares independent live recipe `matches`/`assemble` results with the grid's actual published pattern objects and their accepted/rejected inputs. Final real ME item stock must be unchanged.

The exact ingredient is produced through NeoForge's native `DataComponentIngredient` and `Ingredient.CODEC`. Plain JSON numeric round trips can narrow NBT numeric tag types; the fixture records that result, then writes `minecraft:custom_data` using the native **typed SNBT string** supported by `CustomData.CODEC`. It verifies exact decoded custom data and that the nested integer remains an `IntTag` (tag ID 3), accepts A, and rejects B and component-free paper. This preserves the intended native type semantics; it does not weaken exact matching or teach production code to treat byte/int/long tags as interchangeable.

This fixture proves datapack-driven discovery, replacement and invalidation using standard recipe/ingredient codecs. It submits no crafting order, installs no KubeJS engine and makes no claim about a particular scripting engine's callbacks or arbitrary script-defined recipe semantics. Restoration failures remain failures even if an earlier publication phase succeeded.

### Actual tool components and random damage

`tool_components` uses the real AE2 quartz-cutting cable-anchor recipe: one quartz knife plus one iron ingot produces four anchors. Both runs encode an ordinary Certus Quartz Knife with substitution enabled and request **12 anchors**, exactly three recipe applications. Each starts with three actual knives and three iron ingots. The first run uses ordinary knives and must complete in one CPU call and one Ripper call within one tick, with deterministic total tool damage of three and a 50 AE fee.

The second run stores only same-item knives carrying actual Unbreaking III components; no ordinary knife remains available. Classification of the encoded ordinary pattern alone remains deterministic, while classification of the actual selected plan must require native continuation. The job must commit three operations in distinct ticks and charge 50 AE once for the whole job. After each operation, exact knife keys observed in the CPU plus network must equal the balance derived from the real remainder callbacks. Final tool count remains three, only native damage may change, enchantments and other components remain exact, and the complete final inventory must contain precisely the requested anchors and legal tool remainders.

`ToolRemainderObserver` observes the original `QuartzCuttingRecipe.getRemainingItems` return; it never invokes that method itself, changes RNG or supplies a fabricated result. The report separates callbacks within the production executor's entry/return boundary from callbacks outside it. The enchanted run requires exactly one such callback per committed operation and three in total within production execution, so a randomized remainder cannot be sampled in preparation and then extrapolated as a batch. Native AE2 planning may itself evaluate remainders; those observations are recorded separately and are not counted as extra production execution. The short fixture verifies call placement and real outcomes, not a statistical distribution of Unbreaking damage.

### World output identity and full CPU restoration

`native_catalog` requests three map extensions through the automatic Loop Card catalog, starting with three copies of a real scale-zero source map and 24 paper. There is no external assembler by default. The completed stock must contain three distinct newly allocated map IDs, each at the intended incremented scale, with no source maps, paper or `MAP_POST_PROCESSING` marker remaining. Intermediate observations must show one, two and three completed maps across native ticks. Repeatedly scaling an earlier output instead of consuming the remaining source maps fails this contract even if three recipe applications were reported.

After the first committed operation, `NativeContinuationAssertions` schedules restoration at `ServerTick.Post`, outside the active executor. It first performs the production State codec round trip and installs the recovered State on the real job. It then calls the real CPU's `writeToNBT`, writes and reads `native-cpu-first-commit.nbt` through binary NBT, and invokes the real CPU's `readFromNBT`. The fixture checks the exact physical inventory, pending pattern definitions and counts, final requested key, waiting entries, request amount, schedule cursor when present, continuation identity, paid flag, operation count and planned-to-actual output credits. It rebinds observation to the recreated job and link with the same crafting UUID, then lets operations two and three continue normally without canceling the old standalone link or resubmitting the plan.

The map's transient `MAP_POST_PROCESSING` component has no ordinary persistent component codec, so strict restoration must retain the production sidecar markers as well as ordinary component data. Successful completion requires both the map fixture and continuation assertions to pass, with three real operations and exactly 50 AE measured across executor calls. A State-only round trip, a successful NBT parse or three outputs without exact world identities is insufficient. This is a **complete CPU save/read continuation test in one running server**, not an operating-system process restart, crash recovery or proof of an entire server restart.

### Independent catalog audit

`catalog_audit` inventories live recipe IDs and the three allowed recipe types, constructs finite real inputs, and obtains expected outputs through native recipe matching/assembly and registered pattern decoding. It queries actual AE network publication rather than invoking the production catalog builder as its oracle. The report separates missing witnessed recipes, missing witnesses, unsupported types and allowed recipes for which no valid witness was constructed; an unproven recipe is not silently counted as covered. Arbitrary component/NBT combinations are not exhaustively enumerated.

The default stock-witness mode also injects concrete component-bearing inputs after the card is installed and waits for their new outputs to appear. Lifecycle checks cover retained manual patterns, blocked insertion, withdrawal without reinsertion, removal restoring the manual publication and insertion rules, and reinstallation restoring the catalog. Actual published dye/book pattern objects undergo detached input extraction and live validation, including rejected alternatives; this remains a directory/input validation audit, not a submitted CPU order. On 26.1.2, optional-empty smithing witnesses distinguish `native_encoder_rejected` from validated custom patterns, and the report also records registered catalog-pattern decoder/persistence checks. Those custom witnesses must never be described as native AE2 encoding successes.

## Reproduction and result interpretation

Use generation-matched production and separate probe JARs in the dedicated PCL instance. The launcher supports Windows PowerShell 5.1, derives real dependencies from installed version metadata, uses Java 21 for 1.21.1 or Java 25 for 26.1.2, and writes a Java argument file with a fixed offline probe identity. It does not read account tokens, open existing saves, copy worlds, alter PCL setup or stop other processes. `-JavaHome` can select a compatible installed runtime.

From the repository root, preparation and launch use **different fresh evidence directories** because preparation writes launch artifacts too:

```powershell
powershell -NoProfile -File .\tools\runtime-ripper-probe\launch-pcl-probe.ps1 -Generation 1.21.1 -Chain exact_components -EvidenceDir C:\Temp\ae2lo-exact-19-prepare -PrepareOnly
powershell -NoProfile -File .\tools\runtime-ripper-probe\launch-pcl-probe.ps1 -Generation 1.21.1 -Chain exact_components -EvidenceDir C:\Temp\ae2lo-exact-19-run1
```

Use `-Generation 26.1.2` and another evidence directory for the other generation; replace `-Chain` with any table entry to select that contract. `-PrepareOnly` validates the launch dependencies without starting Java and permits the probe JAR to be absent while it is being built. Actual launch requires both production and probe JARs, starts visible Minecraft, and writes `launch-plan.json`, `java-arguments.txt`, `client-stdout.log` and `client-stderr.log`. Repeat runs need fresh evidence directories and automatically generated fresh world names. Remove the separate helper from the dedicated instance after coordinated acceptance; never package it with production.

For standalone native AE2 jobs, `link.isDone()` is not a reliable completion flag: the pinned implementation signals a linked nexus, which standalone jobs do not have. The order fixtures use an uncanceled standalone link, CPU with no job and not busy, empty CPU inventory/waiting entries and the exact expected network results. A false native done flag alone is not a production failure; a true execution result alone is not complete acceptance either.

Preserve `report.json`, the fixture reports, event and client logs, launch metadata, screenshots and any binary CPU evidence together. A timeout may occur after a correct production commit because a test completion oracle is wrong; inspect the isolated executor fee, exact stock, native state and CPU/link snapshots before assigning the cause. Conversely, a successful submit or `COMPLETED` result does not prove all requested modes, typed components, world identities, reload phases or persistence checks passed. Record each successful stage only within its own evidence boundary. Keep original failures, use a new run directory after fixes, and do not overwrite a failed run with a later success. Normal world-save completion and client-process exit are separate from gameplay assertions; an unload hang must be reported separately and does not establish clean shutdown.

Runtime properties:

- `ae2lo.probe=true`: enable new-world client bootstrap and player-triggered server probe.
- `ae2lo.probe.world`: a fresh world name starting with `AE2LO-Ripper-Probe-`.
- `ae2lo.probe.reportDir` / `ae2lo.probe.output`: absolute evidence directory; the server prefers `reportDir`, with `output` as fallback.
- `ae2lo.probe.autoExit=true`: request normal Minecraft shutdown after native world and UI screenshots complete.
- `ae2lo.probe.auto=true`: start without a player on a dedicated disposable test server.
- `ae2lo.probe.chain=template|smithing|layered|core256m|catalog_audit|native_catalog|exact_components|pack_catalog|tool_components`: select one fixture. The default remains `template`; component, reload and continuation contracts are described above.
- `ae2lo.probe.catalog.stockWitnesses=true`: include concrete and late-inserted component witnesses in `catalog_audit` (default true).
- `ae2lo.probe.native.assembler=true`: optionally place a real adjacent molecular assembler in `native_catalog` for separate comparison; the default acceptance uses no external assembler.

Evidence includes `events.jsonl`, a final `report.json`, and native framebuffer PNGs under `screenshots/`. A missing or failing report is not a pass. `executeMillis` measures the production synchronous call while excluding the probe's pre-entry JSONL write; `sameExecutionTick` and `submissionToExecutionTicks` state the independent tick evidence. Global network idle draw is recorded separately.

For `core256m`, passive test Mixins additionally write `planner-request-N.json`, `planner-resources-N.json`, `planner-result-N.json` and `planner-bridge-N.json`. These capture the actual reachable graph, key aliases, shared planner status and takeover outcome without changing any input or return value. Native `plan_received` events include unfiltered `missingAll` and `usedAll` lists so an unexpected automatic-catalog ingredient cannot be hidden by the fixture's stock projection. The first complete-catalog core attempts exposed a producer-selection failure around reversible crystal-block compression; their original failing evidence remains preserved.

Compilation and launch are coordinated from outside the production Gradle source sets. Generation-specific `src/main/java` and `src/main/resources` trees belong solely to this test mod.
