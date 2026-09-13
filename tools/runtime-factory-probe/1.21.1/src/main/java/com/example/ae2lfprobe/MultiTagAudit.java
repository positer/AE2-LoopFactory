package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import appeng.api.config.Actionable;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import java.util.*;
import java.nio.file.*;

/** Native machine groups share one instruction budget; no test inventory replaces a world capability. */
public final class MultiTagAudit {
    public static volatile boolean finished,passed;
    private static final BlockPos POS=new BlockPos(208,100,48);
    private static ServerLevel level;private static FactoryBlockEntity host;
    private static final List<BarrelBlockEntity> machines=new ArrayList<>();
    private static FactoryJob job;private static int ticks,cycles,mustTicks;private static boolean mustPhase;
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    public static void start(ServerLevel world) {
        level=world;
        try {
            // Later native CPU/induction fixtures still own these two original chunks.
            level.setChunkForced(0,0,true);level.setChunkForced(0,-1,true);
            level.setChunkForced(POS.getX()>>4,POS.getZ()>>4,true);
            level.setBlockAndUpdate(POS,FactoryContent.TERMINAL.get().defaultBlockState());host=(FactoryBlockEntity)level.getBlockEntity(POS);
            level.setBlockAndUpdate(POS.west(),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
            ((EnergyCellBlockEntity)level.getBlockEntity(POS.west())).injectAEPower(1_000_000,Actionable.MODULATE);
            for(int i=0;i<7;i++) {
                var cable=POS.east(i+1);level.setBlockAndUpdate(cable,FactoryContent.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(cable.above(),Blocks.BARREL.defaultBlockState());
                var barrel=(BarrelBlockEntity)level.getBlockEntity(cable.above());machines.add(barrel);
                if(i<3){barrel.setItem(0,new ItemStack(Items.IRON_INGOT,new int[]{10,20,34}[i]));barrel.setItem(1,new ItemStack(Items.GOLD_INGOT,7+i));fill(barrel,2);}
                if(i==3)fill(barrel,0);
                if(i==4){barrel.setItem(0,new ItemStack(Items.IRON_INGOT,60));fill(barrel,1);}
                if(i==5)fill(barrel,1);
            }
        }catch(Throwable error){finish(error);}
    }
    private static void fill(BarrelBlockEntity barrel,int start){for(int slot=start;slot<barrel.getContainerSize();slot++)barrel.setItem(slot,new ItemStack(Items.COBBLESTONE,64));}
    private static void bind(String name,int... indexes){for(int i:indexes)check(host.tags.tag(name,machines.get(i).getBlockPos().asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))),"bind_"+name+"_"+i);}
    private static long count(int i,Item item){long n=0;var inv=machines.get(i);for(int s=0;s<inv.getContainerSize();s++)if(inv.getItem(s).is(item))n+=inv.getItem(s).getCount();return n;}
    private static long group(String name){return new FactoryJob(host,new FactoryPatternData("done",host.factoryId(),ItemStack.EMPTY)).count(name,"minecraft:iron_ingot");}
    private static void run(String code){job=new FactoryJob(host,new FactoryPatternData(code,host.factoryId(),ItemStack.EMPTY));}
    private static void membershipCheck() {
        var candidates=new java.util.HashSet<Long>();
        for(var machine:machines)candidates.add(machine.getBlockPos().asLong());
        candidates.add(POS.asLong());candidates.add(POS.west().asLong());candidates.add(POS.above(5).asLong());
        var expected=candidates.stream().map(BlockPos::of).filter(p->level.hasChunkAt(p)&&FactoryServer.contains(host.factoryGrid(),p)).sorted(java.util.Comparator.comparingLong(BlockPos::asLong)).toList();
        check(FactoryServer.members(host.factoryGrid(),candidates).equals(expected),"batch_membership_matches_live_single_position_checks");
        for(long candidate:candidates) {
            var pos=BlockPos.of(candidate);
            check(FactoryServer.members(host.factoryGrid(),java.util.Set.of(candidate)).contains(pos)==(level.hasChunkAt(pos)&&FactoryServer.contains(host.factoryGrid(),pos)),"single_member_fast_path_matches_live_membership");
        }
    }
    private static void membershipBenchmark() {
        var candidates=new java.util.HashSet<Long>();
        for(var machine:machines)candidates.add(machine.getBlockPos().asLong());
        // Saved stale labels, not a claim of 512 extra physical machines.
        for(int i=0;i<512;i++)candidates.add(POS.offset(i%32,5,i/32).asLong());
        long[] original=new long[12],batch=new long[12];
        for(int run=-4;run<12;run++) {
            boolean reverse=(run&1)==0;
            for(int pass=0;pass<2;pass++) {
                boolean batched=(pass==0)==reverse;long begin=System.nanoTime();long size;
                if(batched)size=FactoryServer.members(host.factoryGrid(),candidates).size();
                else size=candidates.stream().map(BlockPos::of).filter(p->level.hasChunkAt(p)&&FactoryServer.contains(host.factoryGrid(),p)).count();
                long nanos=System.nanoTime()-begin;check(size==7,"membership_benchmark_same_seven_world_machines");
                if(run>=0)(batched?batch:original)[run]=nanos;
            }
        }
        java.util.Arrays.sort(original);java.util.Arrays.sort(batch);
        evidence.put("membershipCandidates",candidates.size());
        evidence.put("membershipOriginalMedianMicros",original[6]/1000.0);
        evidence.put("membershipBatchMedianMicros",batch[6]/1000.0);
        evidence.put("membershipPerformanceScope","Same live network, seven barrels plus512 stale positions; alternating order, four warmups, twelve samples. Timing is diagnostic, not a TPS guarantee.");
    }
    private static void check(boolean ok,String name){evidence.put(name,ok);if(!ok)throw new IllegalStateException(name);}
    public static void tick() {
        if(level==null||finished)return;
        try {
            ticks++;
            if(mustPhase) {
                mustTicks++;
                if(mustTicks==2) {
                    var skipped=new FactoryJob(host,new FactoryPatternData("import A,B\nget 64 minecraft:iron_ingot from A\nput 64 minecraft:iron_ingot into B\ndone",host.factoryId(),ItemStack.EMPTY));
                    long sourceBefore=group("A"),targetBefore=group("B");skipped.tick();
                    check(skipped.finished()&&group("A")==sourceBefore&&group("B")==targetBefore,"ordinary_fully_blocked_skips_without_consuming_source");
                    var independent=new FactoryJob(host,new FactoryPatternData("import A,C\nget 1 minecraft:gold_ingot from A\nput minecraft:gold_ingot into C\ndone",host.factoryId(),ItemStack.EMPTY));
                    independent.tick();check(independent.finished()&&count(6,Items.GOLD_INGOT)==1,"independent_task_finishes_while_must_task_waits");
                }
                if(mustTicks==3)machines.get(3).setItem(0,ItemStack.EMPTY);
                job.tick();
                var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                job=FactoryJob.restore(host,FactoryJob.Saved.CODEC.parse(ops,FactoryJob.Saved.CODEC.encodeStart(ops,job.save()).getOrThrow()).getOrThrow());
                check(job.error().isEmpty(),"must_group_transfer_has_no_runtime_error");
                if(mustTicks<=2)check(!job.finished()&&group("A")==60&&group("B")==64,"must_partial_40_waits_for_remaining_24_without_replay");
                if(mustTicks==3){check(job.finished()&&group("A")==36&&group("B")==88,"must_resumes_after_capacity_opens_and_finishes_exact_64");finish(null);}
                return;
            }
            if(ticks==100) {
                check(host.getMainNode().isActive(),"native_network_active");
                host.tags.reconcile(List.of("A","B","C","Alias","Origin","R"));bind("A",0,1,2);bind("B",3,4,5);bind("C",6);bind("Alias",1,6);bind("Origin",1);bind("R",2);
                check(!host.tags.tag("A",machines.get(0).getBlockPos().asLong(),p->true)&&host.tags.positions("A").size()==3,"same_tag_binding_deduplicates_positions");
                check(group("A")==64&&group("B")==60,"has_aggregates_all_group_members");
                run("work\ndone\nif false do\n    func work\n        get 64 minecraft::item!(minecraft:gold_ingot,minecraft:cobblestone) from A\n        wait 1 tick\n        put minecraft::item into B\n    end\nimport A,B");
            }
            if(ticks==100||ticks==142||ticks==153)membershipCheck();
            if(ticks==101)check(group("A")==64&&group("B")==60,"get_is_group_declaration_without_extraction");
            if(ticks==103) {
                check(job.finished()&&group("A")==0&&group("B")==124,"many_to_many_uses_one_global_64_budget");
                check(count(0,Items.COBBLESTONE)==1600&&count(1,Items.COBBLESTONE)==1600&&count(2,Items.COBBLESTONE)==1600,"both_excluded_item_types_remain_in_sources");
                check(count(4,Items.IRON_INGOT)==64&&count(5,Items.IRON_INGOT)==60,"full_machine_skipped_and_partial_capacity_split");
                var before=host.tags.snapshot();host.tags=FactoryTags.restore(before);check(host.tags.snapshot().equals(before),"all_group_bindings_survive_snapshot_restore");
            }
            if(ticks==110)run("import B,C\nget 100 minecraft:iron_ingot from B\nwait 1 tick\nput minecraft:iron_ingot into C\ndone");
            if(ticks==113)check(job.finished()&&group("B")==24&&group("C")==100,"many_sources_feed_one_machine_exactly");
            if(ticks==120)run("import A,C\nget 100 minecraft:iron_ingot from C\nwait 1 tick\nput minecraft:iron_ingot into A\ndone");
            if(ticks==123)check(job.finished()&&count(0,Items.IRON_INGOT)==64&&count(1,Items.IRON_INGOT)==36&&group("C")==0,"one_source_fills_multiple_machines");
            if(ticks==130)run("import Origin,Alias\nget 20 minecraft:iron_ingot from Origin\nput minecraft:iron_ingot into Alias\ndone");
            if(ticks==132)check(job.finished()&&count(1,Items.IRON_INGOT)==16&&group("C")==20,"overlapping_labels_skip_same_machine_endpoint_without_spending_budget");
            if(ticks==140)level.destroyBlock(machines.get(2).getBlockPos(),true);
            if(ticks==142)check(group("A")==80,"removed_group_member_does_not_break_others");
            if(ticks==150) {
                var pos=machines.get(2).getBlockPos();level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());machines.set(2,(BarrelBlockEntity)level.getBlockEntity(pos));fill(machines.get(2),1);
                run("import C,R\nget 20 minecraft:iron_ingot from C\nput minecraft:iron_ingot into R\ndone");
            }
            if(ticks==153)check(job.finished()&&count(2,Items.IRON_INGOT)==20&&group("A")==100&&group("C")==0,"replacement_at_tagged_position_rejoins_group");
            if(ticks==154)host.pulse("A",3);
            if(ticks==155)check(machines.subList(0,3).stream().allMatch(machine->level.hasNeighborSignal(machine.getBlockPos())),"redstone_reaches_every_group_member");
            if(ticks==158) {
                check(machines.subList(0,3).stream().noneMatch(machine->level.hasNeighborSignal(machine.getBlockPos())),"group_redstone_expires_on_every_member");
                var redistribution=new FactoryJob(host,new FactoryPatternData("done",host.factoryId(),ItemStack.EMPTY));
                redistribution.transfer(true,"minecraft:iron_ingot","A","",200);
                long moved=redistribution.transfer(false,"minecraft:iron_ingot","A","",200);
                check(moved<=100&&group("A")==100,"overlapping_group_never_reexports_newly_received_stock_in_same_put");
            }
            if(ticks>=160) {
                if(job==null||job.finished()) {
                    if(ticks>160)cycles++;
                    if(cycles==128){
                        check(group("A")==100&&group("B")==24&&group("C")==0,"128_group_round_trips_conserve_all_124_iron");
                        machines.get(4).setItem(0,new ItemStack(Items.COBBLESTONE,64));
                        run("import A,B\nget must 64 minecraft:iron_ingot from A\nput minecraft:iron_ingot into B\ndone");mustPhase=true;return;
                    }
                    run("import A,C\nget 17 minecraft:iron_ingot from A\nput minecraft:iron_ingot into C\nwait 1 tick\nget 17 minecraft:iron_ingot from C\nput minecraft:iron_ingot into A\ndone");
                }
            }
            if(job!=null&&!job.finished()) {
                job.tick();if(!job.error().isEmpty())throw new IllegalStateException(job.error());
                var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                job=FactoryJob.restore(host,FactoryJob.Saved.CODEC.parse(ops,FactoryJob.Saved.CODEC.encodeStart(ops,job.save()).getOrThrow()).getOrThrow());
            }
            if(ticks>=100&&ticks!=140&&ticks!=141)check(group("A")+group("B")+group("C")==124,"group_total_iron_conserved");
            if(ticks>=100)check(count(0,Items.GOLD_INGOT)==7&&count(1,Items.GOLD_INGOT)==8,"excluded_resources_stay_in_source_members");
            if(ticks>1000)throw new IllegalStateException("Group fixture timeout");
        }catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error) {
        if(error==null)try{membershipBenchmark();}catch(Throwable failure){error=failure;}
        passed=error==null;evidence.put("status",passed?"passed":"failed");evidence.put("ticks",ticks);evidence.put("roundTrips",cycles);
        if(error!=null) {
            evidence.put("failure",error.toString());
            try {
                evidence.put("machineIron",java.util.stream.IntStream.range(0,machines.size()).mapToObj(i->count(i,Items.IRON_INGOT)).toList());
                evidence.put("machineCobble",java.util.stream.IntStream.range(0,machines.size()).mapToObj(i->count(i,Items.COBBLESTONE)).toList());
                evidence.put("groupA",group("A"));evidence.put("groupB",group("B"));evidence.put("groupC",group("C"));
                evidence.put("active",host.getMainNode().isActive());evidence.put("ownsGrid",FactoryServer.owner(host.factoryGrid())==host);
                if(job!=null){evidence.put("job",FactoryJob.Saved.CODEC.encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),job.save()).getOrThrow());evidence.put("jobFinished",job.finished());evidence.put("jobError",job.error());}
            }catch(Throwable diagnostic){evidence.put("diagnosticFailure",diagnostic.toString());}
        }
        try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("multi-tag-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
        catch(Exception e){throw new RuntimeException(e);}finally{finished=true;}
    }
}
