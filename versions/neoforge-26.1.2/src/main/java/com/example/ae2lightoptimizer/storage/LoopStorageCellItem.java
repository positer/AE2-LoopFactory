package com.example.ae2lightoptimizer.storage;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public final class LoopStorageCellItem extends Item implements LoopStorageTierProvider {
    private final LoopStorageTier tier;

    public LoopStorageCellItem(Properties properties, LoopStorageTier tier) {
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
            TooltipDisplay display,
            Consumer<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        LoopStorageCellHandler.INSTANCE.addCellInformationToTooltip(stack, lines);
        lines.accept(Component.translatable("tooltip.ae2lightoptimizer.loop_storage_cell.universal")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
