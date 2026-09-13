package com.example.ae2lightoptimizer.factory;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.level.Level;
import java.util.List;

public record FactoryPatternDetails(AEItemKey definition, FactoryPatternData data,
        IPatternDetails delegate) implements IPatternDetails {
    public static FactoryPatternDetails decode(AEItemKey key, Level level) {
        var data = key.get(FactoryPatternData.TYPE.get());
        if (data == null || !data.hasRecipe() || data.factoryId().isBlank()) return null;
        // Only native AE2 encoded recipes can be wrapped; nested factories are invalid.
        if (!data.recipe().getItem().getClass().getName().startsWith("appeng.")) return null;
        var delegate = PatternDetailsHelper.decodePattern(data.recipe(), level);
        if (delegate == null) return null;
        FactoryCompiler.compile(data.code(), true, delegate.getInputs().length,delegate.getOutputs().size());
        return new FactoryPatternDetails(key, data, delegate);
    }
    @Override public AEItemKey getDefinition() { return definition; }
    @Override public IInput[] getInputs() { return delegate.getInputs(); }
    @Override public List<GenericStack> getOutputs() { return delegate.getOutputs(); }
}
