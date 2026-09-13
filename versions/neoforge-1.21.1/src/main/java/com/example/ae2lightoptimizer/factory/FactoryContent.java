package com.example.ae2lightoptimizer.factory;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.bus.api.IEventBus;

public final class FactoryContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Ae2LightOptimizer.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Ae2LightOptimizer.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ae2LightOptimizer.MOD_ID);
    public static final DeferredBlock<FactoryBlock> PROVIDER = block("loop_factory_pattern_provider", FactoryBlock.Kind.PROVIDER);
    public static final DeferredBlock<FactoryBlock> TERMINAL = block("loop_factory_network_terminal", FactoryBlock.Kind.TERMINAL);
    public static final DeferredBlock<FactoryBlock> CABLE = block("loop_factory_interface_cable", FactoryBlock.Kind.CABLE);
    public static final DeferredItem<appeng.items.parts.PartItem<FactoryEncodingPanel>> PANEL = ITEMS.registerItem("loop_factory_pattern_encoding_panel", properties -> new appeng.items.parts.PartItem<>(properties, FactoryEncodingPanel.class, FactoryEncodingPanel::new));
    public static final DeferredItem<FactoryEncoderItem> ENCODER = ITEMS.registerItem("handheld_loop_factory_encoder", FactoryEncoderItem::new);
    public static final DeferredItem<BlockItem> PROVIDER_ITEM = ITEMS.registerSimpleBlockItem(PROVIDER);
    public static final DeferredItem<BlockItem> TERMINAL_ITEM = ITEMS.registerSimpleBlockItem(TERMINAL);
    public static final DeferredItem<BlockItem> CABLE_ITEM = ITEMS.registerSimpleBlockItem(CABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactoryBlockEntity>> ENTITY = ENTITIES.register("loop_factory",
            () -> BlockEntityType.Builder.of(FactoryBlockEntity::new, PROVIDER.get(), TERMINAL.get(), CABLE.get()).build(null));
    private static DeferredBlock<FactoryBlock> block(String id, FactoryBlock.Kind kind) {
        return BLOCKS.register(id, () -> new FactoryBlock(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL), kind));
    }
    public static void register(IEventBus bus) { FactoryEncoderView.TYPE.getId(); bus.addListener(FactoryEncoderAction::register); BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); FactoryPanelRecipeMenu.TYPE.getId(); FactoryEditorMenu.MENUS.register(bus); }
    public static void setup() {
        FactoryInduction.setup();
        appeng.api.parts.PartModels.registerModels(FactoryEncodingPanel.OFF.getModels());
        appeng.api.parts.PartModels.registerModels(FactoryEncodingPanel.ON.getModels());
        appeng.api.parts.PartModels.registerModels(FactoryEncodingPanel.ACTIVE.getModels());
        for (var block : java.util.List.of(PROVIDER, TERMINAL, CABLE)) block.get().setBlockEntity(FactoryBlockEntity.class, ENTITY.get(), null, null);
    }
    private FactoryContent() {}
}
