package com.example.ae2lightoptimizer.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChannelCapacityPolicyTest {
    @Test void doublesTheActualNativeCapacityIncludingConfiguredModes() {
        assertEquals(16,ChannelCapacityPolicy.effective(8,true));
        assertEquals(64,ChannelCapacityPolicy.effective(32,true));
        assertEquals(48,ChannelCapacityPolicy.effective(24,true));
        assertEquals(192,ChannelCapacityPolicy.effective(96,true));
    }
    @Test void detachedInfiniteAndForbiddenPathsKeepTheirNativeValues() {
        assertEquals(8,ChannelCapacityPolicy.effective(8,false));
        assertEquals(0,ChannelCapacityPolicy.effective(0,true));
        assertEquals(Integer.MAX_VALUE,ChannelCapacityPolicy.effective(Integer.MAX_VALUE,true));
    }
    @Test void capacityCannotWrapIntoNegativeNumbers() {
        assertEquals(Integer.MAX_VALUE,ChannelCapacityPolicy.effective(Integer.MAX_VALUE/2+1,true));
    }
}
