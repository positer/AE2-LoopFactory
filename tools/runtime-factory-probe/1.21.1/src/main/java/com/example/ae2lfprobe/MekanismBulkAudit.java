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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import java.nio.file.*;
import java.util.*;

/** Optional MEK integration fixture. No MEK classes/dependencies enter the production mod. */
public final class MekanismBulkAudit {
 public static volatile boolean finished,passed;
 public static final BlockPos POS=new BlockPos(640,100,80);
 public static final List<BlockPos> machines=new ArrayList<>();
 private static final Map<String,Object> evidence=new LinkedHashMap<>();
 private static final List<Map<String,Object>> orders=new ArrayList<>();
 private static final Set<Integer> touched=new HashSet<>();
 private static ServerLevel level;
 private static FactoryBlockEntity provider;
 private static int ticks,order,phase,started,maxJobs,blockedTicks,lastProgress;
 private static String progressSignature="";
 private static long feReceived;
 private static final int AE_BATTERY_COUNT=8;
 private static final double AE_INITIAL_CHARGE_PER_CELL=1_000_000;
 private static java.util.concurrent.Future<ICraftingPlan> calculation;
 private static int batches=96,configuredTickRate=20;
 private static long startedNanos;
 public static int timeoutTicks(){return Math.max(24000,1000+batches*200);}
 private static final String[] METALS={"iron","gold","copper"};
 private static final Item[] RAW={Items.RAW_IRON_BLOCK,Items.RAW_GOLD_BLOCK,Items.RAW_COPPER_BLOCK};
 private static final Item[] OUTPUT={Items.IRON_INGOT,Items.GOLD_INGOT,Items.COPPER_INGOT};
 private static final List<java.util.concurrent.Future<ICraftingPlan>> plans=new ArrayList<>();
 private static final Map<FactoryJob,Integer> seen=new IdentityHashMap<>();
 private static final Set<FactoryJob> previous=Collections.newSetFromMap(new IdentityHashMap<>());
 private static final int[] admitted=new int[3],completed=new int[3],peak=new int[3];
 private static final List<Map<String,Object>> timeline=new ArrayList<>();
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
 private static Item item(String id){return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));}
 private static void force(BlockPos p){level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);}
 @SuppressWarnings({"unchecked","rawtypes"})
 private static void upgrades(BlockEntity be)throws Exception {
  Class type=Class.forName("mekanism.api.Upgrade");Object component=be.getClass().getMethod("getComponent").invoke(be);
  if(!(boolean)be.getClass().getMethod("isSorting").invoke(be))be.getClass().getMethod("toggleSorting").invoke(be);
  check((boolean)be.getClass().getMethod("isSorting").invoke(be),"sorting_"+be.getBlockPos());
  for(String name:List.of("SPEED","ENERGY")) {
   Object upgrade=Enum.valueOf(type,name);component.getClass().getMethod("addUpgrades",type,int.class).invoke(component,upgrade,8);
   check((int)component.getClass().getMethod("getUpgrades",type).invoke(component,upgrade)==8,"upgrades_"+be.getBlockPos()+"_"+name);
  }
 }
 public static void start(ServerLevel world) {
  level=world;startedNanos=System.nanoTime();
  try {
   batches=StressAudit.option("mekBatches",96,96,1536);
   configuredTickRate=StressAudit.configureTickRate(world);
   check(net.neoforged.fml.ModList.get().isLoaded("mekanism"),"mekanism_loaded");
   evidence.put("mekanismVersion",net.neoforged.fml.ModList.get().getModContainerById("mekanism").orElseThrow().getModInfo().getVersion().toString());
   force(POS);level.setBlockAndUpdate(POS,FactoryContent.PROVIDER.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.SOUTH));
   provider=(FactoryBlockEntity)level.getBlockEntity(POS);
   var batteries=new ArrayList<Map<String,Object>>();double acceptedTotal=0;
   for(int index=0;index<AE_BATTERY_COUNT;index++) {
    var batteryPos=POS.west().below(index);force(batteryPos);
    level.setBlockAndUpdate(batteryPos,AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
    var battery=(EnergyCellBlockEntity)level.getBlockEntity(batteryPos);
    double accepted=AE_INITIAL_CHARGE_PER_CELL-battery.injectAEPower(AE_INITIAL_CHARGE_PER_CELL,Actionable.MODULATE);
    acceptedTotal+=accepted;
    batteries.add(Map.of("position",batteryPos.toShortString(),"acceptedAE",accepted,"storedAE",battery.getAECurrentPower(),"maximumAE",battery.getAEMaxPower()));
    check(accepted==AE_INITIAL_CHARGE_PER_CELL,"finite_AE_cell_charged_once_"+index);
   }
   evidence.put("finiteAEInitialCharge",batteries);evidence.put("finiteAETotalAccepted",acceptedTotal);
   evidence.put("powerPolicy","AE grid: eight native finite cells charged once, no refill. Mekanism FE: explicit external capability supply after the existing delayed-power checkpoints, recorded in feReceived.");
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
    var pos=cable.south();force(pos);String id=i<4?"mekanism:ultimate_enriching_factory":"mekanism:ultimate_smelting_factory";
    var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));check(block!=net.minecraft.world.level.block.Blocks.AIR,"registered_"+id);
    level.setBlockAndUpdate(pos,block.defaultBlockState());machines.add(pos);initializePlacedMachine(level,pos);upgrades(level.getBlockEntity(pos));
   }
  }catch(Throwable e){finish(e);}
 }
 public static void initializePlacedMachine(ServerLevel world,BlockPos pos) {
  var state=world.getBlockState(pos);var stack=state.getBlock().asItem().getDefaultInstance();
  world.getBlockEntity(pos).applyComponentsFromItemStack(stack);
  state.getBlock().setPlacedBy(world,pos,state,world.getServer().getPlayerList().getPlayers().getFirst(),stack);
 }
 private static long stored(Item item) {
  long count=0;for(int m=0;m<machines.size();m++) {
   var handler=level.getCapability(Capabilities.ItemHandler.BLOCK,machines.get(m),null);
   if(handler==null)throw new IllegalStateException("MEK null-face item handler missing at "+m);
   for(int s=0;s<handler.getSlots();s++)if(handler.getStackInSlot(s).is(item)){count+=handler.getStackInSlot(s).getCount();touched.add(m);}
  }return count;
 }
 private static Map<String,Object> aePowerState() {
  var state=new LinkedHashMap<String,Object>();state.put("gameTime",level.getGameTime());
  if(provider==null){state.put("providerPresent",false);return state;}
  var grid=provider.getMainNode().getGrid();var subnet=provider.factoryGrid();
  state.put("providerActive",provider.getMainNode().isActive());
  state.put("physicalMainSubnetDistinct",grid!=null&&subnet!=null&&grid!=subnet);
  state.put("providerOwnsSubnet",subnet!=null&&FactoryServer.owner(subnet)==provider);
  var cells=new ArrayList<Map<String,Object>>();double remaining=0;boolean connected=grid!=null;
  for(int index=0;index<AE_BATTERY_COUNT;index++) {
   var pos=POS.west().below(index);var row=new LinkedHashMap<String,Object>();row.put("position",pos.toShortString());row.put("chunkLoaded",level.hasChunkAt(pos));
   if(level.getBlockEntity(pos) instanceof EnergyCellBlockEntity cell) {
    boolean ready=cell.getMainNode().isReady()&&cell.getMainNode().getGrid()==grid;
    row.put("readyOnMainGrid",ready);row.put("storedAE",cell.getAECurrentPower());connected&=ready;remaining+=cell.getAECurrentPower();
   }else{row.put("missing",true);connected=false;}
   cells.add(row);
  }
  state.put("finiteCells",cells);state.put("allFiniteCellsOnMainGrid",connected);state.put("remainingFiniteAE",remaining);
  double drain=0;
  if(grid!=null) {
   var energy=grid.getEnergyService();state.put("mainPowered",energy.isNetworkPowered());
   state.put("mainAvailableAE",energy.extractAEPower(1_000_000_000,Actionable.SIMULATE,PowerMultiplier.ONE));
   state.put("mainIdleAEPerTick",energy.getIdlePowerUsage());state.put("mainChannelAEPerTick",energy.getChannelPowerUsage());drain+=energy.getIdlePowerUsage()+energy.getChannelPowerUsage();
  }
  if(subnet!=null) {
   var energy=subnet.getEnergyService();state.put("subnetPowered",energy.isNetworkPowered());
   state.put("subnetIdleAEPerTick",energy.getIdlePowerUsage());state.put("subnetChannelAEPerTick",energy.getChannelPowerUsage());drain+=energy.getIdlePowerUsage()+energy.getChannelPowerUsage();
  }
  state.put("observedIdleAndChannelAEPerTick",drain);
  if(drain>0){state.put("initialChargeTicksAtObservedDrain",AE_BATTERY_COUNT*AE_INITIAL_CHARGE_PER_CELL/drain);state.put("remainingTicksAtObservedDrain",remaining/drain);}
  state.put("driveActiveOnMainGrid",level.getBlockEntity(POS.east()) instanceof DriveBlockEntity drive&&drive.getMainNode().isActive()&&drive.getMainNode().getGrid()==grid);
  return state;
 }
 private static void recordPhysicalStock() {
  var drive=(DriveBlockEntity)level.getBlockEntity(POS.east());
  var cell=appeng.api.storage.StorageCells.getCellInventory(drive.getInternalInventory().getStackInSlot(0),null);
  var stock=new LinkedHashMap<String,Long>();
  if(cell!=null)for(var entry:cell.getAvailableStacks())stock.put(entry.getKey().toString(),entry.getLongValue());
  evidence.put("physicalDriveStockIncludingOfflineCell",stock);
  var cpus=new ArrayList<Map<String,Object>>();
  for(int i=0;i<3;i++) {
   var pos=POS.east(2+2*i);var cpu=((CraftingBlockEntity)level.getBlockEntity(pos)).getCluster();
   if(cpu!=null){var tag=new net.minecraft.nbt.CompoundTag();cpu.writeToNBT(tag,level.registryAccess());cpus.add(Map.of("position",pos.toShortString(),"busy",cpu.isBusy(),"nativeSavedState",tag.toString()));}
  }
  evidence.put("cpuPhysicalState",cpus);
  var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
  evidence.put("fullSavedJobs",provider.getLogic().saveJobs().stream().map(saved->FactoryJob.Saved.CODEC.encodeStart(ops,saved).getOrThrow()).toList());
 }
 private static void powerMachines(int age) {
  for(int m=0;m<machines.size();m++) {
   boolean enabled=age>= (m<4?250:600);
   if(!enabled)continue;
   for(Direction face:Direction.values()) {
    var energy=level.getCapability(Capabilities.EnergyStorage.BLOCK,machines.get(m),face);
    if(energy!=null&&energy.canReceive()){feReceived+=energy.receiveEnergy(Integer.MAX_VALUE,false);break;}
   }
  }
 }
 private static String code(int kind) {
  return "import Enrich,Smelt\nget 1 minecraft:diamond from Enrich\nput 1 minecraft:diamond into Smelt\n"
   +"get must 1 P1 from source\nput must 1 P1 into Enrich\n"
   +"get must 12 mekanism:dust_"+METALS[kind]+" from Enrich\nput must 12 mekanism:dust_"+METALS[kind]+" into Smelt\n"
   +"get must 12 O1 from Smelt\nput must 12 O1 into source\nwait 5 tick\ndone";
 }
 public static void tick() {
  if(level==null||finished)return;
  try {
   ticks++;
   if(ticks>timeoutTicks())throw new IllegalStateException("MekanismBulkAudit timeout at phase "+phase);
   if(phase>0&&phase<3&&ticks-started>3000)throw new IllegalStateException("Native CPU calculation/submission stalled at phase "+phase);
   if(ticks<100)return;
   var grid=provider.getMainNode().getGrid();var inv=grid.getStorageService().getInventory();var action=new MachineSource(grid::getPivot);var logic=provider.getLogic();
   var cpus=new ArrayList<appeng.me.cluster.implementations.CraftingCPUCluster>();
   for(int i=0;i<3;i++) {
    var cpu=((CraftingBlockEntity)level.getBlockEntity(POS.east(2+2*i))).getCluster();
    if(cpu==null){if(ticks>300)throw new IllegalStateException("Native CPUs did not form");return;}cpus.add(cpu);
   }
   check(new HashSet<>(cpus).size()==3,"three_distinct_cpus_same_main_grid");
   if(phase==0) {
    var power=aePowerState();evidence.put("aePowerBeforeOrders",power);
    if(!Boolean.TRUE.equals(power.get("allFiniteCellsOnMainGrid"))&&ticks<300)return;
    check(Boolean.TRUE.equals(power.get("allFiniteCellsOnMainGrid")),"eight_finite_AE_cells_join_actual_main_grid");
    check(Boolean.TRUE.equals(power.get("mainPowered"))&&Boolean.TRUE.equals(power.get("driveActiveOnMainGrid")),"finite_AE_grid_and_drive_powered_before_orders");
    check(power.get("initialChargeTicksAtObservedDrain") instanceof Number budget&&budget.doubleValue()>timeoutTicks(),"finite_AE_charge_covers_full_timeout_at_observed_drain");
    check(provider.getMainNode().isActive()&&provider.factoryGrid()!=grid,"powered_isolated_subnet");
    provider.tags.reconcile(List.of("Enrich","Smelt"));
    for(int i=0;i<8;i++)check(provider.tags.tag(i<4?"Enrich":"Smelt",machines.get(i).asLong(),p->FactoryServer.contains(provider.factoryGrid(),BlockPos.of(p))),"native_group_binding_"+i);
    for(int i=0;i<3;i++) {
     var recipe=PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(RAW[i]),1)),List.of(new GenericStack(AEItemKey.of(OUTPUT[i]),12)));
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
    for(int i=0;i<3;i++)plans.add(grid.getCraftingService().beginCraftingCalculation(level,()->action,AEItemKey.of(OUTPUT[i]),12L*batches,CalculationStrategy.REPORT_MISSING_ITEMS));
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
    raw+=stored(RAW[i]);dust+=stored(item("mekanism:dust_"+METALS[i]));out+=stored(OUTPUT[i]);
    returned[i]=inv.getAvailableStacks().get(AEItemKey.of(OUTPUT[i]));check(returned[i]<=12L*batches,"no_excess_output_"+METALS[i]);
    settled&=returned[i]==12L*batches&&!cpus.get(i).isBusy();
   }
   if(age==240)check(raw>0&&maxJobs==16&&Arrays.stream(returned).sum()==0,"power_loss_retains_inputs_and_parallel_jobs");
   if(age>300&&age<590&&!jobs().isEmpty())blockedTicks++;
   if(settled&&jobs().isEmpty()) {
    var power=aePowerState();evidence.put("aePowerAfterOrders",power);
    check(Boolean.TRUE.equals(power.get("mainPowered"))&&((Number)power.get("remainingFiniteAE")).doubleValue()>0,"finite_AE_supply_remains_after_all_orders");
    check(raw==0&&dust==0&&out==0,"all_mek_buffers_settled");check(maxJobs==16,"parallel_cap_reached");
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
    evidence.put("aePowerProgress",aePowerState());
    evidence.put("status","running");evidence.put("ticks",ticks);evidence.put("machineRaw",raw);evidence.put("machineDust",dust);evidence.put("machineOutput",out);
    evidence.put("returned",returned);evidence.put("admitted",admitted);evidence.put("completed",completed);evidence.put("active",jobs().size());evidence.put("peakJobs",maxJobs);
    Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("mekanism-bulk-progress.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
   }
   String current=raw+":"+dust+":"+out+":"+Arrays.toString(returned)+":"+Arrays.toString(completed);
   if(!current.equals(progressSignature)){progressSignature=current;lastProgress=ticks;}
   if(ticks-lastProgress>3000)throw new IllegalStateException("MEK parallel orders made no progress for 3000 ticks");

  }catch(Throwable e){finish(e);}
 }
 private static void finish(Throwable e) {
  finished=true;passed=e==null;evidence.put("status",passed?"passed":"failed");evidence.put("ticks",ticks);evidence.put("orders",orders);evidence.put("feReceived",feReceived);evidence.put("timeline",timeline);evidence.put("peakJobs",maxJobs);evidence.put("machinesUsed",touched.size());
  evidence.put("configuredTickRate",configuredTickRate);evidence.put("wallClockSeconds",(System.nanoTime()-startedNanos)/1_000_000_000.0);
  evidence.put("targetBatchesPerOrder",batches);evidence.put("targetCpuOrders",3);evidence.put("targetTotalBatches",3*batches);evidence.put("targetTotalOutput",36L*batches);evidence.put("timeoutServerTicks",timeoutTicks());
  evidence.put("scope","Three real concurrent AE2 CPU orders with per-resource admitted/completed/active conservation; outputs come from native machines; configured tick rate is a scheduler target");
  if(level!=null)try{evidence.put("aePowerAtFinish",aePowerState());recordPhysicalStock();}catch(Throwable diagnostic){evidence.put("physicalStateDiagnosticFailure",diagnostic.toString());}
  if(passed) {
   evidence.put("active",0);evidence.put("returned",orders.stream().map(row->row.get("output")).toList());
   evidence.put("admitted",admitted);evidence.put("completed",completed);
   evidence.put("machineRaw",0);evidence.put("machineDust",0);evidence.put("machineOutput",0);
   timeline.add(Map.of("tick",ticks,"admitted",admitted.clone(),"completed",completed.clone(),"active",new int[3]));
  }
  if(e!=null){evidence.put("failure",e.toString());evidence.put("stack",Arrays.toString(e.getStackTrace()));try{evidence.put("jobs",provider.getLogic().saveJobs().stream().map(j->j.continuation()).toList());}catch(Exception ignored){}}
  try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("mekanism-bulk-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}catch(Exception x){throw new RuntimeException(x);}finally{if(level!=null)level.getServer().tickRateManager().setTickRate(20);}
 }
}
