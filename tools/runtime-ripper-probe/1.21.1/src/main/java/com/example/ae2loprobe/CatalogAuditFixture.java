package com.example.ae2loprobe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.recipes.game.StorageCellUpgradeRecipe;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.helpers.MachineSource;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.integration.CraftingRipperPatterns;
import com.example.ae2lightoptimizer.item.ModItems;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/** Independent, real-server catalog oracle; does not invoke the production catalog or its whitelist. */
public final class CatalogAuditFixture {
    public static final boolean ENABLED = "catalog_audit".equals(System.getProperty("ae2lo.probe.chain"));
    private static final boolean STOCK_WITNESSES = Boolean.parseBoolean(System.getProperty("ae2lo.probe.catalog.stockWitnesses", "true"));
    private static final int SETTLE_TICKS = 30;
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final Map<String, Row> rows = new LinkedHashMap<>();
    private static final List<Map<String, Object>> lifecycle = new ArrayList<>();
    private static final List<Frame> concreteFrames = new ArrayList<>();
    private static final List<Frame> lateFrames = new ArrayList<>();
    private static final List<Map<String, Object>> stockInsertions = new ArrayList<>();
    private static ServerLevel level;
    private static IGrid grid;
    private static CraftingRipperBlockEntity ripper;
    private static Path output;
    private static Iterator<RecipeHolder<?>> recipeIterator;
    private static List<ItemStack> registryItems;
    private static Set<IPatternDetails> manualPatterns = Set.of();
    private static ItemStack manual = ItemStack.EMPTY;
    private static ItemStack[] originalSlots;
    private static ItemStack originalCard;
    private static long phaseTick;
    private static int stage;
    private static boolean finished;
    private static boolean passed;
    private static boolean lifecyclePassed = true;

    private CatalogAuditFixture() { }

    /** Invoke once after the ordinary test grid is active and finitely charged. */
    public static void begin(ServerLevel serverLevel, IGrid activeGrid, CraftingRipperBlockEntity block, Path evidence) {
        if (level != null) return;
        level = serverLevel;
        grid = activeGrid;
        ripper = block;
        output = evidence.resolve("catalog-report.json");
        try {
            if (!level.getServer().getWorldData().getLevelName().startsWith("AE2LO-Ripper-Probe-")) {
                throw new IllegalStateException("Catalog audit requires the isolated probe world");
            }
            originalCard = ripper.getUpgrades().getStackInSlot(0).copy();
            var inventory = ripper.getLogic().getPatternInv();
            originalSlots = new ItemStack[inventory.size()];
            for (int i = 0; i < inventory.size(); i++) originalSlots[i] = inventory.getStackInSlot(i).copy();
            report.put("oracle", "Live RecipeManager type/ID inventory, native matches + repeated assemble + AE2 encode/decode; no production catalog calls");
            report.put("coverage", "Every recipe ID is classified. Finite ingredient witnesses plus concrete component-bearing special-recipe examples prove omissions; arbitrary component combinations are not exhaustively enumerated.");
            report.put("world", level.getServer().getWorldData().getLevelName());
            report.put("lifecycle", lifecycle);
            report.put("stockWitnessesEnabled", STOCK_WITNESSES);
            report.put("stockInsertions", stockInsertions);
            report.put("executionChecks", "Detached native input extraction, production validateInputs and native output/remainder comparison. Separate actualPublishedPatternExecution checks query the grid's real pattern objects for dye and book component witnesses, including rejected alternative inputs. No crafting job was submitted by this audit.");
            registryItems = BuiltInRegistries.ITEM.stream().map(Item::getDefaultInstance).toList();
            var recipes = new ArrayList<RecipeHolder<?>>(level.getRecipeManager().getRecipes());
            recipes.sort(Comparator.comparing(h -> h.id().toString()));
            recipeIterator = recipes.iterator();
            buildConcreteFrames();
            transition(1, "oracle_inventory");
        } catch (Throwable failure) { fail(failure); }
    }

