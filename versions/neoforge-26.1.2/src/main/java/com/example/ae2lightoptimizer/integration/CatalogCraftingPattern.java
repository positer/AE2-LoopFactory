package com.example.ae2lightoptimizer.integration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.crafting.RecipeAccess;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.EncodedCraftingPattern;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Native crafting semantics with persistent, live-validated component input alternatives. */
public final class CatalogCraftingPattern extends AECraftingPattern {
    private static final String MARKER = "ae2lightoptimizer:catalog_crafting";
    private static final Codec<List<List<AEItemKey>>> CHOICES_CODEC = AEItemKey.CODEC.listOf().listOf();
    private static final Codec<List<List<CompoundTag>>> CHOICE_MARKERS_CODEC = CompoundTag.CODEC.listOf().listOf();
    private static boolean decoderRegistered;

    private final EncodedCraftingPattern nativeIdentity;
    private final List<List<AEItemKey>> slotChoices;
    private final IPatternDetails.IInput[] catalogInputs;

    /** A blank item avoids the native decoder swallowing this subtype's additional input data. */
    public static synchronized void registerDecoder() {
        if (decoderRegistered) return;
        PatternDetailsHelper.registerDecoder(new IPatternDetailsDecoder() {
            @Override public boolean isEncodedPattern(ItemStack stack) { return marked(AEItemKey.of(stack)); }
            @Override public IPatternDetails decodePattern(AEItemKey definition, Level level) {
                return decode(definition, level);
            }
        });
        decoderRegistered = true;
    }

    static CatalogCraftingPattern create(AEItemKey definition, Level level, CraftingRecipe recipe,
                                         List<List<ItemStack>> choices) {
        if (liveRecipe(definition, level) != recipe || choices.size() != 9) {
            throw new IllegalArgumentException("Unregistered crafting recipe or non-nine-slot choices");
        }
        var original = new AECraftingPattern(definition, level);
        var keys = new ArrayList<List<AEItemKey>>(9);
        for (var slot : choices) {
            var candidates = new LinkedHashSet<AEItemKey>();
            for (var stack : slot) {
                var key = AEItemKey.of(stack);
                if (key != null) candidates.add(key);
            }
            keys.add(List.copyOf(candidates));
        }
        var validated = validateChoices(original, recipe, keys, level, false);
        return new CatalogCraftingPattern(encode(definition, validated, level), level, recipe, validated);
    }

    /** Reject stale or forged records before restoring this exact pattern subtype. */
    public static CatalogCraftingPattern decode(AEItemKey definition, Level level) {
        if (level == null || !marked(definition)) return null;
        try {
            var data = definition.get(DataComponents.CUSTOM_DATA).copyTag();
            if (data.getIntOr(MARKER, 0) != 1 || !data.contains("choices")) return null;
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            if (data.contains("mapMarkers")) {
                definition = (AEItemKey) RipperMapSerialization.restoreKey(definition,
                        CompoundTag.CODEC.parse(ops, data.get("mapMarkers")).getOrThrow());
            }
            var recipe = liveRecipe(definition, level);
            if (recipe == null) return null;
            var choices = CHOICES_CODEC.parse(ops, data.get("choices")).getOrThrow();
            if (data.contains("choiceMapMarkers")) {
                var markers = CHOICE_MARKERS_CODEC.parse(ops, data.get("choiceMapMarkers")).getOrThrow();
                if (markers.size() != choices.size()) return null;
                var restored = new ArrayList<List<AEItemKey>>(choices.size());
                for (int slot = 0; slot < choices.size(); slot++) {
                    if (markers.get(slot).size() != choices.get(slot).size()) return null;
                    var candidates = new ArrayList<AEItemKey>();
                    for (int index = 0; index < choices.get(slot).size(); index++) {
                        candidates.add((AEItemKey) RipperMapSerialization.restoreKey(
                                choices.get(slot).get(index), markers.get(slot).get(index)));
                    }
                    restored.add(List.copyOf(candidates));
                }
                choices = List.copyOf(restored);
            }
            var original = new AECraftingPattern(definition, level);
            var validated = validateChoices(original, recipe, choices, level, true);
            return new CatalogCraftingPattern(definition, level, recipe, validated);
        } catch (RuntimeException invalidDefinition) {
            return null;
        }
    }

