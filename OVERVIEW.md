# AE2-LoopFactory (AE2LF) Project Overview

## 2026-09-12 editor scroll coordinate correction

Both `FactoryEditorHighlightMixin.java` adapters now draw in the native parent's content coordinates and inherit its viewport scissor. Previously they subtracted scroll twice and established another clip inside an already translated pose, producing blank space below prematurely hidden text. Visibility checks retain the exact native scroll value; the caret uses the native cursor-line index.

`tools/runtime-factory-probe/<generation>/.../EditorScrollAudit.java` adds ten native scenarios for top/down/end/up movement, fractional wheel scrolling, click positioning, selection, wrapped content, shorter text and empty text. `archive/2026-09-12-editor-scroll/` preserves the old JARs, native parent-source excerpts, before/fix source snapshots, the identical pre/post render checks, screenshots and deployment evidence. The old production JARs reproduce seven failed scroll scenarios in each generation.

Both corrected native runs pass all 20 scroll scenarios, the existing 22 syntax screenshot checks and channel/MUST/function logistics regressions. Full build/test gates pass with 262/260 tests. The accepted JARs (`3d5ae037...` / `64dabb18...`) are installed in both PCL instances with previous JAR backups; 665/905 save files and all other existing instance files retain bytes and timestamps. All ten original development run/mods files were restored. [Final report](archive/2026-09-12-editor-scroll/REPORT.md).

## 2026-09-13 README current-capability rewrite

`README.md` now presents the current AE2LF feature set in complete English followed by complete Simplified Chinese. It identifies 0.0.5 as the public release and covers Loop Factory execution refresh, redstone edges, lexical channels, comments/highlighting, recipe previews, ID insertion, unloaded chunks, crafting services, Loop Storage, and compatibility limits. Its bulk-logistics section describes compressed batches, checked signed-64-bit accounting, independent job backpressure/recovery, and the bounded 4,096-round / 2,304-batch regression evidence without making universal throughput claims. The documentation-only change is recorded in [archive/2026-09-13-readme-rewrite/REPORT.md](archive/2026-09-13-readme-rewrite/REPORT.md).

## 2026-09-12 complete editor highlighting and slash comments

`shared/.../factory/FactorySyntaxHighlighter.java` lexes whole source into UTF-16 spans, including both supported languages, Unicode names, resource tags, logical/comparison operators, aggregate and indexed recipe references, strings, and comments. Its range intersection preserves token context across native soft-wrapped visual lines. Both adapters' `FactoryEditorHighlightMixin.java` cache spans only after a text change and restrict the render override to `FactoryEditorScreen`.

`FactorySourceComments.java` masks unquoted `//` comments without changing offsets or line numbers. `FactoryCompiler.java` uses the masked text for indentation parsing; `SfmSyntax.java` uses it for dialect recognition and tokenization. Original program source remains editable and persisted. `FactoryCompletion.java` discovers Unicode declarations and ignores trailing slash comments. `FactorySyntaxHighlighterTest.java` adds 11 classification/wrapping/maximum-source tests; `FactorySlashCommentTest.java` adds 8 compiler, quotation, line-number and coloring regressions. Both full suites pass (262/260).

`tools/runtime-factory-probe/<generation>/.../SyntaxHighlightAudit.java` supplies native widget fixtures, verifies render-cache refresh and captures 11 unmodified framebuffer pages per generation. `ChannelFunctionAudit.java` exercises commented function calls and SFM transfers with real containers. `docs/loop-factory-highlighting.md` contains bilingual syntax and comment examples. `archive/2026-09-12-complete-highlighting/` retains builds, focused test XML, frozen accepted JARs, artifact verification, native and pixel-check scripts, runtime-mod backups and exact-hash PCL deployment tooling.

Final native runs pass on both generations, including all 22 original screenshot checks and the added SFM comment transfer ending at A/B/Dst = 0/0/15. Both PCL instances now contain the accepted JARs (`91db1f1f...` / `937e6830...`); all 1,570 save files and other instance bytes/timestamps remain unchanged. Ten original development run/mods files were restored after normal client exit. See [the dated report](archive/2026-09-12-complete-highlighting/REPORT.md) for exact hashes, scopes and evidence locations.

## 2026-09-12 channel highlighting and function isolation

`FactoryCompiler.RESERVED` now includes `channel`, so the native editor's shared highlighter classifies it correctly and tag/function names cannot shadow it. CALL instructions retain their lexical channel in their previously unused target field. `FactoryMachine.effectiveChannel` derives inherited function routing from the existing return-address stack; no instruction indexes or persisted snapshot fields change. Both adapters' `FactoryJob.transfer(GET, ..., channel)` also retain the requested channel.

`FactoryChannelIsolationTest.java` adds six regressions that failed on the previous code and pass after the correction. Native `ChannelFunctionAudit.java` in each isolated helper verifies real container balances, nested function MUST resume, the direct channel-aware GET API and independent jobs. The legacy helper now includes the channel scene in its native tick lifecycle; screenshot capture waits for the editor to render. Final targeted native runs and screenshot checks pass on both generations, alongside 243/241 full unit tests. `docs/loop-factory-channels.md` defines the routing and shared-inventory boundaries. `archive/2026-09-12-channel-isolation/` retains red results, failed helper attempts, explicit final native verification, production-byte audits, runtime-mod restoration, and the PCL deployment/backup evidence.

## 2026-09-12 PCL test deployment

Following the completed native campaign, the user requested local installation for manual testing. Both accepted 0.0.5 JARs are now installed in the existing `AE2-lightoptimizer-1.21.1` and `AE2-lightoptimizer-26.1.2` PCL instances. Only each production JAR changed; all 1,570 save files and every other existing file retain their bytes and timestamps. No client was launched as part of deployment.

`archive/2026-09-12-pcl-final-deployment/` contains `deploy.py` (exact artifact and target guards, previous-JAR backups, complete before/after inventories), `deployment.json` (installed hashes and preservation counts), `<generation>-before.json` / `-after.json` (every instance file), `backups/<generation>/` (the two previous production JARs), and `REPORT.md` (deployment scope and manual test handoff). This follow-up supersedes the earlier campaign's no-install status; its original completion evidence remains immutable.

## 2026-09-12 expanded logistics and native pattern preview

The current development update repairs channel-local unquantified MUST accounting, old inflated pending-count migration, native recipe target previews independent of factory-code validity, resource-only machine removal recovery and native chunk tick eligibility. It adds GUI-carried item/container ID insertion plus optional JEI/legacy EMI dragging. Final acceptance is complete for the frozen current JARs: 179/182 full native scenes, five input profiles (127 cases / 266 frames), separate-JVM restart, legacy Mekanism 2,304 batches, and all 18 final7 Ripper chains. Strict collectors pass 77 factory checks, 5/5 input profiles and 18/18 Ripper runs; unit tests pass 237/235. All ten original run/mods files are restored exactly. Scope, immutable failures and evidence live in `archive/2026-09-12-expanded-background-qa/REPORT.md`; this does not publish or install a new release.

New and changed source/tool responsibilities:

