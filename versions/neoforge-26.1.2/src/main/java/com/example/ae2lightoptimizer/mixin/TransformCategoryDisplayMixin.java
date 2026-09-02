package com.example.ae2lightoptimizer.mixin;

import com.example.ae2lightoptimizer.item.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the infinite explosion transform inside AE2's native world-interaction
 * category while aggregating the 64 cores into one slot. The real transform field
 * is untouched, so the actual explosion still consumes 64 cores plus one housing.
 */
@Mixin(targets = "appeng.client.integrations.jei.TransformCategory$1", remap = false)
abstract class TransformCategoryDisplayMixin {
    @Shadow(aliases = "val$recipe")
    private appeng.recipes.transform.TransformRecipe recipe;

    @Shadow(aliases = "val$yOffset")
    private int yOffset;

    @Inject(method = "buildSlots", at = @At("HEAD"), cancellable = true, require = 1)
    private void ae2lightoptimizer$compressInfinite(IRecipeLayoutBuilder builder, CallbackInfo callback) {
        if (recipe.createResult().is(ModItems.INFINITE_LOOP_STORAGE_CELL.get())) {
            builder.addInputSlot(6, 6)
                    .setStandardSlotBackground()
                    .add(new ItemStack(ModItems.LOOP_STORAGE_CORE_256M.get(), 64));
            builder.addInputSlot(24, 6)
                    .setStandardSlotBackground()
                    .add(new ItemStack(ModItems.LOOP_STORAGE_CELL_HOUSING.get()));
            builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 56, yOffset + 1)
                    .setSlotName("explosion")
                    .add(new ItemStack(Blocks.TNT))
                    .add(appeng.core.definitions.AEBlocks.TINY_TNT.stack());
            builder.addOutputSlot(106, yOffset + 1)
                    .setOutputSlotBackground()
                    .add(recipe.result().create());
            callback.cancel();
        }
    }
}