    /** The caller must let native server ticks run; this never forcibly flushes AE2 provider updates. */
    public static void tick() {
        if (level == null || finished) return;
        try {
            if (stage == 1) {
                for (int i = 0; i < 12 && recipeIterator.hasNext(); i++) inspect(recipeIterator.next());
                if (recipeIterator.hasNext()) return;
                // Use a witnessed ordinary recipe as the retained manual pattern in the isolated fixture.
                manual = rows.values().stream().filter(r -> r.id.equals("minecraft:oak_planks"))
                        .flatMap(r -> r.witnesses.stream()).findFirst()
                        .or(() -> rows.values().stream().filter(r -> !r.special)
                                .flatMap(r -> r.witnesses.stream()).findFirst())
                        .orElseThrow(() -> new IllegalStateException("No encodable manual baseline recipe"))
                        .pattern.getDefinition().toStack();
                ripper.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
                var inventory = ripper.getLogic().getPatternInv();
                for (int i = 0; i < inventory.size(); i++) inventory.setItemDirect(i, ItemStack.EMPTY);
                inventory.setItemDirect(0, manual.copy());
                inventory.setItemDirect(1, manual.copy());
                if (STOCK_WITNESSES) injectFrames(concreteFrames, "before_card");
                transition(2, "manual_wait");
            } else if (stage == 2 && settled()) {
                manualPatterns = networkPatterns();
                check("manual_pattern_published", manualPatterns.contains(PatternDetailsHelper.decodePattern(manual, level)));
                report.put("beforeCard", publication(manualPatterns));
                ripper.getUpgrades().setItemDirect(0, ModItems.LOOP_CARD.get().getDefaultInstance());
                transition(3, "card_wait");
            } else if (stage == 3 && settled()) {
                check("card_installed", ripper.isLoopCardInstalled());
                check("retained_pattern_unchanged", ItemStack.matches(manual, ripper.getLogic().getPatternInv().getStackInSlot(0)));
                check("insertion_locked", !ripper.getLogic().getPatternInv().isItemValid(2, manual));
                var withdrawn = ripper.getLogic().getPatternInv().extractItem(1, 1, false);
                check("retained_pattern_withdrawable", ItemStack.matches(manual, withdrawn));
                var rejected = ripper.getLogic().getPatternInv().insertItem(1, withdrawn.copy(), false);
                check("withdrawn_pattern_cannot_be_reinserted", ItemStack.matches(withdrawn, rejected)
                        && ripper.getLogic().getPatternInv().getStackInSlot(1).isEmpty());
                auditPublished(networkPatterns());
                if (STOCK_WITNESSES) {
                    report.put("beforeLateInsertion", latePublication());
                    report.put("beforeLateInsertionSummary", summary());
                    long lateWitnessCount = 0;
                    for (var row : rows.values()) for (var witness : row.witnesses) {
                        if (witness.fixture.startsWith("late_")) {
                            lateWitnessCount++;
                            check("late_component_output_previously_absent:" + row.id + ":" + witness.fixture,
                                    !published(row, witness));
                        }
                    }
                    check("late_component_native_witnesses_exist", lateWitnessCount >= 2);
                    writeReport(false);
                    injectFrames(lateFrames, "after_card_new_components");
                    transition(7, "component_inventory_refresh_wait");
                    return;
                }
                // Write omissions before removal, so a later lifecycle failure cannot erase the baseline evidence.
                writeReport(false);
                ripper.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
                transition(4, "removed_wait");
            } else if (stage == 7 && level.getGameTime() - phaseTick >= 120) {
                report.put("afterLateInsertion", latePublication());
                for (var row : rows.values()) for (var witness : row.witnesses) {
                    if (witness.fixture.startsWith("late_")) {
                        check("late_component_output_added:" + row.id + ":" + witness.fixture, published(row, witness));
                    }
                }
                auditPublished(networkPatterns());
                writeReport(false);
                ripper.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
                transition(4, "removed_wait");
            } else if (stage == 4 && settled()) {
                var restored = networkPatterns();
                report.put("afterRemoval", publication(restored));
                check("card_removed", !ripper.isLoopCardInstalled());
                check("manual_publication_restored", restored.equals(manualPatterns));
                check("retained_slot_unchanged_after_removal", ItemStack.matches(manual, ripper.getLogic().getPatternInv().getStackInSlot(0)));
                check("insertion_unlocked", ripper.getLogic().getPatternInv().isItemValid(1, manual));
                check("normal_insert_succeeds", ripper.getLogic().getPatternInv().insertItem(1, manual.copy(), false).isEmpty());
                // Leave the final screenshot in card mode after proving normal-state restoration.
                ripper.getUpgrades().setItemDirect(0, ModItems.LOOP_CARD.get().getDefaultInstance());
                transition(5, "reinstall_wait");
            } else if (stage == 5 && settled()) {
                check("reinstall_republishes_catalog", networkPatterns().size() == ((Number) report.get("actualPatternCount")).intValue());
                  passed = lifecyclePassed && ((Number) report.get("missingPublishableRecipeCount")).intValue() == 0
                          && ((Number) report.get("missingWitnessCount")).intValue() == 0
                          && ((Number) report.get("unexpectedTypePatternCount")).intValue() == 0
                          && Boolean.TRUE.equals(report.get("actualCatalogExecutionChecksPassed"));
                finished = true;
                transition(6, passed ? "catalog_pass" : "catalog_omissions");
                writeReport(true);
            }
        } catch (Throwable failure) { fail(failure); }
    }

    public static boolean isFinished() { return finished; }
    public static boolean passed() { return passed; }
    public static Map<String, Object> summary() {
        var result = new LinkedHashMap<String, Object>();
        for (String key : List.of("passed", "actualPatternCount", "publishableRecipeCount", "missingPublishableRecipeCount",
                "missingWitnessCount", "missingExecutableWitnessCount", "executionBoundaryWitnessCount",
                "actualCatalogExecutionChecksPassed", "actualCatalogExecutionCases", "actualCatalogInvalidSelectedPatterns",
                "unprovenAllowedRecipeCount", "lifecyclePassed", "failure")) {
            if (report.containsKey(key)) result.put(key, report.get(key));
        }
        return result;
    }

