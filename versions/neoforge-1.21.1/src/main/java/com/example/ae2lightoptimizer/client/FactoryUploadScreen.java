package com.example.ae2lightoptimizer.client;

import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.widgets.AE2Button;
import com.example.ae2lightoptimizer.factory.FactoryEditorMenu;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Native AE2 dialog chrome; selection still submits only the server-provided UUID. */
public final class FactoryUploadScreen extends Screen {
    private final Screen parent;
    private final FactoryEditorMenu menu;
    private int page;
    private String observedProviders = "";
    private int observedPage = -1;
    private int left, top, panelWidth, panelHeight;

    public FactoryUploadScreen(Screen parent, FactoryEditorMenu menu) {
        super(Component.translatable("gui.ae2lightoptimizer.factory.upload"));
        this.parent = parent;
        this.menu = menu;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void tick() {
        super.tick();
        if (!observedProviders.equals(menu.providers) || observedPage != menu.providerPage) {
            page = 0;
            rebuildWidgets();
        }
    }

    @Override protected void init() {
        observedProviders = menu.providers;
        observedPage = menu.providerPage;
        panelWidth = Math.min(320, width - 16);
        String[] rows = menu.providers.isEmpty() ? new String[0] : menu.providers.split("\n");
        int maximumHeight = Math.min(240, height - 16);
        int capacity = Math.max(1, (maximumHeight - 58) / 22);
        panelHeight = Math.min(maximumHeight, 58 + Math.max(1, Math.min(rows.length, capacity)) * 22);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        page = Math.min(page, Math.max(0, (rows.length - 1) / capacity));
        for (int i = page * capacity; i < Math.min(rows.length, (page + 1) * capacity); i++) {
            String[] row = rows[i].split("[|]", 4);
            Component label = row.length == 4
                    ? Component.translatable("gui.ae2lightoptimizer.factory.provider_choice",
                            row[1].equals(row[0]) ? Component.translatable("block.ae2lightoptimizer.loop_factory_pattern_provider") : Component.literal(row[1]), row[2],
                            Component.translatable("gui.ae2lightoptimizer.factory.direction." + row[3]))
                    : Component.literal(row[0]);
            var button = new AE2Button(left + 8, top + 25 + (i % capacity) * 22, panelWidth - 16, 20,
                    label, clicked -> { menu.upload(row[0]); minecraft.setScreen(parent); });
            button.setTooltip(Tooltip.create(label));
            addRenderableWidget(button);
        }
        if (rows.length == 0) {
            var empty = new AE2Button(left + 8, top + 25, panelWidth - 16, 20,
                    Component.translatable("gui.ae2lightoptimizer.factory.no_providers"), clicked -> {});
            empty.active = false;
            addRenderableWidget(empty);
        }
        int buttonWidth = (panelWidth - 20) / 2;
        var next = new AE2Button(left + 8, top + panelHeight - 28, buttonWidth, 20,
                Component.translatable("gui.ae2lightoptimizer.factory.next"), clicked -> {
                    if ((page + 1) * capacity < rows.length) { page++; rebuildWidgets(); }
                    else { page = 0; menu.nextProviders(); if (menu.providerPages == 1) rebuildWidgets(); }
                });
        next.active = rows.length > capacity || menu.providerPages > 1;
        addRenderableWidget(next);
        addRenderableWidget(new AE2Button(left + panelWidth - 8 - buttonWidth, top + panelHeight - 28,
                buttonWidth, 20, Component.translatable("gui.ae2lightoptimizer.factory.back"),
                clicked -> minecraft.setScreen(parent)));
    }

    @Override public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float delta) {
        BackgroundGenerator.draw(panelWidth, panelHeight, g, left, top);
        g.drawString(font, title, left + 8, top + 8, 0xff404040, false);
        super.render(g, mx, my, delta);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
