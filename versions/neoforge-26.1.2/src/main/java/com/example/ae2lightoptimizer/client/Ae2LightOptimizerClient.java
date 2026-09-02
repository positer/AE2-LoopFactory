package com.example.ae2lightoptimizer.client;

import appeng.api.client.StorageCellModels;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.storage.LoopStorageTier;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InitializeClientRegistriesEvent;

@Mod(value = Ae2LightOptimizer.MOD_ID, dist = Dist.CLIENT)
public final class Ae2LightOptimizerClient {
    public Ae2LightOptimizerClient(IEventBus modEventBus) {
        modEventBus.addListener(Ae2LightOptimizerClient::initializeClientRegistries);
    }

    private static void initializeClientRegistries(InitializeClientRegistriesEvent event) {
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_1K, model(LoopStorageTier.SIZE_1K));
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_4K, model(LoopStorageTier.SIZE_4K));
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_16K, model(LoopStorageTier.SIZE_16K));
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_64K, model(LoopStorageTier.SIZE_64K));
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_256K, model(LoopStorageTier.SIZE_256K));
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_1M, model(LoopStorageTier.SIZE_1M));
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_4M, model(LoopStorageTier.SIZE_4M));
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_16M, model(LoopStorageTier.SIZE_16M));
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_64M, model(LoopStorageTier.SIZE_64M));
        StorageCellModels.registerModel(ModItems.LOOP_STORAGE_CELL_256M, model(LoopStorageTier.SIZE_256M));
        StorageCellModels.registerModel(ModItems.INFINITE_LOOP_STORAGE_CELL, model(LoopStorageTier.INFINITE));
    }

    private static Identifier model(LoopStorageTier tier) {
        return Identifier.fromNamespaceAndPath(
                Ae2LightOptimizer.MOD_ID,
                "block/drive/cells/" + tier.id() + "_loop_storage_cell");
    }
}
