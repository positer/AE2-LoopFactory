package com.example.ae2lightoptimizer.storage;

import java.util.List;
import java.util.Objects;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.items.contents.CellConfig;
import appeng.items.tools.powered.AbstractPortableCell;
import appeng.menu.me.common.MEStorageMenu;
import appeng.util.ConfigInventory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** AE2-native portable terminal whose storage backend is the universal loop cell handler. */
public final class PortableLoopStorageCellItem extends AbstractPortableCell implements LoopStorageTierProvider {
    private static final int DEFAULT_COLOR = 0x80CDFF;
    private final LoopStorageTier tier;

    public PortableLoopStorageCellItem(LoopStorageTier tier, Properties properties) {
        super(MEStorageMenu.PORTABLE_ITEM_CELL_TYPE, properties.stacksTo(1), DEFAULT_COLOR);
        this.tier = Objects.requireNonNull(tier, "tier");
    }

    @Override
    public LoopStorageTier tier() {
        return tier;
    }

    @Override
    public ResourceLocation getRecipeId() {
        return Objects.requireNonNull(getRegistryName(), "portable loop cell registry name");
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 80.0 + 80.0 * Upgrades.getEnergyCardMultiplier(getUpgrades(stack));
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, 4, this::onUpgradesChanged);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(AEKeyTypes.getAll(), stack);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return stack.getOrDefault(AEComponents.STORAGE_CELL_FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        stack.set(AEComponents.STORAGE_CELL_FUZZY_MODE, mode);
    }

    public List<ItemStack> emptyAndFullStacks() {
        ItemStack empty = new ItemStack(this);
        ItemStack full = new ItemStack(this);
        injectAEPower(full, getAEMaxPower(full), Actionable.MODULATE);
        return List.of(empty, full);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        LoopStorageCellHandler.INSTANCE.addCellInformationToTooltip(stack, lines::add);
        lines.add(Component.translatable("tooltip.ae2lightoptimizer.loop_storage_cell.universal")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
