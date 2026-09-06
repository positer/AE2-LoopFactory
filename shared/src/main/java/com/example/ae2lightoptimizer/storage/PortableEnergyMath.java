package com.example.ae2lightoptimizer.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Exact FE accounting at the boundary of AE2's bounded double-valued battery. */
public final class PortableEnergyMath {
    private static final long MAX_EXACT_DOUBLE_INTEGER = 1L << 53;

    private PortableEnergyMath() {
    }

    public static long chargeAmount(long storedFe, double currentAe, double capacityAe,
            double chargeRateAe, double aePerFe) {
        if (storedFe <= 0 || !Double.isFinite(currentAe) || !Double.isFinite(capacityAe)
                || !Double.isFinite(chargeRateAe) || !Double.isFinite(aePerFe)
                || currentAe < 0 || aePerFe <= 0) {
            return 0;
        }
        double budget = Math.min(capacityAe - currentAe, chargeRateAe);
        if (budget <= 0) {
            return 0;
        }
        long result = Math.min(storedFe, Math.min(MAX_EXACT_DOUBLE_INTEGER, (long) (budget / aePerFe)));
        // Rounding at a fractional battery boundary must never overfill the battery.
        if (result > 0 && result * aePerFe > budget) {
            result--;
        }
        return Math.max(0, result);
    }

    public static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    public static int saturatedInt(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, amount));
    }

    public record StoredAmount<K>(K key, long amount) {
    }

    public record Debit<K>(long extracted, List<StoredAmount<K>> contents) {
    }

    /** Builds a replacement immutable component; the input is never changed by a probe. */
    public static <K> Debit<K> debit(List<StoredAmount<K>> contents, Predicate<K> matches, long requested) {
        if (requested <= 0) {
            return new Debit<>(0, contents);
        }
        long remaining = requested;
        var updated = new ArrayList<StoredAmount<K>>(contents.size());
        for (var entry : contents) {
            long taken = entry.amount() > 0 && matches.test(entry.key())
                    ? Math.min(remaining, entry.amount()) : 0;
            remaining -= taken;
            if (taken == 0) {
                updated.add(entry);
            } else if (taken < entry.amount()) {
                updated.add(new StoredAmount<>(entry.key(), entry.amount() - taken));
            }
        }
        return new Debit<>(requested - remaining, List.copyOf(updated));
    }

    public static boolean isForgeEnergy(String keyTypeId, String keyId) {
        // Both pinned Applied Flux generations expose FE under these public AEKey IDs.
        // GTEU shares the same key type in 1.21.1, so matching the type alone is unsafe.
        return "appflux:flux".equals(keyTypeId) && "appflux:fe".equals(keyId);
    }
}
