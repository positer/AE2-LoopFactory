package com.example.ae2lightoptimizer.storage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;

/** Identifies a cyclic storage cell and its loader-independent capacity tier. */
public final class LoopStorageCellItem extends Item implements LoopStorageTierProvider, ICellWorkbenchItem {
    private final LoopStorageTier tier;

    public LoopStorageCellItem(LoopStorageTier tier, Properties properties) {
        super(properties);
        this.tier = Objects.requireNonNull(tier, "tier");
    }

    @Override
    public LoopStorageTier tier() {
        return tier;
    }

    @Override
    public appeng.api.upgrades.IUpgradeInventory getUpgrades(ItemStack stack) {
        return appeng.api.upgrades.UpgradeInventories.forItem(stack, 2);
    }

    /** The Cell Workbench partition, so a condenser can tell a marked cell from an unmarked one. */
    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(new LinkedHashSet<>(AEKeyTypes.getAll()), stack);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return stack.getOrDefault(AEComponents.STORAGE_CELL_FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        stack.set(AEComponents.STORAGE_CELL_FUZZY_MODE, mode);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        LoopStorageCellHandler.INSTANCE.addCellInformationToTooltip(stack, lines::add);
        lines.add(Component.translatable("tooltip.ae2lpt.loop_storage_cell.universal")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}