    private static void inspect(RecipeHolder<?> holder) {
        var recipe = holder.value();
        var type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString();
        var row = new Row(holder.id().toString(), type, recipe.getClass().getName(), recipe.isSpecial());
        rows.put(row.id, row);
        if (recipe.getType() != RecipeType.CRAFTING && recipe.getType() != RecipeType.SMITHING
                && recipe.getType() != RecipeType.STONECUTTING) {
            row.reasons.add("outside_requested_three_recipe_types");
            return;
        }
        row.allowed = true;
        try {
            if (recipe instanceof CraftingRecipe crafting) {
                inspectCrafting(row, cast(holder), crafting);
            } else if (recipe instanceof StonecutterRecipe stonecutting) {
                for (var ingredient : alternatives(stonecutting.getIngredients().getFirst())) {
                    var input = new SingleRecipeInput(ingredient);
                    if (!stonecutting.matches(input, level)) continue;
                    var assembled = stonecutting.assemble(input, level.registryAccess());
                    if (assembled.isEmpty()) continue;
                    accept(row, "native_stonecutting_ingredient", PatternDetailsHelper.encodeStonecuttingPattern(
                            cast(holder), AEItemKey.of(ingredient), AEItemKey.of(assembled), false), assembled, List.of(ingredient));
                }
            } else if (recipe instanceof SmithingRecipe smithing) {
                var templates = registryItems.stream().filter(smithing::isTemplateIngredient).toList();
                var bases = registryItems.stream().filter(smithing::isBaseIngredient).toList();
                var additions = registryItems.stream().filter(smithing::isAdditionIngredient).toList();
                for (var template : templates) for (var base : bases) for (var addition : additions) {
                    var input = new SmithingRecipeInput(template, base, addition);
                    if (!smithing.matches(input, level)) continue;
                    var assembled = smithing.assemble(input, level.registryAccess());
                    if (assembled.isEmpty()) continue;
                    accept(row, "native_smithing_predicate_product", PatternDetailsHelper.encodeSmithingTablePattern(
                            cast(holder), AEItemKey.of(template), AEItemKey.of(base), AEItemKey.of(addition),
                            AEItemKey.of(assembled), false), assembled, List.of(template, base, addition));
                }
            } else row.reasons.add("allowed_recipe_type_with_unknown_input_interface");
        } catch (RuntimeException failure) {
            row.reasons.add("oracle_exception:" + failure.getClass().getSimpleName() + ":" + failure.getMessage());
        }
        if (row.witnesses.isEmpty()) row.reasons.add("no_native_encodable_witness_found_not_proof_of_impossibility");
    }

    private static void inspectCrafting(Row row, RecipeHolder<CraftingRecipe> holder, CraftingRecipe recipe) {
        // Public ingredient placement applies to every CraftingRecipe, including mod implementations.
        var ingredients = recipe.getIngredients();
        if (!ingredients.isEmpty() && ingredients.size() <= 9) {
            for (int width : recipe instanceof ShapedRecipe shaped ? new int[] { shaped.getWidth() } : new int[] { 3, 2, 1 }) {
                if ((ingredients.size() + width - 1) / width > 3) continue;
                var frame = emptyFrame();
                boolean populated = true;
                for (int i = 0; i < ingredients.size(); i++) {
                    if (ingredients.get(i).isEmpty()) continue;
                    var choices = alternatives(ingredients.get(i));
                    if (choices.isEmpty()) { populated = false; break; }
                    frame[(i / width) * 3 + i % width] = choices.getFirst().copyWithCount(1);
                }
                if (!populated) continue;
                craftingWitness(row, holder, recipe, "ingredient_placement_width_" + width, frame);
                // Vary one ingredient at a time to expose output-dependent alternatives without claiming
                // an exhaustive Cartesian product over arbitrary data components.
                for (int i = 0; i < ingredients.size(); i++) {
                    int slot = (i / width) * 3 + i % width;
                    for (var choice : alternatives(ingredients.get(i))) {
                        var variant = frame.clone();
                        variant[slot] = choice.copyWithCount(1);
                        craftingWitness(row, holder, recipe, "ingredient_variant_slot_" + slot, variant);
                    }
                }
            }
        }
        // Try actual concrete inputs against every native special/custom recipe; class names never admit it.
        if (recipe.isSpecial() || !(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)
                || row.witnesses.isEmpty()) {
            for (var frame : concreteFrames) craftingWitness(row, holder, recipe, frame.name, frame.items);
            if (STOCK_WITNESSES) for (var frame : lateFrames) craftingWitness(row, holder, recipe, frame.name, frame.items);
        }
    }

    private static void craftingWitness(Row row, RecipeHolder<CraftingRecipe> holder, CraftingRecipe recipe,
            String name, ItemStack[] candidate) {
        try {
            var frame = Arrays.stream(candidate).map(ItemStack::copy).toArray(ItemStack[]::new);
            var input = CraftingInput.of(3, 3, Arrays.asList(frame));
            if (!recipe.matches(input, level)) return;
            row.nativeMatches++;
            var assembled = recipe.assemble(input, level.registryAccess());
            if (assembled.isEmpty()) { row.reasons.add("native_match_with_empty_result:" + name); return; }
            if (!ItemStack.matches(assembled, recipe.assemble(input, level.registryAccess()))) {
                row.reasons.add("non_deterministic_assemble:" + name);
                return;
            }
            accept(row, name, PatternDetailsHelper.encodeCraftingPattern(holder, frame, assembled, false, false),
                    assembled, Arrays.asList(frame));
        } catch (RuntimeException invalid) {
            row.reasons.add("witness_rejected:" + name + ":" + invalid.getClass().getSimpleName() + ":" + invalid.getMessage());
        }
    }

