package com.example.ae2lightoptimizer.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets native chunk dependencies advance between shutdown unload batches. */
@Mixin(MinecraftServer.class)
abstract class MinecraftShutdownMixin {
    @Redirect(method = "stopServer()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerChunkCache;tick(Ljava/util/function/BooleanSupplier;Z)V"),
            require = 1, expect = 1, allow = 1)
    private void ae2lf$advanceShutdownChunks(ServerChunkCache chunks, BooleanSupplier hasTime, boolean tickChunks) {
        // The native shutdown supplier is always true. A completed save future can otherwise
        // keep re-enqueuing an unload callback before its generation dependencies can advance.
        long started = System.nanoTime();
        chunks.tick(() -> hasTime.getAsBoolean() && System.nanoTime() - started < 1_000_000L, tickChunks);

        // stopServer's own one-millisecond deadline may already have elapsed. Attempt native
        // task polling explicitly; leave all completion decisions to its hasWork/save loop.
        for (int polls = 0; polls < 32; polls++) {
            if (!chunks.pollTask()) break;
        }
    }
}
