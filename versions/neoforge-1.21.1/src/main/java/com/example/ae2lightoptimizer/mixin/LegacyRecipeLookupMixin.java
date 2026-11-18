package com.example.ae2lightoptimizer.mixin;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.LegacyNamespace;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

@Mixin(RecipeManager.class)
abstract class LegacyRecipeLookupMixin {
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;

    @Inject(method = "byKey", at = @At("RETURN"), cancellable = true)
    private void lookup(ResourceLocation id, CallbackInfoReturnable<Optional<RecipeHolder<?>>> result) {
        if (LegacyNamespace.ID.equals(id.getNamespace()) && result.getReturnValue().isEmpty()) {
            result.setReturnValue(Optional.ofNullable(byName.get(ResourceLocation.fromNamespaceAndPath(
                    Ae2LightOptimizer.NAMESPACE, id.getPath()))));
        }
    }

    @Inject(method = "byKeyTyped", at = @At("RETURN"), cancellable = true)
    private <T extends Recipe<?>> void lookupTyped(RecipeType<T> type, ResourceLocation id,
            CallbackInfoReturnable<RecipeHolder<?>> result) {
        if (id != null && LegacyNamespace.ID.equals(id.getNamespace()) && result.getReturnValue() == null) {
            var holder = byName.get(ResourceLocation.fromNamespaceAndPath(Ae2LightOptimizer.NAMESPACE, id.getPath()));
            if (holder != null && holder.value().getType() == type) result.setReturnValue(holder);
        }
    }
}
