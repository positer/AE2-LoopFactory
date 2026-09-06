package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/** Optional JEI discovery only; the base mod never loads this class when JEI is absent. */
@JeiPlugin
public final class CraftingRipperJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(Ae2LightOptimizer.MOD_ID, "crafting_ripper_ui");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // JEI anchors navigation to guiRight; extra areas alone only exclude ingredient cells.
        registration.addGuiScreenHandler(CraftingRipperScreen.class, screen -> {
            int left = screen.getGuiLeft();
            int right = left + screen.getXSize();
            for (var area : screen.getExclusionZones()) {
                right = Math.max(right, area.getX() + area.getWidth());
            }
            return new RipperGuiProperties(screen.getClass(), left, screen.getGuiTop(),
                    right - left, screen.getYSize(), screen.width, screen.height);
        });
        registration.addGuiContainerHandler(CraftingRipperScreen.class,
                new IGuiContainerHandler<CraftingRipperScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(CraftingRipperScreen screen) {
                        return screen.getExclusionZones();
                    }
                });
    }

    private record RipperGuiProperties(Class<? extends Screen> screenClass, int guiLeft, int guiTop,
            int guiXSize, int guiYSize, int screenWidth, int screenHeight) implements IGuiProperties { }
}
