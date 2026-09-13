package com.example.ae2lfprobe;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Native ticket removal, actual chunk absence, then normal chunk-NBT reconstruction. */
public final class ChunkLifecycleAudit {
    public static volatile boolean finished, passed;
    private static final BlockPos OWNER = new BlockPos(2404,100,488), REMOTE = OWNER.east(512);
    private static final BlockPos MAIN = OWNER.west(), RECEIPT = OWNER.south(2), TAGGED = REMOTE.west().north();
    private static final AEKey IRON = AEItemKey.of(Items.IRON_INGOT), GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Map<String,Object>> cases = new ArrayList<>(), power = new ArrayList<>();
    private static final Set<Long> fixtureChunks = new LinkedHashSet<>(), ownedTickets = new LinkedHashSet<>();
    private static ServerLevel level;
    private static ServerPlayer player;
    private static FactoryBlockEntity host, oldHost;
    private static DriveBlockEntity remoteDrive, oldRemote;
    private static String id;
    private static Map<String,Object> row;
    private static FactoryJob.Saved beforeOwnerUnload;
    private static ItemStack carrier;
    private static int ticks, phase, phaseStarted, completed, heldTicks;
    private static boolean started;
    private static long startedNanos;

    public static int timeoutTicks() { return 6000; }
    public static void begin(ServerLevel world, ServerPlayer testPlayer) {
        if (started) return;
        started = true; level = world; player = testPlayer; startedNanos = System.nanoTime();
        try {
            check(player.level() == level && playersDistant(), "all_players_begin_over_512_blocks_from_fixture");
            check((REMOTE.getX()>>4)-(OWNER.getX()>>4)>ChunkLevel.MAX_LEVEL-ChunkMap.FORCED_TICKET_LEVEL, "remote_is_beyond_native_forced_ticket_generation_dependency_radius");
            place(OWNER.below(), Blocks.STONE.defaultBlockState());
            place(OWNER, FactoryContent.PROVIDER.get().defaultBlockState().setValue(BlockStateProperties.FACING,Direction.EAST));
            host = (FactoryBlockEntity) loadedBE(OWNER); id = host.factoryId();
            drive(MAIN); remoteDrive = drive(REMOTE);
            for (int x=1; x<512; x++) place(OWNER.east(x), FactoryContent.CABLE.get().defaultBlockState());
            for (int n=1; n<=4; n++) battery(OWNER.north(n));
            battery(REMOTE.east());
            place(TAGGED, Blocks.BARREL.defaultBlockState());
            ((Container) loadedBE(TAGGED)).setItem(0, new ItemStack(Items.DIAMOND,7));
            place(RECEIPT, Blocks.BARREL.defaultBlockState());
            newCase("remote_storage_unload_must");
            writeReport(null);
        } catch (Throwable error) { finish(error); }
    }