- Both adapters' `client/FactoryEditorScreen.java`: GUI-carried-only left/right ID insertion, exact native caret/selection replacement, atomic 4096-selector/65536-source limits, and shared viewer drop bounds.
- Both adapters' `client/ContainerResourceIds.java`: read-only copies queried through native fluid/energy/item capabilities and AE cell inventories; deduplicated actual content IDs, canonical FE selector, and guarded legacy chemical lookup.
- Both adapters' `client/FactoryEditorJeiPlugin.java` and legacy `client/FactoryEditorEmiPlugin.java`: independent optional public viewer plugins for typed item/fluid dragging into the code area. Viewer APIs are compile-only and never bundled.
- `tools/runtime-factory-probe/<generation>/.../{ItemIdInsertionAudit,NativeIdInput,ViewerIdInput,IdInsertionJeiObserver}.java`: actual native menu/inventory synchronization, synthetic native GUI callbacks, real viewer sidebar drags, client/server resource snapshots, and framebuffer captures.
- `tools/runtime-factory-probe/verify-id-insertion-background.ps1` and `verify-id-insertion.py`: exact viewer isolation/restoration and independently reconstructed per-case text/caret/resource/save/framebuffer gates.
- `tools/runtime-factory-probe/verify-background.ps1 -ValidateExisting`: reuse every normal postcondition against immutable completed native evidence, verify current artifacts, and write a separate revalidation manifest; launch failures remain archived separately.
- Both adapters' `FactoryServer.java`: tick factory hosts only when the existing native chunk holder is eligible to tick, without loading chunks or renewing tickets; this prevents waiting jobs from keeping their own FULL chunks loaded through save notifications.
- Shared `FactoryMachine.java`: pass the executing instruction's channel into required-transfer accounting.
- Both adapters' `FactoryJob.java`: channel-filtered outstanding MUST quotas and migration of persisted inflated pending remainders without replay.
- Shared `FactoryChannelMustTest.java`: six compiler/VM/routing and migration behavioral regressions.
- Both adapters' `FactoryPatternItem.java`: delegate output preview and native tooltip to the embedded ordinary AE encoded pattern, with a non-nested native recipe boundary.
- Both adapters' `FactoryRecovery.java`: versioned resource-only machine cargo, segmented generic-resource quantities, pending opaque native recovery records, and accepted-amount accounting during ME reinsertion.
- Both adapters' `FactoryBlock.java`, `FactoryBlockEntity.java`, `FactoryProviderLogic.java`: native removal/placement lifecycle, exactly-once physical buffer ownership, memory-card copy exclusion, separately saved return queue and native pattern/upgrade drops.
- Legacy `mixin/MinecraftShutdownMixin.java` and its common Mixin registration: intercept only `MinecraftServer.stopServer`'s unique native chunk tick call, apply a one-millisecond unload budget and attempt up to 32 native task polls. Normal gameplay, original completion conditions, queued work and final saving remain native. Three retained shutdown failures precede a successful native 3,000-core run with the transformed server method independently observed.
- `tools/runtime-factory-probe/<generation>/.../HugeQuantityAudit.java`: 21 real item/water/FE transfers, signed-long boundaries, missing-stock and saturated-destination continuation, 33 job codecs and 108 binary cell NBT round trips.
- `tools/runtime-factory-probe/<generation>/.../RecoveryAudit.java`: actual world/wrench removal and native item placement, resource conservation above `Long.MAX_VALUE` in separate segments, MUST cancellation, induction resources and 64 maximum-length queued programs.
- `tools/runtime-factory-probe/<generation>/.../ChunkLifecycleAudit.java`: real remote/whole-owner/partial-cargo unload and reload, native ticket-level diagnostics, new block-entity identity, persisted remainders and exact resource accounting. Its 33 chunks lie between x=2404 and x=2916, beyond earlier fixture ticket influence; native FULL inaccessibility alone never counts as completed unloading.
- `tools/runtime-factory-probe/<generation>/.../PatternPreviewAudit.java`, `.../mixin/PatternPreviewInputMixin.java`, `ae2lf_preview_probe.mixins.json`: isolated QA screens, scoped test modifier input, and native render/tooltip comparisons. None belong to production JARs.
- `tools/runtime-factory-probe/verify-preview-pixels.py`: compare regions of original normal/Shift/cleared framebuffer images without modifying source screenshots.
- `tools/runtime-factory-probe/verify-runtime-identity.py`: complete class/resource inventory and byte equality between the frozen production JAR and ModDev's actual main source set.
- `tools/runtime-factory-probe/verify-background.ps1`, `verify-restart.ps1`, `verify-focused-background.ps1`: requested load scale, exactly-once scene coverage, strict independent report/normal-exit gates and version-isolated optional machine fixtures.
- `tools/runtime-ripper-probe/verify-background.ps1`: nine separate hidden native chain gates, exact tested production-version metadata, native CPU/fee evidence and restoration of the incoming mod manifest.
- Legacy Ripper `ShutdownReadiness.java`: read-only native holder/queue readiness snapshots before ticket removal, after ticket removal and after a real flush save. Readiness is useful diagnostics but did not itself repair the retained shutdown loop; the production fix is independently required by the legacy runtime gate.
- `archive/2026-09-12-expanded-background-qa/run-final-matrix.ps1`: generation-specific full/restart/legacy-MEK and nine-chain Ripper sequencing, immutable per-gate logs and immediate failure propagation.
- `archive/2026-09-12-expanded-background-qa/campaign/run-final-ui-matrix.ps1`: fresh final source/helper hash freeze, full3, separate-JVM finalui restart, legacy Mek final3, and nine final7 Ripper chains per generation.
- `archive/2026-09-12-expanded-background-qa/campaign/resume-modern-final-ui-matrix.ps1`: narrowly resume remaining modern gates only after the completed full3 evidence passes the full independent revalidation; preserve the original PowerShell Channel-variable failure and reject changed artifacts or existing future output.
- `archive/2026-09-12-expanded-background-qa/new-id-insertion/verify-final-id-input.py`, `campaign/verify-final-factory.py`, and `collect-final-ripper.py`: exact named-run input/factory/Ripper collectors with no automatic latest-run fallback; final reports retain raw failures and version/feature boundaries.
- `archive/2026-09-12-expanded-background-qa/restore-campaign-mods.ps1`: verifies native clients have exited, rejects unknown files and links, checks original backups and known changed helpers, archives those helpers and restores exact initial file names, bytes and hashes. `final-run-mods-restoration.json` records all ten files.
- The campaign archive also contains initial manifests, current artifact audits, bounded JFR data from the final full3 native processes, payload-limit analysis, independently reviewed screenshots, root-cause notes for failed fixtures, and machine-readable run inventories. `completion.json` binds the final strict reports to the two production JAR hashes.

English name: AE2-LoopFactory (AE2LF).
中文名称：应用能源 2 循环工厂（AE2LF）。

