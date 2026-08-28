# 2026-08-28 Recipe Ring Solver Stress Test

- Added the loader-independent recipe-ring solver and its request/result domain types under `shared/`.
- Added a closed-form path for smithing-template-style single-recipe self-growth.
- Added bounded breadth-first search for nested recipe growth, with backward relevance filtering, capped resource vectors, and inventory-dominance pruning.
- Kept `BUDGET_EXHAUSTED` distinct from `NO_GROWTH_PATH` so a bounded search cannot falsely prove impossibility.
- Stress-tested 75 recipes: one productive irreducible three-recipe component plus 24 irrelevant three-recipe dead cycles.
- The stress plan grows one seed to exactly 33 seeds in 48 recipe applications, never selects a dead-cycle recipe, stays below the 5,000-state budget, and passes a step-by-step inventory replay.
- Promoted maximum planned applications from `int` to `long`, matching AE2-scale bulk quantities while retaining an integer state budget for memory control.
- Added a peta-scale (`10^15`) single-recipe case that stays at one explored state and a tera-input/peta-output multi-recipe case mixed with 256 irrelevant three-node SCCs that stays within four explored states.
- Registered `recipe_ring_solver_terminal` independently in both target trees with an AE2 in-world node capability, required channel, 2 AE/t idle use, six-side exposure, placing-player ownership, managed-node lifecycle, and generation-specific persistence.
- Added bilingual names, block/item models, a 16x16 texture, self-drop loot, pickaxe tag, and functional-block creative placement; deliberately added no recipe.
- Migrated the 26.1.2 data run from the removed `data` run type to `clientData`.
- Ran 10 tests per target with zero failures/errors, completed both builds, completed both data-generation launches with AE2 loaded, and passed both texture audits with zero issues.
- Final JAR class majors are 65 (Java 21) and 69 (Java 25). SHA-256: 1.21.1 `A26EC665467F1E2E08DA555FF8D085C0ADC166CF660133308DB8CF2D751020FD`; 26.1.2 `BDB876E2700A35E98BF22F32CF0982F9AE05B4F40B2248C78CCCB0DB2EEED5C9`.
