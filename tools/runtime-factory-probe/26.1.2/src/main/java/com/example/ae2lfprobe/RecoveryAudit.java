package com.example.ae2lfprobe;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.util.SettingsFrom;
import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Actual removals and native placements; cancelled jobs never run again, only owned resources return. */
public final class RecoveryAudit {
    public static volatile boolean finished,passed;
    private static final long Q=Long.MAX_VALUE-4096;
    private static final BigInteger TWO_Q=BigInteger.valueOf(Q).multiply(BigInteger.TWO);
    private static final AEKey IRON=AEItemKey.of(Items.IRON_INGOT),COBBLE=AEItemKey.of(Items.COBBLESTONE),STONE=AEItemKey.of(Items.STONE);
    private static final com.google.gson.Gson GSON=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private static final List<Lane> lanes=new ArrayList<>();
    private static final List<Map<String,Object>> cases=new ArrayList<>();
    private static ServerLevel level;private static ServerPlayer player;private static Item inductionCard;private static AEKey fe;
    private static int ticks,index,phase,phaseStarted,configuredTickRate=20,worldRemovals,wrenchRemovals,emptyRemovals;
    private static long startedNanos,caseNanos;private static boolean started;private static Map<String,Object> row;
    private static final BlockPos LONG_PROGRAM_POS=new BlockPos(1408,100,480);
    private static final Map<String,Object> longProgramPreflight=new LinkedHashMap<>();
    private static final List<Map<String,Object>> entityReadiness=new ArrayList<>();
    private static ItemStack longProgramPattern;
    private static final class Lane {
        BlockPos pos;FactoryBlockEntity host,old,empty;DriveBlockEntity sourceDrive,mainDrive;
        MEStorage source,main;AEKey key;String id;ItemStack pattern,carrier;List<ItemStack> inventoryBefore;
        final List<ItemStack> configurationEscrow=new ArrayList<>();
    }
    public static int timeoutTicks(){return 1200;}
    public static void start(ServerLevel world) {
        if(started)return;started=true;level=world;startedNanos=System.nanoTime();
        try {
            configuredTickRate=StressAudit.configureTickRate(world);player=world.getServer().getPlayerList().getPlayers().getFirst();
            inductionCard=BuiltInRegistries.ITEM.stream().filter(i->BuiltInRegistries.ITEM.getKey(i).toString().equals("appflux:induction_card")).findFirst().orElseThrow();
            var energy=Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
            fe=(AEKey)Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey").getMethod("of",energy).invoke(null,energy.getField("FE").get(null));
            for(int i=0;i<3;i++) {
                var lane=new Lane();lane.pos=new BlockPos(1280+i*32,100,480);lane.key=i==2?fe:IRON;
                preloadEntityArea(lane.pos);preloadEntityArea(lane.pos.east(10));
                place(lane.pos.below(),Blocks.STONE.defaultBlockState());place(lane.pos,FactoryContent.PROVIDER.get().defaultBlockState().setValue(BlockStateProperties.FACING,Direction.SOUTH));
                lane.host=(FactoryBlockEntity)level.getBlockEntity(lane.pos);lane.id=lane.host.factoryId();
                power(lane.pos.west());lane.mainDrive=drive(lane.pos.east());
                place(lane.pos.south(),FactoryContent.CABLE.get().defaultBlockState());place(lane.pos.south(2),FactoryContent.CABLE.get().defaultBlockState());
                lane.sourceDrive=drive(lane.pos.south(3));power(lane.pos.south(4));
                place(lane.pos.east(10),FactoryContent.PROVIDER.get().defaultBlockState());lane.empty=(FactoryBlockEntity)level.getBlockEntity(lane.pos.east(10));lanes.add(lane);
            }
            preloadEntityArea(LONG_PROGRAM_POS);
        }catch(Throwable error){finish(error);}
    }
    private static void place(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);level.setBlockAndUpdate(pos,state);}
    private static void preloadEntityArea(BlockPos pos){for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.setChunkForced((pos.getX()>>4)+dx,(pos.getZ()>>4)+dz,true);}
    private static boolean entityAreaReady(BlockPos pos,String stage) {
        var samples=new ArrayList<Map<String,Object>>();boolean ready=true;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            int x=(pos.getX()>>4)+dx,z=(pos.getZ()>>4)+dz;
            long key=(x&0xffffffffL)|((z&0xffffffffL)<<32);
            boolean loaded=level.areEntitiesLoaded(key),ticking=level.isPositionEntityTicking(new BlockPos(x*16+8,pos.getY(),z*16+8));
            ready&=loaded&&ticking;
            var sample=new LinkedHashMap<String,Object>();sample.put("chunkX",x);sample.put("chunkZ",z);sample.put("entitiesLoaded",loaded);sample.put("entityTicking",ticking);samples.add(sample);
        }
        if(ready||ticks%10==0){var sample=new LinkedHashMap<String,Object>();sample.put("serverTick",ticks);sample.put("stage",stage);sample.put("position",pos.toShortString());sample.put("ready",ready);sample.put("chunks",samples);entityReadiness.add(sample);}
        return ready;
    }
    private static void power(BlockPos pos){place(pos,AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());((EnergyCellBlockEntity)level.getBlockEntity(pos)).injectAEPower(1_000_000,Actionable.MODULATE);}
    private static DriveBlockEntity drive(BlockPos pos){var b=AEBlocks.DRIVE.block();place(pos,b.getOrientationStrategy().setFacing(b.defaultBlockState(),Direction.EAST));var d=(DriveBlockEntity)level.getBlockEntity(pos);d.getInternalInventory().setItemDirect(0,ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());return d;}
    private static void check(boolean value,String label){if(row!=null)row.put(label,value);if(!value)throw new IllegalStateException(label);}
    @SuppressWarnings("unchecked") private static List<FactoryJob> jobs(FactoryBlockEntity host)throws ReflectiveOperationException {var f=FactoryProviderLogic.class.getDeclaredField("jobs");f.setAccessible(true);return (List<FactoryJob>)f.get(host.getLogic());}
    private static boolean connected(Lane l){return l.host.getMainNode().isActive()&&l.host.factoryGrid()!=null&&l.host.isolated()&&FactoryServer.owner(l.host.factoryGrid())==l.host&&l.sourceDrive.getMainNode().isActive()&&l.mainDrive.getMainNode().isActive()&&l.sourceDrive.getMainNode().getGrid()==l.host.factoryGrid()&&l.mainDrive.getMainNode().getGrid()==l.host.getMainNode().getGrid();}
    private static void networks(Lane l){l.source=l.host.factoryGrid().getStorageService().getInventory();l.main=l.host.getMainNode().getGrid().getStorageService().getInventory();}
    private static long count(MEStorage s,AEKey k){return s.extract(k,Long.MAX_VALUE,Actionable.SIMULATE,IActionSource.empty());}
    private static void seed(MEStorage s,AEKey k,long n){check(s.insert(k,n,Actionable.MODULATE,IActionSource.empty())==n,"actual_fixture_supply_accepted");}
    private static BigInteger sum(List<GenericStack> values,AEKey key){var n=BigInteger.ZERO;for(var s:values){check(s.amount()>0,"all_physical_segments_positive");if(s.what().equals(key))n=n.add(BigInteger.valueOf(s.amount()));}return n;}
    private static List<GenericStack> owned(Lane l){var values=new ArrayList<GenericStack>();for(var j:l.host.getLogic().saveJobs()){values.addAll(j.input());values.addAll(j.output());}values.addAll(l.host.getLogic().induction().snapshot());return values;}
    private static void addOutputJob(Lane l)throws Exception{jobs(l.host).add(new FactoryJob(l.host,new FactoryPatternData("get must "+Q+" minecraft::item from storage\nput minecraft::item into source\ndone",l.id,ItemStack.EMPTY)));}
    private static void next(int n){phase=n;phaseStarted=ticks;}
    public static void tick() {
        if(!started||finished)return;
        try {
            if(++ticks>timeoutTicks())throw new IllegalStateException("Recovery audit timed out");if(ticks<100)return;
            if(index==3){longProgramPreflight();return;}var l=lanes.get(index);
            if(phase==0) {
                if(!entityAreaReady(l.pos,"before-case-"+index)||!entityAreaReady(l.empty.getBlockPos(),"before-empty-copy-"+index))return;
                if(!connected(l)){check(ticks<350,"initial_networks_connected_before_timeout");return;}
                row=new LinkedHashMap<>();cases.add(row);caseNanos=System.nanoTime();
                row.put("case",index==0?"segmented_cargo_above_long_max":index==1?"must_without_private_resources_cancels":"induction_resources_return");row.put("position",l.pos.toShortString());row.put("status","running");
                row.put("ownedTargetDecimal",index==0?TWO_Q.toString():index==1?"0":Long.toString(FactoryInduction.CAPACITY));networks(l);
                var recipe=PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(COBBLE,1)),List.of(new GenericStack(STONE,1)));
                l.pattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();l.pattern.set(FactoryPatternData.TYPE.get(),new FactoryPatternData("wait 600 tick\ndone",l.id,recipe));
                l.host.getLogic().getPatternInv().setItemDirect(0,l.pattern.copy());l.host.getLogic().updatePatterns();
                var upgrades=((IUpgradeableObject)(Object)l.host.getLogic()).getUpgrades();check(upgrades.isItemValid(0,inductionCard.getDefaultInstance()),"native_upgrade_inventory_accepts_card");upgrades.setItemDirect(0,inductionCard.getDefaultInstance());
                if(index==2)seed(l.main,fe,FactoryInduction.CAPACITY);
                else {
                    if(index==0) {
                        seed(l.main,IRON,Long.MAX_VALUE);seed(l.main,COBBLE,1);
                        check(l.main.extract(COBBLE,1,Actionable.MODULATE,IActionSource.empty())==1,"recipe_input_allocated_from_actual_me_inventory");
                        var allocated=new KeyCounter();allocated.add(COBBLE,1);
                        check(l.host.getLogic().pushPattern(PatternDetailsHelper.decodePattern(l.pattern,level),new KeyCounter[]{allocated}),"native_provider_admits_actual_one_item_input");
                    }
                    seed(l.source,IRON,index==0?Q:17);addOutputJob(l);
                }
                next(1);return;
            }
            if(ticks-phaseStarted<2)return;
            if(phase==1) {
                if(index==0){check(sum(owned(l),IRON).equals(BigInteger.valueOf(Q))&&count(l.source,IRON)==0,"first_whole_large_output_is_physically_buffered");seed(l.source,IRON,Q);addOutputJob(l);}
                next(2);return;
            }
            if(phase==2) {
                if(index==0){check(sum(owned(l),IRON).equals(TWO_Q)&&sum(owned(l),COBBLE).equals(BigInteger.ONE)&&count(l.main,IRON)==Long.MAX_VALUE,"two_long_sized_output_segments_and_actual_input_are_owned");}
                else if(index==1){check(owned(l).isEmpty()&&count(l.main,IRON)==17&&count(l.source,IRON)==0,"partial_must_has_no_private_resource_ownership");check(l.host.getLogic().saveJobs().size()==1,"must_job_is_waiting_before_cancellation");}
                else check(l.host.getLogic().saveJobs().isEmpty()&&sum(owned(l),fe).equals(BigInteger.valueOf(FactoryInduction.CAPACITY))&&count(l.main,fe)==0,"native_card_induced_real_fe_without_a_job");
                l.old=l.host;check(entities(l.pos).isEmpty(),"removal_area_initially_empty");
                if(index==1) {
                    l.inventoryBefore=inventorySnapshot();check(l.inventoryBefore.stream().filter(ItemStack::isEmpty).count()>=3,"player_has_room_for_native_wrench_drops");
                    var result=l.host.disassembleWithWrench(player,level,new BlockHitResult(Vec3.atCenterOf(l.pos),Direction.UP,l.pos,false),AEItems.CERTUS_QUARTZ_WRENCH.stack());
                    check(result.consumesAction()&&level.getBlockEntity(l.pos)==null,"actual_ae_wrench_disassembly_removes_machine");wrenchRemovals++;
                } else {check(level.destroyBlock(l.pos,true,player),"actual_world_destroy_block_succeeds");worldRemovals++;}
                row.put("immediateRemovalEntities",describeEntities(l.pos));next(3);return;
            }
            if(phase==3) {
                if(!entityAreaReady(l.pos,"settled-removal-"+index))return;
                row.put("settledRemovalEntities",describeEntities(l.pos));
                var released=takeWorldDrops(l.pos);if(index==1)released.addAll(takeInventoryDelta(l.inventoryBefore));
                row.put("actualReleasedItems",describeItems(released));
                check(itemCount(released,FactoryContent.PROVIDER_ITEM.get())==1&&itemCount(released,ModItems.LOOP_FACTORY_PATTERN.get())==1&&itemCount(released,inductionCard)==1,"one_original_machine_and_native_pattern_upgrade_drops");
                check(released.stream().allMatch(s->s.is(FactoryContent.PROVIDER_ITEM.get())||s.is(ModItems.LOOP_FACTORY_PATTERN.get())||s.is(inductionCard)),"no_loose_bulk_resources_or_expected_output_drops");
                l.carrier=released.stream().filter(s->s.is(FactoryContent.PROVIDER_ITEM.get())).findFirst().orElseThrow();
                for(var stack:released)if(!stack.is(FactoryContent.PROVIDER_ITEM.get()))l.configurationEscrow.add(stack);
                check(ItemStack.isSameItemSameComponents(l.pattern,l.configurationEscrow.stream().filter(s->s.is(ModItems.LOOP_FACTORY_PATTERN.get())).findFirst().orElseThrow()),"original_physical_pattern_preserved_separately");
                l.carrier=wireAndDisk(l.carrier);
                if(index==1)check(!hasCargo(l.carrier),"resource_free_must_returns_plain_machine_without_phantom_budget");
                else {
                    var cargo=carrierCargo(l.carrier);var chunks=decodeResources(cargo);row.put("carrierResourceSegments",chunks.size());
                    check(sum(chunks,l.key).equals(index==0?TWO_Q:BigInteger.valueOf(FactoryInduction.CAPACITY)),"carrier_owns_exact_physical_segments");
                    check(index!=0||sum(chunks,COBBLE).equals(BigInteger.ONE),"carrier_contains_unconsumed_input_but_not_output_debt");
                    check(chunks.stream().noneMatch(s->s.what().equals(STONE)),"expected_recipe_output_is_never_minted");
                    check(cargo.keySet().equals(Set.of("resources","nativeRecovery")),"carrier_contains_no_program_routes_parameters_or_expected_debt");
                    check(nativeRecords(cargo).isEmpty(),"no_synthetic_native_recovery_records");
                    check(l.old.getLogic().saveJobs().isEmpty()&&l.old.getLogic().induction().isEmpty(),"old_owner_physical_resources_cleared_after_drop");
                    var duplicate=new ArrayList<ItemStack>();l.old.addAdditionalDrops(level,l.pos,duplicate);check(duplicate.isEmpty(),"old_owner_cannot_release_carrier_twice");
                }
                l.empty.importSettings(SettingsFrom.MEMORY_CARD,l.carrier.getComponents(),player);
                check(pending(l.empty).isEmpty()&&l.empty.getLogic().induction().isEmpty()&&l.empty.getLogic().saveJobs().isEmpty(),"memory_card_import_does_not_own_resources");
                check(level.destroyBlock(l.empty.getBlockPos(),true),"memory_copy_target_actually_removed");row.put("immediateEmptyEntities",describeEntities(l.empty.getBlockPos()));next(4);return;
            }
            if(phase==4) {
                if(!entityAreaReady(l.empty.getBlockPos(),"settled-empty-"+index))return;
                var pos=l.empty.getBlockPos();row.put("settledEmptyEntities",describeEntities(pos));var drops=takeWorldDrops(pos);row.put("emptyActualDrops",describeItems(drops));
                check(drops.size()==1&&drops.getFirst().is(FactoryContent.PROVIDER_ITEM.get())&&drops.getFirst().getCount()==1&&!hasCargo(drops.getFirst()),"empty_machine_has_one_plain_drop_after_settled_ticks");
                emptyRemovals++;l.empty=nativePlace(pos,wireAndDisk(drops.getFirst()));
                check(pending(l.empty).isEmpty()&&l.empty.getLogic().induction().isEmpty()&&l.empty.getLogic().saveJobs().isEmpty(),"memory_copy_target_native_replacement_has_zero_return_resources");
                l.host=nativePlace(l.pos,l.carrier);
                check(l.host.getLogic().saveJobs().isEmpty()&&l.host.getLogic().getPatternInv().isEmpty()&&!((IUpgradeableObject)(Object)l.host.getLogic()).getUpgrades().isInstalled(inductionCard),"cancelled_jobs_and_separately_dropped_configuration_are_not_recreated");
                check(!l.host.factoryId().equals(l.id),"ordinary_new_machine_identity_does_not_resume_old_work");
                check(sum(pending(l.host),l.key).equals(index==0?TWO_Q:index==1?BigInteger.ZERO:BigInteger.valueOf(FactoryInduction.CAPACITY)),"only_real_owned_resources_enter_the_independent_return_queue");
                next(5);return;
            }
            if(phase==5) {
                if(!connected(l)){check(ticks-phaseStarted<200,"native_replacement_network_reconnects_before_timeout");return;}networks(l);
                check(l.host.getLogic().saveJobs().isEmpty(),"no_cancelled_program_restarts_after_network_reconnect");
                if(index==0){check(count(l.main,IRON)==Long.MAX_VALUE&&sum(pending(l.host),IRON).equals(TWO_Q),"saturated_network_preserves_both_long_segments");check(l.main.extract(IRON,Long.MAX_VALUE,Actionable.MODULATE,IActionSource.empty())==Long.MAX_VALUE,"remove_only_the_recorded_original_prefill");}
                else if(index==1)seed(l.source,IRON,Q-17);
                next(6);return;
            }
            if(phase==6&&index==0) {
                check(count(l.main,IRON)==Long.MAX_VALUE&&sum(pending(l.host),IRON).equals(TWO_Q.subtract(BigInteger.valueOf(Long.MAX_VALUE))),"first_return_fills_capacity_and_keeps_exact_above_long_remainder");
                check(l.main.extract(IRON,Long.MAX_VALUE,Actionable.MODULATE,IActionSource.empty())==Long.MAX_VALUE,"receive_first_real_return_batch");row.put("receivedFirstBatchDecimal",Long.toString(Long.MAX_VALUE));next(7);return;
            }
            check(l.host.getLogic().saveJobs().isEmpty()&&pending(l.host).isEmpty()&&l.host.getLogic().induction().isEmpty(),"cancelled_owner_finishes_with_zero_pending_ae_resources");
            check(pending(l.empty).isEmpty()&&l.empty.getLogic().saveJobs().isEmpty()&&l.empty.getLogic().induction().isEmpty(),"memory_copy_target_stays_empty_after_real_ticks");
            if(index==0) {
                var returned=BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(count(l.main,IRON)));
                check(returned.equals(TWO_Q)&&count(l.source,IRON)==0&&count(l.main,COBBLE)==1&&count(l.main,STONE)==0,"two_long_segments_plus_input_conserved_without_minting_expected_output");row.put("actualReturnedDecimal",returned.toString());
            } else if(index==1){check(count(l.source,IRON)==Q-17&&count(l.main,IRON)==17,"cancelled_must_leaves_new_source_supply_untouched");row.put("actualReturnedDecimal","0");}
            else {check(count(l.main,fe)==FactoryInduction.CAPACITY,"induction_cargo_returns_exactly_once_without_recreating_upgrade");row.put("actualReturnedDecimal",Long.toString(FactoryInduction.CAPACITY));}
            row.put("finalSource",count(l.source,l.key));row.put("finalDestination",count(l.main,l.key));row.put("conserved",true);row.put("status","passed");row.put("wallClockSeconds",(System.nanoTime()-caseNanos)/1_000_000_000.0);
            index++;phase=0;
        }catch(Throwable error){finish(error);}
    }
    private static List<ItemEntity> entities(BlockPos pos){return level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));}
    @SuppressWarnings("unchecked") private static void longProgramPreflight()throws Exception {
        row=longProgramPreflight;
        if(phase==0) {
            row.put("status","running");row.put("position",LONG_PROGRAM_POS.toShortString());
            if(!entityAreaReady(LONG_PROGRAM_POS,"before-long-program-removal"))return;
            place(LONG_PROGRAM_POS,FactoryContent.TERMINAL.get().defaultBlockState());
            var host=(FactoryBlockEntity)level.getBlockEntity(LONG_PROGRAM_POS);
            var field=FactoryBlockEntity.class.getDeclaredField("terminalJobs");field.setAccessible(true);
            var queued=(List<FactoryJob>)field.get(host);var unique=new HashSet<String>();
            for(int i=0;i<64;i++) {
                var prefix="wait 600 tick\n# unique program "+i+" ";
                var code=prefix+"x".repeat(FactoryCompiler.MAX_SOURCE_LENGTH-prefix.length()-5)+"\ndone";
                check(code.length()==65536&&unique.add(code),"all_64_programs_are_different_and_exactly_65536_characters");
                var data=new FactoryPatternData(code,host.factoryId(),ItemStack.EMPTY);
                queued.add(new FactoryJob(host,data));
                if(i==0){longProgramPattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();longProgramPattern.set(FactoryPatternData.TYPE.get(),data);host.factoryPatterns().setItemDirect(0,longProgramPattern.copy());}
            }
            check(queued.size()==64&&queued.stream().allMatch(j->{var s=j.save();return s.input().isEmpty()&&s.output().isEmpty()&&s.nativeRecovery().isEmpty();}),"all_64_valid_queued_programs_have_no_private_resources");
            row.put("queuedPrograms",64);row.put("charactersPerProgram",65536);row.put("totalSourceCharacters",64L*65536L);
            check(entities(LONG_PROGRAM_POS).isEmpty(),"long_program_removal_area_initially_empty");
            check(level.destroyBlock(LONG_PROGRAM_POS,true,player),"long_program_terminal_actually_removed");
            row.put("immediateRemovalEntities",describeEntities(LONG_PROGRAM_POS));next(1);return;
        }
        if(ticks-phaseStarted<2)return;
        if(!entityAreaReady(LONG_PROGRAM_POS,"settled-long-program-removal"))return;
        row.put("settledRemovalEntities",describeEntities(LONG_PROGRAM_POS));var drops=takeWorldDrops(LONG_PROGRAM_POS);
        row.put("actualReleasedItems",describeItems(drops));
        check(itemCount(drops,FactoryContent.TERMINAL_ITEM.get())==1&&itemCount(drops,ModItems.LOOP_FACTORY_PATTERN.get())==1&&drops.size()==2,"max_length_queue_drops_only_one_plain_terminal_and_one_physical_pattern");
        for(var stack:drops) {
            check(!hasCargo(stack),"zero_owned_resources_do_not_aggregate_queued_source_payload");
            var copy=wireAndDisk(stack);
            if(copy.is(FactoryContent.TERMINAL_ITEM.get()))check(copy.getComponentsPatch().isEmpty(),"empty_terminal_item_has_no_saved_task_components");
            else check(ItemStack.isSameItemSameComponents(copy,longProgramPattern),"original_single_max_length_pattern_survives_native_network_codec");
        }
        row.put("maxLengthQueuedTasksCancelledWithoutPayload",true);row.put("status","passed");finish(null);
    }
    private static List<Map<String,Object>> describeEntities(BlockPos pos){var result=new ArrayList<Map<String,Object>>();for(var e:entities(pos)){var m=new LinkedHashMap<String,Object>();m.put("uuid",e.getUUID().toString());m.put("position",e.position().toString());m.put("item",describeItem(e.getItem()));result.add(m);}return result;}
    private static Map<String,Object> describeItem(ItemStack s){var m=new LinkedHashMap<String,Object>();m.put("id",BuiltInRegistries.ITEM.getKey(s.getItem()).toString());m.put("count",s.getCount());m.put("components",s.getComponents().toString());return m;}
    private static List<Map<String,Object>> describeItems(List<ItemStack> items){return items.stream().filter(s->!s.isEmpty()).map(RecoveryAudit::describeItem).toList();}
    private static List<ItemStack> takeWorldDrops(BlockPos pos){var result=new ArrayList<ItemStack>();for(var e:entities(pos)){result.add(e.getItem().copy());e.discard();}return result;}
    private static int itemCount(List<ItemStack> values,Item item){return values.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    private static List<ItemStack> inventorySnapshot(){var result=new ArrayList<ItemStack>();for(int i=0;i<player.getInventory().getContainerSize();i++)result.add(player.getInventory().getItem(i).copy());return result;}
    private static List<ItemStack> takeInventoryDelta(List<ItemStack> before){var result=new ArrayList<ItemStack>();for(int i=0;i<before.size();i++){var a=before.get(i);var b=player.getInventory().getItem(i);if(a.isEmpty()){if(!b.isEmpty()){result.add(b.copy());player.getInventory().setItem(i,ItemStack.EMPTY);}}else{check(ItemStack.isSameItemSameComponents(a,b)&&b.getCount()>=a.getCount(),"wrench_preserves_existing_player_inventory");int delta=b.getCount()-a.getCount();if(delta>0){result.add(b.copyWithCount(delta));player.getInventory().setItem(i,a.copy());}}}return result;}
    private static boolean hasCargo(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);return data!=null&&data.copyTag().contains("Ae2lfFactoryRecovery");}
    private static CompoundTag carrierCargo(ItemStack stack){var tag=(CompoundTag)stack.get(DataComponents.CUSTOM_DATA).copyTag().get("Ae2lfFactoryRecovery");check(tag.keySet().equals(Set.of("version","kind","cargo")),"carrier_envelope_is_physical_only");return (CompoundTag)tag.get("cargo");}
    private static List<GenericStack> decodeResources(CompoundTag cargo){return GenericStack.CODEC.listOf().parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),cargo.get("resources")).getOrThrow();}
    private static List<String> nativeRecords(CompoundTag cargo){return FactoryPatternData.LARGE_TEXT.listOf().parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),cargo.get("nativeRecovery")).getOrThrow();}
    private static List<GenericStack> pending(FactoryBlockEntity host){return decodeResources((CompoundTag)stateTag(host).get("FactoryResourceReturns"));}
    private static CompoundTag stateTag(FactoryBlockEntity host){var tag=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess());host.saveAdditional(tag);return tag.buildResult();}
    private static FactoryBlockEntity nativePlace(BlockPos pos,ItemStack carrier){var result=((BlockItem)carrier.getItem()).place(new DirectionalPlaceContext(level,pos,Direction.NORTH,carrier,Direction.SOUTH));check(result.consumesAction()&&carrier.isEmpty(),"native_item_placement_consumes_single_machine");check(level.getBlockEntity(pos) instanceof FactoryBlockEntity,"native_item_placement_creates_real_machine");return (FactoryBlockEntity)level.getBlockEntity(pos);}
    private static ItemStack wireAndDisk(ItemStack original)throws IOException {
        var ops=level.registryAccess().createSerializationContext(NbtOps.INSTANCE);var encoded=ItemStack.CODEC.encodeStart(ops,original).getOrThrow();var bytes=new ByteArrayOutputStream();NbtIo.write((CompoundTag)encoded,new DataOutputStream(bytes));
        var restored=ItemStack.CODEC.parse(ops,NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))).getOrThrow();check(ItemStack.isSameItemSameComponents(original,restored),"real_machine_item_binary_nbt_round_trip");row.put("lastItemNbtBytes",bytes.size());
        var packet=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        try{ItemStack.OPTIONAL_STREAM_CODEC.encode(packet,restored);row.put("lastItemNetworkBytes",packet.readableBytes());var decoded=ItemStack.OPTIONAL_STREAM_CODEC.decode(packet);check(ItemStack.isSameItemSameComponents(restored,decoded)&&!packet.isReadable(),"real_machine_item_native_network_round_trip");return decoded;}finally{packet.release();}
    }
    private static void finish(Throwable failure){passed=failure==null;if(failure!=null&&row!=null){row.put("status","failed");row.put("failure",failure.toString());}var report=new LinkedHashMap<String,Object>();report.put("status",passed?"passed":"failed");report.put("targetCases",3);report.put("completedCases",index);report.put("cases",cases);report.put("longProgramPreflight",longProgramPreflight);report.put("entityReadiness",entityReadiness);report.put("configuredTickRate",configuredTickRate);report.put("serverTicks",ticks);report.put("wallClockSeconds",(System.nanoTime()-startedNanos)/1_000_000_000.0);report.put("worldDestroyRemovals",worldRemovals);report.put("nativeAeWrenchRemovals",wrenchRemovals);report.put("emptyMachineRemovalChecks",emptyRemovals);report.put("explosionExecuted",false);report.put("actualPickaxeClickExecuted",false);report.put("nativeRecoveryReplayTested",false);report.put("scope","Actual world destroyBlock and AE disassembleWithWrench removals, settled entity observations at least two real server ticks later, native BlockItem placement and FactoryServer resource return. Original jobs are cancelled; no CPU order or continuation resumption is claimed. Native rollback records have no replay decoder and are not counted as returned. This validates the observed item payloads, not arbitrary component-heavy keys above native packet limits.");if(failure!=null)report.put("failure",failure.toString());try{Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("huge-recovery-report.json"),GSON.toJson(report));}catch(IOException e){throw new UncheckedIOException(e);}finally{if(level!=null)level.getServer().tickRateManager().setTickRate(20);finished=true;}}
}
