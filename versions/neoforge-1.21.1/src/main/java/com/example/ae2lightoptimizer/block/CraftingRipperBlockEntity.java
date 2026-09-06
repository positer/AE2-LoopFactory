package com.example.ae2lightoptimizer.block;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.menu.CraftingRipperMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Native provider settings and inventory, with CPU-owned atomic execution. */
public final class CraftingRipperBlockEntity extends AENetworkedBlockEntity
        implements PatternProviderLogicHost, IUpgradeableObject {
    public static final int PATTERN_SLOTS = 36;
    private final CraftingRipperLogic logic;
    private final IUpgradeInventory upgrades;

    public CraftingRipperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRAFTING_RIPPER.get(), pos, state);
        getMainNode().setIdlePowerUsage(5.0);
        this.logic = new CraftingRipperLogic(this);
        this.upgrades = UpgradeInventories.forMachine(ModBlocks.CRAFTING_RIPPER.get(), 1, this::upgradesChanged);
    }

    private void upgradesChanged() {
        saveChanges();
        if (logic != null && getLevel() != null && !getLevel().isClientSide()) {
            logic.updatePatterns();
        }
    }

    public boolean isLoopCardInstalled() {
        return upgrades != null && upgrades.isInstalled(ModItems.LOOP_CARD.get());
    }

    public boolean ownsPattern(IPatternDetails pattern) {
        return getMainNode().isActive() && logic.getAvailablePatterns().contains(pattern);
    }

    @Override public IUpgradeInventory getUpgrades() { return upgrades; }
    @Override public CraftingRipperLogic getLogic() { return logic; }
    @Override public EnumSet<Direction> getTargets() { return EnumSet.noneOf(Direction.class); }
    @Override public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }
    @Override public AEItemKey getTerminalIcon() { return AEItemKey.of(ModItems.CRAFTING_RIPPER.get()); }
    @Override public ItemStack getMainMenuIcon() { return ModItems.CRAFTING_RIPPER.get().getDefaultInstance(); }
    @Override public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(CraftingRipperMenu.TYPE.get(), player, locator);
    }

    @Override public void onReady() {
        super.onReady();
        logic.updatePatterns();
    }
    @Override public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (logic != null) logic.onMainNodeStateChanged();
        markForUpdate();
    }

    @Override public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        logic.addDrops(drops);
        for (var stack : upgrades) if (!stack.isEmpty()) drops.add(stack.copy());
    }
    @Override public void clearContent() {
        super.clearContent();
        logic.clearContent();
        upgrades.clear();
    }

    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        logic.writeToNBT(tag, registries);
        upgrades.writeToNBT(tag, "upgrades", registries);
    }
    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        upgrades.readFromNBT(tag, "upgrades", registries);
        logic.readFromNBT(tag, registries);
    }
}