    private static void accept(Row row, String fixture, ItemStack encoded, ItemStack expected, List<ItemStack> inputs) {
        var pattern = PatternDetailsHelper.decodePattern(encoded, level);
        if (pattern == null) { row.reasons.add("native_ae2_decoder_rejected:" + fixture); return; }
        if (!row.id.equals(recipeId(pattern)) || pattern.getPrimaryOutput().amount() != expected.getCount()
                || !pattern.getPrimaryOutput().what().equals(AEItemKey.of(expected))) {
            row.reasons.add("native_ae2_round_trip_output_or_recipe_mismatch:" + fixture);
            return;
        }
        if (row.witnesses.stream().anyMatch(w -> w.pattern.equals(pattern))) return;
        var witness = new Witness(pattern, fixture, inputs.stream().map(CatalogAuditFixture::stackDescription).toList(),
                stackDescription(expected));
        witness.sourceInputs = inputs.stream().map(ItemStack::copy).toList();
        witness.expectedOutput = expected.copy();
        witness.execution = inspectExecution(pattern, inputs, expected);
        row.witnesses.add(witness);
    }

    private static void auditPublished(Set<IPatternDetails> actual) {
        report.put("afterCard", publication(actual));
        report.put("actualPatternCount", actual.size());
        var actualByRecipe = new LinkedHashMap<String, List<IPatternDetails>>();
        int unexpected = 0;
        for (var pattern : actual) {
            String id = recipeId(pattern);
            actualByRecipe.computeIfAbsent(id, ignored -> new ArrayList<>()).add(pattern);
            var row = rows.get(id);
            if (row == null || !row.allowed) unexpected++;
        }
        int publishable = 0, missingRecipes = 0, missingWitnesses = 0, unproven = 0;
        int executionBoundaries = 0, missingExecutable = 0;
        int actualCases = 0, actualPassed = 0, actualInvalidSelected = 0;
        var missingIds = new ArrayList<String>();
        var byType = new TreeMap<String, Map<String, Integer>>();
        for (var row : rows.values()) {
            row.actual = actualByRecipe.getOrDefault(row.id, List.of());
            if (!row.allowed) continue;
            var counts = byType.computeIfAbsent(row.type, ignored -> new TreeMap<>());
            counts.merge("recipeIds", 1, Integer::sum);
            if (!row.actual.isEmpty()) counts.merge("publishedRecipeIds", 1, Integer::sum);
            if (row.witnesses.isEmpty()) { unproven++; counts.merge("unprovenRecipeIds", 1, Integer::sum); continue; }
            publishable++;
            counts.merge("nativeWitnessPublishableRecipeIds", 1, Integer::sum);
            if (row.actual.isEmpty()) { missingRecipes++; missingIds.add(row.id); counts.merge("missingRecipeIds", 1, Integer::sum); }
            for (var witness : row.witnesses) {
                // Query the actual network for this exact item+components output, not just the provider list.
                witness.published = published(row, witness);
                if (!witness.published) missingWitnesses++;
                boolean executable = Boolean.TRUE.equals(witness.execution.get("localExecutionEligible"));
                if (!executable) executionBoundaries++;
                else if (!witness.published) missingExecutable++;
                if (witness.published && (witness.fixture.startsWith("leather_dye_")
                        || witness.fixture.equals("book_copy") || witness.fixture.startsWith("late_"))) {
                    witness.actualExecution = inspectPublishedExecution(row, witness);
                    actualCases++;
                    if (Boolean.TRUE.equals(witness.actualExecution.get("passed"))) actualPassed++;
                    actualInvalidSelected += ((Number) witness.actualExecution.get("invalidSelectedPatterns")).intValue();
                }
            }
        }
        report.put("byType", byType);
        report.put("publishableRecipeCount", publishable);
        report.put("missingPublishableRecipeCount", missingRecipes);
        report.put("missingPublishableRecipeIds", missingIds);
        report.put("missingWitnessCount", missingWitnesses);
        report.put("missingExecutableWitnessCount", missingExecutable);
        report.put("executionBoundaryWitnessCount", executionBoundaries);
        report.put("unprovenAllowedRecipeCount", unproven);
        report.put("unexpectedTypePatternCount", unexpected);
        report.put("actualCatalogExecutionCases", actualCases);
        report.put("actualCatalogExecutionCasesPassed", actualPassed);
        report.put("actualCatalogInvalidSelectedPatterns", actualInvalidSelected);
        report.put("actualCatalogExecutionChecksPassed", actualCases >= 3 && actualCases == actualPassed);
    }

