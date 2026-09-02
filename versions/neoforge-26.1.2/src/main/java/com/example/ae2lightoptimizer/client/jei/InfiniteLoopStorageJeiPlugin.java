package com.example.ae2lightoptimizer.client.jei;

import appeng.client.integrations.jei.TransformCategory;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.item.ModItems;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRuntimeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class InfiniteLoopStorageJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_ID =
            Identifier.fromNamespaceAndPath(Ae2LightOptimizer.MOD_ID, "infinite_loop_storage");
    private static final IRecipeType<DisplayRecipe> RECIPE_TYPE =
            IRecipeType.create(Ae2LightOptimizer.MOD_ID, "infinite_loop_storage", DisplayRecipe.class);

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(RECIPE_TYPE, List.of(DisplayRecipe.INSTANCE));
    }

    @Override
    public void registerRuntime(IRuntimeRegistration registration) {
        var recipeManager = registration.getRecipeManager();
        var original = recipeManager.createRecipeLookup(TransformCategory.RECIPE_TYPE)
                .includeHidden()
                .get()
                .filter(holder -> holder.value().createResult().is(ModItems.INFINITE_LOOP_STORAGE_CELL.get()))
                .toList();
        recipeManager.hideRecipes(TransformCategory.RECIPE_TYPE, original);
    }

    private enum DisplayRecipe {
        INSTANCE
    }

    private static final class Category implements IRecipeCategory<DisplayRecipe> {
        private final IDrawable icon;
        private final IDrawableStatic arrow;

        private Category(IRecipeCategoryRegistration registration) {
            var gui = registration.getJeiHelpers().getGuiHelper();
            this.icon = gui.createDrawableItemLike(ModItems.INFINITE_LOOP_STORAGE_CELL.get());
            this.arrow = gui.getRecipeArrow();
        }

        @Override
        public IRecipeType<DisplayRecipe> getRecipeType() {
            return RECIPE_TYPE;
        }

        @Override
        public Component getTitle() {
            return new ItemStack(ModItems.INFINITE_LOOP_STORAGE_CELL.get()).getHoverName();
        }

        @Override
        public int getWidth() {
            return 108;
        }

        @Override
        public int getHeight() {
            return 36;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, DisplayRecipe recipe, IFocusGroup focuses) {
            builder.addInputSlot(1, 10)
                    .setStandardSlotBackground()
                    .add(new ItemStack(ModItems.LOOP_STORAGE_CORE_256M.get(), 64));
            builder.addInputSlot(25, 10)
                    .setStandardSlotBackground()
                    .add(ModItems.LOOP_STORAGE_CELL_HOUSING.get());
            builder.addOutputSlot(89, 10)
                    .setOutputSlotBackground()
                    .add(ModItems.INFINITE_LOOP_STORAGE_CELL.get());
        }

        @Override
        public void draw(DisplayRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                         net.minecraft.client.gui.GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 52, 10);
        }
    }
}
