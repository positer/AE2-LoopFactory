package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKeyTypes;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.energy.IEnergyStorage;
import java.util.*;
import java.nio.file.*;

/** Physical MEK tank/cube tests with no Applied Mekanistics or AppliedFlux key types loaded. */
public final class NativeCapabilityAudit {
 public static volatile boolean finished,passed;
 public static final List<BlockPos> positions=new ArrayList<>();
 private static final BlockPos POS=new BlockPos(720,100,80);
 private static final Map<String,Object> result=new LinkedHashMap<>();
 private static ServerLevel level;private static FactoryBlockEntity terminal;
 private static int ticks,phase,round,restores;
 private static long oxygenBaseline,energyBaseline,energySourceInitial,energyRemoved;
 private static Class<?> stackType,handlerType,actionType;private static Object execute;
 private static BlockCapability<?,Direction> chemicalCapability;
 private static void check(boolean b,String key){result.put(key,b);if(!b)throw new IllegalStateException(key);}
 private static Object call(Object o,Class<?> c,String n,Class<?>[] p,Object...a)throws Exception{return c.getMethod(n,p).invoke(o,a);}
 private static Object chemical(String id,long amount)throws Exception {var registry=(net.minecraft.core.Registry<?>)Class.forName("mekanism.api.MekanismAPI").getField("CHEMICAL_REGISTRY").get(null);var value=registry.get(ResourceLocation.parse(id));return stackType.getConstructor(Class.forName("mekanism.api.chemical.Chemical"),long.class).newInstance(value,amount);}
 private static Object handler(int i,Direction face){return level.getCapability(chemicalCapability,positions.get(i),face);}
 private static long amount(Object stack)throws Exception{return (long)call(stack,stackType,"getAmount",new Class<?>[0]);}
 private static long oxygen(int... indices)throws Exception {long n=0;for(int i:indices){var stack=call(handler(i,null),handlerType,"getChemicalInTank",new Class<?>[]{int.class},0);var registry=(net.minecraft.core.Registry)Class.forName("mekanism.api.MekanismAPI").getField("CHEMICAL_REGISTRY").get(null);if(registry.getKey(call(stack,stackType,"getChemical",new Class<?>[0])).toString().equals("mekanism:oxygen"))n+=amount(stack);}return n;}
 private static long chemicalAmount(int i)throws Exception{return amount(call(handler(i,null),handlerType,"getChemicalInTank",new Class<?>[]{int.class},0));}
 private static long fill(int i,String id,long n)throws Exception{return n-amount(call(handler(i,Direction.SOUTH),handlerType,"insertChemical",new Class<?>[]{stackType,actionType},chemical(id,n),execute));}
 private static IEnergyStorage energy(int i,Direction face){var e=level.getCapability(Capabilities.EnergyStorage.BLOCK,positions.get(i),face);if(e==null)throw new IllegalStateException("Missing native FE endpoint");return e;}
 private static long fillEnergy(int i,long n){long moved=0;for(int k=0;k<10000&&moved<n;k++){int x=energy(i,Direction.SOUTH).receiveEnergy((int)Math.min(Integer.MAX_VALUE,n-moved),false);if(x==0)break;moved+=x;}return moved;}
 @SuppressWarnings("unchecked") private static List<FactoryJob> jobs()throws Exception{var f=FactoryBlockEntity.class.getDeclaredField("terminalJobs");f.setAccessible(true);return (List<FactoryJob>)f.get(terminal);}
 private static void saveRestore()throws Exception{var list=jobs();var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);for(int i=0;i<list.size();i++){var saved=FactoryJob.Saved.CODEC.parse(ops,FactoryJob.Saved.CODEC.encodeStart(ops,list.get(i).save()).getOrThrow()).getOrThrow();list.set(i,FactoryJob.restore(terminal,saved));restores++;}}
 private static void code(boolean reverse,boolean energyProgram) {
  String source=reverse?"ChemTarget":"ChemSource",target=reverse?"ChemSource":"ChemTarget";
  if(!energyProgram)install(0,"import ChemSource,ChemTarget,EnergySource,EnergyTarget\nget must 30000 mekanism::chemical!(mekanism:hydrogen) from "+source+"\nwait 1 tick\nput must 30000 mekanism::chemical!(mekanism:hydrogen) into "+target+"\ndone");
  source=reverse?"EnergyTarget":"EnergySource";target=reverse?"EnergySource":"EnergyTarget";
  if(energyProgram)install(0,"import ChemSource,ChemTarget,EnergySource,EnergyTarget\nget must 100000 neoforge::fe from "+source+"\nwait 1 tick\nput must 100000 neoforge::fe into "+target+"\ndone");
 }
 private static void install(int slot,String code){var stack=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(code,terminal.factoryId(),ItemStack.EMPTY));terminal.factoryPatterns().setItemDirect(slot,stack);}
 private static void signal(boolean high){level.setBlockAndUpdate(POS.north(),high?Blocks.REDSTONE_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState());}
 @SuppressWarnings("unchecked") public static void start(ServerLevel world) {
  level=world;try {
   check(!net.neoforged.fml.ModList.get().isLoaded("appmek")&&!net.neoforged.fml.ModList.get().isLoaded("appflux"),"no_AE2_extra_storage_addons");
   result.put("registeredAEKeyTypes",AEKeyTypes.getAll().stream().map(t->t.getId().toString()).toList());
   check(AEKeyTypes.getAll().size()==2,"only_item_and_fluid_AE_key_types");
   stackType=Class.forName("mekanism.api.chemical.ChemicalStack");handlerType=Class.forName("mekanism.api.chemical.IChemicalHandler");actionType=Class.forName("mekanism.api.Action");execute=actionType.getField("EXECUTE").get(null);
   var type=Class.forName("mekanism.common.capabilities.Capabilities").getField("CHEMICAL").get(null);chemicalCapability=(BlockCapability<?,Direction>)type.getClass().getMethod("block").invoke(type);
   result.put("chemicalCapability",chemicalCapability.name().toString());result.put("energyCapability",Capabilities.EnergyStorage.BLOCK.name().toString());
   level.setChunkForced(POS.getX()>>4,POS.getZ()>>4,true);level.setBlockAndUpdate(POS,FactoryContent.TERMINAL.get().defaultBlockState());terminal=(FactoryBlockEntity)level.getBlockEntity(POS);
   level.setBlockAndUpdate(POS.west(),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());((EnergyCellBlockEntity)level.getBlockEntity(POS.west())).injectAEPower(1_000_000,Actionable.MODULATE);
   for(int x=1;x<=14;x++){var c=POS.east(x);level.setChunkForced(c.getX()>>4,c.getZ()>>4,true);level.setBlockAndUpdate(c,FactoryContent.CABLE.get().defaultBlockState());}
   for(int i=0;i<7;i++){var p=POS.east(2*(i+1)).above();positions.add(p);level.setBlockAndUpdate(p,BuiltInRegistries.BLOCK.get(ResourceLocation.parse(i<5?"mekanism:basic_chemical_tank":"mekanism:basic_energy_cube")).defaultBlockState());MekanismBulkAudit.initializePlacedMachine(level,p);}
   check(fill(0,"mekanism:oxygen",20000)==20000&&fill(1,"mekanism:oxygen",20000)==20000,"two_native_oxygen_sources");check(fill(2,"mekanism:hydrogen",7000)==7000,"excluded_hydrogen_seed");
   long capacity=(long)call(handler(3,null),handlerType,"getChemicalTankCapacity",new Class<?>[]{int.class},0);
   oxygenBaseline=capacity-1000;check(fill(3,"mekanism:oxygen",oxygenBaseline)==oxygenBaseline,"partial_capacity_target");check(fill(4,"mekanism:hydrogen",capacity)==capacity,"incompatible_full_target");
   energySourceInitial=fillEnergy(5,1_000_000);check(energySourceInitial==1_000_000,"native_FE_source_seed");energyBaseline=energy(6,Direction.SOUTH).getMaxEnergyStored()-4096L;check(fillEnergy(6,energyBaseline)==energyBaseline,"native_FE_partial_capacity");
  }catch(Throwable e){finish(e);}
 }
 public static void tick() {
  if(level==null||finished)return;try {
   ticks++;
   if(ticks==100){check(terminal.getMainNode().isActive(),"native_terminal_active");terminal.tags.reconcile(List.of("ChemSource","ChemTarget","EnergySource","EnergyTarget"));for(int i=0;i<7;i++)check(terminal.tags.tag(i<3?"ChemSource":i<5?"ChemTarget":i==5?"EnergySource":"EnergyTarget",positions.get(i).asLong(),p->FactoryServer.contains(terminal.factoryGrid(),BlockPos.of(p))),"native_tag_"+i);code(false,false);signal(true);}
   if(ticks==110)signal(false);
   if(ticks==120){code(false,true);signal(true);}
   if(ticks==130)signal(false);
   if(ticks==190){check(jobs().size()==2,"two_independent_native_waiting_jobs");check(oxygen(0,1)==39000&&oxygen(3,4)==oxygenBaseline+1000,"chemical_partial_1000_waits");check(energy(5,Direction.NORTH).getEnergyStored()==energySourceInitial-4096&&energy(6,Direction.NORTH).getEnergyStored()==energyBaseline+4096,"FE_partial_4096_waits");check(chemicalAmount(2)==7000,"hydrogen_exclusion_preserved");check(jobs().getFirst().count("ChemSource","mekanism::chemical!(mekanism:hydrogen)")==39000,"native_HAS_chemical_without_AE_key");check(jobs().getFirst().count("EnergySource","neoforge::fe")==energySourceInitial-4096,"native_HAS_FE_without_AE_key");saveRestore();check(jobs().stream().allMatch(j->j.save().continuation().contains("pendingTransfer")),"native_partial_continuations_saved");}
   if(ticks==200){long removed=amount(call(handler(4,Direction.NORTH),handlerType,"extractChemical",new Class<?>[]{long.class,actionType},Long.MAX_VALUE,execute));result.put("fixtureHydrogenRemoved",removed);check(removed>0,"released_chemical_target");for(int k=0;k<10000&&energyRemoved<200000;k++){int n=energy(6,Direction.NORTH).extractEnergy((int)(200000-energyRemoved),false);if(n==0)break;energyRemoved+=n;}check(energyRemoved==200000,"released_FE_target");}
   if(ticks>200&&phase==0&&jobs().isEmpty()) {verifyForward();result.put("blockedRecoveryTicks",ticks);phase=1;}
   if(phase==1&&ticks%40==0){code(round%2==0,false);signal(true);phase=2;}
   if(phase==2&&ticks%40==10){signal(false);phase=3;}
   if(phase==3&&ticks%40==20){code(round%2==0,true);signal(true);phase=4;}
   if(phase==4&&ticks%40==30){signal(false);phase=5;}
   if(phase==5&&jobs().isEmpty()) {
    boolean reverse=round%2==0;check(oxygen(0,1)==(reverse?40000:10000),"round_chemical_source_"+round);check(oxygen(3,4)==oxygenBaseline+(reverse?0:30000),"round_chemical_target_"+round);
    check(energy(5,Direction.NORTH).getEnergyStored()==energySourceInitial-(reverse?0:100000),"round_FE_source_"+round);check(energy(6,Direction.NORTH).getEnergyStored()==energyBaseline+(reverse?0:100000)-energyRemoved,"round_FE_target_"+round);
    check(chemicalAmount(2)==7000,"round_excluded_hydrogen_"+round);round++;if(round==32){finish(null);return;}phase=1;
   }
   if(ticks%7==0&&ticks>=100)saveRestore();
   if(ticks>=100){check(terminal.executionError().isEmpty(),"native_terminal_no_error");for(var job:jobs())check(job.error().isEmpty(),"native_job_no_error");}
   if(ticks%100==0){result.put("status","running");result.put("ticks",ticks);result.put("phase",phase);result.put("round",round);result.put("sourceOxygen",oxygen(0,1));result.put("targetOxygen",oxygen(3,4));result.put("sourceFE",energy(5,Direction.NORTH).getEnergyStored());result.put("targetFE",energy(6,Direction.NORTH).getEnergyStored());write("native-capability-progress.json");}
   if(ticks>2400)throw new IllegalStateException("Native capability timeout phase="+phase+" round="+round);
  }catch(Throwable e){finish(e);}
 }
 private static void verifyForward()throws Exception {check(oxygen(0,1)==10000&&oxygen(3,4)==oxygenBaseline+30000,"native_chemical_exact_30000");check(energy(5,Direction.NORTH).getEnergyStored()==energySourceInitial-100000&&energy(6,Direction.NORTH).getEnergyStored()==energyBaseline+100000-energyRemoved,"native_FE_exact_100000");}
 private static void finish(Throwable e){finished=true;passed=e==null;result.put("status",passed?"passed":"failed");result.put("ticks",ticks);result.put("directions",round);result.put("codecRestores",restores);if(e!=null){result.put("failure",e.toString());result.put("stack",Arrays.toString(e.getStackTrace()));try{result.put("jobs",jobs().stream().map(j->j.save().continuation()).toList());}catch(Exception ignored){}}write("native-capability-report.json");}
 private static void write(String name){try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve(name),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new RuntimeException(e);}}
}
