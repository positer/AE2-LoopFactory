package com.example.ae2lightoptimizer.omni;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Serialises an omni connector's selected link through an AE2 memory card. */
public final class OmniMemoryCard {
    public static final String TAG = "ae2lfOmniLink";

    private OmniMemoryCard() {
    }

    public static void write(ItemStack card, String link) {
        var tag = card.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(TAG, link);
        card.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static String read(ItemStack card) {
        return card.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(TAG).orElse("");
    }
}