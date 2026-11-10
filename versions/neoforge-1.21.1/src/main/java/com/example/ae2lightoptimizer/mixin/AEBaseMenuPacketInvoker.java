package com.example.ae2lightoptimizer.mixin;

@org.spongepowered.asm.mixin.Mixin(value = appeng.menu.AEBaseMenu.class, remap = false)
public interface AEBaseMenuPacketInvoker {
    @org.spongepowered.asm.mixin.gen.Invoker("sendPacketToClient")
    void ae2lf$sendNative(appeng.core.network.ClientboundPacket packet);
}
