package com.example.ae2lightoptimizer.integration;

import com.example.ae2lightoptimizer.part.LoopTickerPart;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives every loaded loop ticker part once per server tick. */
public final class LoopTickerPartService {
    private static final Set<LoopTickerPart> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private LoopTickerPartService() {
    }

    public static void register(LoopTickerPart part) {
        LOADED.add(part);
    }

    public static void unregister(LoopTickerPart part) {
        LOADED.remove(part);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (LOADED.isEmpty()) return;
        for (LoopTickerPart part : new java.util.ArrayList<>(LOADED)) {
            try {
                part.serverTick();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
