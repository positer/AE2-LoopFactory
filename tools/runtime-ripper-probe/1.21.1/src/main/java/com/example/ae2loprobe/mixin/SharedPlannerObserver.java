package com.example.ae2loprobe.mixin;

import com.example.ae2lightoptimizer.crafting.GlobalCraftingPlan;
import com.example.ae2lightoptimizer.crafting.GlobalCraftingPlanner;
import com.example.ae2lightoptimizer.crafting.GlobalPlanRequest;
import com.example.ae2loprobe.PlannerDiagnostic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GlobalCraftingPlanner.class, remap = false)
public abstract class SharedPlannerObserver {
    @Inject(method = "plan", at = @At("HEAD"))
    private void ae2loProbe$request(GlobalPlanRequest request, CallbackInfoReturnable<GlobalCraftingPlan> callback) {
        PlannerDiagnostic.request(request);
    }

    @Inject(method = "plan", at = @At("RETURN"))
    private void ae2loProbe$result(GlobalPlanRequest request, CallbackInfoReturnable<GlobalCraftingPlan> callback) {
        PlannerDiagnostic.result(callback.getReturnValue());
    }
}
