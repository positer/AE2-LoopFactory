package com.example.ae2loprobe;

import appeng.api.networking.*;
import appeng.api.config.Actionable;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import com.example.ae2lightoptimizer.omni.*;
import com.example.ae2lightoptimizer.block.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Acceptance only: native AE2 grids and real machine relay; managed nodes are consumer fixtures. */
public final class OmniProbe {
 public static final boolean ENABLED="omni".equals(System.getProperty("ae2lo.probe.chain"));
 static MinecraftServer server; static ServerPlayer player; static Path output;
 static ServerLevel level; static OmniConnectorBlockEntity a,b,c,e;
 static String link; static int phase; static long since; static volatile boolean ready;
 static final BlockPos A=new BlockPos(0,100,0),B=new BlockPos(4,100,0),R=new BlockPos(1600,80,0), P=new BlockPos(-3,100,2),OPT=new BlockPos(-2,100,-1);
 static final List<IManagedGridNode> loads=new ArrayList<>(),relayLoads=new ArrayList<>();
 static final Map<String,Object> report=new LinkedHashMap<>();
 static void check(boolean b,String key){if(!b)throw new IllegalStateException(key);report.put(key,true);}
 static void save(){try{Files.writeString(output.resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception ex){throw new RuntimeException(ex);}}
 static OmniConnectorBlockEntity place(ServerLevel l,BlockPos p){l.setChunkForced(p.getX()>>4,p.getZ()>>4,true);l.setBlockAndUpdate(p,OmniContent.BLOCK.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST));return (OmniConnectorBlockEntity)l.getBlockEntity(p);}
 static void advance(){phase++;since=level.getGameTime();report.put("phase",phase);save();}
 public static void start(MinecraftServer s,ServerPlayer p,Path o){server=s;player=p;output=o;level=s.overworld();
 report.put("minecraft","1.21.1");report.put("world",s.getWorldData().getLevelName());report.put("chain","omni_connector");report.put("passed",false);
 try{
  player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
  a=place(level,A);b=place(level,B);c=place(s.getLevel(Level.NETHER),R);e=place(s.getLevel(Level.END),R);
  link=OmniLinkSavedData.get(s).create("跨维度物流").id();
  for(int i=0;i<9;i++)OmniLinkSavedData.get(s).create("Logistics "+i);
  since=level.getGameTime(); player.connection.teleport(3,102,4,180,20);save();
 }catch(Throwable ex){fail(ex);}}
 static void selectAll(){for(var be:List.of(a,b,c,e))be.selectLink(link);}
 static void battery(BlockPos pos){level.setBlockAndUpdate(pos,AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());((EnergyCellBlockEntity)level.getBlockEntity(pos)).injectAEPower(1_000_000,Actionable.MODULATE);}
 static IManagedGridNode load(ServerLevel l,IGridNode parent){var n=GridHelper.createManagedNode(new Object(),new IGridNodeListener<Object>(){public void onSaveChanges(Object o,IGridNode n){}}).setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(0).setInWorldNode(false);n.create(l,BlockPos.ZERO);GridHelper.createConnection(parent,n.getNode());return n;}
 static int active(List<IManagedGridNode> list){return (int)list.stream().filter(IManagedGridNode::isActive).count();}
 static IGridNode node(OmniConnectorBlockEntity be){return be.getMainNode().getNode();}
 static int cap(IGridNode n){return ((appeng.me.GridNode)n).getMaxChannels();}
 public static void tick(){if(ready)return;try{
 if(level.getGameTime()-since<110)return;
 switch(phase){
 case 0->{selectAll();advance();}
 case 1->{check(OmniContent.BLOCK.get().getTicker(level,level.getBlockState(A),OmniContent.ENTITY.get())==null,"no_server_particle_tick");var net=OmniNetworks.get(server);check(net.count(link)==4,"four_loaded_endpoints");check(net.hubNode(link).getConnections().size()==4,"one_star_hub");for(var be:List.of(b,c,e))check(node(a).getGrid()==node(be).getGrid(),"same_native_grid_"+be.getLevel().dimension().location());check(level.getBlockState(A).getValue(OmniConnectorBlock.ONLINE)==a.getMainNode().isActive(),"online_flag_matches_native_zero_power_state");check(node(a).getGrid().getEnergyService().getStoredPower()==0,"connected_without_power");check(node(a).getGrid().getPathingService().getUsedChannels()==0,"connectors_require_no_channels");b.selectLink(OmniLinkSavedData.get(server).create("备用链路").id());advance();}
 case 2->{check(node(a).getGrid()!=node(b).getGrid(),"different_links_isolated");check(OmniNetworks.get(server).count(link)==3,"old_link_membership_removed");b.selectLink(link);advance();}
 case 3->{check(node(a).getGrid()==node(b).getGrid(),"reselect_merges_native_grids");
  level.setBlockAndUpdate(A.west(),AEBlocks.CONTROLLER.block().defaultBlockState());battery(A.west(2));
  level.setBlockAndUpdate(P,AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
  var provider=(appeng.blockentity.crafting.PatternProviderBlockEntity)level.getBlockEntity(P);
  advance();}
 case 4->{var controller=(appeng.blockentity.networking.ControllerBlockEntity)level.getBlockEntity(A.west());var provider=(appeng.blockentity.crafting.PatternProviderBlockEntity)level.getBlockEntity(P);GridHelper.createConnection(controller.getMainNode().getNode(),provider.getMainNode().getNode());
  for(int i=0;i<40;i++)loads.add(load(i%2==0?server.getLevel(Level.NETHER):server.getLevel(Level.END),node(i%2==0?c:e)));
  for(int i=0;i<12;i++)relayLoads.add(load(level,provider.getMainNode().getNode()));advance();}
 case 5->{report.put("baselineRemoteActive",active(loads));report.put("baselineRelayActive",active(relayLoads));check(cap(node(a))==32,"baseline_connector_capacity_32");check(active(loads)==32,"star_native_channel_limit_32");check(active(relayLoads)<12,"machine_relay_baseline_limited");level.setBlockAndUpdate(OPT,ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get().defaultBlockState());advance();}
 case 6->{var provider=(appeng.blockentity.crafting.PatternProviderBlockEntity)level.getBlockEntity(P);check(cap(node(a))==64,"supercomputer_connector_capacity_64");check(cap(provider.getMainNode().getNode())==16,"supercomputer_machine_relay_capacity_16");check(level.getBlockState(A).getValue(OmniConnectorBlock.ONLINE),"powered_connected_connector_online");check(active(loads)==40,"cross_dimension_40_channels_active");check(active(relayLoads)==12,"machine_relay_12_consumers_active");level.removeBlock(OPT,false);advance();}
 case 7->{check(cap(node(a))==32,"removed_supercomputer_restores_32");check(active(relayLoads)<12,"removed_supercomputer_restores_machine_limit");level.removeBlock(B,false);advance();}
 case 8->{check(OmniNetworks.get(server).count(link)==3,"destroy_endpoint_removes_virtual_edge");b=place(level,B);b.selectLink(link);advance();}
 case 9->{check(OmniNetworks.get(server).count(link)==4,"replaced_endpoint_rejoins");server.saveEverything(false,true,true);energyAudit();OmniPlacementAudit.run(level,player);
 report.put("registryEntries",OmniLinkSavedData.get(server).directory().size());
 for(Direction direction:Direction.values()){
 var nativeState=AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,direction);
 var own=OmniContent.BLOCK.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,direction);
 check(nativeState.getShape(level,A).toAabbs().equals(own.getShape(level,A).toAabbs()),"native_shape_"+direction);
 }
 report.put("spriteWidth",com.example.ae2lightoptimizer.client.OmniWhiteSprite.sourceWidth);report.put("spriteHeight",com.example.ae2lightoptimizer.client.OmniWhiteSprite.sourceHeight);check(com.example.ae2lightoptimizer.client.OmniWhiteSprite.changedPixels>0,"white_sprite_loaded");
 server.getLevel(Level.NETHER).setChunkForced(R.getX()>>4,R.getZ()>>4,false);advance();}
 case 10->{
  if(server.getLevel(Level.NETHER).getChunkSource().hasChunk(R.getX()>>4,R.getZ()>>4)||OmniNetworks.get(server).count(link)!=3){
   if(level.getGameTime()-since>1200)throw new IllegalStateException("Real nether chunk unload timeout");return;
  }
  check(c.isRemoved() && c.getMainNode().getNode()==null,"unloaded_endpoint_old_node_destroyed");
  check(OmniNetworks.get(server).count(link)==3,"actual_chunk_unload_detaches");
  server.getLevel(Level.NETHER).setChunkForced(R.getX()>>4,R.getZ()>>4,true);advance();}
 case 11->{c=(OmniConnectorBlockEntity)server.getLevel(Level.NETHER).getBlockEntity(R);check(c!=null && c.linkId().equals(link),"chunk_reload_preserves_binding");check(OmniNetworks.get(server).count(link)==4,"chunk_reload_rejoins_star");
  check(com.example.ae2lightoptimizer.client.OmniWhiteSprite.coloredPixelsRemaining==0,"white_sprite_no_colored_pixels");
  a.open(player);ready=true;save();}
 }
 }catch(Throwable ex){fail(ex);}}
 static void energyAudit() throws Exception {
  var grid=node(a).getGrid();
  var edges=Collections.newSetFromMap(new IdentityHashMap<IGridConnection,Boolean>());
  int sum=0;
  for(var n:grid.getNodes()){
   if(!(n.getOwner() instanceof OmniNetworkNode) && !(n.getOwner() instanceof appeng.blockentity.networking.ControllerBlockEntity))sum+=n.getUsedChannels();
   for(var edge:n.getConnections())edges.add(edge);
   if(n.getOwner() instanceof OmniNetworkNode)check(((appeng.me.GridNode)n).getIdlePowerUsage()==0,"omni_nodes_idle_zero");
  }
  for(var edge:edges){
   var concrete=(appeng.me.GridConnection)edge;
   if(!(concrete.a().getOwner() instanceof OmniNetworkNode && concrete.b().getOwner() instanceof OmniNetworkNode))sum+=edge.getUsedChannels();
  }
  var field=appeng.me.service.PathingService.class.getDeclaredField("channelPowerUsage");field.setAccessible(true);
  double actual=field.getDouble(grid.getPathingService());report.put("nativeChannelPower",actual);report.put("expectedOrdinaryInfrastructurePower",sum/128.0);
  check(Math.abs(actual-sum/128.0)<1e-9,"omni_nodes_and_virtual_edges_no_channel_energy");
 }
 static void finish(){report.put("passed",true);save();ProbeState.finished=true;}
 static void fail(Throwable ex){report.put("failure",ex.toString());save();ex.printStackTrace();ProbeState.finished=true;ready=true;}
}
