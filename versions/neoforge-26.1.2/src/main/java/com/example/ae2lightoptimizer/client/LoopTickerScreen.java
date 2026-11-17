package com.example.ae2lightoptimizer.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import com.example.ae2lightoptimizer.menu.LoopTickerMenu;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** AE2 screen with a horizontal slider that sets the accelerator rate. */
public final class LoopTickerScreen extends AEBaseScreen<LoopTickerMenu> {
    private final RateSlider slider;

    public LoopTickerScreen(LoopTickerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.slider = new RateSlider();
        addRenderableWidget(slider);
    }

    @Override
    protected void init() {
        super.init();
        slider.setPosition(leftPos + 8, topPos + 20);
        slider.setWidth(150);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        slider.refresh(menu.rate, Math.max(1, menu.maxRate));
    }

    private final class RateSlider extends AbstractSliderButton {
        private int max = 4;

        RateSlider() {
            super(0, 0, 150, 20, Component.empty(), 0.0);
        }

        void refresh(int rate, int maxRate) {
            max = maxRate;
            double value = (double) (rate - 1) / Math.max(1, maxRate - 1);
            this.value = Math.max(0.0, Math.min(1.0, value));
            updateMessage();
        }

        private int currentRate() {
            return 1 + (int) Math.round(value * (max - 1));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("x" + currentRate() + " / " + max));
        }

        @Override
        protected void applyValue() {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, currentRate());
            }
        }
    }
}
