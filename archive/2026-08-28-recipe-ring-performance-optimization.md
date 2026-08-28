# 2026-08-28 Recipe Ring Performance Optimization

- Replaced repeated whole-list backward relevance scans with an output-resource producer index and queue traversal. Preprocessing is now `O(total recipe outputs + relevant dependency edges)`.
- Replaced per-resource whole-recipe cap scans with one aggregation pass over relevant recipe inputs and outputs.
- Compiled recipes now store only input and touched-resource indexes/amounts, reducing compiled transition storage from dense `O(recipe count * resource count)` to `O(recipe entries)`.
- Recipe applicability checks scan real inputs only, and transition application scans real touched resources only.
- Combined candidate rejection and dominated-state removal into one dominance-frontier iterator pass.
- Added `filtersTenThousandIrrelevantRecipesBeforeSearch`: 10,000 disconnected recipes plus one relevant growth recipe must solve in one application and no more than two explored states.
- Re-ran both complete builds with task reruns. Minecraft 1.21.1 completed successfully in 27 seconds; Minecraft 26.1.2 completed successfully in 29 seconds.
- Each target reports 9 shared solver tests plus 2 version-local terminal contract tests, for 11 tests with zero failures and zero errors.
- `git diff --check` passed, and all imports in `RecipeRingSolver.java` are used.
- The closed-form single-recipe path remains `O(recipe resource entries)` and handles peta-scale counts in one explored state. Multi-recipe search remains bounded BFS for minimum application count; its Pareto frontier can still grow exponentially in adversarial graphs, so this work does not claim a mathematical absolute optimum.
