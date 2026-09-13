package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Production jobs on real server ticks and native containers; separate finite fixture ledgers. */
final class ChannelFunctionAudit {
    private static final String ITEM = "minecraft:iron_ingot";
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    private static FactoryJob job;
    private static int phase, blockedTicks;
    private static final String PREFIX = "import SrcA,SrcB,Dst\n";

    static boolean tick(FactoryBlockEntity host, BlockPos a, BlockPos b, BlockPos dst) {
        try {
            var level = (ServerLevel) host.getLevel();
            switch (phase) {
                case 0 -> {
                    fill(level, a, 7); fill(level, b, 5); fill(level, dst, 0);
                    evidence.put("initialSupply", Map.of("A", 7, "B", 5, "Dst", 0));
                    job = create(host, "func send\n    put 3 "+ITEM+" into Dst\nend\nget 3 "+ITEM+" from SrcA\nchannel\n    get 3 "+ITEM+" from SrcB\n    send\ndone");
                    phase++;
                }
                case 1 -> {
                    job.tick(); require(job.finished() && job.error().isEmpty(), "function PUT finishes");
                    require(balance(level,a,b,dst,7,2,3), "function PUT selects caller B, never outer A");
                    record(host,a,b,dst,"functionPut");
                    job = create(host, "func load\n    get 3 "+ITEM+" from SrcA\nend\nchannel\n    load\nput 3 "+ITEM+" into Dst\ndone");
                    phase++;
                }
                case 2 -> {
                    job.tick(); require(job.finished() && job.error().isEmpty(), "function GET finishes");
                    require(balance(level,a,b,dst,7,2,3), "function GET cannot leak to outer PUT");
                    record(host,a,b,dst,"functionGet");
                    job = create(host, "func inner\n    put "+ITEM+" into Dst\nend\nfunc outer\n    inner\nend\nget must 1000000000000 "+ITEM+" from SrcA\nchannel\n    get must 5 "+ITEM+" from SrcB\n    outer\nput must 1 "+ITEM+" into Dst\ndone");
                    phase++;
                }
                case 3 -> {
                    job.tick(); var state = snapshot();
                    require(!job.finished() && job.error().isEmpty() && state.pendingTransfer()
                            && state.transferRemaining()==3 && state.returns().size()==2, "nested MUST owes exactly three in caller channel");
                    require(balance(level,a,b,dst,7,0,5), "nested function never consumes foreign trillion debt");
                    record(host,a,b,dst,"nestedPartial");
                    var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                    var saved = FactoryJob.Saved.CODEC.encodeStart(ops, job.save()).getOrThrow();
                    job = FactoryJob.restore(host, FactoryJob.Saved.CODEC.parse(ops,saved).getOrThrow());
                    record(host,a,b,dst,"nestedRestored"); phase++;
                }
                case 4 -> {
                    job.tick(); require(snapshot().transferRemaining()==3 && !job.finished()
                            && balance(level,a,b,dst,7,0,5), "restored nested call stays blocked without replay or foreign extraction");
                    if (++blockedTicks < 20) return false;
                    record(host,a,b,dst,"nestedBlocked");
                    evidence.put("blockedServerTicks",blockedTicks);
                    fill(level,b,3); evidence.put("explicitRefill",3); phase++;
                }
                case 5 -> {
                    job.tick(); require(job.finished() && job.error().isEmpty(), "nested function and outer tail finish");
                    require(balance(level,a,b,dst,6,0,9), "caller channel restored after return and exact conservation");
                    record(host,a,b,dst,"nestedCompleted");
                    job=create(host,"done");
                    require(job.transfer(true,ITEM,"SrcA","",3,9)==0, "GET only declares");
                    require(job.transfer(false,ITEM,"Dst","",3,0)==0, "direct GET API retains requested channel");
                    var other=create(host,"done");
                    require(other.transfer(false,ITEM,"Dst","",3,9)==0, "separate jobs never share routes");
                    require(job.transfer(false,ITEM,"Dst","",3,9)==3, "owning channel transfers exactly three");
                    require(balance(level,a,b,dst,3,0,12), "all fifteen supplied physical items conserved");
                    record(host,a,b,dst,"directApiAndSeparateJobs");
                    job = new FactoryJob(host,new FactoryPatternData("// native SFM slash comment\nNAME \"SFM // label\" // header\nEVERY 1 TICKS DO // trigger\nINPUT 3 iron_ingot FROM SrcA // get\nOUTPUT 3 iron_ingot TO Dst // put\nEND // done",host.factoryId(),ItemStack.EMPTY));
                    phase++; blockedTicks=0;
                }
                case 6 -> {
                    job.tick(); require(job.error().isEmpty(), "SFM inline comments compile and execute");
                    if (!balance(level,a,b,dst,0,0,15) && ++blockedTicks<20) return false;
                    require(balance(level,a,b,dst,0,0,15), "SFM comments conserve all fifteen supplied items");
                    record(host,a,b,dst,"sfmSlashComments");
                    evidence.put("status","passed"); write(); return true;
                }
                default -> throw new IllegalStateException("Unexpected phase");
            }
            return false;
        } catch (Throwable failure) {
            evidence.put("status","failed"); evidence.put("failure",failure.toString()); write();
            throw new IllegalStateException("Channel function isolation failed",failure);
        }
    }
    private static FactoryJob create(FactoryBlockEntity host,String code) {
        return new FactoryJob(host,new FactoryPatternData((PREFIX+code).replace("\n", " // inline native regression\n")+" // trailing",host.factoryId(),ItemStack.EMPTY));
    }
    private static FactoryMachine.Snapshot snapshot() {
        return new com.google.gson.Gson().fromJson(job.save().continuation(),FactoryMachine.Snapshot.class);
    }
    private static int count(ServerLevel level,BlockPos pos) {
        var c=(Container)level.getBlockEntity(pos); int n=0;
        for(int i=0;i<c.getContainerSize();i++) {var s=c.getItem(i); require(s.isEmpty()||s.is(Items.IRON_INGOT),"only fixture iron"); n+=s.getCount();}
        return n;
    }
    private static boolean balance(ServerLevel level,BlockPos a,BlockPos b,BlockPos dst,int av,int bv,int dv) {
        return count(level,a)==av && count(level,b)==bv && count(level,dst)==dv;
    }
    private static void fill(ServerLevel level,BlockPos pos,int n) {
        var c=(Container)level.getBlockEntity(pos); c.clearContent(); if(n>0)c.setItem(0,new ItemStack(Items.IRON_INGOT,n)); c.setChanged();
    }
    private static void require(boolean condition,String check) {if(!condition)throw new IllegalStateException(check);}
    private static void record(FactoryBlockEntity host,BlockPos a,BlockPos b,BlockPos dst,String name) {
        var level=(ServerLevel)host.getLevel();
        var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        evidence.put(name,Map.of("gameTime",level.getGameTime(),"A",count(level,a),"B",count(level,b),
                "Dst",count(level,dst),"savedJob",FactoryJob.Saved.CODEC.encodeStart(ops,job.save()).getOrThrow()));
    }
    private static void write() {
        try {Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("channel-function-report.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
        catch(Exception failure){throw new RuntimeException(failure);}
    }
}
