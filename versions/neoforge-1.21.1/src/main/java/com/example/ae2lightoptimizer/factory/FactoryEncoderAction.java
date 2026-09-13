package com.example.ae2lightoptimizer.factory;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;

public record FactoryEncoderAction(int delta, BlockPos position, boolean mark, boolean bulk, boolean offhand,
                                   boolean remove) implements CustomPacketPayload {
    public FactoryEncoderAction(int delta, BlockPos position, boolean mark, boolean bulk, boolean offhand) {
        this(delta, position, mark, bulk, offhand, false);
    }
    public static final Type<FactoryEncoderAction> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("ae2lightoptimizer","encoder_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FactoryEncoderAction> CODEC=StreamCodec.of(
        (b,v)->{b.writeVarInt(v.delta);b.writeBlockPos(v.position);b.writeBoolean(v.mark);b.writeBoolean(v.bulk);b.writeBoolean(v.offhand);b.writeBoolean(v.remove);},
        b->new FactoryEncoderAction(b.readVarInt(),b.readBlockPos(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE,CODEC,(message,context)->context.enqueueWork(()->{
            var player=context.player();
            var hand=message.offhand?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
            var stack=player.getItemInHand(hand);
            if(!(stack.getItem() instanceof FactoryEncoderItem))return;
            if(message.mark) {
                if(player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(message.position))>64 || !player.level().hasChunkAt(message.position))return;
                if(message.remove)FactoryEncoderItem.shiftClick(player,stack,message.position,message.bulk);
                else FactoryEncoderItem.mark(player,stack,message.position,message.bulk);
            } else FactoryEncoderItem.select(player,stack,Integer.signum(message.delta));
        }));
    }
}
