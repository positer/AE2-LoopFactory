package com.example.ae2lightoptimizer.integration;

import java.util.ArrayList;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.pattern.EncodedCraftingPattern;
import appeng.crafting.pattern.EncodedSmithingTablePattern;
import appeng.crafting.pattern.EncodedStonecuttingPattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.core.component.DataComponents;

/** Job/definition-local persistence for vanilla's network-only map postprocessing component. */
public final class RipperMapSerialization {
    public static final String TAG_NAME = "ae2loMapPostProcessing";
    private RipperMapSerialization() {}

    public static CompoundTag markers(AEKey key) {
        var data = new CompoundTag();
        if (!(key instanceof AEItemKey item)) return data;
        remember(data, "self", item.toStack());
        var crafting = item.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) {
            for (int slot = 0; slot < crafting.inputs().size(); slot++) remember(data, "craft_" + slot, crafting.inputs().get(slot));
            remember(data, "craft_result", crafting.result());
        }
        var smithing = item.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) {
            remember(data, "smith_template", smithing.template());
            remember(data, "smith_base", smithing.base());
            remember(data, "smith_addition", smithing.addition());
            remember(data, "smith_result", smithing.resultItem());
        }
        var stone = item.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stone != null) {
            remember(data, "stone_input", stone.input());
            remember(data, "stone_output", stone.output());
        }
        return data;
    }

    public static AEKey restoreKey(AEKey decoded, CompoundTag data) {
        if (data.isEmpty()) return decoded;
        if (!(decoded instanceof AEItemKey item)) throw new IllegalArgumentException("Map marker without item key");
        var stack = restoreStack(item.toStack(), data, "self");
        var crafting = item.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) {
            var inputs = new ArrayList<ItemStack>(crafting.inputs().size());
            for (int slot = 0; slot < crafting.inputs().size(); slot++) {
                inputs.add(restoreStack(crafting.inputs().get(slot), data, "craft_" + slot));
            }
            stack.set(AEComponents.ENCODED_CRAFTING_PATTERN, new EncodedCraftingPattern(inputs,
                    restoreStack(crafting.result(), data, "craft_result"), crafting.recipeId(),
                    crafting.canSubstitute(), crafting.canSubstituteFluids()));
        } else if (data.contains("craft_result") || hasSlotMarker(data)) {
            throw new IllegalArgumentException("Map marker without crafting record");
        }
        var smithing = item.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) {
            stack.set(AEComponents.ENCODED_SMITHING_TABLE_PATTERN, new EncodedSmithingTablePattern(
                    restoreStack(smithing.template(), data, "smith_template"),
                    restoreStack(smithing.base(), data, "smith_base"),
                    restoreStack(smithing.addition(), data, "smith_addition"),
                    restoreStack(smithing.resultItem(), data, "smith_result"), smithing.canSubstitute(), smithing.recipeId()));
        } else if (data.contains("smith_template") || data.contains("smith_base")
                || data.contains("smith_addition") || data.contains("smith_result")) {
            throw new IllegalArgumentException("Map marker without smithing record");
        }
        var stone = item.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stone != null) {
            stack.set(AEComponents.ENCODED_STONECUTTING_PATTERN, new EncodedStonecuttingPattern(
                    restoreStack(stone.input(), data, "stone_input"), restoreStack(stone.output(), data, "stone_output"),
                    stone.canSubstitute(), stone.recipeId()));
        } else if (data.contains("stone_input") || data.contains("stone_output")) {
            throw new IllegalArgumentException("Map marker without stonecutting record");
        }
        return AEItemKey.of(stack);
    }

    private static void remember(CompoundTag data, String name, ItemStack stack) {
        var marker = stack.get(DataComponents.MAP_POST_PROCESSING);
        if (marker != null) data.putString(name, marker.name());
    }

    private static ItemStack restoreStack(ItemStack original, CompoundTag data, String name) {
        if (!data.contains(name)) return original.copy();
        if (!original.is(Items.FILLED_MAP) || original.get(DataComponents.MAP_ID) == null) {
            throw new IllegalArgumentException("Map postprocessing marker lacks the corresponding map identity");
        }
        var marker = MapPostProcessing.valueOf(data.getStringOr(name, ""));
        var present = original.get(DataComponents.MAP_POST_PROCESSING);
        if (present != null && present != marker) throw new IllegalArgumentException("Conflicting map postprocessing marker");
        var copy = original.copy();
        copy.set(DataComponents.MAP_POST_PROCESSING, marker);
        return copy;
    }

    private static boolean hasSlotMarker(CompoundTag data) {
        for (int slot = 0; slot < 9; slot++) if (data.contains("craft_" + slot)) return true;
        return false;
    }
}
