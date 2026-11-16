package com.example.ae2lightoptimizer.omni;

import appeng.api.networking.IGrid;
import com.example.ae2lightoptimizer.factory.FactoryBlockEntity;
import java.util.Collections;
import java.util.HashSet;
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
 * Wireless Loop Factory membership. A formed structure joins the connector's channel to one factory
 * network and marks the connector's chunk as a member; factory code may then address machines in that
 * chunk. AE grids are never joined: this registry is only consulted by factory machine resolution.
 */
public final class OmniFactoryNetworks {
    private record Spot(ResourceKey<Level> dimension, long chunk) {}
    /** The factory machine is kept instead of a grid snapshot: an AE merge may replace the grid instance. */
    private record Member(String channel, Spot spot, FactoryBlockEntity factory) {}
    private static final Map<MinecraftServer, Map<String, Set<Member>>> SERVERS = new WeakHashMap<>();
    private static final Map<OmniConnectorBlockEntity, Member> ENDPOINTS = new IdentityHashMap<>();

    private OmniFactoryNetworks() {}

    /** Joins the channel's factory network. A provider contributes its factory subnet; its AE main network stays out. */
    public static void join(OmniConnectorBlockEntity endpoint, FactoryBlockEntity target) {
        var level = endpoint.getLevel();
        var server = level == null ? null : level.getServer();
        if (server == null || endpoint.linkId().isEmpty() || target == null) { leave(endpoint); return; }
        leave(endpoint);
        var grid = target.factoryGrid();
        if (grid == null) return;
        var pos = endpoint.getBlockPos();
        var spot = new Spot(level.dimension(), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        var member = new Member(endpoint.linkId(), spot, target);
        ENDPOINTS.put(endpoint, member);
        SERVERS.computeIfAbsent(server, ignored -> new java.util.HashMap<>())
                .computeIfAbsent(member.channel(), ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(member);
    }

    public static void leave(OmniConnectorBlockEntity endpoint) {
        var removed = ENDPOINTS.remove(endpoint);
        if (removed == null) return;
        var level = endpoint.getLevel();
        var server = level == null ? null : level.getServer();
        if (server == null) return;
        var groups = SERVERS.get(server);
        var members = groups == null ? null : groups.get(removed.channel());
        if (members != null) { members.remove(removed); if (members.isEmpty()) groups.remove(removed.channel()); }
    }

    public static boolean linked(OmniConnectorBlockEntity endpoint) { return ENDPOINTS.containsKey(endpoint); }
    /** Diagnostics for the acceptance probe. */
    public static int endpoints() { return ENDPOINTS.size(); }

    /** True when {@code pos} lies in a member chunk of a factory network that {@code grid} belongs to. */
    public static boolean reachable(IGrid grid, Level level, BlockPos pos) {
        if (grid == null || level == null) return false;
        var server = level.getServer();
        if (server == null) return false;
        var groups = SERVERS.get(server);
        if (groups == null) return false;
        var spot = new Spot(level.dimension(), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        for (var members : groups.values()) {
            boolean member = false;
            for (var entry : members) if (entry.spot().equals(spot)) { member = true; break; }
            if (!member) continue;
            for (var entry : members) {
                var factory = entry.factory();
                if (factory != null && !factory.isRemoved() && factory.factoryGrid() == grid) return true;
            }
        }
        return false;
    }

    public static void stopped(MinecraftServer server) { SERVERS.remove(server); ENDPOINTS.keySet().removeIf(e -> e.getLevel() == null || e.getLevel().getServer() == server); }
}
