package com.example.ae2lightoptimizer.storage;

import java.util.List;
import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Identifies a cyclic storage cell and its loader-independent capacity tier. */
public final class LoopStorageCellItem extends Item implements LoopStorageTierProvider {
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
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        LoopStorageCellHandler.INSTANCE.addCellInformationToTooltip(stack, lines::add);
        lines.add(Component.translatable("tooltip.ae2lightoptimizer.loop_storage_cell.universal")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
