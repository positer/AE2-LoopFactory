package com.example.ae2lfprobe;

import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.config.Actionable;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.factory.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.nio.file.*;
import java.util.*;

public final class TopologyAudit {
    public static volatile boolean finished,passed;
    private record Pair(Block block,BlockPos a,BlockPos b) {}
    private static final List<Pair> pairs=new ArrayList<>();
    private static ServerLevel level;
    private static int ticks;
    private static FactoryBlockEntity provider,subTerminal;
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    public static void start(ServerLevel world) {
        level=world;
        try {
            Block[] blocks={FactoryContent.TERMINAL.get(),ModBlocks.RECIPE_RING_SOLVER_TERMINAL.get(),ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get()};
            for(int i=0;i<blocks.length;i++) {
                var a=new BlockPos(96+i*8,100,32);var b=a.east(2);
                level.setChunkForced(a.getX()>>4,a.getZ()>>4,true);
                level.setBlockAndUpdate(a,blocks[i].defaultBlockState());level.setBlockAndUpdate(b,blocks[i].defaultBlockState());
                level.setBlockAndUpdate(a.east(),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
                ((EnergyCellBlockEntity)level.getBlockEntity(a.east())).injectAEPower(1_000_000,Actionable.MODULATE);
                pairs.add(new Pair(blocks[i],a,b));
            }
            var p=new BlockPos(120,100,32);
            level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);
            level.setBlockAndUpdate(p,FactoryContent.PROVIDER.get().defaultBlockState());provider=(FactoryBlockEntity)level.getBlockEntity(p);
            var cable=p.relative(provider.getFront());
            level.setBlockAndUpdate(cable,FactoryContent.CABLE.get().defaultBlockState());
            level.setBlockAndUpdate(cable.relative(provider.getFront()),FactoryContent.TERMINAL.get().defaultBlockState());
            subTerminal=(FactoryBlockEntity)level.getBlockEntity(cable.relative(provider.getFront()));
        }catch(Throwable error){finish(error);}
    }
    public static void tick() {
        if(level==null||finished)return;
        try {
            ++ticks;
            if(ticks==100) {
                for(var pair:pairs) {
                    var a=level.getBlockEntity(pair.a);var b=level.getBlockEntity(pair.b);
                    check(!UniqueNetworkServices.disconnected(a)&&connected(a),pair.block+":winner_connected");
                    check(UniqueNetworkServices.disconnected(b)&&!connected(b),pair.block+":duplicate_physically_disconnected");
                    level.setBlockAndUpdate(pair.a,Blocks.AIR.defaultBlockState());
                }
                check(UniqueNetworkServices.disconnected(subTerminal)&&!connected(subTerminal),"provider_subnet_takes_priority");
                level.setBlockAndUpdate(provider.getBlockPos(),Blocks.AIR.defaultBlockState());
            }
            if(ticks==200) {
                for(var pair:pairs) {
                    var b=level.getBlockEntity(pair.b);
                    check(!UniqueNetworkServices.disconnected(b)&&connected(b),pair.block+":replacement_recovers");
                    level.setBlockAndUpdate(pair.a,pair.block.defaultBlockState());
                }
                check(!UniqueNetworkServices.disconnected(subTerminal)&&connected(subTerminal),"subnet_terminal_recovers_after_provider_removal");
            }
            if(ticks==300) {
                for(var pair:pairs) {
                    check(connected(level.getBlockEntity(pair.a))&&!connected(level.getBlockEntity(pair.b)),pair.block+":deterministic_reelection");
                }
                finish(null);
            }
        }catch(Throwable error){finish(error);}
    }
    private static boolean connected(BlockEntity block) {
        if(!(block instanceof IInWorldGridNodeHost host))return false;
        for(var side:Direction.values()) {var node=host.getGridNode(side);if(node!=null&&!node.getConnections().isEmpty())return true;}
        return false;
    }
    private static void check(boolean ok,String key) {evidence.put(key,ok);if(!ok)throw new IllegalStateException(key);}
    private static void finish(Throwable error) {
        passed=error==null;evidence.put("status",passed?"passed":"failed");evidence.put("ticks",ticks);
        if(error!=null)evidence.put("failure",error.toString());
        try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("topology-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
        catch(Exception e){throw new RuntimeException(e);}finally{finished=true;}
    }
}
