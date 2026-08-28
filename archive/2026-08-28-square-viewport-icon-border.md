# 2026-08-28 Square viewport icon border

## Clarification

The requested border belongs to the square PNG icon viewport, not merely to the isometric block silhouette.

## Correction

- Shrunk the rendered ring-terminal cube into an interior safe area.
- Added a complete four-sided frame directly on the 64x64 PNG canvas.
- Built the frame from five pixel-exact layers: two dark outer steel lines, one metal-grey line, one cyan signal line, and one dark inner line.
- Kept the area between the square frame and cube transparent.
- Replaced `Graphics.DrawRectangle`, whose half-pixel behavior omitted the bottom and right edges, with explicit per-pixel drawing for all four sides and corners.
- Strengthened both generation contracts to require every pixel on the top, bottom, left, and right outer edges to be opaque.

## Verification

- Both source icons are pixel-identical: SHA-256 `E4AAF34FA4CAADB33FCC153F81B68F9C1F01688CDDA3C2C89601029136233CCB`.
- All four outer edges are fully opaque.
- The framed interior retains 1,242 fully transparent pixels.
- Both Minecraft texture audits report zero issues.
- 54 tests pass in each generation with zero failures and zero errors.
- Both complete generation builds pass.
- Both PCL instances were refreshed without changing saves.
- Build and deployed JAR hashes match:
  - 1.21.1: `3E2A118D9B58300561239B78935FE82B23403A8AEA7E396E90DE1290721C7CF5`
  - 26.1.2: `E5A7B123DBCD2560A1217E41190B2EFF1B9828EE58F0E4FD32F7D83D7A563120`
