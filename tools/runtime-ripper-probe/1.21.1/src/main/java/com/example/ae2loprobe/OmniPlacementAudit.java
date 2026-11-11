package com.example.ae2loprobe;
import com.example.ae2lightoptimizer.omni.*;
import appeng.core.definitions.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.*;
final class OmniPlacementAudit {
 static void run(ServerLevel level,ServerPlayer player)throws Exception {
  var pos=new BlockPos(8,100,5);
  for(var direction:Direction.values())for(boolean water:List.of(false,true)){
   level.setBlockAndUpdate(pos,water?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState());
   var stack=OmniContent.ITEM.get().getDefaultInstance();
   var context=new DirectionalPlaceContext(level,pos,direction,stack,direction.getOpposite());
   var original=AEBlocks.WIRELESS_ACCESS_POINT.block().getStateForPlacement(context);
   var own=OmniContent.BLOCK.get().getStateForPlacement(context);
   OmniProbe.check(original.getValue(BlockStateProperties.FACING)==own.getValue(BlockStateProperties.FACING),"placement_facing_"+direction+"_"+water);
   OmniProbe.check(original.getValue(BlockStateProperties.WATERLOGGED)==own.getValue(BlockStateProperties.WATERLOGGED),"placement_water_"+direction+"_"+water);
   OmniProbe.check(original.getCollisionShape(level,pos).toAabbs().equals(own.getCollisionShape(level,pos).toAabbs()),"native_collision_"+direction+"_"+water);
  }
  level.removeBlock(pos,false);
  var input=CraftingInput.of(3,1,List.of(AEBlocks.WIRELESS_ACCESS_POINT.stack(),com.example.ae2lightoptimizer.item.ModItems.LOOP_CRYSTAL.get().getDefaultInstance(),AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack()));
  var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
  var output=recipe.value().assemble(input,level.registryAccess());
  OmniProbe.check(output.is(OmniContent.ITEM.get())&&output.getCount()==4,"native_shapeless_recipe_produces_four");
  var saved=player.getMainHandItem().copy();boolean shift=player.isShiftKeyDown();
  try {
   level.setBlockAndUpdate(pos,OmniContent.BLOCK.get().defaultBlockState());
   ((OmniConnectorBlockEntity)level.getBlockEntity(pos)).selectLink(OmniProbe.link);
   player.setShiftKeyDown(true);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,AEItems.CERTUS_QUARTZ_WRENCH.stack());
   var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);
   var result=appeng.hooks.WrenchHook.onPlayerUseBlock(player,level,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
   OmniProbe.check(result.consumesAction()&&level.getBlockState(pos).isAir(),"native_wrench_dismantles");
   ItemStack carrier=ItemStack.EMPTY;int slot=-1;
   for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(OmniContent.ITEM.get())){carrier=player.getInventory().getItem(i).copyWithCount(1);slot=i;break;}
   OmniProbe.check(slot>=0,"wrench_returns_connector_item");player.getInventory().getItem(slot).shrink(1);
   var placed=((BlockItem)carrier.getItem()).place(new DirectionalPlaceContext(level,pos,Direction.NORTH,carrier,Direction.SOUTH));
   OmniProbe.check(placed.consumesAction()&&((OmniConnectorBlockEntity)level.getBlockEntity(pos)).linkId().equals(OmniProbe.link),"wrench_item_replacement_preserves_link");
   level.removeBlock(pos,false);
  }finally{player.setShiftKeyDown(shift);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,saved);}
 }
}
