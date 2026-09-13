package com.example.ae2lightoptimizer.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** GuideME 26.1.10-alpha retains the previous line width after an explicit newline. */
@Pseudo
@Mixin(targets="guideme.layout.flow.LineBuilder",remap=false)
public abstract class GuideCodeLineWrapMixin {
    @ModifyVariable(method="iterateRuns",at=@At(value="INVOKE",target="Ljava/lang/StringBuilder;setLength(I)V",ordinal=0,shift=At.Shift.AFTER),ordinal=0,require=0)
    private float ae2lf$resetExplicitLineWidth(float width) {
        return 0;
    }
}
