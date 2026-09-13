# Isolated Loop Factory runtime probe

This helper is a separate test mod. Never package it in a production JAR or install it as a normal dependency. Both generations use independent source trees and helper JARs.

The September 12 ID input fixture uses `verify-id-insertion-background.ps1 -Generation <generation> -Mode none|jei|emi -EvidenceName <fresh-name>`. It pins and isolates optional viewers, executes 23 carried-stack cases plus four actual viewer-drag cases, and restores the incoming file inventory exactly. Modern EMI is rejected before any file writes because this workspace has no verified compatible native binary. `verify-id-insertion.py` independently reconstructs complete text hashes, native UTF-16 caret/selection, 65,536 source-size behavior, actual container contents, unchanged client/server resources, save acknowledgements and PNG capture inventory. Native mouse callbacks and NeoForge drag dispatch are synthetic inputs in the real client; they are not OS mouse automation.

`verify-background.ps1 -ValidateExisting -ValidationName <fresh-token>` runs the same postconditions against a completed existing `EvidenceName`, with the original mode and scale arguments. It does not build, launch a client or copy run/mods. It requires native exit 0, current production/helper hashes, original before/after identities and requested scales, then validates all native reports and snapshots the original file hashes. It creates a separate sibling revalidation directory; an originally absent computed preview-pixel sidecar may be appended only after exact comparison. Existing raw evidence and the original launcher's exit status remain unchanged. This entry does not turn a failed native scene or an unfinished process into a pass.

Build with the matching root launcher and `-I ../../tools/runtime-factory-probe/probe.init.gradle factoryProbeJar`. Put the matching helper JAR in that generation's development `run/mods`. Launch `runClient` with JVM properties `ae2lf.probe=true` and an absolute `ae2lf.probe.reportDir`.

The client creates a new UUID-named `AE2LF-Factory-Probe-*` world and refuses an existing path. It does not open or copy PCL worlds. Render/simulation distances and pause-on-focus-loss are changed in memory.

- `factory-report.json`: physical grids, cable-adjacent barrels, virtual GET, two partial transfers (4 then 5), excluded gold, production continuation codec round trips, tagged redstone and expiration. The fixture explicitly removes five destination items between transfers.
- `native-cpu-report.json`: real AE2 CPU/drive/finite power/provider, one network cobblestone, one coal in a vanilla furnace, a processing pattern using P1, native planning/submission and stone returned through source. No stone is preloaded and no furnace processing is replaced.
- The fixture requests the production editor after waiting for chunk synchronisation. This is only an open request; inspect the screen and logs separately.

Read each final report's status, failure and scope. A crash after a passing report remains a runtime failure. Codec round trips are not a full process restart. These original two reports alone do not establish the expanded acceptance surfaces below. Read each separate report and final gate; a finished fixture is not necessarily a passing fixture.

## Full process restart fixture

Run with `ae2lf.probe.restart=prepare`. The native CPU's factory pattern waits 600 ticks after declaring P1 from source. At tick 80, the helper requires one unfinished job with physical source material, writes an ownership marker only in its new world, performs native world save, disconnects and exits normally. The report records the exact world ID and a restart_ready status.

After that process exits, run a new process with `ae2lf.probe.restart=resume` and `ae2lf.probe.world=<reported-world-id>`, using a new report directory. Only a UUID-named world with the helper's ownership marker is accepted. This path does not rebuild the fixture or insert materials. It verifies the restored job, exact source material, P1 mapping and remaining delay before waiting for the original native CPU order to finish. A complete pass requires the final report, normal shutdown evidence and process exit, not merely restart_ready.

## Blocking and recursive dispatch

Add `-Dae2lf.probe.blocking=true` in a fresh world. First exercise the production provider against a real barrel and network: pre-existing ingredients do not block the current batch; byproducts, unrelated network stock and partial primary returns do not release admission. Round-trip the production continuation codec with partially paid primary debt. Full primary return still leaves admission blocked while the code tail runs. Only complete code and physical resource settlement permit the next single recipe. O1 and O2 select the native primary and byproduct respectively. Then a real CPU requests two stones from two cobblestones, recursively feeds a vanilla furnace, and must dispatch only one recipe before the first primary return. Coal is supplied at tick 80, and both native batches must complete. No furnace processing is emulated. These checks supersede the older target-inventory admission fixture.

