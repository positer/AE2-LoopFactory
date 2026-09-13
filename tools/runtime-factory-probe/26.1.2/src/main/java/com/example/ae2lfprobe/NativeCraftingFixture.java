package com.example.ae2lfprobe;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.helpers.MachineSource;
import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import java.util.List;

/** A real AE2 CPU dispatches cobblestone through the production provider into a vanilla furnace. */
final class NativeCraftingFixture {
    private final ServerLevel level;
    private final FactoryBlockEntity provider;
    private final java.util.Map<String,Object> report = new java.util.LinkedHashMap<>();
    private final BlockPos cpuPos = new BlockPos(1, 100, 0), drivePos = new BlockPos(2,100,0), furnacePos = new BlockPos(-1,100,-1);
    private java.util.concurrent.Future<ICraftingPlan> future;
    private int phase, ticks;
    private final boolean blockingProbe = Boolean.getBoolean("ae2lf.probe.blocking");
    private boolean blockingReleased;
    NativeCraftingFixture(ServerLevel level, FactoryBlockEntity provider) {
        this.level=level; this.provider=provider;
        if (RestartState.RESUME) {
            var jobs = provider.getLogic().saveJobs();
            check(jobs.size()==1,"unfinished_provider_job_loaded_from_disk");
            check(jobs.getFirst().input().size()==1 && jobs.getFirst().input().getFirst().amount()==1
                    && jobs.getFirst().input().getFirst().what().equals(AEItemKey.of(Items.COBBLESTONE)),
                    "source_material_loaded_exactly_once");
            var state = new com.google.gson.Gson().fromJson(jobs.getFirst().continuation(), FactoryMachine.Snapshot.class);
            check(state.pc()==2 && state.delay()>0 && state.delay()<600,"program_resumed_after_get_with_remaining_wait");
            check(jobs.getFirst().parameters().size()==1,"recipe_parameter_mapping_survives_restart");
            report.put("restored_delay",state.delay());
            try {RestartExtras.restored(provider);}catch(Exception e){throw new RuntimeException(e);}
            phase=3;write("running");
        }
    }
    private void check(boolean value, String name) {
        if (!value) throw new IllegalStateException(name);
        report.put(name, true);
    }
    private void verifyPrimaryReturnGate() {
        var logic = provider.getLogic();
        var inventory = provider.getMainNode().getGrid().getStorageService().getInventory();
        var action = new MachineSource(provider.getMainNode().getGrid()::getPivot);
        var pos = new BlockPos(0,100,-2);
        level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
        var barrel = (net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(pos);
        var cobble = AEItemKey.of(Items.COBBLESTONE);
        var stone = AEItemKey.of(Items.STONE);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        barrel.setItem(0,new ItemStack(Items.COBBLESTONE));
        barrel.setItem(1,new ItemStack(Items.STONE,4));
        barrel.setItem(2,new ItemStack(Items.GOLD_INGOT,2));
        provider.tags.reconcile(List.of("Batch"));
        check(provider.tags.tag("Batch",pos.asLong(),p->FactoryServer.contains(provider.factoryGrid(),BlockPos.of(p))),"batch_target_in_real_subnet");
        var recipe = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(cobble,1)),
                List.of(new GenericStack(stone,2),new GenericStack(gold,1)));
        String code = "import Batch\nget 1 P1 from source\nput P1 into Batch\nget 1 O2 from Batch\nput O2 into source\nwait 1 tick\nget 1 O1 from Batch\nput O1 into source\nwait 1 tick\nget 1 O1 from Batch\nput O1 into source\nwait 20 tick\ndone";
        var stack = ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(code,provider.factoryId(),recipe));
        logic.getPatternInv().setItemDirect(1,stack);
        logic.getConfigManager().putSetting(appeng.api.config.Settings.BLOCKING_MODE,appeng.api.config.YesNo.YES);
        var details = (FactoryPatternDetails)PatternDetailsHelper.decodePattern(stack,level);
        var input = new appeng.api.stacks.KeyCounter(); input.add(cobble,1);
        check(logic.pushPattern(details,new appeng.api.stacks.KeyCounter[]{input}),"first_single_recipe_admitted");
        check(!logic.pushPattern(details,new appeng.api.stacks.KeyCounter[]{input}),"second_recipe_rejected_before_primary_return");
        logic.tick();
        check(barrel.getItem(0).getCount()==2,"existing_machine_input_does_not_block_current_batch");
        check(inventory.getAvailableStacks().get(gold)==1 && logic.isBusy(),"byproduct_return_does_not_unlock");
        inventory.insert(stone,9,Actionable.MODULATE,action);
        check(logic.isBusy(),"unrelated_main_network_product_does_not_unlock");
        inventory.extract(stone,9,Actionable.MODULATE,action);
        logic.tick();
        check(inventory.getAvailableStacks().get(stone)==1 && logic.isBusy(),"partial_primary_return_does_not_unlock");
        var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var saved=logic.saveJobs().getFirst();
        var encoded=FactoryJob.Saved.CODEC.encodeStart(ops,saved).getOrThrow();
        check(FactoryJob.restore(provider,FactoryJob.Saved.CODEC.parse(ops,encoded).getOrThrow()).awaitingPrimaryReturn(),"partial_primary_debt_survives_codec_restore");
        logic.tick();
        check(inventory.getAvailableStacks().get(stone)==2 && logic.isBusy() && logic.saveJobs().size()==1,
                "full_primary_return_keeps_gate_closed_until_tail_finishes");
        check(!FactoryJob.restore(provider,logic.saveJobs().getFirst()).awaitingPrimaryReturn(),"paid_primary_debt_stays_paid_after_restore");
        check(!logic.pushPattern(details,new appeng.api.stacks.KeyCounter[]{input})&&logic.saveJobs().size()==1,
                "next_recipe_rejected_while_previous_tail_runs");
        for(int i=0;i<20;i++)logic.tick();
        check(logic.saveJobs().isEmpty()&&!logic.isBusy(),"complete_code_and_resource_cleanup_release_gate");
        check(logic.pushPattern(details,new appeng.api.stacks.KeyCounter[]{input})&&logic.saveJobs().size()==1,
                "next_recipe_admitted_only_after_full_round_completion");
        for(int i=0;i<30;i++)logic.tick();
        check(logic.saveJobs().isEmpty() && inventory.getAvailableStacks().get(stone)==4
                && inventory.getAvailableStacks().get(gold)==2,"both_jobs_finish_without_duplicate_returns");
        inventory.extract(stone,4,Actionable.MODULATE,action);
        inventory.extract(gold,2,Actionable.MODULATE,action);
        logic.getPatternInv().setItemDirect(1,ItemStack.EMPTY);
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        provider.tags.reconcile(List.of("F"));
        provider.tags.tag("F",furnacePos.asLong(),p->FactoryServer.contains(provider.factoryGrid(),BlockPos.of(p)));
    }
    private void write(String status) {
        report.put("status",status); report.put("phase",phase); report.put("ticks",ticks);
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(System.getProperty("ae2lf.probe.reportDir")));
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("native-cpu-report.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch(java.io.IOException failure) { throw new RuntimeException(failure); }
    }
    void tick() {
        if (phase==99) return;
        try {
            if (++ticks>1000) throw new IllegalStateException("Native crafting timed out");
            if (phase==0) {
                level.setBlockAndUpdate(cpuPos, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
                level.setBlockAndUpdate(drivePos, AEBlocks.DRIVE.block().defaultBlockState());
                ((DriveBlockEntity)level.getBlockEntity(drivePos)).getInternalInventory().setItemDirect(0,
                        ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());
                level.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
                ((FurnaceBlockEntity)level.getBlockEntity(furnacePos)).setItem(1,new ItemStack(Items.COAL));
                provider.tags.reconcile(List.of("F"));
                check(provider.tags.tag("F",furnacePos.asLong(),p->FactoryServer.contains(provider.factoryGrid(),BlockPos.of(p))),"furnace_is_in_subnet");
                String code="import F\nget 1 P1 from source\nput P1 into F on up\nwhile F has minecraft:stone < 1 do\n    wait 1 tick\nget 1 minecraft:stone from F on down\nput minecraft:stone into source\nwait 40 tick\ndone";
                if (blockingProbe) {
                    provider.getLogic().getConfigManager().putSetting(appeng.api.config.Settings.BLOCKING_MODE, appeng.api.config.YesNo.YES);
                    var furnace = (FurnaceBlockEntity) level.getBlockEntity(furnacePos);
                    furnace.setItem(0, ItemStack.EMPTY);
                    furnace.setItem(1, ItemStack.EMPTY);
                    code = "import F\nfunc feed\n    if source has P1 > 0 do\n        get 1 P1 from source\n        put P1 into F on up\n        wait 1 tick\n        feed\nend\nfeed\nwhile F has minecraft:stone < 1 do\n    wait 1 tick\nget 1 minecraft:stone from F on down\nput minecraft:stone into source\nwait 40 tick\ndone";
                }
                if (Boolean.getBoolean("ae2lf.probe.sfmCpu")) code="EVERY TICK DO INPUT MUST 1 P1 FROM source OUTPUT P1 TO F TOP SIDE FORGET INPUT MUST 1 minecraft:stone FROM F BOTTOM SIDE OUTPUT minecraft:stone TO source END";
                code=code.replace("minecraft:stone","O1");
                if (Boolean.getBoolean("ae2lf.probe.selectorCpu")) {
                    // Same pipeline, expressed only with the aggregated selector syntax so the real CPU
                    // dispatch, the provider round and the blocking gate are exercised by the new operators.
                    code = blockingProbe
                            ? "import F\nfunc feed\n    if source has P1&#minecraft:stone_crafting_materials > 0 do\n"
                              + "        get 1 P1&#minecraft:stone_crafting_materials from source\n"
                              + "        put (P1&#minecraft:stone_crafting_materials)!(minecraft:dirt) into F on up\n"
                              + "        wait 1 tick\n        feed\nend\nfeed\n"
                              + "while F has minecraft:st?ne < 1 do\n    wait 1 tick\n"
                              + "get 1 minecraft:*one from F on down\nput minecraft:st* into source\nwait 40 tick\ndone"
                            : "import F\nget 1 P1&#minecraft:stone_crafting_materials from source\n"
                              + "put (P1&#minecraft:stone_crafting_materials)!(minecraft:dirt) into F on up\n"
                              + "while F has minecraft:st?ne < 1 do\n    wait 1 tick\n"
                              + "get 1 minecraft:*one from F on down\nput minecraft:st* into source\nwait 40 tick\ndone";
                }
                if (RestartState.PREPARE) code=code.replace("put P1 into F", "wait 600 tick\nput P1 into F");
                var recipe=PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE),1)),
                        List.of(new GenericStack(AEItemKey.of(Items.STONE),1)));
                var pattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
                pattern.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(code,provider.factoryId(),recipe));
                check(provider.getLogic().accepts(pattern),"provider_accepts_bound_factory_pattern");
                provider.getLogic().getPatternInv().setItemDirect(0,pattern);
                provider.getLogic().updatePatterns();
                phase=1;write("running");return;
            }
            var grid=provider.getMainNode().getGrid();
            var cpu=((CraftingBlockEntity)level.getBlockEntity(cpuPos)).getCluster();
            if(grid==null||cpu==null||!provider.getMainNode().isActive())return;
            var inventory=grid.getStorageService().getInventory();
            var action=new MachineSource(grid::getPivot);
            if(phase==1) {
                if(provider.getLogic().getAvailablePatterns().isEmpty() || ticks<30)return;
                if (blockingProbe) verifyPrimaryReturnGate();
                int batches = blockingProbe ? 2 : 1;
                check(inventory.insert(AEItemKey.of(Items.COBBLESTONE),batches,Actionable.MODULATE,action)==batches,"exact_raw_material_inserted");
                check(inventory.getAvailableStacks().get(AEItemKey.of(Items.STONE))==0,"no_stone_preloaded");
                future=grid.getCraftingService().beginCraftingCalculation(level,()->action,AEItemKey.of(Items.STONE),batches,CalculationStrategy.REPORT_MISSING_ITEMS);
                phase=2;write("running");return;
            }
            if(phase==2) {
                if(!future.isDone())return;
                var plan=future.get();
                report.put("planned_bytes",plan==null?-1:plan.bytes());
                check(plan!=null&&!plan.simulation()&&plan.missingItems().isEmpty(),"native_plan_has_no_missing_material");
                var submission=grid.getCraftingService().submitJob(plan,null,cpu,false,action);
                check(submission.successful(),"native_cpu_submission_accepted");
                phase=3;write("running");return;
            }
            if(phase==3) {
                if (blockingProbe && !blockingReleased) {
                    if (ticks < 80) return;
                    var jobs = provider.getLogic().saveJobs();
                    check(jobs.size()==1 && jobs.getFirst().input().isEmpty(),"only_one_native_cpu_recipe_dispatched");
                    var furnace=(FurnaceBlockEntity)level.getBlockEntity(furnacePos);
                    check(furnace.getItem(0).getCount()==1 && furnace.getItem(2).isEmpty() && provider.getLogic().isBusy(),
                            "next_batch_waits_for_primary_not_machine_admission");
                    check(cpu.isBusy(),"native_cpu_pending_until_primary_returns");
                    furnace.setItem(1,new ItemStack(Items.COAL));
                    blockingReleased = true;
                    write("running");
                    return;
                }
                if(RestartState.PREPARE && ticks>=80) {
                    if(!RestartExtras.readyForSave(provider))return;
                    var jobs=provider.getLogic().saveJobs();
                    check(jobs.size()==1 && jobs.getFirst().input().size()==1,"unfinished_job_retains_source_before_shutdown");
                    report.put("world",level.getServer().getWorldData().getLevelName());
                    report.put("saved_continuation",jobs.getFirst().continuation());
                    java.nio.file.Files.writeString(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("ae2lf-probe-owned.txt"),
                            "Created only by AE2LF isolated runtime fixture");
                    level.getServer().saveEverything(false,true,true);
                    write("restart_ready");phase=99;RestartState.exitRequested=true;return;
                }
                long stone=inventory.getAvailableStacks().get(AEItemKey.of(Items.STONE));
                if(stone < (blockingProbe ? 2 : 1))return;
                if(!provider.getLogic().saveJobs().isEmpty())return;
                check(stone==(blockingProbe ? 2 : 1),"exact_requested_primary_returned_to_main_network");
                check(inventory.getAvailableStacks().get(AEItemKey.of(Items.COBBLESTONE))==0,"raw_material_consumed_exactly_once");
                var furnace=(FurnaceBlockEntity)level.getBlockEntity(furnacePos);
                check(furnace.getItem(0).isEmpty()&&furnace.getItem(2).isEmpty(),"furnace_input_output_empty_after_return");
                check(!cpu.isBusy(),"native_cpu_job_finished");
                if (blockingProbe) check(provider.getLogic().saveJobs().isEmpty(), Boolean.getBoolean("ae2lf.probe.sfmCpu")?"sfm_provider_job_finishes":"recursive_function_returns_and_provider_job_finishes");
                if (blockingProbe) {
                    // A cleared, supported gallery avoids random world foliage and falling during visual capture.
                    for (int x=0;x<=8;x++) for(int z=4;z<=11;z++) {
                        level.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState());
                        for(int y=101;y<=107;y++) level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    var display = java.util.List.of(FactoryContent.PROVIDER.get(), FactoryContent.TERMINAL.get(), FactoryContent.CABLE.get());
                    for (int i=0; i<display.size(); i++) {
                        var pos = new BlockPos(2+i*2,101,4);
                        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(pos, display.get(i).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.SOUTH));
                    }
                    var player = level.getServer().getPlayerList().getPlayers().getFirst();
                    player.closeContainer();
                    player.getAbilities().flying=true;
                    player.onUpdateAbilities();
                    player.connection.teleport(4.5,102,10,180f,20f);
                    RestartState.visualReady=true;
                    report.put("post_acceptance_visual_display", "provider, terminal and cable facing south; separate from native CPU fixture");
                }
                if(RestartState.RESUME)RestartExtras.complete(provider);
                write("passed");phase=99;
                if (RestartState.RESUME) RestartState.exitRequested=true;
            }
        } catch(Throwable failure) {
            report.put("failure",failure.toString());report.put("stack",java.util.Arrays.toString(failure.getStackTrace()));
            write("failed");phase=99;RestartState.exitRequested=true;
        }
    }
}
