package com.example.ae2lightoptimizer;

import appeng.api.AECapabilities;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import com.example.ae2lightoptimizer.block.ModBlockEntities;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.storage.LoopStorageCellHandler;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(Ae2LightOptimizer.MOD_ID)
public final class Ae2LightOptimizer {
    public static final String MOD_ID = "ae2lightoptimizer";
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab, CreativeModeTab> AE2_LIGHTOPTIMIZER_TAB = CREATIVE_TABS.register("ae2_lightoptimizer", () -> CreativeModeTab.builder().title(net.minecraft.network.chat.Component.translatable("itemGroup.ae2lightoptimizer")).icon(() -> ModItems.LOOP_CRYSTAL.get().getDefaultInstance()).displayItems((p, o) -> { o.accept(com.example.ae2lightoptimizer.omni.OmniContent.ITEM.get()); o.accept(ModItems.CRAFTING_RIPPER.get()); o.accept(ModItems.LOOP_CONDENSER.get()); o.accept(ModItems.LOOP_TICKER.get()); o.accept(ModItems.LOOP_TICKER_PART.get()); o.accept(ModItems.LOOP_CARD.get()); o.accept(ModItems.LOOP_FACTORY_PATTERN.get()); com.example.ae2lightoptimizer.factory.FactoryContent.ITEMS.getEntries().forEach(item -> o.accept(item.get())); o.accept(ModItems.RECIPE_RING_SOLVER_TERMINAL.get()); o.accept(ModItems.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get()); o.accept(ModItems.LOOP_CRYSTAL.get()); o.accept(ModItems.LOOP_CRYSTAL_FRAGMENT.get()); o.accept(ModItems.LOOP_CRYSTAL_BLOCK.get()); o.accept(ModItems.LOOP_CRYSTAL_POWDER.get()); ModItems.loopStorageItems().forEach(o::accept); ModItems.PORTABLE_LOOP_STORAGE_CELLS.forEach(item -> item.get().emptyAndFullStacks().forEach(o::accept)); }).build());

