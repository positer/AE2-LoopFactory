package com.example.ae2lightoptimizer.factory;

import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import com.example.ae2lightoptimizer.storage.PortableEnergyMath;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.RegisteredResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;
import java.util.function.*;

/** Native transactional machine transport. RegisteredResource handlers need no AE key registration. */
public final class FactoryNativeTransfers {
    private FactoryNativeTransfers() {}
    private static final Direction[] FACES=Direction.values();
    public record Resource(String type,String id,Object token) {
        public boolean matchesParameter(AEKey key) {
            if(token instanceof ItemResource item)return key.equals(AEItemKey.of(item.toStack()));
            if(token instanceof FluidResource fluid)return key.equals(AEFluidKey.of(fluid.toStack(1)));
            if(type.equals("neoforge::fe"))return PortableEnergyMath.isForgeEnergy(key.getType().getId().toString(),key.getId().toString());
            return key.getId().toString().equals(id);
        }
    }
    private record Stock(Resource resource,long amount) {}
    private interface Port {
        List<Stock> contents();
        long insert(Resource resource,long amount,TransactionContext transaction);
        long extract(Resource resource,long amount,TransactionContext transaction);
    }
    private record Endpoint(BlockPos pos,String capability,Port port,List<Stock> stock) {}
    private static long valid(long value,long limit){if(value<0||value>limit)throw new IllegalStateException("Native capability violated amount contract");return value;}
    private static int integer(long amount){return (int)Math.min(Integer.MAX_VALUE,amount);}
    public static long count(ServerLevel level,List<BlockPos> positions,Predicate<Resource> filter) {
        long total=0;for(var endpoint:endpoints(level,positions,null,true))for(var stock:endpoint.stock)if(filter.test(stock.resource))total=Math.addExact(total,stock.amount);return total;
    }
    public static long move(ServerLevel level,List<BlockPos> origins,Direction from,List<BlockPos> destinations,Direction to,Predicate<Resource> filter,long limit,Consumer<String> recovery) {
        var sources=endpoints(level,origins,from,true);var targets=endpoints(level,destinations,to,false);long moved=0;
        for(var source:sources)for(var stock:source.stock) {
            if(!filter.test(stock.resource))continue;long remaining=stock.amount;
            for(var target:targets) {
                if(source.pos.equals(target.pos)||!source.capability.equals(target.capability)||remaining<=0||moved>=limit)continue;
                long offered=Math.min(remaining,limit-moved),inserted;
                try(var transaction=Transaction.openRoot()) {
                    long accepted;
                    try(var simulation=Transaction.open(transaction)) {
                        accepted=valid(target.port.insert(stock.resource,offered,simulation),offered);
                    }
                    if(accepted==0)continue;
                    long extracted=valid(source.port.extract(stock.resource,accepted,transaction),accepted);
                    if(extracted==0)continue;
                    inserted=valid(target.port.insert(stock.resource,extracted,transaction),extracted);
                    // The transaction rolls back both endpoints if acceptance changes after extraction.
                    if(inserted!=extracted)continue;
                    transaction.commit();
                }
                remaining-=inserted;moved+=inserted;
            }
        }
        return moved;
    }
    private static final class ResourceCapabilities {
        // Capability registration completes before server-world transport starts.
        static final List<BlockCapability<?,?>> ALL=BlockCapability.getAll().stream().filter(c->c.typeClass()==ResourceHandler.class&&(c.contextClass()==Direction.class||c.contextClass()==Void.class)).sorted(Comparator.comparing(c->c.name().toString())).toList();
    }
    private static List<Endpoint> endpoints(ServerLevel level,List<BlockPos> positions,Direction side,boolean snapshot) {
        var result=new ArrayList<Endpoint>();
        var capabilities=ResourceCapabilities.ALL;
        for(var pos:positions) {
            if(!level.hasChunkAt(pos))continue;
            for(var capability:capabilities) {
                var port=resolve(side,d->resourcePort(level,pos,capability,d));
                if(port!=null)result.add(new Endpoint(pos,capability.name().toString(),port,snapshot?port.contents():List.of()));
            }
            var energy=resolve(side,d->{var handler=level.getCapability(Capabilities.Energy.BLOCK,pos,d);return handler==null?null:new EnergyPort(handler);});
            if(energy!=null)result.add(new Endpoint(pos,Capabilities.Energy.BLOCK.name().toString(),energy,snapshot?energy.contents():List.of()));
        }
        return result;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Port resourcePort(ServerLevel level,BlockPos pos,BlockCapability capability,Direction side) {
        if(capability.contextClass()==Void.class&&side!=null)return null;
        var handler=(ResourceHandler<?>)level.getCapability(capability,pos,side);
        return handler==null?null:new ResourcePort((ResourceHandler)handler,capability==Capabilities.Item.BLOCK,capability==Capabilities.Fluid.BLOCK);
    }
    private static Port resolve(Direction side,Function<Direction,Port> factory) {
        if(side!=null)return factory.apply(side);
        var ports=new FactoryLazyPorts<Port>(7,i->factory.apply(i==0?null:FACES[i-1]));
        return ports.first()==null?null:new OmniPort(ports);
    }
    private record OmniPort(FactoryLazyPorts<Port> ports) implements Port {
        public List<Stock> contents(){return ports.first().contents();}
        public long insert(Resource resource,long amount,TransactionContext transaction){long total=0;for(var port:ports){total+=valid(port.insert(resource,amount-total,transaction),amount-total);if(total==amount)break;}return total;}
        public long extract(Resource resource,long amount,TransactionContext transaction){long total=0;for(var port:ports){total+=valid(port.extract(resource,amount-total,transaction),amount-total);if(total==amount)break;}return total;}
    }
    private record ResourcePort(ResourceHandler<net.neoforged.neoforge.transfer.resource.Resource> handler,boolean items,boolean fluids) implements Port {
        public List<Stock> contents() {
            var result=new ArrayList<Stock>();
            for(int i=0;i<handler.size();i++) {
                var value=handler.getResource(i);if(value.isEmpty()||!(value instanceof RegisteredResource<?> registered))continue;
                var key=registered.typeHolder().unwrapKey();if(key.isEmpty())continue;
                result.add(new Stock(new Resource(key.get().registry().toString().replace(":","::"),key.get().identifier().toString(),value),handler.getAmountAsLong(i)));
            }
            return result;
        }
        private boolean accepts(Resource resource) {
            if(!(resource.token instanceof net.neoforged.neoforge.transfer.resource.Resource))return false;
            if(items)return resource.token instanceof ItemResource;
            if(fluids)return resource.token instanceof FluidResource;
            // Native custom routes already require the same capability identity at both endpoints.
            return true;
        }
        public long insert(Resource resource,long amount,TransactionContext transaction){return accepts(resource)?handler.insert((net.neoforged.neoforge.transfer.resource.Resource)resource.token,integer(amount),transaction):0;}
        public long extract(Resource resource,long amount,TransactionContext transaction){return accepts(resource)?handler.extract((net.neoforged.neoforge.transfer.resource.Resource)resource.token,integer(amount),transaction):0;}
    }
    private record EnergyPort(EnergyHandler handler) implements Port {
        public List<Stock> contents(){return List.of(new Stock(new Resource("neoforge::fe","neoforge:fe",null),handler.getAmountAsLong()));}
        public long insert(Resource resource,long amount,TransactionContext transaction){return resource.type.equals("neoforge::fe")?handler.insert(integer(amount),transaction):0;}
        public long extract(Resource resource,long amount,TransactionContext transaction){return resource.type.equals("neoforge::fe")?handler.extract(integer(amount),transaction):0;}
    }
    private static long access(Port port,Resource resource,long amount,boolean insert,boolean simulate) {
        try(var transaction=Transaction.openRoot()) {
            long moved=insert?port.insert(resource,amount,transaction):port.extract(resource,amount,transaction);
            if(!simulate)transaction.commit();return moved;
        }
    }
    public static List<MEStorage> storageBridges(ServerLevel level,BlockPos pos,Direction face) {
        return endpoints(level,List.of(pos),face,false).stream().filter(endpoint->Set.of(Capabilities.Item.BLOCK.name().toString(),Capabilities.Fluid.BLOCK.name().toString(),Capabilities.Energy.BLOCK.name().toString()).contains(endpoint.capability)).map(endpoint->(MEStorage)new MEStorage(){
            public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.literal("NeoForge capability");}
            public void getAvailableStacks(KeyCounter counter){for(var stock:endpoint.port.contents()){var key=key(stock.resource);if(key!=null)counter.add(key,stock.amount);}}
            public long insert(AEKey key,long amount,Actionable mode,IActionSource action){var resource=resource(key);return resource==null?0:access(endpoint.port,resource,amount,true,mode==Actionable.SIMULATE);}
            public long extract(AEKey key,long amount,Actionable mode,IActionSource action){var resource=resource(key);return resource==null?0:access(endpoint.port,resource,amount,false,mode==Actionable.SIMULATE);}
        }).toList();
    }
    private static Resource resource(AEKey key) {
        if(key instanceof AEItemKey item)return new Resource("minecraft::item",key.getId().toString(),ItemResource.of(item.toStack()));
        if(key instanceof AEFluidKey fluid)return new Resource("minecraft::fluid",key.getId().toString(),FluidResource.of(fluid.toStack(1)));
        if(PortableEnergyMath.isForgeEnergy(key.getType().getId().toString(),key.getId().toString()))return new Resource("neoforge::fe","neoforge:fe",null);
        return null;
    }
    private static AEKey key(Resource resource) {
        if(resource.token instanceof ItemResource item)return AEItemKey.of(item.toStack());
        if(resource.token instanceof FluidResource fluid)return AEFluidKey.of(fluid.toStack(1));
        if(resource.type.equals("neoforge::fe")&&net.neoforged.fml.ModList.get().isLoaded("appflux"))try {
            var type=Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
            return (AEKey)Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey").getMethod("of",type).invoke(null,type.getField("FE").get(null));
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported optional FE storage bridge",e);}
        return null;
    }
}
