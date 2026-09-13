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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import java.nio.file.*;
import java.util.*;

/** Real terminal queues plus native AE2 CPU orders through fueled vanilla furnace pipelines. */
public final class ComplexFlowAudit {
    public static volatile boolean finished,passed;
    private static ServerLevel level;
    private static final FactoryBlockEntity[] terminals=new FactoryBlockEntity[6];
    private static final BarrelBlockEntity[][] barrels=new BarrelBlockEntity[6][5];
    private static final BlockPos PROVIDER=new BlockPos(480,100,80);
    private static FactoryBlockEntity provider;
    private static final List<FurnaceBlockEntity> furnaces=new ArrayList<>();
    private static final Map<String,String> examples=new LinkedHashMap<>();
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    private static final List<Map<String,Object>> orders=new ArrayList<>();
    private static int ticks,order,orderPhase,orderStarted,maxProviderJobs,maxTerminalJobs,restores,pulseTicks;
    private static java.util.concurrent.Future<ICraftingPlan> calculation;
    private static boolean terminalsChecked;
    // Return stability: repeated blocking / parallel dispatch rounds with a per-round ledger.
    private static final int ROUND_BATCHES=2;
    private static int blockingRounds=12,parallelRounds=12,configuredTickRate=20,submittedCpuOrders;
    private static long startedNanos;
    public static int timeoutTicks(){return Math.max(40000,10000+(blockingRounds+2*parallelRounds)*ROUND_BATCHES*650);}
    private static final List<Map<String,Object>> stability=new ArrayList<>();
    private static int stabilityRound,roundPhase,roundStarted,roundPeakJobs,roundCodecRestores,roundBlockingGateHoldTicks;
    private static long roundBaselinePrimary,roundBaselineInput;
    private static appeng.me.cluster.implementations.CraftingCPUCluster firstCpu,cpu2;
    private static java.util.concurrent.Future<ICraftingPlan> calculation2;
    private static final List<Long> roundDurations=new ArrayList<>();
    private static final String[] ORDER_EXAMPLES={"recipe_two_stage","recipe_two_stage","recipe_multi_output","sfm_recipe"};
    private static final int[] BATCHES={2,4,4,2};
    public static void start(ServerLevel world) {
        level=world;startedNanos=System.nanoTime();
        try {
            blockingRounds=StressAudit.option("blockingRounds",12,1,32);
            parallelRounds=StressAudit.option("parallelRounds",12,1,32);
            configuredTickRate=StressAudit.configureTickRate(world);
            try(var stream=ComplexFlowAudit.class.getResourceAsStream("/factory-examples/catalog.json")) {
                for(var element:com.google.gson.JsonParser.parseString(new String(Objects.requireNonNull(stream).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()) {
                    var value=element.getAsJsonObject();examples.put(value.get("id").getAsString(),value.get("code").getAsString());
                }
            }
            for(int i=0;i<terminals.length;i++) {
                var pos=new BlockPos(256+i*32,100,80);force(pos);
                level.setBlockAndUpdate(pos,FactoryContent.TERMINAL.get().defaultBlockState());terminals[i]=(FactoryBlockEntity)level.getBlockEntity(pos);power(pos.west());
                for(int j=0;j<5;j++) {
                    var cable=pos.east(j+1);force(cable);level.setBlockAndUpdate(cable,FactoryContent.CABLE.get().defaultBlockState());
                    level.setBlockAndUpdate(cable.above(),Blocks.BARREL.defaultBlockState());barrels[i][j]=(BarrelBlockEntity)level.getBlockEntity(cable.above());
                }
            }
            for(int i=0;i<6;i++)barrels[i][0].setItem(0,new ItemStack(Items.IRON_INGOT,64));
            barrels[0][0].setItem(1,new ItemStack(Items.GOLD_INGOT,5));
            for(int i:new int[]{0,1}) {
                barrels[i][3].setItem(0,new ItemStack(Items.IRON_INGOT,40));
                for(int s=1;s<27;s++)barrels[i][3].setItem(s,new ItemStack(Items.COBBLESTONE,64));
            }
            barrels[1][0].setItem(1,new ItemStack(Items.GOLD_INGOT));barrels[1][3].setItem(26,new ItemStack(Items.GOLD_INGOT,63));
            barrels[2][1].setItem(0,new ItemStack(Items.IRON_INGOT,64));
            barrels[3][1].setItem(0,new ItemStack(Items.IRON_INGOT,32));
            barrels[4][0].setItem(1,new ItemStack(Items.GOLD_INGOT,8));
            force(PROVIDER);level.setBlockAndUpdate(PROVIDER,FactoryContent.PROVIDER.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.SOUTH));
            provider=(FactoryBlockEntity)level.getBlockEntity(PROVIDER);power(PROVIDER.west());
            level.setBlockAndUpdate(PROVIDER.east(),AEBlocks.DRIVE.block().defaultBlockState());
            ((DriveBlockEntity)level.getBlockEntity(PROVIDER.east())).getInternalInventory().setItemDirect(0,ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());
            level.setBlockAndUpdate(PROVIDER.east(2),AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
            level.setBlockAndUpdate(PROVIDER.east(2).above(),AEBlocks.CRAFTING_ACCELERATOR.block().defaultBlockState());
            // The second cluster sits on the powered side so it stays a separate CPU from the east cluster.
            level.setBlockAndUpdate(PROVIDER.west().above(),AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
            level.setBlockAndUpdate(PROVIDER.west().above(2),AEBlocks.CRAFTING_ACCELERATOR.block().defaultBlockState());
            level.setBlockAndUpdate(PROVIDER.south(),FactoryContent.CABLE.get().defaultBlockState());
            for(int j=0;j<5;j++) {
                var cable=PROVIDER.south().below().east(j);force(cable);level.setBlockAndUpdate(cable,FactoryContent.CABLE.get().defaultBlockState());
                // A vertical gap prevents the main-network drive touching the subnet.
                var machine=cable.south();force(machine);level.setBlockAndUpdate(machine,Blocks.FURNACE.defaultBlockState());
                var furnace=(FurnaceBlockEntity)level.getBlockEntity(machine);furnace.setItem(1,new ItemStack(Items.COAL,32));furnaces.add(furnace);
            }
        } catch(Throwable error){finish(error);}
    }
    private static void force(BlockPos pos){level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}
    private static void power(BlockPos pos){force(pos);level.setBlockAndUpdate(pos,AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());((EnergyCellBlockEntity)level.getBlockEntity(pos)).injectAEPower(1_000_000,Actionable.MODULATE);}
    private static void check(boolean ok,String key){evidence.put(key,ok);if(!ok)throw new IllegalStateException(key);}
    private static ItemStack pattern(FactoryBlockEntity host,String code,ItemStack recipe) {var stack=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(code,host.factoryId(),recipe));return stack;}
    private static void install(int i,String code){terminals[i].factoryPatterns().setItemDirect(0,pattern(terminals[i],code,ItemStack.EMPTY));}
    private static void signal(int i,boolean high){level.setBlockAndUpdate(terminals[i].getBlockPos().north(),high?Blocks.REDSTONE_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState());}
    @SuppressWarnings("unchecked") private static List<FactoryJob> jobs(FactoryBlockEntity host)throws Exception {var field=FactoryBlockEntity.class.getDeclaredField("terminalJobs");field.setAccessible(true);return (List<FactoryJob>)field.get(host);}
    /** Provider jobs live in the pattern-provider logic; the same codec path the block uses for NBT. */
    @SuppressWarnings("unchecked") private static List<FactoryJob> providerJobs()throws Exception {var field=FactoryProviderLogic.class.getDeclaredField("jobs");field.setAccessible(true);return (List<FactoryJob>)field.get(provider.getLogic());}
    private static void restoreProviderJobs()throws Exception {
        var list=providerJobs();var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for(int i=0;i<list.size();i++) {var saved=FactoryJob.Saved.CODEC.parse(ops,FactoryJob.Saved.CODEC.encodeStart(ops,list.get(i).save()).getOrThrow()).getOrThrow();list.set(i,FactoryJob.restore(provider,saved));restores++;roundCodecRestores++;}
    }
    private static void restoreJobs(FactoryBlockEntity host)throws Exception {
        var list=jobs(host);var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for(int i=0;i<list.size();i++) {var saved=FactoryJob.Saved.CODEC.parse(ops,FactoryJob.Saved.CODEC.encodeStart(ops,list.get(i).save()).getOrThrow()).getOrThrow();list.set(i,FactoryJob.restore(host,saved));restores++;}
    }
    private static long count(int host,int barrel,Item item){long n=0;var inventory=barrels[host][barrel];for(int s=0;s<inventory.getContainerSize();s++)if(inventory.getItem(s).is(item))n+=inventory.getItem(s).getCount();return n;}
    public static void tick() {
        if(level==null||finished)return;
        try {
            ticks++;
            if(ticks==100) {
                for(int i=0;i<6;i++) {
                    var host=terminals[i];check(host.getMainNode().isActive(),"terminal_active_"+i);host.tags.reconcile(List.of("Input","Buffer","Output","Ready"));
                    for(int j=0;j<5;j++) {String tag=j<2?"Input":j==2?"Buffer":j==3?"Output":"Ready";check(host.tags.tag(tag,barrels[i][j].getBlockPos().asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))),"group_binding_"+i+"_"+j);}
                }
                String[] ids={"transfer","must_wait","continuous","finite_nested","sfm_timer"};for(int i=0;i<5;i++)install(i,examples.get(ids[i]));
                install(5,"import Input,Output\nget must 1 minecraft:iron_ingot from Input\nwait 20 tick\nput minecraft:iron_ingot into Output\ndone");
                for(int i=0;i<4;i++)signal(i,true);
                check(provider.isolated()&&provider.getMainNode().isActive()&&FactoryServer.owner(provider.factoryGrid())==provider,"provider_owns_powered_isolated_subnet");
                provider.tags.reconcile(List.of("StageOne","StageTwo","IronFurnace","GoldFurnace","Furnace"));
                String[] names={"StageOne","StageTwo","IronFurnace","GoldFurnace","Furnace"};for(int j=0;j<5;j++)check(provider.tags.tag(names[j],furnaces.get(j).getBlockPos().asLong(),p->FactoryServer.contains(provider.factoryGrid(),BlockPos.of(p))),"furnace_binding_"+names[j]);
                check(!provider.getLogic().accepts(pattern(provider,examples.get("continuous"),ItemStack.EMPTY)),"provider_rejects_recipe_free_pattern");
            }
            if(ticks<101)return;
            if(ticks==102)for(int i=0;i<4;i++)signal(i,false);
            if(ticks==103) {
                var pending=jobs(terminals[1]);check(pending.size()==1,"must_declaration_wait_is_admitted");
                restoreJobs(terminals[1]);
                var routes=com.google.gson.JsonParser.parseString(jobs(terminals[1]).getFirst().save().routes()).getAsJsonArray();
                check(routes.get(0).getAsJsonObject().get("must").getAsBoolean(),"must_flag_survives_before_first_put");
            }
            if(ticks==120) {
                check(jobs(terminals[0]).isEmpty()&&count(0,0,Items.IRON_INGOT)==40&&count(0,3,Items.IRON_INGOT)==64&&count(0,0,Items.GOLD_INGOT)==5,"ordinary_partial_finishes_and_exclusion_preserves_gold");
                check(jobs(terminals[1]).size()==1&&count(1,0,Items.IRON_INGOT)==40&&count(1,3,Items.IRON_INGOT)==64,"restored_get_must_does_not_advance_after_24");
            }
            if(ticks==125)install(1,"import Input,Output\nget must 1 minecraft:gold_ingot from Input\nput minecraft:gold_ingot into Output\ndone");
            if(ticks==126)signal(1,true);if(ticks==128)signal(1,false);
            if(ticks==140)check(count(1,3,Items.GOLD_INGOT)==64&&jobs(terminals[1]).size()==1,"later_gold_run_finishes_while_iron_must_waits");
            if(ticks==200)barrels[1][3].setItem(1,ItemStack.EMPTY);
            if(ticks==210)check(jobs(terminals[1]).isEmpty()&&count(1,0,Items.IRON_INGOT)==0&&count(1,3,Items.IRON_INGOT)==104,"restored_must_completes_only_remaining_40");
            if(ticks>=110&&ticks<174)signal(5,ticks%2==0);
            if(ticks==174)signal(5,false);
            maxTerminalJobs=Math.max(maxTerminalJobs,jobs(terminals[5]).size());
            if(ticks>=110&&ticks<142)signal(4,ticks%4==2);if(ticks==142)signal(4,true);
            if(ticks==500)barrels[2][0].setItem(0,new ItemStack(Items.IRON_INGOT,64));
            if(level.hasNeighborSignal(barrels[2][4].getBlockPos()))pulseTicks++;
            if(ticks%7==0)for(var terminal:terminals)restoreJobs(terminal);
            for(var terminal:terminals) {check(terminal.executionError().isEmpty(),"all_terminal_programs_error_free");for(var job:jobs(terminal))check(job.error().isEmpty(),"all_independent_jobs_error_free");}
            if(ticks==850) {
                check(count(2,3,Items.IRON_INGOT)==192&&count(2,2,Items.IRON_INGOT)==0&&jobs(terminals[2]).size()==1&&pulseTicks>=128,"continuous_nested_pipeline_resumes_after_late_refill");
                check(count(3,3,Items.IRON_INGOT)==96&&jobs(terminals[3]).isEmpty(),"finite_nested_forward_function_finishes_96");
                check(count(4,3,Items.IRON_INGOT)==64&&count(4,3,Items.GOLD_INGOT)==8,"sfm_two_trigger_program_timer_and_edges_are_exact");
                check(count(5,3,Items.IRON_INGOT)==32&&count(5,0,Items.IRON_INGOT)==32&&jobs(terminals[5]).isEmpty()&&maxTerminalJobs>=8,"32_native_redstone_runs_keep_independent_continuations");
                terminalsChecked=true;
            }
            runOrders();
            if(terminalsChecked&&order==ORDER_EXAMPLES.length) {
                if(stabilityRound<blockingRounds+parallelRounds)runStabilityRound();
                else finish(null);
            }
            if(ticks>timeoutTicks())throw new IllegalStateException("Complex flow timeout at order "+order+" phase "+orderPhase+" round "+stabilityRound+" phase "+roundPhase);
        }catch(Throwable error){finish(error);}
    }
    private static Item inputItem(){return order==2?Items.RAW_IRON:Items.COBBLESTONE;}
    private static Item outputItem(){return order<2?Items.SMOOTH_STONE:order==2?Items.IRON_INGOT:Items.STONE;}
    private static void runOrders()throws Exception {
        if(order>=ORDER_EXAMPLES.length)return;
        if(orderPhase>0&&ticks-orderStarted>6000)throw new IllegalStateException("Complex native order stalled at "+order+" phase "+orderPhase);
        var logic=provider.getLogic();var grid=provider.getMainNode().getGrid();var inventory=grid.getStorageService().getInventory();var action=new MachineSource(grid::getPivot);
        var cpu=((CraftingBlockEntity)level.getBlockEntity(PROVIDER.east(2))).getCluster();if(cpu==null)return;
        if(orderPhase==0) {
            check(logic.saveJobs().isEmpty(),"new_order_starts_without_old_jobs_"+order);
            for(var stack:inventory.getAvailableStacks())inventory.extract(stack.getKey(),stack.getLongValue(),Actionable.MODULATE,action);
            var inputs=new ArrayList<GenericStack>();inputs.add(new GenericStack(AEItemKey.of(inputItem()),1));if(order==2)inputs.add(new GenericStack(AEItemKey.of(Items.RAW_GOLD),1));
            var outputs=new ArrayList<GenericStack>();outputs.add(new GenericStack(AEItemKey.of(outputItem()),1));if(order==2)outputs.add(new GenericStack(AEItemKey.of(Items.GOLD_INGOT),1));
            var recipe=PatternDetailsHelper.encodeProcessingPattern(inputs,outputs);var stack=pattern(provider,examples.get(ORDER_EXAMPLES[order]),recipe);
            check(logic.accepts(stack),"provider_accepts_recipe_example_"+order);
            check(!terminals[0].factoryPatterns().isItemValid(0,stack),"terminal_rejects_recipe_pattern_"+order);
            var wrong=stack.copy();wrong.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(examples.get(ORDER_EXAMPLES[order]),"wrong-network",recipe));check(!logic.accepts(wrong),"provider_rejects_wrong_recipe_binding_"+order);
            logic.getPatternInv().setItemDirect(0,stack);logic.getConfigManager().putSetting(Settings.BLOCKING_MODE,order==0?YesNo.YES:YesNo.NO);logic.updatePatterns();
            for(var input:inputs)check(inventory.insert(input.what(),BATCHES[order],Actionable.MODULATE,action)==BATCHES[order],"cpu_inputs_inserted_"+order+"_"+input.what());
            orderStarted=ticks;maxProviderJobs=0;orderPhase=1;return;
        }
        if(orderPhase==1) {if(ticks-orderStarted<10)return;calculation=grid.getCraftingService().beginCraftingCalculation(level,()->action,AEItemKey.of(outputItem()),BATCHES[order],CalculationStrategy.REPORT_MISSING_ITEMS);orderPhase=2;return;}
        if(orderPhase==2) {
            if(!calculation.isDone())return;var plan=calculation.get();check(plan!=null&&!plan.simulation()&&plan.missingItems().isEmpty(),"native_complex_plan_has_no_missing_inputs_"+order);
            check(grid.getCraftingService().submitJob(plan,null,cpu,false,action).successful(),"native_complex_cpu_submission_"+order);submittedCpuOrders++;orderPhase=3;return;
        }
        maxProviderJobs=Math.max(maxProviderJobs,logic.saveJobs().size());check(logic.executionError().isEmpty(),"recipe_program_error_free_"+order);
        if(order==0)check(logic.saveJobs().size()<=1,"blocking_never_admits_two_pipeline_batches");
        if(inventory.getAvailableStacks().get(AEItemKey.of(outputItem()))<BATCHES[order]||!logic.saveJobs().isEmpty()||cpu.isBusy())return;
        check(inventory.getAvailableStacks().get(AEItemKey.of(outputItem()))==BATCHES[order],"exact_complex_primary_output_"+order);
        if(order==2)check(inventory.getAvailableStacks().get(AEItemKey.of(Items.GOLD_INGOT))==BATCHES[order],"all_O2_byproducts_return_exactly");
        check(inventory.getAvailableStacks().get(AEItemKey.of(inputItem()))==0,"all_P1_allocations_consumed_exactly_"+order);
        if(order==2)check(inventory.getAvailableStacks().get(AEItemKey.of(Items.RAW_GOLD))==0,"all_P2_allocations_consumed_exactly");
        check(furnaces.stream().allMatch(f->f.getItem(0).isEmpty()&&f.getItem(2).isEmpty()),"all_furnace_pipeline_buffers_settled_"+order);
        if(order==1)check(maxProviderJobs>=2,"nonblocking_cpu_has_parallel_pipeline_jobs");
        orders.add(Map.of("example",ORDER_EXAMPLES[order],"batches",BATCHES[order],"blocking",order==0,"ticks",ticks-orderStarted,"maxConcurrentJobs",maxProviderJobs));
        order++;orderPhase=0;
    }
    /**
     * Configurable blocking rounds then parallel rounds through the same provider, each with its own
     * ledger row. Every round must return exactly its own primary output, consume exactly its own input
     * and leave no provider, CPU or furnace residue; provider jobs are codec-restored mid-round.
     */
    private static void runStabilityRound()throws Exception {
        var logic=provider.getLogic();var grid=provider.getMainNode().getGrid();var inventory=grid.getStorageService().getInventory();var action=new MachineSource(grid::getPivot);
        var firstEntity=(CraftingBlockEntity)level.getBlockEntity(PROVIDER.east(2));if(firstEntity!=null)firstCpu=firstEntity.getCluster();var secondEntity=(CraftingBlockEntity)level.getBlockEntity(PROVIDER.west().above());if(secondEntity!=null)cpu2=secondEntity.getCluster();if(firstCpu==null)return;
        boolean blocking=stabilityRound<blockingRounds;
        int orders=blocking?1:2,expected=orders*ROUND_BATCHES;
        if(roundPhase>0&&ticks-roundStarted>1000+expected*650)throw new IllegalStateException("Stability round stalled at "+stabilityRound+" phase "+roundPhase);
        var input=AEItemKey.of(Items.COBBLESTONE);var output=AEItemKey.of(Items.SMOOTH_STONE);
        if(roundPhase==0) {
            check(logic.saveJobs().isEmpty(),"stability_round_starts_clean_"+stabilityRound);
            check(!firstCpu.isBusy()&&(cpu2==null||!cpu2.isBusy()),"stability_cpus_idle_"+stabilityRound);
            var snapshot=new ArrayList<AEKey>();for(var stack:inventory.getAvailableStacks())snapshot.add(stack.getKey());
            for(var key:snapshot)inventory.extract(key,Long.MAX_VALUE,Actionable.MODULATE,action);
            logic.getPatternInv().setItemDirect(0,pattern(provider,examples.get("recipe_two_stage"),
                PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(input,1)),List.of(new GenericStack(output,1)))));
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE,blocking?YesNo.YES:YesNo.NO);logic.updatePatterns();
            roundBaselinePrimary=inventory.getAvailableStacks().get(output);roundBaselineInput=inventory.getAvailableStacks().get(input);
            for(int i=0;i<orders;i++)check(inventory.insert(input,ROUND_BATCHES,Actionable.MODULATE,action)==ROUND_BATCHES,"stability_input_insert_"+stabilityRound+"_"+i);
            roundStarted=ticks;roundPeakJobs=0;roundCodecRestores=0;roundBlockingGateHoldTicks=0;roundPhase=1;
            calculation=grid.getCraftingService().beginCraftingCalculation(level,()->action,output,ROUND_BATCHES,CalculationStrategy.REPORT_MISSING_ITEMS);
            calculation2=blocking?null:grid.getCraftingService().beginCraftingCalculation(level,()->action,output,ROUND_BATCHES,CalculationStrategy.REPORT_MISSING_ITEMS);
            return;
        }
        if(roundPhase==1) {
            if(!calculation.isDone()||(!blocking&&(calculation2==null||!calculation2.isDone())))return;
            var plan=calculation.get();check(plan!=null&&!plan.simulation()&&plan.missingItems().isEmpty(),"stability_plan_"+stabilityRound);
            check(grid.getCraftingService().submitJob(plan,null,firstCpu,false,action).successful(),"stability_submit_"+stabilityRound);submittedCpuOrders++;
            if(!blocking) {
                var plan2=calculation2.get();check(plan2!=null&&!plan2.simulation()&&plan2.missingItems().isEmpty(),"stability_plan_parallel_"+stabilityRound);
                check(grid.getCraftingService().submitJob(plan2,null,cpu2==null?firstCpu:cpu2,false,action).successful(),"stability_submit_parallel_"+stabilityRound);submittedCpuOrders++;
            }
            roundPhase=2;return;
        }
        int liveJobs=logic.saveJobs().size();
        roundPeakJobs=Math.max(roundPeakJobs,liveJobs);
        long primaryNow=inventory.getAvailableStacks().get(output);
        if(blocking&&liveJobs==1&&primaryNow-roundBaselinePrimary<expected)roundBlockingGateHoldTicks++;
        check(logic.executionError().isEmpty(),"stability_program_error_free_"+stabilityRound);
        if(liveJobs>0&&ticks%3==0)restoreProviderJobs();
        boolean settled=logic.saveJobs().isEmpty()&&!firstCpu.isBusy()&&(cpu2==null||!cpu2.isBusy())
            &&furnaces.stream().allMatch(f->f.getItem(0).isEmpty()&&f.getItem(2).isEmpty());
        if(!settled||primaryNow-roundBaselinePrimary<expected)return;
        check(primaryNow-roundBaselinePrimary==expected,"stability_exact_primary_delta_"+stabilityRound);
        check(inventory.getAvailableStacks().get(input)==roundBaselineInput,"stability_input_conserved_"+stabilityRound);
        if(blocking) {
            check(roundPeakJobs<=1,"stability_blocking_single_admission_"+stabilityRound);
            check(roundBlockingGateHoldTicks>0,"stability_blocking_gate_held_"+stabilityRound);
        } else check(roundPeakJobs>=2,"stability_parallel_admission_"+stabilityRound);
        var row=new LinkedHashMap<String,Object>();
        row.put("round",stabilityRound);row.put("mode",blocking?"blocking":"parallel");row.put("orders",orders);row.put("batches",expected);
        row.put("ticks",ticks-roundStarted);row.put("primaryDelta",primaryNow-roundBaselinePrimary);row.put("inputResidue",0);
        row.put("peakProviderJobs",roundPeakJobs);row.put("blockingGateHoldTicks",roundBlockingGateHoldTicks);
        check(roundCodecRestores>0,"stability_continuation_restored_"+stabilityRound);
        row.put("codecRestores",roundCodecRestores);row.put("duplicateReturns",0);row.put("lostReturns",0);
        stability.add(row);roundDurations.add((long)(ticks-roundStarted));
        inventory.extract(output,expected,Actionable.MODULATE,action);
        check(inventory.getAvailableStacks().get(output)==roundBaselinePrimary,"stability_round_reset_"+stabilityRound);
        stabilityRound++;roundPhase=0;calculation2=null;
    }
    private static void finish(Throwable error) {
        passed=error==null;finished=true;evidence.put("status",passed?"passed":"failed");evidence.put("ticks",ticks);evidence.put("terminalCodecRestores",restores);evidence.put("orders",orders);evidence.put("maxQueuedTerminalJobs",maxTerminalJobs);
        evidence.put("configuredTickRate",configuredTickRate);evidence.put("wallClockSeconds",(System.nanoTime()-startedNanos)/1_000_000_000.0);
        evidence.put("targetBlockingRounds",blockingRounds);evidence.put("targetParallelRounds",parallelRounds);
        evidence.put("targetStabilityCpuOrders",blockingRounds+2*parallelRounds);
        evidence.put("targetStabilityPrimary",(blockingRounds+2*parallelRounds)*ROUND_BATCHES);
        evidence.put("targetTotalCpuOrders",ORDER_EXAMPLES.length+blockingRounds+2*parallelRounds);evidence.put("submittedCpuOrders",submittedCpuOrders);
        evidence.put("timeoutServerTicks",timeoutTicks());
        evidence.put("scope","Real AE2 CPU submissions and fueled vanilla furnace recipes; stability rounds retain native recipe durations and exact input/output deltas; tick rate is a requested scheduler target, not a normal-20-TPS guarantee");
        evidence.put("stabilityRounds",stability);
        evidence.put("stabilityRoundCount",stability.size());
        evidence.put("stabilityBlockingRounds",(int)stability.stream().filter(r->"blocking".equals(r.get("mode"))).count());
        evidence.put("stabilityParallelRounds",(int)stability.stream().filter(r->"parallel".equals(r.get("mode"))).count());
        evidence.put("stabilityTotalReturnedPrimary",stability.stream().mapToLong(r->((Number)r.get("primaryDelta")).longValue()).sum());
        evidence.put("stabilityDuplicateReturns",stability.stream().mapToLong(r->((Number)r.get("duplicateReturns")).longValue()).sum());
        evidence.put("stabilityLostReturns",stability.stream().mapToLong(r->((Number)r.get("lostReturns")).longValue()).sum());
        evidence.put("stabilityMaxPeakProviderJobs",stability.stream().mapToLong(r->((Number)r.get("peakProviderJobs")).longValue()).max().orElse(0));
        var sortedDurations=new ArrayList<>(roundDurations);Collections.sort(sortedDurations);
        evidence.put("stabilityMaxRoundTicks",sortedDurations.isEmpty()?0:sortedDurations.get(sortedDurations.size()-1));
        evidence.put("stabilityMedianRoundTicks",sortedDurations.isEmpty()?0:sortedDurations.get(sortedDurations.size()/2));
        try {
            evidence.put("stabilityProviderJobResidue",providerJobs().size());
            evidence.put("stabilityFurnaceResidue",furnaces.stream().filter(f->!f.getItem(0).isEmpty()||!f.getItem(2).isEmpty()).count());
            evidence.put("stabilityMainNetworkPrimaryResidue",provider.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(Items.SMOOTH_STONE)));
        } catch(Exception ignored) {}
        if(error!=null){evidence.put("failure",error.toString());evidence.put("stack",Arrays.toString(error.getStackTrace()));evidence.put("order",order);evidence.put("orderPhase",orderPhase);try {evidence.put("providerJobs",provider.getLogic().saveJobs().stream().map(j->j.continuation()).toList());evidence.put("terminalCounts",java.util.stream.IntStream.range(0,6).mapToObj(i->List.of(count(i,0,Items.IRON_INGOT),count(i,3,Items.IRON_INGOT),count(i,3,Items.GOLD_INGOT))).toList());}catch(Exception ignored){}}
        try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("complex-flow-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}catch(Exception e){throw new RuntimeException(e);}finally{if(level!=null)level.getServer().tickRateManager().setTickRate(20);}
    }
}
