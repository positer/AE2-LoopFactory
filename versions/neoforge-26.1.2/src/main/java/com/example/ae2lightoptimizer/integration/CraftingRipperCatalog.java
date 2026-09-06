package com.example.ae2lightoptimizer.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.*;
import net.minecraft.world.level.Level;

/** Enumerates real finite recipe inputs. Arbitrary player components come only from this network. */
final class CraftingRipperCatalog {
    private final Level level;
    private final List<ItemStack> network;
    private final List<ItemStack> derived = new ArrayList<>();
    private final java.util.Set<String> derivedRecipes = new java.util.HashSet<>();
    private final boolean dynamic;
    private final LinkedHashSet<IPatternDetails> patterns = new LinkedHashSet<>();
    private final Map<CatalogCraftingPattern, CatalogCraftingPattern> craftingPatterns = new LinkedHashMap<>();
    private final Map<String, CraftingRipperPatterns.RecipeCoverage> reports = new LinkedHashMap<>();
    private RecipeHolder<?> current;
    private int added;
    private String status;
    private final LinkedHashSet<String> failures = new LinkedHashSet<>();

    private CraftingRipperCatalog(Level level, List<ItemStack> network,
                                 CraftingRipperPatterns.CatalogSnapshot base) {
        this.level = level;
        this.network = unique(network);
        dynamic = base != null;
        if (base != null) {
            patterns.addAll(base.patterns());
            for (var pattern : base.patterns()) {
                if (pattern instanceof CatalogCraftingPattern crafting) craftingPatterns.put(crafting, crafting);
            }
            for (var report : base.report().recipes()) reports.put(report.recipeId(), report);
        }
    }

    static CraftingRipperPatterns.CatalogSnapshot build(Level level, List<ItemStack> network,
                                                       CraftingRipperPatterns.CatalogSnapshot base) {
        return new CraftingRipperCatalog(level, network, base).build();
    }

    private CraftingRipperPatterns.CatalogSnapshot build() {
        var recipes = new ArrayList<>(((ServerLevel) level).getServer().getRecipeManager().getRecipes());
        recipes.sort(java.util.Comparator.comparingInt(holder -> propagationOrder(holder.value())));
        for (var holder : recipes) {
            var recipe = holder.value();
            if (recipe.getType() != RecipeType.CRAFTING && recipe.getType() != RecipeType.SMITHING
                    && recipe.getType() != RecipeType.STONECUTTING) continue;
            if (!(recipe instanceof CraftingRecipe || recipe instanceof SmithingRecipe
                    || recipe instanceof StonecutterRecipe)) continue;
            current = holder;
            added = 0;
            status = "NO_CONCRETE_INPUT";
            failures.clear();
            try {
                if (recipe instanceof CraftingRecipe crafting) crafting(crafting);
                else if (recipe instanceof SmithingRecipe smithing) smithing(smithing);
                else stonecutting((StonecutterRecipe) recipe);
            } catch (RuntimeException failure) {
                failure(failure);
            }
            String id = holder.id().identifier().toString();
            var previous = reports.get(id);
            int count = added + (previous == null ? 0 : previous.variants());
            String outcome = count > 0 ? "ENUMERATED" : status;
            if (!failures.isEmpty()) outcome += ":" + String.join(" | ", failures);
            reports.put(id, new CraftingRipperPatterns.RecipeCoverage(id,
                    BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(), count, outcome));
        }
        var result = List.copyOf(patterns);
        return new CraftingRipperPatterns.CatalogSnapshot(result,
                new CraftingRipperPatterns.CatalogReport(List.copyOf(reports.values()), result.size()));
    }

    private static int propagationOrder(Recipe<?> recipe) {
        if (recipe instanceof appeng.recipes.game.RemoveItemUpgradeRecipe) return 4;
        if (recipe instanceof FireworkStarFadeRecipe) return 2;
        if (recipe instanceof FireworkRocketRecipe) return 3;
        return recipe instanceof FireworkStarRecipe ? 1 : 0;
    }

