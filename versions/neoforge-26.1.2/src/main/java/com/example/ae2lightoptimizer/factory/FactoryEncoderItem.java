package com.example.ae2lightoptimizer.factory;

import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** A code-only editor with a persistent network identity; never contains a factory resource buffer. */
public final class FactoryEncoderItem extends Item implements IMenuItem {
    public FactoryEncoderItem(Properties properties) { super(properties.stacksTo(1)); }
    @Override public FactoryEncoderHost getMenuHost(Player player, ItemMenuHostLocator locator, BlockHitResult hit) {
        return new FactoryEncoderHost(this, player, locator);
    }
    private static void open(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return;
        if (player.isShiftKeyDown()) {
            clearBinding(player, player.getItemInHand(hand));
        } else MenuOpener.open(FactoryEditorMenu.TYPE.get(), player, MenuLocators.forHand(player, hand));
    }

    /** Drops the network binding while keeping the code draft. */
    public static void clearBinding(Player player, ItemStack stack) {
        var old = FactoryPatternData.get(stack);
        stack.set(FactoryPatternData.TYPE.get(), new FactoryPatternData(old.code(), "", ItemStack.EMPTY));
        if (player instanceof net.minecraft.server.level.ServerPlayer server)
            server.sendSystemMessage(Component.translatable("gui.ae2lightoptimizer.factory.binding_cleared"), true);
    }

