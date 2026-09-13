package com.example.ae2lightoptimizer.factory;

import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.api.parts.IPartItem;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

/** Native AE2 multipart pattern terminal: recipe and code pages share the same physical output slot. */
public final class FactoryEncodingPanel extends PatternEncodingTerminalPart implements FactoryEditorHost {
    private String draft = "";
    public FactoryEncodingPanel(IPartItem<?> item) { super(item); }
    @Override public InternalInventory factoryPatterns() { return getLogic().getEncodedPatternInv(); }
    @Override public String factoryDraft() { return draft; }
    @Override public void setFactoryDraft(String value) { draft = value; markForSave(); }
    @Override public IGrid editorGrid() { return getMainNode().getGrid(); }
    @Override public String editorTitleKey() { return "item.ae2lightoptimizer.loop_factory_pattern_encoding_panel"; }
    @Override public MenuType<?> getMenuType(Player player) { return FactoryPanelRecipeMenu.TYPE.get(); }
    @Override public void readFromNBT(net.minecraft.world.level.storage.ValueInput tag) {
        super.readFromNBT(tag); draft=tag.read("FactoryDraft",FactoryPatternData.TEXT).orElse("");
    }
    @Override public void writeToNBT(net.minecraft.world.level.storage.ValueOutput tag) {
        super.writeToNBT(tag); tag.store("FactoryDraft",FactoryPatternData.TEXT,draft);
    }

}
