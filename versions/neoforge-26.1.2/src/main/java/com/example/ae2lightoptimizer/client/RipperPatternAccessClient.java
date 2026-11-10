package com.example.ae2lightoptimizer.client;

/** Reuse AE2's native terminal screen and inventory application, on the client thread. */
public final class RipperPatternAccessClient {
    public static void handle(appeng.core.network.clientbound.PatternAccessTerminalPacket packet) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof appeng.client.gui.me.patternaccess.PatternAccessTermScreen<?> screen) {
            if (packet.fullUpdate()) screen.postFullUpdate(packet.inventoryId(), packet.sortBy(), packet.group(), packet.inventorySize(), packet.slots());
            else screen.postIncrementalUpdate(packet.inventoryId(), packet.slots());
        }
    }
    private RipperPatternAccessClient() {}
}
