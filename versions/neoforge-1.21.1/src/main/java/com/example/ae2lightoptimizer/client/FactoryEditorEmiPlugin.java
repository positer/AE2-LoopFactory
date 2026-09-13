package com.example.ae2lightoptimizer.client;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Optional native EMI integration; no JEI installation is required. */
@EmiEntrypoint
public final class FactoryEditorEmiPlugin implements EmiPlugin {
    @Override public void register(EmiRegistry registry) {
        registry.addDragDropHandler(FactoryEditorScreen.class, new EmiDragDropHandler<FactoryEditorScreen>() {
            @Override public boolean dropStack(FactoryEditorScreen screen, EmiIngredient ingredient, int mouseX, int mouseY) {
                if (!screen.getCodeDropArea().contains(mouseX, mouseY)) return false;
                return screen.insertCodeAtCursor(selector(ingredient));
            }
            @Override public void render(FactoryEditorScreen screen, EmiIngredient ingredient,
                    net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                if (!screen.canInsertCode(selector(ingredient))) return;
                var area = screen.getCodeDropArea();
                graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), 0x8822bb33);
            }
        });
    }

    private static String selector(EmiIngredient ingredient) {
        var choices = ingredient.getEmiStacks();
        // A tag/list is ambiguous; only an actual single dragged resource supplies an identifier.
        if (choices.size() != 1) return "";
        var stack = choices.getFirst();
        if (stack.isEmpty()) return "";
        var item = stack.getItemStack();
        if (!item.isEmpty()) return BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        var fluid = stack.getKeyOfType(Fluid.class);
        return fluid == null || fluid == Fluids.EMPTY ? "" : BuiltInRegistries.FLUID.getKey(fluid).toString();
    }
}