    private void crafting(CraftingRecipe recipe) {
        if (recipe instanceof appeng.recipes.game.StorageCellUpgradeRecipe upgrade) {
            var choices = emptyChoices();
            choices.set(0, enrichDisplay(List.of(upgrade.getInputCell().getDefaultInstance())));
            choices.set(1, enrichDisplay(List.of(upgrade.getInputComponent().getDefaultInstance())));
            for (var cell : choices.get(0)) for (var component : choices.get(1)) {
                if (dynamic && !isNetworkVariant(cell) && !isNetworkVariant(component)) continue;
                var grid = emptyGrid();
                grid[0] = cell;
                grid[1] = component;
                craft(recipe, grid, choices);
            }
            return;
        }
        boolean displayed = false;
        for (var display : recipe.display()) {
            List<SlotDisplay> slots;
            int width;
            if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                if (shaped.width() > 3 || shaped.height() > 3) continue;
                slots = shaped.ingredients();
                width = shaped.width();
            } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                slots = shapeless.ingredients();
                width = 3;
            } else continue;
            if (slots.isEmpty() || slots.size() > 9) continue;
            displayed = true;
            var choices = emptyChoices();
            boolean enriched = false;
            for (int i = 0; i < slots.size(); i++) {
                var basic = displayChoices(slots.get(i));
                var values = enrichDisplay(basic);
                enriched |= values.size() > basic.size();
                choices.set((i / width) * 3 + i % width, values);
            }
            if (dynamic && !enriched) continue;
            if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
                basis(choices, grid -> craft(recipe, grid, choices));
            } else {
                product(choices, 0, emptyGrid(), grid -> craft(recipe, grid, choices));
            }
        }
        if (displayed) {
            if (dynamic && recipe instanceof DyeRecipe) {
                var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                var json = Recipe.CODEC.encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
                mixedDyes(recipe, field(json, "target"), field(json, "dye"));
            }
            return;
        }
        var placement = recipe.placementInfo();
        if (!placement.isImpossibleToPlace() && !placement.slotsToIngredientIndex().isEmpty()
                && placement.slotsToIngredientIndex().size() <= 9) {
            var choices = emptyChoices();
            for (int i = 0; i < placement.slotsToIngredientIndex().size(); i++) {
                int ingredient = placement.slotsToIngredientIndex().getInt(i);
                if (ingredient >= 0) choices.set(i, alternatives(placement.ingredients().get(ingredient)));
            }
            basis(choices, grid -> craft(recipe, grid, choices));
            return;
        }
        special(recipe);
    }

    private List<ItemStack> displayChoices(SlotDisplay display) {
        if (display == SlotDisplay.Empty.INSTANCE) return List.of(ItemStack.EMPTY);
        return unique(display.resolveForStacks(SlotDisplayContext.fromLevel(level)));
    }

    private List<ItemStack> enrichDisplay(List<ItemStack> basic) {
        var result = new ArrayList<>(basic);
        for (var stack : network) {
            if (basic.stream().anyMatch(choice -> !choice.isEmpty() && choice.is(stack.getItem()))) result.add(stack);
        }
        return unique(result);
    }

    private List<ItemStack> alternatives(Ingredient ingredient) {
        var result = new ArrayList<>(displayChoices(ingredient.display()));
        ingredient.items().forEach(item -> result.add(item.value().getDefaultInstance()));
        for (var stack : network) if (ingredient.test(stack)) result.add(stack);
        for (var stack : derived) if (ingredient.test(stack)) result.add(stack);
        result.removeIf(stack -> stack.isEmpty() || !ingredient.test(stack));
        return unique(result);
    }

    private void smithing(SmithingRecipe recipe) {
        if (recipe.templateIngredient().isEmpty() || recipe.additionIngredient().isEmpty()) {
            var templates = recipe.templateIngredient().map(this::alternatives).orElse(List.of(ItemStack.EMPTY));
            var additions = recipe.additionIngredient().map(this::alternatives).orElse(List.of(ItemStack.EMPTY));
            for (var template : templates) for (var base : alternatives(recipe.baseIngredient())) {
                for (var addition : additions) {
                    if (dynamic && !isNetworkVariant(template) && !isNetworkVariant(base)
                            && !isNetworkVariant(addition)) continue;
                    attempt(() -> {
                        var input = new SmithingRecipeInput(template, base, addition);
                        if (!recipe.matches(input, level)) return;
                        var pattern = CatalogSmithingPattern.fromRecipe(current, input, level);
                        if (CraftingRipperPatterns.supportsPattern(pattern, level) && patterns.add(pattern)) added++;
                    });
                }
            }
            return;
        }
        for (var template : alternatives(recipe.templateIngredient().get())) {
            for (var base : alternatives(recipe.baseIngredient())) {
                for (var addition : alternatives(recipe.additionIngredient().get())) {
                    if (dynamic && !isNetworkVariant(template) && !isNetworkVariant(base)
                            && !isNetworkVariant(addition)) continue;
                    attempt(() -> {
                        var input = new SmithingRecipeInput(template, base, addition);
                        if (!recipe.matches(input, level)) return;
                        var output = recipe.assemble(input);
                        if (!output.isEmpty()) add(PatternDetailsHelper.encodeSmithingTablePattern(cast(current),
                                AEItemKey.of(template), AEItemKey.of(base), AEItemKey.of(addition),
                                AEItemKey.of(output), false));
                    });
                }
            }
        }
    }

    private boolean isNetworkVariant(ItemStack stack) {
        return network.stream().anyMatch(value -> ItemStack.isSameItemSameComponents(value, stack))
                && !ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance());
    }

    private void stonecutting(StonecutterRecipe recipe) {
        for (var input : alternatives(recipe.input())) {
            if (dynamic && !isNetworkVariant(input)) continue;
            attempt(() -> {
                var frame = new SingleRecipeInput(input);
                if (!recipe.matches(frame, level)) return;
                var result = recipe.assemble(frame);
                if (!result.isEmpty()) add(PatternDetailsHelper.encodeStonecuttingPattern(cast(current),
                        AEItemKey.of(input), AEItemKey.of(result), false));
            });
        }
    }

    private void special(CraftingRecipe recipe) {
        status = "NEEDS_NETWORK_INPUT";
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = Recipe.CODEC.encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
        if (recipe instanceof BannerDuplicateRecipe) {
            var banners = field(json, "banner");
            pairs(banners, banners, recipe);
        } else if (recipe instanceof BookCloningRecipe) {
            repeated(recipe, field(json, "source"), field(json, "material"), 1, 8);
        } else if (recipe instanceof ShieldDecorationRecipe) {
            pairs(field(json, "banner"), field(json, "target"), recipe);
        } else if (recipe instanceof MapExtendingRecipe) {
            status = "WORLD_POST_PROCESSING_NEEDS_NETWORK_INPUT";
            for (var map : field(json, "map")) for (var material : field(json, "material")) {
                var grid = new ItemStack[9];
                Arrays.fill(grid, material);
                grid[4] = map;
                craft(recipe, grid, singletonChoices(grid));
            }
        } else if (recipe instanceof RepairItemRecipe) {
            var repair = new ArrayList<ItemStack>();
            for (var stack : network) if (stack.isDamageableItem()) repair.add(stack);
            pairs(repair, repair, recipe);
        } else if (recipe instanceof appeng.recipes.game.FacadeRecipe) {
            var blocks = new ArrayList<ItemStack>();
            for (var item : BuiltInRegistries.ITEM) {
                if (item instanceof net.minecraft.world.item.BlockItem) blocks.add(item.getDefaultInstance());
            }
            blocks.addAll(network);
            for (var block : unique(blocks)) {
                if (dynamic && !isNetworkVariant(block)) continue;
                var grid = emptyGrid();
                grid[4] = block;
                for (int slot : new int[]{1, 3, 5, 7}) grid[slot] = appeng.core.definitions.AEParts.CABLE_ANCHOR.stack();
                craft(recipe, grid, singletonChoices(grid));
            }
        } else if (recipe instanceof appeng.recipes.game.AddItemUpgradeRecipe) {
            for (var machine : appeng.api.upgrades.Upgrades.getUpgradableItems().entrySet()) {
                var targets = new ArrayList<ItemStack>();
                targets.add(machine.getKey().asItem().getDefaultInstance());
                for (var stack : network) if (stack.is(machine.getKey().asItem())) targets.add(stack);
                var cards = machine.getValue().stream().map(item -> item.getDefaultInstance()).toList();
                for (var target : unique(targets)) {
                    if (dynamic && !isNetworkVariant(target)) continue;
                    repeated(recipe, List.of(target), cards, 1, 8);
                }
            }
        } else if (recipe instanceof appeng.recipes.game.RemoveItemUpgradeRecipe) {
            var inputs = new ArrayList<>(network);
            inputs.addAll(derived);
            for (var stack : unique(inputs)) {
                if (!(stack.getItem() instanceof appeng.api.upgrades.IUpgradeableItem)) continue;
                var grid = emptyGrid();
                grid[0] = stack;
                craft(recipe, grid, singletonChoices(grid));
            }
        } else if (recipe instanceof DecoratedPotRecipe) {
            var choices = emptyChoices();
            choices.set(1, field(json, "back"));
            choices.set(3, field(json, "left"));
            choices.set(5, field(json, "right"));
            choices.set(7, field(json, "front"));
            basis(choices, grid -> craft(recipe, grid, choices));
            if (dynamic) {
                var actual = emptyChoices();
                for (int slot : new int[]{1, 3, 5, 7}) {
                    actual.set(slot, choices.get(slot).stream().filter(this::presentInNetwork).toList());
                }
                product(actual, 0, emptyGrid(), grid -> craft(recipe, grid, actual));
            }
        } else if (recipe instanceof FireworkStarRecipe) {
            var shapes = new ArrayList<ItemStack>();
            shapes.add(ItemStack.EMPTY);
            for (var shape : json.getAsJsonObject("shapes").entrySet()) {
                shapes.addAll(alternatives(Ingredient.CODEC.parse(ops, shape.getValue()).getOrThrow()));
            }
            var choices = emptyChoices();
            choices.set(0, field(json, "fuel"));
            choices.set(1, field(json, "dye"));
            choices.set(2, unique(shapes));
            choices.set(3, optional(field(json, "trail")));
            choices.set(4, optional(field(json, "twinkle")));
            product(choices, 0, emptyGrid(), grid -> craft(recipe, grid, choices));
            mixedDyes(recipe, field(json, "fuel"), field(json, "dye"));
        } else if (recipe instanceof FireworkStarFadeRecipe) {
            repeated(recipe, field(json, "target"), field(json, "dye"), 1, 8);
            mixedDyes(recipe, field(json, "target"), field(json, "dye"));
        } else if (recipe instanceof FireworkRocketRecipe) {
            var stars = field(json, "star");
            for (int fuelCount = 1; fuelCount <= 3; fuelCount++) {
                for (var shell : field(json, "shell")) for (var fuel : field(json, "fuel")) {
                    var grid = emptyGrid();
                    grid[0] = shell;
                    for (int slot = 1; slot <= fuelCount; slot++) grid[slot] = fuel;
                    craft(recipe, grid, singletonChoices(grid));
                    for (var star : stars) {
                        for (int count = 1; count <= 8 - fuelCount; count++) {
                            var withStar = grid.clone();
                            for (int slot = fuelCount + 1; slot <= fuelCount + count; slot++) withStar[slot] = star;
                            craft(recipe, withStar, singletonChoices(withStar));
                        }
                    }
                }
            }
        } else {
            status = "NO_DISPLAY_OR_PLACEMENT";
        }
    }

    private boolean presentInNetwork(ItemStack stack) {
        return network.stream().anyMatch(value -> ItemStack.isSameItemSameComponents(value, stack));
    }

    private List<ItemStack> field(JsonObject json, String field) {
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        if (!json.has(field)) throw new IllegalArgumentException("Missing recipe ingredient: " + field);
        return alternatives(Ingredient.CODEC.parse(ops, json.get(field)).getOrThrow());
    }

    private void pairs(List<ItemStack> first, List<ItemStack> second, CraftingRecipe recipe) {
        for (var a : first) for (var b : second) {
            var grid = emptyGrid();
            grid[0] = a;
            grid[1] = b;
            craft(recipe, grid, singletonChoices(grid));
        }
    }

    private void repeated(CraftingRecipe recipe, List<ItemStack> sources, List<ItemStack> materials,
                          int minimum, int maximum) {
        for (var source : sources) for (var material : materials) {
            for (int count = minimum; count <= maximum; count++) {
                var grid = emptyGrid();
                grid[0] = source;
                Arrays.fill(grid, 1, count + 1, material);
                craft(recipe, grid, singletonChoices(grid));
            }
        }
    }

    private void mixedDyes(CraftingRecipe recipe, List<ItemStack> sources, List<ItemStack> dyes) {
        if (!dynamic) return;
        var actual = dyes.stream().filter(this::presentInNetwork).toList();
        // Publish concrete common mixtures of observed dyes, not every possible
        // eight-slot color/component universe whenever a storage key changes.
        for (var source : sources) {
            var grid = emptyGrid();
            grid[0] = source;
            combinations(actual, 0, 1, 3, grid, value -> craft(recipe, value, singletonChoices(value)));
            Arrays.fill(grid, 1, 9, ItemStack.EMPTY);
            for (int count = 1; count <= Math.min(8, actual.size()); count++) {
                grid[count] = actual.get(count - 1);
                craft(recipe, grid, singletonChoices(grid));
            }
        }
    }

    private void craft(CraftingRecipe recipe, ItemStack[] grid, List<List<ItemStack>> choices) {
        attempt(() -> {
            var detached = Arrays.stream(grid).map(stack -> stack.copyWithCount(1)).toArray(ItemStack[]::new);
            var input = CraftingInput.of(3, 3, Arrays.asList(detached));
            if (!recipe.matches(input, level)) return;
            var output = recipe.assemble(input);
            if (output.isEmpty()) return;
            var encoded = PatternDetailsHelper.encodeCraftingPattern(cast(current), grid, output, true, false);
            var pattern = CatalogCraftingPattern.create(AEItemKey.of(encoded), level, recipe, choices);
            if (!CraftingRipperPatterns.supportsPattern(pattern, level)) {
                failures.add("NATIVE_ENCODING_REJECTED");
                return;
            }
            var previous = craftingPatterns.get(pattern);
            if (previous == null) {
                craftingPatterns.put(pattern, pattern);
                if (patterns.add(pattern)) added++;
            } else {
                var merged = previous.merge(pattern, level);
                if (merged != previous) {
                    patterns.remove(previous);
                    patterns.add(merged);
                    craftingPatterns.put(merged, merged);
                }
            }
            if ((recipe instanceof FireworkStarRecipe && derivedRecipes.add(current.id().toString()))
                    || (recipe instanceof appeng.recipes.game.AddItemUpgradeRecipe
                        && derivedRecipes.add(current.id() + ":" + BuiltInRegistries.ITEM.getKey(output.getItem())))) {
                derived.add(output.copyWithCount(1));
            }
        });
    }

    private void add(ItemStack encoded) {
        var pattern = PatternDetailsHelper.decodePattern(encoded, level);
        if (!CraftingRipperPatterns.supportsPattern(pattern, level)) {
            failures.add("NATIVE_ENCODING_REJECTED");
        } else if (patterns.add(pattern)) added++;
    }

    private void attempt(Runnable work) {
        try { work.run(); } catch (RuntimeException failure) { failure(failure); }
    }

    private void failure(RuntimeException failure) {
        failures.add("NATIVE_ENCODING_REJECTED(" + failure.getClass().getSimpleName() + ":"
                + String.valueOf(failure.getMessage()).replace('\n', ' ').replace('\r', ' ') + ")");
    }

    private static void basis(List<List<ItemStack>> choices, Consumer<ItemStack[]> consumer) {
        if (choices.stream().anyMatch(List::isEmpty)) return;
        var base = choices.stream().map(List::getFirst).toArray(ItemStack[]::new);
        consumer.accept(base);
        for (int slot = 0; slot < choices.size(); slot++) {
            for (var value : choices.get(slot)) {
                var single = base.clone();
                single[slot] = value;
                consumer.accept(single);
                var uniform = base.clone();
                for (int other = 0; other < choices.size(); other++) {
                    if (choices.get(other).stream().anyMatch(candidate -> ItemStack.isSameItemSameComponents(candidate, value))) {
                        uniform[other] = value;
                    }
                }
                consumer.accept(uniform);
            }
        }
    }

    private static void product(List<List<ItemStack>> choices, int slot, ItemStack[] grid,
                                Consumer<ItemStack[]> consumer) {
        if (slot == grid.length) { consumer.accept(grid); return; }
        for (var value : choices.get(slot)) {
            grid[slot] = value;
            product(choices, slot + 1, grid, consumer);
        }
    }

    private static void combinations(List<ItemStack> choices, int first, int slot, int end, ItemStack[] grid,
                                     Consumer<ItemStack[]> consumer) {
        if (slot == end) { consumer.accept(grid); return; }
        for (int choice = first; choice < choices.size(); choice++) {
            grid[slot] = choices.get(choice);
            combinations(choices, choice, slot + 1, end, grid, consumer);
        }
    }

    private static ItemStack[] emptyGrid() {
        var grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        return grid;
    }

    private static List<List<ItemStack>> emptyChoices() {
        return new ArrayList<>(Collections.nCopies(9, List.of(ItemStack.EMPTY)));
    }

    private static List<List<ItemStack>> singletonChoices(ItemStack[] grid) {
        return Arrays.stream(grid).map(List::of).toList();
    }

    private static List<ItemStack> optional(List<ItemStack> values) {
        var result = new ArrayList<ItemStack>();
        result.add(ItemStack.EMPTY);
        result.addAll(values);
        return result;
    }

    private static List<ItemStack> unique(List<ItemStack> values) {
        var result = new LinkedHashMap<AEItemKey, ItemStack>();
        boolean empty = false;
        for (var value : values) {
            if (value.isEmpty()) empty = true;
            else result.putIfAbsent(AEItemKey.of(value), value.copyWithCount(1));
        }
        var stacks = new ArrayList<>(result.values());
        if (empty) stacks.addFirst(ItemStack.EMPTY);
        return List.copyOf(stacks);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> RecipeHolder<T> cast(RecipeHolder<?> holder) {
        return (RecipeHolder<T>) holder;
    }
}
