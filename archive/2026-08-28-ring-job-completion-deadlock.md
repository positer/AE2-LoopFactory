# 2026-08-28 Ring job completion deadlock

## Runtime report

A cyclic recipe completed its physical crafting work and delivered the requested output, but AE2 continued to report the crafting job as active instead of returning completion.

## Root cause

The ring output-lock Mixin required the entire AE2 `ExecutingCraftingJob.waitingFor` inventory to become empty before calling `finishJob(true)`. That collection can retain non-final output, container-return, emitter, or other bookkeeping keys after the requested cyclic final output has already completed. AE2's native completion path does not require every key to drain, so the stricter addon condition could hold a finished ring job open forever.

## Correction

- Added the loader-independent `RingCompletionGate`.
- Completion now requires all three relevant invariants: compressed schedule complete, remaining requested output equal to zero, and pending final-output carrier equal to zero.
- Pending final output is read from AE2's live `waitingFor` counter for the exact final key, so the last dispatched cyclic output must still return before the seed is released.
- Unrelated waiting keys no longer block completion.
- Removed the obsolete whole-`waitingFor` completion method from both version adapters.
- Kept owner isolation: this behavior remains ring-only and optimizer-owned acyclic jobs retain AE2's native completion path.

## Verification

- 52 tests pass in each generation with zero failures and zero errors.
- Both generation builds complete successfully.
- Both real NeoForge/AE2 environments transform and verify calculation, compressed execution, and cyclic output-lock takeover.
- The PCL instances were refreshed through the non-destructive managed-file deployment path.
- The 26.1.2 `saves/Test` world remained unchanged according to the deployment path/size/timestamp/SHA-256 manifest guard.

Artifacts:

- 1.21.1 SHA-256: `3309A5746677C7CABADCD766A71DBFF793408A65EAB093DE5CF3786792575CEA`
- 26.1.2 SHA-256: `882C97C5FB0DD9CB310F8C18A5BE47BBF17D1814F208CC19CE032C8F71B83DD6`
