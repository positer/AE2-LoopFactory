package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortableEnergyMathTest {
    @Test
    void usesConfiguredConversionAndNativeChargeRate() {
        assertEquals(160, PortableEnergyMath.chargeAmount(Long.MAX_VALUE, 0, 200_000, 80, 0.5));
        assertEquals(480, PortableEnergyMath.chargeAmount(Long.MAX_VALUE, 0, 200_000, 240, 0.5));
        assertEquals(20, PortableEnergyMath.chargeAmount(1000, 0, 200_000, 80, 4));
        assertEquals(7, PortableEnergyMath.chargeAmount(7, 0, 200_000, 80, 0.5));
    }

    @Test
    void onlyConsumesWholeFeAndCanRestartAnEmptyBattery() {
        assertEquals(160, PortableEnergyMath.chargeAmount(1000, 0, 200_000, 80, 0.5));
        assertEquals(1, PortableEnergyMath.chargeAmount(1000, 99.25, 100, 80, 0.5));
        assertEquals(0, PortableEnergyMath.chargeAmount(1000, 99.75, 100, 80, 0.5));
        assertEquals(0, PortableEnergyMath.chargeAmount(1000, 100, 100, 80, 0.5));
    }

    @Test
    void rejectsInvalidConversionAndPowerStates() {
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0, PortableEnergyMath.chargeAmount(1000, 0, 200_000, 80, invalid));
        }
        assertEquals(0, PortableEnergyMath.chargeAmount(0, 0, 200_000, 80, 0.5));
        assertEquals(0, PortableEnergyMath.chargeAmount(1000, -1, 200_000, 80, 0.5));
        assertEquals(0, PortableEnergyMath.chargeAmount(1000, 0, Double.NaN, 80, 0.5));
    }

    @Test
    void rechargeConservesLongFeBeyondDoublePrecision() {
        long remaining = Long.MAX_VALUE;
        double charged = 0;
        long consumed = 0;
        while (charged < 200_000) {
            long amount = PortableEnergyMath.chargeAmount(remaining, charged, 200_000, 80, 0.5);
            assertTrue(amount > 0);
            remaining -= amount;
            consumed += amount;
            charged += amount * 0.5;
        }
        assertEquals(400_000, consumed);
        assertEquals(200_000, charged);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(remaining).add(BigInteger.valueOf(consumed)));
    }

    @Test
    void legacyIntViewsSaturateWithoutTruncatingLongContents() {
        assertEquals(Integer.MAX_VALUE, PortableEnergyMath.saturatedInt(Long.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, PortableEnergyMath.saturatedInt(1L << 40));
        assertEquals(0, PortableEnergyMath.saturatedInt(-1));
        assertEquals(Long.MAX_VALUE, PortableEnergyMath.saturatedAdd(Long.MAX_VALUE - 2, 3));
    }

    @Test
    void recognizesFeButNeverTreatsEuItemsOrFluidsAsFe() {
        assertTrue(PortableEnergyMath.isForgeEnergy("appflux:flux", "appflux:fe"));
        assertFalse(PortableEnergyMath.isForgeEnergy("appflux:flux", "appflux:gteu"));
        assertFalse(PortableEnergyMath.isForgeEnergy("ae2:item", "appflux:fe"));
        assertFalse(PortableEnergyMath.isForgeEnergy("ae2:fluid", "appflux:fe"));
    }

    @Test
    void simulatedDebitLeavesTheOriginalImmutableComponentUntouched() {
        var contents = List.of(entry("fe", Long.MAX_VALUE), entry("iron", 42), entry("eu", 17));
        var debit = PortableEnergyMath.debit(contents, "fe"::equals, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, debit.extracted());
        assertEquals(Long.MAX_VALUE, contents.get(0).amount());
        assertEquals(Long.MAX_VALUE - Integer.MAX_VALUE, debit.contents().get(0).amount());
        assertEquals(contents.subList(1, 3), debit.contents().subList(1, 3));
        assertThrows(UnsupportedOperationException.class, () -> debit.contents().clear());
    }

    @Test
    void handlesSeveralEntriesAndExactFinalDrainWithoutOverflow() {
        var contents = List.of(entry("fe", Long.MAX_VALUE - 1), entry("fe", 10), entry("iron", 42));
        var debit = PortableEnergyMath.debit(contents, "fe"::equals, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, debit.extracted());
        assertEquals(List.of(entry("fe", 9), entry("iron", 42)), debit.contents());
        var finalDebit = PortableEnergyMath.debit(debit.contents(), "fe"::equals, Long.MAX_VALUE);
        assertEquals(9, finalDebit.extracted());
        assertEquals(List.of(entry("iron", 42)), finalDebit.contents());
    }

    @Test
    void successiveCommittedDebitsNeverRecreatePreviouslyExtractedFe() {
        var contents = List.of(entry("fe", 1000), entry("iron", 42));
        var external = PortableEnergyMath.debit(contents, "fe"::equals, 320);
        var recharge = PortableEnergyMath.debit(external.contents(), "fe"::equals, 160);
        var terminal = PortableEnergyMath.debit(recharge.contents(), "fe"::equals, Long.MAX_VALUE);
        assertEquals(520, terminal.extracted());
        assertEquals(1000, external.extracted() + recharge.extracted() + terminal.extracted());
        assertEquals(List.of(entry("iron", 42)), terminal.contents());
    }

    private static PortableEnergyMath.StoredAmount<String> entry(String key, long amount) {
        return new PortableEnergyMath.StoredAmount<>(key, amount);
    }
}