    private CatalogCraftingPattern(AEItemKey definition, Level level, CraftingRecipe recipe,
                                   List<List<AEItemKey>> choices) {
        super(definition, level);
        nativeIdentity = Objects.requireNonNull(definition.get(AEComponents.ENCODED_CRAFTING_PATTERN));
        slotChoices = choices;
        var original = super.getInputs();
        // AE2 returns its concrete Input[] through the interface-array signature.
        // clone() retains that runtime component type and rejects our wrappers.
        catalogInputs = new IPatternDetails.IInput[original.length];
        var sparse = getSparseInputs();
        for (int index = 0; index < original.length; index++) {
            var delegate = original[index];
            var encoded = (AEItemKey) delegate.getPossibleInputs()[0].what();
            var possible = new LinkedHashSet<AEItemKey>();
            possible.add(encoded);
            for (int slot = 0; slot < sparse.size(); slot++) {
                if (sparse.get(slot) == null || !sparse.get(slot).what().equals(encoded)) continue;
                possible.addAll(slotChoices.get(slot));
            }
            var alternatives = possible.stream().map(key -> new GenericStack(key, 1)).toArray(GenericStack[]::new);
            catalogInputs[index] = new IPatternDetails.IInput() {
                @Override public GenericStack[] getPossibleInputs() { return alternatives; }
                @Override public long getMultiplier() { return delegate.getMultiplier(); }
                @Override public boolean isValid(AEKey input, Level world) {
                    return input instanceof AEItemKey item && delegate.isValid(input, world)
                            && validReplacement(CatalogCraftingPattern.this, recipe, encoded, item, world);
                }
                @Override public AEKey getRemainingKey(AEKey input) { return delegate.getRemainingKey(input); }
            };
        }
    }

    /** Catalog refreshes may add choices while old CPU jobs retain the same semantic identity. */
    CatalogCraftingPattern merge(CatalogCraftingPattern other, Level level) {
        if (!equals(other)) throw new IllegalArgumentException("Different crafting records");
        var merged = new ArrayList<List<AEItemKey>>(9);
        for (int slot = 0; slot < 9; slot++) {
            var union = new LinkedHashSet<>(slotChoices.get(slot));
            union.addAll(other.slotChoices.get(slot));
            merged.add(List.copyOf(union));
        }
        if (merged.equals(slotChoices)) return this;
        var recipe = Objects.requireNonNull(liveRecipe(getDefinition(), level));
        var validated = validateChoices(this, recipe, merged, level, true);
        return new CatalogCraftingPattern(encode(getDefinition(), validated, level), level, recipe, validated);
    }

    private static List<List<AEItemKey>> validateChoices(AECraftingPattern original, CraftingRecipe recipe,
            List<List<AEItemKey>> choices, Level level, boolean strict) {
        var encoded = original.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (encoded == null || encoded.inputs().size() != 9 || choices.size() != 9
                || encoded.canSubstituteFluids() || encoded.containsMissingContent()
                || !(original.getPrimaryOutput().what() instanceof AEItemKey output)
                || !ItemStack.matches(encoded.result(), output.toStack((int) original.getPrimaryOutput().amount()))) {
            throw new IllegalArgumentException("Invalid catalog crafting record");
        }
        var result = new ArrayList<List<AEItemKey>>(9);
        for (int slot = 0; slot < 9; slot++) {
            var stack = encoded.inputs().get(slot);
            if (stack.isEmpty()) {
                if (strict && !choices.get(slot).isEmpty()) throw new IllegalArgumentException("Occupied empty slot");
                result.add(List.of());
                continue;
            }
            if (stack.getCount() != 1) throw new IllegalArgumentException("Non-unit input");
            var baseline = Objects.requireNonNull(AEItemKey.of(stack));
            if (strict && !choices.get(slot).contains(baseline)) {
                throw new IllegalArgumentException("Missing encoded input candidate");
            }
            var accepted = new LinkedHashSet<AEItemKey>();
            accepted.add(baseline);
            for (var candidate : choices.get(slot)) {
                boolean valid = candidate != null && original.isItemValid(slot, candidate, level)
                        && validReplacement(original, recipe, baseline, candidate, level);
                if (valid) accepted.add(candidate);
                else if (strict) throw new IllegalArgumentException("Invalid crafting substitution");
            }
            result.add(List.copyOf(accepted));
        }
        return List.copyOf(result);
    }

