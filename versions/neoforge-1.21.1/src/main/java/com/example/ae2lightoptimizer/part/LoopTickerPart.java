package com.example.ae2lightoptimizer.part;

import appeng.api.networking.energy.IEnergyService;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The loop ticker as a cable part: it mounts on one face of a cable exactly like the ME output bus,
 * and accelerates whatever block sits behind that face. It draws no power of its own but only runs
 * while the grid is powered.
 */
public final class LoopTickerPart extends AEBasePart
        implements IUpgradeableObject, com.example.ae2lightoptimizer.api.LoopTickerHost {
    private static final int BASE_CAP = 4;
    private static final int MAX_CARDS = 4;

    public static final ResourceLocation MODEL_BASE =
            ResourceLocation.fromNamespaceAndPath("ae2lightoptimizer", "part/loop_ticker_base");
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE,
            ResourceLocation.fromNamespaceAndPath("ae2", "part/export_bus_off"));
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE,
            ResourceLocation.fromNamespaceAndPath("ae2", "part/export_bus_on"));
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
            ResourceLocation.fromNamespaceAndPath("ae2", "part/export_bus_has_channel"));

    private final IUpgradeInventory upgrades;
    private int rate;

    public LoopTickerPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0);
        this.upgrades = UpgradeInventories.forMachine(partItem, MAX_CARDS, this::onUpgradesChanged);
    }

    private void onUpgradesChanged() {
        if (getHost() != null && getHost().getBlockEntity() != null) {
            getHost().getBlockEntity().setChanged();
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public int maxRate() {
        int installed = 0;
        for (int slot = 0; slot < upgrades.size(); slot++) {
            if (!upgrades.getStackInSlot(slot).isEmpty()) installed++;
        }
        return BASE_CAP << Math.min(installed, MAX_CARDS);
    }

    public int rate() {
        return rate <= 0 ? maxRate() : Math.min(rate, maxRate());
    }

    public void setRate(int value) {
        rate = Math.max(1, Math.min(value, maxRate()));
        if (getHost() != null && getHost().getBlockEntity() != null) {
            getHost().getBlockEntity().setChanged();
        }
    }

    /** Empty-hand right-click opens the rate menu, exactly like the native output bus. */
    @Override
    public boolean onUseWithoutItem(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.Vec3 pos) {
        if (!isClientSide()) {
            appeng.menu.MenuOpener.open(com.example.ae2lightoptimizer.menu.LoopTickerMenu.PART_TYPE.get(),
                    player, appeng.menu.locator.MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 pos) {
        if (player.isShiftKeyDown() && stack.is(appeng.core.definitions.AEItems.SPEED_CARD.asItem())) {
            if (!isClientSide()) {
                var inventory = getUpgrades();
                for (int slot = 0; slot < inventory.size() && !stack.isEmpty(); slot++) {
                    if (inventory.getStackInSlot(slot).isEmpty()) {
                        inventory.setItemDirect(slot, stack.copyWithCount(1));
                        stack.shrink(1);
                    }
                }
                saveRate();
            }
            return true;
        }
        if (stack.isEmpty() && !isClientSide()) {
            appeng.menu.MenuOpener.open(com.example.ae2lightoptimizer.menu.LoopTickerMenu.PART_TYPE.get(), player,
                    appeng.menu.locator.MenuLocators.forPart(this));
            return true;
        }
        return super.onUseItemOn(stack, player, hand, pos);
    }

    @Override
    public void saveRate() {
        if (getHost() != null && getHost().getBlockEntity() != null) {
            getHost().getBlockEntity().setChanged();
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(4, 4, 2, 12, 12, 4);
        bch.addBox(5, 5, 1, 11, 11, 2);
        bch.addBox(6, 6, 0, 10, 10, 1);
        bch.addBox(6, 6, 4, 10, 10, 5);
    }

    @Override
    public IPartModel getStaticModels() {
        if (isActive()) {
            return MODELS_HAS_CHANNEL;
        }
        return isPowered() ? MODELS_ON : MODELS_OFF;
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        upgrades.writeToNBT(tag, "upgrades", registries);
        tag.putInt("ae2lfRate", rate);
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        upgrades.readFromNBT(tag, "upgrades", registries);
        rate = tag.getInt("ae2lfRate");
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        com.example.ae2lightoptimizer.integration.LoopTickerPartService.register(this);
    }

    @Override
    public void removeFromWorld() {
        com.example.ae2lightoptimizer.integration.LoopTickerPartService.unregister(this);
        super.removeFromWorld();
    }

    /** Diagnostic: how often the server tick service called this part. */
    public int tickCount;

    /** Called once per server tick by the tick service. */
    public void serverTick() {
        tickCount++;
        accelerate();
    }

    /**
     * Any AE2 network with energy qualifies, controller or not: ad-hoc networks are networks too.
     * Only a missing grid or a fully drained network stops the accelerator.
     */
    private boolean networkReady() {
        var grid = getMainNode().getGrid();
        if (grid == null) return false;
        var energy = grid.getEnergyService();
        return energy != null && (energy.getStoredPower() > 0 || energy.getAvgPowerInjection() > 0);
    }

    /** Runs the block behind the face this part is mounted on. */
    private void accelerate() {
        if (!(getLevel() instanceof ServerLevel server)) return;
        if (getMainNode().getNode() == null) {
            // The node may not exist yet if the part was restored before its host was ready.
            getMainNode().create(getLevel(), getBlockEntity().getBlockPos());
        }
        if (!networkReady()) return;
        // The part is mounted on one face of the cable; the block it acts on is the one that face points at.
        Direction side = getSide();
        BlockPos target = getBlockEntity().getBlockPos().relative(side);
        BlockState targetState = server.getBlockState(target);
        if (targetState.isAir()) return;
        BlockEntity targetEntity = server.getBlockEntity(target);
        BlockEntityTicker<BlockEntity> ticker = null;
        if (targetEntity != null && targetState.getBlock() instanceof EntityBlock entityBlock) {
            @SuppressWarnings("unchecked")
            var found = (BlockEntityTicker<BlockEntity>) entityBlock.getTicker(
                    server, targetState, targetEntity.getType());
            ticker = found;
        }
        for (int i = 0; i < rate(); i++) {
            targetState.randomTick(server, target, server.random);
            if (ticker != null) {
                ticker.tick(server, target, targetState, targetEntity);
            }
        }
    }
}