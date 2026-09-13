package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import appeng.api.config.Actionable;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import java.util.*;
import java.nio.file.*;

/** Real powered, isolated networks; each job advances at most once per actual server tick. */
public final class StressAudit {
    public static volatile boolean finished;
    public static volatile boolean passed;
    private static final int LANES=8;
    private static int cycles=512, configuredTickRate=20;
    private static long startedNanos;
    public static int timeoutTicks(){return Math.max(2400,cycles*3+600);}
    public static int option(String key,int fallback,int minimum,int maximum) {
        String raw=System.getProperty("ae2lf.probe."+key,Integer.toString(fallback));
        final int value;
        try {value=Integer.parseInt(raw);}catch(NumberFormatException error){throw new IllegalArgumentException(key+" must be an integer: "+raw,error);}
        if(value<minimum||value>maximum)throw new IllegalArgumentException(key+" must be in ["+minimum+", "+maximum+"], got "+value);
        return value;
    }
    /** Changes the native server scheduler only; recipes and jobs still receive real server ticks. */
    public static int configureTickRate(ServerLevel world) {
        int rate=option("tickRate",20,20,100);
        world.getServer().tickRateManager().setTickRate(rate);
        return rate;
    }
    private static final List<Lane> lanes=new ArrayList<>();
    private static ServerLevel level;
    private static int age, completed, restores;
    private static long elapsedNanos,maxNanos,transportNanos,codecNanos;
    private static boolean started;
    private static final class Lane {
        FactoryBlockEntity host;
        BarrelBlockEntity a,b;
        FactoryJob job;
        int cycle;
    }
    public static void start(ServerLevel world) {
        if(started)return;
        level=world;started=true;startedNanos=System.nanoTime();
        try {
            cycles=option("stressCycles",512,512,16384);
            configuredTickRate=configureTickRate(world);
            for(int i=0;i<LANES;i++) {
                var lane=new Lane();var pos=new BlockPos(32+i*5,100,32);
                level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
                level.setBlockAndUpdate(pos,FactoryContent.PROVIDER.get().defaultBlockState());
                lane.host=(FactoryBlockEntity)level.getBlockEntity(pos);
                Direction face=lane.host.getFront();
                var power=pos.relative(face.getOpposite());
                level.setBlockAndUpdate(power,AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
                ((EnergyCellBlockEntity)level.getBlockEntity(power)).injectAEPower(1_000_000,Actionable.MODULATE);
                var cable=pos.relative(face);
                level.setBlockAndUpdate(cable,FactoryContent.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(cable.above(),Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(cable.below(),Blocks.BARREL.defaultBlockState());
                lane.a=(BarrelBlockEntity)level.getBlockEntity(cable.above());
                lane.b=(BarrelBlockEntity)level.getBlockEntity(cable.below());
                lane.a.setItem(0,new ItemStack(Items.IRON_INGOT,64));
                lane.a.setItem(1,new ItemStack(Items.GOLD_INGOT,7));
                lanes.add(lane);
            }
        }catch(Throwable error){finish(error);}
    }
    public static void tick() {
        if(!started||finished)return;
        long begin=System.nanoTime();
        try {
            if(++age>timeoutTicks())throw new IllegalStateException("Stress audit timed out");
            for(var lane:lanes) {
                if(lane.cycle==cycles)continue;
                if(!lane.host.getMainNode().isActive()||lane.host.factoryGrid()==null)continue;
                if(lane.job==null) {
                    lane.host.tags.reconcile(List.of("A","B"));
                    lane.host.tags.tag("A",lane.a.getBlockPos().asLong(),p->FactoryServer.contains(lane.host.factoryGrid(),BlockPos.of(p)));
                    lane.host.tags.tag("B",lane.b.getBlockPos().asLong(),p->FactoryServer.contains(lane.host.factoryGrid(),BlockPos.of(p)));
                    int quantity=1+lane.cycle%64;
                    String code="import A,B\nget "+quantity+" minecraft::item!(minecraft:gold_ingot) from A\nput minecraft::item into B\nwait 1 tick\nget minecraft:iron_ingot from B\nput minecraft:iron_ingot into A\ndone";
                    lane.job=new FactoryJob(lane.host,new FactoryPatternData(code,lane.host.factoryId(),ItemStack.EMPTY));
                }
                long transportStart=System.nanoTime();lane.job.tick();transportNanos+=System.nanoTime()-transportStart;
                if(!lane.job.error().isEmpty())throw new IllegalStateException(lane.job.error());
                if(count(lane.a,Items.IRON_INGOT)+count(lane.b,Items.IRON_INGOT)!=64)throw new IllegalStateException("Iron conservation failed");
                if(count(lane.a,Items.GOLD_INGOT)!=7||count(lane.b,Items.GOLD_INGOT)!=0)throw new IllegalStateException("Excluded gold moved");
                long codecStart=System.nanoTime();
                var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                var encoded=FactoryJob.Saved.CODEC.encodeStart(ops,lane.job.save()).getOrThrow();
                lane.job=FactoryJob.restore(lane.host,FactoryJob.Saved.CODEC.parse(ops,com.google.gson.JsonParser.parseString(encoded.toString())).getOrThrow());
                codecNanos+=System.nanoTime()-codecStart;
                restores++;
                if(lane.job.finished()) {
                    if(count(lane.a,Items.IRON_INGOT)!=64||count(lane.b,Items.IRON_INGOT)!=0)throw new IllegalStateException("Round trip failed");
                    lane.job=null;lane.cycle++;completed++;
                }
            }
            if(completed==LANES*cycles)finish(null);
        }catch(Throwable error){finish(error);}
        finally {long spent=System.nanoTime()-begin;elapsedNanos+=spent;maxNanos=Math.max(maxNanos,spent);}
    }
    private static int count(BarrelBlockEntity inventory,net.minecraft.world.item.Item item) {
        int total=0;for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).is(item))total+=inventory.getItem(i).getCount();return total;
    }
    private static void finish(Throwable failure) {
        passed=failure==null;
        var report=new LinkedHashMap<String,Object>();
        report.put("status",passed?"passed":"failed");report.put("concurrentNetworks",LANES);
        report.put("cyclesPerNetwork",cycles);report.put("targetRoundTrips",LANES*cycles);
        report.put("targetJobCodecRestores",2*LANES*cycles);report.put("configuredTickRate",configuredTickRate);
        double wallSeconds=(System.nanoTime()-startedNanos)/1_000_000_000.0;
        report.put("wallClockSeconds",wallSeconds);report.put("observedAuditTicksPerSecond",age/Math.max(0.000001,wallSeconds));
        report.put("timeoutServerTicks",timeoutTicks());report.put("completedRoundTrips",completed);report.put("jobCodecRestores",restores);
        report.put("serverTicks",age);report.put("measuredTotalMillis",elapsedNanos/1_000_000.0);
        report.put("maxAuditTickMillis",maxNanos/1_000_000.0);
        report.put("transportJobMillis",transportNanos/1_000_000.0);report.put("codecRestoreMillis",codecNanos/1_000_000.0);
        report.put("scope","Actual world item capabilities; exclusion/conservation/independent networks/continuation pressure, not full process restart or all mod capabilities");
        if(failure!=null)report.put("failure",failure.toString());
        try {Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("stress-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception e){throw new RuntimeException(e);}
        finally {if(level!=null)level.getServer().tickRateManager().setTickRate(20);finished=true;}
    }
}
