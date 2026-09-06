package com.example.ae2loprobe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEPatternDecoder;
import appeng.crafting.pattern.EncodedCraftingPattern;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.integration.CatalogCraftingPattern;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeType;

/** Examines actual published objects; all serialized and tampered stacks stay detached from the grid. */
public final class CatalogPatternPersistenceFixture {
    private static final String MARKER = "ae2lightoptimizer:catalog_crafting";
    private static final Codec<List<List<AEItemKey>>> CHOICES = AEItemKey.CODEC.listOf().listOf();

    private CatalogPatternPersistenceFixture() {}

    public static Map<String, Object> run(ServerLevel level, CraftingRipperBlockEntity ripper,
                                         Collection<IPatternDetails> published) {
        var candidates = published.stream().filter(CatalogCraftingPattern.class::isInstance)
                .map(CatalogCraftingPattern.class::cast).toList();
        var cases = new ArrayList<Map<String, Object>>();
        checkSelected("ordinary_with_empty_slots", candidates, p -> p.getPrimaryOutput().what() instanceof AEItemKey key
                && key.is(Items.OAK_PLANKS), level, ripper, cases);
        checkSelected("same_output_input_alternatives", candidates,
                p -> Arrays.stream(p.getInputs()).anyMatch(input -> input.getPossibleInputs().length > 1),
                level, ripper, cases);
        checkSelected("named_leather_components", candidates,
                p -> hasInput(p, stack -> stack.is(Items.LEATHER_CHESTPLATE) && stack.has(DataComponents.CUSTOM_NAME)),
                level, ripper, cases);
        checkSelected("written_book_name_and_content", candidates,
                p -> hasInput(p, stack -> stack.is(Items.WRITTEN_BOOK) && stack.has(DataComponents.CUSTOM_NAME)
                        && stack.has(DataComponents.WRITTEN_BOOK_CONTENT)), level, ripper, cases);
        checkSelected("world_postprocessing_map", candidates,
                p -> p.getPrimaryOutput().what() instanceof AEItemKey key
                        && key.get(DataComponents.MAP_POST_PROCESSING) != null, level, ripper, cases);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", cases.size() == 5 && cases.stream().allMatch(row -> Boolean.TRUE.equals(row.get("passed"))));
        result.put("caseCount", cases.size());
        result.put("publishedCatalogSubtypeCount", candidates.size());
        result.put("cases", cases);
        result.put("boundary", "Actual grid-published catalog patterns; full AEItemKey NBT codec and PatternDetailsHelper decoder, all nine persisted candidate slots and runtime IInput alternatives, active provider ownership and detached corruption tests. No crafting task, world postprocess callback or server restart occurs in this fixture.");
        return result;
    }

    private static void checkSelected(String name, List<CatalogCraftingPattern> candidates,
            Predicate<CatalogCraftingPattern> predicate, ServerLevel level, CraftingRipperBlockEntity ripper,
            List<Map<String, Object>> cases) {
        var row = new LinkedHashMap<String, Object>();
        row.put("case", name);
        try {
            var pattern = candidates.stream().filter(predicate).findFirst().orElseThrow(
                    () -> new IllegalStateException("Required real published witness not found"));
            check(pattern, level, ripper, row);
            row.put("passed", true);
        } catch (Throwable failure) {
            row.put("passed", false);
            row.put("failure", failure.toString());
        }
        cases.add(row);
    }

