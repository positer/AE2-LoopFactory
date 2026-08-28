# 2026-08-28 Independent module takeover contract

## Requirement

Each optimization block must connect and work independently. When neither block is connected and active, AE2's original crafting calculation must retain control without addon writes to its calculation state.

## Implementation

- Added shared `CraftingTakeoverPolicy`, used directly by both production adapters.
- Encoded one exclusive owner per graph class: the optimizer interface owns acyclic graphs and the ring terminal owns cyclic graphs.
- Added a no-service gate before reachable graph discovery and simulated network-stock extraction.
- Added a graph-ownership gate before `ChildCraftingSimulationState`, stock extraction, missing/emitted writes, pattern-count writes, simulation-mode mutation, and callback cancellation.
- Changed the Mixin simulation-mode assignment into a callback invoked only after takeover ownership is confirmed. This preserves AE2's required `isSimulation()` value during `buildCraftingPlan` without writing the field on declined paths.
- Retained handled-only `CallbackInfoReturnable.setReturnValue`; all declined paths execute AE2's original `runCraftAttempt`.

## Behavior matrix

| Ring terminal | Optimizer interface | Acyclic graph | Cyclic graph |
| --- | --- | --- | --- |
| Offline | Offline | Original AE2 | Original AE2 |
| Online | Offline | Original AE2 | Ring terminal |
| Offline | Online | Optimizer interface | Original AE2 |
| Online | Online | Optimizer interface | Ring terminal |

## Source basis

The pinned official AE2 sources for both generations assign `this.simulate = simulate` at the beginning of `CraftingCalculation.runCraftAttempt`, then read the calculation through `CraftingSimulationState.buildCraftingPlan`. The adapter therefore delays the equivalent write until ownership is proven but performs it before native plan construction.

- Official AE2 repository: https://github.com/AppliedEnergistics/Applied-Energistics-2
- AE2 API documentation: https://appliedenergistics.org/javadoc/appeng-api/latest/

## Verification

- Added four policy tests covering all eight service/graph combinations.
- Added a version-local contract proving the no-service gate precedes graph traversal and the ownership gate precedes every first AE2 mutation boundary.
- Minecraft 1.21.1 full build: 40 tests, 0 failures, 0 errors.
- Minecraft 26.1.2 full build: 40 tests, 0 failures, 0 errors.
- `tools/verify_global_takeover.ps1`: both transformed `CraftingCalculation` classes passed optimizer-call, handled-decision, callback-cancellation, and early-return verification.
- The ImmortalStorage workspace was not modified.

## Boundary statement

With no active module, the injected entry performs only the two AE2 active-machine index lookups required to determine service presence. It does not traverse patterns, access stock, construct child state, change calculation fields, publish missing/emitted items, add crafting operations, or cancel AE2's callback. Thus original AE2 behavior and calculation state remain unchanged; the unavoidable presence check is constant-sized integration overhead.
