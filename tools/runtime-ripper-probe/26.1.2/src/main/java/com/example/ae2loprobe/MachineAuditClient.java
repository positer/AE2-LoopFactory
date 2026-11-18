package com.example.ae2loprobe;

/** Drives the actual screen responders and native C2S menu packets, not server-side substitutes. */
public final class MachineAuditClient {
    public static void tick(net.minecraft.client.Minecraft minecraft) {
        if (net.neoforged.fml.ModList.get().isLoaded("ae2cs") && !MachineAudit.crystalSpritesReady) {
            var atlas = minecraft.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.ITEMS);
            for (var name : java.util.List.of("certus_quartz_seed_0", "certus_quartz_seed_1",
                    "certus_quartz_seed_2", "purified_certus_quartz_crystal")) {
                var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("ae2cs", "item/" + name);
                var nativeSprite = atlas.getSprite(id);
                var silver = atlas.getSprite(id.withSuffix("_ae2lt_silver"));
                if (!silver.contents().name().equals(id.withSuffix("_ae2lt_silver"))) return;
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    int pixel = silver.getPixelRGBA(0, x, y);
                    if ((pixel >>> 24) != (nativeSprite.getPixelRGBA(0, x, y) >>> 24)) return;
                    if ((pixel >>> 24) != 0 && ((pixel & 255) != ((pixel >> 8) & 255)
                            || (pixel & 255) != ((pixel >> 16) & 255))) return;
                }
            }
            MachineAudit.crystalSpritesReady = true;
        }
        if (!(minecraft.screen instanceof com.example.ae2lightoptimizer.client.LoopTickerScreen screen)) return;
        if (screen.getMenu().maxRate < 1) return;
        MachineAudit.uiLocale = minecraft.options.languageCode;
        MachineAudit.uiTitle = screen.getStyle().getText().get("dialog_title").getText().getString();
        try {
            if (MachineAudit.uiAction.equals("number")) {
                var field = screen.getClass().getDeclaredField("input"); field.setAccessible(true);
                var input = (net.minecraft.client.gui.components.EditBox)field.get(screen);
                input.setFocused(true); input.setValue("2");
                MachineAudit.uiAction = "number_sent";
            } else if (MachineAudit.uiAction.equals("slider")) {
                var field = screen.getClass().getDeclaredField("slider"); field.setAccessible(true);
                var slider = (com.example.ae2lightoptimizer.client.LoopRateSlider)field.get(screen);
                var bounds = slider.getBounds();
                var point = new appeng.client.Point(bounds.getX()+bounds.getWidth()-1,bounds.getY()+5);
                for (int i=0;i<64 && slider.getValue()<screen.getMenu().maxRate;i++) {
                    slider.onMouseDown(point,0); slider.onMouseUp(point,0);
                }
                var inputField=screen.getClass().getDeclaredField("input"); inputField.setAccessible(true);
                MachineAudit.sliderUpdatedInput = ((net.minecraft.client.gui.components.EditBox)inputField.get(screen))
                        .getValue().equals(Integer.toString(screen.getMenu().maxRate));
                MachineAudit.uiAction = "slider_sent";
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native machine UI driver failed",failure); }
    }
}
