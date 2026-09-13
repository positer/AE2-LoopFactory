package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import appeng.api.config.Actionable;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import java.util.*;
import java.nio.file.*;

/**
 * Industrial Foregoing Souls registers its own NeoForge block capability instead of an AE key type.
 * This fixture keeps the real capability system in the loop: a test-only endpoint is registered through
 * {@link RegisterCapabilitiesEvent} and every soul movement below runs through the production code path.
 */
public final class SoulCompatAudit {
    public static volatile boolean finished,passed;
    private static final BlockPos POS=new BlockPos(272,100,48);
    private static final BlockPos REAL_BASE=POS.east(4);
    private static final int CAPACITY=1000;
    private static final Map<Long,Integer> store=new java.util.concurrent.ConcurrentHashMap<>();
    private static Class<?> handlerInterface,actionClass;
    private static BlockCapability<?,net.minecraft.core.Direction> capability;
    private static ServerLevel level;private static FactoryBlockEntity host;
    private static BarrelBlockEntity source,destination;
    private static FactoryJob job;private static int ticks,phase,wait;
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    private static boolean capabilityResolved;

    /** Called from the mod event bus before any world exists. */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void registerCapability(RegisterCapabilitiesEvent event) {
        if(!net.neoforged.fml.ModList.get().isLoaded("industrialforegoingsouls"))return;
        try {
            handlerInterface=Class.forName("com.buuz135.industrialforegoingsouls.capabilities.ISoulHandler");
            actionClass=Class.forName("com.buuz135.industrialforegoingsouls.capabilities.ISoulHandler$Action");
            capability=(BlockCapability<?,net.minecraft.core.Direction>)Class.forName("com.buuz135.industrialforegoingsouls.capabilities.SoulCapabilities").getField("BLOCK").get(null);
            bind(event,(BlockCapability)capability);
            capabilityResolved=true;
        } catch(ReflectiveOperationException unavailable) {capabilityResolved=false;}
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void bind(RegisterCapabilitiesEvent event,BlockCapability capability) {
        event.registerBlockEntity(capability,BlockEntityType.BARREL,
            (net.minecraft.world.level.block.entity.BarrelBlockEntity entity,net.minecraft.core.Direction side)->handler(entity.getBlockPos()));
    }
    private static Object handler(BlockPos pos) {
        return java.lang.reflect.Proxy.newProxyInstance(SoulCompatAudit.class.getClassLoader(),new Class<?>[]{handlerInterface},(proxy,method,args)->switch(method.getName()) {
            case "getSoulTanks" -> 1;
            case "getSoulInTank" -> store.getOrDefault(pos.asLong(),0);
            case "getTankCapacity" -> CAPACITY;
            case "fill" -> fill(pos,args!=null&&args.length>0?(Integer)args[0]:0,args!=null&&args.length>1&&execute(args[1]));
            case "drain" -> drain(pos,args!=null&&args.length>0?(Integer)args[0]:0,args!=null&&args.length>1&&execute(args[1]));
            case "toString" -> "SoulCompatEndpoint"+pos;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy==args[0];
            default -> null;
        });
    }
    private static boolean execute(Object action) {
        try {return (Boolean)actionClass.getMethod("execute").invoke(action);}
        catch(ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
    }
    private static int fill(BlockPos pos,int amount,boolean execute) {
        int before=store.getOrDefault(pos.asLong(),0);int accepted=Math.max(0,Math.min(amount,CAPACITY-before));
        if(execute)store.put(pos.asLong(),before+accepted);
        return accepted;
    }
    private static int drain(BlockPos pos,int amount,boolean execute) {
        int before=store.getOrDefault(pos.asLong(),0);int drained=Math.max(0,Math.min(amount,before));
        if(execute)store.put(pos.asLong(),before-drained);
        return drained;
    }
    /** Real machine capability calls; no transfer engine code is replaced. */
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object endpoint(BlockPos pos) {return level.getCapability((BlockCapability)capability,pos,null);}
    private static int tank(BlockPos pos) {
        var handler=endpoint(pos);if(handler==null)return -1;
        try {return (Integer)handlerInterface.getMethod("getSoulInTank",int.class).invoke(handler,0);}
        catch(ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
    }
    private static void check(boolean ok,String name){evidence.put(name,ok);if(!ok)throw new IllegalStateException(name);}
    public static void start(ServerLevel world) {
        System.out.println("[ae2lf-probe] SoulCompatAudit.start invoked");
        level=world;
        try {
            evidence.put("soulCapabilityResolved",capabilityResolved);
            evidence.put("productionSoulCapabilityPresent",FactoryNativeTransfers.soulCapabilityPresent());
            level.setChunkForced(POS.getX()>>4,POS.getZ()>>4,true);
            level.setBlockAndUpdate(POS,FactoryContent.TERMINAL.get().defaultBlockState());host=(FactoryBlockEntity)level.getBlockEntity(POS);
            level.setBlockAndUpdate(POS.west(),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
            ((EnergyCellBlockEntity)level.getBlockEntity(POS.west())).injectAEPower(1_000_000,Actionable.MODULATE);
            var firstCable=POS.east(1);var secondCable=POS.east(2);
            level.setBlockAndUpdate(firstCable,FactoryContent.CABLE.get().defaultBlockState());
            level.setBlockAndUpdate(secondCable,FactoryContent.CABLE.get().defaultBlockState());
            level.setBlockAndUpdate(firstCable.above(),net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            level.setBlockAndUpdate(secondCable.above(),net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
            source=(BarrelBlockEntity)level.getBlockEntity(firstCable.above());
            destination=(BarrelBlockEntity)level.getBlockEntity(secondCable.above());
            level.setBlockAndUpdate(REAL_BASE,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("industrialforegoingsouls:soul_laser_base")).defaultBlockState());
            check(host!=null&&source!=null&&destination!=null,"soul_fixture_blocks_present");
            check(capabilityResolved&&FactoryNativeTransfers.soulCapabilityPresent(),"soul_capability_resolves_through_neoforge");
            phase=1;
        } catch(Throwable failure) {finish(failure);}
    }
    /** The subnet only knows its members once the node has activated, so binding happens on a later tick. */
    private static void bind() throws ReflectiveOperationException {
            var candidates=new java.util.LinkedHashSet<Long>();
            candidates.add(POS.asLong());candidates.add(POS.west().asLong());candidates.add(POS.east(1).asLong());candidates.add(POS.east(2).asLong());
            candidates.add(source.getBlockPos().asLong());candidates.add(destination.getBlockPos().asLong());candidates.add(REAL_BASE.asLong());
            evidence.put("soulSubnetMembers",FactoryServer.members(host.factoryGrid(),candidates).stream().map(BlockPos::toString).toList());
            evidence.put("soulFixtureActive",host.getMainNode()!=null&&host.getMainNode().isActive());
            host.tags.reconcile(java.util.List.of("Src","Dst"));
            check(host.tags.tag("Src",source.getBlockPos().asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))),"soul_bind_Src");
            check(host.tags.tag("Dst",destination.getBlockPos().asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))),"soul_bind_Dst");
            var realBase=endpoint(REAL_BASE);
            evidence.put("realSoulLaserBaseCapability",realBase!=null);
            var execute=actionClass.getField("EXECUTE").get(null);
            if(realBase!=null) {
                evidence.put("realSoulLaserBaseTanks",handlerInterface.getMethod("getSoulTanks").invoke(realBase));
                evidence.put("realSoulLaserBaseCapacity",handlerInterface.getMethod("getTankCapacity",int.class).invoke(realBase,0));
                int inserted=(Integer)handlerInterface.getMethod("fill",int.class,actionClass).invoke(realBase,64,execute);
                evidence.put("realSoulLaserBaseAcceptedInsert",inserted);
                evidence.put("realSoulLaserBaseBoundary","Industrial Foregoing souls machines expose a sink-only capability: fill returns 0, drain consumes for operation");
            }
            var filter=new FactoryNativeTransfers.Resource("industrialforegoingsouls::soul","industrialforegoingsouls:soul",null);
            int seeded=(Integer)handlerInterface.getMethod("fill",int.class,actionClass).invoke(endpoint(source.getBlockPos()),128,execute);
            evidence.put("soulsWrittenThroughCapability",seeded);
            check(seeded==128,"soul_endpoint_accepts_capability_write");
            check(tank(source.getBlockPos())==128,"soul_endpoint_reports_written_amount");
            evidence.put("nativeCountBeforeMove",FactoryNativeTransfers.count(level,List.of(source.getBlockPos()),r->r.type().equals(filter.type())));
            long moved=FactoryNativeTransfers.move(level,List.of(source.getBlockPos()),null,List.of(destination.getBlockPos()),null,
                r->r.type().equals(filter.type()),64,x->{throw new IllegalStateException("Unexpected soul recovery "+x);});
            check(moved==64,"native_soul_move_exact_64");
            check(tank(source.getBlockPos())==64&&tank(destination.getBlockPos())==64,"native_soul_split_is_exact");
            phase=2;
    }
    public static void tick() {
        if(level==null||finished)return;
        try {
            if(phase==0)return;
            ticks++;
            if(ticks==1)System.out.println("[ae2lf-probe] SoulCompatAudit.tick running");
            if(ticks>6000)throw new IllegalStateException("Soul fixture timeout");
            if(phase==1) {
                if(ticks<100)return;
                if(host.getMainNode()==null||!host.getMainNode().isActive()||host.factoryGrid()==null)
                    throw new IllegalStateException("Soul fixture network never activated: active="
                        +(host.getMainNode()!=null&&host.getMainNode().isActive())+" subnet="+(host.factoryGrid()!=null));
                bind();
                return;
            }
            if(phase==2) {
                check(new FactoryJob(host,new FactoryPatternData("done",host.factoryId(),ItemStack.EMPTY)).count("Src","industrialforegoingsouls::soul")==64,"has_counts_native_souls");
                job=new FactoryJob(host,new FactoryPatternData("import Src,Dst\nget industrialforegoingsouls::soul from Src\nput industrialforegoingsouls::soul into Dst\ndone",host.factoryId(),ItemStack.EMPTY));
                job.tick();
                check(job.error().isEmpty(),"soul_program_declares_without_error");
                phase=3;wait=0;return;
            }
            if(phase==3) {
                job.tick();
                if(++wait<4)return;
                check(job.finished()&&job.error().isEmpty(),"soul_program_finishes");
                check(tank(source.getBlockPos())==0&&tank(destination.getBlockPos())==128,"soul_program_moved_exactly_128");
                evidence.put("providerCacheBoundary","souls are not an AE key type, so they stay machine-to-machine; the provider cache holds AE keys only");
                finish(null);
            }
        } catch(Throwable failure) {finish(failure);}
    }
    private static void finish(Throwable failure) {
        if(finished)return;
        evidence.put("status",failure==null?"passed":"failed");
        if(failure!=null){evidence.put("failure",failure.toString());evidence.put("stack",java.util.Arrays.toString(failure.getStackTrace()));}
        try {Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("addon-soul-report.json"),
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
        catch(Exception writeFailure){throw new RuntimeException(writeFailure);}
        passed=failure==null;finished=true;
    }
}
