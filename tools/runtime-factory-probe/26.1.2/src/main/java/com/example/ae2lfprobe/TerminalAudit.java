package com.example.ae2lfprobe;

import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import appeng.api.config.Actionable;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import java.util.*;
import java.nio.file.*;

public final class TerminalAudit {
    public static volatile boolean finished,passed;
    private static ServerLevel level;private static FactoryBlockEntity terminal;
    private static BarrelBlockEntity a,b;private static int ticks;
    private static FactoryBlockEntity dropProvider;
    private static final BlockPos POS=new BlockPos(168,100,40);
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    public static void start(ServerLevel world) {
        level=world;
        try {
            level.setChunkForced(POS.getX()>>4,POS.getZ()>>4,true);
            level.setBlockAndUpdate(POS,FactoryContent.TERMINAL.get().defaultBlockState());terminal=(FactoryBlockEntity)level.getBlockEntity(POS);
            level.setBlockAndUpdate(POS.west(),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
            ((EnergyCellBlockEntity)level.getBlockEntity(POS.west())).injectAEPower(1_000_000,Actionable.MODULATE);
            level.setBlockAndUpdate(POS.east(),FactoryContent.CABLE.get().defaultBlockState());
            level.setBlockAndUpdate(POS.east().above(),Blocks.BARREL.defaultBlockState());
            level.setBlockAndUpdate(POS.east().below(),Blocks.BARREL.defaultBlockState());
            a=(BarrelBlockEntity)level.getBlockEntity(POS.east().above());b=(BarrelBlockEntity)level.getBlockEntity(POS.east().below());
            a.setItem(0,new ItemStack(Items.IRON_INGOT,64));
        }catch(Throwable error){finish(error);}
    }
    private static void install(String code) {
        var pattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
        pattern.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(code,terminal.factoryId(),ItemStack.EMPTY));
        terminal.factoryPatterns().setItemDirect(0,pattern);
        terminal.setFactoryDraft(code);
        terminal.tags.reconcile(List.of("A","B"));
        terminal.tags.tag("A",a.getBlockPos().asLong(),p->FactoryServer.contains(terminal.factoryGrid(),BlockPos.of(p)));
        terminal.tags.tag("B",b.getBlockPos().asLong(),p->FactoryServer.contains(terminal.factoryGrid(),BlockPos.of(p)));
    }
    public static void tick() {
        if(level==null||finished)return;
        try {
            ++ticks;
            if(ticks==100) {
                install("func recurse\n    recurse\nend\nrecurse");
                level.setBlockAndUpdate(POS.north(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
            if(ticks==110) {
                check(terminal.executionError().contains("recursion exceeds 64"),"runaway_recursion_reports_error_without_crash");
                level.setBlockAndUpdate(POS.north(),Blocks.AIR.defaultBlockState());
            }
            if(ticks==120)install("import A,B\nget 1 minecraft:iron_ingot from A\nput minecraft:iron_ingot into B\ndone");
            if(ticks>=130&&ticks<150)level.setBlockAndUpdate(POS.north(),ticks%2==0?Blocks.REDSTONE_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState());
            if(ticks==160) {
                check(b.getItem(0).getCount()==10,"ten_pulses_execute_after_failed_job");
                install("EVERY 2 TICKS DO INPUT 1 iron_ingot FROM A OUTPUT TO B END");
            }
            if(ticks==200) {
                check(b.getItem(0).getCount()==30,"sfm_timer_runs_twenty_times_without_redstone");
                install("EVERY REDSTONE PULSE DO INPUT 1 iron_ingot FROM A OUTPUT TO B END");
            }
            if(ticks==210)level.setBlockAndUpdate(POS.north(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            if(ticks==230) {
                check(b.getItem(0).getCount()==31,"sfm_held_redstone_only_fires_once");
                check(a.getItem(0).getCount()+b.getItem(0).getCount()==64,"terminal_has_no_hidden_material_loss");
                level.setBlockAndUpdate(POS.north(),Blocks.AIR.defaultBlockState());
                install("import A,B\nget must 1 minecraft:copper_ingot from A\nput minecraft:copper_ingot into B\ndone");
            }
            if(ticks==231)level.setBlockAndUpdate(POS.north(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            if(ticks==232)level.setBlockAndUpdate(POS.north(),Blocks.AIR.defaultBlockState());
            if(ticks==233){a.setItem(1,new ItemStack(Items.GOLD_INGOT));install("import A,B\nget 1 minecraft:gold_ingot from A\nput minecraft:gold_ingot into B\ndone");}
            if(ticks==234)level.setBlockAndUpdate(POS.north(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            if(ticks==238)check(b.getItem(1).is(Items.GOLD_INGOT)&&b.getItem(1).getCount()==1&&!terminal.waitingStatus().isEmpty(),"terminal_later_task_finishes_while_first_waits_on_copper");
            if(ticks==240) {
                level.destroyBlock(POS,true);
                var pos=POS.east(8);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
                level.setBlockAndUpdate(pos,FactoryContent.PROVIDER.get().defaultBlockState());dropProvider=(FactoryBlockEntity)level.getBlockEntity(pos);
                var front=dropProvider.getFront();
                level.setBlockAndUpdate(pos.relative(front),FactoryContent.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(pos.relative(front.getOpposite()),AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
                ((EnergyCellBlockEntity)level.getBlockEntity(pos.relative(front.getOpposite()))).injectAEPower(1_000_000,Actionable.MODULATE);
            }
            if(ticks==242) {
                check(dropped(POS,ModItems.LOOP_FACTORY_PATTERN.get())==1,"terminal_drops_its_physical_pattern_once");
                check(dropped(POS,FactoryContent.TERMINAL_ITEM.get())==1,"terminal_block_returns_on_break");
                check(level.getBlockEntity(POS)==null,"terminal_waiting_job_owner_is_removed");
            }
            if(ticks==340) {
                var recipe=appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE),1)),List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.STONE),1)));
                var stack=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
                stack.set(FactoryPatternData.TYPE.get(),new FactoryPatternData("wait 600 tick\ndone",dropProvider.factoryId(),recipe));
                dropProvider.getLogic().getPatternInv().setItemDirect(0,stack);dropProvider.getLogic().updatePatterns();
                var pattern=appeng.api.crafting.PatternDetailsHelper.decodePattern(stack,level);
                var input=new appeng.api.stacks.KeyCounter();input.add(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE),1);
                check(dropProvider.getLogic().pushPattern(pattern,new appeng.api.stacks.KeyCounter[]{input}),"provider_accepts_concrete_allocated_input_before_break");
            }
            if(ticks==350) {
                check(dropProvider.getLogic().saveJobs().size()==1&&dropProvider.getLogic().executionError().isEmpty(),"finite_recipe_wait_runs_without_error");
                var pos=dropProvider.getBlockPos();level.destroyBlock(pos,true);
            }
            if(ticks==352) {
                var pos=dropProvider.getBlockPos();
                check(dropped(pos,Items.COBBLESTONE)==0,"provider_input_remains_in_recovery_carrier");
                check(dropped(pos,ModItems.LOOP_FACTORY_PATTERN.get())==1,"provider_drops_encoded_pattern_once");
                check(dropped(pos,FactoryContent.PROVIDER_ITEM.get())==1,"provider_block_returns_on_break");
                verifyResourceCarrier(pos);
                finish(null);
            }
        }catch(Throwable error){finish(error);}
    }
    private static void verifyResourceCarrier(BlockPos pos) {
        var entities=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2));
        check(entities.size()==2,"provider_has_only_physical_pattern_and_machine_drops");
        var carrier=entities.stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).filter(stack->stack.is(FactoryContent.PROVIDER_ITEM.get())).findFirst().orElseThrow();
        var data=carrier.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        check(data!=null,"provider_carrier_has_private_resource_data");
        var envelope=net.minecraft.nbt.CompoundTag.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,data.copyTag().get("Ae2lfFactoryRecovery")).getOrThrow();
        var cargo=net.minecraft.nbt.CompoundTag.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,envelope.get("cargo")).getOrThrow();
        var ops=level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var resources=appeng.api.stacks.GenericStack.CODEC.listOf().parse(ops,cargo.get("resources")).getOrThrow();
        var nativePending=FactoryPatternData.LARGE_TEXT.listOf().parse(ops,cargo.get("nativeRecovery")).getOrThrow();
        check(resources.size()==1&&resources.getFirst().what().equals(appeng.api.stacks.AEItemKey.of(Items.COBBLESTONE))&&resources.getFirst().amount()==1&&nativePending.isEmpty(),"provider_retains_exactly_one_real_cobble_not_expected_stone");
        check(envelope.get("state")==null,"provider_carrier_does_not_export_old_program_or_task_state");
        evidence.put("providerPhysicalRecoveryCargo",cargo.toString());
    }
    private static int dropped(BlockPos pos,Item item) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2)).stream()
                .filter(entity->entity.getItem().is(item)).mapToInt(entity->entity.getItem().getCount()).sum();
    }
    private static void check(boolean ok,String key){evidence.put(key,ok);if(!ok)throw new IllegalStateException(key);}
    private static void finish(Throwable error) {
        passed=error==null;evidence.put("status",passed?"passed":"failed");evidence.put("ticks",ticks);
        if(error!=null)evidence.put("failure",error.toString());
        try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("terminal-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
        catch(Exception e){throw new RuntimeException(e);}finally{finished=true;}
    }
}
