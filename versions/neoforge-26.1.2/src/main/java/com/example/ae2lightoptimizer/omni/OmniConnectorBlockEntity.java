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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;

public final class OmniConnectorBlockEntity extends AENetworkedBlockEntity implements OmniNetworkNode {
    private String linkId = "";
    private appeng.api.networking.IGridNode factoryNode;
    private appeng.api.networking.IGridConnection factoryLink;
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
    /** True while a formed Loop Factory structure is bridged to this connector. */
    public boolean factoryLinked() { return factoryLink != null; }
    /**
     * Bridges the channel network to the machine below when the structure is formed. The machine's
     * MAIN node is used; a provider's subnet is never joined, because the subnet is the factory side.
     */
    public void refreshFactoryLink() {
        var level = getLevel();
        if (level == null || level.isClientSide()) return;
        var target = OmniFactoryWireless.target(level, getBlockPos());
        var self = getMainNode().getNode();
        if (target == null || self == null) { dropFactoryLink(); markForUpdate(); return; }
        var main = target.getMainNode().getNode();
        if (main == null) { dropFactoryLink(); markForUpdate(); return; }
        // The connector rests on the machine, so AE2 may already have joined it to that machine's node
        // (or, for a provider, to the subnet on its up face). Sever every physical edge to the machine:
        // the channel network must be the only carrier, and a provider's subnet is never the bridge.
        var subnet = target.isProvider() ? target.getGridNode(net.minecraft.core.Direction.UP) : null;
        for (var connection : java.util.List.copyOf(self.getConnections())) {
            var other = connection.getOtherSide(self);
            if (other == main || (subnet != null && other == subnet)) {
                try { connection.destroy(); } catch (RuntimeException ignored) { }
            }
        }
        if (factoryLink == null || factoryNode != main) {
            dropFactoryLink();
            factoryNode = main;
            try { factoryLink = appeng.api.networking.GridHelper.createConnection(self, main); }
            catch (RuntimeException alreadyConnected) { factoryLink = null; }
        }
        markForUpdate();
    }
    private void dropFactoryLink() {
        if (factoryLink != null) {
            try { factoryLink.destroy(); } catch (RuntimeException ignored) { }
            factoryLink = null;
        }
        factoryNode = null;
    }
    @Override public void onReady() {
        super.onReady();
        if (getLevel().getServer() != null) OmniNetworks.get(getLevel().getServer()).attach(this);
        refreshFactoryLink();
    }
    @Override public void setRemoved() { dropFactoryLink(); OmniNetworks.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { dropFactoryLink(); OmniNetworks.remove(this); super.onChunkUnloaded(); }
    @Override public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        // The bottom face is the factory-mounting slot: cabling downward would physically short the
        // machine (and, for a provider, its subnet) before the wireless bridge can be the carrier.
        var back = orientation.getSide(RelativeSide.BACK);
        return back == Direction.DOWN ? Set.of() : Set.of(back);
    }
    @Override public AECableType getCableConnectionType(Direction direction) { return AECableType.DENSE_SMART; }
    @Override public void onMainNodeStateChanged(IGridNodeListener.State state) { markForUpdate(); }
    public void open(Player player) { MenuOpener.open(OmniConnectorMenu.TYPE.get(), player, MenuLocators.forBlockEntity(this)); }
    @Override public void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag); tag.putString("OmniLink", linkId);
    }
    @Override public void loadTag(ValueInput tag) {
        super.loadTag(tag); linkId = tag.getStringOr("OmniLink", "");
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
        if (from == appeng.util.SettingsFrom.DISMANTLE_ITEM && custom != null) selectLink(custom.copyTag().getString("AE2LFOmniLink").orElse(""));
    }
}
