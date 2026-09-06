package com.example.ae2loprobe.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.inv.ListCraftingInventory;
import com.example.ae2lightoptimizer.integration.CraftingRipperExecutor;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import com.example.ae2loprobe.RuntimeRipperProbe;
import com.example.ae2loprobe.NativeContinuationAssertions;
import com.example.ae2loprobe.ExactComponentFixture;
import com.example.ae2loprobe.ToolComponentFixture;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measures only the production synchronous executor, isolating its fee from ordinary grid idle power. */
@Mixin(value = CraftingRipperExecutor.class, remap = false)
public abstract class RipperExecutionObserver {
    @Inject(method = "execute", at = @At("HEAD"), require = 1)
    private static void ae2loprobe$before(IGrid grid, IEnergyService energy, ListCraftingInventory inventory,
            ScheduledCraftingJob job, Level level, CallbackInfoReturnable<CraftingRipperExecutor.Result> callback) {
        RuntimeRipperProbe.beforeRipper(grid, energy, inventory);
        NativeContinuationAssertions.before(energy, inventory, job, level);
        ExactComponentFixture.beforeRipper(grid, energy, job, level);
        ToolComponentFixture.beforeRipper(grid, energy, job, level);
    }

    @Inject(method = "execute", at = @At("RETURN"), require = 1)
    private static void ae2loprobe$after(IGrid grid, IEnergyService energy, ListCraftingInventory inventory,
            ScheduledCraftingJob job, Level level, CallbackInfoReturnable<CraftingRipperExecutor.Result> callback) {
        RuntimeRipperProbe.afterRipper(grid, energy, inventory, callback.getReturnValue());
        NativeContinuationAssertions.after(energy, inventory, job, level, callback.getReturnValue());
        ExactComponentFixture.afterRipper(grid, energy, inventory, job, level, callback.getReturnValue());
        ToolComponentFixture.afterRipper(grid, energy, inventory, job, level, callback.getReturnValue());
    }
}
