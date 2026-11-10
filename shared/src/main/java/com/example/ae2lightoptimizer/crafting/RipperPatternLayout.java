package com.example.ae2lightoptimizer.crafting;

/** Storage grows by rows; only a fixed viewport becomes Minecraft menu slots. */
public final class RipperPatternLayout {
    public static final int COLUMNS = 9;
    public static final int VISIBLE_ROWS = 4;
    public static final int VISIBLE_SLOTS = COLUMNS * VISIBLE_ROWS;

    public static int rowsAfter(int lastOccupiedSlot) {
        return lastOccupiedSlot < 0 ? 1 : Math.addExact(lastOccupiedSlot / COLUMNS, 2);
    }

    public static int slotsAfter(int lastOccupiedSlot) {
        return Math.multiplyExact(rowsAfter(lastOccupiedSlot), COLUMNS);
    }

    public static int clampRow(int requested, int rows) {
        return Math.max(0, Math.min(requested, Math.max(0, rows - VISIBLE_ROWS)));
    }

    public static int storageSlot(int firstRow, int visibleSlot) {
        if (firstRow < 0 || visibleSlot < 0 || visibleSlot >= VISIBLE_SLOTS)
            throw new IllegalArgumentException("Invalid pattern viewport coordinate");
        return Math.addExact(Math.multiplyExact(firstRow, COLUMNS), visibleSlot);
    }

    private RipperPatternLayout() {}
}
