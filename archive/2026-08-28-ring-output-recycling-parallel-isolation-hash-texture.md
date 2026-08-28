# 2026-08-28 Ring output recycling, parallel isolation, and hash-core texture

## Reported runtime defect

A live smithing-template growth request started with one template and sufficient raw material, requested 1,000 new templates, dispatched one pattern, produced one net template, and then stalled. Planning and ordered-dispatch tests had passed, but they did not exercise AE2's final-output delivery path.

## Root cause

AE2's `CraftingCpuLogic.insert` sends every provider output matching `job.finalOutput` directly to the crafting requester and decrements the request by gross output. For `1 template -> 2 templates`, both returned templates left the CPU after the first operation. The compressed cursor still pointed at the next repetition, but no template remained in CPU inventory to extract as its input.

## Runtime correction

- Added a ring-only final-output interception at AE2's global CPU insertion path.
- Provider output for a ring-owned job enters that crafting CPU's private inventory first.
- No matching final-output carrier leaves the CPU while the compressed ring schedule is incomplete.
- The schedule persists target stock initially required as `finalOutputReserve`.
- At dispatch completion, the CPU releases only `stored - finalOutputReserve`, capped by the remaining request.
- Completion-time flushing includes surplus accumulated before a non-power-of-two final batch, preventing residual demand from stalling.
- Requester backpressure leaves output in CPU inventory for a later flush.
- Finalization waits until the request is satisfied and `waitingFor` contains no output from already-dispatched patterns.
- AE2's existing `finishJob/storeItems` path returns the locked seed and non-requested surplus to network storage.
- Native elapsed-time tracking is preserved through a narrow Mixin invoker forced to transform at startup.

## Parallel module compatibility

- Recycling requires a `RING_TERMINAL` owner before final-output identity is checked.
- `OPTIMIZER_INTERFACE` jobs retain AE2's original final-output insertion logic.
- Schedule, cursor, reserve, waiting outputs, and remaining request are stored per `ExecutingCraftingJob`; none is static or network-global.
- Both blocks online still route acyclic graphs to the optimizer and cyclic graphs to the ring terminal.
- A regression advances ring and optimizer cursors concurrently and proves completion of one does not mutate the other.

## Optimizer texture

- Preserved both recipe-ring terminal textures.
- Replaced the optimizer's prior cross core with a native 16x16 hash-grid core.
- Both states use identical double-horizontal/double-vertical geometry with four compute intersections and eight endpoints.
- Connected state uses cyan-white rails and amber endpoints; offline state keeps the same geometry dim.
- The generator proves exact 90-degree pixel rotation invariance for both states.
- The Minecraft texture audit reports four referenced textures and zero issues for each generation.

## Verification and deployment

- 49 tests pass per generation with zero failures and errors.
- The execution model replays 1,000 template duplications, delivers exactly 1,000 new templates, and retains one seed.
- A 500-round `seed -> alpha -> beta -> seed` irreducible ring delivers 1,000 net seed without skipping an unavailable node.
- P-scale (`10^15`) output-release arithmetic remains constant-state.
- Both real NeoForge/AE2 environments pass transformed-bytecode checks for calculation takeover, compressed dispatch, ring output locking, CPU inventory recycling, elapsed-time accounting, and persistence.
- Both isolated PCL instances were recreated with exactly AE2, GuideME, JEI, and their matching addon JAR.

## Final addon artifacts

- 1.21.1 SHA-256: `EA5ED1212AFB3891EE23CE81DFDF77CE0FED681162EE79E3307D8AEB39820FD6`
- 26.1.2 SHA-256: `999A0998747F33B5DE505925C4854EB10A8B8058372063D7B7B9122626AB80C6`
