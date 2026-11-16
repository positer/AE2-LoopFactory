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
        // A channel change re-targets the factory-network membership as well.
        refreshFactoryLink();
    }
    public boolean linked() {
        return getLevel() != null && getLevel().getServer() != null && OmniNetworks.get(getLevel().getServer()).linked(this);
    }
    /** True while a formed Loop Factory structure is joined to this connector's channel. */
    public boolean factoryLinked() { return OmniFactoryNetworks.linked(this); }
    /**
     * Joins the channel to the machine below. Two independent halves:
     * the factory network registers the machine's factory side (a provider's subnet) plus this chunk so
     * scheduled code can address it, while the AE transport joins only the machine's MAIN network to the
     * channel. A provider's subnet is never put on the AE channel.
     */
    /** The connector's base (mounting) face; it may point in any direction. */
    private Direction base() { return getOrientation().getSide(RelativeSide.BACK); }
    public void refreshFactoryLink() {
        var level = getLevel();
        if (level == null || level.isClientSide()) return;
        // The factory network joins on the structure alone; the channel only carries the AE network.
        var target = OmniFactoryWireless.target(level, getBlockPos(), base());
        var self = getMainNode().getNode();
        if (target == null || self == null) { dropFactoryLink(); markForUpdate(); return; }
        OmniFactoryNetworks.join(this, target);
        if (!target.isProvider()) {
            // A terminal joins through the base face itself: with a channel the physical edge is the AE
            // link, without one the base is not connectable, so the terminal's network is untouched.
            dropAeLink(); markForUpdate(); return;
        }
        // A provider's subnet looks back at the base face; AE2 may have wired that edge physically.
        // The subnet must never be on the channel, so drop exactly that edge (never the main pair).
        var subnet = target.getGridNode(base().getOpposite());
        if (subnet != null) for (var connection : java.util.List.copyOf(self.getConnections())) {
            var other = connection.getOtherSide(self);
            if (other == subnet) { try { connection.destroy(); } catch (RuntimeException ignored) { } }
        }
        var main = target.getMainNode().getNode();
        if (main == null || main == self || linkId.isEmpty()) { dropAeLink(); markForUpdate(); return; }
        if (factoryNode != main) {
            dropAeLink();
            factoryNode = main;
            if (main.getGrid() != self.getGrid()) {
                try { factoryLink = appeng.api.networking.GridHelper.createConnection(self, main); }
                catch (RuntimeException alreadyConnected) { factoryLink = null; }
            }
        }
        markForUpdate();
    }
    private void dropAeLink() {
        if (factoryLink != null) {
            try { factoryLink.destroy(); } catch (RuntimeException ignored) { }
            factoryLink = null;
        }
        factoryNode = null;
    }
    private void dropFactoryLink() { dropAeLink(); OmniFactoryNetworks.leave(this); }
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
        var level = getLevel();
        if (level != null && level.getBlockEntity(getBlockPos().relative(back))
                instanceof com.example.ae2lightoptimizer.factory.FactoryBlockEntity factory) {
            // The provider's subnet face is never wired: the channel must reach the main network instead.
            if (factory.kind() == com.example.ae2lightoptimizer.factory.FactoryBlock.Kind.PROVIDER
                    && factory.getFront() == back.getOpposite()) return Set.of();
            // A terminal structure without a channel must leave the terminal's AE network untouched.
            if (factory.kind() == com.example.ae2lightoptimizer.factory.FactoryBlock.Kind.TERMINAL
                    && back == Direction.DOWN && linkId.isEmpty()) return Set.of();
        }
        return Set.of(back);
    }
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
