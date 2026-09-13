package com.example.ae2lightoptimizer.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.api.crafting.PatternDetailsHelper;
import com.example.ae2lightoptimizer.factory.FactoryCompiler;
import com.example.ae2lightoptimizer.factory.FactoryPatternData;
import com.example.ae2lightoptimizer.factory.FactoryPatternItem;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native terminal owns recipe input and output semantics; only the physical pattern wrapper changes. */
@Mixin(PatternEncodingTermMenu.class)
public abstract class FactoryPatternEncodingMixin {
    @Shadow @Final private RestrictedInputSlot blankPatternSlot;
    @Shadow @Final private RestrictedInputSlot encodedPatternSlot;
    @Inject(method = "encode", at = @At("HEAD"), cancellable = true)
    private void ae2lightoptimizer$encodeFactoryPattern(CallbackInfo callback) {
        var menu = (AEBaseMenu) (Object) this;
        if (menu.isClientSide()) return;
        boolean replace = !encodedPatternSlot.getItem().isEmpty();
        var original = replace ? encodedPatternSlot.getItem() : blankPatternSlot.getItem();
        if (!(original.getItem() instanceof FactoryPatternItem)) return;
        callback.cancel();
        if (!menu.stillValid(menu.getPlayer()) || (replace && original.getCount() != 1)) return;
        var recipe = ((FactoryPatternEncodingAccess) this).ae2lightoptimizer$encodeNativeRecipe();
        if (recipe == null || recipe.isEmpty()) return;
        var nativePattern = PatternDetailsHelper.decodePattern(recipe, menu.getPlayer().level());
        if (nativePattern == null) return;
        var data = FactoryPatternData.get(original);
        try { FactoryCompiler.compile(data.code(), true, nativePattern.getInputs().length); }
        catch (IllegalArgumentException error) {
            menu.getPlayer().sendSystemMessage(Component.literal(error.getMessage()));
            return;
        }
        var result = original.copyWithCount(1);
        result.set(FactoryPatternData.TYPE.get(), new FactoryPatternData(data.code(), data.factoryId(), recipe));
        if (!replace) { original.shrink(1); blankPatternSlot.setChanged(); }
        encodedPatternSlot.set(result);
        menu.broadcastChanges();
    }
}
