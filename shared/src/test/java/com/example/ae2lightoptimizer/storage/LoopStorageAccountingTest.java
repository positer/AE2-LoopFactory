package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LoopStorageAccountingTest {
    private static final long ITEM_AMOUNT_PER_BYTE = 8;
    private static final long FLUID_AMOUNT_PER_BYTE = 8_000;

    @Test
    void chargesOneItemAndOneThousandFluidUnitsEqually() {
        assertEquals(1L, LoopStorageAccounting.dataBytes(1, ITEM_AMOUNT_PER_BYTE).orElseThrow());
        assertEquals(1L, LoopStorageAccounting.dataBytes(1_000, FLUID_AMOUNT_PER_BYTE).orElseThrow());
        assertEquals(1L, LoopStorageAccounting.dataBytes(1_001, FLUID_AMOUNT_PER_BYTE).orElseThrow());
        assertEquals(2L, LoopStorageAccounting.dataBytes(8_001, FLUID_AMOUNT_PER_BYTE).orElseThrow());
    }

    @Test
    void usesEachDynamicallyRegisteredKeyTypesOwnAmountPerByte() {
        assertEquals(3L, LoopStorageAccounting.dataBytes(17, 8).orElseThrow());
        assertEquals(3L, LoopStorageAccounting.dataBytes(30_000, 10_000).orElseThrow());
        assertEquals(3L, LoopStorageAccounting.dataBytes(3, 1).orElseThrow());
    }

    @Test
    void chargesTypeOverheadOnlyWhenTheCallerAddsANewType() {
        assertEquals(9L, LoopStorageAccounting.entryBytes(
                LoopStorageTier.SIZE_1K, 1, ITEM_AMOUNT_PER_BYTE, true).orElseThrow());
        assertEquals(1L, LoopStorageAccounting.entryBytes(
                LoopStorageTier.SIZE_1K, 1, ITEM_AMOUNT_PER_BYTE, false).orElseThrow());
        assertEquals(1L, LoopStorageAccounting.entryBytes(
                LoopStorageTier.SIZE_1M, 1, ITEM_AMOUNT_PER_BYTE, true).orElseThrow());
    }

    @Test
    void rejectsInvalidAndOverflowingAmountsInsteadOfWrapping() {
        assertTrue(LoopStorageAccounting.dataBytes(0, ITEM_AMOUNT_PER_BYTE).isPresent());
        assertEquals(0L, LoopStorageAccounting.dataBytes(0, ITEM_AMOUNT_PER_BYTE).orElseThrow());
        assertTrue(LoopStorageAccounting.dataBytes(-1, ITEM_AMOUNT_PER_BYTE).isEmpty());
        assertTrue(LoopStorageAccounting.dataBytes(1, 0).isEmpty());
        assertTrue(LoopStorageAccounting.entryBytes(
                LoopStorageTier.SIZE_256K, Long.MAX_VALUE, 1, true).isEmpty());
    }

    @Test
    void appliesTypeLimitsOnlyToKTiers() {
        assertTrue(LoopStorageAccounting.allowsTypeCount(LoopStorageTier.SIZE_1K, 63));
        assertFalse(LoopStorageAccounting.allowsTypeCount(LoopStorageTier.SIZE_1K, 64));
        assertTrue(LoopStorageAccounting.allowsTypeCount(LoopStorageTier.SIZE_1M, 1_000_000));
        assertTrue(LoopStorageAccounting.allowsTypeCount(LoopStorageTier.INFINITE, Long.MAX_VALUE));
        assertFalse(LoopStorageAccounting.allowsTypeCount(LoopStorageTier.SIZE_1M, -1));
    }

    @Test
    void mTierUsesOneSharedSixtyThreeTimesSingleTypeBudgetAcrossKeyTypes() {
        long capacity = LoopStorageTier.SIZE_1M.capacityBytes();
        long itemBytes = capacity / 2;
        long fluidBytes = capacity - itemBytes;
        LoopStorageUsage usage = LoopStorageAccounting.summarize(LoopStorageTier.SIZE_1M, List.of(
                new LoopStorageKeyUsage<>("iron", "items", itemBytes * ITEM_AMOUNT_PER_BYTE,
                        ITEM_AMOUNT_PER_BYTE),
                new LoopStorageKeyUsage<>("water", "fluids", fluidBytes * FLUID_AMOUNT_PER_BYTE,
                        FLUID_AMOUNT_PER_BYTE)))
                .orElseThrow();

        assertEquals(63L * LoopStorageTier.SIZE_1M.singleTypeCapacityBytes(), capacity);
        assertEquals(capacity, usage.contentBytes());
        assertEquals(0, usage.typeBytes());
        assertTrue(LoopStorageAccounting.fits(
                LoopStorageTier.SIZE_1M, usage.typeCount(), usage.usedBytes()));
    }

    @Test
    void mAndInfinitePoolsMergeFractionalBytesAcrossRegisteredKeyTypes() {
        for (LoopStorageTier tier : List.of(LoopStorageTier.SIZE_1M, LoopStorageTier.INFINITE)) {
            LoopStorageUsage usage = LoopStorageAccounting.summarize(tier, List.of(
                    new LoopStorageKeyUsage<>("iron", "items", 1, ITEM_AMOUNT_PER_BYTE),
                    new LoopStorageKeyUsage<>("water", "fluids", 1_000, FLUID_AMOUNT_PER_BYTE)))
                    .orElseThrow();

            assertEquals(1, usage.contentBytes());
            assertEquals(0, usage.typeBytes());
            assertTrue(usage.hasPartialContentByte());
        }
    }

    @Test
    void checksFiniteCapacityAndTreatsInfiniteTierAsUnbounded() {
        assertTrue(LoopStorageAccounting.fits(LoopStorageTier.SIZE_1K, 63, 1_024));
        assertFalse(LoopStorageAccounting.fits(LoopStorageTier.SIZE_1K, 63, 1_025));
        assertFalse(LoopStorageAccounting.fits(LoopStorageTier.SIZE_1K, 64, 1));
        assertTrue(LoopStorageAccounting.fits(LoopStorageTier.INFINITE, Long.MAX_VALUE, Long.MAX_VALUE));
        assertFalse(LoopStorageAccounting.fits(LoopStorageTier.INFINITE, 0, -1));
    }

    @Test
    void sharesPartialBytesBetweenKeysOfTheSameRegisteredKeyType() {
        LoopStorageUsage usage = LoopStorageAccounting.summarize(LoopStorageTier.SIZE_1K, List.of(
                new LoopStorageKeyUsage<>("iron", "items", 3, ITEM_AMOUNT_PER_BYTE),
                new LoopStorageKeyUsage<>("gold", "items", 5, ITEM_AMOUNT_PER_BYTE)))
                .orElseThrow();

        assertEquals(2, usage.typeCount());
        assertEquals(1, usage.contentBytes());
        assertEquals(16, usage.typeBytes());
        assertEquals(17, usage.usedBytes());
        assertFalse(usage.hasPartialContentByte());
    }

    @Test
    void neverSharesPartialBytesAcrossDifferentRegisteredKeyTypes() {
        LoopStorageUsage usage = LoopStorageAccounting.summarize(LoopStorageTier.SIZE_1K, List.of(
                new LoopStorageKeyUsage<>("iron", "items", 1, ITEM_AMOUNT_PER_BYTE),
                new LoopStorageKeyUsage<>("water", "fluids", 1_000, FLUID_AMOUNT_PER_BYTE)))
                .orElseThrow();

        assertEquals(2, usage.contentBytes());
        assertEquals(16, usage.typeBytes());
        assertEquals(18, usage.usedBytes());
        assertTrue(usage.hasPartialContentByte());
    }

    @Test
    void countsUniqueKeysForOverheadButAggregatesDuplicateKeyAmounts() {
        LoopStorageUsage usage = LoopStorageAccounting.summarize(LoopStorageTier.SIZE_4K, List.of(
                new LoopStorageKeyUsage<>("iron", "items", 4, ITEM_AMOUNT_PER_BYTE),
                new LoopStorageKeyUsage<>("iron", "items", 4, ITEM_AMOUNT_PER_BYTE)))
                .orElseThrow();

        assertEquals(1, usage.typeCount());
        assertEquals(1, usage.contentBytes());
        assertEquals(32, usage.typeBytes());
        assertEquals(33, usage.usedBytes());
    }

    @Test
    void rejectsInconsistentOrOverflowingRegisteredKeyTypeMetadata() {
        assertTrue(LoopStorageAccounting.summarize(LoopStorageTier.SIZE_1K, List.of(
                new LoopStorageKeyUsage<>("iron", "items", 1, 8),
                new LoopStorageKeyUsage<>("gold", "items", 1, 16))).isEmpty());
        assertTrue(LoopStorageAccounting.summarize(LoopStorageTier.SIZE_1K, List.of(
                new LoopStorageKeyUsage<>("iron", "items", Long.MAX_VALUE, 1),
                new LoopStorageKeyUsage<>("gold", "items", 1, 1))).isEmpty());
    }

    @Test
    void summarizesSameKeyTypeTotalsBeyondLongMaxWithoutOverflowing() {
        LoopStorageUsage usage = LoopStorageAccounting.summarize(LoopStorageTier.INFINITE, List.of(
                new LoopStorageKeyUsage<>("iron", "items", Long.MAX_VALUE, ITEM_AMOUNT_PER_BYTE),
                new LoopStorageKeyUsage<>("gold", "items", Long.MAX_VALUE, ITEM_AMOUNT_PER_BYTE)))
                .orElseThrow();

        assertEquals(2, usage.typeCount());
        assertEquals(2_305_843_009_213_693_952L, usage.contentBytes());
        assertEquals(usage.contentBytes(), usage.usedBytes());
        assertTrue(usage.hasPartialContentByte());
    }

    @Test
    void distinguishesAFullByteBudgetWithAndWithoutInsertableTailUnits() {
        LoopStorageUsage tailSpace = LoopStorageAccounting.summarize(LoopStorageTier.SIZE_1K, List.of(
                new LoopStorageKeyUsage<>("iron", "items", 8_121, ITEM_AMOUNT_PER_BYTE)))
                .orElseThrow();
        LoopStorageUsage exactlyFull = LoopStorageAccounting.summarize(LoopStorageTier.SIZE_1K, List.of(
                new LoopStorageKeyUsage<>("iron", "items", 8_128, ITEM_AMOUNT_PER_BYTE)))
                .orElseThrow();

        assertEquals(1_024, tailSpace.usedBytes());
        assertTrue(tailSpace.hasPartialContentByte());
        assertFalse(LoopStorageAccounting.canAddNewType(LoopStorageTier.SIZE_1K, tailSpace));
        assertTrue(LoopStorageAccounting.canGrowExistingType(LoopStorageTier.SIZE_1K, tailSpace));

        assertEquals(1_024, exactlyFull.usedBytes());
        assertFalse(exactlyFull.hasPartialContentByte());
        assertFalse(LoopStorageAccounting.canAddNewType(LoopStorageTier.SIZE_1K, exactlyFull));
        assertFalse(LoopStorageAccounting.canGrowExistingType(LoopStorageTier.SIZE_1K, exactlyFull));
    }

    @Test
    void reportsTypesFullWhenOnlyExistingTypesCanUseTheRemainingBytes() {
        LoopStorageUsage usage = new LoopStorageUsage(62, 524, 496, 1_020, false);

        assertFalse(LoopStorageAccounting.canAddNewType(LoopStorageTier.SIZE_1K, usage));
        assertTrue(LoopStorageAccounting.canGrowExistingType(LoopStorageTier.SIZE_1K, usage));
    }

    @Test
    void rejectsGrowthWhenPersistedContentsAlreadyExceedTheTypeLimit() {
        LoopStorageUsage usage = new LoopStorageUsage(64, 1, 512, 513, true);

        assertFalse(LoopStorageAccounting.canAddNewType(LoopStorageTier.SIZE_1K, usage));
        assertFalse(LoopStorageAccounting.canGrowExistingType(LoopStorageTier.SIZE_1K, usage));
    }

    @Test
    void definesOneIdleDrainScheduleForBothVersionAdapters() {
        assertEquals(0.5, LoopStorageTier.SIZE_1K.idleDrain());
        assertEquals(1.0, LoopStorageTier.SIZE_4K.idleDrain());
        assertEquals(1.5, LoopStorageTier.SIZE_16K.idleDrain());
        assertEquals(2.0, LoopStorageTier.SIZE_64K.idleDrain());
        assertEquals(2.5, LoopStorageTier.SIZE_256K.idleDrain());
        assertEquals(3.0, LoopStorageTier.SIZE_1M.idleDrain());
        assertEquals(3.5, LoopStorageTier.SIZE_4M.idleDrain());
        assertEquals(4.0, LoopStorageTier.SIZE_16M.idleDrain());
        assertEquals(4.5, LoopStorageTier.SIZE_64M.idleDrain());
        assertEquals(5.0, LoopStorageTier.SIZE_256M.idleDrain());
        assertEquals(8.0, LoopStorageTier.INFINITE.idleDrain());
    }
}
