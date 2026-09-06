package com.example.ae2loprobe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEPatternDecoder;
import com.example.ae2lightoptimizer.integration.CatalogSmithingPattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

/** Real registered datapack recipes; only detached stacks and counters are used in this fixture. */
public final class OptionalSmithingFixture {
    private OptionalSmithingFixture() {}

    public static Map<String, Object> run(ServerLevel level) {
        CatalogSmithingPattern.registerDecoder();
        var cases = new ArrayList<Map<String, Object>>();
        for (String name : List.of("no_template", "no_addition", "both_empty")) {
            for (boolean components : List.of(false, true)) cases.add(check(level, name, components));
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", true);
        report.put("caseCount", cases.size());
        report.put("cases", cases);
        report.put("boundary", "Live recipe manager and native codec/matches/assemble; detached input counters only. No network task submitted here.");
        return report;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> check(ServerLevel level, String name, boolean components) {
        var id = ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("ae2lo_runtime_probe", "optional_smithing_" + name));
        var holder = level.getServer().getRecipeManager().byKey(id).orElseThrow();
        require(holder.value() instanceof SmithingRecipe, "fixture did not load through vanilla smithing codec");
        var recipe = (SmithingRecipe) holder.value();
        var template = name.equals("no_addition") ? Items.PAPER.getDefaultInstance() : ItemStack.EMPTY;
        var base = Items.STICK.getDefaultInstance();
        var addition = name.equals("no_template") ? Items.AMETHYST_SHARD.getDefaultInstance() : ItemStack.EMPTY;
        if (components) base.set(DataComponents.CUSTOM_NAME, Component.literal("Optional smithing component witness"));
        var input = new SmithingRecipeInput(template, base, addition);
        require(recipe.matches(input, level), "native matches rejected real empty slots");
        var output = recipe.assemble(input);
        require(output.is(Items.ECHO_SHARD) && output.getCount() == 1, "unexpected native output");
        require(!components || output.get(DataComponents.CUSTOM_NAME).equals(base.get(DataComponents.CUSTOM_NAME)),
                "native transform lost base components");

        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var recipeTag = Recipe.CODEC.encodeStart(ops, recipe).getOrThrow();
        var parsed = (SmithingRecipe) Recipe.CODEC.parse(ops, recipeTag).getOrThrow();
        require(parsed.matches(input, level) && ItemStack.matches(output, parsed.assemble(input)),
                "native recipe codec round trip changed semantics");

        String nativeRejection = null;
        try {
            PatternDetailsHelper.encodeSmithingTablePattern((RecipeHolder<SmithingRecipe>) holder,
                    AEItemKey.of(template), AEItemKey.of(base), AEItemKey.of(addition), AEItemKey.of(output), false);
        } catch (RuntimeException expected) {
            nativeRejection = expected.getClass().getSimpleName();
        }
        require(nativeRejection != null, "fixture no longer demonstrates an AE2 native encoding gap");

        var pattern = CatalogSmithingPattern.fromRecipe(holder, input, level);
        require(AEPatternDecoder.INSTANCE.decodePattern(pattern.getDefinition(), level) == null,
                "native decoder unexpectedly claims custom pattern before its decoder");
        var tag = AEItemKey.CODEC.encodeStart(ops, pattern.getDefinition()).getOrThrow();
        var definition = AEItemKey.CODEC.parse(ops, tag).getOrThrow();
        var decoded = PatternDetailsHelper.decodePattern(definition, level);
        require(decoded instanceof CatalogSmithingPattern && decoded.equals(pattern), "custom pattern persistence/decoder failed");
        var current = (CatalogSmithingPattern) decoded;
        require(current.getOutputs().equals(pattern.getOutputs()), "persistent output changed");
        var counters = counters(current);
        var grid = emptyGrid();
        current.fillCraftingGrid(counters, grid::set);
        require(ItemStack.matches(grid.get(3), template) && ItemStack.matches(grid.get(4), base)
                && ItemStack.matches(grid.get(5), addition), "empty smithing positions changed");
        for (var counter : counters) {
            counter.removeZeros();
            require(counter.isEmpty(), "one operation failed to consume exactly one set of inputs");
        }
        var craftingInput = CraftingInput.of(3, 3, grid);
        require(ItemStack.matches(current.assemble(craftingInput, level), output), "trimmed grid changed smithing position semantics");
        require(current.getRemainingItems(craftingInput).stream().allMatch(ItemStack::isEmpty), "invented smithing remainder");

        var badGrid = new ArrayList<>(grid);
        badGrid.set(template.isEmpty() ? 3 : 5, Items.STICK.getDefaultInstance());
        require(current.assemble(CraftingInput.of(3, 3, badGrid), level).isEmpty(), "accepted an item in required empty slot");
        if (components) {
            var changed = new ArrayList<>(grid);
            changed.set(4, Items.STICK.getDefaultInstance());
            require(current.assemble(CraftingInput.of(3, 3, changed), level).isEmpty(), "accepted different input components");
        }
        var shortage = counters(current);
        var firstKey = current.getInputs()[0].getPossibleInputs()[0].what();
        shortage[shortage.length - 1].clear();
        long firstBefore = shortage[0].get(firstKey);
        boolean refused = false;
        try { current.fillCraftingGrid(shortage, emptyGrid()::set); }
        catch (IllegalArgumentException expected) { refused = true; }
        require(refused && shortage[0].get(firstKey) == firstBefore, "insufficient detached counters were partially consumed");

        var forged = definition.toStack();
        var data = forged.get(DataComponents.CUSTOM_DATA).copyTag();
        data.put("output", ItemStack.CODEC.encodeStart(ops, Items.DIAMOND.getDefaultInstance()).getOrThrow());
        forged.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        require(PatternDetailsHelper.decodePattern(AEItemKey.of(forged), level) == null, "accepted forged output");
        data.putString("recipe", "ae2lo_runtime_probe:missing_recipe");
        forged.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        require(PatternDetailsHelper.decodePattern(AEItemKey.of(forged), level) == null, "accepted missing recipe");

        var row = new LinkedHashMap<String, Object>();
        row.put("recipeId", id.identifier().toString());
        row.put("components", components);
        row.put("nativeEncodingRejection", nativeRejection);
        row.put("inputs", current.getInputs().length);
        row.put("trimmedWidth", craftingInput.width());
        row.put("codecRoundTrip", true);
        row.put("exactExecution", true);
        row.put("tamperRejected", true);
        return row;
    }

    private static KeyCounter[] counters(CatalogSmithingPattern pattern) {
        var result = new KeyCounter[pattern.getInputs().length];
        for (int i = 0; i < result.length; i++) {
            result[i] = new KeyCounter();
            var input = pattern.getInputs()[i].getPossibleInputs()[0];
            result[i].add(input.what(), input.amount());
        }
        return result;
    }

    private static ArrayList<ItemStack> emptyGrid() {
        return new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
