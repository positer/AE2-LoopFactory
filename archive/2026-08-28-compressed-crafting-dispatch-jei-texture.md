# 2026-08-28 Compressed crafting dispatch, optimizer texture, and JEI deployment

## Reported defect

Cyclic plans were solved correctly but AE2 received only aggregate `patternTimes`. `ExecutingCraftingJob` copied those totals into a `HashMap`, and `CraftingCpuLogic` iterated that unordered map, discarding the planner's executable cyclic schedule.

## Execution fix

- Attached the shared planner's ordered compressed batches to handled AE2 `CraftingPlan` instances.
- Marked cyclic schedules as ring-terminal owned and acyclic schedules as optimizer-interface owned.
- Installed a CPU execution cursor that exposes only the current batch, caps a call by the batch remainder, and advances only by successful AE2 pushes.
- Kept AE2's provider, input, power, waiting-output, security, cancellation, and completion behavior.
- Persisted schedule owner, pattern definitions, batch index, and remaining `long` repetitions through the 1.21.1 NBT and 26.1.2 `ValueInput`/`ValueOutput` APIs.
- Left every unmarked/original AE2 job on the original task-map dispatch path.

## Scale and regression evidence

- Added `CompressedBatchCursor` tests for alternating batches and a `10^15` repetition batch without expansion.
- Both generations pass 43 tests with zero failures and errors.
- Enhanced `tools/verify_global_takeover.ps1` exported and disassembled `CraftingCalculation`, `CraftingPlan`, `ExecutingCraftingJob`, and `CraftingCpuLogic` for both generations.
- Verified transformed bytecode contains calculation takeover, schedule attachment, current-batch selection, operation limiting, cursor advancement, and persistence calls.

## Texture change

- Preserved both recipe-ring terminal textures unchanged.
- Replaced only the optimizer offline/connected textures with a four-way AE2-style compute core.
- Added a generation-time pixel assertion proving invariance under 90-degree rotation.
- Connected state uses cyan-white lanes and four amber pulses; offline state retains the geometry with dim colors.
- The Minecraft texture asset audit reports zero issues for both generations.

## PCL deployment

- Rebuilt both clean instances with exact four-JAR whitelists: AE2, GuideME, JEI, and the matching addon.
- Added JEI 19.37.0.363 for 1.21.1 and JEI 29.21.0.68 for 26.1.2 from pinned author-Maven URLs.
- Did not copy any dependency, configuration, save, or metadata from ImmortalStorage.
- Both instances retain `VersionArgumentIndieV2:True`, contain zero filesystem links and zero forbidden ImmortalStorage artifacts.
- Recreate semantics removed prior mutable target-instance state before deployment.

## Final addon hashes

- 1.21.1: `7DCC6332F8C06169161B4D1909557D92C094F95D0F44D65E05BFCC9B6E08743E`
- 26.1.2: `3863AFEF863C8C993F900A505CE3E4326B8B1608106A8F961967575697DBD62B`
