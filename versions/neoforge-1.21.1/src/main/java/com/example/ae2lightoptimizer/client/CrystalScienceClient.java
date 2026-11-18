package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.compat.CrystalScienceCompat;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

final class CrystalScienceClient {
    private CrystalScienceClient() {}

    static void registerSeedAge() {
        var seed = CrystalScienceCompat.SEED.get();
        ItemProperties.register(seed, ResourceLocation.fromNamespaceAndPath("ae2cs", "age"),
                (stack, level, holder, salt) -> seed.getGrowProcess(stack));
    }
}