    private static void check(CatalogCraftingPattern pattern, ServerLevel level, CraftingRipperBlockEntity ripper,
                              Map<String, Object> row) {
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var source = pattern.getDefinition();
        var nativeRecord = source.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        require(source.is(AEItems.BLANK_PATTERN) && nativeRecord != null, "Missing marked blank native record");
        require(PatternDetailsHelper.isEncodedPattern(source.toStack()), "Registered decoder does not claim marked pattern");
        require(AEPatternDecoder.INSTANCE.decodePattern(source, level) == null, "Base decoder swallowed subtype definition");

        var tag = AEItemKey.CODEC.encodeStart(ops, source).getOrThrow();
        var loadedDefinition = AEItemKey.CODEC.parse(ops, tag).getOrThrow();
        var restored = PatternDetailsHelper.decodePattern(loadedDefinition, level);
        require(restored != null && restored.getClass() == CatalogCraftingPattern.class,
                "PatternDetailsHelper restored the wrong class");
        // Vanilla deliberately omits MAP_POST_PROCESSING from its NBT codec; only the custom decoder
        // may restore the explicitly persisted marker. Every original component must then match exactly.
        require(source.equals(restored.getDefinition()) && ItemStack.matches(source.toStack(), restored.getDefinition().toStack())
                        && tag.equals(AEItemKey.CODEC.encodeStart(ops, restored.getDefinition()).getOrThrow()),
                "Full item components changed after the registered decoder's NBT restoration");
        require(pattern.equals(restored) && restored.equals(pattern) && pattern.hashCode() == restored.hashCode(),
                "Symmetric identity or hash changed after restoration");
        require(Map.of(pattern, 3L).get(restored) == 3L, "Restored pattern cannot find saved remaining-task key");
        require(ripper.ownsPattern(pattern) && ripper.ownsPattern(restored), "Active ripper lost loaded task ownership");
        require(pattern.getOutputs().equals(restored.getOutputs()), "Persistent output components/count changed");

        var choices = choices(source, level);
        var restoredChoices = choices(restored.getDefinition(), level);
        require(choices.size() == 9 && choices.equals(restoredChoices), "Nine-slot component candidate lists changed");
        var slotReport = new ArrayList<Map<String, Object>>();
        int componentKeys = 0;
        for (int slot = 0; slot < 9; slot++) {
            var encodedStack = nativeRecord.inputs().get(slot);
            require(encodedStack.isEmpty() == choices.get(slot).isEmpty(), "Empty slot encoded as an item");
            int components = (int) choices.get(slot).stream().filter(key -> !key.toStack().getComponentsPatch().isEmpty()).count();
            componentKeys += components;
            slotReport.add(Map.of("slot", slot, "empty", encodedStack.isEmpty(), "candidates", choices.get(slot).size(),
                    "componentKeys", components, "equalAfterReload", choices.get(slot).equals(restoredChoices.get(slot))));
        }
        require(pattern.getInputs().length == restored.getInputs().length, "Condensed input count changed");
        for (int i = 0; i < pattern.getInputs().length; i++) {
            var before = pattern.getInputs()[i];
            var after = restored.getInputs()[i];
            require(before.getMultiplier() == after.getMultiplier()
                            && Arrays.equals(before.getPossibleInputs(), after.getPossibleInputs()),
                    "Runtime possible inputs or multipliers changed");
            for (var choice : before.getPossibleInputs()) {
                require(before.isValid(choice.what(), level) == after.isValid(choice.what(), level),
                        "Candidate validity changed after reload");
            }
        }

        var legacyStack = AEItems.CRAFTING_PATTERN.stack();
        legacyStack.set(AEComponents.ENCODED_CRAFTING_PATTERN, nativeRecord);
        var legacy = PatternDetailsHelper.decodePattern(legacyStack, level);
        require(legacy != null && legacy.getClass() == AECraftingPattern.class, "Legacy native definition did not decode");
        require(!pattern.equals(legacy) && !legacy.equals(pattern), "Base/subclass equality is asymmetric");
        require(ripper.ownsPattern(legacy), "Legacy pending native task migration lost ownership");

        // A previously persisted task may carry fewer hints than the current provider; its recipe identity stays exact.
        var reducedChoices = nativeRecord.inputs().stream()
                .map(stack -> stack.isEmpty() ? List.<AEItemKey>of() : List.of(AEItemKey.of(stack))).toList();
        var reduced = source.toStack();
        var reducedData = reduced.get(DataComponents.CUSTOM_DATA).copyTag();
        reducedData.put("choices", CHOICES.encodeStart(ops, reducedChoices).getOrThrow());
        reduced.set(DataComponents.CUSTOM_DATA, CustomData.of(reducedData));
        var priorTask = PatternDetailsHelper.decodePattern(reduced, level);
        require(priorTask instanceof CatalogCraftingPattern && priorTask.equals(pattern) && pattern.equals(priorTask)
                && priorTask.hashCode() == pattern.hashCode() && ripper.ownsPattern(priorTask),
                "Older valid candidate hints invalidated task identity");

        var rejected = new ArrayList<String>();
        var corrupt = source.toStack();
        var data = corrupt.get(DataComponents.CUSTOM_DATA).copyTag();
        data.remove(MARKER);
        corrupt.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        reject(corrupt, level, "missing_marker", rejected);
        corrupt = source.toStack();
        data = corrupt.get(DataComponents.CUSTOM_DATA).copyTag();
        data.putInt(MARKER, 999);
        corrupt.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        reject(corrupt, level, "unsupported_marker_version", rejected);
        corrupt = source.toStack();
        data = corrupt.get(DataComponents.CUSTOM_DATA).copyTag();
        data.remove("choices");
        corrupt.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        reject(corrupt, level, "missing_candidates", rejected);
        var altered = new ArrayList<>(choices);
        int filled = firstSlot(nativeRecord, false);
        altered.set(filled, List.of(AEItemKey.of(Items.BEDROCK)));
        corrupt = source.toStack();
        data = corrupt.get(DataComponents.CUSTOM_DATA).copyTag();
        data.put("choices", CHOICES.encodeStart(ops, altered).getOrThrow());
        corrupt.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        reject(corrupt, level, "foreign_candidate_and_missing_original", rejected);
        altered = new ArrayList<>(choices);
        var appended = new ArrayList<>(choices.get(filled));
        appended.add(AEItemKey.of(Items.BEDROCK));
        altered.set(filled, List.copyOf(appended));
        corrupt = source.toStack();
        data = corrupt.get(DataComponents.CUSTOM_DATA).copyTag();
        data.put("choices", CHOICES.encodeStart(ops, altered).getOrThrow());
        corrupt.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        reject(corrupt, level, "invalid_substitution_with_original_retained", rejected);
        int empty = firstSlot(nativeRecord, true);
        if (empty >= 0) {
            altered = new ArrayList<>(choices);
            altered.set(empty, List.of(AEItemKey.of(Items.BEDROCK)));
            corrupt = source.toStack();
            data = corrupt.get(DataComponents.CUSTOM_DATA).copyTag();
            data.put("choices", CHOICES.encodeStart(ops, altered).getOrThrow());
            corrupt.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            reject(corrupt, level, "fake_item_in_empty_slot", rejected);
        }
        corrupt = source.toStack();
        corrupt.set(AEComponents.ENCODED_CRAFTING_PATTERN, new EncodedCraftingPattern(nativeRecord.inputs(),
                Items.BEDROCK.getDefaultInstance(), nativeRecord.recipeId(), nativeRecord.canSubstitute(), false));
        reject(corrupt, level, "forged_output", rejected);
        var wrongType = level.getServer().getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value().getType() == RecipeType.STONECUTTING).findFirst().orElseThrow();
        corrupt = source.toStack();
        corrupt.set(AEComponents.ENCODED_CRAFTING_PATTERN, new EncodedCraftingPattern(nativeRecord.inputs(),
                nativeRecord.result(), wrongType.id(), nativeRecord.canSubstitute(), false));
        reject(corrupt, level, "live_recipe_wrong_type", rejected);

