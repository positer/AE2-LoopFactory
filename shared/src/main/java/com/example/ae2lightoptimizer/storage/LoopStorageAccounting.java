package com.example.ae2lightoptimizer.storage;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** Converts any dynamically registered AE key type into the cell's shared byte budget. */
public final class LoopStorageAccounting {
    private LoopStorageAccounting() {
    }

    public static OptionalLong dataBytes(long amount, long amountPerByte) {
        if (amount < 0 || amountPerByte <= 0) {
            return OptionalLong.empty();
        }
        if (amount == 0) {
            return OptionalLong.of(0);
        }

        long wholeBytes = amount / amountPerByte;
        long remainder = amount % amountPerByte;
        if (remainder == 0) {
            return OptionalLong.of(wholeBytes);
        }
        if (wholeBytes == Long.MAX_VALUE) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(wholeBytes + 1);
    }

    public static OptionalLong entryBytes(
            LoopStorageTier tier, long amount, long amountPerByte, boolean newType) {
        Objects.requireNonNull(tier, "tier");
        OptionalLong dataBytes = dataBytes(amount, amountPerByte);
        if (dataBytes.isEmpty()) {
            return OptionalLong.empty();
        }

        try {
            long typeOverhead = newType ? tier.bytesPerType() : 0;
            return OptionalLong.of(Math.addExact(dataBytes.getAsLong(), typeOverhead));
        } catch (ArithmeticException overflow) {
            return OptionalLong.empty();
        }
    }

    public static boolean allowsTypeCount(LoopStorageTier tier, long typeCount) {
        Objects.requireNonNull(tier, "tier");
        return typeCount >= 0 && (!tier.hasTypeLimit() || typeCount <= tier.maxTypes());
    }

    public static boolean fits(LoopStorageTier tier, long typeCount, long usedBytes) {
        Objects.requireNonNull(tier, "tier");
        return usedBytes >= 0
                && allowsTypeCount(tier, typeCount)
                && (tier.infinite() || usedBytes <= tier.capacityBytes());
    }

    public static <K, T> Optional<LoopStorageUsage> summarize(
            LoopStorageTier tier, Iterable<LoopStorageKeyUsage<K, T>> entries) {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(entries, "entries");

        Map<T, KeyTypeAccumulator> usageByKeyType = new HashMap<>();
        Set<K> uniqueKeys = new HashSet<>();
        try {
            for (LoopStorageKeyUsage<K, T> entry : entries) {
                Objects.requireNonNull(entry, "entry");
                uniqueKeys.add(entry.key());
                KeyTypeAccumulator accumulator = usageByKeyType.computeIfAbsent(
                        entry.keyType(), ignored -> new KeyTypeAccumulator(entry.amountPerByte()));
                if (!accumulator.add(entry.amount(), entry.amountPerByte())) {
                    return Optional.empty();
                }
            }

            long contentBytes = 0;
            boolean hasPartialContentByte = false;
            if (tier.usesAggregatePool()) {
                var aggregate = aggregateDataBytes(usageByKeyType.values());
                contentBytes = aggregate.dataBytes();
                hasPartialContentByte = aggregate.hasPartialByte();
            } else {
                for (KeyTypeAccumulator accumulator : usageByKeyType.values()) {
                    contentBytes = Math.addExact(contentBytes, accumulator.dataBytes());
                    hasPartialContentByte |= accumulator.hasPartialByte();
                }
            }

            long typeCount = uniqueKeys.size();
            long typeBytes = Math.multiplyExact(typeCount, tier.bytesPerType());
            long usedBytes = Math.addExact(contentBytes, typeBytes);
            return Optional.of(new LoopStorageUsage(
                    typeCount, contentBytes, typeBytes, usedBytes, hasPartialContentByte));
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
    }

    private static AggregateBytes aggregateDataBytes(Iterable<KeyTypeAccumulator> accumulators) {
        long wholeBytes = 0;
        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ONE;
        boolean hasFraction = false;

        for (KeyTypeAccumulator accumulator : accumulators) {
            wholeBytes = Math.addExact(wholeBytes, accumulator.wholeBytes);
            if (accumulator.remainder == 0) {
                continue;
            }
            hasFraction = true;
            BigInteger divisor = BigInteger.valueOf(accumulator.amountPerByte);
            numerator = numerator.multiply(divisor)
                    .add(BigInteger.valueOf(accumulator.remainder).multiply(denominator));
            denominator = denominator.multiply(divisor);
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }

        if (!hasFraction) {
            return new AggregateBytes(wholeBytes, false);
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        long completeFractionBytes = division[0].longValueExact();
        boolean partialByte = division[1].signum() != 0;
        long roundedFractionBytes = Math.addExact(completeFractionBytes, partialByte ? 1 : 0);
        return new AggregateBytes(Math.addExact(wholeBytes, roundedFractionBytes), partialByte);
    }

    private record AggregateBytes(long dataBytes, boolean hasPartialByte) {
    }

    public static boolean canAddNewType(LoopStorageTier tier, LoopStorageUsage usage) {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(usage, "usage");
        if (tier.hasTypeLimit() && usage.typeCount() >= tier.maxTypes()) {
            return false;
        }
        if (tier.infinite()) {
            return true;
        }
        if (usage.usedBytes() > tier.capacityBytes()) {
            return false;
        }

        long remainingBytes = tier.capacityBytes() - usage.usedBytes();
        long requiredBytes = tier.bytesPerType() + (usage.hasPartialContentByte() ? 0L : 1L);
        return remainingBytes >= requiredBytes;
    }

    public static boolean canGrowExistingType(LoopStorageTier tier, LoopStorageUsage usage) {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(usage, "usage");
        if (usage.typeCount() == 0 || !allowsTypeCount(tier, usage.typeCount())) {
            return false;
        }
        if (tier.infinite()) {
            return true;
        }
        if (usage.usedBytes() > tier.capacityBytes()) {
            return false;
        }
        return usage.usedBytes() < tier.capacityBytes() || usage.hasPartialContentByte();
    }

    private static final class KeyTypeAccumulator {
        private final long amountPerByte;
        private long wholeBytes;
        private long remainder;

        private KeyTypeAccumulator(long amountPerByte) {
            this.amountPerByte = amountPerByte;
        }

        private boolean add(long amount, long entryAmountPerByte) {
            if (amountPerByte != entryAmountPerByte) {
                return false;
            }

            wholeBytes = Math.addExact(wholeBytes, amount / amountPerByte);
            long additionalRemainder = amount % amountPerByte;
            if (additionalRemainder == 0) {
                return true;
            }

            long unitsUntilNextByte = amountPerByte - remainder;
            if (additionalRemainder >= unitsUntilNextByte) {
                wholeBytes = Math.addExact(wholeBytes, 1);
                remainder = additionalRemainder - unitsUntilNextByte;
            } else {
                remainder += additionalRemainder;
            }
            return true;
        }

        private long dataBytes() {
            return remainder == 0 ? wholeBytes : Math.addExact(wholeBytes, 1);
        }

        private boolean hasPartialByte() {
            return remainder != 0;
        }
    }
}
