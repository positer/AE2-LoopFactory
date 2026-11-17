package com.example.ae2lightoptimizer.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import com.example.ae2lightoptimizer.menu.LoopTickerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** AE2's native scrollbar, text field, upgrade panel and inventory chrome. */
public final class LoopTickerScreen extends AEBaseScreen<LoopTickerMenu> {
    private final LoopRateSlider slider = new LoopRateSlider(this::sendRate);
    private final AETextField input;
    private boolean refreshing;
    private int requestedRate;
    private boolean pending;

    public LoopTickerScreen(LoopTickerMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        widgets.add("rateSlider", slider);
        widgets.add("upgrades", new appeng.client.gui.widgets.UpgradesPanel(
                menu.getSlots(appeng.menu.SlotSemantics.UPGRADE),
                () -> appeng.api.upgrades.Upgrades.getTooltipLinesForMachine(menu.getTickerUpgrades().getUpgradableItem())));
        input = new AETextField(style, font, 0, 0, 30, 9);
        // AETextField draws AE2's own 12px frame. The vanilla EditBox border would cover it.
        input.setBordered(false);
        input.setMaxLength(2);
        input.setFilter(value -> value.isEmpty() || value.matches("[0-9]{1,2}"));
        input.setResponder(value -> {
            if (!refreshing && !value.isEmpty()) {
                int rate = Integer.parseInt(value);
                if (rate >= 1 && rate <= menu.maxRate) sendRate(rate);
            }
        });
    }

    @Override protected void init() {
        super.init();
        input.setPosition(leftPos + 126, topPos + 27);
        addRenderableWidget(input);
    }

    @Override public void drawBG(net.minecraft.client.gui.GuiGraphics graphics, int x, int y,
                                int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTicks);
        for (var semantic : java.util.List.of(appeng.menu.SlotSemantics.PLAYER_INVENTORY,
                                             appeng.menu.SlotSemantics.PLAYER_HOTBAR)) {
            for (var slot : menu.getSlots(semantic)) {
                appeng.client.gui.Icon.SLOT_BACKGROUND.getBlitter().dest(x+slot.x-1,y+slot.y-1).blit(graphics);
            }
        }
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        int max = Math.max(1, menu.maxRate);
        slider.setRange(1,max);
        if (pending && (menu.rate == requestedRate || requestedRate > max)) pending = false;
        if (!pending) slider.setValue(Math.max(1, menu.rate));
        if (!input.isFocused()) {
            refreshing = true;
            input.setValue(Integer.toString(pending ? requestedRate : Math.max(1, menu.rate)));
            refreshing = false;
        }
    }

    private void sendRate(int value) {
        if (minecraft != null && minecraft.gameMode != null && value >= 1 && value <= menu.maxRate) {
            requestedRate = value; pending = true;
            refreshing = true;
            try {
                String text = Integer.toString(value);
                if (!input.getValue().equals(text)) input.setValue(text);
            } finally { refreshing = false; }
            slider.setValue(value);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,value);
        }
    }

}
