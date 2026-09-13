package com.example.ae2lightoptimizer.factory;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryTagsTest {
    @Test void oneTagKeepsMultipleDistinctMembersAcrossReconcileAndRestore() {
        var tags=new FactoryTags();tags.reconcile(List.of("A","B"));
        for(long pos:List.of(1L,2L,3L))assertTrue(tags.tag("A",pos,p->true));
        assertFalse(tags.tag("A",2,p->true));assertTrue(tags.tag("B",2,p->true));
        tags=FactoryTags.restore(tags.snapshot());tags.reconcile(List.of("A","B","C"));
        assertEquals(java.util.Set.of(1L,2L,3L),tags.positions("A"));assertEquals(java.util.Set.of(2L),tags.positions("B"));
        assertTrue(tags.remove("A",2));assertEquals(java.util.Set.of(1L,3L),tags.positions("A"));assertEquals(java.util.Set.of(2L),tags.positions("B"));
    }
    @Test void editingImportsPreservesOnlyUnchangedTagBindings() {
        var tags = new FactoryTags();
        tags.reconcile(List.of("A", "B"));
        assertTrue(tags.tag("A", 12, position -> true));
        assertTrue(tags.tag("B", 34, position -> true));
        tags = FactoryTags.restore(tags.snapshot());
        tags.reconcile(List.of("A", "C"));
        assertEquals(java.util.Set.of(12L), tags.positions("A"));
        assertTrue(tags.positions("B").isEmpty());
        assertTrue(tags.positions("C").isEmpty());
        assertFalse(tags.tag("C", 90, position -> false));
        assertFalse(tags.tag("missing", 12, position -> true));
    }
}
