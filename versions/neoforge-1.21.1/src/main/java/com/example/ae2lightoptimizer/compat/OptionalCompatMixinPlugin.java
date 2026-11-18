package com.example.ae2lightoptimizer.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the optional Thunderbolt channel bridge only when Thunderbolt (AE2 Lightning Tech's channel
 * engine) is on the classpath. Without it the bridge mixins stay unapplied, so AE2LT keeps its native
 * AE2 pathing behaviour and never hard-depends on that mod.
 */
public final class OptionalCompatMixinPlugin implements IMixinConfigPlugin {
    private static final String THUNDERBOLT = "com.moakiee.thunderbolt.core.channel.HighCapacityChannelSupport";
    private boolean present;
    @Override public void onLoad(String mixinPackage) { present = isPresent(THUNDERBOLT); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return present; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    private static boolean isPresent(String name) {
        // Never Class.forName a mixin target here: loading it before the mixin transformer prepares the
        // config throws MixinTargetAlreadyLoadedException. A class resource lookup does not load it.
        var resource = name.replace('.', '/') + ".class";
        return OptionalCompatMixinPlugin.class.getClassLoader().getResource(resource) != null;
    }
}
