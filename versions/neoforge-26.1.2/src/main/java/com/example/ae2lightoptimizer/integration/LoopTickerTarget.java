package com.example.ae2lightoptimizer.integration;

import com.example.ae2lightoptimizer.api.LoopTickerHost;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

public final class LoopTickerTarget {
    private static final ThreadLocal<Set<LoopTickerHost>> RUNNING =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private LoopTickerTarget() {}

    public static void accelerate(LoopTickerHost source, ServerLevel server, BlockPos target, int times) {
        if (!server.hasChunkAt(target) || !server.shouldTickBlocksAt(target)) return;
        var running = RUNNING.get();
        if (!running.add(source)) return;
        try {
            for (int i = 0; i < times; i++) {
                BlockState state = server.getBlockState(target);
                if (state.isAir()) break;
                BlockEntity entity = server.getBlockEntity(target);
                if (entity != null && !entity.isRemoved() && state.getBlock() instanceof EntityBlock block) {
                    @SuppressWarnings("unchecked")
                    var ticker = (BlockEntityTicker<BlockEntity>) block.getTicker(server, state, entity.getType());
                    if (ticker != null) ticker.tick(server, target, state, entity);
                }
                state = server.getBlockState(target);
                if (state.isRandomlyTicking()) state.randomTick(server, target, server.getRandom());
            }
        } finally {
            running.remove(source);
            if (running.isEmpty()) RUNNING.remove();
        }
    }
}