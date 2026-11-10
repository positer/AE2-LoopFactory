package com.example.ae2lightoptimizer.mixin;

import appeng.menu.implementations.PatternAccessTermMenu;
import com.example.ae2lightoptimizer.menu.RipperPatternTracker;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ask AE2 for its normal full update before it indexes an obsolete-sized client mirror. */
@Mixin(value = PatternAccessTermMenu.class, remap = false)
public abstract class RipperPatternAccessMixin {
    @Shadow @Final private Map<?, ?> diList;
    @Invoker("getGrid") public abstract appeng.api.networking.IGrid ae2lf$currentGrid();
    @Invoker("sendFullUpdate") public abstract void ae2lf$sendFullUpdate(appeng.api.networking.IGrid grid);
    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void ae2lf$refreshGrowingInventories(CallbackInfo ci) {
        if (diList.values().stream().anyMatch(value -> value instanceof RipperPatternTracker tracker
                && tracker.ae2lf$patternSizeChanged())) ae2lf$sendFullUpdate(ae2lf$currentGrid());
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = {"sendFullUpdate", "sendIncrementalUpdate"},
            at = @At(value = "INVOKE", target = "Lappeng/menu/implementations/PatternAccessTermMenu;sendPacketToClient(Lappeng/core/network/ClientboundPacket;)V"))
    private void ae2lf$sendPatternChunks(PatternAccessTermMenu menu, appeng.core.network.ClientboundPacket packet) {
        if (packet instanceof appeng.core.network.clientbound.PatternAccessTerminalPacket patterns
                && diList.entrySet().stream().anyMatch(entry -> entry.getKey() instanceof com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity
                        && entry.getValue() instanceof RipperPatternTracker tracker && tracker.ae2lf$inventoryId() == patterns.inventoryId())) {
            com.example.ae2lightoptimizer.menu.RipperPatternAccessPacket.send((net.minecraft.server.level.ServerPlayer)menu.getPlayer(), patterns);
        } else ((AEBaseMenuPacketInvoker)menu).ae2lf$sendNative(packet);
    }
}
