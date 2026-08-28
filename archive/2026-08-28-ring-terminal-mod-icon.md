# 2026-08-28 Ring-terminal mod icon

## Request

Render the recipe ring solver terminal as a three-dimensional block, add a border, and use it as the mod icon.

## Implementation

- Extended the deterministic texture generator with a 64x64 transparent isometric renderer.
- Affine-mapped the existing connected ring-terminal 16x16 texture onto the top, left, and right cube faces.
- Added a dark steel silhouette border, internal metallic edge rails, face shading, and a restrained cyan top accent.
- Kept nearest-neighbor sampling and disabled smoothing so the source texture remains crisp.
- Generated the same root-level `ae2lightoptimizer.png` for both maintained versions.
- Added `logoFile="ae2lightoptimizer.png"` to both NeoForge mod descriptors.
- Added version contracts for icon packaging and metadata binding.

## Verification

- Both icon files are 64x64 RGBA PNGs with 1,583 fully transparent background pixels.
- The two source icons are pixel-identical: SHA-256 `2A6BC66D299A3F5CF542622987F51D8DB5F0E96957CF237418CEA00EAC5CC779`.
- Both Minecraft texture audits report zero issues.
- Both built JARs contain the icon and resolved `logoFile` declaration.
- 54 tests pass in each generation with zero failures and zero errors.
- Both complete generation builds pass.
- Both PCL instances were refreshed without changing saves.
- Build and deployed JAR hashes match:
  - 1.21.1: `58D82E3D5C791B5CFCD8A3A5234D2A6EDEC036AE2C393A3ED9BB35BD0EA73F54`
  - 26.1.2: `5F7A642687478BB407C6D21AA299F256D11FFBE466DF2B063D7E9DBEE989D2BE`
