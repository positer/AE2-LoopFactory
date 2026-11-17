package com.example.ae2lightoptimizer.api;

import appeng.api.upgrades.IUpgradeInventory;

/** Common view of the loop ticker whether it is a block or a cable part. */
public interface LoopTickerHost {
    int MAX_CARDS = 4;

    int rate();

    int maxRate();

    void setRate(int rate);

    IUpgradeInventory getUpgrades();

    /** Persists a rate change on the host. */
    default void saveRate() {
    }
}
