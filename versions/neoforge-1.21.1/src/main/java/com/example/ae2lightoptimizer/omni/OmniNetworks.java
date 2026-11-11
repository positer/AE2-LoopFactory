package com.example.ae2lightoptimizer.omni;

import appeng.api.networking.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** One server-local native virtual node per named link; no chunk tickets or power dependency. */
public final class OmniNetworks {
    private static final Map<MinecraftServer, OmniNetworks> SERVERS = new WeakHashMap<>();
    private final MinecraftServer server;
    private final Map<String, Hub> hubs = new HashMap<>();
    private final Map<OmniConnectorBlockEntity, Hub> members = new IdentityHashMap<>();
    public static final class Hub implements OmniNetworkNode {
        private final IManagedGridNode node = GridHelper.createManagedNode(this, new IGridNodeListener<Hub>() {
            @Override public void onSaveChanges(Hub owner, IGridNode node) {}
        }).setFlags(GridFlags.DENSE_CAPACITY).setIdlePowerUsage(0).setInWorldNode(false)
                .setExposedOnSides(Set.of()).setVisualRepresentation(OmniContent.ITEM.get());
        private final Map<OmniConnectorBlockEntity, IGridConnection> connections = new IdentityHashMap<>();
    }
    private OmniNetworks(MinecraftServer server) { this.server = server; }
    public static OmniNetworks get(MinecraftServer server) { return SERVERS.computeIfAbsent(server, OmniNetworks::new); }
    public void attach(OmniConnectorBlockEntity endpoint) {
        detach(endpoint);
        if (endpoint.isRemoved() || endpoint.getMainNode().getNode() == null
                || OmniLinkSavedData.get(server).directory().get(endpoint.linkId()) == null) return;
        var hub = hubs.computeIfAbsent(endpoint.linkId(), id -> {
            var created = new Hub();
            created.node.create(server.overworld(), BlockPos.ZERO);
            return created;
        });
        hub.connections.put(endpoint, GridHelper.createConnection(hub.node.getNode(), endpoint.getMainNode().getNode()));
        members.put(endpoint, hub);
        endpoint.markForUpdate();
    }
    public void detach(OmniConnectorBlockEntity endpoint) {
        var hub = members.remove(endpoint);
        if (hub == null) return;
        var connection = hub.connections.remove(endpoint);
        if (connection != null) connection.destroy();
        if (hub.connections.isEmpty()) {
            hubs.values().removeIf(value -> value == hub);
            hub.node.destroy();
        }
    }
    public int count(String id) { var hub = hubs.get(id); return hub == null ? 0 : hub.connections.size(); }
    public boolean linked(OmniConnectorBlockEntity endpoint) { return members.containsKey(endpoint); }
    public IGridNode hubNode(String id) { var hub = hubs.get(id); return hub == null ? null : hub.node.getNode(); }
    public static void remove(OmniConnectorBlockEntity endpoint) {
        if (endpoint.getLevel() != null && endpoint.getLevel().getServer() != null) {
            var networks = SERVERS.get(endpoint.getLevel().getServer());
            if (networks != null) networks.detach(endpoint);
        }
    }
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        var networks = SERVERS.remove(event.getServer());
        if (networks == null) return;
        for (var endpoint : List.copyOf(networks.members.keySet())) networks.detach(endpoint);
    }
}
