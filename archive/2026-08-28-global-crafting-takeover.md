# 2026-08-28 Network-wide crafting takeover

## Scope

- Replaced the version-local `Ae2SinglePatternFastPath` with `Ae2GlobalCraftingOptimizer` in both maintained generations.
- Kept the hook at AE2 `CraftingCalculation.runCraftAttempt`, the network-wide calculation entry used by normal requests, craft-less retries, and missing-item simulation.
- Added a loader-independent global planner with reverse reachability, Tarjan SCC detection, checked integer balance solving, stock-aware multi-producer allocation, and compressed executable scheduling.

## Independent block behavior

- A supercomputing optimizer interface alone accepts proven acyclic graphs and does not require a ring terminal.
- A recipe ring solver terminal alone accepts proven cyclic graphs and leaves acyclic jobs untouched.
- With both online, the ring terminal owns SCC eligibility and the optimizer continues to cover ordinary global planning.
- Neither block has a UI. Both still require their AE2 channel, power, and active managed grid node.

## AE2 plan bridge

- Collects all reachable producers through `ICraftingService.getCraftingFor`.
- Selects listed substitute inputs using stored coverage and craftability, and models all pattern outputs plus container remaining keys.
- Snapshots relevant network stock with simulated extraction and records craftable emitters.
- Writes credited stock, missing stock, emitted stock, pattern repetitions, and byte accounting into `ChildCraftingSimulationState` before building AE2's native `CraftingPlan`.
- Preserves a target item when it is required as an SCC seed instead of applying AE2's normal top-level output ignore rule.
- Declines before state mutation on overflow, exhausted budgets, dead cycles, or unprovable schedules.

## Performance evidence

- Peta-scale irreducible SCC: three patterns, `1.5×10^15` total pattern repetitions, 94 balance iterations, 96 compressed schedule batches, work ratio `7,894,736,842,105` relative to expanded applications.
- Tera-scale two-producer graph: AE2's official multi-branch path applies a pattern one at a time; the `10^12`-application fixture plans in three balance iterations and two batches, work ratio `200,000,000,000`.
- Automated gates require the SCC ratio to remain above `10^12`, the multi-branch ratio above `10^10`, both planners to finish within two seconds, and SCC balance/schedule counts to remain below 256 each.
- A regression found during implementation caused an external catalyst to be injected one item per loop. The scheduler now injects the full remaining non-produced demand once.
- A second regression seeded the cheapest internal node instead of the requested growth resource. Seed selection now prefers a pattern consuming the requested target.
- Multi-producer allocation was corrected to consume each route's stock-supported batch before selecting the next route, preventing false missing-item reports.
- A 64-component, 128-route fixture now carries exactly `10^15` total raw items and verifies compressed global allocation without false missing stock.
- A metadata-only envelope covers `10^12` distinct materials, `10^12` irreducible nodes, `3×10^12` edges, and `10^15` total material. It reports at least `5×10^12` visits and 80 TB of 16-byte references without allocating the graph or deciding live admission.
- The live AE2 collector has no fixed resource, pattern, or edge ceiling. Every graph AE2 can enumerate remains eligible, while network planning budgets scale with the discovered graph and repeated T/P-level quantities remain compressed.

## Validation

- Minecraft 1.21.1 / NeoForge 21.1.235 / AE2 19.2.17 / Java 21: 35 tests passed, full build passed, `runData` passed.
- Minecraft 26.1.2 / NeoForge 26.1.2.94 / AE2 26.1.10-beta / Java 25: 35 tests passed, full build passed, `runClientData` passed.
- Both adapter sources are byte-identical across generations.
- English and Simplified Chinese GuideME pages now describe global DAG/SCC behavior, independent operation, 16-round reserve accounting, and conservative fallback.
- The sibling ImmortalStorage `project/` tree was inspected only. It already contained unrelated user changes and was not modified by this work.

## Remaining release acceptance

- Submit representative DAG, multi-producer, single-growth, and nested-SCC jobs in a persistent in-game AE2 network and inspect CPU execution plus hold-`G` rendering before publishing a release.
- The planner guarantees bounded deterministic behavior for its modeled integer transitions; it does not claim a universal mathematical optimum for arbitrary mod-specific side effects.