    private static boolean validReplacement(AECraftingPattern pattern, CraftingRecipe recipe,
            AEItemKey baseline, AEItemKey replacement, Level level) {
        var grid = new ArrayList<ItemStack>(9);
        for (var ingredient : pattern.getSparseInputs()) {
            if (ingredient == null) grid.add(ItemStack.EMPTY);
            else if (ingredient.what() instanceof AEItemKey item) {
                grid.add((item.equals(baseline) ? replacement : item).toStack());
            } else return false;
        }
        var input = CraftingInput.of(3, 3, grid);
        return recipe.matches(input, level) && ItemStack.matches(recipe.assemble(input),
                ((AEItemKey) pattern.getPrimaryOutput().what()).toStack((int) pattern.getPrimaryOutput().amount()));
    }

    private static AEItemKey encode(AEItemKey source, List<List<AEItemKey>> choices, Level level) {
        var data = new CompoundTag();
        data.putInt(MARKER, 1);
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        data.put("choices", CHOICES_CODEC.encodeStart(ops, choices).getOrThrow());
        var mapMarkers = RipperMapSerialization.markers(source);
        if (!mapMarkers.isEmpty()) data.put("mapMarkers", mapMarkers);
        var choiceMarkers = choices.stream().map(slot -> slot.stream().map(RipperMapSerialization::markers).toList()).toList();
        if (choiceMarkers.stream().flatMap(List::stream).anyMatch(marker -> !marker.isEmpty())) {
            data.put("choiceMapMarkers", CHOICE_MARKERS_CODEC.encodeStart(ops, choiceMarkers).getOrThrow());
        }
        var stack = AEItems.BLANK_PATTERN.stack();
        stack.set(AEComponents.ENCODED_CRAFTING_PATTERN,
                Objects.requireNonNull(source.get(AEComponents.ENCODED_CRAFTING_PATTERN)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return Objects.requireNonNull(AEItemKey.of(stack));
    }

    private static boolean marked(AEItemKey definition) {
        if (definition == null || !definition.is(AEItems.BLANK_PATTERN)
                || definition.get(AEComponents.ENCODED_CRAFTING_PATTERN) == null) return false;
        var data = definition.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(MARKER);
    }

    private static CraftingRecipe liveRecipe(AEItemKey definition, Level level) {
        if (definition == null || level == null) return null;
        var encoded = definition.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (encoded == null) return null;
        var holder = RecipeAccess.byKey(level, RecipeType.CRAFTING, encoded.recipeId());
        return holder == null ? null : holder.value();
    }

    // Hints may grow; identity is the complete native record, including all components and flags.
    // Both this final subtype and AE2's base class reject the other, preserving equality symmetry.
    @Override public boolean equals(Object other) {
        return other instanceof CatalogCraftingPattern pattern && nativeIdentity.equals(pattern.nativeIdentity);
    }
    @Override public int hashCode() { return nativeIdentity.hashCode(); }
    @Override public IPatternDetails.IInput[] getInputs() { return catalogInputs; }
}
