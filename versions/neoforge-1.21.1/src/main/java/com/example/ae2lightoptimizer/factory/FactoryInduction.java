package com.example.ae2lightoptimizer.factory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import com.example.ae2lightoptimizer.storage.PortableEnergyMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Optional addon integration by public registry IDs; no addon Java class dependency. */
public final class FactoryInduction {
    public static final long CAPACITY=1_000_000;
    private static Item card=Items.AIR;
    public static void setup() {
        card=BuiltInRegistries.ITEM.stream().filter(item->BuiltInRegistries.ITEM.getKey(item).toString().equals("appflux:induction_card")).findFirst().orElse(Items.AIR);
        if(card!=Items.AIR)Upgrades.add(card,FactoryContent.PROVIDER.get(),1);
    }
    public static boolean installed(FactoryProviderLogic logic) {
        return card!=Items.AIR && (Object)logic instanceof IUpgradeableObject upgrades && upgrades.getUpgrades().isInstalled(card);
    }
    public static void tick(FactoryBlockEntity host,FactoryBuffer buffer) {
        if(!host.isolated() || FactoryServer.owner(host.factoryGrid())!=host)return;
        var action=IActionSource.ofMachine(host);
        var network=host.getMainNode().getGrid().getStorageService().getInventory();
        if(!installed(host.getLogic())) {
            for(var stack:buffer.snapshot()) {
                long inserted=network.insert(stack.what(),stack.amount(),Actionable.MODULATE,action);
                buffer.extract(stack.what(),inserted,Actionable.MODULATE,action);
                if(inserted>0)host.saveChanges();
            }
            return;
        }
        long amount=0;for(var stack:buffer.snapshot())amount=Math.addExact(amount,stack.amount());
        if(amount>=CAPACITY)return;
        long moved=FactoryTransfers.move(network,buffer,key->PortableEnergyMath.isForgeEnergy(key.getType().getId().toString(),key.getId().toString()),
                CAPACITY-amount,action,stack->{throw new IllegalStateException("Induction rollback failed");});
        if(moved>0)host.saveChanges();
    }
    private FactoryInduction() {}
}
