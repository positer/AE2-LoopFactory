package com.example.ae2lightoptimizer.factory;

import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import appeng.api.networking.IGrid;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class FactoryServer {
    private static final Set<FactoryBlockEntity> LOADED = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    public static void add(FactoryBlockEntity host) { LOADED.add(host); }
    public static void remove(FactoryBlockEntity host) { LOADED.remove(host); }
    public static boolean powered(net.minecraft.world.level.SignalGetter level, net.minecraft.core.BlockPos pos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel)) return false;
        for (var host : LOADED) if (!host.isRemoved() && host.getLevel() == level && host.powers(pos)) return true;
        return false;
    }
    /**
     * Redstone is a property of the factory network, not of a single block: a signal fed into the
     * interface cable must run the terminal's round just like a signal on the terminal body itself.
     */
    public static boolean signal(FactoryBlockEntity terminal) {
        var level = terminal.getLevel();
        if (level == null) return false;
        if (level.hasNeighborSignal(terminal.getBlockPos())) return true;
        // Only the terminal treats its whole network as one redstone input. Provider jobs keep the
        // exact block-level redstone semantics they had before, so dispatch and subnets are untouched.
        if (terminal.kind() != FactoryBlock.Kind.TERMINAL) return false;
        var grid = terminal.factoryGrid();
        if (grid == null) return false;
        for (var host : LOADED) {
            if (host == terminal || host.isRemoved() || host.getLevel() != level) continue;
            if (host.kind() != FactoryBlock.Kind.CABLE || !host.isolated() || host.factoryGrid() != grid) continue;
            if (level.hasNeighborSignal(host.getBlockPos())) return true;
        }
        return false;
    }
    public static void tick(ServerTickEvent.Post event) {
        UniqueNetworkServices.tick(event.getServer());
        if(event.getServer().getTickCount()%10==0)for(var player:event.getServer().getPlayerList().getPlayers())
            for(var hand:net.minecraft.world.InteractionHand.values()) {
                var stack=player.getItemInHand(hand);
                if(stack.getItem() instanceof FactoryEncoderItem)FactoryEncoderItem.refresh(player,stack);
            }
        for (var host : List.copyOf(LOADED)) if (!host.isRemoved() && host.getLevel() != null
                && host.getLevel().getServer() == event.getServer() && ticksInWorld(host)) host.tickFactory();
    }
    /** A demoted FULL chunk must not renew its own loading ticket through job saveChanges. */
    private static boolean ticksInWorld(FactoryBlockEntity host) {
        if (!(host.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        var pos = host.getBlockPos();
        long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        // Reuse the native block-ticking range/future check, without requesting a chunk load.
        return level.tickRateManager().runsNormally() && level.getChunkSource().isPositionTicking(chunk)
                && level.areEntitiesLoaded(chunk) && level.getWorldBorder().isWithinBounds(pos);
    }
    public static FactoryBlockEntity owner(IGrid grid) {
        if (grid == null) return null;
        return LOADED.stream().filter(host -> !host.isRemoved() && host.kind() != FactoryBlock.Kind.CABLE
                && host.factoryGrid() == grid && host.isolated() && !UniqueNetworkServices.disconnected(host))
                .min(java.util.Comparator.<FactoryBlockEntity>comparingInt(host -> host.isProvider() ? 0 : 1)
                        .thenComparingLong(host -> host.getBlockPos().asLong())).orElse(null);
    }
    public static java.util.List<FactoryBlockEntity> providers(IGrid grid) {
        if(grid==null)return java.util.List.of();
        return LOADED.stream().filter(host -> !host.isRemoved() && host.isProvider()
            && host.getMainNode().getGrid()==grid && host.isolated() && owner(host.factoryGrid())==host)
            .sorted(java.util.Comparator.comparingLong(host -> host.getBlockPos().asLong())).toList();
    }
    public static FactoryBlockEntity find(net.minecraft.world.level.Level level, String id) {
        if (id.isEmpty()) return null;
        return LOADED.stream().filter(host -> !host.isRemoved() && host.getLevel() == level
                && host.factoryId().equals(id) && owner(host.factoryGrid()) == host).findFirst().orElse(null);
    }
    public static FactoryBlockEntity at(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FactoryBlockEntity host && host.isProvider()) return owner(host.factoryGrid());
        return LOADED.stream().filter(host -> !host.isRemoved() && host.getLevel() == level
                && owner(host.factoryGrid()) == host && contains(host.factoryGrid(), pos))
                .min(java.util.Comparator.comparingLong(host -> host.getBlockPos().asLong())).orElse(null);
    }
    public static boolean contains(IGrid grid, net.minecraft.core.BlockPos pos) {
        return contains(grid,pos,owner(grid));
    }
    private static boolean contains(IGrid grid,net.minecraft.core.BlockPos pos,FactoryBlockEntity factoryOwner) {
        if (factoryOwner == null) return false;
        for (var node : grid.getNodes()) {
            Object owner = node.getOwner();
            net.minecraft.world.level.block.entity.BlockEntity block = owner instanceof net.minecraft.world.level.block.entity.BlockEntity be ? be
                    : owner instanceof appeng.parts.AEBasePart part ? part.getBlockEntity() : null;
            if (block == null || block.isRemoved()) continue;
            if (block.getLevel() != factoryOwner.getLevel()) continue;
            if (block.getBlockPos().equals(pos)) return true;
            if (block instanceof FactoryBlockEntity factory && factory.kind() == FactoryBlock.Kind.CABLE
                    && block.getBlockPos().distManhattan(pos) == 1 && block.getLevel().hasChunkAt(pos)
                    && !block.getLevel().getBlockState(pos).isAir()) return true;
        }
        return false;
    }
    /** Resolve one tag against one live grid traversal; never retain world membership across operations. */
    public static java.util.List<net.minecraft.core.BlockPos> members(IGrid grid, java.util.Set<Long> positions) {
        if(grid==null||positions.isEmpty())return java.util.List.of();
        var factoryOwner=owner(grid);
        if(factoryOwner==null)return java.util.List.of();
        var level=factoryOwner.getLevel();
        // Single-machine tags need no batch set or grid-neighbor enumeration.
        if(positions.size()==1) {
            var pos=net.minecraft.core.BlockPos.of(positions.iterator().next());
            return level.hasChunkAt(pos)&&contains(grid,pos,factoryOwner)?java.util.List.of(pos):java.util.List.of();
        }
        var found=new java.util.HashSet<Long>();
        for(var node:grid.getNodes()) {
            Object nodeOwner=node.getOwner();
            net.minecraft.world.level.block.entity.BlockEntity block=nodeOwner instanceof net.minecraft.world.level.block.entity.BlockEntity be?be
                :nodeOwner instanceof appeng.parts.AEBasePart part?part.getBlockEntity():null;
            if(block==null||block.isRemoved()||block.getLevel()!=level)continue;
            long packed=block.getBlockPos().asLong();
            if(positions.contains(packed)&&level.hasChunkAt(block.getBlockPos()))found.add(packed);
            if(block instanceof FactoryBlockEntity factory&&factory.kind()==FactoryBlock.Kind.CABLE) {
                for(var face:net.minecraft.core.Direction.values()) {
                    var neighbor=block.getBlockPos().relative(face);long candidate=neighbor.asLong();
                    if(positions.contains(candidate)&&level.hasChunkAt(neighbor)&&!level.getBlockState(neighbor).isAir())found.add(candidate);
                }
            }
            if(found.size()==positions.size())break;
        }
        return found.stream().sorted().map(net.minecraft.core.BlockPos::of).toList();
    }
    private FactoryServer() {}
}
