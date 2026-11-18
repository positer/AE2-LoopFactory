package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.item.ModItems;
import appeng.core.definitions.AEBlocks;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import appeng.recipes.handlers.InscriberRecipe;
import appeng.recipes.transform.TransformRecipe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class LoopRecipeJeiPlugin implements IModPlugin {
    private static final RecipeType<Display> DISASSEMBLY =
            RecipeType.create(Ae2LightOptimizer.NAMESPACE, "cell_disassembly", Display.class);
    private static final RecipeType<Display> INSCRIBER =
            RecipeType.create(Ae2LightOptimizer.NAMESPACE, "inscriber", Display.class);
    private static final RecipeType<Display> EXPLOSION =
            RecipeType.create(Ae2LightOptimizer.NAMESPACE, "explosion", Display.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Ae2LightOptimizer.NAMESPACE, "loop_recipes");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new Category(DISASSEMBLY, "cell_disassembly",
                ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance(), gui));
        registration.addRecipeCategories(new Category(INSCRIBER, "inscriber", AEBlocks.INSCRIBER.stack(), gui),
                new Category(EXPLOSION, "explosion", new ItemStack(Items.TNT), gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var disassembly = new ArrayList<Display>();
        var inscriber = new ArrayList<Display>();
        var explosion = new ArrayList<Display>();
        for (var holder : level.getRecipeManager().getRecipes()) {
            var id = holder.id();
            if (!id.getNamespace().equals(Ae2LightOptimizer.NAMESPACE)) {
                continue;
            }
            switch (holder.value()) {
                case StorageCellDisassemblyRecipe recipe when recipe.canDisassemble() -> disassembly.add(new Display(
                        id, List.of(List.of(recipe.getStorageCell().getDefaultInstance())), recipe.getCellDisassemblyItems()));
                case InscriberRecipe recipe -> inscriber.add(new Display(id,
                        recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty())
                                .map(ingredient -> List.of(ingredient.getItems())).toList(),
                        List.of(recipe.getResultItem())));
                case TransformRecipe recipe when recipe.getCircumstance().isExplosion() -> explosion.add(new Display(
                        id, stackRepeatedIngredients(recipe), List.of(recipe.getResultItem())));
                default -> {}
            }
        }
        registration.addRecipes(DISASSEMBLY, disassembly);
        registration.addRecipes(INSCRIBER, inscriber);
        registration.addRecipes(EXPLOSION, explosion);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(AEBlocks.INSCRIBER.stack(), INSCRIBER);
        registration.addRecipeCatalyst(new ItemStack(Items.TNT), EXPLOSION);
    }

    private static List<List<ItemStack>> stackRepeatedIngredients(TransformRecipe recipe) {
        var quantities = new LinkedHashMap<List<net.minecraft.world.item.Item>, Integer>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            var options = Arrays.stream(ingredient.getItems()).map(ItemStack::getItem).toList();
            quantities.merge(options, 1, Integer::sum);
        }
        return quantities.entrySet().stream()
                .map(entry -> entry.getKey().stream().map(item -> new ItemStack(item, entry.getValue())).toList())
                .toList();
    }

    private record Display(ResourceLocation id, List<List<ItemStack>> inputs, List<ItemStack> outputs) {}

    private static final class Category implements IRecipeCategory<Display> {
        private final RecipeType<Display> type;
        private final Component title;
        private final IDrawable icon;
        private final IDrawable arrow;

        Category(RecipeType<Display> type, String name, ItemStack icon, IGuiHelper gui) {
            this.type = type;
            this.title = Component.translatable("jei.ae2lpt." + name);
            this.icon = gui.createDrawableItemStack(icon);
            this.arrow = gui.getRecipeArrow();
        }

        @Override
        public RecipeType<Display> getRecipeType() {
            return type;
        }

        @Override
        public Component getTitle() {
            return title;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public int getWidth() {
            return 152;
        }

        @Override
        public int getHeight() {
            return 22;
        }

        @Override
        public ResourceLocation getRegistryName(Display recipe) {
            return recipe.id();
        }

        @Override
        public void draw(Display recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 57, 5);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder layout, Display recipe, IFocusGroup focuses) {
            for (int i = 0; i < recipe.inputs().size(); i++) {
                var slot = layout.addInputSlot(2 + 18 * i, 3);
                slot.setStandardSlotBackground().addItemStacks(recipe.inputs().get(i));
                if (type == DISASSEMBLY) {
                    slot.addRichTooltipCallback((view, tooltip) ->
                            tooltip.add(Component.translatable("jei.ae2lpt.cell_disassembly.hint")));
                }
            }
            for (int i = 0; i < recipe.outputs().size(); i++) {
                layout.addOutputSlot(81 + 18 * i, 3).setStandardSlotBackground()
                        .addItemStack(recipe.outputs().get(i));
            }
        }
    }
}
