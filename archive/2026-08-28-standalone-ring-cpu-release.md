# 2026-08-28 Standalone ring CPU release

## Runtime report

The cyclic physical work and output production completed, but the selected AE2 crafting CPU remained busy indefinitely.

## Proven root cause

AE2's crafting confirmation menu submits player-initiated jobs with a null `ICraftingRequester`. For such standalone jobs, `CraftingLink.insert` returns zero because there is no requester endpoint. AE2's native `CraftingCpuLogic.insert` still reduces `ExecutingCraftingJob.remainingAmount` by the final-output quantity that returned to the CPU, independent of the link's routed amount.

The ring output-lock implementation instead reduced `remainingAmount` only by the amount returned from `CraftingLink.insert`. A normal crafting-terminal request therefore remained at its original requested quantity forever even after every cyclic operation and final output had completed, preventing the completion gate from calling `finishJob(true)`.

## Correction

- The amount above the seed reserve that returned to the CPU now satisfies the outstanding request in full.
- `CraftingLink.insert` controls only how much is routed directly to a real requester.
- Any unrouted final output remains in CPU inventory.
- Once the schedule is complete, the request is satisfied, and final output is no longer in flight, native `finishJob(true)` marks the link done, clears the AE2 job, and calls `storeItems`.
- Native `storeItems` returns unrouted net output, the retained ring seed, and other residual CPU inventory to network storage before the CPU is reused.
- The behavior remains ring-owner-only; optimizer-owned and unmarked jobs are unchanged.

## Verification

- Added a standalone terminal regression with a zero-routed crafting link and full net-output satisfaction.
- Added both version contracts requiring `decrementRemainingRequest(releasable)` and rejecting requester-routed accounting.
- 53 tests pass in each generation with zero failures and zero errors.
- Both complete builds pass.
- Both real NeoForge/AE2 environments pass transformed-bytecode global takeover verification.
- Both PCL instances were refreshed through the save-manifest-guarded non-destructive deployment path.
- Build and deployed JAR SHA-256 values match:
  - 1.21.1: `56DE6A480B38FF79E777F9ABBBB4BF5B5BD1BDC20726AE2F74483C5B2CBE24A4`
  - 26.1.2: `EC6D0D9918DBA06BF0E2B86E631CA65A0CBD27AAA28A2BB7D1C45FE92EA363E4`
