package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluids;

/** Loaded only by JEI's optional plugin discovery. */
@JeiPlugin
public final class FactoryEditorJeiPlugin implements IModPlugin {
    @Override public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(Ae2LightOptimizer.MOD_ID, "factory_editor_identifiers");
    }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        var fluids = registration.getJeiHelpers().getPlatformFluidHelper();
        registration.addGhostIngredientHandler(FactoryEditorScreen.class, new IGhostIngredientHandler<FactoryEditorScreen>() {
            @Override public <I> List<Target<I>> getTargetsTyped(FactoryEditorScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
                String text = selector(ingredient, fluids);
                if (!screen.canInsertCode(text)) return List.of();
                return List.of(new Target<I>() {
                    @Override public Rect2i getArea() { return screen.getCodeDropArea(); }
                    @Override public void accept(I ignored) { screen.insertCodeAtCursor(text); }
                });
            }
            @Override public void onComplete() { }
        });
    }

    private static <I, F> String selector(ITypedIngredient<I> ingredient, IPlatformFluidHelper<F> fluids) {
        var item = ingredient.getItemStack();
        if (item.isPresent() && !item.get().isEmpty()) return BuiltInRegistries.ITEM.getKey(item.get().getItem()).toString();
        var type = fluids.getFluidIngredientType();
        return ingredient.getIngredient(type).map(type::getBase).filter(fluid -> fluid != Fluids.EMPTY)
                .map(fluid -> BuiltInRegistries.FLUID.getKey(fluid).toString()).orElse("");
    }
}
