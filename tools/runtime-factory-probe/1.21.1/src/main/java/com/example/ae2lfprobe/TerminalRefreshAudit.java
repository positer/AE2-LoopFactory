package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.nio.file.*;

/** Production terminal lifecycle, real capabilities/redstone and binary NBT, in a separate native world.
 * Private tick invocation keeps each lifecycle transition observable within one server-thread callback.
 */
final class TerminalRefreshAudit {
    private static final Map<String,Object> checks = new LinkedHashMap<>();
    private static FactoryBlockEntity host;
    private static ServerLevel level;
    private static BlockPos source, oldTarget, newTarget, signal;
    private static final String OLD = "import InputChest,OldTarget,NewTarget\nchannel\n    get 1 minecraft:iron_ingot from InputChest\n    put 1 minecraft:iron_ingot into OldTarget\nwait 40 tick\ndone";
    private static final String NEW = OLD.replace("into OldTarget", "into NewTarget");
    static void run(FactoryBlockEntity terminal, BlockPos src, BlockPos oldDst, BlockPos newDst) throws Exception {
        host=terminal;level=(ServerLevel)host.getLevel();source=src;oldTarget=oldDst;newTarget=newDst;
        signal=host.getBlockPos().south();
        try {
            host.factoryPatterns().setItemDirect(0,ItemStack.EMPTY);
            level.setBlockAndUpdate(signal,Blocks.AIR.defaultBlockState());tick();
            for(var pos:List.of(source,oldTarget,newTarget)) {
                level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
                ((Container)level.getBlockEntity(pos)).clearContent();
            }
            ((Container)level.getBlockEntity(source)).setItem(0,new ItemStack(Items.IRON_INGOT,64));
            host.tags.reconcile(List.of("InputChest","OldTarget","NewTarget"));
            for(var entry:Map.of("InputChest",source,"OldTarget",oldTarget,"NewTarget",newTarget).entrySet())
                check(host.tags.tag(entry.getKey(),entry.getValue().asLong(),p->FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))),"connected_"+entry.getKey());
            install(OLD);check(host.activeJobCount()==0,"changed_code_cancels_before_next_tick");tick();
            check(count(oldTarget)==1&&jobs().size()==1&&snapshot().delay()==40,"code_install_starts_once_without_signal");
            var original=jobs().getFirst();var before=original.save();install(OLD);
            check(jobs().getFirst()==original&&jobs().getFirst().save().equals(before),"identical_save_preserves_exact_continuation");
            for(int i=0;i<5;i++)tick();
            check(snapshot().delay()==35&&count(oldTarget)==1,"wait_makes_progress");
            level.setBlockAndUpdate(signal,Blocks.REDSTONE_BLOCK.defaultBlockState());tick();
            check(count(oldTarget)==2&&jobs().size()==1&&jobs().getFirst()!=original&&snapshot().delay()==40,"rising_edge_replaces_waiting_run");
            for(int i=0;i<5;i++)tick();
            check(count(oldTarget)==2&&snapshot().delay()==35,"held_high_does_not_restart");
            for(int i=0;i<32;i++) {
                level.setBlockAndUpdate(signal,Blocks.AIR.defaultBlockState());tick();
                level.setBlockAndUpdate(signal,Blocks.REDSTONE_BLOCK.defaultBlockState());tick();
                check(jobs().size()==1&&snapshot().delay()==40,"repeat_edge_single_run_"+i);
            }
            check(count(oldTarget)==34,"all_32_edges_restart_from_first_instruction");
            install(NEW);check(jobs().isEmpty(),"code_change_discards_old_jobs_immediately");tick();
            check(count(oldTarget)==34&&count(newTarget)==1&&jobs().size()==1&&snapshot().delay()==40,"changed_code_while_powered_starts_only_new_target");
            check(!jobs().getFirst().save().routes().contains("OldTarget"),"new_run_does_not_inherit_old_routes");
            install("func parked\n    wait 40 tick\nend\nparked\ndone");tick();
            check(!snapshot().returns().isEmpty(),"old_function_call_really_parked");
            install("import InputChest,OldTarget,NewTarget\nget must 1000000000000 minecraft:iron_ingot from InputChest\nput minecraft:iron_ingot into OldTarget\ndone");tick();
            check(snapshot().pendingTransfer()&&snapshot().transferRemaining()>0&&snapshot().returns().isEmpty(),"old_must_really_blocked_and_old_call_cleared");
            int oldCount=count(oldTarget);
            install("import InputChest,OldTarget,NewTarget\nwait 3 tick\ndone");tick();
            check(!snapshot().pendingTransfer()&&snapshot().returns().isEmpty()&&snapshot().delay()==3&&jobs().getFirst().save().routes().equals("[]"),"refresh_clears_must_debt_routes_and_call_stack");
            tick();tick();tick();
            check(jobs().isEmpty()&&count(oldTarget)==oldCount,"new_wait_finishes_without_old_must_resuming");
            // Model the observed eighteen persisted old loops using production Saved and BE codecs.
            var oldJob=new FactoryJob(host,new FactoryPatternData("while true do\n    wait 40 tick\ndone",host.factoryId(),ItemStack.EMPTY));
            oldJob.tick();var tag=state();
            tag.put("FactoryTerminalJobs",FactoryJob.Saved.CODEC.listOf().encodeStart(ops(),Collections.nCopies(18,oldJob.save())).getOrThrow());
            load(tag);check(jobs().size()==18,"eighteen_old_runs_loaded_from_nbt");
            level.setBlockAndUpdate(signal,Blocks.AIR.defaultBlockState());tick();
            level.setBlockAndUpdate(signal,Blocks.REDSTONE_BLOCK.defaultBlockState());tick();
            check(jobs().size()==1&&snapshot().delay()==3,"edge_replaces_all_eighteen_saved_runs");
            // A changed program awaiting ownership/tick survives disk serialization without rerunning old code.
            install("wait 17 tick\ndone");tag=roundTrip(state());load(tag);
            check(jobs().isEmpty(),"pending_restart_nbt_does_not_restore_old_jobs");tick();
            check(jobs().size()==1&&snapshot().delay()==17,"pending_restart_survives_binary_nbt");
            for(int i=0;i<4;i++)tick();
            before=jobs().getFirst().save();load(roundTrip(state()));
            check(jobs().getFirst().save().equals(before),"ordinary_reload_preserves_continuation");tick();
            check(snapshot().delay()==12,"held_high_reload_does_not_restart");
            host.setFactoryDraft("unsaved editor text");
            check(snapshot().delay()==12,"unsaved_draft_does_not_restart");
            // Inject explicitly accounted physical cargo, with an expected debt that must never become cargo.
            var saved=jobs().getFirst().save();
            var input=List.of(new GenericStack(AEItemKey.of(Items.DIAMOND),7));
            var output=List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT),11));
            var debts=List.of(new GenericStack(AEItemKey.of(Items.EMERALD),1_000_000_000_000L));
            var nativeRecords=List.of("audit:opaque-native-rollback-record");
            var cargoJob=new FactoryJob.Saved(saved.data(),input,saved.routes(),output,debts,saved.parameters(),saved.continuation(),saved.admissions(),nativeRecords);
            tag=state();tag.put("FactoryTerminalJobs",FactoryJob.Saved.CODEC.listOf().encodeStart(ops(),List.of(cargoJob)).getOrThrow());load(tag);
            host.pulse("OldTarget",100);check(host.powers(oldTarget),"old_pulse_is_active");
            install("wait 19 tick\ndone");
            check(!host.powers(oldTarget),"refresh_cancels_old_output_pulses");
            check(jobs().isEmpty()&&resources().equals(java.util.stream.Stream.concat(input.stream(),output.stream()).toList()),"only_actual_input_output_become_recovery");
            check(nativeCargo().equals(nativeRecords),"opaque_native_cargo_retained_verbatim");
            load(roundTrip(state()));tick();
            level.setBlockAndUpdate(signal,Blocks.AIR.defaultBlockState());tick();
            level.setBlockAndUpdate(signal,Blocks.REDSTONE_BLOCK.defaultBlockState());tick();
            check(resources().equals(java.util.stream.Stream.concat(input.stream(),output.stream()).toList())&&nativeCargo().equals(nativeRecords),"reload_and_second_refresh_do_not_duplicate_cargo");
            install("");tick();check(jobs().isEmpty()&&host.executionError().isEmpty(),"blank_code_stops_execution");
            level.setBlockAndUpdate(signal,Blocks.AIR.defaultBlockState());tick();
            install("EVERY REDSTONE PULSE DO INPUT 1 iron_ingot FROM InputChest OUTPUT TO NewTarget END");tick();
            check(jobs().size()==1&&jobs().getFirst().scheduled(),"sfm_installs_one_scheduler");
            original=jobs().getFirst();level.setBlockAndUpdate(signal,Blocks.REDSTONE_BLOCK.defaultBlockState());tick();
            check(jobs().size()==1&&jobs().getFirst()!=original&&jobs().getFirst().scheduled(),"signal_replaces_sfm_scheduler");
            original=jobs().getFirst();for(int i=0;i<5;i++)tick();
            check(jobs().getFirst()==original,"held_signal_keeps_sfm_scheduler");
            host.factoryPatterns().setItemDirect(0,ItemStack.EMPTY);tick();
            check(jobs().isEmpty(),"removing_pattern_stops_scheduler");
            checks.put("status","passed");
        } catch(Throwable failure) {checks.put("status","failed");checks.put("failure",failure.toString());throw failure;}
        finally {
            checks.put("scope","Real native terminal, chest capabilities and signal blocks; lifecycle ticks invoked on server thread; binary NBT reload; synthetic saved cargo explicitly injected. User save untouched.");
            Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("terminal-refresh-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(checks));
        }
    }
    private static void install(String code){var stack=com.example.ae2lightoptimizer.item.ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(code,host.factoryId(),ItemStack.EMPTY));host.factoryPatterns().setItemDirect(0,stack);}
    private static void tick() throws Exception {var method=FactoryBlockEntity.class.getDeclaredMethod("tickTerminal");method.setAccessible(true);method.invoke(host);checks.put("lastExecutionError",host.executionError());check(host.executionError().isEmpty(),"execution_error_empty");}
    @SuppressWarnings("unchecked") private static List<FactoryJob> jobs() throws Exception {var f=FactoryBlockEntity.class.getDeclaredField("terminalJobs");f.setAccessible(true);return (List<FactoryJob>)f.get(host);}
    private static FactoryMachine.Snapshot snapshot() throws Exception {return new com.google.gson.Gson().fromJson(jobs().getFirst().save().continuation(),FactoryMachine.Snapshot.class);}
    private static int count(BlockPos pos){var c=(Container)level.getBlockEntity(pos);int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(Items.IRON_INGOT))n+=c.getItem(i).getCount();return n;}
    private static com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops(){return level.registryAccess().createSerializationContext(NbtOps.INSTANCE);}
    private static CompoundTag state(){var tag=new CompoundTag();host.saveAdditional(tag,level.registryAccess());return tag;}
    private static void load(CompoundTag tag){host.loadTag(tag,level.registryAccess());}
    private static CompoundTag roundTrip(CompoundTag tag) throws Exception {var bytes=new java.io.ByteArrayOutputStream();net.minecraft.nbt.NbtIo.write(tag,new java.io.DataOutputStream(bytes));return net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));}
    private static List<GenericStack> resources(){return GenericStack.CODEC.listOf().parse(ops(),((CompoundTag)state().get("FactoryResourceReturns")).get("resources")).getOrThrow();}
    private static List<String> nativeCargo(){return FactoryPatternData.LARGE_TEXT.listOf().parse(ops(),((CompoundTag)state().get("FactoryResourceReturns")).get("nativeRecovery")).getOrThrow();}
    private static void check(boolean valid,String name){checks.put(name,valid);if(!valid)throw new IllegalStateException(name);}
}
