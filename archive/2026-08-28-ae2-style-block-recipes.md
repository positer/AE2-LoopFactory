# 2026-08-28 AE2-style block recipes

## Design

- Recipe Ring Solver Terminal: `FCA / CUC / ACF`, using four calculation processors, two formation cores, two annihilation cores, and one crafting unit. Opposing input/output cores express the terminal's cyclic material-flow role.
- Supercomputing Crafting Optimizer Interface: `CEC / EAE / CEC`, using four calculation processors, four engineering processors, and one crafting accelerator. Its higher processor cost matches its network-wide acyclic planning and dispatch role.
- Both recipes produce one block and use only AE2 item IDs confirmed present in AE2 19.2.17 and 26.1.10-beta.

## Cross-generation implementation

- Minecraft 1.21.1 uses object-form shaped-recipe ingredients and explicit result count.
- Minecraft 26.1.2 uses string-form shaped-recipe ingredients and the generation's result form.
- JEI discovers both normal crafting recipes automatically.
- All four English/Chinese GuideME pages embed their block recipe through `RecipeFor`.

## Verification

- Added structured Gson assertions for recipe type, all three pattern rows, every ingredient ID, and result ID.
- 56 tests pass in each generation with zero failures and zero errors.
- Both complete builds pass.
- Both real NeoForge/AE2 data environments start successfully and transformed-bytecode takeover verification remains green.
- Both PCL instances were refreshed without changing saves.
- Build and deployed JAR hashes match:
  - 1.21.1: `C860FBE8762326145A2842F8D1C5473EB7ED2C0274282C317715996625B45998`
  - 26.1.2: `E7E777C8BA0DBA5658507770B0136A6BF0E795DD0869C426527B8A30C7C14E67`
