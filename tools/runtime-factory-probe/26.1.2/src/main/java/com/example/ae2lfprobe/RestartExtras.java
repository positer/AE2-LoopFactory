package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import appeng.api.config.Actionable;
import appeng.api.stacks.*;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.networking.security.IActionSource;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import java.util.*;
import java.nio.file.*;

/** Additional disk restart assertions; same owned world, two separate JVMs. */
public final class RestartExtras {
    private static final BlockPos[] POS={new BlockPos(8,100,12),new BlockPos(12,100,12),new BlockPos(16,100,12)};
    private static final String MAXIMUM_UNICODE_DRAFT="#"+"中😀".repeat(21843)+"x\ndone";
    private static boolean initialized,armed;
    private static AEKey fe;
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    private static ServerLevel level;
    public static boolean readyForSave(FactoryBlockEntity provider) throws Exception {
        level=(ServerLevel)provider.getLevel();
        if(!initialized) {
            check(MAXIMUM_UNICODE_DRAFT.length()==FactoryCompiler.MAX_SOURCE_LENGTH,"maximum_unicode_fixture_reaches_source_limit");
            for(var pos:POS) {
                level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
                level.setBlockAndUpdate(pos,FactoryContent.TERMINAL.get().defaultBlockState());
                level.setBlockAndUpdate(pos.west(),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
                ((EnergyCellBlockEntity)level.getBlockEntity(pos.west())).injectAEPower(1_000_000,Actionable.MODULATE);
                level.setBlockAndUpdate(pos.east(),FactoryContent.CABLE.get().defaultBlockState());
                for(var machine:List.of(pos.east().above(),pos.east().below()))level.setBlockAndUpdate(machine,Blocks.BARREL.defaultBlockState());
                for(int extra=1;extra<=2;extra++) {
                    var cable=pos.east().north(extra);level.setBlockAndUpdate(cable,FactoryContent.CABLE.get().defaultBlockState());
                    level.setBlockAndUpdate(cable.above(),Blocks.BARREL.defaultBlockState());
                }
                barrel(pos,true).setItem(0,new ItemStack(Items.IRON_INGOT,64));
                if(pos.equals(POS[2])) {
                    barrel(pos,false).setItem(0,new ItemStack(Items.IRON_INGOT,40));
                    for(int slot=1;slot<27;slot++)barrel(pos,false).setItem(slot,new ItemStack(Items.COBBLESTONE,64));
                }
            }
            fe=key();
            var card=BuiltInRegistries.ITEM.stream().filter(i->BuiltInRegistries.ITEM.getKey(i).toString().equals("appflux:induction_card")).findFirst().orElseThrow();
            check(provider.getMainNode().getGrid().getStorageService().getInventory().insert(fe,2_000_000,Actionable.MODULATE,IActionSource.empty())==2_000_000,"restart_fe_fixture_inserted");
            ((IUpgradeableObject)(Object)provider.getLogic()).getUpgrades().setItemDirect(0,card.getDefaultInstance());
            initialized=true;return false;
        }
        if(!armed) {
            for(var pos:POS)if(!host(pos).getMainNode().isActive())return false;
            for(int i=0;i<POS.length;i++) {
                var host=host(POS[i]);host.tags.reconcile(List.of("A","B","PersistenceOnly"));
                for(int n=0;n<6000;n++)host.tags.tag("PersistenceOnly",Long.MAX_VALUE-n,ignored->true);
                host.tags.tag("A",POS[i].east().above().asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p)));
                for(int extra=1;extra<=2;extra++)host.tags.tag("A",POS[i].east().north(extra).above().asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p)));
                host.tags.tag("B",POS[i].east().below().asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p)));
                String code=i==0?"import A,B\nget 1 minecraft:iron_ingot from A\nwait 600 tick\nput minecraft:iron_ingot into B\ndone":
                        i==1?"EVERY 600 TICKS DO INPUT 1 iron_ingot FROM A OUTPUT TO B END EVERY REDSTONE PULSE DO INPUT 1 iron_ingot FROM A OUTPUT TO B END":
                        "import A,B\nget must 64 minecraft:iron_ingot from A\nput minecraft:iron_ingot into B\ndone";
                var stack=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(code,host.factoryId(),ItemStack.EMPTY));
                host.factoryPatterns().setItemDirect(0,stack);
                check(host.factoryDraft().equals(code),"coded_pattern_replaces_draft_before_persistence_"+POS[i].getX());
                // Installing a coded pattern deliberately replaces the editor draft. Set the
                // independent maximum-size draft afterwards, exactly as an editor change would.
                host.setFactoryDraft(MAXIMUM_UNICODE_DRAFT);
                level.setBlockAndUpdate(POS[i].north(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
            armed=true;return false;
        }
        var custom=state(POS[0]);var sfm=state(POS[1]);
        if(custom==null||sfm==null||sfm.elapsed()<10)return false;
        var must=state(POS[2]);
        check(must!=null&&must.pendingTransfer()&&must.transferRemaining()==40&&barrel(POS[2],true).getItem(0).getCount()==40,"must_partial_24_checkpoint_remaining_40");
        check(custom.delay()>0&&custom.delay()<600,"terminal_wait_checkpoint_created");
        check(sfm.lastSignal()&&sfm.elapsed()<600&&barrel(POS[1],false).getItem(0).getCount()==1,"sfm_timer_and_held_pulse_checkpoint_created");
        check(provider.getLogic().induction().getAvailableStacks().get(fe)==1_000_000,"induction_cache_checkpoint_created");
        for(var pos:POS)checkMaximumDraft(pos,"before_disk_save");
        var checkpoint=Map.of("sfmElapsed",sfm.elapsed(),"terminalDelay",custom.delay());
        Files.writeString(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("ae2lf-extra-checkpoint.json"),new com.google.gson.Gson().toJson(checkpoint));
        write("restart_ready");return true;
    }
    public static void restored(FactoryBlockEntity provider) throws Exception {
        level=(ServerLevel)provider.getLevel();fe=key();
        var checkpoint=com.google.gson.JsonParser.parseString(Files.readString(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("ae2lf-extra-checkpoint.json"))).getAsJsonObject();
        var custom=state(POS[0]);var sfm=state(POS[1]);
        check(custom!=null&&custom.delay()>0&&custom.delay()<=checkpoint.get("terminalDelay").getAsLong(),"terminal_wait_loaded_from_disk");
        check(sfm!=null&&sfm.elapsed()>=checkpoint.get("sfmElapsed").getAsLong()&&sfm.elapsed()<600&&sfm.lastSignal(),"sfm_elapsed_and_pulse_edge_loaded_from_disk");
        check(provider.getLogic().induction().getAvailableStacks().get(fe)==1_000_000,"induction_buffer_loaded_from_disk");
        for(var pos:POS) {
            check(host(pos).tags.positions("A").size()==3,"three_real_machine_bindings_loaded_"+pos.getX());
            checkMaximumDraft(pos,"loaded_from_disk");
            check(host(pos).tags.positions("PersistenceOnly").size()==6000,"six_thousand_serialized_tag_positions_loaded_"+pos.getX());
        }
        var must=state(POS[2]);
        check(must!=null&&must.pendingTransfer()&&must.transferRemaining()==40&&barrel(POS[2],true).getItem(0).getCount()==40&&barrel(POS[2],false).getItem(0).getCount()==64,"must_partial_progress_loaded_without_duplicate_transfer");
        barrel(POS[2],false).setItem(1,ItemStack.EMPTY);
        write("resumed");
    }
    public static void complete(FactoryBlockEntity provider) throws Exception {
        check(barrel(POS[0],true).getItem(0).getCount()==63&&barrel(POS[0],false).getItem(0).getCount()==1,"terminal_wait_finishes_once_after_process_restart");
        check(barrel(POS[1],true).getItem(0).getCount()==62&&barrel(POS[1],false).getItem(0).getCount()==2,"sfm_timer_continues_without_replaying_held_pulse");
        check(barrel(POS[2],true).getItem(0).isEmpty()&&barrel(POS[2],false).getItem(0).getCount()==64&&barrel(POS[2],false).getItem(1).is(Items.IRON_INGOT)&&barrel(POS[2],false).getItem(1).getCount()==40&&state(POS[2])==null,"must_finishes_only_remaining_40_after_real_process_restart");
        long main=provider.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(fe);
        check(main+provider.getLogic().induction().getAvailableStacks().get(fe)==2_000_000,"induction_fe_conserved_across_process_restart");
        write("passed");
    }
    private static FactoryBlockEntity host(BlockPos pos){return (FactoryBlockEntity)level.getBlockEntity(pos);}
    private static void checkMaximumDraft(BlockPos pos,String stage) throws Exception {
        String actual=host(pos).factoryDraft();
        var details=new LinkedHashMap<String,Object>();
        details.put("expectedUtf16Length",MAXIMUM_UNICODE_DRAFT.length());
        details.put("actualUtf16Length",actual.length());
        details.put("expectedSha256Utf8",sha256(MAXIMUM_UNICODE_DRAFT));
        details.put("actualSha256Utf8",sha256(actual));
        evidence.put("unicode_draft_"+stage+"_"+pos.getX(),details);
        // Hashes and lengths are diagnostics only. Acceptance compares every UTF-16 code unit,
        // including every surrogate pair, against the independently recreated value in this JVM.
        check(actual.equals(MAXIMUM_UNICODE_DRAFT),"maximum_unicode_draft_"+stage+"_"+pos.getX());
    }
    private static String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static BarrelBlockEntity barrel(BlockPos pos,boolean source){return (BarrelBlockEntity)level.getBlockEntity(source?pos.east().above():pos.east().below());}
    private static FactoryMachine.Snapshot state(BlockPos pos) throws Exception {
        var field=FactoryBlockEntity.class.getDeclaredField("terminalJobs");field.setAccessible(true);
        @SuppressWarnings("unchecked") var jobs=(List<FactoryJob>)field.get(host(pos));
        return jobs.isEmpty()?null:new com.google.gson.Gson().fromJson(jobs.getFirst().save().continuation(),FactoryMachine.Snapshot.class);
    }
    private static AEKey key() throws Exception {
        Class<?> type=Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
        return (AEKey)Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey").getMethod("of",type).invoke(null,type.getField("FE").get(null));
    }
    private static void check(boolean ok,String key){evidence.put(key,ok);if(!ok){write("failed");throw new IllegalStateException(key);}}
    private static void write(String status) {
        evidence.put("status",status);
        try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("restart-extras.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
        catch(Exception e){throw new RuntimeException(e);}
    }
}
