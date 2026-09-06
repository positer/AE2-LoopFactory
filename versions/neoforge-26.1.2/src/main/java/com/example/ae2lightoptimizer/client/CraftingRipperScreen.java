package com.example.ae2lightoptimizer.client;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.example.ae2lightoptimizer.menu.CraftingRipperMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** AE2 supplies the toolbar, priority submenu and native pattern rendering. */
public final class CraftingRipperScreen extends PatternProviderScreen<CraftingRipperMenu> {
    public CraftingRipperScreen(CraftingRipperMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        var existing = (com.example.ae2lightoptimizer.mixin.WidgetContainerAccessor) widgets;
        if (!existing.ae2lightoptimizer$getWidgets().containsKey("upgrades")
                && !existing.ae2lightoptimizer$getCompositeWidgets().containsKey("upgrades")) {
            widgets.add("upgrades", new appeng.client.gui.widgets.UpgradesPanel(
                    menu.getSlots(SlotSemantics.UPGRADE), () -> appeng.api.upgrades.Upgrades.getTooltipLinesForMachine(
                            menu.getRipperUpgrades().getUpgradableItem())));
        }
    }

    @Override public void drawBG(GuiGraphicsExtractor graphics, int offsetX, int offsetY,
            int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        // Generated backgrounds contain no baked slot frames. AE2's native upgrade panel
        // paints its own frames; all remaining inventory groups use AE2's slot sprite here.
        for (var semantic : java.util.List.of(SlotSemantics.ENCODED_PATTERN,
                SlotSemantics.PLAYER_INVENTORY, SlotSemantics.PLAYER_HOTBAR)) {
            for (var slot : menu.getSlots(semantic)) {
                if (slot.isActive()) {
                    appeng.client.gui.style.Blitter.icon(appeng.util.Icon.SLOT_BACKGROUND)
                            .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                            .blit(graphics);
                }
            }
        }
    }

    @Override public void drawFG(GuiGraphicsExtractor graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(graphics, offsetX, offsetY, mouseX, mouseY);
        if (menu.loopCardInstalled) {
            for (var slot : menu.getSlots(SlotSemantics.ENCODED_PATTERN)) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x99606060);
            }
        }
    }
}
