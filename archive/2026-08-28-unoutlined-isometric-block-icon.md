# 2026-08-28 Unoutlined isometric block icon

## Clarification

The 64x64 PNG keeps its complete square viewport frame, but the rendered recipe-ring terminal cube itself must not have an added border.

## Implementation

- Removed the dark silhouette polygons surrounding the cube.
- Removed the grey outer edge, all three seam strokes, and the cyan top-face outline.
- Mapped the connected terminal texture onto three contiguous isometric faces sharing exact vertices.
- Retained only the top-face light and left-face shade required to read the unoutlined material faces as a cube.
- Preserved the separate five-layer square viewport frame and transparent interior.

## Verification

- Both source icons are pixel-identical: SHA-256 `AF9FDEF32E420086C5BEED0E18B3F48655894EEBEE2DDC8C1CEDA94A734341DF`.
- Both Minecraft texture audits report zero issues.
- 54 tests pass in each generation with zero failures and zero errors.
- Both complete generation builds pass.
- Both PCL instances were refreshed without changing saves.
- Build and deployed JAR hashes match:
  - 1.21.1: `8267307DA8EDEA8295CD1664B3BDB701AC3EC753AB88B78C18B22829B656B950`
  - 26.1.2: `04A011D14190976FC57C88E85242248E886F3D6BB6C370D99D75BF5709C3134C`
