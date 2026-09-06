package com.example.ae2loprobe.mixin;

import appeng.recipes.quartzcutting.QuartzCuttingRecipe;
import com.example.ae2loprobe.ToolComponentFixture;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes the original result; never calls a remainder method or changes randomness. */
@Mixin(value = QuartzCuttingRecipe.class, remap = false)
public abstract class ToolRemainderObserver {
    @Inject(method = "getRemainingItems(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/core/NonNullList;", at = @At("RETURN"), require = 1)
    private void ae2loprobe$observe(CraftingInput input, CallbackInfoReturnable<NonNullList<ItemStack>> callback) {
        ToolComponentFixture.observeRemainder(input, callback.getReturnValue());
    }
}
