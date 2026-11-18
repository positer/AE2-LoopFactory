package com.example.ae2lightoptimizer;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Only our own entries receive old IDs, even when another addon shares the namespace. */
@net.neoforged.fml.common.Mod(LegacyNamespace.ID)
public final class LegacyNamespace {
    public static final String ID = "ae2lightoptimizer";

    public LegacyNamespace() {}

    public static void bind(DeferredRegister<?> entries, IEventBus bus) {
        entries.register(bus);
        bus.addListener(EventPriority.LOWEST, (RegisterEvent event) -> {
            if (entries.getRegistryKey().equals(event.getRegistryKey())) {
                for (var entry : entries.getEntries()) {
                    var id = entry.getId();
                    event.getRegistry().addAlias(ResourceLocation.fromNamespaceAndPath(ID, id.getPath()), id);
                }
            }
        });
    }
}
