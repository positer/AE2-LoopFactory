package com.example.ae2lightoptimizer.factory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.util.SettingsFrom;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import java.util.ArrayList;
import java.util.List;

/** Dismantling cancels work. Only actual resources remain owned, in independent long-sized segments. */
final class FactoryRecovery {
    private static final String KEY="Ae2lfFactoryRecovery";
    record Cargo(List<GenericStack> resources,List<String> nativeRecovery) {
        static final Cargo EMPTY=new Cargo(List.of(),List.of());
        static final Codec<Cargo> CODEC=RecordCodecBuilder.create(i->i.group(
                GenericStack.CODEC.listOf().fieldOf("resources").forGetter(Cargo::resources),
                FactoryPatternData.LARGE_TEXT.listOf().fieldOf("nativeRecovery").forGetter(Cargo::nativeRecovery)).apply(i,Cargo::new));
        Cargo {
            resources=List.copyOf(resources);nativeRecovery=List.copyOf(nativeRecovery);
            if(resources.stream().anyMatch(s->s.amount()<=0))throw new IllegalArgumentException("Recovery amounts must be positive");
        }
        boolean isEmpty(){return resources.isEmpty()&&nativeRecovery.isEmpty();}
    }
    private record Saved(int version,String kind,Cargo cargo) {
        private static final Codec<Saved> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.INT.fieldOf("version").forGetter(Saved::version),
                Codec.STRING.fieldOf("kind").forGetter(Saved::kind),
                Cargo.CODEC.fieldOf("cargo").forGetter(Saved::cargo)).apply(i,Saved::new));
    }
    private final List<GenericStack> resources=new ArrayList<>();
    private final List<String> nativeRecovery=new ArrayList<>();
    boolean isEmpty(){return resources.isEmpty()&&nativeRecovery.isEmpty();}
    void clear(){resources.clear();nativeRecovery.clear();}
    void add(Cargo cargo){resources.addAll(cargo.resources());nativeRecovery.addAll(cargo.nativeRecovery());}
    Cargo snapshot(){return new Cargo(resources,nativeRecovery);}
    String waitingStatus(){return !nativeRecovery.isEmpty()?"Saved native recovery resources remain pending":!resources.isEmpty()?"Waiting to return saved resources to the ME network":"";}
    void tick(FactoryBlockEntity host) {
        if(resources.isEmpty()||!host.getMainNode().isActive()||host.getMainNode().getGrid()==null)return;
        var inventory=host.getMainNode().getGrid().getStorageService().getInventory();
        var action=IActionSource.ofMachine(host);
        for(var iterator=resources.listIterator();iterator.hasNext();) {
            var stack=iterator.next();long sent=inventory.insert(stack.what(),stack.amount(),Actionable.MODULATE,action);
            if(sent<0||sent>stack.amount())throw new IllegalStateException("ME storage violated recovery insertion contract");
            if(sent==0)continue;
            // Commit ownership immediately. Never defer this subtraction until a later insert or serialization.
            if(sent==stack.amount())iterator.remove();else iterator.set(new GenericStack(stack.what(),stack.amount()-sent));
            host.saveChanges();
        }
        // Native rollback records currently have no replay decoder; retain them verbatim, never claim a return.
    }
    static Cargo collect(Cargo pending,List<FactoryJob.Saved> jobs,List<GenericStack> induction) {
        var resources=new ArrayList<>(pending.resources());resources.addAll(induction);
        var nativeRecovery=new ArrayList<>(pending.nativeRecovery());
        for(var job:jobs) {
            resources.addAll(job.input());resources.addAll(job.output());nativeRecovery.addAll(job.nativeRecovery());
        }
        // No merging: multiple buffers may jointly exceed Long.MAX_VALUE for the same component-aware key.
        return new Cargo(resources,nativeRecovery);
    }
    static ItemStack capture(FactoryBlockEntity host,Cargo cargo) {
        var envelope=new CompoundTag();
        var ops=host.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        envelope.put(KEY,Saved.CODEC.encodeStart(ops,new Saved(2,host.kind().name(),cargo)).getOrThrow());
        var carrier=new ItemStack(host.getBlockState().getBlock());
        carrier.applyComponents(withoutCargo(host.exportSettings(SettingsFrom.DISMANTLE_ITEM,null)));
        carrier.set(DataComponents.CUSTOM_DATA,CustomData.of(envelope));
        var lines=new ArrayList<Component>();
        lines.add(Component.translatableWithFallback("gui.ae2lightoptimizer.factory.recovery_saved",
                "Buffered resources saved. Place on a network to return them.").withStyle(net.minecraft.ChatFormatting.GRAY));
        if(!cargo.nativeRecovery().isEmpty())lines.add(Component.translatableWithFallback(
                "gui.ae2lightoptimizer.factory.recovery_native_pending","Native recovery resources remain pending.").withStyle(net.minecraft.ChatFormatting.YELLOW));
        carrier.set(DataComponents.LORE,new ItemLore(lines));return carrier;
    }
    static DataComponentMap withoutCargo(DataComponentMap components) {
        var builder=DataComponentMap.builder().addAll(components);boolean changed=false;
        var custom=components.get(DataComponents.CUSTOM_DATA);
        if(custom!=null&&custom.copyTag().contains(KEY)) {
            var cleaned=custom.copyTag();cleaned.remove(KEY);
            builder.set(DataComponents.CUSTOM_DATA,cleaned.isEmpty()?null:CustomData.of(cleaned));changed=true;
        }
        var lore=components.get(DataComponents.LORE);
        if(lore!=null) {
            var retained=lore.lines().stream().filter(line->!(line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)
                    ||!java.util.Set.of("gui.ae2lightoptimizer.factory.recovery_saved","gui.ae2lightoptimizer.factory.recovery_native_pending").contains(text.getKey())).toList();
            if(retained.size()!=lore.lines().size()){builder.set(DataComponents.LORE,retained.isEmpty()?null:new ItemLore(retained));changed=true;}
        }
        return changed?builder.build():components;
    }
    static Cargo decode(FactoryBlockEntity host,DataComponentMap components) {
        var custom=components.get(DataComponents.CUSTOM_DATA);
        if(custom==null)return Cargo.EMPTY;
        var encoded=custom.copyTag().get(KEY);if(encoded==null)return Cargo.EMPTY;
        var saved=Saved.CODEC.parse(host.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),encoded).getOrThrow();
        if(saved.version()!=2||!saved.kind().equals(host.kind().name()))throw new IllegalArgumentException("Invalid factory resource carrier");
        return saved.cargo();
    }
}
