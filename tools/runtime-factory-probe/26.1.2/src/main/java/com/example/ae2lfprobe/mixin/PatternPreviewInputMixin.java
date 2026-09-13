package com.example.ae2lfprobe.mixin;

import com.example.ae2lfprobe.PatternPreviewAudit;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test input only: no production Shift policy, no influence outside PreviewScreen. */
@Mixin(InputConstants.class)
public abstract class PatternPreviewInputMixin {
    @Inject(method="isKeyDown",at=@At("HEAD"),cancellable=true)
    private static void ae2lf$previewModifier(com.mojang.blaze3d.platform.Window window,int key,CallbackInfoReturnable<Boolean> result) {
        var forced=PatternPreviewAudit.inputOverride(key);
        if(forced!=null)result.setReturnValue(forced);
    }
}
