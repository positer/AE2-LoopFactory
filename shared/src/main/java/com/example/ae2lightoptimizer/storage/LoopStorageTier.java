package com.example.ae2lightoptimizer.storage;

/** Loader-independent capacity contract for every cyclic storage cell tier. */
public enum LoopStorageTier {
    SIZE_1K("1k", 1_024L, 1, 8, 63, false),
    SIZE_4K("4k", 4_096L, 1, 32, 63, false),
    SIZE_16K("16k", 16_384L, 1, 128, 63, false),
    SIZE_64K("64k", 65_536L, 1, 512, 63, false),
    SIZE_256K("256k", 262_144L, 1, 2_048, 63, false),
    SIZE_1M("1m", 1_048_576L, 63, 0, Integer.MAX_VALUE, false),
    SIZE_4M("4m", 4_194_304L, 63, 0, Integer.MAX_VALUE, false),
    SIZE_16M("16m", 16_777_216L, 63, 0, Integer.MAX_VALUE, false),
    SIZE_64M("64m", 67_108_864L, 63, 0, Integer.MAX_VALUE, false),
    SIZE_256M("256m", 268_435_456L, 63, 0, Integer.MAX_VALUE, false),
    INFINITE("infinite", Long.MAX_VALUE, 1, 0, Integer.MAX_VALUE, true);

    private final String id;
    private final long singleTypeCapacityBytes;
    private final int aggregateMultiplier;
    private final int bytesPerType;
    private final int maxTypes;
    private final boolean infinite;

    LoopStorageTier(String id, long singleTypeCapacityBytes, int aggregateMultiplier,
            int bytesPerType, int maxTypes, boolean infinite) {
        this.id = id;
        this.singleTypeCapacityBytes = singleTypeCapacityBytes;
        this.aggregateMultiplier = aggregateMultiplier;
        this.bytesPerType = bytesPerType;
        this.maxTypes = maxTypes;
        this.infinite = infinite;
    }

    public String id() {
        return id;
    }

    public long capacityBytes() {
        return infinite ? Long.MAX_VALUE : Math.multiplyExact(singleTypeCapacityBytes, aggregateMultiplier);
    }

    public long singleTypeCapacityBytes() {
        return singleTypeCapacityBytes;
    }

    public int aggregateMultiplier() {
        return aggregateMultiplier;
    }

    public boolean usesAggregatePool() {
        return infinite || aggregateMultiplier > 1;
    }

    public int bytesPerType() {
        return bytesPerType;
    }

    public int maxTypes() {
        return maxTypes;
    }

    public boolean infinite() {
        return infinite;
    }

    public boolean hasTypeLimit() {
        return maxTypes != Integer.MAX_VALUE;
    }

    public double idleDrain() {
        return switch (this) {
            case SIZE_1K -> 0.5;
            case SIZE_4K -> 1.0;
            case SIZE_16K -> 1.5;
            case SIZE_64K -> 2.0;
            case SIZE_256K -> 2.5;
            case SIZE_1M -> 3.0;
            case SIZE_4M -> 3.5;
            case SIZE_16M -> 4.0;
            case SIZE_64M -> 4.5;
            case SIZE_256M -> 5.0;
            case INFINITE -> 8.0;
        };
    }
}