    public static void tick() {
        if (!started || finished) return;
        try {
            if (++ticks % 20 == 0 && (phase==2 || phase==7 || phase==15)) observation("waiting_native_unload");
            if (ticks > timeoutTicks()) throw new IllegalStateException("Chunk lifecycle total timeout in phase " + phase);
            check(playersDistant(), "all_players_remain_over_512_blocks_from_fixture");
            if (ticks-phaseStarted > 1000) throw new IllegalStateException("Native chunk lifecycle phase timeout " + phase);
            switch (phase) {
                case 0 -> {
                    if (!connected()) return;
                    var mainEnergy=host.getMainNode().getGrid().getEnergyService(); var remoteEnergy=host.factoryGrid().getEnergyService();
                    double drain=mainEnergy.getIdlePowerUsage()+mainEnergy.getChannelPowerUsage()+remoteEnergy.getIdlePowerUsage()+remoteEnergy.getChannelPowerUsage();
                    double stored=mainEnergy.getStoredPower()+remoteEnergy.getStoredPower();
                    row.put("finiteEnergyBudget",Map.of("nativeConservativeDrainAEPerTick",drain,"remainingStoredAE",stored,"maximumAuditTicks",timeoutTicks(),"requiredAEWithMargin",drain*timeoutTicks()+1_000_000));
                    check(stored>drain*timeoutTicks()+1_000_000,"finite_initial_batteries_cover_native_drain_for_entire_timeout_plus_margin");
                    host.tags.reconcile(Set.of("Remote"));
                    check(host.tags.tag("Remote", TAGGED.asLong(), p -> FactoryServer.contains(host.factoryGrid(),BlockPos.of(p))), "tag_binds_real_remote_network_member");
                    host.saveChanges();
                    seed(subnet(),IRON,17); seed(subnet(),GOLD,31);
                    check(remoteCount()==7 && count(main(),IRON)==0, "positive_remote_witnesses_and_empty_destination");
                    enqueue(29); next(1);
                }
                case 1 -> {
                    if (!waiting(12,17)) return;
                    row.put("initialIron",17); row.put("explicitRefillIron",12); row.put("remoteGoldWitness",31); row.put("remoteTaggedDiamondsWitness",7);
                    row.put("beforeUnloadSaved", encoded(singleJob()));
                    observation("loaded_before_unload"); oldRemote=remoteDrive;
                    releaseTickets(false); next(2);
                }
                case 2 -> {
                    if (!remoteUnloaded()) return;
                    check(loadedBE(OWNER)==host && FactoryServer.find(level,id)==host, "remote_unload_keeps_actual_owner_registered");
                    row.put("unloadObserved",true); observation("unloaded"); heldTicks=0; next(3);
                }
                case 3 -> {
                    check(remoteUnloaded(), "remote_chunk_stays_actually_unloaded");
                    check(count(subnet(),GOLD)==0 && remoteCount()==0 && FactoryServer.members(host.factoryGrid(),Set.of(TAGGED.asLong())).isEmpty(), "unloaded_me_and_tagged_resources_are_not_ghost_read");
                    check(remoteUnloaded(), "production_storage_and_tag_queries_do_not_implicitly_reload_remote_chunk");
                    check(waiting(12,17), "waiting_must_keeps_twelve_debt_and_seventeen_committed");
                    if (++heldTicks<20) return;
                    row.put("unloadedServerTicks",heldTicks); row.put("ghostMeReadsBlocked",true); row.put("ghostTaggedReadsBlocked",true);
                    reloadTickets(); next(4);
                }
                case 4 -> {
                    if (!connected()) return;
                    check(remoteDrive!=oldRemote && oldRemote.isRemoved(), "remote_drive_is_new_native_block_entity");
                    check(count(subnet(),GOLD)==31 && remoteCount()==7 && waiting(12,17), "remote_nbt_stock_and_waiting_debt_return_without_replay");
                    row.put("reloadedNewBlockEntity",true); observation("reloaded");
                    seed(subnet(),IRON,12); next(5);
                }
                case 5 -> {
                    if (!done(29)) return;
                    completeCase(29);
                    newCase("whole_owner_unload_must"); seed(subnet(),IRON,5); enqueue(13); next(6);
                }
                case 6 -> {
                    if (!waiting(8,34)) return;
                    row.put("previousDeliveredIron",29); row.put("initialIron",5); row.put("explicitRefillIron",8);
                    beforeOwnerUnload=singleJob(); row.put("beforeUnloadSaved",encoded(beforeOwnerUnload));
                    oldHost=host; oldRemote=remoteDrive; observation("loaded_before_unload"); releaseTickets(true); next(7);
                }
                case 7 -> {
                    if (!wholeUnloaded()) return;
                    row.put("unloadObserved",true); row.put("registryOwnerAbsent",true); observation("unloaded"); heldTicks=0; next(8);
                }
                case 8 -> {
                    check(wholeUnloaded(), "whole_fixture_stays_unloaded_without_owner_or_visible_block_entities");
                    if (++heldTicks<20) return;
                    row.put("unloadedServerTicks",heldTicks); reloadTickets(); next(9);
                }
                case 9 -> {
                    if (!connected()) return;
                    check(host!=oldHost && oldHost.isRemoved() && remoteDrive!=oldRemote && id.equals(host.factoryId()), "owner_and_remote_are_new_nbt_instances_with_same_factory_id");
                    check(waiting(8,34) && sameOwnedAndRoutes(beforeOwnerUnload,singleJob()), "native_owner_reload_restores_exact_pending_eight_and_owned_state");
                    check(count(subnet(),GOLD)==31 && remoteCount()==7, "owner_reload_preserves_remote_stock_and_persisted_tags");
                    row.put("afterReloadSaved",encoded(singleJob())); row.put("reloadedNewBlockEntity",true); observation("reloaded");
                    seed(subnet(),IRON,8); next(10);
                }
                case 10 -> {
                    if (!done(42)) return;
                    completeCase(42);
                    newCase("partial_return_cargo_unload");
                    long prefill=Long.MAX_VALUE-55;
                    seed(main(),IRON,prefill); seed(subnet(),IRON,37); enqueue(37);
                    row.put("previousDeliveredIron",42); row.put("explicitPrefillIron",Long.toString(prefill)); row.put("explicitInputIron",37);
                    row.put("conservationTotalDecimal",BigInteger.valueOf(42).add(BigInteger.valueOf(prefill)).add(BigInteger.valueOf(37)).toString()); next(11);
                }
                case 11 -> {
                    if (count(main(),IRON)!=Long.MAX_VALUE || ownedIron().longValueExact()!=24) return;
                    check(count(subnet(),IRON)==0 && receipt()==0, "thirteen_committed_and_twenty_four_owned_before_real_removal");
                    if (!entityAreaReady()) return;
                    check(entities().isEmpty(), "native_removal_area_initially_has_no_items");
                    row.put("beforeRemovalSaved",encoded(singleJob()));
                    check(level.destroyBlock(OWNER,true,player), "pending_output_owner_actually_destroyed_in_world"); next(12);
                }
                case 12 -> {
                    if (ticks-phaseStarted<2 || !entityAreaReady()) return;
                    var drops=entities();
                    if (drops.isEmpty()) return;
                    row.put("actualWorldDrops",drops.stream().map(e -> Map.of("id",e.getItem().getItem().toString(),"count",e.getItem().getCount(),"components",e.getItem().getComponents().toString())).toList());
                    check(drops.size()==1 && drops.getFirst().getItem().is(FactoryContent.PROVIDER_ITEM.get()) && drops.getFirst().getItem().getCount()==1, "one_real_machine_carries_the_owned_remainder");
                    carrier=drops.getFirst().getItem().copy();
                    var data=carrier.get(DataComponents.CUSTOM_DATA);
                    check(data!=null && data.copyTag().contains("Ae2lfFactoryRecovery"), "real_machine_has_native_resource_cargo_component");
                    drops.getFirst().discard();
                    var result=((BlockItem)carrier.getItem()).place(new DirectionalPlaceContext(level,OWNER,Direction.WEST,carrier,Direction.EAST) {
                        @Override public Direction getNearestLookingDirection() { return Direction.WEST; }
                        @Override public Direction[] getNearestLookingDirections() { return new Direction[]{Direction.WEST,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.UP,Direction.EAST}; }
                    });
                    check(result.consumesAction() && carrier.isEmpty(), "native_block_item_placement_consumes_carrier");
                    host=(FactoryBlockEntity)loadedBE(OWNER); id=host.factoryId();
                    check(host.getFront()==Direction.EAST,"native_placement_restores_fixture_facing");
                    check(pendingIron().longValueExact()==24 && host.getLogic().saveJobs().isEmpty(), "replacement_owns_only_twenty_four_resources_and_no_cancelled_job"); next(13);
                }
                case 13 -> {
                    if (!connected()) return;
                    check(count(main(),IRON)==Long.MAX_VALUE && pendingIron().longValueExact()==24, "full_native_destination_keeps_return_queue_pending");
                    withdrawToReceipt(5); next(14);
                }
                case 14 -> {
                    if (pendingIron().longValueExact()!=19 || count(main(),IRON)!=Long.MAX_VALUE) return;
                    check(receipt()==5 && host.getLogic().saveJobs().isEmpty(), "five_returned_once_leaves_exact_nineteen_resource_cargo");
                    row.put("beforeUnloadPendingIron",19); row.put("beforeUnloadReceiptIron",5); row.put("beforeUnloadNativeReturnTag",stateTag(host).get("FactoryResourceReturns").toString());
                    oldHost=host; oldRemote=remoteDrive; observation("loaded_before_unload"); releaseTickets(true); next(15);
                }
                case 15 -> {
                    if (!wholeUnloaded()) return;
                    row.put("unloadObserved",true); row.put("registryOwnerAbsent",true); observation("unloaded"); heldTicks=0; next(16);
                }
                case 16 -> {
                    check(wholeUnloaded(), "partly_returned_cargo_owner_stays_really_unloaded");
                    if (++heldTicks<20) return;
                    row.put("unloadedServerTicks",heldTicks); reloadTickets(); next(17);
                }
                case 17 -> {
                    if (!connected()) return;
                    check(host!=oldHost && oldHost.isRemoved() && id.equals(host.factoryId()), "partial_return_owner_is_new_native_nbt_block_entity");
                    check(pendingIron().longValueExact()==19 && receipt()==5 && count(main(),IRON)==Long.MAX_VALUE && host.getLogic().saveJobs().isEmpty(), "native_nbt_restores_only_nineteen_with_no_return_replay_or_old_job");
                    row.put("afterReloadPendingIron",19); row.put("afterReloadReceiptIron",5); row.put("reloadedNewBlockEntity",true); observation("reloaded");
                    withdrawToReceipt(19); next(18);
                }
                case 18 -> {
                    if (!pendingIron().equals(BigInteger.ZERO)) return;
                    check(count(main(),IRON)==Long.MAX_VALUE && receipt()==24 && count(subnet(),IRON)==0 && ownedIron().equals(BigInteger.ZERO), "final_native_storage_plus_physical_receipt_conserves_every_iron");
                    check(BigInteger.valueOf(count(main(),IRON)).add(BigInteger.valueOf(receipt())).toString().equals(row.get("conservationTotalDecimal")), "above_long_total_is_exact_in_big_integer_ledger");
                    check(count(subnet(),GOLD)==31 && taggedDiamonds()==7, "unrelated_gold_and_diamonds_survive_all_unload_cycles");
                    row.put("finalMainIron",Long.toString(Long.MAX_VALUE)); row.put("finalReceiptIron",24); row.put("finalPendingIron",0);
                    completeCase(Long.MAX_VALUE); finish(null);
                }
                default -> throw new IllegalStateException("Unknown chunk phase " + phase);
            }
            if (ticks%100==0) writeReport(null);
        } catch (Throwable error) { finish(error); }
    }

