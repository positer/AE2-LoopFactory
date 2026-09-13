package com.example.ae2lightoptimizer.factory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import java.util.function.Predicate;
import java.util.function.Consumer;

/** Direct source-to-target routing. get never calls this operation or extracts physical resources. */
public final class FactoryTransfers {
    private FactoryTransfers() {}
    public static long move(MEStorage source, MEStorage destination, Predicate<AEKey> filter,
            long limit, IActionSource action, Consumer<GenericStack> recovery) {
        if (source == destination) return 0;
        long moved = 0;
        for (var entry : source.getAvailableStacks()) {
            AEKey key = entry.getKey();
            if (!filter.test(key) || moved >= limit) continue;
            long offered = Math.min(limit - moved, entry.getLongValue());
            if (offered <= 0) continue;
            long accepted = destination.insert(key, offered, Actionable.SIMULATE, action);
            if (accepted < 0 || accepted > offered) throw new IllegalStateException("Storage violated insertion simulation contract");
            if (accepted == 0) continue;
            long extracted = source.extract(key, accepted, Actionable.MODULATE, action);
            if (extracted < 0 || extracted > accepted) throw new IllegalStateException("Storage violated extraction contract");
            if (extracted == 0) continue;
            long inserted = destination.insert(key, extracted, Actionable.MODULATE, action);
            if (inserted < 0 || inserted > extracted) throw new IllegalStateException("Storage violated insertion contract");
            if (inserted < extracted) {
                long remainder = extracted - inserted;
                long restored = source.insert(key, remainder, Actionable.MODULATE, action);
                if (restored < remainder) {
                    // A third-party handler invalidated its simulation and rejects rollback.
                    // The provider's existing source cache owns these actual resources; never discard them.
                    recovery.accept(new GenericStack(key, remainder - restored));
                    throw new IllegalStateException("Target invalidated transfer reservation; residual resources retained in provider source");
                }
            }
            moved += inserted;
        }
        return moved;
    }
}
