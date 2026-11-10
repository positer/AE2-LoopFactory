package com.example.ae2loprobe;

import appeng.api.networking.*;
import appeng.api.config.Actionable;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.parts.networking.CablePart;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import com.example.ae2lightoptimizer.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;
import java.util.*;

/** Physical AE2 cable/controller/energy-cell networks; synthetic loads use real managed AE2 nodes. */
public final class ChannelCapacityFixture {
    public static final boolean ENABLED = "channel_capacity".equals(System.getProperty("ae2lo.probe.chain"));
    private static ServerLevel level;
    private static ServerPlayer player;
    private static Path output;
    private static final BlockPos CONTROL=new BlockPos(8,100,0), DENSE=CONTROL.east();
    private static final BlockPos GLASS=new BlockPos(8,100,10), OTHER=new BlockPos(-8,100,10);
    private static final BlockPos OPT=CONTROL.north(2), ADHOC_OPT=GLASS.west(), DUPLICATE=CONTROL.south(2);
    private static final List<BlockPos> blocks=new ArrayList<>();
    private static final List<Load> denseLoads=new ArrayList<>(), glassLoads=new ArrayList<>(), otherLoads=new ArrayList<>();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static Load extra;
    private static int phase;
    private static int duplicateStableTicks;
    private static long since, started;
    private static final class Load {
        final IManagedGridNode node=GridHelper.createManagedNode(this,new IGridNodeListener<Load>() {
            @Override public void onSaveChanges(Load load,IGridNode n) {}
        }).setFlags(GridFlags.REQUIRE_CHANNEL).setInWorldNode(false).setIdlePowerUsage(0);
    }
    private static void check(boolean value,String message) {
        if(!value)throw new IllegalStateException("Channel capacity: "+message);
        report.put(message,true);
    }
    public static void start(ServerLevel l,ServerPlayer p,Path path) {
        level=l;player=p;output=path;started=since=level.getGameTime();phase=0;
        level.setBlockAndUpdate(CONTROL,AEBlocks.CONTROLLER.block().defaultBlockState());blocks.add(CONTROL);
        battery(CONTROL.west());
        for(var side:Direction.values())if(side!=Direction.WEST)cable(CONTROL.relative(side),true);
        battery(GLASS.below());cable(GLASS,false);
        battery(OTHER.below());cable(OTHER,false);
        report.put("fixture","native-world-cables-controller-and-finite-power; real managed-node consumer loads");
        report.put("consumersDense",40);report.put("consumersAdHocBeforeOptimizer",15);report.put("independentConsumers",9);
    }
    private static void battery(BlockPos pos) {
        level.setBlockAndUpdate(pos,AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());blocks.add(pos);
        var cell=(EnergyCellBlockEntity)level.getBlockEntity(pos);
        double accepted=1_000_000-cell.injectAEPower(1_000_000,Actionable.MODULATE);
        check(accepted==1_000_000,"finite_cell_charged_"+pos.toShortString());
    }
    private static void cable(BlockPos pos,boolean dense) {
        level.removeBlock(pos,false);
        if(dense)appeng.api.parts.PartHelper.setPart(level,pos,null,player,AEParts.SMART_DENSE_CABLE.item(AEColor.TRANSPARENT));
        else appeng.api.parts.PartHelper.setPart(level,pos,null,player,AEParts.SMART_CABLE.item(AEColor.TRANSPARENT));
        blocks.add(pos);
    }
    private static IGridNode node(BlockPos pos) {return appeng.api.parts.PartHelper.getPart(level,pos,null).getGridNode();}
    private static int capacity(BlockPos pos) {return ((appeng.me.GridNode)node(pos)).getMaxChannels();}
    private static void loads(BlockPos pos,List<Load> list,int count) throws Exception {
        for(int i=0;i<count;i++) {
            var load=new Load();load.node.create(level,pos);list.add(load);
            GridHelper.createConnection(node(pos),load.node.getNode());
        }
    }
    private static int active(List<Load> loads) {return (int)loads.stream().filter(l->l.node.isActive()).count();}
    private static void optimizer(BlockPos pos) {level.setBlockAndUpdate(pos,ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get().defaultBlockState());blocks.add(pos);}
    private static void mode(BlockPos pos,ChannelMode mode) {((appeng.me.service.PathingService)node(pos).getGrid().getPathingService()).setForcedChannelMode(mode);}
    private static void advance(int next) {
        phase=next;since=level.getGameTime();report.put("phase",phase);report.put("passed",false);
        try {Files.writeString(output.resolve("channel-capacity-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
    }
    private static int readableCapacity(BlockPos pos) {
        var part=appeng.api.parts.PartHelper.getPart(level,pos,null);
        return part==null || part.getGridNode()==null ? -1 : ((appeng.me.GridNode)part.getGridNode()).getMaxChannels();
    }
    private static void isolated() {check(capacity(OTHER)==8 && active(otherLoads)==0,"independent_grid_unchanged_phase_"+phase);}
    public static boolean tick() throws Exception {
        if(level.getGameTime()-started>1600)throw new IllegalStateException("Channel capacity timeout phase "+phase);
        if(level.getGameTime()-since<85)return false;
        report.put("observedPhase"+phase,Map.of("denseCapacity",readableCapacity(DENSE),"denseActive",active(denseLoads),"glassCapacity",readableCapacity(GLASS),"glassActive",active(glassLoads),"independentCapacity",readableCapacity(OTHER),"independentActive",active(otherLoads)));
        switch(phase) {
            case 0 -> {mode(DENSE,ChannelMode.DEFAULT);mode(GLASS,ChannelMode.DEFAULT);mode(OTHER,ChannelMode.DEFAULT);loads(DENSE,denseLoads,40);loads(GLASS,glassLoads,15);loads(OTHER,otherLoads,9);advance(1);}
            case 1 -> {
                check(capacity(DENSE)==32 && active(denseLoads)==32,"baseline_dense_32_active");
                check(capacity(GLASS)==8 && active(glassLoads)==0,"baseline_adhoc_15_rejected");isolated();
                optimizer(OPT);optimizer(ADHOC_OPT);advance(2);
            }
            case 2 -> {
                check(capacity(DENSE)==64 && active(denseLoads)==40,"dense_40_active_over_old_face_limit");
                check(capacity(GLASS)==16 && active(glassLoads)==15,"adhoc_16_including_optimizer_active");
                var controller=(ControllerBlockEntity)level.getBlockEntity(CONTROL);int faces=0;
                for(var con:controller.getMainNode().getNode().getInWorldConnections().values()) {
                    check(((appeng.me.GridConnection)con).getMaxChannels()==64,"controller_face_"+(faces++)+"_64");
                }
                check(faces==6,"all_six_faces_verified");
                var visual=CablePart.class.getDeclaredMethod("getVisualChannels",int.class);visual.setAccessible(true);
                check(((Byte)visual.invoke(appeng.api.parts.PartHelper.getPart(level,DENSE,null),40))==20,"native_smart_visual_normalizes_40_of_64_to_20_of_32");
                isolated();extra=new Load();extra.node.create(level,GLASS);GridHelper.createConnection(node(GLASS),extra.node.getNode());advance(3);
            }
            case 3 -> {
                check(capacity(GLASS)==16 && active(glassLoads)==0 && !extra.node.isActive(),"adhoc_17_rejected_without_boost_oscillation");
                extra.node.destroy();extra=null;advance(4);
            }
            case 4 -> {
                check(active(glassLoads)==15,"adhoc_recovers_after_excess_load_removed");
                mode(GLASS,ChannelMode.X2);mode(DENSE,ChannelMode.X2);advance(5);
            }
            case 5 -> {
                check(capacity(GLASS)==32 && capacity(DENSE)==128,"configured_x2_mode_is_doubled_not_replaced");
                mode(GLASS,ChannelMode.INFINITE);mode(DENSE,ChannelMode.INFINITE);advance(6);
            }
            case 6 -> {
                check(capacity(GLASS)==Integer.MAX_VALUE && capacity(DENSE)==Integer.MAX_VALUE,"infinite_mode_unchanged");
                mode(GLASS,ChannelMode.DEFAULT);mode(DENSE,ChannelMode.DEFAULT);optimizer(DUPLICATE);advance(7);
            }
            case 7 -> {
                check(capacity(DENSE)==64 && active(denseLoads)==40,"duplicate_optimizers_do_not_stack");
                if(++duplicateStableTicks<60)return false;
                report.put("duplicateStableServerTicks",duplicateStableTicks);isolated();
                level.removeBlock(OPT,false);level.removeBlock(DUPLICATE,false);level.removeBlock(ADHOC_OPT,false);advance(8);
            }
            case 8 -> {
                check(capacity(DENSE)==32 && active(denseLoads)==32,"removing_optimizer_restores_face_and_dense_limits");
                check(capacity(GLASS)==8 && active(glassLoads)==0,"removing_optimizer_restores_adhoc_limit");isolated();
                optimizer(OPT);optimizer(ADHOC_OPT);advance(9);
            }
            case 9 -> {
                check(capacity(DENSE)==64 && active(denseLoads)==40 && active(glassLoads)==15,"reinstalling_optimizer_repaths_both_networks");
                level.removeBlock(GLASS,false);advance(10);
            }
            case 10 -> {
                check(glassLoads.stream().allMatch(l->((appeng.me.GridNode)l.node.getNode()).getMaxChannels()==8),"split_load_grids_do_not_keep_stale_capacity");
                cable(GLASS,false);advance(11);
            }
            case 11 -> {for(var load:glassLoads)GridHelper.createConnection(node(GLASS),load.node.getNode());advance(12);}
            case 12 -> {
                check(capacity(GLASS)==16 && active(glassLoads)==15,"merged_network_restores_capacity");isolated();
                for(var list:List.of(denseLoads,glassLoads,otherLoads))for(var load:list)load.node.destroy();
                for(var pos:blocks)level.removeBlock(pos,false);
                report.put("passed",true);report.put("elapsedServerTicks",level.getGameTime()-started);
                Files.writeString(output.resolve("channel-capacity-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
                return true;
            }
        }
        return false;
    }
}
