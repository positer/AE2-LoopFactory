package com.example.ae2lightoptimizer.factory;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Elects before changing connections, including the attachment networks of disconnected duplicates. */
public final class UniqueNetworkServices {
    public enum Kind { FACTORY, RING, OPTIMIZER }
    private record Service(BlockEntity host,IManagedGridNode node,Kind kind,boolean provider) {}
    private static final Map<BlockEntity,Service> loaded=new IdentityHashMap<>();
    private static final Set<BlockEntity> disconnected=Collections.newSetFromMap(new IdentityHashMap<>());
    public static void add(BlockEntity host,IManagedGridNode node,Kind kind,boolean provider) {
        loaded.put(host,new Service(host,node,kind,provider));
        if(!provider){disconnected.add(host);node.setExposedOnSides(Set.of());}
    }
    public static void remove(BlockEntity host) {loaded.remove(host);disconnected.remove(host);}
    public static boolean disconnected(BlockEntity host) {return disconnected.contains(host);}
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if(server.getTickCount()%20!=0)return;
        var all=loaded.values().stream().filter(s->!s.host.isRemoved() && s.host.getLevel()!=null
                && s.host.getLevel().getServer()==server && s.node.isReady()).toList();
        var losers=Collections.newSetFromMap(new IdentityHashMap<BlockEntity,Boolean>());
        for(var kind:Kind.values()) {
            var parent=new IdentityHashMap<Object,Object>();
            var candidates=all.stream().filter(s->s.kind==kind).toList();
            for(var s:candidates) {
                union(parent,s,s.node.getGrid());
                // Providers claim only their real facing subnet; their main network is unrelated.
                if(s.provider)continue;
                for(var side:Direction.values()) {
                    var pos=s.host.getBlockPos().relative(side);
                    if(!s.host.getLevel().hasChunkAt(pos))continue;
                    if(s.host.getLevel().getBlockEntity(pos) instanceof IInWorldGridNodeHost neighbor) {
                        var node=neighbor.getGridNode(side.getOpposite());
                        if(node!=null)union(parent,s,node.getGrid());
                    }
                }
            }
            var winners=new IdentityHashMap<Object,Service>();
            var order=Comparator.<Service>comparingInt(s->s.provider?0:1)
                    .thenComparingLong(s->s.host.getBlockPos().asLong());
            for(var s:candidates) {
                Object component=root(parent,s);
                var previous=winners.get(component);
                if(previous==null||order.compare(s,previous)<0)winners.put(component,s);
            }
            for(var s:candidates)if(!s.provider && winners.get(root(parent,s))!=s)losers.add(s.host);
        }
        // Remove every losing connection first. A replacement never joins alongside an old winner.
        for(var s:all)if(losers.contains(s.host) && disconnected.add(s.host))s.node.setExposedOnSides(Set.of());
        for(var s:all)if(!losers.contains(s.host) && disconnected.remove(s.host))s.node.setExposedOnSides(EnumSet.allOf(Direction.class));
    }
    private static Object root(IdentityHashMap<Object,Object> parent,Object key) {
        Object value=parent.computeIfAbsent(key,k->k);
        while(value!=parent.get(value))value=parent.get(value);
        Object cursor=key;
        while(parent.get(cursor)!=value) {Object next=parent.get(cursor);parent.put(cursor,value);cursor=next;}
        return value;
    }
    private static void union(IdentityHashMap<Object,Object> parent,Object a,Object b) {
        if(b==null)return;
        Object left=root(parent,a),right=root(parent,b);
        if(left!=right)parent.put(left,right);
    }
    private UniqueNetworkServices() {}
}
