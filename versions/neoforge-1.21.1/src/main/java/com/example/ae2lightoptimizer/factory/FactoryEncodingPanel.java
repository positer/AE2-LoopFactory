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
    @Override public void readFromNBT(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries); draft=tag.contains("FactoryDraft")?FactoryPatternData.TEXT.parse(net.minecraft.nbt.NbtOps.INSTANCE,tag.get("FactoryDraft")).getOrThrow():"";
    }
    @Override public void writeToNBT(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries); tag.put("FactoryDraft",FactoryPatternData.TEXT.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,draft).getOrThrow());
    }
    private static final net.minecraft.resources.ResourceLocation MODEL_OFF = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2lightoptimizer", "part/loop_factory_panel_off");
    private static final net.minecraft.resources.ResourceLocation MODEL_ON = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2lightoptimizer", "part/loop_factory_panel_on");
    public static final appeng.api.parts.IPartModel OFF = new appeng.parts.PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final appeng.api.parts.IPartModel ON = new appeng.parts.PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final appeng.api.parts.IPartModel ACTIVE = new appeng.parts.PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);
    @Override public appeng.api.parts.IPartModel getStaticModels() { return isActive() ? ACTIVE : isPowered() ? ON : OFF; }
}
