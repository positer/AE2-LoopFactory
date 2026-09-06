package com.example.ae2loprobe.mixin;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import com.example.ae2lightoptimizer.integration.Ae2GlobalCraftingOptimizer;
import com.example.ae2loprobe.PlannerDiagnostic;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Ae2GlobalCraftingOptimizer.class, remap = false)
public abstract class PlannerBridgeObserver {
    @Inject(method = "tryPlan", at = @At("HEAD"))
    private static void ae2loProbe$begin(IGrid grid, NetworkCraftingSimulationState inventory,
            CraftingCalculation calculation, AEKey requestedKey, long requestedAmount, boolean simulate,
            ObjLongConsumer<AEKey> missingSink, Consumer<Boolean> multiplePathsSink, Consumer<Boolean> simulationSink,
            CallbackInfoReturnable<Ae2GlobalCraftingOptimizer.OptimizationAttempt> callback) {
        PlannerDiagnostic.begin();
    }

    @Inject(method = "idFor", at = @At("RETURN"))
    private static void ae2loProbe$alias(AEKey key, Map<AEKey, String> ids, Map<String, AEKey> resources,
            CallbackInfoReturnable<String> callback) {
        PlannerDiagnostic.alias(key, callback.getReturnValue());
    }

    @Inject(method = "tryPlan", at = @At("RETURN"))
    private static void ae2loProbe$outcome(IGrid grid, NetworkCraftingSimulationState inventory,
            CraftingCalculation calculation, AEKey requestedKey, long requestedAmount, boolean simulate,
            ObjLongConsumer<AEKey> missingSink, Consumer<Boolean> multiplePathsSink, Consumer<Boolean> simulationSink,
            CallbackInfoReturnable<Ae2GlobalCraftingOptimizer.OptimizationAttempt> callback) {
        PlannerDiagnostic.outcome(callback.getReturnValue());
    }
}
