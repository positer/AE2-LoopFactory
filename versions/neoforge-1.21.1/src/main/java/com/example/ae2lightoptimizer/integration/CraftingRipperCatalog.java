package com.example.ae2lightoptimizer.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.core.definitions.AEParts;
import appeng.items.materials.UpgradeCardItem;
import appeng.recipes.game.AddItemUpgradeRecipe;
import appeng.recipes.game.FacadeRecipe;
import appeng.recipes.game.RemoveItemUpgradeRecipe;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Resolves actual native recipe inputs. Recipe-book visibility and Java implementation class are not
 * admission rules. Every advertised variant has passed matches, assemble and the pinned AE2 decoder.
 */
final class CraftingRipperCatalog {
    private final Level level;
    private final List<ItemStack> network;
    private final LinkedHashMap<AEItemKey, ItemStack> candidates = new LinkedHashMap<>();
    private final LinkedHashSet<IPatternDetails> patterns = new LinkedHashSet<>();
    private final List<CraftingRipperPatterns.RecipeCoverage> coverage = new ArrayList<>();
    private RecipeHolder<?> current;
    private int accepted;
    private int matched;
    private int nativeOnly;
    private String reason;

    private CraftingRipperCatalog(Level level, List<ItemStack> networkInputs) {
        this.level = level;
        this.network = unique(networkInputs);
        BuiltInRegistries.ITEM.forEach(item -> remember(item.getDefaultInstance()));
        network.forEach(this::remember);
        // These components are finite registry entries, rather than invented potion contents.
        level.registryAccess().registryOrThrow(Registries.POTION).holders().forEach(potion -> {
            var stack = new ItemStack(Items.LINGERING_POTION);
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
            remember(stack);
        });
    }

    static CraftingRipperPatterns.CatalogSnapshot build(Level level, List<ItemStack> networkInputs) {
        if (level == null) return new CraftingRipperPatterns.CatalogSnapshot(List.of(),
                new CraftingRipperPatterns.CatalogReport(List.of(), 0));
        var builder = new CraftingRipperCatalog(level, networkInputs);
        var recipes = new ArrayList<>(level.getRecipeManager().getRecipes());
        recipes.sort(Comparator.comparingInt((RecipeHolder<?> holder) -> order(holder.value()))
                .thenComparing(holder -> holder.id().toString()));
        for (var holder : recipes) builder.addRecipe(holder);
        return new CraftingRipperPatterns.CatalogSnapshot(List.copyOf(builder.patterns),
                new CraftingRipperPatterns.CatalogReport(builder.coverage, builder.patterns.size()));
    }

    private static int order(Recipe<?> recipe) {
        if (recipe instanceof FireworkStarRecipe) return 1;
        if (recipe instanceof FireworkStarFadeRecipe) return 2;
        if (recipe instanceof FireworkRocketRecipe) return 3;
        return 0;
    }

    private void addRecipe(RecipeHolder<?> holder) {
        var recipe = holder.value();
        var type = recipe.getType();
        if (type != RecipeType.CRAFTING && type != RecipeType.SMITHING && type != RecipeType.STONECUTTING) return;
        current = holder;
        accepted = 0;
        matched = 0;
        nativeOnly = 0;
        reason = "NEEDS_NETWORK_INPUT";
        try {
            if (recipe instanceof CraftingRecipe crafting && type == RecipeType.CRAFTING) {
                if (!special(crafting)) {
                    ingredients(crafting);
                }
            } else if (recipe instanceof StonecutterRecipe stonecutting && type == RecipeType.STONECUTTING) {
                stonecutting(stonecutting);
            } else if (recipe instanceof SmithingRecipe smithing && type == RecipeType.SMITHING) {
                smithing(smithing);
            } else {
                reason = "NATIVE_RECIPE_INPUT_API_UNAVAILABLE";
            }
        } catch (RuntimeException invalidRecipe) {
            reason = "RECIPE_ERROR:" + invalidRecipe.getClass().getSimpleName();
        }
        coverage.add(new CraftingRipperPatterns.RecipeCoverage(holder.id().toString(), type.toString(),
                accepted, accepted > 0 ? nativeOnly > 0 ? "ENUMERATED_WITH_NATIVE_EXECUTION" : "ENUMERATED"
                        : reason));
    }