    public Ae2LightOptimizer(IEventBus modEventBus) {
        verifyTakeoverTargetLoads();
        com.example.ae2lightoptimizer.omni.OmniContent.register(modEventBus);
        modEventBus.addListener(com.example.ae2lightoptimizer.menu.RipperPatternAccessPacket::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.example.ae2lightoptimizer.block.CraftingRipperLogic::onDatapackSync);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.example.ae2lightoptimizer.block.CraftingRipperLogic::onServerTick);
        ModBlocks.BLOCKS.register(modEventBus);
        com.example.ae2lightoptimizer.factory.FactoryContent.register(modEventBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.example.ae2lightoptimizer.factory.FactoryServer::tick);
        ModItems.ITEMS.register(modEventBus);
        com.example.ae2lightoptimizer.factory.FactoryPatternData.COMPONENTS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        com.example.ae2lightoptimizer.menu.CraftingRipperMenu.MENUS.register(modEventBus);
        com.example.ae2lightoptimizer.menu.LoopTickerMenu.MENUS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(Ae2LightOptimizer::commonSetup);
        modEventBus.addListener(Ae2LightOptimizer::registerCapabilities);
        modEventBus.addListener(Ae2LightOptimizer::addCreativeTabContents);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.example.ae2lightoptimizer.factory.FactoryContent.setup();
            com.example.ae2lightoptimizer.omni.OmniContent.setup();
            com.example.ae2lightoptimizer.integration.CatalogSmithingPattern.registerDecoder();
            com.example.ae2lightoptimizer.integration.CatalogCraftingPattern.registerDecoder();
            ModBlocks.CRAFTING_RIPPER.get().setBlockEntity(
                    com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity.class,
                    ModBlockEntities.CRAFTING_RIPPER.get(), null, null);
            Upgrades.add(ModItems.LOOP_CARD.get(), ModBlocks.CRAFTING_RIPPER.get(), 1);
            @SuppressWarnings({"unchecked", "rawtypes"})
            var condenserType = (net.minecraft.world.level.block.entity.BlockEntityType)
                    ModBlockEntities.LOOP_CONDENSER.get();
            @SuppressWarnings({"unchecked", "rawtypes"})
            var tickerType26 = (net.minecraft.world.level.block.entity.BlockEntityType)
                    ModBlockEntities.LOOP_TICKER.get();
            ModBlocks.LOOP_TICKER.get().setBlockEntity(
                    com.example.ae2lightoptimizer.block.LoopTickerBlockEntity.class, tickerType26, null, null);
            ModBlocks.LOOP_CONDENSER.get().setBlockEntity(
                    appeng.blockentity.misc.CondenserBlockEntity.class, condenserType, null, null);
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.LOOP_TICKER.get(), 4);
            Upgrades.add(AEItems.SPEED_CARD, ModItems.LOOP_TICKER_PART.get(), 4);
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.LOOP_CONDENSER.get(), ModItems.LOOP_CONDENSER.get());
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.LOOP_TICKER.get(), ModItems.LOOP_TICKER.get());
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.example.ae2lightoptimizer.integration.CondenserLoopService::onServerTick);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.example.ae2lightoptimizer.integration.LoopTickerService::onServerTick);
            StorageCells.addCellHandler(LoopStorageCellHandler.INSTANCE);
            for (var cell : java.util.List.of(ModItems.LOOP_STORAGE_CELL_1K, ModItems.LOOP_STORAGE_CELL_4K, ModItems.LOOP_STORAGE_CELL_16K, ModItems.LOOP_STORAGE_CELL_64K, ModItems.LOOP_STORAGE_CELL_256K, ModItems.LOOP_STORAGE_CELL_1M, ModItems.LOOP_STORAGE_CELL_4M, ModItems.LOOP_STORAGE_CELL_16M, ModItems.LOOP_STORAGE_CELL_64M, ModItems.LOOP_STORAGE_CELL_256M, ModItems.INFINITE_LOOP_STORAGE_CELL)) {
                Upgrades.add(AEItems.FUZZY_CARD, cell.get(), 1);
                Upgrades.add(AEItems.INVERTER_CARD, cell.get(), 1);
            }
            for (var cell : ModItems.PORTABLE_LOOP_STORAGE_CELLS) {
                var item = cell.get();
                Upgrades.add(ModItems.LOOP_CARD.get(), item, 1);
                Upgrades.add(AEItems.FUZZY_CARD, item, 1);
                Upgrades.add(AEItems.INVERTER_CARD, item, 1);
                Upgrades.add(AEItems.EQUAL_DISTRIBUTION_CARD, item, 1);
                Upgrades.add(AEItems.VOID_CARD, item, 1);
                Upgrades.add(AEItems.ENERGY_CARD, item, 2);
            }
        });
    }

    private static void verifyTakeoverTargetLoads() {
        try {
            // Forces all required calculation and execution targets to transform at startup.
            var loader = Ae2LightOptimizer.class.getClassLoader();
            Class.forName("appeng.crafting.CraftingCalculation", false, loader);
            Class.forName("appeng.helpers.patternprovider.PatternProviderLogic", false, loader);
            Class.forName("appeng.crafting.CraftingPlan", false, loader);
            Class.forName("appeng.crafting.execution.ExecutingCraftingJob", false, loader);
            Class.forName("appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", false, loader);
            Class.forName("appeng.crafting.execution.CraftingCpuLogic", false, loader);
            Class.forName("appeng.crafting.execution.ElapsedTimeTracker", false, loader);
        } catch (ClassNotFoundException missingAe2Internals) {
            throw new IllegalStateException("Cannot install the AE2 global crafting takeover", missingAe2Internals);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, com.example.ae2lightoptimizer.omni.OmniContent.ENTITY.get(), (host, context) -> host);
        com.example.ae2lightoptimizer.storage.PortableLoopEnergy.registerCapabilities(event);
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                ModBlockEntities.LOOP_CONDENSER.get(), (host, side) -> host.getExternalInv().toResourceHandler());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                ModBlockEntities.LOOP_CONDENSER.get(), (host, side) -> host.getFluidHandler());
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, com.example.ae2lightoptimizer.factory.FactoryContent.ENTITY.get(), (host, side) -> host);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.CRAFTING_RIPPER.get(), (ripper, side) -> ripper);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.RECIPE_RING_SOLVER_TERMINAL.get(),
                (terminal, ignoredContext) -> terminal);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get(),
                (optimizer, ignoredContext) -> optimizer);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.LOOP_CONDENSER.get(),
                (condenser, ignoredContext) -> condenser);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.LOOP_TICKER.get(), (ticker, ignoredContext) -> ticker);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(com.example.ae2lightoptimizer.omni.OmniContent.ITEM.get());
            event.accept(ModItems.CRAFTING_RIPPER.get());
            event.accept(ModItems.LOOP_CARD.get());
            event.accept(ModItems.LOOP_FACTORY_PATTERN.get());
            event.accept(ModItems.RECIPE_RING_SOLVER_TERMINAL.get());
            event.accept(ModItems.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get());
            event.accept(ModItems.LOOP_CRYSTAL.get());
            event.accept(ModItems.LOOP_CRYSTAL_FRAGMENT.get());
            event.accept(ModItems.LOOP_CRYSTAL_BLOCK.get());
            event.accept(ModItems.LOOP_CRYSTAL_POWDER.get());
            ModItems.loopStorageItems().forEach(event::accept);
            ModItems.PORTABLE_LOOP_STORAGE_CELLS.forEach(
                    item -> item.get().emptyAndFullStacks().forEach(event::accept));
        }
    }
}