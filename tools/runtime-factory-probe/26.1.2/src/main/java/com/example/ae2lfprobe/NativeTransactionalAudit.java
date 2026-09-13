package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import appeng.core.definitions.AEBlocks;
import appeng.api.config.Actionable;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.RegisteredResource;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;
import java.nio.file.*;

/** Test-only registered capabilities backed by persistent world block data, not AE inventories. */
public final class NativeTransactionalAudit {
    public static volatile boolean finished,passed;
    private static final BlockPos POS=new BlockPos(688,100,80),A=POS.east(2).above(),B=POS.east(4).above();
    private static final BlockCapability<ResourceHandler<Matter>,Direction> MATTER=BlockCapability.createSided(Identifier.fromNamespaceAndPath("ae2lf_runtime_probe","additional_storage"),ResourceHandler.asClass());
    private static final Matter IRON=new Matter(Items.IRON_INGOT),GOLD=new Matter(Items.GOLD_INGOT);
    private static final Map<BlockPos,Store> stores=new HashMap<>();
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    private static ServerLevel level;private static FactoryBlockEntity host;
    private static FactoryJob matterJob,energyJob;
    private static int ticks,directions,restores;private static boolean cycling,rejectAfterExtract;
    private static long rollbackBaseline;
    private record Matter(Item value) implements RegisteredResource<Item> {
        public Holder<Item> typeHolder(){return value.builtInRegistryHolder();}
        public boolean isEmpty(){return false;}
    }
    private static final class Store extends SnapshotJournal<long[]> {
        final BarrelBlockEntity barrel;
        long matterCapacity=100_000,energyCapacity=200_000;
        Store(BarrelBlockEntity barrel){this.barrel=barrel;}
        long get(int index){return barrel.getPersistentData().getLongOr("ae2lfNative"+index,0);}
        void set(int index,long amount){barrel.getPersistentData().putLong("ae2lfNative"+index,amount);barrel.setChanged();}
        protected long[] createSnapshot(){return new long[]{get(0),get(1),get(2)};}
        protected void revertToSnapshot(long[] values){for(int i=0;i<3;i++)set(i,values[i]);}
        protected void onRootCommit(long[] original){barrel.setChanged();}
        int move(int index,int amount,boolean insert,Direction side,TransactionContext transaction) {
            if(side!=(insert?Direction.SOUTH:Direction.NORTH)||amount==0)return 0;
            if(insert&&index==2&&barrel.getBlockPos().equals(B)&&rejectAfterExtract&&stores.get(A).get(2)<rollbackBaseline)return 0;
            long capacity=index==2?energyCapacity:matterCapacity;
            int moved=(int)Math.min(amount,insert?Math.max(0,capacity-get(index)):get(index));
            if(moved>0){updateSnapshots(transaction);set(index,get(index)+(insert?moved:-moved));}return moved;
        }
    }
    private static Store store(Object entity) {
        if(!(entity instanceof BarrelBlockEntity barrel)||(!barrel.getBlockPos().equals(A)&&!barrel.getBlockPos().equals(B)))return null;
        return stores.compute(barrel.getBlockPos(),(p,old)->old!=null&&old.barrel==barrel?old:new Store(barrel));
    }
    public static void register(IEventBus bus) {bus.addListener(NativeTransactionalAudit::capabilities);}
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(MATTER,(world,pos,state,entity,side)->{
            var store=store(entity);if(store==null)return null;
            return new ResourceHandler<Matter>() {
                public int size(){return 2;}
                public Matter getResource(int index){return index==0?IRON:GOLD;}
                public long getAmountAsLong(int index){return store.get(index);}
                public long getCapacityAsLong(int index,Matter resource){return store.matterCapacity;}
                public boolean isValid(int index,Matter resource){return getResource(index).equals(resource);}
                public int insert(int index,Matter resource,int amount,TransactionContext transaction){return isValid(index,resource)?store.move(index,amount,true,side,transaction):0;}
                public int extract(int index,Matter resource,int amount,TransactionContext transaction){return isValid(index,resource)?store.move(index,amount,false,side,transaction):0;}
            };
        },Blocks.BARREL);
        event.registerBlock(Capabilities.Energy.BLOCK,(world,pos,state,entity,side)->{
            var store=store(entity);if(store==null)return null;
            return new EnergyHandler() {
                public long getAmountAsLong(){return store.get(2);}
                public long getCapacityAsLong(){return store.energyCapacity;}
                public int insert(int amount,TransactionContext transaction){return store.move(2,amount,true,side,transaction);}
                public int extract(int amount,TransactionContext transaction){return store.move(2,amount,false,side,transaction);}
            };
        },Blocks.BARREL);
    }
    private static void check(boolean ok,String key){evidence.put(key,ok);if(!ok)throw new IllegalStateException(key);}
    private static FactoryJob job(String code){return new FactoryJob(host,new FactoryPatternData(code,host.factoryId(),ItemStack.EMPTY));}
    private static String code(boolean energy,boolean reverse){String resource=energy?"neoforge::fe":"minecraft::item!(minecraft:gold_ingot)";return "import A,B\nget must "+(energy?100000:64)+" "+resource+" from "+(reverse?"B":"A")+"\nwait 1 tick\nput "+resource+" into "+(reverse?"A":"B")+"\ndone";}
    private static FactoryJob restore(FactoryJob job) {
        var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);restores++;
        return FactoryJob.restore(host,FactoryJob.Saved.CODEC.parse(ops,FactoryJob.Saved.CODEC.encodeStart(ops,job.save()).getOrThrow()).getOrThrow());
    }
    public static void start(ServerLevel world) {
        level=world;
        try {
            world.setChunkForced(POS.getX()>>4,POS.getZ()>>4,true);
            world.setBlockAndUpdate(POS,FactoryContent.TERMINAL.get().defaultBlockState());host=(FactoryBlockEntity)world.getBlockEntity(POS);
            world.setBlockAndUpdate(POS.west(),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());((EnergyCellBlockEntity)world.getBlockEntity(POS.west())).injectAEPower(1_000_000,Actionable.MODULATE);
            for(int i=1;i<=4;i++)world.setBlockAndUpdate(POS.east(i),FactoryContent.CABLE.get().defaultBlockState());
            for(var pos:List.of(A,B)){world.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());store(world.getBlockEntity(pos));}
            stores.get(A).set(0,64);stores.get(A).set(1,7);stores.get(A).set(2,500);
            evidence.put("scope","Real server world capability lookup and transactions on test-only persistent barrel endpoints; not a claim of a 26.1.2 Mekanism release.");
            evidence.put("additionalCapability",MATTER.name().toString());
        }catch(Throwable error){finish(error);}
    }
    public static void tick() {
        if(level==null||finished)return;
        try {
            ticks++;if(ticks<100)return;
            var a=stores.get(A);var b=stores.get(B);
            if(ticks==100) {
                check(host.getMainNode().isActive(),"native_network_active");host.tags.reconcile(List.of("A","B"));
                check(host.tags.tag("A",A.asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p)))&&host.tags.tag("B",B.asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))),"native_endpoint_tags_bound");
                rollbackBaseline=500;rejectAfterExtract=true;
                long moved=FactoryNativeTransfers.move(level,List.of(A),null,List.of(B),null,r->r.type().equals("neoforge::fe"),100,s->{throw new IllegalStateException("Unexpected recovery buffer");});
                check(moved==0&&a.get(2)==500&&b.get(2)==0,"destination_refusal_rolls_back_native_source_transaction");rejectAfterExtract=false;
                moved=FactoryNativeTransfers.move(level,List.of(A),Direction.SOUTH,List.of(B),Direction.SOUTH,r->r.type().equals("neoforge::fe"),100,s->{});
                check(moved==0&&a.get(2)==500,"explicit_wrong_face_does_not_fallback");
                moved=FactoryNativeTransfers.move(level,List.of(A),null,List.of(B),null,r->r.type().equals("neoforge::fe"),100,s->{});
                check(moved==100&&a.get(2)==400&&b.get(2)==100,"unsided_readonly_falls_back_to_available_faces");
                a.set(2,100000);b.set(2,0);b.matterCapacity=24;b.energyCapacity=4096;
                matterJob=job(code(false,false));energyJob=job(code(true,false));
            }
            if(ticks==150) {
                check(a.get(0)==40&&b.get(0)==24&&a.get(1)==7&&!matterJob.finished(),"custom_registered_resource_partial_must_and_exclusion");
                check(a.get(2)==95904&&b.get(2)==4096&&!energyJob.finished(),"native_FE_partial_must_is_independent");
                check(matterJob.count("A","minecraft::item!(minecraft:gold_ingot)")==40&&energyJob.count("A","neoforge::fe")==95904,"native_HAS_uses_registered_resource_and_energy_handlers");
                var ordinary=job("import A,B\nget 64 minecraft:iron_ingot from A\nput 64 minecraft:iron_ingot into B\ndone");ordinary.tick();
                check(ordinary.finished()&&a.get(0)==40&&b.get(0)==24,"ordinary_native_custom_resource_fully_blocked_skips");
            }
            if(ticks==200){b.matterCapacity=100000;b.energyCapacity=200000;}
            matterJob.tick();energyJob.tick();check(matterJob.error().isEmpty()&&energyJob.error().isEmpty(),"native_jobs_error_free");
            if(ticks%7==0){matterJob=restore(matterJob);energyJob=restore(energyJob);}
            if(ticks>=210&&matterJob.finished()&&energyJob.finished()) {
                boolean atB=!cycling||directions%2==0;
                check(a.get(0)==(atB?0:64)&&b.get(0)==(atB?64:0)&&a.get(1)==7,"additional_resource_exact_both_endpoints");
                check(a.get(2)==(atB?0:100000)&&b.get(2)==(atB?100000:0),"native_FE_exact_both_endpoints");
                if(cycling&&directions==32){finish(null);return;}
                cycling=true;directions++;boolean reverse=directions%2==1;
                matterJob=job(code(false,reverse));energyJob=job(code(true,reverse));
            }
            if(ticks>1000)throw new IllegalStateException("Native transaction fixture timeout");
        }catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error) {
        finished=true;passed=error==null;evidence.put("status",passed?"passed":"failed");evidence.put("ticks",ticks);evidence.put("directions",directions);evidence.put("codecRestores",restores);
        if(error!=null){evidence.put("failure",error.toString());evidence.put("stack",Arrays.toString(error.getStackTrace()));}
        try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("native-transactions-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}catch(Exception e){throw new RuntimeException(e);}
    }
}
