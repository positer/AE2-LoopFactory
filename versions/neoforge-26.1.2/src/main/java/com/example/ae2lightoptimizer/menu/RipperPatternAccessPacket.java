package com.example.ae2lightoptimizer.menu;

import appeng.core.network.clientbound.PatternAccessTerminalPacket;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Ripper-only transport: bounded chunks and VarInt slot identities; AE2's wire codec stays untouched. */
public record RipperPatternAccessPacket(PatternAccessTerminalPacket contents) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 128;
    public static final Type<RipperPatternAccessPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ae2lightoptimizer", "ripper_patterns"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RipperPatternAccessPacket> CODEC = StreamCodec.of(
            (buffer, message) -> message.write(buffer), RipperPatternAccessPacket::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf buffer) {
        var p = contents;
        buffer.writeVarLong(p.inventoryId()); buffer.writeBoolean(p.fullUpdate());
        if (p.fullUpdate()) { buffer.writeVarInt(p.inventorySize()); buffer.writeVarLong(p.sortBy()); p.group().writeToPacket(buffer); }
        if (p.slots().size() > MAX_ENTRIES) throw new IllegalArgumentException("Oversized Ripper pattern chunk");
        buffer.writeVarInt(p.slots().size());
        for (var entry : p.slots().int2ObjectEntrySet()) {
            buffer.writeVarInt(entry.getIntKey()); ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.getValue());
        }
    }
    private static RipperPatternAccessPacket read(RegistryFriendlyByteBuf buffer) {
        long id = buffer.readVarLong(); boolean full = buffer.readBoolean();
        int size = 0; long sort = 0; PatternContainerGroup group = null;
        if (full) { size = buffer.readVarInt(); sort = buffer.readVarLong(); group = PatternContainerGroup.readFromPacket(buffer); }
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid Ripper pattern chunk size");
        var slots = new Int2ObjectOpenHashMap<ItemStack>(count);
        for (int i = 0; i < count; i++) {
            int slot = buffer.readVarInt();
            if (slot < 0 || (full && slot >= size)) throw new IllegalArgumentException("Invalid Ripper slot index");
            slots.put(slot, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        return new RipperPatternAccessPacket(new PatternAccessTerminalPacket(full, id, size, sort, group, slots));
    }
    public static void send(net.minecraft.server.level.ServerPlayer player, PatternAccessTerminalPacket packet) {
        var chunk = new Int2ObjectOpenHashMap<ItemStack>(); boolean full = packet.fullUpdate();
        for (var entry : packet.slots().int2ObjectEntrySet()) {
            chunk.put(entry.getIntKey(), entry.getValue());
            if (chunk.size() == MAX_ENTRIES) {
                sendChunk(player, packet, full, chunk); full = false;
                chunk = new Int2ObjectOpenHashMap<>();
            }
        }
        if (full || !chunk.isEmpty()) sendChunk(player, packet, full, chunk);
    }
    private static void sendChunk(net.minecraft.server.level.ServerPlayer player, PatternAccessTerminalPacket original,
            boolean full, it.unimi.dsi.fastutil.ints.Int2ObjectMap<ItemStack> slots) {
        var packet = full ? PatternAccessTerminalPacket.fullUpdate(original.inventoryId(), original.inventorySize(), original.sortBy(), original.group(), slots)
                : PatternAccessTerminalPacket.incrementalUpdate(original.inventoryId(), slots);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new RipperPatternAccessPacket(packet));
    }
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (message, context) -> context.enqueueWork(() ->
                com.example.ae2lightoptimizer.client.RipperPatternAccessClient.handle(message.contents())));
    }
}
