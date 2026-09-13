package com.example.ae2lightoptimizer.mixin;

import com.example.ae2lightoptimizer.factory.FactoryServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies a virtual incoming signal to the explicitly tagged machine, without replacing its block. */
@Mixin(SignalGetter.class)
public interface FactorySignalMixin {
    @Inject(method = "hasNeighborSignal", at = @At("HEAD"), cancellable = true)
    default void ae2lf$hasPulse(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (FactoryServer.powered((SignalGetter) this, pos)) callback.setReturnValue(true);
    }
    @Inject(method = "getBestNeighborSignal", at = @At("HEAD"), cancellable = true)
    default void ae2lf$pulseStrength(BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        if (FactoryServer.powered((SignalGetter) this, pos)) callback.setReturnValue(15);
    }
}
