package com.example.ae2loprobe.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import com.example.ae2loprobe.RuntimeRipperProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observation only: does not cancel, redirect, set variables or replace return values. */
@Mixin(value = CraftingCpuLogic.class, remap = false, priority = 500)
public abstract class CpuTickObserver {
    @Inject(method = "tickCraftingLogic", at = @At("HEAD"), require = 1)
    private void ae2loprobe$countNativeTick(IEnergyService energy, CraftingService crafting, CallbackInfo callback) {
        RuntimeRipperProbe.onCpuTick((CraftingCpuLogic) (Object) this);
    }

    @Inject(method = "finishJob", at = @At("RETURN"), require = 1)
    private void ae2loprobe$observeNativeFinish(boolean success, CallbackInfo callback) {
        RuntimeRipperProbe.afterFinish((CraftingCpuLogic) (Object) this, success);
    }
}
