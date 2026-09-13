package com.example.ae2lfprobe;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Actual powered AE grids and installed infinite cells, with one production job tick per server tick. */
public final class HugeQuantityAudit {
    public static volatile boolean finished, passed;
    private static final String GENERATION="1.21.1";
    private static final BlockPos POS=new BlockPos(1200,100,480);
    private static final long NEAR_MAX=Long.MAX_VALUE-4096;
    private static final long[] QUANTITIES={1_000_000L,1_000_000_000L,1_000_000_000_000L,1_000_000_000_000_000L,NEAR_MAX};
    private static final Gson GSON=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private static final List<Map<String,Object>> cases=new ArrayList<>();
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    private static final List<Case> plan=new ArrayList<>();
    private static ServerLevel level;
    private static FactoryBlockEntity host;
    private static DriveBlockEntity sourceDrive,destinationDrive;
    private static MEStorage source,destination;
    private static FactoryJob job;
    private static Case current;
    private static Map<String,Object> row;
    private static List<Map<String,Object>> tickMovements;
    private static BigInteger supplied=BigInteger.ZERO,withdrawn=BigInteger.ZERO;
    private static long startedNanos,caseStartedNanos,jobNanos,codecNanos;
    private static int ticks,caseIndex,phase,jobTicks,caseCodecs,totalCodecs,cellCodecs,configuredTickRate=20;
    private static boolean started,ready;
    private record Resource(String label,AEKey key,String selector,String unit) {}
    private record Case(String category,Resource resource,long quantity) {}
    public static int timeoutTicks(){return 600;}

    public static void start(ServerLevel world) {
        if(started)return;
        started=true;level=world;startedNanos=System.nanoTime();
        try {
            configuredTickRate=StressAudit.configureTickRate(world);
            var resources=List.of(new Resource("item",AEItemKey.of(Items.IRON_INGOT),"minecraft::item","items"),
                    new Resource("fluid",AEFluidKey.of(Fluids.WATER),"minecraft::fluid","AE fluid storage units"),
                    new Resource("FE",fluxKey(),"neoforge::fe","FE"));
            for(var resource:resources)for(long quantity:QUANTITIES)plan.add(new Case("direct",resource,quantity));
            for(var resource:resources)plan.add(new Case("partialMust",resource,NEAR_MAX));
            for(var resource:resources)plan.add(new Case("saturatedReturn",resource,NEAR_MAX));
            place(POS,FactoryContent.PROVIDER.get().defaultBlockState().setValue(BlockStateProperties.FACING,Direction.SOUTH));
            host=(FactoryBlockEntity)level.getBlockEntity(POS);
            power(POS.west());
            destinationDrive=drive(POS.east());
            place(POS.south(),FactoryContent.CABLE.get().defaultBlockState());
            place(POS.south(2),FactoryContent.CABLE.get().defaultBlockState());
            sourceDrive=drive(POS.south(3));
            power(POS.south(4));
            evidence.put("fixturePosition",POS.toShortString());
            evidence.put("fixturePower","Two separate native AE dense energy cells, seeded once with 1000000 AE each");
        } catch(Throwable failure){finish(failure);}
    }

