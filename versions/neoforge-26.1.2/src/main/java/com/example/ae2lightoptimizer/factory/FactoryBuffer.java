package com.example.ae2lightoptimizer.factory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Private job storage, with exact amounts and a bounded number of distinct component-aware keys. */
public final class FactoryBuffer implements MEStorage {
    public static final int MAX_TYPES = 256;
    private final KeyCounter contents = new KeyCounter();
    @Override public Component getDescription() { return Component.translatable("gui.ae2lightoptimizer.factory.source_buffer"); }
    @Override public void getAvailableStacks(KeyCounter result) { result.addAll(contents); }
    @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(key, amount, mode, source);
        if (contents.get(key) == 0 && contents.size() >= MAX_TYPES) return 0;
        long inserted = Math.min(amount, Long.MAX_VALUE - contents.get(key));
        if (mode == Actionable.MODULATE && inserted > 0) contents.add(key, inserted);
        return inserted;
    }
    @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(key, amount, mode, source);
        long extracted = Math.min(amount, contents.get(key));
        if (mode == Actionable.MODULATE && extracted > 0) {
            contents.remove(key, extracted);
            contents.removeZeros();
        }
        return extracted;
    }
    public boolean isEmpty() { return contents.isEmpty(); }
    public List<GenericStack> snapshot() {
        var result = new ArrayList<GenericStack>();
        for (var entry : contents) if (entry.getLongValue() > 0) result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        return List.copyOf(result);
    }
    public static FactoryBuffer restore(List<GenericStack> stacks) {
        var buffer = new FactoryBuffer();
        for (var stack : stacks) {
            if (stack.amount() <= 0 || buffer.insert(stack.what(), stack.amount(), Actionable.MODULATE, IActionSource.empty()) != stack.amount())
                throw new IllegalArgumentException("Invalid factory buffer snapshot");
        }
        return buffer;
    }
}
