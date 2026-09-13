package com.example.ae2lightoptimizer.factory;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;
import java.util.*;
import java.util.function.*;

/** Machine-to-machine transport through NeoForge capabilities, without AE2 key registration. */
public final class FactoryNativeTransfers {
 private FactoryNativeTransfers() {}
    private static final Direction[] FACES=Direction.values();
 public record Resource(String type,String id,Object token) {
  public boolean matchesParameter(appeng.api.stacks.AEKey key) {
   if(token instanceof ItemStack item)return key.equals(appeng.api.stacks.AEItemKey.of(item));
   if(token instanceof FluidStack fluid)return key.equals(appeng.api.stacks.AEFluidKey.of(fluid));
   if(type.equals("neoforge::fe"))return com.example.ae2lightoptimizer.storage.PortableEnergyMath.isForgeEnergy(key.getType().getId().toString(),key.getId().toString());
   return key.getId().toString().equals(id);
  }
  String encode(ServerLevel level,long amount,BlockPos origin,Direction side) {
   var json=new com.google.gson.JsonObject();json.addProperty("type",type);json.addProperty("id",id);json.addProperty("amount",amount);json.addProperty("origin",origin.asLong());json.addProperty("side",side==null?"":side.name());
   var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
   if(token instanceof ItemStack item)json.add("stack",ItemStack.CODEC.encodeStart(ops,item.copyWithCount(1)).getOrThrow());
   if(token instanceof FluidStack fluid)json.add("stack",FluidStack.CODEC.encodeStart(ops,fluid.copyWithAmount(1)).getOrThrow());
   return json.toString();
  }
 }
 private record Stock(Resource resource,long amount) {}
 private interface Port {List<Stock> contents();long insert(Resource r,long amount,boolean simulate);long extract(Resource r,long amount,boolean simulate);}
 private record Endpoint(BlockPos pos,Direction face,Port port,List<Stock> stock) {}
 private static int integer(long n){return (int)Math.min(Integer.MAX_VALUE,n);}
 private static long valid(long n,long max){if(n<0||n>max)throw new IllegalStateException("Native capability violated amount contract");return n;}
 public static long count(ServerLevel level,List<BlockPos> positions,Predicate<Resource> filter) {
  long total=0;for(var endpoint:endpoints(level,positions,null,true))for(var stock:endpoint.stock)if(filter.test(stock.resource))total=Math.addExact(total,stock.amount);return total;
 }
 public static long move(ServerLevel level,List<BlockPos> origins,Direction from,List<BlockPos> destinations,Direction to,Predicate<Resource> filter,long limit,Consumer<String> recovery) {
  // Snapshot every origin first: overlapping tags cannot repeatedly relay newly arrived stock.
  var sources=endpoints(level,origins,from,true);var targets=endpoints(level,destinations,to,false);long moved=0;
  for(var source:sources)for(var stock:source.stock) {
   if(!filter.test(stock.resource))continue;long remaining=stock.amount;
   for(var target:targets) {
    if(source.pos.equals(target.pos)||remaining<=0||moved>=limit)continue;
    long offered=Math.min(remaining,limit-moved);
    long accepted=valid(target.port.insert(stock.resource,offered,true),offered);if(accepted==0)continue;
    long extracted=valid(source.port.extract(stock.resource,accepted,false),accepted);if(extracted==0)continue;
    long inserted=valid(target.port.insert(stock.resource,extracted,false),extracted);
    if(inserted<extracted) {
     long restored=valid(source.port.insert(stock.resource,extracted-inserted,false),extracted-inserted);
     if(restored<extracted-inserted){recovery.accept(stock.resource.encode(level,extracted-inserted-restored,source.pos,source.face));throw new IllegalStateException("Native handler invalidated simulation; residual resource retained in saved recovery");}
    }
    remaining-=inserted;moved+=inserted;
   }
  }return moved;
 }
 private static List<Endpoint> endpoints(ServerLevel level,List<BlockPos> positions,Direction face,boolean snapshot) {
  var result=new ArrayList<Endpoint>();for(var pos:positions) {
   if(!level.hasChunkAt(pos))continue;
   var items=resolve(face,d->{var h=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,d);return h==null?null:new ItemPort(h);});if(items!=null)add(result,pos,face,items,snapshot);
   var fluids=resolve(face,d->{var h=level.getCapability(Capabilities.FluidHandler.BLOCK,pos,d);return h==null?null:new FluidPort(h);});if(fluids!=null)add(result,pos,face,fluids,snapshot);
   var energy=resolve(face,d->{var h=level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,d);return h==null?null:new EnergyPort(h);});if(energy!=null)add(result,pos,face,energy,snapshot);
   if(net.neoforged.fml.ModList.get().isLoaded("mekanism")){var chemical=resolve(face,d->Mek.port(level,pos,d));if(chemical!=null)add(result,pos,face,chemical,snapshot);}
   if(net.neoforged.fml.ModList.get().isLoaded("industrialforegoingsouls")){var soul=resolve(face,d->Soul.port(level,pos,d));if(soul!=null)add(result,pos,face,soul,snapshot);}
  }return result;
 }
 private static Port resolve(Direction face,Function<Direction,Port> factory) {
  if(face!=null)return factory.apply(face);
  var ports=new FactoryLazyPorts<Port>(7,i->factory.apply(i==0?null:FACES[i-1]));
  return ports.first()==null?null:new OmniPort(ports);
 }
 /** Unspecified face uses the unsided view and available face handlers. Never sum aliased simulations. */
 private record OmniPort(FactoryLazyPorts<Port> ports) implements Port {
  public List<Stock> contents(){return ports.first().contents();}
  public long insert(Resource r,long amount,boolean simulate){long result=0;for(var p:ports){long n=valid(p.insert(r,simulate?amount:amount-result,simulate),simulate?amount:amount-result);result=simulate?Math.max(result,n):result+n;if(result==amount)break;}return result;}
  public long extract(Resource r,long amount,boolean simulate){long result=0;for(var p:ports){long n=valid(p.extract(r,simulate?amount:amount-result,simulate),simulate?amount:amount-result);result=simulate?Math.max(result,n):result+n;if(result==amount)break;}return result;}
 }
 /** AE references are needed only at source/storage boundaries; physical machine I/O remains native. */
 public static List<appeng.api.storage.MEStorage> storageBridges(ServerLevel level,BlockPos pos,Direction face) {
  return endpoints(level,List.of(pos),face,false).stream().map(e->(appeng.api.storage.MEStorage)new appeng.api.storage.MEStorage(){
   public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.literal("NeoForge capability");}
   public void getAvailableStacks(appeng.api.stacks.KeyCounter counter){for(var stock:e.port.contents()){var key=key(stock.resource);if(key!=null)counter.add(key,stock.amount);}}
   public long insert(appeng.api.stacks.AEKey key,long amount,appeng.api.config.Actionable mode,appeng.api.networking.security.IActionSource action){var r=resource(key);return r==null?0:e.port.insert(r,amount,mode==appeng.api.config.Actionable.SIMULATE);}
   public long extract(appeng.api.stacks.AEKey key,long amount,appeng.api.config.Actionable mode,appeng.api.networking.security.IActionSource action){var r=resource(key);return r==null?0:e.port.extract(r,amount,mode==appeng.api.config.Actionable.SIMULATE);}
  }).toList();
 }
 private static Resource resource(appeng.api.stacks.AEKey key) {
  if(key instanceof appeng.api.stacks.AEItemKey item)return new Resource("minecraft::item",key.getId().toString(),item.toStack());
  if(key instanceof appeng.api.stacks.AEFluidKey fluid)return new Resource("minecraft::fluid",key.getId().toString(),fluid.toStack(1));
  if(com.example.ae2lightoptimizer.storage.PortableEnergyMath.isForgeEnergy(key.getType().getId().toString(),key.getId().toString()))return new Resource("neoforge::fe","neoforge:fe",null);
  return null;
 }
 private static appeng.api.stacks.AEKey key(Resource r) {
  if(r.token instanceof ItemStack item)return appeng.api.stacks.AEItemKey.of(item);
  if(r.token instanceof FluidStack fluid)return appeng.api.stacks.AEFluidKey.of(fluid);
  if(r.type.equals("neoforge::fe")&&net.neoforged.fml.ModList.get().isLoaded("appflux"))try{var type=Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");return (appeng.api.stacks.AEKey)Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey").getMethod("of",type).invoke(null,type.getField("FE").get(null));}catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported optional FE storage bridge",e);}
  return null;
 }
 private static void add(List<Endpoint> result,BlockPos pos,Direction face,Port port,boolean snapshot){result.add(new Endpoint(pos,face,port,snapshot?port.contents():List.of()));}
 private record ItemPort(IItemHandler handler) implements Port {
  public List<Stock> contents(){var list=new ArrayList<Stock>();for(int i=0;i<handler.getSlots();i++){var s=handler.getStackInSlot(i);if(!s.isEmpty())list.add(new Stock(new Resource("minecraft::item",BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),s.copyWithCount(1)),s.getCount()));}return list;}
  public long insert(Resource r,long n,boolean sim){if(!(r.token instanceof ItemStack item))return 0;int offered=integer(n);var rest=item.copyWithCount(offered);for(int i=0;i<handler.getSlots()&&!rest.isEmpty();i++)rest=handler.insertItem(i,rest,sim);return offered-rest.getCount();}
  public long extract(Resource r,long n,boolean sim){if(!(r.token instanceof ItemStack item))return 0;long total=0;for(int i=0;i<handler.getSlots()&&total<n;i++)if(ItemStack.isSameItemSameComponents(item,handler.getStackInSlot(i)))total+=handler.extractItem(i,integer(n-total),sim).getCount();return total;}
 }
 private record FluidPort(IFluidHandler handler) implements Port {
  public List<Stock> contents(){var list=new ArrayList<Stock>();for(int i=0;i<handler.getTanks();i++){var s=handler.getFluidInTank(i);if(!s.isEmpty())list.add(new Stock(new Resource("minecraft::fluid",BuiltInRegistries.FLUID.getKey(s.getFluid()).toString(),s.copyWithAmount(1)),s.getAmount()));}return list;}
  public long insert(Resource r,long n,boolean sim){return r.token instanceof FluidStack f?handler.fill(f.copyWithAmount(integer(n)),sim?IFluidHandler.FluidAction.SIMULATE:IFluidHandler.FluidAction.EXECUTE):0;}
  public long extract(Resource r,long n,boolean sim){return r.token instanceof FluidStack f?handler.drain(f.copyWithAmount(integer(n)),sim?IFluidHandler.FluidAction.SIMULATE:IFluidHandler.FluidAction.EXECUTE).getAmount():0;}
 }
 private record EnergyPort(IEnergyStorage handler) implements Port {
  public List<Stock> contents(){return List.of(new Stock(new Resource("neoforge::fe","neoforge:fe",null),handler.getEnergyStored()));}
  public long insert(Resource r,long n,boolean sim){return r.type.equals("neoforge::fe")?handler.receiveEnergy(integer(n),sim):0;}
  public long extract(Resource r,long n,boolean sim){return r.type.equals("neoforge::fe")?handler.extractEnergy(integer(n),sim):0;}
 }
 /** Resolve MEK's registered NeoForge capability lazily; MEK remains optional. */
 private static final class Mek {
  static final Class<?> STACK, HANDLER, ACTION;
  static final Object EXECUTE,SIMULATE;
  static final java.lang.reflect.Method AMOUNT,COPY,CHEMICAL,TANKS,IN_TANK,INSERT,EXTRACT;
  static final BlockCapability<?,Direction> CAPABILITY;
  static final net.minecraft.core.Registry<?> REGISTRY;
  static {
   try {
    STACK=Class.forName("mekanism.api.chemical.ChemicalStack");HANDLER=Class.forName("mekanism.api.chemical.IChemicalHandler");ACTION=Class.forName("mekanism.api.Action");
    AMOUNT=STACK.getMethod("getAmount");COPY=STACK.getMethod("copyWithAmount",long.class);CHEMICAL=STACK.getMethod("getChemical");
    TANKS=HANDLER.getMethod("getChemicalTanks");IN_TANK=HANDLER.getMethod("getChemicalInTank",int.class);
    INSERT=HANDLER.getMethod("insertChemical",STACK,ACTION);EXTRACT=HANDLER.getMethod("extractChemical",STACK,ACTION);
    EXECUTE=ACTION.getField("EXECUTE").get(null);SIMULATE=ACTION.getField("SIMULATE").get(null);
    Object type=Class.forName("mekanism.common.capabilities.Capabilities").getField("CHEMICAL").get(null);
    @SuppressWarnings("unchecked") var cap=(BlockCapability<?,Direction>)type.getClass().getMethod("block").invoke(type);CAPABILITY=cap;
    REGISTRY=(net.minecraft.core.Registry<?>)Class.forName("mekanism.api.MekanismAPI").getField("CHEMICAL_REGISTRY").get(null);
   }catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported Mekanism capability API",e);}
  }
  static Object call(Object object,java.lang.reflect.Method method,Object...args){try{return method.invoke(object,args);}catch(ReflectiveOperationException e){throw new IllegalStateException("Mekanism capability call failed: "+method.getName(),e);}}
  static long amount(Object stack){return (long)call(stack,AMOUNT);}
  static Object copy(Object stack,long n){return call(stack,COPY,n);}
  @SuppressWarnings({"unchecked","rawtypes"}) static String id(Object stack){return ((net.minecraft.core.Registry)REGISTRY).getKey(call(stack,CHEMICAL)).toString();}
  static Port port(ServerLevel level,BlockPos pos,Direction face){Object handler=level.getCapability(CAPABILITY,pos,face);return handler==null?null:new ChemicalPort(handler);}
  private record ChemicalPort(Object handler) implements Port {
   public List<Stock> contents(){var list=new ArrayList<Stock>();int tanks=(int)call(handler,TANKS);for(int i=0;i<tanks;i++){Object s=call(handler,IN_TANK,i);long n=amount(s);if(n>0)list.add(new Stock(new Resource("mekanism::chemical",id(s),copy(s,1)),n));}return list;}
   public long insert(Resource r,long n,boolean sim){if(!r.type.equals("mekanism::chemical"))return 0;Object rest=call(handler,INSERT,copy(r.token,n),sim?SIMULATE:EXECUTE);return n-amount(rest);}
   public long extract(Resource r,long n,boolean sim){if(!r.type.equals("mekanism::chemical"))return 0;return amount(call(handler,EXTRACT,copy(r.token,n),sim?SIMULATE:EXECUTE));}
  }
 }
 /** Industrial Foregoing Souls registers its own NeoForge block capability; souls stay a capability-native resource. */
 public static boolean soulCapabilityPresent(){return net.neoforged.fml.ModList.get().isLoaded("industrialforegoingsouls")&&Soul.CAPABILITY!=null;}
 private static final class Soul {
  static final Object EXECUTE,SIMULATE;
  static final java.lang.reflect.Method TANKS,IN_TANK,FILL,DRAIN;
  static final BlockCapability<?,Direction> CAPABILITY;
  static {
   Object execute=null,simulate=null;java.lang.reflect.Method tanks=null,inTank=null,fill=null,drain=null;BlockCapability<?,Direction> capability=null;
   try {
    var handler=Class.forName("com.buuz135.industrialforegoingsouls.capabilities.ISoulHandler");
    var action=Class.forName("com.buuz135.industrialforegoingsouls.capabilities.ISoulHandler$Action");
    execute=action.getField("EXECUTE").get(null);simulate=action.getField("SIMULATE").get(null);
    tanks=handler.getMethod("getSoulTanks");inTank=handler.getMethod("getSoulInTank",int.class);
    fill=handler.getMethod("fill",int.class,action);drain=handler.getMethod("drain",int.class,action);
    capability=(BlockCapability<?,Direction>)Class.forName("com.buuz135.industrialforegoingsouls.capabilities.SoulCapabilities").getField("BLOCK").get(null);
   } catch(ReflectiveOperationException unavailable) {capability=null;}
   EXECUTE=execute;SIMULATE=simulate;TANKS=tanks;IN_TANK=inTank;FILL=fill;DRAIN=drain;CAPABILITY=capability;
  }
  static Object call(Object handler,java.lang.reflect.Method method,Object...args){try{return method.invoke(handler,args);}catch(ReflectiveOperationException e){throw new IllegalStateException("Soul capability call failed: "+method.getName(),e);}}
  @SuppressWarnings({"unchecked","rawtypes"})
  static Port port(ServerLevel level,BlockPos pos,Direction face){if(CAPABILITY==null)return null;Object handler=level.getCapability((BlockCapability)CAPABILITY,pos,face);return handler==null?null:new SoulPort(handler);}
  private record SoulPort(Object handler) implements Port {
   public List<Stock> contents(){int tanks=(int)call(handler,TANKS);long total=0;for(int i=0;i<tanks;i++)total+=((Number)call(handler,IN_TANK,i)).longValue();return total>0?List.of(new Stock(new Resource("industrialforegoingsouls::soul","industrialforegoingsouls:soul",null),total)):List.of();}
   public long insert(Resource r,long n,boolean sim){return r.type.equals("industrialforegoingsouls::soul")?((Number)call(handler,FILL,(int)Math.min(Integer.MAX_VALUE,n),sim?SIMULATE:EXECUTE)).longValue():0;}
   public long extract(Resource r,long n,boolean sim){return r.type.equals("industrialforegoingsouls::soul")?((Number)call(handler,DRAIN,(int)Math.min(Integer.MAX_VALUE,n),sim?SIMULATE:EXECUTE)).longValue():0;}
  }
 }
}