    private static AEKey fluxKey() throws ReflectiveOperationException {
        if(!net.neoforged.fml.ModList.get().isLoaded("appflux"))throw new IllegalStateException("Applied Flux must be loaded for the FE case");
        var type=Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
        return (AEKey)Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey")
                .getMethod("of",type).invoke(null,type.getField("FE").get(null));
    }
    private static void place(BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
        level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);level.setBlockAndUpdate(pos,state);
    }
    private static DriveBlockEntity drive(BlockPos pos) {
        // Native ME drives do not connect on their front. East leaves the source's north/south path open.
        var block=AEBlocks.DRIVE.block();
        place(pos,block.getOrientationStrategy().setFacing(block.defaultBlockState(),Direction.EAST));
        var drive=(DriveBlockEntity)level.getBlockEntity(pos);
        drive.getInternalInventory().setItemDirect(0,ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());return drive;
    }
    private static void power(BlockPos pos) {
        place(pos,AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
        ((EnergyCellBlockEntity)level.getBlockEntity(pos)).injectAEPower(1_000_000,Actionable.MODULATE);
    }
    private static IActionSource action(){return IActionSource.ofMachine(host);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}

    public static void tick() {
        if(!started||finished)return;
        try {
            if(++ticks>timeoutTicks())throw new IllegalStateException("Huge quantity audit timed out");
            if(!ready) {
                if(ticks<100)return;
                var topology=new LinkedHashMap<String,Object>();
                topology.put("serverTick",ticks);topology.put("providerFront",host.getFront().name());
                topology.put("sourceDriveFront",sourceDrive.getFront().name());topology.put("destinationDriveFront",destinationDrive.getFront().name());
                topology.put("providerMainGrid",gridId(host.getMainNode().getGrid()));topology.put("providerSubnetGrid",gridId(host.factoryGrid()));
                topology.put("sourceDriveGrid",gridId(sourceDrive.getMainNode().getGrid()));topology.put("destinationDriveGrid",gridId(destinationDrive.getMainNode().getGrid()));
                topology.put("providerActive",host.getMainNode().isActive());topology.put("sourceDriveActive",sourceDrive.getMainNode().isActive());
                topology.put("destinationDriveActive",destinationDrive.getMainNode().isActive());
                topology.put("sourceBelongsToSubnet",sourceDrive.getMainNode().getGrid()==host.factoryGrid());
                topology.put("destinationBelongsToMain",destinationDrive.getMainNode().getGrid()==host.getMainNode().getGrid());
                evidence.put("actualTopology",topology);
                if(!host.getMainNode().isActive()||host.factoryGrid()==null
                        ||!sourceDrive.getMainNode().isActive()||!destinationDrive.getMainNode().isActive()
                        ||sourceDrive.getMainNode().getGrid()!=host.factoryGrid()
                        ||destinationDrive.getMainNode().getGrid()!=host.getMainNode().getGrid()) {
                    require(ticks<300,"Both real AE drives must form the required native topology within 300 server ticks: "+topology);return;
                }
                require(host.isolated()&&FactoryServer.owner(host.factoryGrid())==host,"Provider must own an isolated subnet");
                require(host.factoryGrid()!=host.getMainNode().getGrid(),"Source and destination must be distinct AE networks");
                require(sourceDrive.getMainNode().getGrid()==host.factoryGrid(),"Source drive must belong to the front subnet");
                require(destinationDrive.getMainNode().getGrid()==host.getMainNode().getGrid(),"Destination drive must belong to the provider main network");
                source=host.factoryGrid().getStorageService().getInventory();
                destination=host.getMainNode().getGrid().getStorageService().getInventory();
                evidence.put("poweredIsolatedNativeNetworks",true);
                evidence.put("sourceInventoryClass",source.getClass().getName());
                evidence.put("destinationInventoryClass",destination.getClass().getName());
                evidence.put("distinctNetworks",true);
                checkQuantitySyntax();ready=true;
            }
            if(current==null)beginCase(plan.get(caseIndex));
            // Fixture mutations are explicit inputs/withdrawals and are included in the independent ledger.
            if(phase==2&&current.category().equals("partialMust"))seed(source,current.quantity()-17);
            if(phase==2&&current.category().equals("saturatedReturn")) {
                long amount=Long.MAX_VALUE-17;
                require(destination.extract(current.resource().key(),amount,Actionable.MODULATE,action())==amount,"Remove only the prefilled destination amount");
                withdrawn=withdrawn.add(BigInteger.valueOf(amount));
                row.put("fixturePrefillWithdrawn",amount);
            }
            tickJob();
            if(current.category().equals("direct")) {
                require(job.finished(),"Direct AE long transfer must finish in one actual job tick");completeCase();return;
            }
            if(phase<2) {
                require(!job.finished(),"Blocked case must not finish before its resource/capacity is restored");
                if(current.category().equals("partialMust")) {
                    require(count(source)==0&&count(destination)==17,"MUST partial transfer must commit exactly 17 units once");
                    require(buffered(job.save())==0,"MUST partial transfer must not duplicate its committed output");
                    var continuation=GSON.fromJson(job.save().continuation(),FactoryMachine.Snapshot.class);
                    require(continuation.pendingTransfer()&&continuation.transferRemaining()==current.quantity()-17,"MUST continuation must preserve the exact large remaining amount");
                    var routes=GSON.fromJson(job.save().routes(),FactoryRoutes.Source[].class);
                    require(routes.length==1&&routes[0].must()&&routes[0].remaining()==current.quantity()-17,"MUST declaration must retain its exact remaining amount");
                    row.put("persistedMustRemaining",continuation.transferRemaining());
                } else {
                    require(count(source)==0&&count(destination)==Long.MAX_VALUE,"Saturated destination must accept only its 17 free units");
                    require(sum(job.save().output())==current.quantity()-17,"Unreturned output must remain owned by the production job");
                    require(destination.insert(current.resource().key(),1,Actionable.SIMULATE,action())==0,"Full real cell must reject simulated overflow");
                    require(destination.insert(current.resource().key(),1,Actionable.MODULATE,action())==0,"Full real cell must reject committed overflow");
                    require(count(destination)==Long.MAX_VALUE,"Rejected overflow must not wrap or mutate the real cell");
                    row.put("saturatedCapacity",Long.MAX_VALUE);row.put("persistedUnreturnedOutput",sum(job.save().output()));
                }
                restoreJob();checkLedger();cellRoundTrip(sourceDrive,count(source));cellRoundTrip(destinationDrive,count(destination));
                phase++;return;
            }
            require(job.finished(),"Restored blocked job must finish on the first subsequent available tick");completeCase();
        } catch(Throwable failure){finish(failure);}
    }

    private static String gridId(Object grid){return grid==null?"none":grid.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(grid));}

    private static void checkQuantitySyntax() {
        String suffix=" minecraft::item from storage\nput minecraft::item into source\ndone";
        require(FactoryCompiler.compile("get must "+Long.MAX_VALUE+suffix,false).instructions().getFirst().amount()==Long.MAX_VALUE,"Exact Long.MAX_VALUE quantity must compile without narrowing");
        boolean rejected=false;
        try {FactoryCompiler.compile("get must 9223372036854775808"+suffix,false);}
        catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"Quantity above Long.MAX_VALUE must be rejected before mutation");
        evidence.put("compilerAcceptsExactLongMax",true);evidence.put("compilerRejectsLongOverflow",true);
    }
    private static void beginCase(Case next) throws IOException {
        clear(source);clear(destination);
        current=next;phase=0;jobTicks=0;caseCodecs=0;jobNanos=0;codecNanos=0;
        supplied=BigInteger.ZERO;withdrawn=BigInteger.ZERO;caseStartedNanos=System.nanoTime();
        row=new LinkedHashMap<>();cases.add(row);tickMovements=new ArrayList<>();
        row.put("category",next.category());row.put("resource",next.resource().label());
        row.put("keyType",next.resource().key().getType().getId().toString());row.put("unit",next.resource().unit());
        row.put("requestedQuantity",next.quantity());row.put("requestedQuantityDecimal",Long.toString(next.quantity()));
        row.put("tickMovements",tickMovements);row.put("status","running");
        seed(source,next.category().equals("partialMust")?17:next.quantity());
        if(next.category().equals("saturatedReturn"))seed(destination,Long.MAX_VALUE-17);
        row.put("initialFixtureSupplyDecimal",supplied.toString());
        cellRoundTrip(sourceDrive,count(source));cellRoundTrip(destinationDrive,count(destination));
        String code="get must "+next.quantity()+" "+next.resource().selector()+" from storage\nput "+next.resource().selector()+" into source\ndone";
        long begin=System.nanoTime();
        var program=FactoryCompiler.compile(code,false);
        require(program.instructions().stream().map(FactoryProgram.Instruction::op).toList().equals(List.of(FactoryProgram.Op.GET,FactoryProgram.Op.PUT,FactoryProgram.Op.DONE,FactoryProgram.Op.DONE)),"Transfer program must retain three source operations plus the compiler's implicit terminal DONE");
        job=new FactoryJob(host,new FactoryPatternData(code,host.factoryId(),ItemStack.EMPTY));
        row.put("compileAndCreateMillis",(System.nanoTime()-begin)/1_000_000.0);
        row.put("compiledInstructions",program.instructions().size());row.put("code",code);checkLedger();
    }
    private static void clear(MEStorage inventory) {
        var existing=new ArrayList<GenericStack>();
        for(var entry:inventory.getAvailableStacks())existing.add(new GenericStack(entry.getKey(),entry.getLongValue()));
        for(var entry:existing)require(inventory.extract(entry.what(),entry.amount(),Actionable.MODULATE,action())==entry.amount(),"Previous case must be completely cleared through the real storage API");
        require(inventory.getAvailableStacks().isEmpty(),"Each quantity case must begin with an empty actual cell ledger");
    }
    private static void seed(MEStorage inventory,long amount) {
        require(inventory.insert(current.resource().key(),amount,Actionable.MODULATE,action())==amount,"Real network failed to accept the explicitly recorded fixture supply");
        supplied=supplied.add(BigInteger.valueOf(amount));
    }
    private static long count(MEStorage inventory) {
        long amount=inventory.extract(current.resource().key(),Long.MAX_VALUE,Actionable.SIMULATE,action());
        require(amount>=0,"Actual AE storage amount must not overflow negative");
        require(inventory.getAvailableStacks().get(current.resource().key())==amount,"Advertised ME count must equal actual extractable amount");
        for(var entry:inventory.getAvailableStacks())require(entry.getKey().equals(current.resource().key()),"Unexpected cross-case resource in a real network");
        return amount;
    }
    private static long sum(List<GenericStack> values) {
        long amount=0;
        for(var stack:values) {
            require(stack.what().equals(current.resource().key())&&stack.amount()>0,"Job must own only positive amounts of this case's key");
            amount=Math.addExact(amount,stack.amount());
        }
        return amount;
    }
    private static long buffered(FactoryJob.Saved saved){return Math.addExact(sum(saved.input()),sum(saved.output()));}
    private static void checkLedger() {
        var saved=job.save();
        BigInteger physical=BigInteger.valueOf(count(source)).add(BigInteger.valueOf(count(destination)))
                .add(BigInteger.valueOf(sum(saved.input()))).add(BigInteger.valueOf(sum(saved.output())));
        require(physical.add(withdrawn).equals(supplied),"Real network plus job buffers must conserve every supplied unit: physical="+physical+", withdrawn="+withdrawn+", supplied="+supplied);
        require(saved.expected().isEmpty()&&saved.nativeRecovery().isEmpty(),"Recipe-free AE storage test must not create output debt or native recovery records");
        row.put("suppliedDecimal",supplied.toString());row.put("withdrawnDecimal",withdrawn.toString());
        row.put("physicalTotalDecimal",physical.toString());row.put("conserved",true);
    }
    private static void tickJob() {
        long sourceBefore=count(source),destinationBefore=count(destination),outputBefore=sum(job.save().output());
        long begin=System.nanoTime();job.tick();long elapsed=System.nanoTime()-begin;jobNanos+=elapsed;jobTicks++;
        require(job.error().isEmpty(),"Production job failed: "+job.error());checkLedger();
        var movement=new LinkedHashMap<String,Object>();
        movement.put("serverTick",ticks);movement.put("jobTick",jobTicks);
        movement.put("sourceExtracted",Math.subtractExact(sourceBefore,count(source)));
        movement.put("destinationInserted",Math.subtractExact(count(destination),destinationBefore));
        movement.put("outputBufferBefore",outputBefore);movement.put("outputBufferAfter",sum(job.save().output()));
        movement.put("elapsedMillis",elapsed/1_000_000.0);tickMovements.add(movement);
    }
    /** Same production codec as block persistence, across actual binary NBT bytes rather than a Gson Object tree. */
    private static void restoreJob() throws IOException {
        long begin=System.nanoTime();var original=job.save();var restored=binaryRoundTrip(FactoryJob.Saved.CODEC,original);
        require(GSON.fromJson(original.continuation(),FactoryMachine.Snapshot.class).equals(GSON.fromJson(restored.continuation(),FactoryMachine.Snapshot.class)),"NBT job continuation must retain exact typed long fields");
        require(Arrays.equals(GSON.fromJson(original.routes(),FactoryRoutes.Source[].class),GSON.fromJson(restored.routes(),FactoryRoutes.Source[].class)),"NBT source routes must retain exact typed long fields");
        require(original.input().equals(restored.input())&&original.output().equals(restored.output()),"NBT job buffers must retain exact keys and long amounts");
        job=FactoryJob.restore(host,restored);codecNanos+=System.nanoTime()-begin;caseCodecs++;totalCodecs++;
    }
    private static <T> T binaryRoundTrip(Codec<T> codec,T original) throws IOException {
        var ops=level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded=codec.encodeStart(ops,original).getOrThrow();
        require(encoded instanceof CompoundTag,"Expected a compound NBT root");
        var bytes=new ByteArrayOutputStream();NbtIo.write((CompoundTag)encoded,new DataOutputStream(bytes));
        var decoded=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        return codec.parse(ops,decoded).getOrThrow();
    }
    private static void cellRoundTrip(DriveBlockEntity drive,long expected) throws IOException {
        var actual=drive.getInternalInventory().getStackInSlot(0);
        var restored=binaryRoundTrip(ItemStack.CODEC,actual);
        var storage=StorageCells.getCellInventory(restored,null);
        require(storage!=null&&storage.getAvailableStacks().get(current.resource().key())==expected,"Installed Loop cell contents must survive binary NBT with exact large counts");
        long originalCount=StorageCells.getCellInventory(actual.copy(),null).getAvailableStacks().get(current.resource().key());
        require(originalCount==expected,"Actual installed cell components must persist the live network count");cellCodecs++;
    }
    private static void completeCase() throws IOException {
        require(count(source)==0&&count(destination)==current.quantity(),"Completed job must move exactly the requested amount to the distinct destination network");
        require(buffered(job.save())==0,"Completed job must leave zero buffered resources");
        restoreJob();require(job.finished(),"Finished continuation must stay finished after binary NBT restore");
        checkLedger();cellRoundTrip(sourceDrive,0);cellRoundTrip(destinationDrive,current.quantity());
        row.put("status","passed");row.put("actualMoved",count(destination));row.put("actualMovedDecimal",Long.toString(count(destination)));
        row.put("finalSource",count(source));row.put("finalDestination",count(destination));row.put("finalJobBuffers",buffered(job.save()));
        row.put("jobTicks",jobTicks);row.put("codecRestores",caseCodecs);row.put("jobMillis",jobNanos/1_000_000.0);
        row.put("codecMillis",codecNanos/1_000_000.0);row.put("wallClockSeconds",(System.nanoTime()-caseStartedNanos)/1_000_000_000.0);
        current=null;job=null;caseIndex++;
        if(caseIndex==plan.size())finish(null);
    }
    private static void finish(Throwable failure) {
        passed=failure==null;
        if(failure!=null&&row!=null){row.put("status","failed");row.put("failure",failure.toString());row.put("jobTicks",jobTicks);}
        var report=new LinkedHashMap<String,Object>();report.put("generation",GENERATION);report.put("status",passed?"passed":"failed");
        report.put("targetCases",21);report.put("completedCases",caseIndex);report.put("serverTicks",ticks);report.put("timeoutServerTicks",timeoutTicks());
        report.put("configuredTickRate",configuredTickRate);report.put("wallClockSeconds",(System.nanoTime()-startedNanos)/1_000_000_000.0);
        report.put("jobCodecRestores",totalCodecs);report.put("cellBinaryNbtRoundTrips",cellCodecs);
        var categories=new LinkedHashMap<String,Long>();
        for(String category:List.of("direct","partialMust","saturatedReturn"))categories.put(category,cases.stream().filter(c->category.equals(c.get("category"))&&"passed".equals(c.get("status"))).count());
        report.put("categoryCounts",categories);report.put("evidence",evidence);report.put("cases",cases);
        report.put("scope","Real independent powered AE2 networks with installed infinite Loop cells; production FactoryJob storage-to-source route. These are standalone recipe-free jobs, not CPU crafting requests. Fixture supplies are inserted through the actual ME API and fully counted. No per-item simulation, fake inventories, or native capability bypass.");
        report.put("nativeCapabilityBoundary","This proves long-count AE storage logistics. Native item, fluid, and FE machine handlers can impose int-sized calls or smaller capacity; a native bucket or battery is not claimed to hold these quantities.");
        report.put("performanceBoundary","One production job tick per actual server tick; direct cases require three source operations (four compiled instructions including the implicit terminal DONE) and one job tick regardless of quantity. Wall timings include JVM warmup/noise and do not prove world TPS, native-machine throughput, or long-duration leak freedom.");
        report.put("fixtureFinalState","Each new case clears only the previous case's two test inventories through the actual ME API; the final completed case's destination contents remain in the real installed cell for normal world saving.");
        if(failure!=null)report.put("failure",failure.toString());
        try {Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("huge-quantity-report.json"),GSON.toJson(report));}
        catch(IOException error){throw new UncheckedIOException(error);}
        finally {if(level!=null)level.getServer().tickRateManager().setTickRate(20);finished=true;}
    }
}