    // These are observational reads only: getBlockEntity/getChunkAt on ServerLevel can load chunks.
    private static BlockEntity loadedBE(BlockPos pos) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        return chunk==null?null:chunk.getBlockEntities().get(pos);
    }
    private static boolean absent(BlockPos pos) {
        return !level.hasChunk(pos.getX()>>4,pos.getZ()>>4) && level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null && loadedBE(pos)==null;
    }
    private static boolean remoteUnloaded() { return absent(REMOTE) && oldRemote!=null && oldRemote.isRemoved() && !oldRemote.getMainNode().isReady(); }
    private static boolean wholeUnloaded() {
        return absent(OWNER) && remoteUnloaded() && oldHost!=null && oldHost.isRemoved() && !oldHost.getMainNode().isReady() && FactoryServer.find(level,id)==null
                && fixtureChunks.stream().allMatch(key -> !level.hasChunk((int)(long)key,(int)(key>>32)) && level.getChunkSource().getChunkNow((int)(long)key,(int)(key>>32))==null);
    }
    private static boolean connected() {
        if (!(loadedBE(OWNER) instanceof FactoryBlockEntity current) || !(loadedBE(REMOTE) instanceof DriveBlockEntity remote) || !(loadedBE(MAIN) instanceof DriveBlockEntity mainDrive)) return false;
        host=current; remoteDrive=remote;
        return host.getFront()==Direction.EAST && host.getMainNode().isActive() && host.factoryGrid()!=null && host.isolated()
                && FactoryServer.owner(host.factoryGrid())==host && remote.getMainNode().isActive() && mainDrive.getMainNode().isActive()
                && remote.getMainNode().getGrid()==host.factoryGrid() && mainDrive.getMainNode().getGrid()==host.getMainNode().getGrid();
    }
    private static MEStorage subnet() { check(host.factoryGrid()!=null,"actual_owner_subnet_exists"); return host.factoryGrid().getStorageService().getInventory(); }
    private static MEStorage main() { check(host.getMainNode().getGrid()!=null,"actual_main_grid_exists"); return host.getMainNode().getGrid().getStorageService().getInventory(); }
    private static long count(MEStorage storage,AEKey key) { return storage.extract(key,Long.MAX_VALUE,Actionable.SIMULATE,IActionSource.empty()); }
    private static void seed(MEStorage storage,AEKey key,long amount) { check(storage.insert(key,amount,Actionable.MODULATE,IActionSource.empty())==amount,"finite_native_fixture_supply_accepted_exactly"); }
    private static long remoteCount() { return new FactoryJob(host,new FactoryPatternData("done",host.factoryId(),ItemStack.EMPTY)).count("Remote","minecraft:diamond"); }
    @SuppressWarnings("unchecked") private static void enqueue(long amount) throws ReflectiveOperationException {
        check(host.getLogic().saveJobs().isEmpty(),"no_existing_job_before_fixture_admission");
        var field=FactoryProviderLogic.class.getDeclaredField("jobs"); field.setAccessible(true);
        var jobs=(List<FactoryJob>)field.get(host.getLogic());
        jobs.add(new FactoryJob(host,new FactoryPatternData("get must "+amount+" minecraft:iron_ingot from storage\nput minecraft:iron_ingot into source\ndone",host.factoryId(),ItemStack.EMPTY)));
        host.saveChanges(); // The real FactoryServer alone advances this queue, including after native NBT restoration.
    }
    private static FactoryJob.Saved singleJob() { var jobs=host.getLogic().saveJobs(); check(jobs.size()==1,"one_actual_production_job"); return jobs.getFirst(); }
    private static boolean waiting(long remaining,long delivered) {
        var jobs=host.getLogic().saveJobs();
        if (jobs.size()!=1) return false;
        var saved=jobs.getFirst(); var snapshot=GSON.fromJson(saved.continuation(),FactoryMachine.Snapshot.class);
        check(host.getLogic().executionError().isEmpty(),"production_job_has_no_execution_error");
        return snapshot.pendingTransfer() && snapshot.transferRemaining()==remaining && count(main(),IRON)==delivered
                && saved.input().isEmpty() && saved.output().isEmpty() && !host.getLogic().waitingStatus().isEmpty();
    }
    private static boolean done(long delivered) {
        return host.getLogic().saveJobs().isEmpty() && host.getLogic().executionError().isEmpty() && count(main(),IRON)==delivered && count(subnet(),IRON)==0 && pendingIron().equals(BigInteger.ZERO);
    }
    private static JsonElement encoded(FactoryJob.Saved saved) { return FactoryJob.Saved.CODEC.encodeStart(level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),saved).getOrThrow(); }
    private static boolean sameOwnedAndRoutes(FactoryJob.Saved before,FactoryJob.Saved after) {
        return before.data().equals(after.data()) && before.input().equals(after.input()) && before.output().equals(after.output()) && before.expected().equals(after.expected())
                && before.routes().equals(after.routes()) && before.nativeRecovery().equals(after.nativeRecovery());
    }
    private static BigInteger ownedIron() {
        var total=BigInteger.ZERO;
        for(var job:host.getLogic().saveJobs()) { total=total.add(sum(job.input())).add(sum(job.output())); }
        return total.add(sum(host.getLogic().induction().snapshot()));
    }
    private static BigInteger sum(List<GenericStack> stacks) { var n=BigInteger.ZERO; for(var s:stacks) if(s.what().equals(IRON)) n=n.add(BigInteger.valueOf(s.amount())); return n; }
    private static BigInteger pendingIron() {
        var cargo=(CompoundTag)stateTag(host).get("FactoryResourceReturns");
        return sum(GenericStack.CODEC.listOf().parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),cargo.get("resources")).getOrThrow());
    }
    private static CompoundTag stateTag(FactoryBlockEntity value) {
        var output=new CompoundTag();
        value.saveAdditional(output,level.registryAccess()); return output;
    }
    private static long receipt() { return containerCount(RECEIPT,Items.IRON_INGOT); }
    private static long taggedDiamonds() { return containerCount(TAGGED,Items.DIAMOND); }
    private static long containerCount(BlockPos pos,Item item) {
        check(loadedBE(pos) instanceof Container,"observed_container_is_actually_loaded"); var container=(Container)loadedBE(pos); long total=0;
        for(int n=0;n<container.getContainerSize();n++) { var stack=container.getItem(n); if(!stack.isEmpty()) { check(stack.is(item),"container_contains_only_expected_physical_item"); total=Math.addExact(total,stack.getCount()); } }
        return total;
    }
    private static void withdrawToReceipt(int amount) {
        int previous=Math.toIntExact(receipt()); check(previous+amount<=64,"physical_receipt_fits_native_slot");
        check(main().extract(IRON,amount,Actionable.MODULATE,IActionSource.empty())==amount,"recorded_main_withdrawal_is_real");
        ((Container)loadedBE(RECEIPT)).setItem(0,new ItemStack(Items.IRON_INGOT,previous+amount));
        loadedBE(RECEIPT).setChanged(); check(receipt()==previous+amount,"actual_withdrawn_items_are_in_physical_receipt_barrel");
    }
    private static List<ItemEntity> entities() { return level.getEntitiesOfClass(ItemEntity.class,new AABB(OWNER).inflate(2)); }
    private static boolean entityAreaReady() { return level.areEntitiesLoaded(chunkKey(OWNER)) && level.isPositionEntityTicking(OWNER); }
    private static boolean playersDistant() {
        return level.players().stream().allMatch(p -> p.distanceToSqr(OWNER.getX(),OWNER.getY(),OWNER.getZ())>512.0*512 && p.distanceToSqr(REMOTE.getX(),REMOTE.getY(),REMOTE.getZ())>512.0*512);
    }
    private static long chunkKey(BlockPos pos) { return ((long)(pos.getX()>>4)&0xffffffffL)|(((long)(pos.getZ()>>4)&0xffffffffL)<<32); }
    private static void place(BlockPos pos,BlockState state) {
        long key=chunkKey(pos);
        if(fixtureChunks.add(key)) { check(level.setChunkForced((int)key,(int)(key>>32),true),"fixture_chunk_did_not_have_a_preexisting_forced_ticket"); ownedTickets.add(key); }
        level.setBlockAndUpdate(pos,state);
    }
    private static DriveBlockEntity drive(BlockPos pos) {
        var block=AEBlocks.DRIVE.block(); place(pos,block.getOrientationStrategy().setFacing(block.defaultBlockState(),Direction.NORTH));
        var drive=(DriveBlockEntity)loadedBE(pos); drive.getInternalInventory().setItemDirect(0,ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance()); return drive;
    }
    private static void battery(BlockPos pos) {
        place(pos,AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState()); var battery=(EnergyCellBlockEntity)loadedBE(pos);
        double rejected=battery.injectAEPower(1_000_000,Actionable.MODULATE);
        check(rejected==0,"one_time_finite_native_battery_supply_accepted");
        power.add(Map.of("position",pos.toShortString(),"offeredAE",1_000_000,"acceptedAE",1_000_000-rejected,"rejectedAE",rejected));
    }
    private static void releaseTickets(boolean all) {
        for(long key:List.copyOf(ownedTickets)) if(all || key!=chunkKey(OWNER)) { check(level.setChunkForced((int)key,(int)(key>>32),false),"owned_forced_ticket_removed_natively"); ownedTickets.remove(key); }
        row.put("ticketsRemainingDuringUnload",ownedTickets.size());
    }
    private static void reloadTickets() {
        for(long key:fixtureChunks) if(ownedTickets.add(key)) check(level.setChunkForced((int)key,(int)(key>>32),true),"explicit_reload_phase_adds_fixture_ticket");
    }
    private static Map<String,Object> chunkState(BlockPos pos) {
        var state=new LinkedHashMap<String,Object>(); state.put("chunkX",pos.getX()>>4); state.put("chunkZ",pos.getZ()>>4);
        state.put("hasChunk",level.hasChunk(pos.getX()>>4,pos.getZ()>>4)); state.put("chunkNow",level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null);
        state.put("blockEntityVisible",loadedBE(pos)!=null);
        state.put("nativeChunkDebug",level.getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(pos.getX()>>4,pos.getZ()>>4)));
        try {
            var method=ServerChunkCache.class.getDeclaredMethod("getVisibleChunkIfPresent",long.class); method.setAccessible(true);
            var holder=(ChunkHolder)method.invoke(level.getChunkSource(),chunkKey(pos));
            state.put("holderPresent",holder!=null);
            if(holder!=null) { state.put("holderTicketLevel",holder.getTicketLevel()); state.put("holderFullStatus",holder.getFullStatus().toString()); state.put("ticketLevelStillNativeLoaded",ChunkLevel.isLoaded(holder.getTicketLevel())); }
        } catch(ReflectiveOperationException error) { state.put("holderDiagnosticFailure",error.toString()); }
        return state;
    }
    @SuppressWarnings("unchecked") private static void observation(String stage) {
        var observation=new LinkedHashMap<String,Object>(); observation.put("stage",stage); observation.put("serverTick",ticks); observation.put("gameTime",level.getGameTime());
        observation.put("ownerChunk",chunkState(OWNER)); observation.put("remoteChunk",chunkState(REMOTE));
        observation.put("oldOwnerRemoved",oldHost!=null && oldHost.isRemoved()); observation.put("oldRemoteRemoved",oldRemote!=null && oldRemote.isRemoved());
        observation.put("oldOwnerNode",nodeState(oldHost)); observation.put("oldRemoteNode",nodeState(oldRemote));
        observation.put("ownedForcedChunkKeys",List.copyOf(ownedTickets)); observation.put("nativeNearbyTickets",nativeTickets());
        observation.put("registryOwnerAbsent",FactoryServer.find(level,id)==null); observation.put("playerDistanceSquared",Math.min(player.distanceToSqr(OWNER.getX(),OWNER.getY(),OWNER.getZ()),player.distanceToSqr(REMOTE.getX(),REMOTE.getY(),REMOTE.getZ())));
        var energy=new ArrayList<Map<String,Object>>();
        for(int n=1;n<=5;n++) { var pos=n==5?REMOTE.east():OWNER.north(n); if(loadedBE(pos) instanceof EnergyCellBlockEntity cell) energy.add(Map.of("position",pos.toShortString(),"storedAE",cell.getAECurrentPower(),"maximumAE",cell.getAEMaxPower())); }
        observation.put("loadedFiniteBatteries",energy);
        observation.put("ownerIdentity",loadedBE(OWNER)==null?null:System.identityHashCode(loadedBE(OWNER))); observation.put("remoteIdentity",loadedBE(REMOTE)==null?null:System.identityHashCode(loadedBE(REMOTE)));
        ((List<Map<String,Object>>)row.get("observations")).add(observation);
    }
    private static Map<String,Object> nodeState(appeng.me.helpers.IGridConnectedBlockEntity entity) {
        if(entity==null) return Map.of("referencePresent",false);
        var node=entity.getMainNode(); return Map.of("referencePresent",true,"isReady",node.isReady(),"isActive",node.isActive(),"nodePresent",node.getNode()!=null,"gridPresent",node.getGrid()!=null);
    }
    private static Object readField(Object instance,String name) throws ReflectiveOperationException {
        for(Class<?> type=instance.getClass();type!=null;type=type.getSuperclass()) {
            try { var field=type.getDeclaredField(name); field.setAccessible(true); return field.get(instance); } catch(NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Object nativeTickets() {
        try {
            var manager=readField(level.getChunkSource(),"distanceManager");
            var tickets=(Map<?,?>)readField(manager,"tickets"); var values=new ArrayList<Map<String,Object>>();
            for(var entry:tickets.entrySet()) {
                long key=((Number)entry.getKey()).longValue(); int x=(int)key,z=(int)(key>>32);
                if(x>=(OWNER.getX()>>4)-16 && x<=(REMOTE.getX()>>4)+16 && Math.abs(z-(OWNER.getZ()>>4))<=16)
                    values.add(Map.of("chunkX",x,"chunkZ",z,"tickets",entry.getValue().toString()));
            }
            return values;
        } catch(ReflectiveOperationException error) { return Map.of("diagnosticFailure",error.toString()); }
    }
    private static void newCase(String name) { row=new LinkedHashMap<>(); row.put("case",name); row.put("status","running"); row.put("observations",new ArrayList<Map<String,Object>>()); cases.add(row); }
    private static void completeCase(long total) { row.put("finalDeliveredIron",Long.toString(total)); row.put("conserved",true); row.put("resumed",true); row.put("status","passed"); completed++; }
    private static void next(int target) { phase=target; phaseStarted=ticks; writeReport(null); }
    private static void check(boolean value,String name) { if(row!=null) row.put(name,value); if(!value) throw new IllegalStateException(name); }
    private static void writeReport(Throwable failure) {
        var report=new LinkedHashMap<String,Object>(); report.put("status",finished?(passed?"passed":"failed"):"running");
        report.put("targetCases",3); report.put("completedCases",completed); report.put("cases",cases); report.put("serverTicks",ticks); report.put("phase",phase);
        report.put("wallClockSeconds",(System.nanoTime()-startedNanos)/1_000_000_000.0); report.put("ownerPosition",OWNER.toShortString()); report.put("remotePosition",REMOTE.toShortString());
        report.put("fixtureChunkKeys",List.copyOf(fixtureChunks)); report.put("nativeChunkMaxLevel",ChunkLevel.MAX_LEVEL); report.put("nativeForcedTicketLevel",ChunkMap.FORCED_TICKET_LEVEL); report.put("remoteChunkDistance",(REMOTE.getX()>>4)-(OWNER.getX()>>4));
        report.put("finiteBatterySupply",power); report.put("playerMustRemainBeyondBlocks",512); report.put("manualJobTicks",0); report.put("manuallyInvokedUnloadCallbacks",0);
        report.put("scope","Real same-dimension chunk unload and native NBT reload. Production FactoryServer alone ticks the admitted provider jobs and resource returns. Remote ME and tagged inventory reads are checked while absent. Owner absence is bounded; cross-dimension execution, permanently missing owner, native CPU request resumption and terminal-specific jobs are not claimed.");
        if(failure!=null) { report.put("failure",failure.toString()); report.put("stack",Arrays.toString(failure.getStackTrace())); report.put("failureOwnerChunk",chunkState(OWNER)); report.put("failureRemoteChunk",chunkState(REMOTE)); }
        try { Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("chunk-lifecycle-report.json"),GSON.toJson(report)); } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    private static void finish(Throwable failure) {
        passed=failure==null; finished=true;
        if(failure!=null && row!=null) { row.put("status","failed"); row.put("failure",failure.toString()); observation("failure"); }
        try { writeReport(failure); } finally { for(long key:List.copyOf(ownedTickets)) level.setChunkForced((int)key,(int)(key>>32),false); ownedTickets.clear(); }
    }
}
