# Loop Factory Blockbench assets

Produced in Blockbench 5.1.6 using tools/blockbench/ae2lf_assets.js. Each version folder contains six editable .bbmodel projects with embedded textures. Blockbench exported production models and PNGs into each independent adapter.

Provider conversion: only yellow/orange pixels become neutral grey. Native 16x16 dimensions, alpha, other source pixels and geometry remain unchanged. The earlier yellow concept is rejected. The phone is a transparent 32x32 grey-black smartphone with eight broken blue code rows, earpiece, camera and side button.

All six contents have production registry/model wiring. The phone is the native handheld code editor; the panel is a dyeable AE2 multipart with recipe/code pages and provider upload. The panel follows AE2's native terminal composition: `display_base` supplies the opaque housing, while the editable bright/medium/dark masks only rearrange the lit pixels from AE2's own `pattern_encoding_terminal_*` layers. The runtime models are `models/part/loop_factory_panel_off.json` and `_on.json`; both use tint indices 1/2/3 so native AE2 part dyeing remains authoritative.

Provider and pattern derive from separately pinned AE2 19.2.17 / 26.1.10-beta textures. Terminal and cable derive from TeamDman/SuperFactoryManager classic textures: https://github.com/TeamDman/SuperFactoryManager/tree/1.19.2/platform/minecraft/src/main/resources/pack/classic/assets/sfm/textures/block . SFM-LICENSE.txt preserves its license; no SFM code or runtime dependency is bundled.

Reproduce in desktop Blockbench: load the local plugin, then Tools > AE2LF: Export Factory Assets. The root path is explicit in the plugin. No network calls or output outside this repository occur. Reference PNGs are preserved under references/ for reproduction; the earlier copies remain in the dated debug archive. blockbench-export.json records the tool version and outputs.


Provider correction: the editable cube is oriented upward, with arrow textures on four lateral faces, the subnet front on top and an independent recolored native back on the bottom. Runtime blockstates use AE2's oriented-provider rotations. The rear keeps every non-yellow pixel and alpha; only the native yellow palette becomes neutral grey. Projects, export script and runtime JSON are synchronized. The correction was applied to the editable project data and exports without reopening a visible Blockbench window during background QA.

Panel correction: AE2's native mask union supplies the 100-pixel colored face footprint. The `bright` layer carries that union as an opaque background, the `medium` layer is transparent, and the `dark` layer overlays the 32-pixel Recipe Ring Solver core with alpha 96/160/255. The item model is the native `ae2:item/display_base` pattern-terminal structure, substituting only `front_bright`, `front_medium`, and `front_dark`; there is no custom item base, empty texture, or GUI lighting override. 1.21.1 explicitly registers the panel's fluix variants with opaque alpha, and 26.1.2 uses AE2's native tint descriptor.

The code-page switch uses AE2's native `TabButton` (`Style.HORIZONTAL`, `TAB_CRAFTING`, 22×22) at the original crafting tab coordinate, parallel to the retained processing tab.
