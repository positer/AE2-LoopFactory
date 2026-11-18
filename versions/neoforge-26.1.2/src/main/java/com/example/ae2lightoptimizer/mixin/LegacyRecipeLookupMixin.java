package com.example.ae2lightoptimizer.mixin;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.LegacyNamespace;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeMap;

@Mixin(RecipeMap.class)
abstract class LegacyRecipeLookupMixin {
    @Shadow public abstract RecipeHolder<?> byKey(ResourceKey<Recipe<?>> key);

    @Inject(method = "byKey", at = @At("RETURN"), cancellable = true)
    private void lookup(ResourceKey<Recipe<?>> key, CallbackInfoReturnable<RecipeHolder<?>> result) {
        if (key != null && LegacyNamespace.ID.equals(key.identifier().getNamespace()) && result.getReturnValue() == null) {
            var id = Identifier.fromNamespaceAndPath(Ae2LightOptimizer.NAMESPACE, key.identifier().getPath());
            result.setReturnValue(byKey(ResourceKey.create(Registries.RECIPE, id)));
        }
    }
}
