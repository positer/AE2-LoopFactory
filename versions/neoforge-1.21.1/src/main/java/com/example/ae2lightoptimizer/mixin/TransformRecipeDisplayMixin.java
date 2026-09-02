package com.example.ae2lightoptimizer.mixin;

import java.util.List;

import com.example.ae2lightoptimizer.item.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Compresses the infinite transform recipe for recipe viewers while leaving the
 * real explosion input stored in the field untouched.
 */
@Mixin(targets = "appeng.recipes.transform.TransformRecipe", remap = false)
abstract class TransformRecipeDisplayMixin {
    @Inject(method = "getIngredients", at = @At("RETURN"), cancellable = true, require = 1)
    private void ae2lightoptimizer$compressInfinite(CallbackInfoReturnable<NonNullList<Ingredient>> callback) {
        var original = callback.getReturnValue();
        if (isInfiniteExplosion(original)) {
            callback.setReturnValue(NonNullList.of(
                    Ingredient.EMPTY,
                    Ingredient.of(new ItemStack(ModItems.LOOP_STORAGE_CORE_256M.get(), 64)),
                    Ingredient.of(new ItemStack(ModItems.LOOP_STORAGE_CELL_HOUSING.get()))));
        }
    }

    private static boolean isInfiniteExplosion(List<Ingredient> ingredients) {
        if (ingredients.size() != 65) {
            return false;
        }
        int cores = 0;
        boolean housing = false;
        for (Ingredient ingredient : ingredients) {
            if (matches(ingredient, ModItems.LOOP_STORAGE_CORE_256M.get())) {
                cores++;
            } else if (matches(ingredient, ModItems.LOOP_STORAGE_CELL_HOUSING.get())) {
                housing = true;
            } else {
                return false;
            }
        }
        return cores == 64 && housing;
    }

    private static boolean matches(Ingredient ingredient, Item item) {
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }
}
