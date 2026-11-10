package com.example.ae2lightoptimizer.client;

/** Reuse AE2's native terminal screen and inventory application, on the client thread. */
public final class RipperPatternAccessClient {
    public static void handle(appeng.core.network.clientbound.PatternAccessTerminalPacket packet) {
        packet.handleOnClient(net.minecraft.client.Minecraft.getInstance().player);
    }
    private RipperPatternAccessClient() {}
}
