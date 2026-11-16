package com.example.ae2lightoptimizer.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** A factory network hosts several patterns and terminals; tag names and bindings must survive all of them. */
class FactoryTagsSyncTest {
    @Test void savingAnotherPatternKeepsEarlierTagNames() {
        var tags = new FactoryTags();
        tags.reconcile(java.util.List.of("in"));
        assertTrue(tags.tag("in", 7L, ignored -> true));
        tags.reconcile(java.util.List.of("in", "out"));
        assertEquals(java.util.Set.of(7L), tags.positions("in"));
        assertTrue(tags.names().contains("out"));
    }

    @Test void reconcileNeverDropsABindingFromAnotherPattern() {
        var tags = new FactoryTags();
        tags.reconcile(java.util.List.of("a"));
        assertTrue(tags.tag("a", 11L, ignored -> true));
        tags.reconcile(java.util.List.of("b"));
        assertEquals(java.util.Set.of(11L), tags.positions("a"));
    }

    @Test void mergingTwoViewsOfOneNetworkKeepsEveryBinding() {
        var left = new FactoryTags();
        left.reconcile(java.util.List.of("in"));
        left.tag("in", 1L, ignored -> true);
        var right = new FactoryTags();
        right.reconcile(java.util.List.of("in", "out"));
        right.tag("in", 2L, ignored -> true);
        right.tag("out", 3L, ignored -> true);
        left.mergeFrom(right);
        var expected = java.util.Set.of(1L, 2L);
        assertEquals(expected, left.positions("in"));
        assertEquals(java.util.Set.of(3L), left.positions("out"));
    }
}