After acceptance, the helper clears a small gallery only in its own test world, places the production provider/terminal/cable with south-facing states, stabilizes the player camera, and captures the actual render target through Minecraft's Screenshot API. It writes factory-assets.png and then performs normal integrated-server and process shutdown. This is a real framebuffer image, not a drawn mock-up.


## Invisible full audit

Build the test-only agent using `build-background-agent.ps1`, rebuild each `factoryProbeJar`, then run `verify-background.ps1 -Generation <generation> -EvidenceName <unique-name>`. The launcher copies only the matching local AppliedFlux/Glodium dependencies into the development instance. The agent suppresses GLFW window visibility and focus at creation, including the early loading window, and never changes production JARs. Each client tick asserts invisible state. Test helpers and optional dependencies must be removed from run/mods after completion.

`BackgroundAudit` opens production menus, sends client menu save/encode actions, enumerates native registry items and placed block models, and captures the real framebuffer. ModelScreen is explicitly a test model view, not a product UI. Twelve directional images show the supplier's six facing states from opposing corners. Preparation screenshots and fixture failures remain separately labelled. `CompatibilityAudit` exercises loaded key types through native cell storage and the production VM/routing, then real provider source/subnet/main-grid paths. Missing gameplay and uninstalled addon capabilities are gaps, never inferred passes. Inventory and block hosts must synchronize to the client before opening their menus.


## Encoder, panel and language audit

The extended `BackgroundAudit` now tests the registered encoder item and native multipart panel. It encodes a physical factory recipe, switches pages, sends a save/upload action, captures the explicit provider chooser and verifies the slot moves. The handheld host is serialized through the production item codec after saving. Validated selection and bulk-tag packets are sent through the real client/server connection, and the native world renderer captures the resulting block outlines. These are programmatic actions, not a claim that physical mouse/keyboard input was replayed.

The same audit now opens AE2's unmodified pattern-encoding terminal, clears its slots, puts a factory pattern in the player inventory and sends the real client `QUICK_MOVE` container action. The row passes only when the server-backed menu receives the factory pattern in the native terminal slot. A separate legacy-state row writes `facing=up` directly, forces block-entity creation and removes it, covering worlds saved before the terminal became horizontal-only. New placements are forced horizontal while the six-direction property remains present for save compatibility.

`-UiOnly` removes model, block, world-stress and native-flow fixtures; it does not run those heavy scenes. The focused panel runner also captures the registered panel item model through the native item renderer, the blue/red dyed multipart world views and the two AE2 status models.

The focused editor rows also press the configured inventory key (`E` by default) while the code page is open and require the screen to remain open. The panel rows require `EncodingMode.PROCESSING`, verify that crafting/smithing/stonecutting slots and widgets are off-screen, and require the code-page control to be a 22×22 horizontal `TabButton` with the native crafting icon/tooltip. The masks preserve AE2's native 100-pixel face union on the first rendered layer and overlay the 32-pixel Recipe Ring core on the last rendered layer. The item model is the native pattern-terminal `display_base` structure with only `front_bright` / `front_medium` / `front_dark` substituted; 1.21.1 explicitly supplies opaque fluix tint values, and the focused runner captures the unmodified AE2 terminal item alongside it for direct comparison.

The editor rows now also exercise Tab / Shift+Tab indentation, indentation-preserving Enter, token-colored syntax rendering, and the intentional absence of a predictive completion popup. The encoder rows verify that every tag of each visible machine reaches `MachineTags`, that single-machine removal removes only the selected binding, that Ctrl+Shift removal clears the selected tag from all directly connected same-type machines, and that switching away from the held encoder clears all projected HUD labels.

