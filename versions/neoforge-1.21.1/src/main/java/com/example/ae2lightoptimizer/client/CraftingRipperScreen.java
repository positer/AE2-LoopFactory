package com.example.ae2lightoptimizer.client;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.example.ae2lightoptimizer.menu.CraftingRipperMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** AE2 supplies the toolbar, priority submenu and native pattern rendering. */
public final class CraftingRipperScreen extends PatternProviderScreen<CraftingRipperMenu> {
    private final appeng.client.gui.widgets.Scrollbar patternScroll = new appeng.client.gui.widgets.Scrollbar(appeng.client.gui.widgets.Scrollbar.BIG);
    public CraftingRipperScreen(CraftingRipperMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        widgets.add("patternScroll", patternScroll);
        var existing = (com.example.ae2lightoptimizer.mixin.WidgetContainerAccessor) widgets;
        if (!existing.ae2lightoptimizer$getWidgets().containsKey("upgrades")
                && !existing.ae2lightoptimizer$getCompositeWidgets().containsKey("upgrades")) {
            widgets.add("upgrades", new appeng.client.gui.widgets.UpgradesPanel(
                    menu.getSlots(SlotSemantics.UPGRADE), () -> appeng.api.upgrades.Upgrades.getTooltipLinesForMachine(
                            menu.getRipperUpgrades().getUpgradableItem())));
        }
    }

    @Override public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
            int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        // Reference the native Pattern Access Terminal track: cap, repeatable center, bottom cap.
        // The track extends one pixel past each end of AE2's 70-pixel scrollbar bounds.
        var track = appeng.client.gui.style.Blitter.texture(appeng.core.AppEng.makeId("textures/guis/patternaccessterminal.png"));
        track.src(178, 17, 6, 3).dest(offsetX + 179, offsetY + 44, 6, 3).blit(graphics);
        track.src(178, 53, 6, 1).dest(offsetX + 179, offsetY + 47, 6, 68).blit(graphics);
        track.src(178, 106, 6, 1).dest(offsetX + 179, offsetY + 115, 6, 1).blit(graphics);
        // Generated backgrounds contain no baked slot frames. AE2's native upgrade panel
        // paints its own frames; all remaining inventory groups use AE2's slot sprite here.
        for (var semantic : java.util.List.of(SlotSemantics.ENCODED_PATTERN,
                SlotSemantics.PLAYER_INVENTORY, SlotSemantics.PLAYER_HOTBAR)) {
            for (var slot : menu.getSlots(semantic)) {
                if (slot.isActive()) {
                    appeng.client.gui.Icon.SLOT_BACKGROUND.getBlitter()
                            .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                            .blit(graphics);
                }
            }
        }
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        patternScroll.setRange(0, Math.max(0, menu.patternRows - 4), 1);
        if (patternScroll.getCurrentScroll() != menu.requestedRow())
            menu.requestPatternRow(patternScroll.getCurrentScroll());
    }
    private boolean overPatterns(double x, double y) {
        return x >= leftPos + 7 && x < leftPos + 170 && y >= topPos + 44 && y < topPos + 117;
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (overPatterns(x, y)) {
            boolean moved = patternScroll.onMouseWheel(new appeng.client.Point((int)x, (int)y), dy);
            if (moved) menu.requestPatternRow(patternScroll.getCurrentScroll());
            return moved;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (overPatterns(x, y)) {
            if (patternScroll.getCurrentScroll() != menu.requestedRow()) menu.requestPatternRow(patternScroll.getCurrentScroll());
            if (menu.isScrollPending()) return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        return (menu.isScrollPending() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) || super.keyPressed(key, scan, modifiers);
    }

}