    /** Uses the objects actually returned by the grid, including production catalog subclasses. */
    private static Map<String, Object> inspectPublishedExecution(Row row, Witness witness) {
        var candidates = grid.getCraftingService().getCraftingFor(witness.pattern.getPrimaryOutput().what()).stream()
                .filter(pattern -> row.id.equals(recipeId(pattern))
                        && pattern.getPrimaryOutput().equals(witness.pattern.getPrimaryOutput())).toList();
        var attempts = new ArrayList<Map<String, Object>>();
        var negativeInputs = witness.sourceInputs.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        boolean changed = false;
        for (int slot = 0; slot < negativeInputs.size(); slot++) {
            var original = negativeInputs.get(slot);
            if (original.getItem() instanceof DyeItem) {
                negativeInputs.set(slot, new ItemStack(original.is(Items.RED_DYE) ? Items.BLUE_DYE : Items.RED_DYE));
                changed = true;
                break;
            }
            if (original.is(Items.WRITTEN_BOOK)) {
                var different = original.copy();
                different.set(DataComponents.CUSTOM_NAME, Component.literal("AE2LO rejected component witness"));
                negativeInputs.set(slot, different);
                changed = true;
                break;
            }
        }
        boolean negativeApplicable = false;
        var holder = level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(row.id)).orElse(null);
        if (changed && holder != null && holder.value() instanceof CraftingRecipe recipe) {
            var negativeGrid = emptyFrame();
            for (int slot = 0; slot < negativeInputs.size(); slot++) negativeGrid[slot] = negativeInputs.get(slot);
            var nativeInput = CraftingInput.of(3, 3, Arrays.asList(negativeGrid));
            negativeApplicable = recipe.matches(nativeInput, level)
                    && !ItemStack.matches(witness.expectedOutput, recipe.assemble(nativeInput, level.registryAccess()));
        }
        int selected = 0, valid = 0, invalid = 0, negativeSelected = 0;
        for (var actual : candidates) {
            var detail = new LinkedHashMap<String, Object>();
            detail.put("actualPatternClass", actual.getClass().getName());
            detail.put("actualDefinition", stackDescription(actual.getDefinition().toStack()));
            detail.put("source", "IGrid.getCraftingService().getCraftingFor(exactOutputKey)");
            var execution = inspectExecution(actual, witness.sourceInputs, witness.expectedOutput);
            detail.put("positiveWitnessExecution", execution);
            if (Boolean.TRUE.equals(execution.get("nativeExactInputExtraction"))) {
                selected++;
                if (Boolean.TRUE.equals(execution.get("localExecutionEligible"))) valid++;
                else invalid++;
            }
            if (negativeApplicable) {
                var local = new ListCraftingInventory(key -> { });
                for (var input : negativeInputs) if (!input.isEmpty()) {
                    local.insert(AEItemKey.of(input), input.getCount(), Actionable.MODULATE);
                }
                var wronglySelected = CraftingCpuHelper.extractPatternInputs(actual, local, level,
                        new KeyCounter(), new KeyCounter());
                detail.put("changedInputRejected", wronglySelected == null);
                if (wronglySelected != null) negativeSelected++;
            }
            attempts.add(detail);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("recipeId", row.id);
        result.put("fixture", witness.fixture);
        result.put("nativeJobExecuted", false);
        result.put("actualCandidatePatterns", candidates.size());
        result.put("positiveInputSelectablePatterns", selected);
        result.put("positiveInputValidatedPatterns", valid);
        result.put("invalidSelectedPatterns", invalid);
        result.put("changedInputHasDifferentLiveRecipeOutput", negativeApplicable);
        result.put("changedInputWronglySelectedPatterns", negativeSelected);
        result.put("attempts", attempts);
        result.put("passed", selected > 0 && valid == selected && invalid == 0
                && negativeApplicable && negativeSelected == 0);
        return result;
    }

    private static boolean published(Row row, Witness witness) {
        return grid.getCraftingService().getCraftingFor(witness.pattern.getPrimaryOutput().what()).stream()
                .anyMatch(p -> row.id.equals(recipeId(p)) && p.getPrimaryOutput().equals(witness.pattern.getPrimaryOutput()));
    }

    private static Map<String, Object> latePublication() {
        var examples = new ArrayList<Map<String, Object>>();
        for (var row : rows.values()) for (var witness : row.witnesses) {
            if (witness.fixture.startsWith("late_")) examples.add(Map.of("recipeId", row.id,
                    "fixture", witness.fixture, "result", witness.result, "networkPublished", published(row, witness)));
        }
        return Map.of("tick", level.getGameTime(), "publication", publication(networkPatterns()), "examples", examples);
    }

