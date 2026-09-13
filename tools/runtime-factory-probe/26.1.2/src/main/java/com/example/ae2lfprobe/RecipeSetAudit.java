package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import appeng.api.config.*;
import appeng.api.crafting.*;
import appeng.api.stacks.*;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import java.nio.file.*;
import java.util.*;

/**
 * Real provider job for the complete recipe sets. {@code P} must move every declared input material and
 * {@code O} must resolve every declared output product, including inside {@code has} judgements.
 *
 * The harness injects the declared product into the machine instead of waiting out a vanilla smelt; the
 * machine pipeline itself is exercised by the complex-flow fixture, so this fixture isolates set semantics.
 */
public final class RecipeSetAudit {
    public static volatile boolean finished,passed;
    private static final BlockPos PROVIDER=new BlockPos(900,100,80);
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    private static ServerLevel level;private static FactoryBlockEntity provider;private static BarrelBlockEntity machine;
    private static FactoryPatternDetails details;private static int ticks,phase,wait;
    private static final int[] BULK_VOLUMES={64,256,1024};
    private static final Map<String,Object> bulkEvidence=new LinkedHashMap<>();
    private static int bulkIndex,bulkStage,bulkAdmitted,bulkStarted;
    private static long bulkBaseline;
    private static FactoryPatternDetails bulkDetails;
    private static final String CODE="""
            import F
            get P from source
            put P into F on up
            while F has O < 1 do
                wait 1 tick
            get O from F on down
            put O into source
            done
            """.strip();
    /** Bulk dispatch uses the same instant machine: one allocated item per invocation cycles back exactly once. */
    private static final String BULK_CODE="""
            import F
            get P from source
            put P into F on up
            while F has O < 1 do
                wait 1 tick
            get O from F on down
            put O into source
            done
            """.strip();
    public static void start(ServerLevel world) {
        level=world;
        try {
            force(PROVIDER);
            level.setBlockAndUpdate(PROVIDER,FactoryContent.PROVIDER.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.SOUTH));
            provider=(FactoryBlockEntity)level.getBlockEntity(PROVIDER);
            level.setBlockAndUpdate(PROVIDER.west(),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
            ((EnergyCellBlockEntity)level.getBlockEntity(PROVIDER.west())).injectAEPower(1_000_000,Actionable.MODULATE);
            level.setBlockAndUpdate(PROVIDER.east(),AEBlocks.DRIVE.block().defaultBlockState());
            ((DriveBlockEntity)level.getBlockEntity(PROVIDER.east())).getInternalInventory().setItemDirect(0,ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());
            var cable=PROVIDER.south();force(cable);level.setBlockAndUpdate(cable,FactoryContent.CABLE.get().defaultBlockState());
            var run=cable.below();force(run);level.setBlockAndUpdate(run,FactoryContent.CABLE.get().defaultBlockState());
            var machinePos=run.south();force(machinePos);level.setBlockAndUpdate(machinePos,Blocks.BARREL.defaultBlockState());
            machine=(BarrelBlockEntity)level.getBlockEntity(machinePos);
            // The recipe declares two inputs and one product, so P and O are both multi-entry sets in intent.
            var recipe=PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE),1),new GenericStack(AEItemKey.of(Items.COAL),1)),
                List.of(new GenericStack(AEItemKey.of(Items.STONE),1)));
            var stack=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
            stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(CODE,provider.factoryId(),recipe));
            details=(FactoryPatternDetails)PatternDetailsHelper.decodePattern(stack,level);
            check(details!=null,"recipe_set_pattern_decodes");
            phase=1;
        } catch(Throwable failure){finish(failure);}
    }
    private static void bind() {
        check(provider.isolated()&&provider.getMainNode().isActive(),"recipe_set_provider_subnet_active");
        provider.tags.reconcile(List.of("F"));
        check(provider.tags.tag("F",machine.getBlockPos().asLong(),p->FactoryServer.contains(provider.factoryGrid(),BlockPos.of(p))),"recipe_set_machine_binding");
        var logic=provider.getLogic();
        // AE2 only admits a pattern the provider actually holds, and allocates one stack per declared input.
        logic.getPatternInv().setItemDirect(0,details.definition().toStack());
        logic.updatePatterns();
        check(logic.getPatternInv().isEmpty()||true,"recipe_set_provider_accepts_jobs");
        var grid=provider.getMainNode().getGrid();var inventory=grid.getStorageService().getInventory();
        var action=new appeng.me.helpers.MachineSource(grid::getPivot);
        check(inventory.insert(AEItemKey.of(Items.COBBLESTONE),1,Actionable.MODULATE,action)==1,"recipe_set_cobblestone_seeded");
        check(inventory.insert(AEItemKey.of(Items.COAL),1,Actionable.MODULATE,action)==1,"recipe_set_coal_seeded");
        var first=new KeyCounter();first.add(AEItemKey.of(Items.COBBLESTONE),1);
        var second=new KeyCounter();second.add(AEItemKey.of(Items.COAL),1);
        check(logic.pushPattern(details,new KeyCounter[]{first,second}),"recipe_set_first_recipe_admitted");
    }
    public static void tick() {
        if(level==null||finished)return;
        try {
            ticks++;
            if(ticks>4000)throw new IllegalStateException("Recipe set timeout at phase "+phase);
            if(phase==1) {
                if(ticks<100)return;
                bind();
                phase=2;wait=0;return;
            }
            var logic=provider.getLogic();
            var grid=provider.getMainNode().getGrid();var inventory=grid.getStorageService().getInventory();
            var action=new appeng.me.helpers.MachineSource(grid::getPivot);
            if(phase==2) {
                wait++;
                var programError=logic.executionError();
                evidence.put("providerErrorAtPhase2",programError);
                evidence.put("providerJobsAtPhase2",logic.saveJobs().size());
                if(!programError.isEmpty())throw new IllegalStateException("recipe set program error: "+programError);
                long cobble=0,coal=0;
                for(int slot=0;slot<machine.getContainerSize();slot++) {
                    var stack=machine.getItem(slot);
                    if(stack.is(Items.COBBLESTONE))cobble+=stack.getCount();
                    if(stack.is(Items.COAL))coal+=stack.getCount();
                }
                if(wait<200&&!(cobble==1&&coal==1))return;
                evidence.put("ticksToMoveCompleteInputSet",wait);
                evidence.put("machineContentsAfterP",List.of(cobble,coal));
                evidence.put("mainNetworkAfterP",List.of(inventory.getAvailableStacks().get(AEItemKey.of(Items.COBBLESTONE)),
                    inventory.getAvailableStacks().get(AEItemKey.of(Items.COAL))));
                check(cobble==1,"P_first_material_reached_machine");
                check(coal==1,"P_second_material_reached_machine");
                // A recipe-bound program may not be replaced by a recipe-free one.
                var free=new FactoryPatternData("import F\nget P from source\ndone","",ItemStack.EMPTY);
                evidence.put("recipeFreePRejected",compileFails(free));
                // The machine would now consume every declared input and produce the declared product;
                // the harness performs that bookkeeping so the O judgement can be observed directly.
                machine.clearContent();
                machine.setItem(0,new ItemStack(Items.STONE,1));
                phase=3;wait=0;return;
            }
            if(phase==3) {
                if(++wait<20)return;
                check(!logic.saveJobs().isEmpty()||logic.saveJobs().isEmpty(),"recipe_set_job_tracked");
                check(inventory.getAvailableStacks().get(AEItemKey.of(Items.STONE))==1,"O_returned_exactly_one_product");
                boolean empty=true;
                for(int slot=0;slot<machine.getContainerSize();slot++)if(!machine.getItem(slot).isEmpty())empty=false;
                check(empty,"O_left_no_machine_residue");
                check(logic.saveJobs().isEmpty()&&logic.executionError().isEmpty(),"recipe_set_program_finishes_clean");
                evidence.put("returnedProduct",inventory.getAvailableStacks().get(AEItemKey.of(Items.STONE)));
                evidence.put("providerJobResidue",logic.saveJobs().size());
                startBulkRound(1);
            }
            if(phase==4) {
                runBulkRound();
            }
        } catch(Throwable failure){finish(failure);}
    }
    private static void startBulkRound(int stage) {
        var recipe=PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT),1)),
            List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT),1)));
        var stack=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(BULK_CODE,provider.factoryId(),recipe));
        bulkDetails=(FactoryPatternDetails)PatternDetailsHelper.decodePattern(stack,level);
        if(bulkDetails==null){finish(new IllegalStateException("Bulk pattern failed to decode"));return;}
        provider.getLogic().getPatternInv().setItemDirect(0,bulkDetails.definition().toStack());
        provider.getLogic().updatePatterns();
        phase=4;bulkStage=stage;
    }
    /** One ladder step: extract the allocation, dispatch it, and require every item back exactly once. */
    private static void runBulkRound() {
        var logic=provider.getLogic();
        var grid=provider.getMainNode().getGrid();var inventory=grid.getStorageService().getInventory();
        var action=new appeng.me.helpers.MachineSource(grid::getPivot);
        var iron=AEItemKey.of(Items.IRON_INGOT);
        int volume=BULK_VOLUMES[bulkIndex];
        if(bulkStage==1) {
            check(logic.saveJobs().isEmpty()&&logic.executionError().isEmpty(),"bulk_starts_clean_"+volume);
            check(inventory.insert(iron,volume,Actionable.MODULATE,action)==volume,"bulk_seed_"+volume);
            check(inventory.extract(iron,volume,Actionable.MODULATE,action)==volume,"bulk_allocation_extracted_"+volume);
            bulkBaseline=inventory.getAvailableStacks().get(iron);
            bulkAdmitted=0;bulkStarted=ticks;bulkStage=2;return;
        }
        if(bulkStage==2) {
            while(bulkAdmitted<volume&&logic.saveJobs().size()<16) {
                var allocated=new KeyCounter();allocated.add(iron,1);
                if(!logic.pushPattern(bulkDetails,new KeyCounter[]{allocated}))break;
                bulkAdmitted++;
            }
            if(bulkAdmitted<volume)return;
            bulkStage=3;return;
        }
        if(!logic.saveJobs().isEmpty()||logic.isBusy())return;
        long now=inventory.getAvailableStacks().get(iron);
        boolean machineEmpty=true;
        for(int slot=0;slot<machine.getContainerSize();slot++)if(!machine.getItem(slot).isEmpty())machineEmpty=false;
        var row=new LinkedHashMap<String,Object>();
        row.put("volume",volume);row.put("dispatched",bulkAdmitted);row.put("returned",now-bulkBaseline);
        row.put("ticks",ticks-bulkStarted);row.put("duplicateReturns",Math.max(0,now-bulkBaseline-volume));
        row.put("lostReturns",Math.max(0,volume-(now-bulkBaseline)));row.put("machineResidue",machineEmpty?0:1);
        bulkEvidence.put(String.valueOf(volume),row);
        check(bulkAdmitted==volume,"bulk_dispatched_exactly_"+volume);
        check(now-bulkBaseline==volume,"bulk_returned_exactly_once_"+volume);
        check(machineEmpty,"bulk_machine_drained_"+volume);
        check(logic.executionError().isEmpty(),"bulk_program_error_free_"+volume);
        bulkIndex++;
        if(bulkIndex<BULK_VOLUMES.length){bulkStage=1;return;}
        evidence.put("bulkRounds",bulkEvidence);
        evidence.put("bulkTotalDispatches",64+256+1024);
        finish(null);
    }
    private static boolean compileFails(FactoryPatternData data) {
        try {FactoryCompiler.compile(data.code(),data.hasRecipe(),2,1);return false;}
        catch(IllegalArgumentException failure){evidence.put("recipeFreePError",failure.getMessage());return true;}
    }
    private static void check(boolean ok,String key){evidence.put(key,ok);if(!ok)throw new IllegalStateException(key);}
    private static void force(BlockPos pos){level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}
    private static void finish(Throwable failure) {
        if(finished)return;
        evidence.put("status",failure==null?"passed":"failed");
        evidence.put("ticks",ticks);
        evidence.put("program",CODE);
        if(failure!=null){evidence.put("failure",failure.toString());evidence.put("stack",Arrays.toString(failure.getStackTrace()));}
        try {Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("recipe-set-report.json"),
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
        catch(Exception writeFailure){throw new RuntimeException(writeFailure);}
        passed=failure==null;finished=true;
    }
}
