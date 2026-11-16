package com.example.ae2lightoptimizer.omni;

import appeng.api.networking.IGrid;
import com.example.ae2lightoptimizer.factory.FactoryBlock;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Wireless Loop Factory membership. The structure alone joins a connector's chunk to the machine's
 * factory network; the selected channel is only the AE transport. A chunk may belong to several
 * networks at once, and a network is only reachable through the factory network that registered it.
 */
public final class OmniFactoryNetworks {
    private record Spot(ResourceKey<Level> dimension, long chunk) {}
    private record Member(Spot spot, FactoryBlockEntity factory) {}
    private static final Map<MinecraftServer, Set<Member>> SERVERS = new WeakHashMap<>();
    private static final Map<OmniConnectorBlockEntity, Member> ENDPOINTS = new IdentityHashMap<>();

    private OmniFactoryNetworks() {}

    public static void join(OmniConnectorBlockEntity endpoint, FactoryBlockEntity target) {
        var level = endpoint.getLevel();
        var server = level == null ? null : level.getServer();
        if (server == null || target == null || target.factoryGrid() == null) { leave(endpoint); return; }
        leave(endpoint);
        var pos = endpoint.getBlockPos();
        var spot = new Spot(level.dimension(), ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        var member = new Member(spot, target);
        ENDPOINTS.put(endpoint, member);
        SERVERS.computeIfAbsent(server, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(member);
    }

    public static void leave(OmniConnectorBlockEntity endpoint) {
        var removed = ENDPOINTS.remove(endpoint);
        if (removed == null) return;
        var level = endpoint.getLevel();
        var server = level == null ? null : level.getServer();
        if (server == null) return;
        var members = SERVERS.get(server);
        if (members != null) { members.remove(removed); if (members.isEmpty()) SERVERS.remove(server); }
    }

    public static boolean linked(OmniConnectorBlockEntity endpoint) { return ENDPOINTS.containsKey(endpoint); }
    /** Diagnostics for the acceptance probe. */
    public static int endpoints() { return ENDPOINTS.size(); }

    /** True when {@code pos} lies in a member chunk of the factory network that {@code grid} belongs to. */
    public static boolean reachable(IGrid grid, Level level, BlockPos pos) {
        if (grid == null || level == null) return false;
        // Blacklist, not a whitelist: every other block stays unconditionally reachable so machines from
        // any mod keep working. The factory network terminal and the factory pattern provider are the
        // mutually exclusive members, so two of them sharing a chunk are never linked without a cable.
        if (level.getBlockEntity(pos) instanceof FactoryBlockEntity factory
                && (factory.kind() == FactoryBlock.Kind.TERMINAL || factory.kind() == FactoryBlock.Kind.PROVIDER)) return false;
        var server = level.getServer();
        if (server == null) return false;
        var members = SERVERS.get(server);
        if (members == null) return false;
        var spot = new Spot(level.dimension(), ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        for (var entry : members) {
            var factory = entry.factory();
            if (factory != null && !factory.isRemoved() && entry.spot().equals(spot) && factory.factoryGrid() == grid) return true;
        }
        return false;
    }

    public static void stopped(MinecraftServer server) { SERVERS.remove(server); ENDPOINTS.keySet().removeIf(e -> e.getLevel() == null || e.getLevel().getServer() == server); }
}
