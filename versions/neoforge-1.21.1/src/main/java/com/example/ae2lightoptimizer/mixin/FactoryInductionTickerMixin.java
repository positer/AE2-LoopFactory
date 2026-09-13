package com.example.ae2lightoptimizer.mixin;

import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Factory code owns FE distribution; the optional card fills its source cache instead. */
@Pseudo
@Mixin(targets="com.glodblock.github.appflux.common.me.energy.EnergyTicker",remap=false)
public abstract class FactoryInductionTickerMixin {
    @Shadow @Final private Object host;
    @Inject(method="distribute",at=@At("HEAD"),cancellable=true)
    private void factoryOwnsDistribution(long tick,CallbackInfo ci) {
        if(host instanceof FactoryBlockEntity factory && factory.isProvider())ci.cancel();
    }
}