    private static void injectFrames(List<Frame> frames, String phase) {
        var requested = new LinkedHashMap<AEItemKey, Long>();
        for (var frame : frames) for (var stack : frame.items) {
            if (!stack.isEmpty()) requested.merge(AEItemKey.of(stack), 16L, Math::max);
        }
        var storage = grid.getStorageService().getInventory();
        for (var entry : requested.entrySet()) {
            long before = storage.getAvailableStacks().get(entry.getKey());
            long inserted = storage.insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, new MachineSource(ripper));
            long after = storage.getAvailableStacks().get(entry.getKey());
            stockInsertions.add(Map.of("tick", level.getGameTime(), "phase", phase,
                    "key", stackDescription(entry.getKey().toStack()), "requested", entry.getValue(),
                    "before", before, "inserted", inserted, "after", after));
            check("finite_stock_inserted:" + phase + ":" + entry.getKey(), inserted == entry.getValue() && after == before + inserted);
            if (phase.equals("after_card_new_components") && entry.getKey().toStack().has(DataComponents.CUSTOM_NAME)) {
                check("late_component_input_previously_absent:" + entry.getKey(), before == 0);
            }
        }
    }

    private static Map<String, Object> inspectExecution(IPatternDetails pattern, List<ItemStack> inputs, ItemStack expected) {
        var result = new LinkedHashMap<String, Object>();
        var boundaries = new ArrayList<String>();
        result.put("nativeJobExecuted", false);
        result.put("boundaries", boundaries);
        try {
            var local = new ListCraftingInventory(key -> { });
            for (var input : inputs) if (!input.isEmpty()) local.insert(AEItemKey.of(input), input.getCount(), Actionable.MODULATE);
            var declaredOutputs = new KeyCounter();
            var declaredRemainders = new KeyCounter();
            var selected = CraftingCpuHelper.extractPatternInputs(pattern, local, level, declaredOutputs, declaredRemainders);
            result.put("nativeExactInputExtraction", selected != null);
            if (selected == null) {
                boundaries.add("native_pattern_cannot_select_its_exact_witness_inputs");
            } else {
                boolean validated = CraftingRipperPatterns.validateInputs(pattern, selected, level);
                result.put("productionValidateInputs", validated);
                if (!validated) boundaries.add("production_live_preflight_rejected_exact_native_witness");
                if (pattern instanceof IMolecularAssemblerSupportedPattern nativePattern) {
                    var copies = new KeyCounter[selected.length];
                    for (int i = 0; i < selected.length; i++) {
                        copies[i] = new KeyCounter();
                        for (var entry : selected[i]) copies[i].add(entry.getKey(), entry.getLongValue());
                    }
                    var frame = new ArrayList<ItemStack>(Collections.nCopies(9, ItemStack.EMPTY));
                    nativePattern.fillCraftingGrid(copies, frame::set);
                    var input = CraftingInput.of(3, 3, frame);
                    var assembled = nativePattern.assemble(input, level);
                    boolean outputMatches = ItemStack.matches(expected, assembled);
                    result.put("localOutputMatchesNativeWitness", outputMatches);
                    if (!outputMatches) boundaries.add("native_pattern_local_output_differs_from_live_recipe_assemble");
                    var first = remainderCounter(nativePattern.getRemainingItems(input));
                    var second = remainderCounter(nativePattern.getRemainingItems(input));
                    boolean stable = sameCounter(first, second);
                    boolean matches = sameCounter(first, declaredRemainders);
                    result.put("nativeRemaindersStableAcrossTwoCalls", stable);
                    result.put("nativeRemaindersMatchPatternDeclaration", matches);
                    result.put("declaredRemainders", describeCounter(declaredRemainders));
                    result.put("localRemainders", describeCounter(first));
                    if (!stable) boundaries.add("non_deterministic_native_remainders");
                    if (!matches) boundaries.add("native_remainders_differ_from_pattern_input_declaration");
                } else boundaries.add("native_pattern_has_no_local_assembly_interface");
            }
            if (expected.has(DataComponents.MAP_POST_PROCESSING)) {
                boundaries.add("world_post_processing_required_for_map_result_not_exercised_by_detached_preflight");
            }
        } catch (RuntimeException failure) {
            boundaries.add("local_preflight_exception:" + failure.getClass().getSimpleName() + ":" + failure.getMessage());
        }
        result.put("localExecutionEligible", boundaries.isEmpty());
        result.put("executionRoute", boundaries.isEmpty() ? "instant-preflight-passed" : "native-required");
        return result;
    }

    private static KeyCounter remainderCounter(List<ItemStack> stacks) {
        var result = new KeyCounter();
        for (var stack : stacks) if (!stack.isEmpty()) result.add(AEItemKey.of(stack), stack.getCount());
        return result;
    }
    private static boolean sameCounter(KeyCounter first, KeyCounter second) {
        for (var entry : first) if (second.get(entry.getKey()) != entry.getLongValue()) return false;
        for (var entry : second) if (first.get(entry.getKey()) != entry.getLongValue()) return false;
        return true;
    }
    private static List<Map<String, Object>> describeCounter(KeyCounter counter) {
        var result = new ArrayList<Map<String, Object>>();
        for (var entry : counter) {
            if (entry.getLongValue() != 0) result.add(Map.of("key", entry.getKey() instanceof AEItemKey item
                    ? stackDescription(item.toStack()) : entry.getKey().toString(), "amount", entry.getLongValue()));
        }
        return result;
    }

    private static Set<IPatternDetails> networkPatterns() {
        var result = new LinkedHashSet<IPatternDetails>();
        for (var key : grid.getCraftingService().getCraftables(key -> true)) {
            result.addAll(grid.getCraftingService().getCraftingFor(key));
        }
        return result;
    }

    private static Map<String, Object> publication(Set<IPatternDetails> patterns) {
        var ids = new TreeMap<String, Integer>();
        for (var pattern : patterns) ids.merge(recipeId(pattern), 1, Integer::sum);
        return Map.of("tick", level.getGameTime(), "patterns", patterns.size(), "recipeIds", ids);
    }

    private static String recipeId(IPatternDetails pattern) {
        var crafting = pattern.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) return crafting.recipeId().toString();
        var smithing = pattern.getDefinition().get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) return smithing.recipeId().toString();
        var stonecutting = pattern.getDefinition().get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        return stonecutting == null ? "<non-native-pattern>" : stonecutting.recipeId().toString();
    }

    private static void buildConcreteFrames() {
        for (var dye : List.of(Items.RED_DYE, Items.BLUE_DYE)) {
            frame("leather_dye_" + dye, new ItemStack(Items.LEATHER_CHESTPLATE), new ItemStack(dye));
            frame("shulker_dye_" + dye, new ItemStack(Items.SHULKER_BOX), new ItemStack(dye));
            frame("bundle_dye_" + dye, new ItemStack(Items.BUNDLE), new ItemStack(dye));
            frame("firework_star_" + dye, new ItemStack(Items.GUNPOWDER), new ItemStack(dye));
        }
        frame("firework_rocket", new ItemStack(Items.PAPER), new ItemStack(Items.GUNPOWDER));
        var star = new ItemStack(Items.FIREWORK_STAR);
        star.set(DataComponents.FIREWORK_EXPLOSION,
                new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(0xFF0000), IntList.of(), false, false));
        frame("firework_fade", star, new ItemStack(Items.BLUE_DYE));
        frame("firework_star_rocket", new ItemStack(Items.PAPER), new ItemStack(Items.GUNPOWDER), star);
        frame("shield_decoration", new ItemStack(Items.SHIELD), new ItemStack(Items.BLUE_BANNER));
        var book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY);
        frame("book_copy", book, new ItemStack(Items.WRITABLE_BOOK));
        var map = MapItem.create(level, 0, 0, (byte) 0, true, false);
        frame("map_copy", map, new ItemStack(Items.MAP));
        var mapExtend = filled(Items.PAPER);
        mapExtend[4] = map;
        concreteFrames.add(new Frame("map_extend_saved_map", mapExtend));
        var damagedA = new ItemStack(Items.DIAMOND_SWORD);
        var damagedB = new ItemStack(Items.DIAMOND_SWORD);
        damagedA.setDamageValue(200);
        damagedB.setDamageValue(300);
        frame("damaged_tool_repair", damagedA, damagedB);
        for (var potion : List.of(Potions.POISON, Potions.SWIFTNESS, Potions.WATER)) {
            var tipped = filled(Items.ARROW);
            tipped[4] = PotionContents.createItemStack(Items.LINGERING_POTION, potion);
            concreteFrames.add(new Frame("tipped_arrows_" + potion, tipped));
        }
        frame("suspicious_stew", new ItemStack(Items.BOWL), new ItemStack(Items.BROWN_MUSHROOM),
                new ItemStack(Items.RED_MUSHROOM), new ItemStack(Items.DANDELION));
        var pot = emptyFrame();
        for (int slot : new int[] {1, 3, 5, 7}) pot[slot] = new ItemStack(Items.BRICK);
        concreteFrames.add(new Frame("plain_decorated_pot", pot));
        var shardPot = pot.clone();
        shardPot[1] = new ItemStack(Items.ANGLER_POTTERY_SHERD);
        concreteFrames.add(new Frame("shard_decorated_pot", shardPot));
        var banner = new ItemStack(Items.BLUE_BANNER);
        banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                .add(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(BannerPatterns.STRIPE_BOTTOM), DyeColor.RED).build());
        frame("banner_copy", banner, new ItemStack(Items.BLUE_BANNER));
        var lateArmor = new ItemStack(Items.LEATHER_CHESTPLATE);
        lateArmor.set(DataComponents.CUSTOM_NAME, Component.literal("AE2LO late catalog dye witness"));
        lateFrames.add(new Frame("late_named_leather_dye", frameItems(lateArmor, new ItemStack(Items.RED_DYE))));
        var lateBook = new ItemStack(Items.WRITTEN_BOOK);
        lateBook.set(DataComponents.CUSTOM_NAME, Component.literal("AE2LO late catalog book witness"));
        lateBook.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Late audit"),
                "AE2LO", 0, List.of(Filterable.passThrough(Component.literal("catalog-refresh-witness"))), true));
        lateFrames.add(new Frame("late_unique_book_copy", frameItems(lateBook, new ItemStack(Items.WRITABLE_BOOK))));
        buildUpgradeFacadeAndBannerFrames();
        report.put("concreteSpecialFixtureNames", concreteFrames.stream().map(Frame::name).toList());
        report.put("lateComponentFixtureNames", lateFrames.stream().map(Frame::name).toList());
    }

    /** Additional witnesses obtained from public native recipe/item APIs, never the production catalog. */
    private static void buildUpgradeFacadeAndBannerFrames() {
        var plainCell = AEItems.ITEM_CELL_1K.stack();
        var upgradedCell = plainCell.copy();
        if (!(upgradedCell.getItem() instanceof IUpgradeableItem upgradeable)) {
            throw new IllegalStateException("Pinned AE2 1k item cell does not expose IUpgradeableItem");
        }
        var card = AEItems.FUZZY_CARD.stack();
        var upgrades = upgradeable.getUpgrades(upgradedCell);
        if (!upgrades.addItems(card.copy()).isEmpty() || upgrades.getInstalledUpgrades(card.getItem()) != 1) {
            throw new IllegalStateException("Native upgrade inventory refused the finite fuzzy-card fixture");
        }
        frame("ae2_public_upgrade_add", plainCell, card);
        frame("ae2_public_upgrade_remove", upgradedCell);
        report.put("upgradeWitnessConstruction", Map.of("api", "IUpgradeableItem.getUpgrades + IUpgradeInventory.addItems",
                "plainCell", stackDescription(plainCell), "card", stackDescription(card),
                "upgradedCell", stackDescription(upgradedCell)));

        var facade = emptyFrame();
        for (int slot : new int[] {1, 3, 5, 7}) facade[slot] = AEParts.CABLE_ANCHOR.stack();
        facade[4] = new ItemStack(Items.STONE);
        concreteFrames.add(new Frame("ae2_facade_anchor_cross_stone", facade));

        // In 26.x each banner color has its own recipe ID; one blue fixture cannot prove the other 15.
        var layer = new BannerPatternLayers.Builder()
                .add(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(BannerPatterns.STRIPE_BOTTOM), DyeColor.RED)
                .build();
        for (var item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BannerItem)) continue;
            var patterned = item.getDefaultInstance();
            patterned.set(DataComponents.BANNER_PATTERNS, layer);
            frame("native_banner_copy_" + BuiltInRegistries.ITEM.getKey(item), patterned, item.getDefaultInstance());
        }

        // AE2 and Applied Flux share this public recipe implementation. Its 26.x placementInfo is empty,
        // while these getters (also used by its codec/display) expose both actual input items precisely.
        int cellUpgrades = 0;
        for (var holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() instanceof StorageCellUpgradeRecipe upgradeRecipe) {
                frame("native_storage_cell_upgrade_" + holder.id().toString(),
                        upgradeRecipe.getInputCell().getDefaultInstance(), upgradeRecipe.getInputComponent().getDefaultInstance());
                cellUpgrades++;
            }
        }
        report.put("publicStorageCellUpgradeInputFixtures", cellUpgrades);
    }

    private static ItemStack[] filled(Item item) {
        var result = new ItemStack[9];
        Arrays.setAll(result, ignored -> new ItemStack(item));
        return result;
    }
    private static ItemStack[] emptyFrame() {
        var result = new ItemStack[9];
        Arrays.fill(result, ItemStack.EMPTY);
        return result;
    }
    private static void frame(String name, ItemStack... inputs) {
        concreteFrames.add(new Frame(name, frameItems(inputs)));
    }
    private static ItemStack[] frameItems(ItemStack... inputs) {
        var frame = emptyFrame();
        System.arraycopy(inputs, 0, frame, 0, inputs.length);
        return frame;
    }
    private static List<ItemStack> alternatives(Ingredient ingredient) { return Arrays.asList(ingredient.getItems()); }
    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> RecipeHolder<T> cast(RecipeHolder<?> holder) { return (RecipeHolder<T>) holder; }
    private static Map<String, Object> stackDescription(ItemStack stack) {
        return Map.of("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "count", stack.getCount(),
                "components", stack.getComponentsPatch().toString());
    }
    private static boolean settled() { return level.getGameTime() - phaseTick >= SETTLE_TICKS; }
    private static void transition(int next, String name) {
        stage = next;
        phaseTick = level.getGameTime();
        ProbeState.phase = "catalog_audit";
        ProbeState.status = name;
        lifecycle.add(Map.of("tick", phaseTick, "stage", name));
    }
    private static void check(String name, boolean success) {
        lifecyclePassed &= success;
        lifecycle.add(Map.of("tick", level.getGameTime(), "check", name, "passed", success));
    }
    private static void writeReport(boolean complete) throws Exception {
        report.put("completed", complete);
        report.put("passed", passed);
        report.put("lifecyclePassed", lifecyclePassed);
        report.put("recipes", rows.values().stream().map(Row::describe).toList());
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void fail(Throwable failure) {
        passed = false;
        finished = true;
        report.put("failure", failure.getClass().getName() + ": " + failure.getMessage());
        try { if (output != null) writeReport(true); } catch (Exception ignored) { }
        ProbeState.status = "catalog_failed: " + failure;
    }
    private record Frame(String name, ItemStack[] items) { }
    private static final class Witness {
        final IPatternDetails pattern;
        final String fixture;
        final List<Map<String, Object>> inputs;
        final Map<String, Object> result;
        Map<String, Object> execution = Map.of();
        List<ItemStack> sourceInputs = List.of();
        ItemStack expectedOutput = ItemStack.EMPTY;
        Map<String, Object> actualExecution = Map.of();
        boolean published;
        Witness(IPatternDetails pattern, String fixture, List<Map<String, Object>> inputs, Map<String, Object> result) {
            this.pattern = pattern; this.fixture = fixture; this.inputs = inputs; this.result = result;
        }
        Map<String, Object> describe() { return Map.of("fixture", fixture, "inputs", inputs, "result", result,
                "networkPublished", published, "localExecutionPreflight", execution,
                "actualPublishedPatternExecution", actualExecution); }
    }
    private static final class Row {
        final String id, type, implementation;
        final boolean special;
        boolean allowed;
        int nativeMatches;
        final Set<String> reasons = new LinkedHashSet<>();
        final List<Witness> witnesses = new ArrayList<>();
        List<IPatternDetails> actual = List.of();
        Row(String id, String type, String implementation, boolean special) {
            this.id = id; this.type = type; this.implementation = implementation; this.special = special;
        }
        Map<String, Object> describe() {
            var result = new LinkedHashMap<String, Object>();
            result.put("id", id); result.put("type", type); result.put("implementation", implementation);
            result.put("special", special); result.put("allowedType", allowed);
            result.put("nativeMatchingExamples", nativeMatches); result.put("publishableByNativeWitness", !witnesses.isEmpty());
            result.put("actualPatterns", actual.size()); result.put("missingRecipe", !witnesses.isEmpty() && actual.isEmpty());
            result.put("classificationReasons", reasons); result.put("witnesses", witnesses.stream().map(Witness::describe).toList());
            return result;
        }
    }
}
