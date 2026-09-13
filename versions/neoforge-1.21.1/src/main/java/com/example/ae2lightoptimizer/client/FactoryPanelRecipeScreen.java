package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.factory.FactoryPanelRecipeMenu;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.TabButton;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;

public final class FactoryPanelRecipeScreen extends PatternEncodingTermScreen<FactoryPanelRecipeMenu> {
    public FactoryPanelRecipeScreen(FactoryPanelRecipeMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        var codeTab = new TabButton(appeng.client.gui.Icon.TAB_CRAFTING,
                Component.translatable("gui.ae2lightoptimizer.factory.code_page"), button -> menu.codePage());
        codeTab.setStyle(TabButton.Style.HORIZONTAL);
        widgets.add("factoryCodePage", codeTab);
    }
}
