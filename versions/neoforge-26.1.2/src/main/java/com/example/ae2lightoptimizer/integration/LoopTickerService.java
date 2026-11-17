package com.example.ae2lightoptimizer.integration;

import com.example.ae2lightoptimizer.block.LoopTickerBlockEntity;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives every loaded loop ticker once per server tick. */
public final class LoopTickerService {
    private static final Set<LoopTickerBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private LoopTickerService() {
    }

    public static void register(LoopTickerBlockEntity ticker) {
        LOADED.add(ticker);
    }

    public static void unregister(LoopTickerBlockEntity ticker) {
        LOADED.remove(ticker);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (LOADED.isEmpty()) return;
        for (LoopTickerBlockEntity ticker : new java.util.ArrayList<>(LOADED)) {
            if (ticker.isRemoved()) {
                LOADED.remove(ticker);
                continue;
            }
            try {
                ticker.accelerate();
            } catch (RuntimeException ignored) {
                // A misbehaving target must never break the server tick.
            }
        }
    }
}
