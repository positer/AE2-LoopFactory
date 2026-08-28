package com.example.ae2lightoptimizer.crafting;

import java.util.List;

/** Mutable cursor over run-length-compressed crafting batches. */
public final class CompressedBatchCursor {
    private final List<Long> batchSizes;
    private int batchIndex;
    private long remainingInBatch;

    public CompressedBatchCursor(List<Long> batchSizes) {
        this(batchSizes, 0, batchSizes.isEmpty() ? 0 : batchSizes.getFirst());
    }

    public CompressedBatchCursor(List<Long> batchSizes, int batchIndex, long remainingInBatch) {
        this.batchSizes = List.copyOf(batchSizes);
        if (this.batchSizes.stream().anyMatch(size -> size == null || size <= 0)) {
            throw new IllegalArgumentException("Batch sizes must be positive");
        }
        if (batchIndex < 0 || batchIndex > this.batchSizes.size()) {
            throw new IllegalArgumentException("Invalid batch index");
        }
        if (batchIndex == this.batchSizes.size()) {
            if (remainingInBatch != 0) {
                throw new IllegalArgumentException("Completed cursor cannot retain work");
            }
        } else if (remainingInBatch <= 0 || remainingInBatch > this.batchSizes.get(batchIndex)) {
            throw new IllegalArgumentException("Invalid remaining batch work");
        }
        this.batchIndex = batchIndex;
        this.remainingInBatch = remainingInBatch;
    }

    public int limit(int requestedOperations) {
        if (requestedOperations <= 0 || complete()) {
            return 0;
        }
        return (int) Math.min((long) requestedOperations, remainingInBatch);
    }

    public void advance(long completedOperations) {
        if (completedOperations < 0 || completedOperations > remainingInBatch) {
            throw new IllegalArgumentException("Completed work crosses a batch boundary");
        }
        remainingInBatch -= completedOperations;
        if (remainingInBatch == 0 && batchIndex < batchSizes.size()) {
            batchIndex++;
            remainingInBatch = complete() ? 0 : batchSizes.get(batchIndex);
        }
    }

    public int batchIndex() {
        return batchIndex;
    }

    public long remainingInBatch() {
        return remainingInBatch;
    }

    public boolean complete() {
        return batchIndex == batchSizes.size();
    }
}
