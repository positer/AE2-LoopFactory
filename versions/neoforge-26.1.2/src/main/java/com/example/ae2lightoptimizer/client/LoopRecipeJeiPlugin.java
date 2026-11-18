package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.item.ModItems;
import appeng.client.AppEngClient;
import appeng.recipes.AERecipeTypes;
import net.minecraft.world.item.ItemStackTemplate;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class LoopRecipeJeiPlugin implements IModPlugin {
    private static final IRecipeType<Display> DISASSEMBLY =
            IRecipeType.create(Ae2LightOptimizer.NAMESPACE, "cell_disassembly", Display.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(Ae2LightOptimizer.NAMESPACE, "loop_recipes");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new Category(DISASSEMBLY, "cell_disassembly",
                ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance(), gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var type = AERecipeTypes.CELL_DISASSEMBLY;
        var recipes = AppEngClient.instance().getRecipeMapForType(level, type).byType(type).stream()
                .filter(holder -> holder.id().identifier().getNamespace().equals(Ae2LightOptimizer.NAMESPACE))
                .filter(holder -> holder.value().canDisassemble())
                .map(holder -> new Display(holder.id().identifier(),
                        List.of(List.of(holder.value().storageCell().getDefaultInstance())),
                        holder.value().cellDisassemblyItems().stream().map(ItemStackTemplate::create).toList()))
                .toList();
        registration.addRecipes(DISASSEMBLY, recipes);
    }
    private record Display(Identifier id, List<List<ItemStack>> inputs, List<ItemStack> outputs) {}

    private static final class Category implements IRecipeCategory<Display> {
        private final IRecipeType<Display> type;
        private final Component title;
        private final IDrawable icon;
        private final IDrawable arrow;

        Category(IRecipeType<Display> type, String name, ItemStack icon, IGuiHelper gui) {
            this.type = type;
            this.title = Component.translatable("jei.ae2lpt." + name);
            this.icon = gui.createDrawableItemStack(icon);
            this.arrow = gui.getRecipeArrow();
        }

        @Override
        public IRecipeType<Display> getRecipeType() {
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
        public Identifier getRegistryName(Display recipe) {
            return recipe.id();
        }

        @Override
        public void draw(Display recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
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
