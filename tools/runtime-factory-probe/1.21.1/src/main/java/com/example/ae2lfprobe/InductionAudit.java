package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import appeng.api.config.Actionable;
import appeng.api.stacks.*;
import appeng.api.upgrades.*;
import appeng.api.networking.security.IActionSource;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import java.util.*;
import java.nio.file.*;

public final class InductionAudit {
    public static volatile boolean finished,passed;
    private static ServerLevel level;private static FactoryBlockEntity host;
    private static AEKey fe;private static Item card;private static FactoryJob job;
    private static int ticks;private static long originalMain,originalSub;
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    public static void start(ServerLevel world) {
        level=world;
        try {
            FactoryProbe.requirePoweredSharedNetwork(level,evidence,"networkBeforeInduction");
            host=(FactoryBlockEntity)level.getBlockEntity(new BlockPos(0,100,0));
            Class<?> energy=Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
            fe=(AEKey)Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey").getMethod("of",energy).invoke(null,energy.getField("FE").get(null));
            card=BuiltInRegistries.ITEM.stream().filter(i->BuiltInRegistries.ITEM.getKey(i).toString().equals("appflux:induction_card")).findFirst().orElseThrow();
            originalMain=main();originalSub=sub();
            evidence.put("originalMainFE",originalMain);evidence.put("originalSubnetFE",originalSub);
            long inserted=host.getMainNode().getGrid().getStorageService().getInventory().insert(fe,2_000_000,Actionable.MODULATE,IActionSource.empty());
            evidence.put("fixtureRequestedFE",2_000_000);evidence.put("fixtureInsertedFE",inserted);
            check(inserted==2_000_000,"real_main_network_accepts_fe_fixture");
            var upgrades=((IUpgradeableObject)(Object)host.getLogic()).getUpgrades();
            check(upgrades.isItemValid(0,card.getDefaultInstance()),"native_upgrade_inventory_accepts_induction_card");
            upgrades.setItemDirect(0,card.getDefaultInstance());
        }catch(Throwable error){finish(error);}
    }
    private static long main(){return host.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(fe);}
    private static long sub(){return host.factoryGrid().getStorageService().getInventory().getAvailableStacks().get(fe);}
    private static long cache(){return host.getLogic().induction().getAvailableStacks().get(fe);}
    public static void tick() {
        if(level==null||finished)return;
        try {
            ++ticks;
            if(ticks==20) {
                FactoryProbe.requirePoweredSharedNetwork(level,evidence,"networkAfterCacheFill");
                check(cache()==FactoryInduction.CAPACITY,"card_fills_bounded_source_cache");
                check(main()+cache()==originalMain+2_000_000,"cache_fill_conserves_native_fe");
                job=new FactoryJob(host,new FactoryPatternData("get 1000 neoforge::fe from source\nwait 5 tick\nput neoforge::fe into storage\ndone",host.factoryId(),ItemStack.EMPTY));
                job.tick();check(cache()==FactoryInduction.CAPACITY,"get_only_declares_induction_source");
                var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                job=FactoryJob.restore(host,FactoryJob.Saved.CODEC.parse(ops,FactoryJob.Saved.CODEC.encodeStart(ops,job.save()).getOrThrow()).getOrThrow());
            } else if(ticks>20&&ticks<30&&job!=null)job.tick();
            if(ticks==30) {
                check(job.finished()&&job.error().isEmpty(),"restored_job_moves_cached_fe");
                check(sub()==originalSub+1000,"fe_reaches_real_subnet_storage");
                ((IUpgradeableObject)(Object)host.getLogic()).getUpgrades().setItemDirect(0,ItemStack.EMPTY);
            }
            if(ticks==50) {
                FactoryProbe.requirePoweredSharedNetwork(level,evidence,"networkAfterCardRemoval");
                check(cache()==0&&main()==originalMain+1_999_000,"removing_card_returns_unused_fe");
            }
            if(ticks>=60&&ticks<188&&ticks%2==0) {
                var upgrades=((IUpgradeableObject)(Object)host.getLogic()).getUpgrades();
                upgrades.setItemDirect(0,ticks%4==0?card.getDefaultInstance():ItemStack.EMPTY);
            }
            if(ticks==190)((IUpgradeableObject)(Object)host.getLogic()).getUpgrades().setItemDirect(0,ItemStack.EMPTY);
            if(ticks==210) {
                FactoryProbe.requirePoweredSharedNetwork(level,evidence,"networkAfterCardStress");
                check(cache()==0&&main()==originalMain+1_999_000&&sub()==originalSub+1000,"sixty_four_card_changes_preserve_all_fe");
                finish(null);
            }
        }catch(Throwable error){finish(error);}
    }
    private static void check(boolean ok,String key){evidence.put(key,ok);if(!ok)throw new IllegalStateException(key);}
    private static void finish(Throwable error) {
        if(level!=null)evidence.put("networkAtFinish",FactoryProbe.sharedNetworkState(level));
        passed=error==null;evidence.put("status",passed?"passed":"failed");evidence.put("ticks",ticks);
        if(error!=null)evidence.put("failure",error.toString());
        try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("induction-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
        catch(Exception e){throw new RuntimeException(e);}finally{finished=true;}
    }
}
