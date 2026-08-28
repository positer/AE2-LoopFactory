package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompressedBatchCursorTest {
    @Test
    void parallelJobsAdvanceWithoutSharingCursorState() {
        var ringJob = new CompressedBatchCursor(List.of(1_000L), 0, 1_000L);
        var optimizerJob = new CompressedBatchCursor(List.of(2_000L), 0, 2_000L);

        ringJob.advance(1);
        optimizerJob.advance(512);

        assertEquals(999L, ringJob.remainingInBatch());
        assertEquals(1_488L, optimizerJob.remainingInBatch());
        ringJob.advance(999);
        assertTrue(ringJob.complete());
        assertFalse(optimizerJob.complete());
    }

    @Test
    void enforcesAlternatingCycleBatchesWithoutExpandingThem() {
        var cursor = new CompressedBatchCursor(List.of(1L, 2L, 1L, 4L));

        assertEquals(1, cursor.limit(Integer.MAX_VALUE));
        cursor.advance(1);
        assertEquals(1, cursor.batchIndex());
        assertEquals(2, cursor.limit(Integer.MAX_VALUE));
        cursor.advance(0);
        assertEquals(1, cursor.batchIndex());
        assertEquals(2, cursor.remainingInBatch());
        cursor.advance(2);
        assertEquals(2, cursor.batchIndex());
        assertFalse(cursor.complete());
    }

    @Test
    void keepsPetaScaleBatchCompressedAndRestoresItsPosition() {
        long peta = 1_000_000_000_000_000L;
        var cursor = new CompressedBatchCursor(List.of(peta, 3L));

        assertEquals(Integer.MAX_VALUE, cursor.limit(Integer.MAX_VALUE));
        cursor.advance(Integer.MAX_VALUE);
        var restored = new CompressedBatchCursor(
                List.of(peta, 3L), cursor.batchIndex(), cursor.remainingInBatch());

        assertEquals(peta - Integer.MAX_VALUE, restored.remainingInBatch());
        restored.advance(peta - Integer.MAX_VALUE);
        restored.advance(3);
        assertTrue(restored.complete());
        assertEquals(0, restored.limit(10));
    }
}
