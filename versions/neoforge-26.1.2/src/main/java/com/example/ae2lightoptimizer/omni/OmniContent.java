package com.example.ae2lightoptimizer.omni;

import net.neoforged.neoforge.registries.*;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class OmniContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("ae2lightoptimizer");
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, "ae2lightoptimizer");
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, net.minecraft.core.particles.SimpleParticleType> PULSE = PARTICLES.register("omni_pulse", () -> new net.minecraft.core.particles.SimpleParticleType(false) {});
    /** Ring particle shown while a formed Loop Factory structure is wirelessly bridged. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, net.minecraft.core.particles.SimpleParticleType> ORBIT = PARTICLES.register("omni_orbit", () -> new net.minecraft.core.particles.SimpleParticleType(false) {});
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2lightoptimizer");
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "ae2lightoptimizer");
    public static final DeferredBlock<OmniConnectorBlock> BLOCK = BLOCKS.registerBlock("omnidirectional_connector", OmniConnectorBlock::new,
            () -> net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(appeng.core.definitions.AEBlocks.WIRELESS_ACCESS_POINT.block()));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OmniConnectorBlockEntity>> ENTITY = ENTITIES.register("omnidirectional_connector",
            () -> new BlockEntityType<>(OmniConnectorBlockEntity::new, BLOCK.get()));
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); PARTICLES.register(bus); ENTITIES.register(bus); OmniConnectorMenu.MENUS.register(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(OmniNetworks::stopped);
    }
    public static void setup() {
        BLOCK.get().setBlockEntity(OmniConnectorBlockEntity.class, ENTITY.get(), (level, pos, state, endpoint) -> {
            // A small stable cadence; native random display ticks alone are too sparse for one block.
            if (Math.floorMod(level.getGameTime() + pos.asLong(), 12) == 0)
                BLOCK.get().animateTick(state, level, pos, level.getRandom());
            // Re-check the factory structure once per second so a machine that finished loading after the
            // neighbour update still joins the channel's factory network.
            if (!level.isClientSide() && Math.floorMod(level.getGameTime() + pos.asLong(), 20) == 0)
                endpoint.refreshFactoryLink();
        }, null);
        appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(ENTITY.get(), ITEM.get());
    }
    private OmniContent() {}
}