        row.put("recipeId", nativeRecord.recipeId().identifier().toString());
        row.put("sourceClass", pattern.getClass().getName());
        row.put("restoredClass", restored.getClass().getName());
        row.put("nativeRecordEqual", true);
        row.put("definitionComponentsEqual", true);
        row.put("rawVanillaDefinitionEqual", source.equals(loadedDefinition));
        row.put("registeredDecoderRestoresEveryOriginalComponent", true);
        row.put("symmetricEqualsAndHash", true);
        row.put("savedTaskKeyLookup", true);
        row.put("loadedAndLegacyTaskOwnership", true);
        row.put("smallerPersistedCandidateSetOwned", true);
        row.put("runtimeInputCandidatesEqual", true);
        row.put("componentCandidateCount", componentKeys);
        row.put("slots", slotReport);
        row.put("rejectedCorruptions", rejected);
    }

    private static List<List<AEItemKey>> choices(AEItemKey definition, ServerLevel level) {
        return CHOICES.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                definition.get(DataComponents.CUSTOM_DATA).copyTag().get("choices")).getOrThrow();
    }

    private static boolean hasInput(CatalogCraftingPattern pattern, Predicate<ItemStack> predicate) {
        return pattern.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN).inputs().stream().anyMatch(predicate);
    }

    private static int firstSlot(EncodedCraftingPattern record, boolean empty) {
        for (int slot = 0; slot < 9; slot++) if (record.inputs().get(slot).isEmpty() == empty) return slot;
        return -1;
    }

    private static void reject(ItemStack definition, ServerLevel level, String corruption, List<String> rejected) {
        require(AEPatternDecoder.INSTANCE.decodePattern(AEItemKey.of(definition), level) == null,
                corruption + " fell through to native decoder");
        require(PatternDetailsHelper.decodePattern(definition, level) == null, corruption + " was accepted");
        rejected.add(corruption);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
