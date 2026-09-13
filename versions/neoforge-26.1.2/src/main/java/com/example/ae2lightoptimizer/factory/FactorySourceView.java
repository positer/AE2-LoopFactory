package com.example.ae2lightoptimizer.factory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

/** Per-PUT availability ceiling only; every extraction still occurs in the physical source. */
final class FactorySourceView implements MEStorage {
    private final MEStorage storage;
    private final KeyCounter remaining;
    FactorySourceView(MEStorage storage){this.storage=storage;remaining=new KeyCounter();storage.getAvailableStacks(remaining);}
    @Override public Component getDescription(){return storage.getDescription();}
    @Override public void getAvailableStacks(KeyCounter result){
        for(var stack:storage.getAvailableStacks()) {
            long amount=Math.min(stack.getLongValue(),remaining.get(stack.getKey()));
            if(amount>0)result.add(stack.getKey(),amount);
        }
    }
    @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source){
        MEStorage.checkPreconditions(key,amount,mode,source);
        long allowed=Math.min(amount,Math.max(0,remaining.get(key)));
        long extracted=storage.extract(key,allowed,mode,source);
        if(extracted<0||extracted>allowed)throw new IllegalStateException("Storage violated extraction contract");
        if(mode==Actionable.MODULATE&&extracted>0)remaining.remove(key,extracted);
        return extracted;
    }
    @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source){
        // Only rollback enters this wrapper; restore its unused availability along with the actual resources.
        long inserted=storage.insert(key,amount,mode,source);
        if(inserted<0||inserted>amount)throw new IllegalStateException("Storage violated rollback contract");
        if(mode==Actionable.MODULATE&&inserted>0)remaining.add(key,inserted);
        return inserted;
    }
}