    /**
     * A shift click removes the selected tag from a member of the bound network. Any other
     * non-interactive block clears the binding instead, matching shift-clicking air.
     */
    public static void shiftClick(Player player, ItemStack stack, net.minecraft.core.BlockPos pos, boolean bulk) {
        // A block without a block entity is not a machine, so a shift click there clears the binding
        // even when the block merely sits next to a factory cable.
        if (player.level().getBlockEntity(pos) == null) {
            clearBinding(player, stack);
            return;
        }
        var owner = FactoryServer.find(player.level(), FactoryPatternData.get(stack).factoryId());
        if (owner != null && FactoryServer.contains(owner.factoryGrid(), pos)) {
            unmark(player, stack, pos, bulk);
        }
    }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        open(level, player, hand);
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        var level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var pos = context.getClickedPos();
        if (!level.mayInteract(player, pos)) return InteractionResult.FAIL;
        var data = FactoryPatternData.get(stack);
        var target = FactoryServer.at(level, pos);
        if (data.factoryId().isEmpty() && target != null && !player.isShiftKeyDown()) {
            stack.set(FactoryPatternData.TYPE.get(), new FactoryPatternData(data.code(), target.factoryId(), ItemStack.EMPTY));
            ((net.minecraft.server.level.ServerPlayer) player).sendSystemMessage(Component.translatable("gui.ae2lightoptimizer.factory.bound", target.factoryName()), true);
            return InteractionResult.SUCCESS;
        }
        var owner = FactoryServer.find(level, data.factoryId());
        if (player.isShiftKeyDown()) {
            shiftClick(player, stack, pos, false);
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown() && owner != null && FactoryServer.contains(owner.factoryGrid(), pos)) {
            mark(player, stack, pos, false);
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) != null) return InteractionResult.PASS;
        open(level, player, context.getHand());
        return InteractionResult.SUCCESS;
    }
    public static void refresh(Player player, ItemStack stack) {
        var owner=FactoryServer.find(player.level(),FactoryPatternData.get(stack).factoryId());
        var old=stack.getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY);
        if(owner==null){if(!old.equals(FactoryEncoderView.EMPTY))stack.set(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY);return;}
        var names=owner.tags.names();
        String selected=names.contains(old.selected())?old.selected():names.isEmpty()?"":names.getFirst();
        var selectedPositions=new java.util.ArrayList<Long>();var others=new java.util.ArrayList<Long>();
        var machineTags=new java.util.LinkedHashMap<Long,java.util.List<String>>();
        for(String tag:names)for(long value:owner.tags.positions(tag)) {
            var pos=net.minecraft.core.BlockPos.of(value);
            if(!player.level().hasChunkAt(pos) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>96*96
                || !FactoryServer.contains(owner.factoryGrid(),pos) || !player.level().mayInteract(player,pos))continue;
            var list=tag.equals(selected)?selectedPositions:others;
            if(list.size()<4096)list.add(value);
            if(machineTags.size()<4096)machineTags.computeIfAbsent(value,ignored->new java.util.ArrayList<>()).add(tag);
        }
        var machineList=machineTags.entrySet().stream().map(entry->
            new FactoryEncoderView.MachineTags(entry.getKey(),entry.getValue())).toList();
        var view=new FactoryEncoderView(names,selected,selectedPositions,others,machineList,player.level().dimension().toString());
        if(!view.equals(old))stack.set(FactoryEncoderView.TYPE.get(),view);
    }
    public static void select(Player player, ItemStack stack, int delta) {
        refresh(player,stack);
        var view=stack.getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY);
        if(view.tags().isEmpty()){message(player,"no_tags");return;}
        String selected=view.tags().get(Math.floorMod(view.tags().indexOf(view.selected())+delta,view.tags().size()));
        stack.set(FactoryEncoderView.TYPE.get(),new FactoryEncoderView(view.tags(),selected,
            java.util.List.of(),java.util.List.of(),view.machineTags(),view.dimension()));
        refresh(player,stack);message(player,"selected_tag",selected);
    }
    public static void mark(Player player, ItemStack stack, net.minecraft.core.BlockPos start, boolean bulk) {
        if(!player.level().mayInteract(player,start))return;
        var owner=FactoryServer.find(player.level(),FactoryPatternData.get(stack).factoryId());
        if(owner==null || !FactoryServer.contains(owner.factoryGrid(),start))return;
        refresh(player,stack);
        String tag=stack.getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY).selected();
        if(tag.isEmpty()){message(player,"no_tags");return;}
        var type=player.level().getBlockState(start).getBlock();
        var queue=new java.util.ArrayDeque<net.minecraft.core.BlockPos>();var seen=new java.util.HashSet<net.minecraft.core.BlockPos>();queue.add(start);
        int added=0;
        while(!queue.isEmpty() && seen.size()<4096) {
            var pos=queue.removeFirst();if(!seen.add(pos))continue;
            if(!player.level().hasChunkAt(pos) || player.level().getBlockState(pos).getBlock()!=type
                || !player.level().mayInteract(player,pos) || !FactoryServer.contains(owner.factoryGrid(),pos))continue;
            if(owner.tags.tag(tag,pos.asLong(),v->true))added++;
            if(bulk)for(var direction:net.minecraft.core.Direction.values())queue.add(pos.relative(direction));
        }
        owner.saveChanges();refresh(player,stack);message(player,"tagged",tag,added);
    }
    public static void unmark(Player player, ItemStack stack, net.minecraft.core.BlockPos start, boolean bulk) {
        if(!player.level().mayInteract(player,start))return;
        var owner=FactoryServer.find(player.level(),FactoryPatternData.get(stack).factoryId());
        if(owner==null || !FactoryServer.contains(owner.factoryGrid(),start))return;
        refresh(player,stack);
        String tag=stack.getOrDefault(FactoryEncoderView.TYPE.get(),FactoryEncoderView.EMPTY).selected();
        if(tag.isEmpty()){message(player,"no_tags");return;}
        var type=player.level().getBlockState(start).getBlock();
        var queue=new java.util.ArrayDeque<net.minecraft.core.BlockPos>();var seen=new java.util.HashSet<net.minecraft.core.BlockPos>();queue.add(start);
        int removed=0;
        while(!queue.isEmpty() && seen.size()<4096) {
            var pos=queue.removeFirst();if(!seen.add(pos))continue;
            if(!player.level().hasChunkAt(pos) || player.level().getBlockState(pos).getBlock()!=type
                || !player.level().mayInteract(player,pos) || !FactoryServer.contains(owner.factoryGrid(),pos))continue;
            if(owner.tags.remove(tag,pos.asLong()))removed++;
            if(bulk)for(var direction:net.minecraft.core.Direction.values())queue.add(pos.relative(direction));
        }
        owner.saveChanges();refresh(player,stack);message(player,"untagged",tag,removed);
    }
    private static void message(Player player,String key,Object... args) { player.sendOverlayMessage(Component.translatable("gui.ae2lightoptimizer.factory."+key,args)); }

}
