package com.example.ae2loprobe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.GenerationChunkHolder;

/** Test-only observation of the pinned 1.21.1 native chunk lifecycle; never mutates native state. */
final class ShutdownReadiness {
    private static final Map<String, Field> FIELDS = new java.util.HashMap<>();

    record Observation(boolean ready, int tick, Map<String, Object> data) {
        void write(Path directory, String fileName) throws java.io.IOException {
            Files.writeString(directory.resolve(fileName),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
        }
    }

    private ShutdownReadiness() {}

    static Observation capture(MinecraftServer server, String phase) throws ReflectiveOperationException {
        if (!server.isSameThread()) throw new IllegalStateException("Chunk readiness must be sampled on the server thread");
        var dimensions = new ArrayList<Map<String, Object>>();
        int holders = 0;
        int unreadyHolders = 0;
        long generationReferences = 0;
        int saveFutureFailures = 0;
        int activeGenerationTasks = 0;
        int pendingGenerationTasks = 0;
        int pendingUnloads = 0;
        int queuedUnloads = 0;
        int toDrop = 0;
        boolean backgroundWork = false;
        for (var level : server.getAllLevels()) {
            var chunks = level.getChunkSource();
            var map = chunks.chunkMap;
            var updating = (Map<?, ?>) field(ChunkMap.class, "updatingChunkMap").get(map);
            var visible = (Map<?, ?>) field(ChunkMap.class, "visibleChunkMap").get(map);
            var pending = (Map<?, ?>) field(ChunkMap.class, "pendingUnloads").get(map);
            var generation = (Collection<?>) field(ChunkMap.class, "pendingGenerationTasks").get(map);
            var unloadQueue = (Collection<?>) field(ChunkMap.class, "unloadQueue").get(map);
            var drop = (Collection<?>) field(ChunkMap.class, "toDrop").get(map);
            var sorter = (ChunkTaskPriorityQueueSorter) field(ChunkMap.class, "queueSorter").get(map);
            boolean sorterHasWork = sorter.hasWork();
            boolean lightHasWork = chunks.getLightEngine().hasLightWork();
            var byIdentity = new IdentityHashMap<ChunkHolder, ArrayList<String>>();
            include(byIdentity, updating, "updating");
            include(byIdentity, visible, "visible");
            include(byIdentity, pending, "pendingUnload");
            var rows = new ArrayList<Map<String, Object>>();
            for (var entry : byIdentity.entrySet()) {
                var holder = entry.getKey();
                var save = holder.getSaveSyncFuture();
                int references = holder.getGenerationRefCount();
                boolean saveFailed = save.isCompletedExceptionally() || save.isCancelled();
                boolean nativeReady = holder.isReadyForSaving();
                Object task = ((AtomicReference<?>) field(GenerationChunkHolder.class, "task").get(holder)).get();
                var row = new LinkedHashMap<String, Object>();
                row.put("chunkX", holder.getPos().x);
                row.put("chunkZ", holder.getPos().z);
                row.put("holderIdentity", Integer.toHexString(System.identityHashCode(holder)));
                row.put("memberships", entry.getValue());
                row.put("ticketLevel", holder.getTicketLevel());
                row.put("generationRefCount", references);
                row.put("saveFutureDone", save.isDone());
                row.put("saveFutureFailed", saveFailed);
                row.put("saveFutureIdentity", Integer.toHexString(System.identityHashCode(save)));
                row.put("nativeReadyForSaving", nativeReady);
                row.put("generationTaskPresent", task != null);
                row.put("generationTaskIdentity", task == null ? "" : Integer.toHexString(System.identityHashCode(task)));
                rows.add(row);
                holders++;
                generationReferences += references;
                if (references != 0 || !save.isDone() || saveFailed || !nativeReady) unreadyHolders++;
                if (saveFailed) saveFutureFailures++;
                if (task != null) activeGenerationTasks++;
            }
            rows.sort(java.util.Comparator.<Map<String, Object>>comparingInt(row -> (Integer) row.get("chunkX"))
                    .thenComparingInt(row -> (Integer) row.get("chunkZ"))
                    .thenComparing(row -> (String) row.get("holderIdentity")));
            var dimension = new LinkedHashMap<String, Object>();
            dimension.put("dimension", level.dimension().location().toString());
            dimension.put("updatingHolderCount", updating.size());
            dimension.put("visibleHolderCount", visible.size());
            dimension.put("pendingUnloadCount", pending.size());
            dimension.put("pendingGenerationTaskCount", generation.size());
            dimension.put("unloadQueueSize", unloadQueue.size());
            dimension.put("toDropCount", drop.size());
            dimension.put("sorterHasWork", sorterHasWork);
            dimension.put("lightHasWork", lightHasWork);
            dimension.put("holders", rows);
            dimensions.add(dimension);
            pendingGenerationTasks += generation.size();
            pendingUnloads += pending.size();
            queuedUnloads += unloadQueue.size();
            toDrop += drop.size();
            backgroundWork |= sorterHasWork || lightHasWork;
        }
        // Loaded holders and ordinary tickets are allowed. Only unfinished lifecycle work blocks.
        boolean ready = holders > 0 && unreadyHolders == 0 && activeGenerationTasks == 0
                && pendingGenerationTasks == 0 && pendingUnloads == 0 && queuedUnloads == 0
                && toDrop == 0 && !backgroundWork;
        var result = new LinkedHashMap<String, Object>();
        result.put("observedAt", java.time.Instant.now().toString());
        result.put("world", server.getWorldData().getLevelName());
        result.put("serverTick", server.getTickCount());
        result.put("phase", phase);
        result.put("ready", ready);
        result.put("holderCount", holders);
        result.put("unreadyHolderCount", unreadyHolders);
        result.put("generationReferences", generationReferences);
        result.put("saveFutureFailures", saveFutureFailures);
        result.put("activeGenerationTasks", activeGenerationTasks);
        result.put("pendingGenerationTasks", pendingGenerationTasks);
        result.put("pendingUnloads", pendingUnloads);
        result.put("queuedUnloads", queuedUnloads);
        result.put("toDrop", toDrop);
        result.put("backgroundWork", backgroundWork);
        result.put("dimensions", dimensions);
        return new Observation(ready, server.getTickCount(), result);
    }

    private static void include(IdentityHashMap<ChunkHolder, ArrayList<String>> holders,
            Map<?, ?> source, String membership) {
        for (Object value : source.values()) {
            if (!(value instanceof ChunkHolder holder)) throw new IllegalStateException("Unexpected native holder type");
            holders.computeIfAbsent(holder, ignored -> new ArrayList<>()).add(membership);
        }
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        String key = owner.getName() + "." + name;
        Field result = FIELDS.get(key);
        if (result == null) {
            result = owner.getDeclaredField(name);
            result.setAccessible(true);
            FIELDS.put(key, result);
        }
        return result;
    }
}
