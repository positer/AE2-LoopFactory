package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import appeng.api.config.*;
import appeng.api.crafting.*;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.*;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.helpers.MachineSource;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import java.nio.file.*;
import java.util.*;

/** Same-subnet simultaneous recipe orders through unmodified vanilla blast furnaces. */
public final class ParallelOrderAudit {
 public static volatile boolean finished,passed;
 public static final BlockPos POS=new BlockPos(624,100,144);
 public static final List<BlockPos> machines=new ArrayList<>();
 private static final Map<String,Object> evidence=new LinkedHashMap<>();
 private static final List<Map<String,Object>> orders=new ArrayList<>();
 private static final Set<Integer> touched=new HashSet<>();
 private static ServerLevel level;
 private static FactoryBlockEntity provider;
 private static int ticks,order,phase,started,maxJobs,blockedTicks,lastProgress;
 private static String progressSignature="";
 private static long feReceived;
 private static java.util.concurrent.Future<ICraftingPlan> calculation;
 private static int batches=12,configuredTickRate=20;
 private static long startedNanos;
 public static int timeoutTicks(){return Math.max(4000,1000+batches*400);}
 private static final String[] METALS={"iron","gold","copper"};
 private static final int INITIAL_COAL_PER_FURNACE=64;
 private static final Item[] RAW={Items.RAW_IRON,Items.RAW_GOLD,Items.RAW_COPPER};
 private static final Item[] OUTPUT={Items.IRON_INGOT,Items.GOLD_INGOT,Items.COPPER_INGOT};
 private static final List<java.util.concurrent.Future<ICraftingPlan>> plans=new ArrayList<>();
 private static final Map<FactoryJob,Integer> seen=new IdentityHashMap<>();
 private static final Set<FactoryJob> previous=Collections.newSetFromMap(new IdentityHashMap<>());
 private static final int[] admitted=new int[3],completed=new int[3],peak=new int[3];
 private static final List<Map<String,Object>> timeline=new ArrayList<>();
 private static final List<Map<String,Object>> readinessObservations=new ArrayList<>();
 private static final Set<Long> forcedChunks=new LinkedHashSet<>();
 private static boolean mixedActive,returnedButRunning;
 @SuppressWarnings("unchecked") private static List<FactoryJob> jobs()throws Exception {
  var field=FactoryProviderLogic.class.getDeclaredField("jobs");field.setAccessible(true);return (List<FactoryJob>)field.get(provider.getLogic());
 }
 private static void counters()throws Exception {
  var current=jobs();int[] active=new int[3];
  for(var job:current) {
   Integer kind=seen.get(job);
   if(kind==null) {
    var parameters=job.save().parameters();check(parameters.size()==1,"each_job_owns_one_input_parameter");
    kind=-1;for(int i=0;i<3;i++)if(parameters.getFirst().getFirst().what().equals(AEItemKey.of(RAW[i])))kind=i;
    check(kind>=0&&parameters.getFirst().getFirst().amount()==1,"exact_per_job_input_identity_and_count");
    seen.put(job,kind);admitted[kind]++;
   }
   active[kind]++;if(!job.awaitingPrimaryReturn()&&!job.finished())returnedButRunning=true;
  }
  for(var job:previous)if(!current.contains(job)){check(job.finished(),"removed_jobs_are_fully_settled");completed[seen.get(job)]++;}
  previous.clear();previous.addAll(current);
  for(int i=0;i<3;i++){check(admitted[i]-completed[i]==active[i]&&admitted[i]<=batches,"counter_conservation_"+METALS[i]);peak[i]=Math.max(peak[i],active[i]);}
  check(current.size()<=16,"shared_provider_parallel_cap_16");
  maxJobs=Math.max(maxJobs,current.size());mixedActive|=active[0]>0&&active[1]>0&&active[2]>0;
  if(ticks%100==0)timeline.add(Map.of("tick",ticks,"admitted",admitted.clone(),"completed",completed.clone(),"active",active));
 }
 private static void check(boolean ok,String key){evidence.put(key,ok);if(!ok)throw new IllegalStateException(key);}
 private static Item item(String id){return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));}
 private static void force(BlockPos p){int x=p.getX()>>4,z=p.getZ()>>4;level.setChunkForced(x,z,true);forcedChunks.add((x&0xffffffffL)|((z&0xffffffffL)<<32));}
 public static void start(ServerLevel world) {
  level=world;startedNanos=System.nanoTime();
  try {
   batches=StressAudit.option("parallelBatches",12,12,128);
   configuredTickRate=StressAudit.configureTickRate(world);
   evidence.put("backend","minecraft:blast_furnace");
   // The power cell is west of the provider's chunk boundary. Load the complete fixture area,
   // including native furnace ticking, before admitting orders; fixed startup ticks are not readiness.
   for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)force(POS.offset(dx*16,0,dz*16));
   force(POS);level.setBlockAndUpdate(POS,FactoryContent.PROVIDER.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.SOUTH));
   provider=(FactoryBlockEntity)level.getBlockEntity(POS);
   level.setBlockAndUpdate(POS.west(),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
   double rejected=((EnergyCellBlockEntity)level.getBlockEntity(POS.west())).injectAEPower(1_000_000,Actionable.MODULATE);
   evidence.put("oneTimeBatteryOfferedAE",1_000_000);evidence.put("oneTimeBatteryAcceptedAE",1_000_000-rejected);evidence.put("oneTimeBatteryRejectedAE",rejected);
   check(rejected==0,"one_time_finite_battery_supply_accepted");
   level.setBlockAndUpdate(POS.east(),AEBlocks.DRIVE.block().defaultBlockState());
   ((DriveBlockEntity)level.getBlockEntity(POS.east())).getInternalInventory().setItemDirect(0,ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());
   for(int i=0;i<3;i++) {
    if(i>0)level.setBlockAndUpdate(POS.east(1+2*i),FactoryContent.CABLE.get().defaultBlockState());
    level.setBlockAndUpdate(POS.east(2+2*i),AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
    level.setBlockAndUpdate(POS.east(2+2*i).above(),AEBlocks.CRAFTING_ACCELERATOR.block().defaultBlockState());
   }
   level.setBlockAndUpdate(POS.south(),FactoryContent.CABLE.get().defaultBlockState());
   for(int i=0;i<8;i++) {
    var cable=POS.south().below().east(i);force(cable);level.setBlockAndUpdate(cable,FactoryContent.CABLE.get().defaultBlockState());
    var pos=cable.south();force(pos);String id="minecraft:blast_furnace";
    var block=BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));check(block!=net.minecraft.world.level.block.Blocks.AIR,"registered_"+id);
    level.setBlockAndUpdate(pos,block.defaultBlockState());machines.add(pos);var furnace=(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)level.getBlockEntity(pos);furnace.setItem(0,new ItemStack(Items.COBBLESTONE,64));furnace.setItem(1,new ItemStack(Items.COAL,INITIAL_COAL_PER_FURNACE));
    check(furnace.getItem(0).is(Items.COBBLESTONE)&&furnace.getItem(0).getCount()==64&&furnace.getItem(1).is(Items.COAL)&&furnace.getItem(1).getCount()==INITIAL_COAL_PER_FURNACE&&furnace.getItem(2).isEmpty(),"fresh_furnace_exact_initial_blocker_and_finite_fuel_"+i);
   }
   // Native group routing may keep returning to its first available furnace. Budget each
   // furnace for that skew instead of dividing the requested orders evenly across eight.
   int burnPerCoal=new ItemStack(Items.COAL).getBurnTime(net.minecraft.world.item.crafting.RecipeType.BLASTING,level.fuelValues())/2;
   evidence.put("finiteFuelBudget",Map.of("furnaces",machines.size(),"coalPerFurnace",INITIAL_COAL_PER_FURNACE,"initialCoalTotal",machines.size()*INITIAL_COAL_PER_FURNACE,"nativeBurnTicksPerCoal",burnPerCoal,"nativeBurnTicksPerFurnace",1L*burnPerCoal*INITIAL_COAL_PER_FURNACE,"nativeBurnTicksTotal",1L*burnPerCoal*INITIAL_COAL_PER_FURNACE*machines.size(),"requestedRecipeCount",3*batches));
   evidence.put("fuelPolicy","One initial stack of 64 coal in each native blast furnace; no runtime fuel insertion. Native coal duration uses the pinned blast-furnace half-duration override.");
   evidence.put("initialNativeFurnaces",furnaceStates());
  }catch(Throwable e){finish(e);}
 }
 private static BlockEntity loaded(BlockPos pos){var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk==null?null:chunk.getBlockEntities().get(pos);}
 private static List<Map<String,Object>> furnaceStates() {
  var result=new ArrayList<Map<String,Object>>();
  for(var pos:machines){var state=new LinkedHashMap<String,Object>();state.put("position",pos.toShortString());var block=loaded(pos);state.put("nativeFurnacePresent",block instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity);
   if(block instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace){var slots=new ArrayList<Map<String,Object>>();for(int slot=0;slot<3;slot++){var stack=furnace.getItem(slot);slots.add(Map.of("slot",slot,"item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),"count",stack.getCount()));}state.put("slots",slots);var nbt=furnace.saveWithoutMetadata(level.registryAccess());state.put("litTimeRemaining",nbt.getIntOr("lit_time_remaining",0));state.put("litTotalTime",nbt.getIntOr("lit_total_time",0));state.put("cookingTimeSpent",nbt.getIntOr("cooking_time_spent",0));state.put("cookingTotalTime",nbt.getIntOr("cooking_total_time",0));state.put("nativeNbt",nbt.toString());}
   result.add(state);
  }return result;
 }
 private static String gridId(Object grid){return grid==null?"none":grid.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(grid));}
 private static Map<String,Object> nativeState() {
  var state=new LinkedHashMap<String,Object>();state.put("serverTick",ticks);
  var main=provider.getMainNode().getGrid();var subnet=provider.factoryGrid();
  state.put("mainGrid",gridId(main));state.put("subnetGrid",gridId(subnet));state.put("distinctGrids",main!=null&&subnet!=null&&main!=subnet);
  state.put("providerNodeReady",provider.getMainNode().isReady());state.put("providerActive",provider.getMainNode().isActive());state.put("providerIsolated",provider.isolated());state.put("providerOwnsSubnet",subnet!=null&&FactoryServer.owner(subnet)==provider);
  state.put("mainPowered",main!=null&&main.getEnergyService().isNetworkPowered());state.put("subnetPowered",subnet!=null&&subnet.getEnergyService().isNetworkPowered());
  if(main!=null){var energy=main.getEnergyService();state.put("mainStoredAE",energy.getStoredPower());state.put("mainIdleAEPerTick",energy.getIdlePowerUsage());state.put("mainChannelAEPerTick",energy.getChannelPowerUsage());}
  if(subnet!=null){var energy=subnet.getEnergyService();state.put("subnetStoredAE",energy.getStoredPower());state.put("subnetIdleAEPerTick",energy.getIdlePowerUsage());state.put("subnetChannelAEPerTick",energy.getChannelPowerUsage());}
  var battery=loaded(POS.west());state.put("batteryPresent",battery instanceof EnergyCellBlockEntity);
  if(battery instanceof EnergyCellBlockEntity cell){state.put("batteryNodeReady",cell.getMainNode().isReady());state.put("batteryOnMainGrid",main!=null&&cell.getMainNode().getGrid()==main);state.put("batteryStoredAE",cell.getAECurrentPower());}
  var disk=loaded(POS.east());state.put("driveReadyOnMainGrid",disk instanceof DriveBlockEntity drive&&drive.getMainNode().isActive()&&main!=null&&drive.getMainNode().getGrid()==main);
  var chunks=new ArrayList<Map<String,Object>>();boolean chunksReady=true;
  for(long key:forcedChunks){int x=(int)key,z=(int)(key>>32);boolean chunkNow=level.getChunkSource().getChunkNow(x,z)!=null,entitiesLoaded=level.areEntitiesLoaded(key),entityTicking=level.isPositionEntityTicking(new BlockPos(x*16+8,POS.getY(),z*16+8));chunksReady&=chunkNow&&entitiesLoaded&&entityTicking;chunks.add(Map.of("chunkX",x,"chunkZ",z,"chunkNow",chunkNow,"entitiesLoaded",entitiesLoaded,"entityTicking",entityTicking));}
  state.put("fixtureChunks",chunks);state.put("fixtureChunksReady",chunksReady);
  var cpuStates=new ArrayList<Map<String,Object>>();var clusters=new HashSet<Object>();boolean cpusReady=true;
  for(int i=0;i<3;i++){var pos=POS.east(2+2*i);var block=loaded(pos);var sample=new LinkedHashMap<String,Object>();sample.put("position",pos.toShortString());boolean ready=false;if(block instanceof CraftingBlockEntity cpu){var cluster=cpu.getCluster();sample.put("formed",cluster!=null);sample.put("active",cpu.getMainNode().isActive());sample.put("onMainGrid",main!=null&&cpu.getMainNode().getGrid()==main);if(cluster!=null)clusters.add(cluster);ready=cluster!=null&&cpu.getMainNode().isActive()&&main!=null&&cpu.getMainNode().getGrid()==main;}sample.put("ready",ready);cpusReady&=ready;cpuStates.add(sample);}
  state.put("cpus",cpuStates);state.put("threeDistinctCpusReady",cpusReady&&clusters.size()==3);
  state.put("activeJobs",provider.getLogic().saveJobs().size());state.put("installedPatternCount",java.util.stream.IntStream.range(0,provider.getLogic().getPatternInv().size()).filter(i->!provider.getLogic().getPatternInv().getStackInSlot(i).isEmpty()).count());
  return state;
 }
 private static boolean fixtureReady() {
  var state=nativeState();boolean ready=true;
  for(String key:List.of("distinctGrids","providerNodeReady","providerActive","providerIsolated","providerOwnsSubnet","mainPowered","subnetPowered","batteryPresent","batteryNodeReady","batteryOnMainGrid","driveReadyOnMainGrid","fixtureChunksReady","threeDistinctCpusReady"))ready&=Boolean.TRUE.equals(state.get(key));
  state.put("ready",ready);evidence.put("latestNativeReadiness",state);
  if(ready||ticks%10==0)readinessObservations.add(state);
  if(ready){evidence.put("nativeReadinessAtServerTick",ticks);evidence.put("nativeReadinessBeforeOrders",state);}
  return ready;
 }
 private static long stored(Item item) {
  long count=0;for(int m=0;m<machines.size();m++) {
   var handler=level.getCapability(Capabilities.Item.BLOCK,machines.get(m),null);
   if(handler==null)throw new IllegalStateException("Native furnace null-face item handler missing at "+m);
   for(int s=0;s<handler.size();s++)if(handler.getResource(s).is(item)){count+=handler.getAmountAsLong(s);touched.add(m);}
  }return count;
 }
 private static void powerMachines(int age) {
  if(age==200)for(var pos:machines)((net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)level.getBlockEntity(pos)).setItem(0,ItemStack.EMPTY);
 }
 private static String code(int kind) {
  return "import Smelt\nget 1 minecraft:diamond from Smelt\nput 1 minecraft:diamond into Smelt\nget must 1 P1 from source\nput must 1 P1 into Smelt\nget must 1 O1 from Smelt\nput must 1 O1 into source\nwait 5 tick\ndone";
 }
 public static void tick() {
  if(level==null||finished)return;
  try {
   ticks++;
   if(ticks>timeoutTicks())throw new IllegalStateException("ParallelOrderAudit timeout at phase "+phase);
   if(phase>0&&phase<3&&ticks-started>3000)throw new IllegalStateException("Native CPU calculation/submission stalled at phase "+phase);
   if(ticks<100)return;
   if(phase==0&&!fixtureReady()){if(ticks>=600)throw new IllegalStateException("Parallel fixture did not become natively powered, isolated, and fully loaded within 600 server ticks");return;}
   var grid=provider.getMainNode().getGrid();var inv=grid.getStorageService().getInventory();var action=new MachineSource(grid::getPivot);var logic=provider.getLogic();
   var cpus=new ArrayList<appeng.me.cluster.implementations.CraftingCPUCluster>();
   for(int i=0;i<3;i++) {
    var cpu=((CraftingBlockEntity)level.getBlockEntity(POS.east(2+2*i))).getCluster();
    if(cpu==null){if(ticks>300)throw new IllegalStateException("Native CPUs did not form");return;}cpus.add(cpu);
   }
   check(new HashSet<>(cpus).size()==3,"three_distinct_cpus_same_main_grid");
   if(phase==0) {
    check(provider.getMainNode().isActive()&&provider.factoryGrid()!=grid,"powered_isolated_subnet");
    check(logic.saveJobs().isEmpty()&&logic.getPatternInv().isEmpty()&&inv.getAvailableStacks().isEmpty(),"fresh_fixture_has_no_prior_orders_patterns_or_inventory");
    provider.tags.reconcile(List.of("Smelt"));
    for(int i=0;i<8;i++)check(provider.tags.tag("Smelt",machines.get(i).asLong(),p->FactoryServer.contains(provider.factoryGrid(),BlockPos.of(p))),"native_group_binding_"+i);
    for(int i=0;i<3;i++) {
     var recipe=PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(RAW[i]),1)),List.of(new GenericStack(AEItemKey.of(OUTPUT[i]),1)));
     var pattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();pattern.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(code(i),provider.factoryId(),recipe));
     check(logic.accepts(pattern),"pattern_accepted_"+METALS[i]);logic.getPatternInv().setItemDirect(i,pattern);
     check(inv.insert(AEItemKey.of(RAW[i]),batches,Actionable.MODULATE,action)==batches,"exact_inputs_"+METALS[i]);
    }
    logic.getConfigManager().putSetting(Settings.BLOCKING_MODE,YesNo.NO);logic.updatePatterns();
    started=ticks;lastProgress=ticks;phase=1;return;
   }
   int age=ticks-started;powerMachines(age);
   if(phase==1) {
    if(age<10)return;
    for(int i=0;i<3;i++)plans.add(grid.getCraftingService().beginCraftingCalculation(level,()->action,AEItemKey.of(OUTPUT[i]),1L*batches,CalculationStrategy.REPORT_MISSING_ITEMS));
    phase=2;return;
   }
   if(phase==2) {
    if(plans.stream().anyMatch(p->!p.isDone()))return;
    for(int i=0;i<3;i++) {
     var plan=plans.get(i).get();check(plan!=null&&!plan.simulation()&&plan.missingItems().isEmpty(),"real_plan_"+METALS[i]);
     check(grid.getCraftingService().submitJob(plan,null,cpus.get(i),false,action).successful(),"concurrent_real_cpu_submit_"+METALS[i]);
    }
    phase=3;return;
   }
   counters();check(logic.executionError().isEmpty(),"no_execution_error");
   long raw=0,dust=0,out=0;long[] returned=new long[3];boolean settled=true;
   for(int i=0;i<3;i++) {
    raw+=stored(RAW[i]);out+=stored(OUTPUT[i]);
    returned[i]=inv.getAvailableStacks().get(AEItemKey.of(OUTPUT[i]));check(returned[i]<=1L*batches,"no_excess_output_"+METALS[i]);
    settled&=returned[i]==1L*batches&&!cpus.get(i).isBusy();
   }
   if(age==150)check(maxJobs==16&&Arrays.stream(returned).sum()==0&&jobs().stream().mapToLong(j->j.save().input().stream().mapToLong(GenericStack::amount).sum()).sum()==16,"blocked_inputs_remain_in_each_admitted_job");
   if(age>60&&age<190&&!jobs().isEmpty())blockedTicks++;
   if(settled&&jobs().isEmpty()) {
    check(raw==0&&dust==0&&out==0,"all_furnace_inputs_outputs_settled");check(maxJobs==16,"parallel_cap_reached");
    check(mixedActive,"three_order_types_simultaneously_active");check(returnedButRunning,"primary_return_keeps_code_tail_active");
    check(blockedTicks>100,"sustained_processing_backpressure");
    for(int i=0;i<3;i++) {
     check(admitted[i]==batches&&completed[i]==batches,"all_batches_counted_"+METALS[i]);
     check(inv.getAvailableStacks().get(AEItemKey.of(RAW[i]))==0,"raw_consumed_"+METALS[i]);
     orders.add(Map.of("resource",METALS[i],"batches",batches,"output",returned[i],"blocking",false,"admitted",admitted[i],"completed",completed[i],"active",0,"peakJobs",peak[i],"code",code(i)));
    }
    finish(null);return;
   }
   if(ticks%100==0) {
    evidence.put("status","running");evidence.put("ticks",ticks);evidence.put("machineRaw",raw);evidence.put("machineDust",dust);evidence.put("machineOutput",out);
    evidence.put("returned",returned);evidence.put("admitted",admitted);evidence.put("completed",completed);evidence.put("active",jobs().size());evidence.put("peakJobs",maxJobs);
    Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("parallel-orders-progress.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
   }
   String current=raw+":"+dust+":"+out+":"+Arrays.toString(returned)+":"+Arrays.toString(completed);
   if(!current.equals(progressSignature)){progressSignature=current;lastProgress=ticks;}
   if(ticks-lastProgress>3000)throw new IllegalStateException("Native furnace parallel orders made no progress for 3000 ticks");

  }catch(Throwable e){finish(e);}
 }
 private static void finish(Throwable e) {
  finished=true;passed=e==null;evidence.put("status",passed?"passed":"failed");evidence.put("ticks",ticks);evidence.put("orders",orders);evidence.put("feReceived",feReceived);evidence.put("timeline",timeline);evidence.put("peakJobs",maxJobs);evidence.put("machinesUsed",touched.size());
  evidence.put("configuredTickRate",configuredTickRate);evidence.put("wallClockSeconds",(System.nanoTime()-startedNanos)/1_000_000_000.0);
  evidence.put("targetBatchesPerOrder",batches);evidence.put("targetCpuOrders",3);evidence.put("targetTotalBatches",3*batches);evidence.put("targetTotalOutput",3L*batches);evidence.put("timeoutServerTicks",timeoutTicks());
  evidence.put("scope","Three real concurrent AE2 CPU orders with per-resource admitted/completed/active conservation; outputs come from native machines; configured tick rate is a scheduler target");
  evidence.put("readinessObservations",readinessObservations);evidence.put("readinessTimeoutServerTicks",600);evidence.put("batteryRefillsAfterStart",0);evidence.put("fuelRefillsAfterStart",0);
  if(level!=null&&provider!=null)try{evidence.put("finalNativeState",nativeState());}catch(Throwable diagnostic){evidence.put("finalNativeStateFailure",diagnostic.toString());}
  if(level!=null)try{evidence.put("finalNativeFurnaces",furnaceStates());}catch(Throwable diagnostic){evidence.put("finalNativeFurnacesFailure",diagnostic.toString());}
  if(passed) {
   evidence.put("active",0);evidence.put("returned",orders.stream().map(row->row.get("output")).toList());
   evidence.put("admitted",admitted);evidence.put("completed",completed);
   evidence.put("machineRaw",0);evidence.put("machineDust",0);evidence.put("machineOutput",0);
   timeline.add(Map.of("tick",ticks,"admitted",admitted.clone(),"completed",completed.clone(),"active",new int[3]));
  }
  if(e!=null){evidence.put("failure",e.toString());evidence.put("stack",Arrays.toString(e.getStackTrace()));try{evidence.put("jobs",provider.getLogic().saveJobs().stream().map(j->j.continuation()).toList());}catch(Exception ignored){}}
  try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("parallel-orders-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}catch(Exception x){throw new RuntimeException(x);}finally{if(level!=null)level.getServer().tickRateManager().setTickRate(20);}
 }
}
