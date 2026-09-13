package com.example.ae2lightoptimizer.factory;

import appeng.menu.guisync.PacketWritable;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Clientbound code exceeds AE2's default 32767-character String field limit. */
public record FactoryCodeText(String value) implements PacketWritable {
    public FactoryCodeText {if(value==null||value.length()>FactoryCompiler.MAX_SOURCE_LENGTH)throw new IllegalArgumentException("Invalid code length");}
    public FactoryCodeText(RegistryFriendlyByteBuf buffer){this(buffer.readUtf(FactoryCompiler.MAX_SOURCE_LENGTH));}
    @Override public void writeToPacket(RegistryFriendlyByteBuf buffer){buffer.writeUtf(value,FactoryCompiler.MAX_SOURCE_LENGTH);}
}
