package com.example.ae2lightoptimizer.factory;

import appeng.crafting.pattern.EncodedPatternItem;
import appeng.api.crafting.PatternDetailsTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class FactoryPatternItem extends EncodedPatternItem<FactoryPatternDetails> {
    public FactoryPatternItem(Properties properties) {
        super(properties, FactoryPatternDetails::decode,
                (stack, level, error, flags) -> new PatternDetailsTooltip(Component.translatable("item.ae2lightoptimizer.loop_factory_pattern")));
    }
    private static ItemStack previewRecipe(ItemStack stack) {
        var recipe = FactoryPatternData.get(stack).recipe();
        // Match the supported native recipe boundary and reject nested factory patterns.
        return recipe.getItem() instanceof EncodedPatternItem<?>
                && recipe.getItem().getClass().getName().startsWith("appeng.") ? recipe : ItemStack.EMPTY;
    }
    @Override public ItemStack getOutput(ItemStack stack) {
        var recipe = previewRecipe(stack);
        // AE2's GUI hook owns Shift behavior and non-item output rendering.
        return recipe.isEmpty() ? ItemStack.EMPTY : ((EncodedPatternItem<?>) recipe.getItem()).getOutput(recipe);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> lines, net.minecraft.world.item.TooltipFlag flags) {
        var recipe = previewRecipe(stack);
        if (recipe.isEmpty()) super.appendHoverText(stack, context, display, lines, flags);
        else recipe.getItem().appendHoverText(recipe, context, display, lines, flags);
    }
    private static void clear(ItemStack stack) {
        var old = FactoryPatternData.get(stack);
        stack.set(FactoryPatternData.TYPE.get(), new FactoryPatternData("", old.factoryId(), ItemStack.EMPTY));
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide()) clear(stack);
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide()) clear(stack);
        return InteractionResult.SUCCESS;
    }
}