    private void ingredients(CraftingRecipe recipe) {
        var ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            var encoded = Recipe.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), recipe)
                    .result().orElse(null);
            var extracted = new ArrayList<Ingredient>();
            readIngredients(encoded, extracted);
            ingredients = net.minecraft.core.NonNullList.create();
            ingredients.addAll(extracted);
        }
        if (ingredients.isEmpty()) {
            reason = "INPUT_RESOLVER_UNAVAILABLE";
            return;
        }
        if (ingredients.size() > 9) {
            reason = "EXCEEDS_CRAFTING_GRID";
            return;
        }
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        var choices = new ArrayList<List<ItemStack>>(ingredients.size());
        for (var ingredient : ingredients) {
            var entries = alternatives(ingredient);
            if (entries.isEmpty()) return;
            choices.add(entries);
        }
        var baseline = new ItemStack[choices.size()];
        // First representatives can fail recipes with cross-slot constraints. Find a real matching frame.
        boolean found = firstMatch(choices, baseline, 0, width, recipe);
        if (!found && !(recipe instanceof ShapedRecipe)) {
            for (int alternateWidth = 1; alternateWidth <= 2 && !found; alternateWidth++) {
                if (choices.size() > alternateWidth * 3) continue;
                found = firstMatch(choices, baseline, 0, alternateWidth, recipe);
                if (found) width = alternateWidth;
            }
        }
        if (!found) return;
        final int frameWidth = width;
        crafting(recipe, toGrid(baseline, width), true);
        for (int slot = 0; slot < choices.size(); slot++) {
            for (var choice : choices.get(slot)) {
                var variant = baseline.clone();
                variant[slot] = choice;
                crafting(recipe, toGrid(variant, width), true);
                // Repeated ingredients often require the same component or species in every position.
                for (int other = 0; other < ingredients.size(); other++) {
                    if (ingredients.get(other).equals(ingredients.get(slot))) variant[other] = choice;
                }
                crafting(recipe, toGrid(variant, width), true);
            }
        }
        // Non-vanilla implementations may derive their output from several simultaneous choices.
        // Their finite ingredient witnesses must be combined, rather than guessing a displayed output.
        if (recipe.getClass() != ShapedRecipe.class && recipe.getClass() != ShapelessRecipe.class) {
            products(choices, new ItemStack[choices.size()], 0,
                    values -> crafting(recipe, toGrid(values, frameWidth), true));
        }
    }

    private boolean firstMatch(List<List<ItemStack>> choices, ItemStack[] result, int slot, int width,
            CraftingRecipe recipe) {
        if (slot == result.length) return recipe.matches(input(toGrid(result, width)), level);
        for (var choice : choices.get(slot)) {
            result[slot] = choice;
            if (firstMatch(choices, result, slot + 1, width, recipe)) return true;
        }
        return false;
    }

    private void stonecutting(StonecutterRecipe recipe) {
        if (recipe.getIngredients().isEmpty()) return;
        for (var stack : alternatives(recipe.getIngredients().getFirst())) {
            var input = new SingleRecipeInput(stack);
            if (!recipe.matches(input, level)) continue;
            var output = recipe.assemble(input, level.registryAccess());
            if (output.isEmpty()) continue;
            matched++;
            try {
                add(PatternDetailsHelper.encodeStonecuttingPattern(cast(current), AEItemKey.of(stack),
                        AEItemKey.of(output), true));
            } catch (RuntimeException invalidPattern) {
                reason = "NATIVE_ENCODING_REJECTED:" + invalidPattern.getClass().getSimpleName();
            }
        }
    }

    private void smithing(SmithingRecipe recipe) {
        // The serialized native ingredients retain NeoForge component predicates; default registry
        // items alone do not witness a DataComponentIngredient.
        var pool = new LinkedHashMap<AEItemKey, ItemStack>(candidates);
        var encoded = Recipe.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), recipe)
                .result().orElse(null);
        var declared = new ArrayList<Ingredient>();
        readIngredients(encoded, declared);
        declared.forEach(ingredient -> alternatives(ingredient).forEach(stack -> put(pool, stack)));
        var templates = pool.values().stream().filter(recipe::isTemplateIngredient).toList();
        var bases = pool.values().stream().filter(recipe::isBaseIngredient).toList();
        var additions = pool.values().stream().filter(recipe::isAdditionIngredient).toList();
        for (var template : templates) for (var base : bases) for (var addition : additions) {
            var input = new SmithingRecipeInput(template, base, addition);
            if (!recipe.matches(input, level)) continue;
            var output = recipe.assemble(input, level.registryAccess());
            if (output.isEmpty()) continue;
            matched++;
            try {
                add(PatternDetailsHelper.encodeSmithingTablePattern(cast(current), AEItemKey.of(template),
                        AEItemKey.of(base), AEItemKey.of(addition), AEItemKey.of(output), false));
            } catch (RuntimeException invalidPattern) {
                reason = "NATIVE_ENCODING_REJECTED:" + invalidPattern.getClass().getSimpleName();
            }
        }
    }

    private boolean special(CraftingRecipe recipe) {
        if (recipe instanceof ArmorDyeRecipe) {
            for (var armor : find(stack -> stack.is(ItemTags.DYEABLE))) {
                for (var dyes : dyes(8)) crafting(recipe, sequence(armor, dyes), false);
            }
        } else if (recipe instanceof BannerDuplicateRecipe) {
            for (var banner : find(stack -> stack.getItem() instanceof BannerItem
                    && stack.getOrDefault(DataComponents.BANNER_PATTERNS,
                            net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY).layers().size() > 0)) {
                for (var blank : find(stack -> stack.getItem() instanceof BannerItem)) {
                    crafting(recipe, row(banner, blank), false);
                }
            }
        } else if (recipe instanceof BookCloningRecipe) {
            for (var book : find(stack -> stack.is(Items.WRITTEN_BOOK)
                    && stack.has(DataComponents.WRITTEN_BOOK_CONTENT))) {
                for (int copies = 1; copies <= 8; copies++) crafting(recipe,
                        sequence(book, copies(Items.WRITABLE_BOOK, copies)), false);
            }
        } else if (recipe instanceof MapCloningRecipe) {
            for (var map : find(stack -> stack.is(Items.FILLED_MAP) && stack.has(DataComponents.MAP_ID))) {
                for (int copies = 1; copies <= 8; copies++) crafting(recipe,
                        sequence(map, copies(Items.MAP, copies)), false);
            }
        } else if (recipe instanceof RepairItemRecipe) {
            var repairable = find(stack -> stack.has(DataComponents.DAMAGE) && stack.has(DataComponents.MAX_DAMAGE));
            var byItem = new LinkedHashMap<Item, List<ItemStack>>();
            repairable.forEach(stack -> byItem.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>()).add(stack));
            for (var group : byItem.values()) for (var first : group) for (var second : group) {
                crafting(recipe, row(first, second), false);
            }
        } else if (recipe instanceof ShulkerBoxColoring) {
            for (var box : find(stack -> stack.getItem() instanceof BlockItem block
                    && block.getBlock() instanceof ShulkerBoxBlock)) {
                for (var dye : find(stack -> stack.getItem() instanceof DyeItem)) {
                    crafting(recipe, row(box, dye), false);
                }
            }
        } else if (recipe instanceof ShieldDecorationRecipe) {
            for (var shield : find(stack -> stack.is(Items.SHIELD))) {
                for (var banner : find(stack -> stack.getItem() instanceof BannerItem)) {
                    crafting(recipe, row(shield, banner), false);
                }
            }
        } else if (recipe instanceof SuspiciousStewRecipe) {
            for (var flower : find(stack -> stack.is(ItemTags.SMALL_FLOWERS))) {
                crafting(recipe, row(new ItemStack(Items.BOWL), new ItemStack(Items.BROWN_MUSHROOM),
                        new ItemStack(Items.RED_MUSHROOM), flower), false);
            }
        } else if (recipe instanceof TippedArrowRecipe) {
            for (var potion : find(stack -> stack.is(Items.LINGERING_POTION))) {
                var grid = filled(Items.ARROW);
                grid[4] = potion;
                crafting(recipe, grid, false);
            }
        } else if (recipe instanceof DecoratedPotRecipe) {
            decoratedPots(recipe);
        } else if (recipe instanceof FireworkStarRecipe) {
            fireworkStars(recipe);
        } else if (recipe instanceof FireworkStarFadeRecipe) {
            for (var star : find(stack -> stack.is(Items.FIREWORK_STAR))) {
                for (var dyes : dyes(8)) crafting(recipe, sequence(star, dyes), false);
            }
        } else if (recipe instanceof FireworkRocketRecipe) {
            fireworkRockets(recipe);
        } else if (recipe instanceof FacadeRecipe) {
            for (var center : find(stack -> stack.getItem() instanceof BlockItem)) {
                var grid = empty();
                for (int slot : new int[] { 1, 3, 5, 7 }) grid[slot] = AEParts.CABLE_ANCHOR.stack();
                grid[4] = center;
                crafting(recipe, grid, false);
            }
        } else if (recipe instanceof AddItemUpgradeRecipe) {
            for (var item : find(stack -> stack.getItem() instanceof IUpgradeableItem)) {
                for (var card : find(stack -> stack.getItem() instanceof UpgradeCardItem)) {
                    for (int count = 1; count <= 8; count++) crafting(recipe,
                            sequence(item, copies(card, count)), false);
                }
                var heldCards = network.stream().filter(stack -> stack.getItem() instanceof UpgradeCardItem).toList();
                if (heldCards.size() > 1 && heldCards.size() <= 8) crafting(recipe, sequence(item, heldCards), false);
            }
        } else if (recipe instanceof RemoveItemUpgradeRecipe) {
            for (var item : find(stack -> stack.getItem() instanceof IUpgradeableItem)) {
                crafting(recipe, row(item), false);
            }
        } else return false;
        return true;
    }

    private void decoratedPots(CraftingRecipe recipe) {
        var all = find(stack -> stack.is(ItemTags.DECORATED_POT_INGREDIENTS));
        for (var shard : all) {
            var uniform = empty();
            for (int slot : new int[] { 1, 3, 5, 7 }) uniform[slot] = shard;
            crafting(recipe, uniform, false);
            for (int slot : new int[] { 1, 3, 5, 7 }) {
                var grid = empty();
                for (int side : new int[] { 1, 3, 5, 7 }) grid[side] = new ItemStack(Items.BRICK);
                grid[slot] = shard;
                crafting(recipe, grid, false);
            }
        }
        var available = network.stream().filter(stack -> stack.is(ItemTags.DECORATED_POT_INGREDIENTS)).toList();
        if (!available.isEmpty()) products(List.of(available, available, available, available), new ItemStack[4], 0,
                sides -> {
                    var grid = empty();
                    int[] slots = { 1, 3, 5, 7 };
                    for (int side = 0; side < 4; side++) grid[slots[side]] = sides[side];
                    crafting(recipe, grid, false);
                });
    }

    private void fireworkStars(CraftingRecipe recipe) {
        var shapes = new Item[] { Items.AIR, Items.FIRE_CHARGE, Items.FEATHER, Items.GOLD_NUGGET,
                Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL, Items.CREEPER_HEAD, Items.PLAYER_HEAD,
                Items.DRAGON_HEAD, Items.ZOMBIE_HEAD, Items.PIGLIN_HEAD };
        for (var dyes : dyes(8)) for (var shape : shapes) for (int flags = 0; flags < 4; flags++) {
            var entries = new ArrayList<ItemStack>();
            entries.add(new ItemStack(Items.GUNPOWDER));
            entries.addAll(dyes);
            if (shape != Items.AIR) entries.add(new ItemStack(shape));
            if ((flags & 1) != 0) entries.add(new ItemStack(Items.DIAMOND));
            if ((flags & 2) != 0) entries.add(new ItemStack(Items.GLOWSTONE_DUST));
            if (entries.size() <= 9) crafting(recipe, row(entries.toArray(ItemStack[]::new)), false);
        }
    }

    private void fireworkRockets(CraftingRecipe recipe) {
        var stars = find(stack -> stack.is(Items.FIREWORK_STAR) && stack.has(DataComponents.FIREWORK_EXPLOSION));
        for (int duration = 1; duration <= 3; duration++) {
            var base = new ArrayList<ItemStack>();
            base.add(new ItemStack(Items.PAPER));
            base.addAll(copies(Items.GUNPOWDER, duration));
            crafting(recipe, row(base.toArray(ItemStack[]::new)), false);
            for (var star : stars) {
                var entries = new ArrayList<>(base);
                entries.add(star);
                crafting(recipe, row(entries.toArray(ItemStack[]::new)), false);
            }
            var held = network.stream().filter(stack -> stack.is(Items.FIREWORK_STAR)).toList();
            if (!held.isEmpty() && held.size() + base.size() <= 9) {
                base.addAll(held);
                crafting(recipe, row(base.toArray(ItemStack[]::new)), false);
            }
        }
    }

    /** A finite palette basis plus actual network pairs and multi-colour frames. */
    private List<List<ItemStack>> dyes(int max) {
        var result = new ArrayList<List<ItemStack>>();
        for (var dye : find(stack -> stack.getItem() instanceof DyeItem)) result.add(List.of(dye));
        var held = network.stream().filter(stack -> stack.getItem() instanceof DyeItem).toList();
        for (int first = 0; first < held.size(); first++) for (int second = first + 1; second < held.size(); second++) {
            result.add(List.of(held.get(first), held.get(second)));
        }
        for (int count = 3; count <= Math.min(max, held.size()); count++) result.add(held.subList(0, count));
        return result;
    }

    private void crafting(CraftingRecipe recipe, ItemStack[] grid, boolean substitute) {
        try {
            var input = input(grid);
            if (!recipe.matches(input, level)) return;
            var output = recipe.assemble(input, level.registryAccess());
            if (output.isEmpty()) return;
            matched++;
            boolean firstOutput = accepted == 0;
            add(PatternDetailsHelper.encodeCraftingPattern(cast(current), grid, output, substitute, false));
            // One actual output witnesses the next family without recursively expanding all palettes.
            // Network component keys are all retained independently of this baseline representative.
            if (firstOutput && recipe instanceof FireworkStarRecipe) remember(output);
        } catch (RuntimeException invalidPattern) {
            reason = "NATIVE_ENCODING_REJECTED:" + invalidPattern.getClass().getSimpleName();
        }
    }

    private void add(ItemStack definition) {
        var pattern = PatternDetailsHelper.decodePattern(definition, level);
        if (!CraftingRipperPatterns.supportsPattern(pattern, level)) {
            reason = "NATIVE_ENCODING_REJECTED";
            return;
        }
        if (patterns.add(pattern)) {
            accepted++;
            if (CraftingRipperPatterns.requiresNativeExecution(pattern, level)) nativeOnly++;
        }
    }

    private List<ItemStack> alternatives(Ingredient ingredient) {
        if (ingredient.isEmpty()) return List.of(ItemStack.EMPTY);
        var values = new LinkedHashMap<AEItemKey, ItemStack>();
        for (var stack : ingredient.getItems()) if (ingredient.test(stack)) put(values, stack);
        for (var stack : network) if (ingredient.test(stack)) put(values, stack);
        return List.copyOf(values.values());
    }

    private void readIngredients(JsonElement element, List<Ingredient> target) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return;
        if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            if (object.has("item") || object.has("tag") || object.has("type")) {
                var decoded = Ingredient.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), element)
                        .result();
                if (decoded.isPresent()) {
                    target.add(decoded.get());
                    return;
                }
            }
            for (var entry : object.entrySet()) {
                // Recipe result objects are not ingredients, even when they have an item field.
                if (!entry.getKey().equals("result")) readIngredients(entry.getValue(), target);
            }
        } else if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) readIngredients(child, target);
        }
    }

    private List<ItemStack> find(Predicate<ItemStack> predicate) {
        return candidates.values().stream().filter(predicate).toList();
    }

    private void remember(ItemStack stack) { put(candidates, stack); }

    private static void put(Map<AEItemKey, ItemStack> target, ItemStack stack) {
        if (!stack.isEmpty()) target.putIfAbsent(AEItemKey.of(stack), stack.copyWithCount(1));
    }

    private static List<ItemStack> unique(List<ItemStack> stacks) {
        var result = new LinkedHashMap<AEItemKey, ItemStack>();
        stacks.forEach(stack -> put(result, stack));
        return List.copyOf(result.values());
    }

    private static void products(List<List<ItemStack>> options, ItemStack[] values, int index,
            Consumer<ItemStack[]> accept) {
        if (index == values.length) {
            accept.accept(values);
            return;
        }
        for (var stack : options.get(index)) {
            values[index] = stack;
            products(options, values, index + 1, accept);
        }
    }

    private static CraftingInput input(ItemStack[] grid) { return CraftingInput.of(3, 3, Arrays.asList(grid)); }

    private static ItemStack[] empty() {
        var grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        return grid;
    }

    private static ItemStack[] filled(Item item) {
        var grid = empty();
        Arrays.setAll(grid, ignored -> new ItemStack(item));
        return grid;
    }

    private static ItemStack[] row(ItemStack... stacks) {
        var grid = empty();
        for (int i = 0; i < stacks.length && i < 9; i++) grid[i] = stacks[i];
        return grid;
    }

    private static ItemStack[] sequence(ItemStack first, List<ItemStack> rest) {
        var entries = new ArrayList<ItemStack>();
        entries.add(first);
        entries.addAll(rest);
        return row(entries.toArray(ItemStack[]::new));
    }

    private static List<ItemStack> copies(Item item, int count) { return copies(new ItemStack(item), count); }

    private static List<ItemStack> copies(ItemStack stack, int count) {
        var result = new ArrayList<ItemStack>();
        for (int i = 0; i < count; i++) result.add(stack.copyWithCount(1));
        return result;
    }

    private static ItemStack[] toGrid(ItemStack[] values, int width) {
        var grid = empty();
        for (int i = 0; i < values.length; i++) grid[i / width * 3 + i % width] = values[i];
        return grid;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> RecipeHolder<T> cast(RecipeHolder<?> holder) {
        return (RecipeHolder<T>) holder;
    }
}
