package com.example.ae2lightoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import com.example.ae2lightoptimizer.integration.Ae2GlobalCraftingOptimizer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCalculation.class, remap = false)
abstract class CraftingCalculationMixin {
    @Shadow
    @Final
    private NetworkCraftingSimulationState networkInv;

    @Shadow
    @Final
    private AEKey output;

    @Shadow
    private boolean simulate;

    @Unique
    private IGrid ae2lightoptimizer$grid;

    @Unique
    private boolean ae2lightoptimizer$hasMultiplePaths;

    @Shadow
    abstract void addMissing(AEKey what, long amount);

    @Inject(method = "<init>", at = @At("RETURN"), require = 1, expect = 1)
    private void ae2lightoptimizer$captureGrid(Level level, IGrid grid,
                                               ICraftingSimulationRequester requester,
                                               GenericStack output, CalculationStrategy strategy,
                                               CallbackInfo callback) {
        this.ae2lightoptimizer$grid = grid;
    }

    @Inject(method = "runCraftAttempt", at = @At("HEAD"), cancellable = true,
            require = 1, expect = 1)
    private void ae2lightoptimizer$runFastPath(boolean simulate, long amount,
                                               CallbackInfoReturnable<CraftingPlan> callback) {
        var attempt = Ae2GlobalCraftingOptimizer.tryPlan(
                ae2lightoptimizer$grid,
                networkInv,
                (CraftingCalculation) (Object) this,
                output,
                amount,
                simulate,
                this::addMissing,
                value -> this.ae2lightoptimizer$hasMultiplePaths = value,
                value -> this.simulate = value);
        if (attempt.handled()) {
            callback.setReturnValue(attempt.plan());
        }
    }

    @Inject(method = "hasMultiplePaths", at = @At("HEAD"), cancellable = true,
            require = 1, expect = 1)
    private void ae2lightoptimizer$reportGlobalMultiplePaths(
            CallbackInfoReturnable<Boolean> callback) {
        if (ae2lightoptimizer$hasMultiplePaths) {
            callback.setReturnValue(true);
        }
    }
}
