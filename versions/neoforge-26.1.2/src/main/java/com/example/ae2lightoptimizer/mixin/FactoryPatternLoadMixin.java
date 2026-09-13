package com.example.ae2lightoptimizer.mixin;

import appeng.parts.encoding.PatternEncodingLogic;
import com.example.ae2lightoptimizer.factory.FactoryPatternData;
import com.example.ae2lightoptimizer.factory.FactoryPatternItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PatternEncodingLogic.class)
public abstract class FactoryPatternLoadMixin {
    @ModifyVariable(method = "loadEncodedPattern", at = @At("HEAD"), argsOnly = true)
    private ItemStack ae2lightoptimizer$loadNativeRecipe(ItemStack stack) {
        return stack.getItem() instanceof FactoryPatternItem ? FactoryPatternData.get(stack).recipe() : stack;
    }
}
