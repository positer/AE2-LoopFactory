package com.example.ae2lightoptimizer.omni;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.util.AECableType;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;

public final class OmniConnectorBlockEntity extends AENetworkedBlockEntity implements OmniNetworkNode {
    private String linkId = "";
    public OmniConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(OmniContent.ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.DENSE_CAPACITY).setIdlePowerUsage(0);
    }
    public String linkId() { return linkId; }
    public void selectLink(String id) {
        if (id.equals(linkId)) return;
        OmniNetworks.remove(this);
        linkId = id;
        saveChanges(); markForUpdate();
        if (getLevel() != null && getLevel().getServer() != null && getMainNode().getNode() != null)
            OmniNetworks.get(getLevel().getServer()).attach(this);
    }
    public boolean linked() {
        return getLevel() != null && getLevel().getServer() != null && OmniNetworks.get(getLevel().getServer()).linked(this);
    }
    @Override public void onReady() { super.onReady(); if (getLevel().getServer() != null) OmniNetworks.get(getLevel().getServer()).attach(this); }
    @Override public void setRemoved() { OmniNetworks.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { OmniNetworks.remove(this); super.onChunkUnloaded(); }
    @Override public Set<Direction> getGridConnectableSides(BlockOrientation orientation) { return Set.of(orientation.getSide(RelativeSide.BACK)); }
    @Override public AECableType getCableConnectionType(Direction direction) { return AECableType.DENSE_SMART; }
    @Override public void onMainNodeStateChanged(IGridNodeListener.State state) { markForUpdate(); }
    public void open(Player player) { MenuOpener.open(OmniConnectorMenu.TYPE.get(), player, MenuLocators.forBlockEntity(this)); }
    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putString("OmniLink", linkId);
    }
    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries); linkId = tag.getString("OmniLink");
    }
    @Override public void exportSettings(appeng.util.SettingsFrom from, DataComponentMap.Builder builder, Player player) {
        super.exportSettings(from, builder, player);
        if (from == appeng.util.SettingsFrom.DISMANTLE_ITEM && !linkId.isEmpty()) {
            var tag = new CompoundTag(); tag.putString("AE2LFOmniLink", linkId);
            builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
    @Override public void importSettings(appeng.util.SettingsFrom from, DataComponentMap components, Player player) {
        super.importSettings(from, components, player);
        var custom = components.get(DataComponents.CUSTOM_DATA);
        if (from == appeng.util.SettingsFrom.DISMANTLE_ITEM && custom != null) selectLink(custom.copyTag().getString("AE2LFOmniLink"));
    }
}