Current release: **[0.0.5](https://github.com/positer/AE2-LoopFactory/releases/tag/v0.0.5)**.

## 2026-09-12 complete recipe sets P and O

- `shared/src/main/java/com/example/ae2lightoptimizer/factory/FactorySelector.java`: new reserved operands `P` (every material the invocation allocated) and `O` (every product the recipe declares). A new `RecipeSet` resolver carries them; `validateParameters` rejects them without a recipe, and `membersMayOverlap` stays conservative.
- `versions/neoforge-*/.../factory/FactoryResourceSelector.java` and `FactoryJob.java`: the provider job resolves `P` from its allocated `parameters` and `O` from the decoded recipe outputs, caches both sets per job, and feeds them to every GET, PUT, HAS, exclusion, `must` and native-capability selector. `resourceType` and `matchesKey` are shared so a set lookup compares canonical identity.
- `FactoryCompiler` and `FactoryTags`: `P` and `O` are reserved words; `func P`, `import O` and `name O` are rejected, and `[PO][0-9]*` cannot be a tag or function name.
- Guide trees (both languages, both generations) document the two new operand rows, the complete-set semantics, and the recipe-free error.
- Real in-game fixture `recipe-set6-1.21.1-20260912` / `recipe-set6-26.1.2-20260912`: a real provider pattern with a two-material recipe moved the whole input set in one tick (`ticksToMoveCompleteInputSet=1`, machine contents `[1,1]`), rejected the same code on a recipe-free pattern with `P and O require a recipe-bound pattern`, and returned exactly one product through `while F has O < 1` / `get O` / `put O into source` with no machine residue. Unit gate: `FactoryRecipeSetTest` plus the existing suite, 231 tests on 1.21.1.
- `tools/runtime-factory-probe/*/ComplexFlowAudit.java`: the complex-flow fixture now ends with twelve blocking and twelve parallel dispatch rounds that share one provider, each writing its own ledger row (ticks, primary delta, peak provider jobs, blocking-gate hold ticks, codec restores, duplicate and lost returns) and requiring exact output, exact input consumption and zero provider/CPU/furnace residue at the end.

## 2026-09-12 full retest, soul capability and shutdown repair

- Fresh hidden-window full audits on the current 0.0.5 sources: 1.21.1 `full-final-1.21.1-20260912` 172 rows / 0 failed / 0 visible windows / normal shutdown, 26.1.2 `full-final-26.1.2-20260912` 174 rows / 0 failed / 0 visible windows / normal shutdown. Unit gates: 1.21.1 225 tests, 26.1.2 223 tests, zero failures. Per-item results: `archive/2026-09-12-full-retest/REPORT.md`.
- `versions/neoforge-1.21.1/src/main/java/com/example/ae2lightoptimizer/factory/FactoryNativeTransfers.java`: adds a reflective Industrial Foregoing Souls port (`industrialforegoingsouls::soul`) guarded by mod presence, plus `soulCapabilityPresent()`. Souls stay a NeoForge block capability, so they move machine-to-machine; the provider AE cache still holds AE keys only.
- `tools/runtime-factory-probe/1.21.1/.../ClientBootstrap.java`: the exit path halts the integrated server directly and waits across normal ticks instead of calling `Minecraft.disconnect`, which stopped firing client ticks and hung the 1.21.1 harness after every full audit since the channel build. A ten-minute watchdog records `shutdown-timeout.txt` rather than hanging. All three 1.21.1 shutdowns after the change are normal.
- `tools/runtime-factory-probe/*/CompatibilityAudit.java`: enumerates `AEKeyTypes.getAll()` and samples every type it can construct, samples registered item/fluid tags per loaded mod namespace, and runs a real tag-selector transfer. Unknown key types are reported as unsampled.
- Addon fixture `addon-compat10-1.21.1-20260912` (Ars Nouveau + Ars Énergistique, Industrial Foregoing + Souls, Patchouli, Curios, GeckoLib): FE, `ars_nouveau:source`, items, fluids and 102 mod-tag members all pass the cell, VM, provider subnet cache and main-network return path; a real code round moves 128 souls through the capability system.
- Mana remains blocked upstream: appbot 1.6.0-alpha.3 throws `NullPointerException: capability` in AE2's `RegisterPartCapabilitiesEvent` and aborts the mod load (`addon-compat3-1.21.1-20260912`). The in-game `channel` fixture still reports `missing-owner` on 26.1.2 while unit and editor coverage stay green; both are open items.
- `tools/runtime-factory-probe/verify-background.ps1`: new `-AddonCompat`, `-Channel` and `-ChannelOnly` switches with staged addon dependencies and bounded-shutdown acceptance.

## 2026-09-09 render and pattern-terminal repair

- `versions/neoforge-*/src/main/java/com/example/ae2lightoptimizer/factory/FactoryBlock.java`: terminal orientation now keeps AE2's six-direction property for legacy saves, forces new terminal placements horizontal, and leaves legacy vertical states non-inverted. Provider and cable behavior are unchanged.
- `versions/neoforge-*/src/main/resources/assets/ae2lightoptimizer/models/part/loop_factory_panel_{off,on}.json`: the panel preserves AE2's 100-pixel native mask union as the colored face footprint and overlays the 32-pixel Recipe Ring Solver core on the last rendered layer with alpha 96/160/255.
- `versions/neoforge-*/src/main/resources/assets/ae2lightoptimizer/models/item/loop_factory_pattern_encoding_panel.json`: the item model is exactly AE2's native pattern-terminal `display_base` structure and substitutes only `front_bright` / `front_medium` / `front_dark` with the three core masks. The 26.1.2 descriptor keeps the native five `fluix` tints; custom item-base, empty, and GUI-light overrides are absent.
- `versions/neoforge-*/src/main/java/com/example/ae2lightoptimizer/client/FactoryEditorScreen.java`: consumes the configured inventory key while editing; Escape remains the only close key.
- `versions/neoforge-*/src/main/java/com/example/ae2lightoptimizer/factory/FactoryPanelRecipeMenu.java` plus the custom screen JSON: forces processing mode, removes the crafting/smithing/stonecutting pages from view and places a native 22×22 horizontal `TabButton` at the original crafting tab position.
- `tools/generate_factory_panel_masks.py`: extracts only the Recipe Ring Solver core shape, emits three transparent tint masks, writes the native panel/item models and 26.1.2 item descriptor, validates dimensions/alpha/counts, updates both editable Blockbench projects, and writes `archive/2026-09-09-render-fix/panel-mask-report.json`.
- `tools/blockbench/ae2lf_assets.js`: Blockbench exporter now embeds/exports only the three panel masks and writes the native panel/item models plus the 26.1.2 part and item descriptors.
- `tools/runtime-factory-probe/*/BackgroundAudit.java`: adds a real client `QUICK_MOVE` insertion row for the unmodified AE2 pattern terminal and a legacy `facing=up` block-entity row. `-UiOnly` now excludes the heavy model/stress/native-flow scenes; `-PanelVisualOnly` also captures the panel item model.
- `tools/runtime-factory-probe/*/ClientBootstrap.java`: shutdown now waits across ticks for the integrated server to stop instead of throwing on the first asynchronous disconnect.
- `tools/provision_pcl_instances.ps1`: accepts existing `.bak` backups and compares only active `.jar` files during the managed whitelist check.
- `shared/.../factory/FactoryCompletion.java` and `FactorySyntaxHighlighter.java`: shared Tab / Shift+Tab indentation, indentation-preserving newline, and token classification for the editor. Predictive completion is intentionally absent.

`shared/.../factory/FactorySelector.java` parses a selector into a left-to-right expression tree over operands. `&` merges, `!` subtracts, parentheses regroup, `*` and `?` are wildcards, and `#namespace:path` resolves through a generation-supplied `TagLookup` that reads the item tag registry. The same tree serves GET, PUT, HAS, must quantities and exclusion lists, keeps `Pn`/`On` recipe validation, and is highlighted as operators. Evidence: `archive/2026-09-11-ampersand-union/REPORT.md`.

Both `loop_factory.md` guide trees now document the language as tables: selector operands, logical operators, statements, recipe references, and a separate boolean/comparison operator section in the control-flow chapter. Guide-only real-client runs capture 60 frames per language per generation, and the 26.1.2 runner walks the laid-out document to reject text outside the 404 px viewport (164 paragraphs, 622 `en_us` / 582 `zh_cn` text runs, one mandatory example paragraph).

`FactorySelector.TagLookup` is resolved per key type by `FactoryResourceSelector.RESOURCE_TAGS`, so `#tag` operands follow the item and fluid tag registries and pick up mod/datapack tags live. `FactoryExpression` accepts `has resource in Tag` and a bare `has resource`, `FactoryTags.expression` splits `A&B` machine groups for `get`/`put`/`redstone`/`has`, and `FactoryCompiler` patches `break` to the instruction after the innermost loop. Unit tests: 221 (1.21.1) and 219 (26.1.2).
- `versions/neoforge-*/.../mixin/FactoryEditorHighlightMixin.java`: renders the highlighted editor text inside the native `MultiLineEditBox` text pass; `MultiLineEditBoxAccess` exposes cursor/text-field operations without duplicating the editor.
- `versions/neoforge-*/.../client/FactoryEncoderClient.java` and `FactoryEncoderView`: project every visible machine's tag list to the HUD for through-block labels, with Shift+right-click single removal and Ctrl+Shift+right-click connected same-type removal.

Actual verification is recorded in `archive/2026-09-09-render-fix/REPORT.md`: 98 focused UI/encoder rows per generation, full runs of 172 rows (1.21.1) and 174 rows (26.1.2), unit tests 210/208, syntax highlighting and through-block tag captures, and byte-identical PCL deployment of the two 0.0.5 JARs.

Factory control nodes now set no channel flags and zero idle power. Ownership election preserves an earlier redstone edge, and `FactoryJob`, provider dispatch, induction return and provider upload do not gate on `getMainNode().isActive()`; a cable + terminal network therefore operates without an AE energy source.

`FactoryServer.signal` reads redstone for a terminal across its whole factory grid, so a signal fed into the interface cable admits a round the same way a signal on the terminal body does; other factory hosts keep their previous block-level semantics and provider dispatch is unchanged. `FactoryBlockEntity.editingFactory()` returns the terminal itself, so saving code binds the pattern without depending on the ownership election. Evidence: `archive/2026-09-11-minimal-redstone/REPORT.md`.

A player-world investigation (`archive/2026-09-11-minimal-redstone/USER-WORLD-FINDING.md`) proved the terminal button path works and isolated the remaining "nothing happens" case to selectors: `minecraft:item` is a registry ID that matches nothing, while `minecraft::item` is the item resource type. Both language guides now list that check under troubleshooting.

## Goal and status

Provide a clean, dual-generation workspace for `AE2-LoopFactory`, an AE2 addon whose universal Loop Storage cells share capacity across every dynamically registered AE2 key type and whose network blocks solve crafting cycles, accelerate eligible global crafting calculations and execute validated crafting chains. The workspace is isolated from ImmortalStorage and hard-separates incompatible Minecraft generations.

The canonical upstream is the standalone GitHub repository `https://github.com/positer/AE2-LoopFactory`. Its Git root, history, branches, tags, and remote are independent from ImmortalStorage; no repository nesting, subtree, submodule, or shared worktree is used.

Release 0.0.4 adds the Crafting Ripper and Loop Card in both maintained generations. The complete stationary and portable storage families, dynamic storage-key compatibility, recipes and existing solver services are retained. Minecraft 1.20.1 is intentionally not maintained.

Optional compatibility data is additive: a NeoForge `mod_loaded(create)` conditional Create Milling recipe and a `mod_loaded(mekanism)` conditional Mekanism Crushing recipe both use the common `c:gems/loop_crystal` item tag. Each is ignored independently when its platform is absent; neither external mod is a dependency or class reference.

Loop Storage Cells have only AE2 as a mod prerequisite. Version adapters enumerate `AEKeyTypes.getAll()` and use each key type's native `getAmountPerByte()`; optional storage addons are therefore compatibility inputs, never compile-time or metadata dependencies.

## Runtime flow

### Crafting Ripper and Loop Card

1. `CraftingRipperBlockEntity` reuses AE2's provider host contract and exposes a channel-requiring managed grid node with 5 AE/t drain.
2. `CraftingRipperLogic` supplies 36 physical pattern slots, validates crafting/smithing/stonecutting patterns and prevents inserting into locked slots through either local menus or the pattern access terminal. Its pattern list is refreshed on card, inventory and recipe changes.
3. `CraftingRipperMenu` and `CraftingRipperScreen` subclass the pinned AE2 provider implementations. The compact 176×210 interface contains only four pattern rows in its main area, with native player inventory and upgrade/control panels. Native return slots are disabled on both menu sides and hidden from the screen. A native upgrade slot holds one Loop Card; synchronized card state drives the grey slot overlay.
4. `CraftingRipperPatterns` validates real recipe identity, selected input matching, assembled results and remainders. Automatic mode enumerates encodable concrete recipes; dynamic recipes without an enumerable representative are not advertised as fabricated static outputs.
   `CraftingRipperCatalog` resolves real recipe inputs, native display/placement data and observed network component keys, with per-recipe coverage diagnostics. The server recipe manager and reload events control namespace-independent recipe additions, replacements, removals and tag changes. `CatalogCraftingPattern` preserves and validates all nine component candidate lists in the pinned 26.1.2 adapter; `CatalogSmithingPattern` provides a validated persistent definition for genuinely absent optional smithing slots.
5. `CraftingRipperExecutor` preflights the entire selected CPU job before initial stock extraction, then validates again against private CPU stock before its single 50 AE commit. `InstantCraftingBatch` performs checked, compressed resource transitions; ring schedules and seed reserves retain their existing ownership.
6. Completed products remain in the CPU for delivery and native finalization. Requester backpressure must never repeat material consumption or the 50 AE charge; reload/cancellation must preserve stock and completion state.
   `RipperNativeCrafting` handles map expansion and AE2 quartz-cutting recipes requiring native random durability handling one operation per tick. Its CPU-job-local state persists real-output identity credits, remaining operations and the single 50 AE payment for the committed job. It never changes global key equality or writes a planned placeholder as a physical result.
   `RipperMapSerialization` preserves vanilla's network-only map postprocessing marker beside normal native codecs for task definitions, final targets and private identity credits. Logical output credits can satisfy only the exact declared subsequent input, preventing fuzzy matching from consuming already completed maps. Quartz-cutting tool classification examines selected component keys before reading random remainders.
7. Portable `inventoryTick` invokes `PortableLoopEnergy` to convert stored FE into AE charge only with a Loop Card, using AE2's conversion and native charge limits.
8. `PortableLoopEnergy` registers version-local item energy capabilities: 1.21.1 uses the saturating legacy integer API, 26.1.2 uses transactional transfers and native long amount queries. Stored FE is available as a capacitor only while both FE and AE charge are positive.
9. Portable inventory operations compare the current `STORAGE_CELL_INV` component to their cached snapshot and refresh after external FE changes, preventing stale terminal contents from restoring consumed energy.

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
- `tools/runtime-ripper-probe/`: separate, test-only real-client acceptance harness; none of its classes or metadata enter production JARs.
  - `launch-pcl-probe.ps1`: launches the matching installed PCL client with a fixed offline test identity and a new uniquely named test world, recording installed hashes and launch arguments.
  - `install-built-probe.ps1`: backs up both installed addon JARs and verifies the replacement production/helper hashes.
  - `probe.init.gradle`: compiles and packages helper sources outside the production source sets.
  - `1.21.1/` and `26.1.2/`: independent helper adapters. `RuntimeRipperProbe` builds a real ME network and submits native crafting jobs; `Core256MFixture` checks the exact 3000-core recipe and stock balance; `ClientBootstrap` creates the new world and captures the native framebuffer; `ProbeState` coordinates screenshots. `PlannerDiagnostic` and observer mixins record the actual planner request/result, bridge decision, native CPU tick and executor call without changing them. Each adapter's resources register only the helper mod and observers.
    - `ExactComponentFixture`: two real 3000-item orders, missing precise input rejection, nested NBT values beyond double precision, and full component/material equality in manual and automatic modes.
    - `PackRecipeReloadFixture`: actual server data-pack reload, recipe addition/replacement/removal, changed tags and typed component predicates; validates the provider's naturally refreshed published patterns.
    - `NativeCatalogFixture`, `NativeContinuationAssertions`: three real map expansions, strict private-state round-trip and native CPU binary save/read after the first commit, followed by resumed completion with one total fee.
    - `ToolComponentFixture`, `ToolRemainderObserver`: ordinary versus actual Unbreaking tools, native remainder callback observations and exact returned-tool balances without replacing the random generator.
    - `CatalogAuditFixture`, `CatalogPatternPersistenceFixture` and `OptionalSmithingFixture`: live recipe witnesses, actual published candidates, card lifecycle and adapter-specific encoded pattern persistence and optional smithing slots.
  - `ReplayPlannerCapture.java`: replays the captured real recipe graph with only the supercomputing service enabled and checks the exact materials for an acyclic order; this checks planning, not native CPU execution.
  - `README.md`: reproducible launch commands, fixture boundaries, energy measurement and acceptance criteria.
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
- `block/CraftingRipperBlock.java`, `CraftingRipperBlockEntity.java`, `CraftingRipperLogic.java`: network machine, provider host, card/pattern persistence and recipe advertisement.
- `menu/CraftingRipperMenu.java`, `client/CraftingRipperScreen.java`: four-row native provider UI and synchronized grey locked-slot overlay.
- `integration/CraftingRipperPatterns.java`, `CraftingRipperExecutor.java`: recipe catalogue, live legality checks and private whole-chain CPU replay/commit.
- `integration/CraftingRipperCatalog.java`: native recipe witnesses, component candidates, namespace-independent automatic discovery and coverage diagnostics.
- `integration/CatalogCraftingPattern.java`, `CatalogSmithingPattern.java` (26.1.2): validated persistent catalog definitions for all nine component choice lists and empty optional smithing slots.
- `integration/RipperNativeCrafting.java`, `RipperMapSerialization.java`: original-CPU native continuation, physical output identities, one-time energy payment, strict logical-credit inputs and validated transient map-marker persistence.
- `storage/PortableLoopEnergy.java`: generation-specific FE capability and stored-FE-to-AE charging; the portable inventory refresh boundary remains in `LoopStorageCellInventory`.
- `mixin/CraftingTaskProgressAccessor.java`: required access to AE2 task counters for exact whole-chain completion and progress accounting.
- `assets/ae2/screens/ae2lightoptimizer_crafting_ripper.json`: unique native ScreenStyle layout extending the provider controls to 36 pattern slots.
- `item/ModItems.java`: service-block items, Loop Crystal materials, housing, ten cores, eleven cell items, and creative-tab placement without menus.
- `storage/LoopStorageCellItem.java`, `LoopStorageCellHandler.java`, `LoopStorageCellInventory.java`: version-native item tooltip, AE2 handler registration, dynamic-key inventory, persistence, nested-cell guard, and shared-capacity enforcement.
- `client/Ae2LightOptimizerClient.java`: `Dist.CLIENT`-isolated registration of eleven drive models; 1.21.1 also binds AE2 cell-state tinting and explicitly registers opaque fluix variants for the factory panel PartItem, while 26.1.2 uses its native item descriptor tint.
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
- `crafting_ripper`

Each version also packages the same root-level `ae2lightoptimizer.png`: a transparent 64x64 isometric three-face render of the connected ring terminal, inset within a fully closed square PNG viewport frame. The four-layer frame uses dark steel, metal-grey, cyan signal, and a dark inner edge; all four outer image edges are pixel-opaque while the interior retains transparency. `META-INF/neoforge.mods.toml` binds it as the NeoForge `logoFile`.

Each version contains 76 recipe JSON files: three machine recipes, the Loop Card recipe, eight Loop Crystal recipes, and 64 Loop Storage recipes/declarations. The Loop Storage set contains the original 32 stationary recipes, two shapeless portable recipes for each of ten finite tiers, one cell-based infinite portable recipe, and eleven native disassembly declarations. The infinite portable tier intentionally has no housing-plus-core recipe because no infinite core exists. The 1.21.1 adapter uses object-form ingredients and `ae2:chest`, while 26.1.2 uses string-form ingredients and `ae2:me_chest`; structured tests lock both layouts and ingredient IDs.

Each version carries 36 Loop Storage texture files and 33 item models: the original ten byte-identical user cores, housing and stationary cells, plus three portable housing palettes and eleven portable side layers. Finite portable side layers are copied byte-for-byte from the matching native AE2 tier; portable housings use the approved k/M/infinite shell LUTs, and only the infinite side uses the approved light-purple core LUT. Native portable LED and screen layers remain external AE2 references. Dimensions, alpha, silhouette, coordinates, and unmapped pixels are unchanged. Eleven stationary drive models remain byte-identical native AE2 models. Minecraft 26.1.2 additionally carries 33 generation-native `assets/.../items/` descriptors.

The two solver service blocks remain UI-free. The Crafting Ripper subclasses the pinned AE2 provider menu/screen and extends its layout to four pattern rows. Each adapter contributes the same GuideME tree to AE2's existing guide:

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

- `tools/audit_runtime_resources.py`: resolves addon model textures against both the local resource tree and the pinned AE2 JAR, avoiding false missing-texture reports for intentional dependency references.
- `tools/verify_crafting_ripper_runtime.ps1`: isolated full builds and data-runtime startup, followed by freshness-checked transformed-bytecode validation for chain preflight, execution, paid persistence and native provider locks.
- `tools/generate_crafting_ripper_resources.py`: generation-native machine and advanced-card recipes, models, blockstates, tags and names.
- `tools/generate_crafting_ripper_textures.ps1`: native machine-frame extension with online/offline nine-cell cores and acceleration-card shell preservation.
- `shared/src/main/.../crafting/InstantCraftingBatch.java`: API-free checked batch execution and maximum legal compressed repetition count.
- `shared/src/main/.../storage/PortableEnergyMath.java`: API-free immutable FE debit and conversion arithmetic used by both adapters.
- `shared/src/test/.../crafting/CraftingRipperResourceTest.java`: exact shaped/shapeless inputs, native-card shell/alpha preservation and two-state grid geometry.

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

Its contract requires `VersionArgumentIndieV2:True`, exactly six managed mod JARs (AE2, GuideME, JEI, AE2LF, Applied Flux, and Glodium), no ImmortalStorage artifact, no filesystem link, and no copied mutable directories. Applied Flux and Glodium exist only in the test-instance whitelist to verify third-party FE key discovery; they are not release dependencies. PCL creates future instance state under the matching target directory. Existing ImmortalStorage PCL instances remain separate and unchanged. Both instances now contain the matching 0.0.4 release artifact. After twelve passing runtime scenarios, the final installation audit removed the two temporary helpers and verified all 335 original non-production files unchanged.

## Isolation contract

Neither version references the ImmortalStorage workspace, packages, artifacts, generated sources, caches, or run directories. No compiled class is shared between generations. Only API-free source under `shared/` is compiled into both builds; compatibility work is duplicated explicitly in each adapter.

## Current 0.0.4 verification

Release 0.0.4 validation: both complete `build`; 1.21.1 passes 138 tests and 26.1.2 passes 136, with zero failures/errors/skips. Final JARs contain 116 and 122 AE2LF classes respectively and 76 recipes each, with no nested JAR, unrelated mod class or runtime-probe class. Both real installed PCL clients have been launched in new, isolated test worlds. A full automatic-catalog order for 3,000 256M cores passes with both the supercomputing interface and ring terminal online: 13 recipes, 188,681,000 applications, 38 compressed batches, exactly 769,888,153 required CPU bytes, one native CPU tick, one executor call and 50 AE total surcharge. Exact materials, preserved crystal seed, zero intermediate stock and empty reusable CPU all pass. The native UI shows four pattern rows and a single upgrade slot, with no return slots; optional JEI navigation uses the full sidebar bounds.

The real catalog is also captured and replayed with the ring terminal offline to verify acyclic supercomputing ownership and exact materials independently. This replay does not submit a native CPU job. Requester backpressure and third-party portable equipment are outside the gameplay fixture. The 1.21.1 client intermittently stalled in native chunk-unload futures after a completed pre-save; its thread dump and verified test-process stop are retained. The 26.1.2 acceptance saved and exited normally. The preceding acceptance campaign's reports and failed-first-attempt evidence remain under `archive/2026-09-06-runtime-ripper-acceptance/`; final release-artifact evidence is in the catalog campaign below.

The subsequent catalog and precise-component acceptance is archived under `archive/2026-09-06-loop-card-catalog/`. Both installed clients pass manual and Loop Card orders of 3000 exact book copies, rejecting wrong deep/partial custom data before material mutation; each completed order uses one native CPU call/tick and 50 AE. Both pass three real recipe/tag data-pack reloads, and ordinary versus actual Unbreaking tool execution with observed native remainder callbacks and exact inventory. Native map continuations produce three distinct scale-1 maps after a strict complete CPU binary write/read following the first operation, retaining one total 50 AE fee. This persistence test keeps the world live. Artifact and original-file auditing confirms 116/122 production classes, 76 recipes each and all 335 original non-production PCL files unchanged; temporary helpers are outside both the production JARs and the original-file baseline.

All twelve final-hash runtime scenarios pass, including the final 3000-core and finite-catalog replays. The completed final installation audit removed exactly the two temporary helper JARs after every accepted client saved and exited. Each instance now contains six production/dependency JARs with installed/build hashes equal, and the same 335 original non-production files unchanged. Test worlds and both failed early-window catalog launch attempts remain archived; the successful 1.21.1 final directory is `catalog-verified-19-retry2`.

See the [build isolation audit](archive/2026-09-06-loop-card-catalog/precise-final-build-audit.md), [completed installation audit](archive/2026-09-06-loop-card-catalog/final-install-audit.md) and [runtime acceptance summary](archive/2026-09-06-loop-card-catalog/SUMMARY.md) for artifact hashes, individual scenarios and verification boundaries.

## Historical 0.0.3 verification baseline

The following measurements are the independent 0.0.3 release snapshot. Its test counts, artifact hashes and installation statements describe that older release; the current 0.0.4 results and installed state are recorded above.

- NeoForge 1.21.1 passes 102 tests and NeoForge 26.1.2 passes 101 tests, with zero failures, errors, or skips.
- Both real AE2/GuideME/Mixin data environments start successfully.
- Calculation, plan, executing-job, CPU-logic, and elapsed-time classes pass transformed-bytecode takeover verification in both generations.
- Each adapter packages exactly 64 Loop Storage recipe JSON files and 74 AE2LF recipe JSON files in total, two localized Loop Storage GuideME pages, 33 Loop Storage items, 36 Loop Storage textures, 33 item models, and eleven native drive models.
- The ten core textures in each adapter match the user-supplied SHA-256 values byte-for-byte. Every finite/infinite cell recolor passes exact pixel comparison, and every drive model matches the pinned AE2 source bytes.
- Both packaged `neoforge.mods.toml` files decode as strict UTF-8, report version 0.0.3, and declare no optional storage addon as a dependency. Both Gradle adapters set `processResources.filteringCharset` to UTF-8 and expand only `META-INF/neoforge.mods.toml`; JSON, GuideME Markdown, PNG, and all other resources remain unfiltered.
- Final release JAR SHA-256: 1.21.1 `8DC0C2854ADCE448B34BB99F77F3B30FB666F5876896C3DBCADE3BD94C531A8A`; 26.1.2 `6A9FC6A0A58F1B34DB3A3E54932DC4582F32F39049E5A814F88AAB4852061A93`.
- At the 0.0.3 deployment, both PCL test instances contained exactly the expected six JARs with the matching 0.0.3 hash, no stale 0.0.2 addon, no filesystem reparse point, and independent mode enabled. The deployment preservation regression and the real-instance save-manifest checks passed.
- The 1,000-template execution model delivers exactly 1,000 net templates and returns one locked seed; a 500-round three-node SCC delivers the same net growth without skipping an unavailable node.
- Parallel ring and optimizer jobs retain separate cursor state, and optimizer-owned final outputs bypass the ring-only recycling branch.
- The two global adapters are byte-identical; the two Mixin sources are byte-identical.
- Both JARs contain `CraftingTakeoverPolicy`, `Ae2GlobalCraftingOptimizer`, and `CraftingCalculationMixin`.
- Peta SCC stress represents `1.5 x 10^15` applications in 94 balance iterations and 96 compressed batches.
- P-total-material stress covers 64 component types and 128 producer routes.
- T-distinct diagnostics report the unavoidable `Omega(V + E)` traversal and minimum reference cost without imposing a live threshold.

This historical baseline did not include persistent in-game network submission or visual hold-`G` acceptance. Current network-submission evidence is listed in the 0.0.4 section; it does not extend to untested GuideME interactions.

## Branding update — 2026-09-08

Public name: AE2-LoopFactory; abbreviation: AE2LF; repository: https://github.com/positer/AE2-LoopFactory. Both version adapters update mod display metadata, creative-tab translations and GuideME references. Internal IDs, package paths, artifact names and existing PCL instance paths remain stable. No project directories were moved. Historical release tags and assets remain intact.

## 0.0.5 development structure

- docs/loop-factory-0.0.5-design.md: user requirements, completed interaction decisions, bufferless routing correction, selectors, recipe parameters and SFM compatibility requirements.
- shared/src/main/java/com/example/ae2lightoptimizer/factory/: native compiler, durable VM, exact Boolean expressions, A!(B,C)/Pn selectors, source-declaration budgets, tag reconciliation, Unicode chunking and supported SFM scheduling/compiler.
- shared/src/test/java/com/example/ae2lightoptimizer/factory/: semantic compiler, continuation, source routing, selector, tag and SFM parser regressions.
- Each version's factory package: component-backed native pattern wrapper, AE network/subnet hosts, provider job persistence, direct resource routing and editor menu foundations.
- Each version's FactoryPattern* mixins: native AE2 recipe encoding preserves factory code/binding, native recipe ghosts load from the wrapped recipe, and only native encoding menus accept factory patterns in their input slots.
- archive/2026-09-08-loop-factory-0.0.5/: acceptance checklist, API inspections and build evidence. Work is incomplete; no release/installation has occurred.
- tools/runtime-factory-probe/: independent generation-specific test mods, fresh-world bootstrap, real barrel routing and native AE2 CPU/vanilla furnace fixtures. Helpers remain outside production source sets.
- Each version's FactoryEditorScreen and factory editor screen style: native AE2 frame/slot/save action with multiline code editing. Live GUI checks are separate from build validation.
- FactorySignalMixin and factory host persistence: tagged incoming redstone, pulse deadlines and terminal execution queues. Complete restart and destruction recovery remain separate acceptance gates.

### Factory source map

Shared source stays independent of Minecraft classes:

| File | Responsibility |
| --- | --- |
| FactoryProgram.java | Immutable instructions, functions, imports and compiler diagnostics |
| FactoryCompiler.java | Native indented syntax and recipe/material-index validation |
| FactoryExpression.java | Checked signed-64-bit counts, comparisons and truth conversion |
| FactorySelector.java | Typed resource selectors, wildcards, exclusions and Pn references |
| FactoryMachine.java | Resumable instruction pointer, waits, call stack, finite budgets and safe suspension |
| FactoryRoutes.java | Virtual source declarations and remaining committed-transfer budgets |
| FactoryTags.java | Named position sets; preserve bindings for unchanged imports |
| SfmSyntax.java / SfmCompiler.java | SFM reader and scheduler lowering for supported triggers, routes and conditions; advanced clauses remain unsupported |

Each independent version adapter owns the following files in its factory package:

| File | Responsibility |
| --- | --- |
| FactoryContent.java | Deferred block, item, entity and menu registration |
| FactoryBlock.java | Provider/terminal/cable block interaction and native redstone notification |
| FactoryBlockEntity.java | Distinct main/subnet nodes, energy forwarding, tag owner, pulses, terminal queue and persistence |
| FactoryServer.java | Loaded-host lifecycle, logical owner selection and dimension-local network membership |
| FactoryPatternData.java | Component codec, defensive native recipe copy and value identity |
| FactoryPatternDetails.java | Native AE2 pattern interface with factory code/binding identity |
| FactoryPatternItem.java | Native pattern preview/decode and shift-clear |
| FactoryProviderLogic.java | Pattern acceptance, native CPU dispatch, bounded job queue and persisted snapshots |
| FactoryBuffer.java | Exact-key provider job source/return storage |
| FactoryJob.java | Concrete job continuation, allocated Pn keys, resource routing and main-grid output |
| FactoryResourceSelector.java | Native AE key adaptation for shared selectors |
| FactoryTransfers.java | Simulate-before-extract direct transfer and residual recovery |
| FactoryEditorHost.java | Common code-editor host contract |
| FactoryEditorMenu.java | Server-authoritative pattern slot, draft/save action and GUI synchronisation |

The client package's FactoryEditorScreen.java and assets/ae2/screens/ae2lightoptimizer_factory_editor.json provide the actual code editor. FactoryPatternEncodingAccess, FactoryPatternEncodingMixin, FactoryPatternLoadMixin and FactoryPatternSlotMixin integrate native recipe menus; FactorySignalMixin contributes tagged incoming redstone. These UI/recipe interactions have separate runtime gates.

tools/runtime-factory-probe contains probe.init.gradle (separate helper compilation), verify-restart.ps1 (two-process acceptance driver), README.md (reproduction and boundaries), and independent per-generation ClientBootstrap.java (owned world lifecycle), FactoryProbe.java (barrel topology/routing), NativeCraftingFixture.java (real CPU/furnace and restart assertions) and RestartState.java (test-only lifecycle flags).

The two verified-restart summaries now confirm an unfinished native CPU job across normal save/exit and new-process load. This covers provider source, Pn, program continuation and final delivery; terminal-only queues and destruction recovery remain unverified.

Final manual UI evidence: both clients accept keyboard code edits and native save actions; the final modern client saves all dimensions and exits normally. Debug helpers are removed from development run/mods after testing; their sources and archived evidence remain available.

## Blockbench asset sources and full-round blocking

- `tools/blockbench/ae2lf_assets.js`: local Blockbench desktop plugin; exact source-palette conversion, phone/panel pixel drawing, native Java model and BB project export for both adapters.
- `design/loop_factory/README.md`: reproduction, source attribution and implementation boundaries.
- `design/loop_factory/references/`: pinned AE2 generation source textures and SFM classic source textures.
- `design/loop_factory/1.21.1/` and `26.1.2/`: six self-contained editable `.bbmodel` projects each, named for their content type.
- `design/loop_factory/blockbench-export.json`: recorded Blockbench version and exported model inventory; `SFM-LICENSE.txt`: upstream source license.
- Each adapter's `assets/ae2lightoptimizer/{textures,models,blockstates,items}`: exported production resources; modern item definitions remain generation-local. The phone is the handheld encoder, and the panel uses AE2's native `display_base` housing with only the three core masks substituted.
- FactoryJob persists code continuation and physical resource obligations. FactoryProviderLogic keeps blocking active until the job is fully finished, including its code tail and resource settlement; primary return alone no longer admits the next task.

The earlier final-assets inventory-admission checks are superseded by the user-confirmed primary-return contract. Current verification is recorded in archive/2026-09-08-primary-return-gate/. Actual framebuffer images verify the final grayscale provider, blue-code terminal and light-grey cable; normal shutdown and helper cleanup are recorded in the dated archive.

2026-09-08 primary-return gate verification: both final clients passed the real two-batch CPU fixture, byproduct/partial-return checks and production CODEC continuation checks, then shut down normally. See archive/2026-09-08-primary-return-gate/REPORT.md.


## Invisible native audit and provider direction correction

- tools/runtime-factory-probe/background-agent/HiddenWindowAgent.java and MANIFEST.MF: test-only invisible GLFW creation and show/focus suppression, with a separate ASM dependency; never bundled in production.
- build-background-agent.ps1: reproducibly compiles that helper for the Java 21/25 launchers. verify-background.ps1: unique evidence directories, generation-specific test dependencies and hidden launch properties, with optional supplier visual-only refresh.
- Each generation's BackgroundAudit.java: native menu actions, inventory/block synchronization, registry item rendering, placed-block and six-facing supplier screenshots. CompatibilityAudit.java: real registered keys/cells and FactoryJob source/subnet/main storage paths. report-background.py: reads actual reports and unmodified PNGs to build the searchable gallery.
- design/loop_factory provider projects and tools/blockbench/ae2lf_assets.js: up-facing base model, exactly four lateral arrows, independent neutral native rear texture; the six blockstates match pinned AE2 direction rotations.
- archive/2026-09-08-background-full-audit/: REPORT.md and COVERAGE.md explain pass/gap boundaries; summary.json includes every case group, loaded storage types, current unit-test totals and production hashes; index.html exposes 174 selected native screenshots. Earlier failed fixture runs remain as evidence, superseded by final-* and provider-final-*.

Both completed main runs contain 100 passing steps and normal shutdown evidence. Temporary probe/AppliedFlux/Glodium JARs were removed from both development mod folders after testing; PCL files were only read as matching dependency sources. Full 0.0.5 feature acceptance remains incomplete.


## Factory encoder and panel additions (2026-09-08)

The following files exist in each version adapter under `src/main/java/com/example/ae2lightoptimizer/`:

| File | Role |
| --- | --- |
| `factory/FactoryEncoderItem.java` | Air/block interactions, persistent binding, server tag selection and bounded same-machine fill |
| `factory/FactoryEncoderHost.java` | Native item menu host; stores the physical pattern in the encoder container component |
| `factory/FactoryEncoderView.java` | Server-filtered tag/position snapshot, selected tag and dimension identity |
| `factory/FactoryEncoderAction.java` | Registered client-to-server selection/marking requests with held-item, reach and membership validation |
| `factory/FactoryEncodingPanel.java` | Native multipart recipe terminal subclass, shared encoded slot and durable code draft |
| `factory/FactoryPanelRecipeMenu.java` | Native recipe menu with an action to switch to the code page |
| `client/FactoryEncoderClient.java` | Tab-scroll, Ctrl-use dispatch and native world outline rendering |
| `client/FactoryPanelRecipeScreen.java` | Native recipe screen with a distinct code-page control |
| `client/FactoryIconButton.java` | Native AE2 icon button with matching localized tooltip and narration label |
| `client/FactoryUploadScreen.java` | Localized, paginated provider chooser using server-provided identities |
| `client/FactoryMessages.java` | Client-language rendering of synchronized statuses and common compiler diagnostics |

`FactoryEditorMenu` now validates recipe material indices when saving, exposes page changes and validates physical uploads. `FactoryServer` resolves UUIDs and main-grid provider choices. `FactoryBlockEntity` persists user factory names. `FactoryContent` registers both items and menus. All six items have generation-specific recipes; language files and GuideME pages remain split by language.

`tools/runtime-factory-probe/BackgroundAudit` adds real recipe/code transitions, upload, encoder container round-trip, validated selection/fill packets, native outline frames, multipart dye changes and client language reloads. Captures wait for loading overlays to disappear. That extension is recorded in `archive/2026-09-08-factory-encoder-panel/`; the later full-stress work below supersedes its induction, scheduling and uniqueness/recovery gaps.

Final encoder/panel evidence (2026-09-08): REPORT.md documents coverage and gaps; summary.json records 122 passed steps per generation and artifact hashes; index.html links 208 raw screenshots. retired-test-mods/ holds the six temporary helper/dependency JARs removed after normal client shutdown.


## 2026-09-08 pressure-test implementation

`archive/2026-09-08-full-stress/` contains the current pressure regression report, raw-frame index, aggregate JSON and captured wildcard hang thread. Earlier intermediate evidence remains immutable. The runtime helper README describes each assertion and its limits.

| Source | Structure and purpose |
| --- | --- |
| `shared/.../factory/SfmCompiler.java` | Lowers supported SFM timers, pulse triggers, routes, FORGET and conditions into durable VM instructions. |
| `shared/.../factory/FactoryCodeChunks.java` | Splits long code without splitting UTF-16 surrogate pairs. |
| `shared/.../factory/FactoryStressTest.java` | 100,000 tick/restore and route redeclaration pressure, long wildcard and Unicode boundaries. |
| `shared/.../factory/SfmExecutionTest.java` | Timer, pulse, recipe completion, exclusion, quoted labels and Boolean semantics. |
| Each adapter's `factory/UniqueNetworkServices.java` | Elects one physical service per kind/grid, disconnects duplicate nodes, and restores topology after changes. |
| Each adapter's `factory/FactoryInduction.java` | Optional registry-discovered card support, bounded FE input cache and return on card removal. |
| Each adapter's `mixin/FactoryInductionTickerMixin.java` | Optional AppliedFlux hook prevents native distribution from bypassing the factory program. |
| Each adapter's `factory/FactoryCodeText.java` / `FactoryEditorMenu` | Native large-code S2C payload and ordered bounded C2S chunks. |
| Each adapter's `factory/FactoryPatternData.java` | Backward-readable string-or-chunk-list codec avoids NBT single-string limits. |
| Each adapter's `FactoryBlockEntity` / `FactoryProviderLogic` | Persist terminal scheduler and provider caches; expose native item/pattern destruction drops. |
| Each adapter's `client/FactoryMessages.java` and language JSON | Client-local diagnostics, including SFM and selector limits, without concatenated translations. |
| Each adapter's `loot_table/blocks/loop_factory_*.json` | Self-drop declarations for the provider, terminal and full cable block. |
| `tools/runtime-factory-probe/<generation>/.../{StressAudit,TopologyAudit,TerminalAudit,InductionAudit,RestartExtras}.java` | Isolated real-world feature pressure and disk restart fixtures, never production dependencies. |
| `tools/runtime-factory-probe/report-stress.py` | Asserts independent evidence success and builds searchable raw screenshot gallery. |

The two full-stress restart pairs now also establish terminal wait continuation, SFM timer and held-pulse state, and induction FE preservation in separate JVMs. Native item/pattern destruction recovery is tested. Advanced SFM clauses, arbitrary unloaded-addon handlers and long-duration chunk/non-item destruction cases remain outside the verified scope.


2026-09-09 extension: FactorySourceView in each adapter keeps per-key availability ceilings for a PUT without buffering items; FactoryJob target records retain physical endpoint identity and sorted machine positions. FactoryFunctionPlacementTest exercises program-scoped forward/nested definitions, and MultiTagAudit supplies seven real group machines plus overlap/replacement/redstone/128-cycle checks. RestartExtras now retains three real machines under one source tag. Latest runtime evidence is pending; see the full-stress progress record.


2026-09-09 loop timing update: recipe programs must complete one round and reject unconditional loops even with waits; only recipe-free programs may run continuous yielding loops. The finite recipe lifetime budget remains. Ordinary instructions execute in the current tick, with explicit wait/redstone suspension and visible watchdog errors. Native English and Simplified Chinese Loop Factory guides now separately cover setup, editor icons, binding, group quotas, selectors, Pn, functions, blocking and supported SFM clauses.

2026-09-09 循环时序更新：有配方必须完成一轮，即使带等待也禁止无条件循环并保留有限指令预算；只有无配方允许实际经过正时长等待/红石的持续循环。普通指令在当前 tick 执行，等待/红石显式暂停，执行保护以错误呈现。中英文原生工厂指南分别补全搭建、图标、绑定、组额度、选择器、Pn、函数、阻挡模式与 SFM 支持范围。

Ordinary quantities advance after partial or zero transfer; must quantities require full completion. Tasks wait independently. 普通数量完全受阻时跳过，部分成功也可继续；must 要求足量完成，各任务独立等待。


2026-09-09 latest accepted contract (supersedes earlier partial-return/skip wording): ordinary PUT advances even when zero is accepted and after partial success; must quantities accumulate in the owning task until complete. GET must declares the corresponding output obligation. Concurrent tasks retain separate continuations. Blocking now waits for complete code execution and physical resource settlement, not merely primary return.

2026-09-09 最新确认规则（覆盖旧的主产物放行/跳过表述）：普通 put 完全受阻跳过、部分成功可继续，must 数量在本任务中累计足量才继续；get must 声明对应输出义务。并行任务各持有流程。阻挡模式等待代码完整结束及实物结清，不再仅凭主产物返回放行。


Recipe output references: O1, O2, ... select native recipe outputs in order, independently of P1, P2 inputs. They support transfers, HAS, exclusions and must; missing recipes and out-of-range output indexes are errors. 配方输出引用 O1、O2 等按原生配方产物顺序编号，与 P1、P2 输入独立，支持物流、has、反选和 must；无配方与越界输出编号报错。


### New execution and acceptance files

| File | Responsibility |
| --- | --- |
| `shared/src/test/java/com/example/ae2lightoptimizer/factory/FactoryFunctionPlacementTest.java` | Forward and nested program-scoped function definitions, wait restoration and invalid names/end markers. |
| `shared/src/test/java/com/example/ae2lightoptimizer/factory/FactoryContinuousLoopTest.java` | Recipe-free persistent yielding, same-tick ready instructions and recipe unconditional-loop rejection. |
| `shared/src/test/java/com/example/ae2lightoptimizer/factory/FactoryMustTest.java` | Ordinary partial/zero behavior, mandatory remaining quantities, source obligations and independent VM progress. |
| `shared/src/test/java/com/example/ae2lightoptimizer/factory/FactoryOutputReferenceTest.java` | Distinct Pn/On identities, output bounds, exclusions, HAS and SFM extensions. |
| Per-generation `factory/FactorySourceView.java` | Numeric per-source-group PUT availability ceiling; actual resources remain in native storage. |
| Per-generation probe `MultiTagAudit.java` | Seven real machine bindings, group quotas, replacement, redstone, 128 round trips and blocked MUST progress. |
| Per-generation probe `RestartExtras.java` | True process restart for waits, SFM, FE, Unicode, tags and partial MUST completion. |
| `tools/runtime-factory-probe/report-stress.py` | Strict evidence collection, production artifact checks and original-frame gallery. |
| `tools/runtime-factory-probe/finish-stress-report.py` | Bilingual bounded acceptance report from validated evidence only. |

Both native guide trees contain a separate `items-blocks-machines/loop_factory.md` for English and `_zh_cn/items-blocks-machines/loop_factory.md` for Simplified Chinese. They explain the same current behavior, including On, must and full-round blocking; language is selected by the client.

### 2026-09-09 output references and final evidence

- `archive/2026-09-08-full-stress/REPORT.md`: bilingual bounded acceptance results and remaining limitations.
- `archive/2026-09-08-full-stress/summary.json`: strict machine-readable counts, loaded storage types and final artifact hashes.
- `archive/2026-09-08-full-stress/index.html`: searchable gallery of 320 untouched native frames.
- `archive/2026-09-08-full-stress/restart7-artifacts.json`: frozen production JAR and class hashes used by both true restart pairs; prior guide-resource hashes remain in `pre-guide-wrap-artifacts.json`.
- `archive/2026-09-08-full-stress/runtime-inputs/`: six removed development-run helper/addon JARs and SHA-256 manifest. Original PCL dependencies are untouched.
- `archive/2026-09-08-background-full-audit/final-stress7-<generation>/`: 147 passing feature/model/UI rows per generation, independent stress and compatibility reports.
- `archive/2026-09-08-background-full-audit/guide9-<generation>/`: 38 passing guide rows per generation after correcting GuideME opening and old-generation Chinese wrapping; earlier guide7/8 remain historical diagnostics.
- `archive/2026-09-08-loop-factory-0.0.5/full-stress-restart7-<generation>-prepare/resume`: separate JVM persistence fixtures, including On recipe continuation and must 24-before/40-after recovery.

Both runtime generations exited normally and their temporary run/mods inputs were archived. Final unit counts 195 / 193, with no failures/errors/skips. Full SFM, arbitrary addon handlers, long chunk lifecycle, non-item destruction recovery and exact general wildcard-must overlap remain outside a complete acceptance claim.


### 2026-09-09 runnable examples and complex flow implementation

| Path | Structure and purpose |
| --- | --- |
| `tools/runtime-factory-probe/examples/catalog.json` | Eight recipe-mode-labelled runnable programs; shared by documentation and the native fixture. |
| `tools/runtime-factory-probe/sync-guide-examples.py` | Writes localized setup/expected-result descriptions and identical fenced programs to four MDX guide pages. |
| `shared/src/test/.../factory/FactoryGuideExamplesTest.java` | Compiles examples, checks recipe context and exact bilingual code synchronization; rejects HTML comments. |
| `shared/src/main/.../factory/FactoryRoutes.java` | Source declarations and quotas; restore preserves the mandatory flag before the first PUT. |
| `tools/runtime-factory-probe/<generation>/.../ComplexFlowAudit.java` | Six real terminal networks, continuous/multi-job restoration checks and four native CPU furnace orders. |
| `versions/neoforge-26.1.2/src/main/.../mixin/GuideCodeLineWrapMixin.java` | Client-only compatibility correction for GuideME 26.1.10-alpha explicit-newline width accumulation. |
| `tools/runtime-factory-probe/<generation>/.../BackgroundAudit.java` | Original-frame catalogue and compiled guide content checks; modern helper also validates actual rendered text geometry. |
| `tools/runtime-factory-probe/report-flows.py` | Strict results/artifact validation and searchable native screenshot gallery. |
| `archive/2026-09-09-factory-flows/` | This campaign's build logs, artifact snapshots, layout diagnosis and final report/gallery; prior rejected captures remain at their original paths. |

The guide fix changes only modern client layout; factory execution code is unchanged after the completed full flow scenes. Final source/JAR checks and separate-process restart validation are recorded in the campaign report.

2026-09-09 flow campaign completed: each generation passed 148 full native rows, 126 guide rows, four real CPU orders / twelve recipe batches, 32 redstone admissions and 4096 world round trips, plus a fresh separate-JVM restart pair. Unit tests: 197 (1.21.1), 195 (26.1.2), no failures/errors/skips. The [final report](archive/2026-09-09-factory-flows/REPORT.md) and [gallery](archive/2026-09-09-factory-flows/index.html) contain 398 selected original frames; guide scroll frames overlap and are not distinct scenarios. Six temporary helper/addon JARs are archived under `archive/2026-09-09-factory-flows/runtime-inputs/`; original PCL files and release 0.0.4 are unchanged. This supersedes earlier test counts, not the documented unsupported-feature boundaries.


### 2026-09-09 native capability and parallel-order work

- `versions/neoforge-1.21.1/.../factory/FactoryNativeTransfers.java`: physical item, fluid, FE and optional MEK chemical capability transport; no additional AE chemical key registration. Native recovery records are persisted in `FactoryJob.Saved`.
- `tools/runtime-factory-probe/1.21.1/.../NativeCapabilityAudit.java`: real MEK tank/cube must, exclusion, HAS, group transport and codec restoration fixtures.
- `tools/runtime-factory-probe/1.21.1/.../MekanismBulkAudit.java`: three simultaneous CPU orders sharing one nonblocking provider/subnet; per-order admission/completion/active accounting with real enrichment and smelting.
- `tools/runtime-factory-probe/report-mekanism.py`: strict evidence, unit, JAR boundary and separate-process checks before generating campaign acceptance.
- `docs/loop-factory-native-capabilities-1.21.1.md`: bilingual native interface contract and examples.
- `archive/2026-09-09-mekanism-bulk/`: pinned optional test inputs, build provenance and campaign records; pending runs are not acceptance evidence.

Both generations share the latest quantity rule: ordinary zero/partial transfers continue; only must obligations retain the instruction until their amount is fulfilled. Both generations now use native machine capability adapters: legacy simulation/execution on 1.21.1 and transactions plus registered-resource discovery on 26.1.2.

- `versions/neoforge-26.1.2/.../factory/FactoryNativeTransfers.java`: transactional native item/fluid/FE and additional RegisteredResource handler discovery, isolated from the older capability API.
- `tools/runtime-factory-probe/26.1.2/.../NativeTransactionalAudit.java`: persistent world endpoints registered by the helper for explicit native interface/rollback tests; no claim of modern MEK execution.
- `tools/runtime-factory-probe/26.1.2/.../ParallelOrderAudit.java`: three native CPUs submitting iron/gold/copper recipes simultaneously to one nonblocking provider and real vanilla blast furnaces;12invocations per type.
- `docs/loop-factory-native-capabilities-26.1.2.md`: modern adapter and fixture boundaries.


- `shared/src/main/java/com/example/ae2lightoptimizer/factory/FactoryLazyPorts.java`: lazy operation-local capability traversal, shared by both native adapters.
- `shared/src/test/java/com/example/ae2lightoptimizer/factory/FactoryLazyPortsTest.java`: successful unsided lookup, fallback order/reuse, full rejection and fresh-operation tests.
- `FactoryServer.members` in both adapters: single live-grid traversal for one tag, validated against per-position membership across block replacement; no cross-tick membership cache.


Final native factory performance evidence is archived under `archive/2026-09-09-mekanism-bulk/`; the accepted report separates local membership and stress timings from universal TPS claims.


- `shared/src/test/java/com/example/ae2lightoptimizer/factory/FactoryParserBoundaryTest.java`: regression cases for constant-true recipe loops and malformed destinations, with legal alternatives.
- `archive/2026-09-09-debug-boundaries/`: failing reproductions, both unit/build logs, exact artifact manifests and native editor results for the compiler boundary fixes.

- 2026-09-09：FactoryPatternSlotMixin 扩展普通样板编码器槽位兼容循环工厂样板。

- 2026-09-09：PCL 部署使用 ae2lf-neoforge-mc*-0.0.5.jar。


### 2026-09-13 terminal lifecycle and saved-scene diagnosis

- `versions/neoforge-{1.21.1,26.1.2}/src/main/java/com/example/ae2lightoptimizer/factory/FactoryBlockEntity.java`: terminal-only cancellation/restart, installed-program comparison, persistent pending restart and physical-cargo handoff. Provider order lifecycle stays independent.
- `tools/runtime-factory-probe/{1.21.1,26.1.2}/src/main/java/com/example/ae2lfprobe/TerminalRefreshAudit.java`: native terminal/signal/container fixture, 67 named assertions including 32 repeated edges, binary NBT and explicitly seeded cargo; called by `ChannelAudit.java` after existing UI/ledger gates.
- `tools/runtime-factory-probe/26.1.2/src/main/java/com/example/ae2lfprobe/SavedSceneMisrouteAudit.java`: six independent saved-code/overlapping-label/real-furnace-face reproductions in a separate world.
- `docs/loop-factory-channels.md` and both generations' bilingual `ae2guide/.../loop_factory.md`: restart semantics, resource ownership and physical label overlap.
- `archive/2026-09-13-channel-face/`: [dated result and full evidence index](archive/2026-09-13-channel-face/REPORT.md); raw read-only world extraction, region hashes, compiler preflight, native reports/verifiers, build/byte manifests, frozen accepted JARs and guarded PCL deployment/restoration backups. Both deployed generations passed 262/260 unit tests and 67/67 native refresh assertions.

### 2026-09-13 README rewrite

- `README.md`: consolidated bilingual, long-lived project description. It describes product behavior, language, channel isolation, bulk logistics, editor input, recipe services, storage, compatibility boundaries, installation, documentation, and development layout without embedding a release number, local deployment path, or one-off validation result.
- `archive/2026-09-13-readme-rewrite/REPORT.md`: records the stable documentation scope and confirms that this pass changed no code, artifact, PCL, save, dependency, or release metadata. Dated measurements and release details remain in their historical archive reports.
- `archive/2026-09-13-readme-stable/REPORT.md`: final dated handoff record for the version-independent README and its retained evidence boundaries.

### 2026-09-13 public release 0.0.5

- Commit `1ea5964` was pushed to `origin/main` and tag `v0.0.5` was published as a non-draft, non-prerelease GitHub release.
- Release assets are `ae2lf-neoforge-mc1.21.1-0.0.5.jar` (699,793 bytes, SHA-256 `1a07eb16816e15acde1910e75a8da01306ed6dcbc0dff219599d5dccaf6e7401`) and `ae2lf-neoforge-mc26.1.2-0.0.5.jar` (717,249 bytes, SHA-256 `663319c64ec9246743d64711d0d98a33ee018ac21221058badec8dfcf4a1d332`).
- Release notes use the complete English 0.0.5 section followed by the complete Simplified Chinese section from `CHANGELOG.md`; no internal paths, hashes, commands, or deployment details appear in the public notes.
- The release archive is [archive/2026-09-13-release-0.0.5/REPORT.md](archive/2026-09-13-release-0.0.5/REPORT.md). Historical sections retain their original test and deployment claims.
