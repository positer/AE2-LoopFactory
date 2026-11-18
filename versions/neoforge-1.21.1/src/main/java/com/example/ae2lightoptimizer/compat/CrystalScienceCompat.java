package com.example.ae2lightoptimizer.compat;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import io.github.lounode.ae2cs.common.item.CrystalSeedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Loaded only in installations with Crystal Science. Its seed owns water and chamber growth. */
public final class CrystalScienceCompat {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Ae2LightOptimizer.NAMESPACE);
    public static final DeferredItem<Item> PURE = ITEMS.registerSimpleItem("purified_loop_crystal");
    public static final DeferredItem<CrystalSeedItem> SEED = ITEMS.registerItem("loop_crystal_seed",
            properties -> new CrystalSeedItem(properties, PURE));

    private CrystalScienceCompat() {}

    public static void register(IEventBus bus) {
        com.example.ae2lightoptimizer.LegacyNamespace.bind(ITEMS, bus);
        bus.addListener(CrystalScienceCompat::addTextures);
    }

    public static void display(CreativeModeTab.Output output) {
        output.accept(SEED.get());
        output.accept(PURE.get());
    }

    private static void addTextures(AddPackFindersEvent event) {
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath(Ae2LightOptimizer.MOD_ID, "resourcepacks/crystal_science"),
                PackType.CLIENT_RESOURCES, Component.literal("AE2LpT • Crystal Science"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }
}
