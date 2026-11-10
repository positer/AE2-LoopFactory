package com.example.ae2lightoptimizer.crafting;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Installed definitions exclusively own their exact output identities, not just a preference score. */
public final class RipperPatternPolicy {
    public static <P, K> List<P> select(Collection<P> installed, Collection<P> automatic,
            Set<K> installedOutputs, Function<P, K> primaryOutput) {
        var result = new LinkedHashSet<P>(installed);
        for (P pattern : automatic)
            if (!installedOutputs.contains(primaryOutput.apply(pattern))) result.add(pattern);
        return List.copyOf(result);
    }

    private RipperPatternPolicy() {}
}
