package com.example.ae2lightoptimizer.factory;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.SlotSemantics;
import appeng.parts.encoding.EncodingMode;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;

public final class FactoryPanelRecipeMenu extends PatternEncodingTermMenu {
    public static final net.neoforged.neoforge.registries.DeferredHolder<MenuType<?>, MenuType<FactoryPanelRecipeMenu>> TYPE = FactoryEditorMenu.MENUS.register("factory_panel_recipe",
        () -> MenuTypeBuilder.create(FactoryPanelRecipeMenu::new, FactoryEncodingPanel.class)
        .buildUnregistered(ResourceLocation.fromNamespaceAndPath("ae2lightoptimizer", "factory_panel_recipe")));
    private final FactoryEncodingPanel panel;
    public FactoryPanelRecipeMenu(MenuType<?> type, int id, Inventory inventory, FactoryEncodingPanel panel) {
        super(type, id, inventory, panel, true); this.panel=panel; this.mode=EncodingMode.PROCESSING;
        hideUnsupportedModes();
        registerClientAction("factoryCodePage", this::codePage);
    }
    @Override public void setMode(EncodingMode mode) {
        this.mode = EncodingMode.PROCESSING;
        super.setMode(EncodingMode.PROCESSING);
    }
    @Override public void onServerDataSync(ShortSet slots) {
        super.onServerDataSync(slots);
        hideUnsupportedModes();
    }
    private void hideUnsupportedModes() {
        for(var semantic:java.util.List.of(SlotSemantics.CRAFTING_GRID,SlotSemantics.CRAFTING_RESULT,
                SlotSemantics.SMITHING_TABLE_TEMPLATE,SlotSemantics.SMITHING_TABLE_BASE,
                SlotSemantics.SMITHING_TABLE_ADDITION,SlotSemantics.SMITHING_TABLE_RESULT,
                SlotSemantics.STONECUTTING_INPUT))
            for(var slot:getSlots(semantic)) {
                if(slot instanceof appeng.menu.slot.AppEngSlot appSlot)appSlot.setActive(false);
                else if(slot instanceof appeng.menu.slot.PatternTermSlot patternSlot)patternSlot.setActive(false);
            }
    }
    public void codePage() {
        if(isClientSide()){sendClientAction("factoryCodePage");return;}
        if(!stillValid(getPlayer()))return;
        var stack=panel.factoryPatterns().getStackInSlot(0);
        if(stack.getItem() instanceof FactoryPatternItem) panel.setFactoryDraft(FactoryPatternData.get(stack).code());
        MenuOpener.open(FactoryEditorMenu.TYPE.get(), getPlayer(), MenuLocators.forPart(panel));
    }
}
