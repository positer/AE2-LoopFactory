package com.example.ae2lightoptimizer.integration;

import java.util.ArrayList;
import java.util.List;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** Uses the pinned AE2 decoders and the live server recipes, never displayed recipe output guesses. */
public final class CraftingRipperPatterns {
    private static final java.util.Map<Level, CatalogSnapshot> CATALOGS = new java.util.WeakHashMap<>();
    private CraftingRipperPatterns() {}

    public static void invalidateCatalogs() { CATALOGS.clear(); }

    public static boolean requiresNativeExecution(IPatternDetails pattern, Level level) {
        if (pattern.getPrimaryOutput().what() instanceof AEItemKey output
                && output.get(net.minecraft.core.component.DataComponents.MAP_POST_PROCESSING) != null) return true;
        if (!(pattern instanceof AECraftingPattern) || !(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return false;
        }
        var encoded = pattern.getDefinition().get(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN);
        if (encoded == null) return false;
        var holder = server.getServer().getRecipeManager().byKey(encoded.recipeId()).orElse(null);
        if (holder == null) return false;
        if (holder.value() instanceof MapExtendingRecipe) return true;
        if (!(holder.value() instanceof appeng.recipes.quartzcutting.QuartzCuttingRecipe)) return false;
        for (var input : encoded.inputs()) {
            if (!input.is(appeng.core.ConventionTags.QUARTZ_KNIFE)) continue;
            return hasRandomKnifeDamage(input);
        }
        return false;
    }


    /** Tests real selected stock (including component substitutions) without remainder or damage callbacks. */
    public static boolean requiresNativeExecution(IPatternDetails pattern, Iterable<AEKey> selectedKeys, Level level) {
        if (requiresNativeExecution(pattern, level)) return true;
        if (!(pattern instanceof AECraftingPattern) || level == null) return false;
        var encoded = pattern.getDefinition().get(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN);
        if (encoded == null) return false;
        var holder = appeng.crafting.RecipeAccess.byKey(level, RecipeType.CRAFTING, encoded.recipeId());
        if (holder == null || !(holder.value() instanceof appeng.recipes.quartzcutting.QuartzCuttingRecipe recipe)) return false;
        int knifeSlot = -1;
        for (int slot = 0; slot < encoded.inputs().size(); slot++) {
            if (encoded.inputs().get(slot).is(appeng.core.ConventionTags.QUARTZ_KNIFE)) { knifeSlot = slot; break; }
        }
        if (knifeSlot < 0) return false;
        var originalKnife = encoded.inputs().get(knifeSlot);
        for (var candidate : selectedKeys) {
            if (!(candidate instanceof AEItemKey item)) continue;
            var knife = item.toStack();
            if (!knife.is(appeng.core.ConventionTags.QUARTZ_KNIFE) || !hasRandomKnifeDamage(knife)
                    || (!encoded.canSubstitute() && !ItemStack.isSameItemSameComponents(originalKnife, knife))) continue;
            var frame = new ArrayList<ItemStack>(9);
            for (var stack : encoded.inputs()) frame.add(stack.copy());
            frame.set(knifeSlot, knife);
            var input = CraftingInput.of(3, 3, frame);
            if (recipe.matches(input, level) && ItemStack.matches(recipe.assemble(input), encoded.result())) return true;
        }
        return false;
    }

    private static boolean hasRandomKnifeDamage(ItemStack input) {
        if (!input.isDamageableItem()) return false;
        var enchantments = input.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (var enchantment : enchantments.entrySet()) {
            for (var effect : enchantment.getKey().value().getEffects(
                    net.minecraft.world.item.enchantment.EnchantmentEffectComponents.ITEM_DAMAGE)) {
                if (effect.requirements().isPresent() || !deterministicDamage(effect.effect())) return true;
            }
        }
        return false;
    }

    private static boolean deterministicDamage(net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect effect) {
        return effect instanceof net.minecraft.world.item.enchantment.effects.AddValue
                || effect instanceof net.minecraft.world.item.enchantment.effects.MultiplyValue
                || effect instanceof net.minecraft.world.item.enchantment.effects.SetValue
                || effect instanceof net.minecraft.world.item.enchantment.effects.AllOf.ValueEffects all
                        && all.effects().stream().allMatch(CraftingRipperPatterns::deterministicDamage);
    }


    public static boolean supportsPattern(IPatternDetails pattern, Level level) {
        if (!(pattern instanceof AECraftingPattern || pattern instanceof AESmithingTablePattern
                || pattern instanceof AEStonecuttingPattern || pattern instanceof CatalogSmithingPattern)
                || level == null) return false;
        try {
            var current = PatternDetailsHelper.decodePattern(pattern.getDefinition(), level);
            return current != null && current.getClass() == pattern.getClass()
                    && current.getOutputs().equals(pattern.getOutputs());
        } catch (RuntimeException invalidRecipe) {
            return false;
        }
    }

    /** Validates the actual selected ingredients, output components/count and every container remainder. */
    public static boolean validateInputs(IPatternDetails pattern, KeyCounter[] inputs, Level level) {
        if (!supportsPattern(pattern, level) || inputs.length != pattern.getInputs().length) return false;
        try {
            var current = (IMolecularAssemblerSupportedPattern) PatternDetailsHelper.decodePattern(
                    pattern.getDefinition(), level);
            var copies = new KeyCounter[inputs.length];
            var expectedRemainders = new KeyCounter();
            for (int i = 0; i < inputs.length; i++) {
                copies[i] = new KeyCounter();
                for (var entry : inputs[i]) {
                    if (entry.getLongValue() < 0) return false;
                    if (entry.getLongValue() == 0) continue;
                    if (!current.getInputs()[i].isValid(entry.getKey(), level)) return false;
                    copies[i].add(entry.getKey(), entry.getLongValue());
                    AEKey remainder = current.getInputs()[i].getRemainingKey(entry.getKey());
                    if (remainder != null) expectedRemainders.add(remainder, entry.getLongValue());
                }
            }
            var grid = new ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
            current.fillCraftingGrid(copies, grid::set);
            for (var counter : copies) {
                counter.removeZeros();
                if (!counter.isEmpty()) return false;
            }
            for (int slot = 0; slot < grid.size(); slot++) {
                var stack = grid.get(slot);
                if (stack.isEmpty()) continue;
                var wrapped = GenericStack.unwrapItemStack(stack);
                if (wrapped != null) {
                    if (!(current instanceof AECraftingPattern crafting)
                            || !wrapped.equals(crafting.getValidFluid(slot))) return false;
                } else if (!current.isItemValid(slot, AEItemKey.of(stack), level)) return false;
            }
            var craftingInput = CraftingInput.of(3, 3, grid);
            ItemStack result = current.assemble(craftingInput, level);
            if (current instanceof AECraftingPattern) {
                var encoded = current.getDefinition().get(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN);
                var holder = ((net.minecraft.server.level.ServerLevel) level).getServer().getRecipeManager().byKey(encoded.recipeId()).orElse(null);
                if (holder == null || !(holder.value() instanceof CraftingRecipe recipe)) return false;
                var actualGrid = new ArrayList<ItemStack>(grid);
                for (int slot = 0; slot < actualGrid.size(); slot++) {
                    if (GenericStack.unwrapItemStack(actualGrid.get(slot)) != null) {
                        actualGrid.set(slot, encoded.inputs().get(slot).copy());
                    }
                }
                var actualInput = CraftingInput.of(3, 3, actualGrid);
                if (!recipe.matches(actualInput, level)) return false;
                var assembled = recipe.assemble(actualInput);
                if (!ItemStack.matches(assembled, result)) return false;
            }
            var output = pattern.getPrimaryOutput();
            if (result.isEmpty() || output.amount() != result.getCount()
                    || !output.what().equals(AEItemKey.of(result)) || pattern.getOutputs().size() != 1) return false;
            var actualRemainders = new KeyCounter();
            for (var remainder : current.getRemainingItems(craftingInput)) {
                if (!remainder.isEmpty()) actualRemainders.add(AEItemKey.of(remainder), remainder.getCount());
            }
            for (var expected : expectedRemainders) {
                if (actualRemainders.get(expected.getKey()) != expected.getLongValue()) return false;
            }
            for (var actual : actualRemainders) {
                if (expectedRemainders.get(actual.getKey()) != actual.getLongValue()) return false;
            }
            return true;
        } catch (RuntimeException invalidRecipe) {
            return false;
        }
    }

    public static List<IPatternDetails> enumerate(Level level) {
        return inspect(level, List.of()).patterns();
    }

    public static List<IPatternDetails> enumerate(Level level, List<ItemStack> networkInputs) {
        return inspect(level, networkInputs).patterns();
    }

    public static synchronized CatalogSnapshot inspect(Level level, List<ItemStack> networkInputs) {
        var base = CATALOGS.computeIfAbsent(level, world -> CraftingRipperCatalog.build(world, List.of(), null));
        return networkInputs.isEmpty() ? base : CraftingRipperCatalog.build(level, networkInputs, base);
    }

    public record CatalogSnapshot(List<IPatternDetails> patterns, CatalogReport report) {}
    public record CatalogReport(List<RecipeCoverage> recipes, int patternCount) {}
    public record RecipeCoverage(String recipeId, String recipeType, int variants, String status) {}
}
