# 2026-08-28 Real AE2 takeover verification

## Problem

Packaging a Mixin config and checking source strings did not prove that AE2's runtime target class was transformed. Data generation previously prepared the Mixin but did not naturally load `appeng.crafting.CraftingCalculation`, so an incompatible injection could have remained undiscovered until a player submitted a crafting job.

## Implementation

- Both mod entry points now force `CraftingCalculation` to load during mod initialization without initializing an instance. This makes Mixin transform and validate the target on every game or data-environment startup.
- Constructor capture, `runCraftAttempt`, and `hasMultiplePaths` injections now each declare `require = 1` and `expect = 1`.
- The Mixin configuration remains `required: true` with `defaultRequire: 1`. A changed or missing AE2 target therefore fails startup instead of silently disabling optimization.
- Added `tools/verify_global_takeover.ps1`. It deletes the previous exported target class, launches each pinned NeoForge/AE2 environment with Mixin export enabled, disassembles the newly transformed class, and requires the optimizer call, handled branch, callback result, cancellation test, and early-return bytecode.

## Runtime evidence

- Minecraft 1.21.1 startup logged `Mixing CraftingCalculationMixin ... into appeng.crafting.CraftingCalculation` and completed successfully.
- Minecraft 26.1.2 startup logged the same target transformation and completed successfully.
- In both exported classes, `runCraftAttempt(boolean,long)` begins by invoking the injected handler. It tests `CallbackInfoReturnable.isCancelled()`, reads `getReturnValue()`, casts it to `CraftingPlan`, and returns at bytecode offset 36 before AE2's original `CraftingTreeNode.request` path.
- The injected handler invokes `Ae2GlobalCraftingOptimizer.tryPlan`. When `OptimizationAttempt.handled()` is true, it calls `CallbackInfoReturnable.setReturnValue(plan)`, which marks the callback cancelled and triggers that early return.
- AE2's official `IGrid#getActiveMachines` implementation indexes grid-node owners by their concrete machine class and filters with `node.isActive()`. The two managed nodes use their block entities as owners, require a channel, and therefore activate the correct independent service gates.

## Verification results

- `tools/verify_global_takeover.ps1`: passed for 1.21.1 and 26.1.2 using freshly exported transformed classes.
- Full builds after the fail-fast changes: passed for both generations.
- Unit/contract tests: 35 passed, 0 failed, 0 errors per generation.
- The sibling ImmortalStorage workspace was not modified.

## Remaining release acceptance

A persistent-world player submission remains useful as gameplay acceptance coverage, but it is no longer the first point at which hook compatibility is tested. Startup and bytecode gates now prove that the deployed runtime class executes the optimizer branch before AE2's original tree expansion.
