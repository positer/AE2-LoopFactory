# 2026-08-28 Unbounded scale takeover policy

## Decision

- Removed the fixed live admission ceilings for reachable resource types, pattern nodes, and graph edges from both AE2 adapters.
- Large and complex graphs remain takeover candidates whenever AE2 can enumerate them. Scale alone is no longer a reason to return control to AE2.
- Replaced the graph scale guard/status model with a diagnostic estimator. The T-distinct envelope still reports the unavoidable `Omega(V + E)` traversal cost and minimum reference-storage footprint, but cannot accept or reject a live plan.
- Changed the network planning budget from one fixed ceiling to a budget that grows monotonically with reachable resource and pattern counts. Explicit small budgets remain available to tests for proving dead-ring termination.
- Retained correctness fallbacks for checked arithmetic overflow, dead non-growing rings, unsupported recipe semantics, and schedules that cannot be proven executable before AE2 state mutation.

## Independent ownership

- The Recipe Ring Solver Terminal remains the sole eligibility gate for cyclic SCC planning.
- The Supercomputing Crafting Optimizer Interface remains the sole eligibility gate for acyclic global planning.
- With both online, the same reachable graph is compressed and planned globally without either block losing its independent responsibility.

## Verification

- Minecraft 1.21.1: full rerun build passed; 35 tests, 0 failures, 0 errors; `runData` passed with AE2 19.2.17 and GuideME loaded.
- Minecraft 26.1.2: full rerun build passed; 35 tests, 0 failures, 0 errors; `runClientData` passed with AE2 26.1.10-beta and GuideME loaded.
- The two `Ae2GlobalCraftingOptimizer` sources are byte-identical.
- All four English/Simplified Chinese GuideME pages are byte-identical across versions.
- Both output JARs contain `Ae2GlobalCraftingOptimizer`, `CraftingCalculationMixin`, and four GuideME pages; neither contains `Ae2SinglePatternFastPath`.
- The sibling ImmortalStorage repository still contains its pre-existing modified and untracked files. This task did not write to or reset that repository.

## Physical bound

Removing admission thresholds does not make a T-distinct object graph constant-time or constant-memory. A graph containing `10^12` distinct resources and `10^12` distinct pattern nodes must still be enumerated and represented by AE2 and must produce per-pattern result entries. The implemented optimization targets the dominant repeated-work explosion: T/P-scale quantities and cyclic repetitions remain compressed into checked `long` counts instead of being expanded application by application.
