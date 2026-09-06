package com.example.ae2lightoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Keep redstone/pulse/result locking owned by the pinned native provider implementation. */
@Mixin(value = PatternProviderLogic.class, remap = false)
public interface PatternProviderLogicInvoker {
    @Invoker("onPushPatternSuccess")
    void ae2lightoptimizer$onPushPatternSuccess(IPatternDetails pattern);

    @Invoker("onStackReturnedToNetwork")
    void ae2lightoptimizer$onStackReturnedToNetwork(GenericStack output);
}