Language checks switch the real client between `en_us` and `zh_cn`, wait for resource reload and loading-overlay fade, then capture localized saved/error states. Multipart dye checks call the native cable-bus recoloring API and verify the part-host color. Existing CPU/blocking, compatibility and registry galleries still run. `report-editor.py` accepts only completed fresh runs with no failed steps, invisible windows and normal shutdown; it verifies production JAR isolation and language key parity.

Final evidence for this extension uses `localized-verified-<generation>` under the full-audit archive and is summarized under `archive/2026-09-08-factory-encoder-panel/`. The extension does not establish complete SFM scheduling, induction-card-specific behavior or duplicate-service/topology/destruction recovery.


## September 8 pressure and persistence extension

The final gate requires every independent report to pass, every UI row to pass with `windowVisible=0`, a normal shutdown marker and exit code zero. `CompatibilityAudit` records actual initial subnet stock: induction tests deliberately leave FE there, so later transfer assertions compare exact deltas. Intermediate runs that checked only fixture completion are retained as superseded evidence.

- `StressAudit.java`: eight powered native provider/subnet networks, 512 round trips each, exclusion and conservation assertions, 8192 production continuation codec restores over 1024 actual ticks. Timing measures the audit body only, not complete server tick cost.
- `TopologyAudit.java`: physical duplicate disconnection and deterministic recovery for factory, recipe-ring and optimizer services, including subnet provider priority.
- `TerminalAudit.java`: recursive failure recovery, repeated pulses, SFM timers and held-signal edge semantics, actual terminal/provider destruction with exact item and pattern drops.
- `InductionAudit.java`: optional native AppliedFlux card inventory, bounded 1,000,000 FE source cache, source declaration, restored routing, removal return and 64 card changes.
- `RestartExtras.java`: extra fixtures attached to the true prepare/resume pair. Reads terminal wait, SFM elapsed/held signal state and induction cache from disk in a second JVM; requires no repeated output and conserved FE.
- `BackgroundAudit.java`: maximum 65,536-character Chinese/emoji code goes through client action chunks, production item components and binary NBT; native Chinese SFM error capture supplements ordinary bilingual UI states.
- `report-stress.py`: strict aggregate report and searchable raw-frame gallery. It checks all independent results and production JAR boundaries and records coverage limits.

Use `verify-background.ps1 -Generation <generation> -EvidenceName <new-directory> -SfmCpu` for real AE2 CPU execution of an SFM recipe. The production VM is shared by custom and supported SFM syntax. Advanced RETAIN/EACH/WITH/WITHOUT, slot/round-robin/relative-side clauses remain explicitly unsupported. Loaded item/fluid/AppliedFlux FE success does not establish arbitrary uninstalled addons. Full chunk unload/rejoin and non-item destruction recovery remain coverage gaps.


Current continuation contract: ordinary zero-transfer PUT skips; partial success advances. MUST quantities retain their remaining amount across codec and process restarts. `MultiTagAudit` uses seven real barrels, 128 group round trips, a 40+24 MUST split and an independent gold task while iron waits. `TerminalAudit` proves that a later admitted gold task runs while the first copper task waits. Each task holds its own VM state; the production terminal scheduler iterates every admitted task.

`RestartExtras` now creates three terminals with three real A-tag machine positions each, maximum Unicode drafts and 6000 synthetic saved positions per terminal. The third task checkpoints after 24 of MUST64, then opens capacity after real process restart and must complete only the remaining 40. Synthetic positions are serialization stress, not 6000 actual machines.

Focused runner switches: `-GroupOnly` for the multi-barrel fixture, `-UiOnly` for editor/menu actions, and `-GuideOnly` for actual GuideME pages in en_us and zh_cn, including scrolling and recipe sections. GuideME is added only to the test helper compile classpath from the existing runtime dependency. `report-stress.py` requires all full, guide and two-process restart evidence; it must not synthesize a passing report from partial runs.

## Runnable guide examples and complex flow gate

`examples/catalog.json` is the shared source for eight code samples. Run `python tools/runtime-factory-probe/sync-guide-examples.py` from the root to update both languages in both native guide trees. `FactoryGuideExamplesTest` compiles every sample in its declared recipe mode, rejects missing recipe context, and checks exact fenced code and MDX-safe markers. The catalogue is embedded only in the helper JAR.

