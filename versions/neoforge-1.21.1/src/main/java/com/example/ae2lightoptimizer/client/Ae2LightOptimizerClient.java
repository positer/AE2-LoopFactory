package com.example.ae2lightoptimizer.client;

import java.util.LinkedHashMap;
import java.util.Map;

import appeng.api.client.StorageCellModels;
import appeng.api.storage.StorageCells;
import appeng.items.tools.powered.AbstractPortableCell;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.storage.LoopStorageCellItem;
import com.example.ae2lightoptimizer.storage.PortableLoopStorageCellItem;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@Mod(value = Ae2LightOptimizer.MOD_ID, dist = Dist.CLIENT)
public final class Ae2LightOptimizerClient {
    private static final Map<String, ResourceLocation> LOOP_STORAGE_DRIVE_MODELS = createDriveModels();

    private static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        appeng.init.client.InitScreens.register(event,
                com.example.ae2lightoptimizer.menu.CraftingRipperMenu.TYPE.get(),
                CraftingRipperScreen::new, "/screens/ae2lightoptimizer_crafting_ripper.json");
    }

    public Ae2LightOptimizerClient(IEventBus modEventBus) {
        modEventBus.addListener(Ae2LightOptimizerClient::registerScreens);
        modEventBus.addListener(Ae2LightOptimizerClient::clientSetup);
        modEventBus.addListener(Ae2LightOptimizerClient::registerAdditionalModels);
        modEventBus.addListener(Ae2LightOptimizerClient::registerItemColors);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModItems.LOOP_STORAGE_CELLS.forEach(cell -> {
            LoopStorageCellItem item = cell.get();
            StorageCellModels.registerModel(item, LOOP_STORAGE_DRIVE_MODELS.get(item.tier().id()));
        }));
    }

    private static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        LOOP_STORAGE_DRIVE_MODELS.values().forEach(model ->
                event.register(ModelResourceLocation.standalone(model)));
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) {
                return 0xFFFFFFFF;
            }
            var inventory = StorageCells.getCellInventory(stack, null);
            return inventory == null
                    ? 0xFF00FF00
                    : 0xFF000000 | (inventory.getStatus().getStateColor() & 0xFFFFFF);
        }, ModItems.LOOP_STORAGE_CELLS.stream()
                .map(cell -> cell.get())
                .toArray(LoopStorageCellItem[]::new));
        event.register((stack, tintIndex) -> FastColor.ARGB32.opaque(
                ((AbstractPortableCell) stack.getItem()).getColor(stack)),
                ModItems.PORTABLE_LOOP_STORAGE_CELLS.stream()
                        .map(cell -> cell.get())
                        .toArray(PortableLoopStorageCellItem[]::new));
    }

    private static Map<String, ResourceLocation> createDriveModels() {
        Map<String, ResourceLocation> models = new LinkedHashMap<>();
        for (String tier : new String[] {
                "1k", "4k", "16k", "64k", "256k", "1m", "4m", "16m", "64m", "256m", "infinite"
        }) {
            models.put(tier, ResourceLocation.fromNamespaceAndPath(
                    Ae2LightOptimizer.MOD_ID, "block/drive/cells/" + tier + "_loop_storage_cell"));
        }
        return Map.copyOf(models);
    }
}
