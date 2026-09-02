package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoopStorageTierTest {
    @Test
    void exposesTheExactFiniteCapacityProgression() {
        assertEquals(1_024L, LoopStorageTier.SIZE_1K.capacityBytes());
        assertEquals(4_096L, LoopStorageTier.SIZE_4K.capacityBytes());
        assertEquals(16_384L, LoopStorageTier.SIZE_16K.capacityBytes());
        assertEquals(65_536L, LoopStorageTier.SIZE_64K.capacityBytes());
        assertEquals(262_144L, LoopStorageTier.SIZE_256K.capacityBytes());
        assertEquals(66_060_288L, LoopStorageTier.SIZE_1M.capacityBytes());
        assertEquals(264_241_152L, LoopStorageTier.SIZE_4M.capacityBytes());
        assertEquals(1_056_964_608L, LoopStorageTier.SIZE_16M.capacityBytes());
        assertEquals(4_227_858_432L, LoopStorageTier.SIZE_64M.capacityBytes());
        assertEquals(16_911_433_728L, LoopStorageTier.SIZE_256M.capacityBytes());
    }

    @Test
    void keepsAe2TypeOverheadAndSixtyThreeTypeLimitForKTiers() {
        assertEquals(8, LoopStorageTier.SIZE_1K.bytesPerType());
        assertEquals(32, LoopStorageTier.SIZE_4K.bytesPerType());
        assertEquals(128, LoopStorageTier.SIZE_16K.bytesPerType());
        assertEquals(512, LoopStorageTier.SIZE_64K.bytesPerType());
        assertEquals(2_048, LoopStorageTier.SIZE_256K.bytesPerType());

        for (LoopStorageTier tier : LoopStorageTier.values()) {
            if (tier.id().endsWith("k")) {
                assertEquals(63, tier.maxTypes());
                assertTrue(tier.hasTypeLimit());
            }
        }
    }

    @Test
    void removesTypeOverheadAndHardLimitForMAndInfiniteTiers() {
        for (LoopStorageTier tier : LoopStorageTier.values()) {
            if (!tier.id().endsWith("k")) {
                assertEquals(0, tier.bytesPerType());
                assertEquals(Integer.MAX_VALUE, tier.maxTypes());
                assertFalse(tier.hasTypeLimit());
            }
        }

        assertFalse(LoopStorageTier.SIZE_256M.infinite());
        assertEquals(63, LoopStorageTier.SIZE_1M.aggregateMultiplier());
        assertEquals(1_048_576L, LoopStorageTier.SIZE_1M.singleTypeCapacityBytes());
        assertTrue(LoopStorageTier.INFINITE.infinite());
        assertEquals(Long.MAX_VALUE, LoopStorageTier.INFINITE.capacityBytes());
    }
}