`ComplexFlowAudit.java` in each generation reads that catalogue into real terminal/provider patterns. It exercises six terminal networks, paired Input barrels, late replenishment, forward functions, must continuation before first PUT, independent blocked/ready jobs and 32 real redstone admissions. Four actual AE2 CPU orders process twelve batches through fueled furnaces: blocking/nonblocking two-stage smooth stone, dual input/output with O2 before O1, and an SFM recipe. The full runner requires its `complex-flow-report.json` as well as every existing independent report.

The guide-only runner inspects the displayed compiled document and captures 60 scroll frames per language. Modern guide checks additionally walk laid-out paragraphs and text-run bounds, and cap the five-line must example's height. Original screenshots remain essential: neither a valid page identifier nor parsed text proves usable layout. `report-flows.py` refuses to produce final reports unless full scenes, final guide runs, both real-process restart pairs, unit suites and production isolation checks pass. Historical rejected attempts remain in the archive.


### MEK native capability and shared-subnet parallel counters (1.21.1)

`verify-background.ps1 -Generation 1.21.1 -EvidenceName <fresh-name> -MekOnly` loads the pinned test-only Mekanism JAR and runs `NativeCapabilityAudit` before `MekanismBulkAudit`. It does not copy AppliedFlux/Glodium in this mode; the native fixture rejects extra AE storage addons. Chemical and FE movements use native BlockCapabilities. Three real CPUs then submit iron, gold and copper processing orders in the same server tick, sharing one nonblocking provider, one subnet and two four-machine tags. Each order has 96 one-raw-block/12-ingot invocations. Identity-based per-order counters sample every tick and record a timeline every100ticks; five-tick code tails make every admission observable. Expected totals:288 admitted,288 completed,zero active,3456 ingots,shared peak16. Machine use is reported rather than assumed to be evenly distributed. This is an execution fixture, not an injected completion-counter mock.

`report-mekanism.py` requires the dedicated native run, full regression, independent JVM restart, current JAR identity and isolation checks before writing a passing report. `MultiTagAudit` in each generation also checks an ordinary fully rejected route finishes immediately without extracting resources while another must job remains pending. Earlier campaign logs remain immutable evidence; progress entries do not imply acceptance.


### Transactional native capabilities and cross-generation acceptance (26.1.2)

`NativeTransactionalAudit` registers test-only persistent world capability endpoints: a custom RegisteredResource handler and EnergyHandler. It checks atomic rollback on changed acceptance, strict explicit faces, unsided read-only fallback, exclusion, native HAS, independent partial MUST and 32 alternating transfers with codec restores. This is a native interface contract fixture, not a Mekanism runtime. `ParallelOrderAudit` uses real vanilla blast furnaces and three native AE CPUs sharing one nonblocking provider: twelve iron, gold and copper invocations each, a shared peak of sixteen and exact per-order accounting.

The full modern runner requires both reports. `-NativeOnly` repeats these two fixtures and the real AppliedFlux compatibility registry check, producing three focused world captures. All runners stamp the production and helper SHA256; the production artifact must stay unchanged throughout the run. Both generations require a separate-JVM prepare/resume pair. `report-mekanism.py --native <old-native> --regression <old-full> --modern <modern-full> --modern-native <modern-focused> --restart <old-prefix> --modern-restart <modern-prefix>` accepts only complete evidence matching the final production artifacts.


### Native transport performance validation

`MultiTagAudit` compares `FactoryServer.members` against the original single-position membership checks in a real network before/after machine removal and replacement. At completion, it measures both algorithms with alternating order, four warmups and twelve samples over seven real barrels plus512 stale saved positions. `membershipOriginalMedianMicros` and `membershipBatchMedianMicros` are local diagnostics, not whole-world TPS claims. The common full stress fixture remains4096actual round trips with8192codec restores. Performance acceptance also reruns dedicated native MEK and modern transaction/parallel fixtures; `report-mekanism.py` preserves both full-run baseline and optimized timings. The modern focused argument is optional when its full run already captures the correct native world cameras.
