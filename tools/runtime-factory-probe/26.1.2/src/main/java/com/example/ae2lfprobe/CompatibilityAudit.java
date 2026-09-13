package com.example.ae2lfprobe;
import java.util.*;
import java.nio.file.*;
import appeng.api.stacks.*;
import appeng.api.storage.*;
import appeng.api.storage.cells.StorageCell;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import com.example.ae2lightoptimizer.factory.*;
import com.example.ae2lightoptimizer.item.ModItems;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;

public final class CompatibilityAudit {
    public static volatile boolean finished,passed;
    public static void run(net.minecraft.server.level.ServerLevel level) {
        var result=new LinkedHashMap<String,Object>();var cases=new ArrayList<Map<String,Object>>();
        result.put("registeredKeyTypes",AEKeyTypes.getAll().stream().map(t->t.getId().toString()).toList());
        result.put("loadedMods",net.neoforged.fml.ModList.get().getMods().stream().map(m->m.getModId()+":"+m.getVersion()).toList());
        try {
            FactoryProbe.requirePoweredSharedNetwork(level,result,"networkBeforeCompatibility");
            rejectedResources(result);
            var flux=KeySamples.flux("FE");
            if(flux!=null) {
                var conversion=FactoryNativeTransfers.class.getDeclaredMethod("resource",AEKey.class);conversion.setAccessible(true);
                boolean nativeFe=conversion.invoke(null,flux)!=null&&FactoryResourceSelector.parse("neoforge::fe").test(flux);
                result.put("native_FE_bridge_accepts_FE",nativeFe);result.put("native_FE_key_id",flux.getId().toString());
                if(!nativeFe)throw new IllegalStateException("Native FE bridge rejected the real addon FE key");
            } else result.put("native_FE_bridge","appflux not loaded");
            // Every registered key type is sampled; a type without a known constructor is reported, never guessed.
            var keys=new ArrayList<AEKey>();var samples=new LinkedHashMap<String,String>();
            for(var type:AEKeyTypes.getAll()) {
                var id=type.getId().toString();var key=KeySamples.forType(id);
                samples.put(id,key==null?"no-sample-constructor":key.getId().toString());
                if(key!=null&&!keys.contains(key))keys.add(key);
            }
            result.put("sampleKeys",samples);
            for(var key:keys) {
                var row=new LinkedHashMap<String,Object>();row.put("key",key.toString());row.put("keyType",key.getType().getId().toString());
                try {
                    ItemStack a=KeySamples.cell(key);
                    ItemStack b=KeySamples.cell(key);
                    var source=StorageCells.getCellInventory(a,null);var target=StorageCells.getCellInventory(b,null);
                    if(target==null)throw new IllegalStateException("Missing native destination cell");
                    var action=IActionSource.empty();
                    if(source.insert(key,1000,Actionable.MODULATE,action)!=1000)throw new IllegalStateException("source insertion");
                    String selector=KeySamples.selector(key);
                    var host=new CellHost(source,target,selector);
                    var program=FactoryCompiler.compile("import A,B\nget 600 "+selector+" from A\nwait 1 tick\nput "+selector+" into B\ndone",false);
                    var vm=new FactoryMachine(program);vm.tick(host);
                    if(source.getAvailableStacks().get(key)!=1000 || target.getAvailableStacks().get(key)!=0)throw new IllegalStateException("GET extracted before PUT");
                    vm=FactoryMachine.restore(program,vm.snapshot());vm.tick(host);
                    if(!vm.error().isEmpty() || !vm.stopped() || source.getAvailableStacks().get(key)!=400 || target.getAvailableStacks().get(key)!=600)throw new IllegalStateException("exact VM route failed: "+vm.error());
                    source.persist();target.persist();
                    if(StorageCells.getCellInventory(a,null).getAvailableStacks().get(key)!=400 || StorageCells.getCellInventory(b,null).getAvailableStacks().get(key)!=600)throw new IllegalStateException("cell persistence");
                    long returned=FactoryTransfers.move(target,source,FactoryResourceSelector.parse(selector),600,action,x->{throw new IllegalStateException("unexpected recovery");});
                    if(returned!=600 || source.getAvailableStacks().get(key)!=1000)throw new IllegalStateException("reverse transfer");
                    row.put("nativeDestination",BuiltInRegistries.ITEM.getKey(b.getItem()).toString());row.put("getIsDeclaration",true);row.put("waitRestoreAndExactTransfer",true);row.put("cellPersistence",true);row.put("reverseTransfer",true);
                    // Actual provider job: source -> real subnet ME storage -> source -> main ME storage.
                    var provider=(FactoryBlockEntity)level.getBlockEntity(new net.minecraft.core.BlockPos(0,100,0));
                    var subnet=provider.factoryGrid().getStorageService().getInventory();
                    var main=provider.getMainNode().getGrid().getStorageService().getInventory();
                    long before=main.getAvailableStacks().get(key);
                    long subnetBefore=subnet.getAvailableStacks().get(key);
                    var recipe=appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(key,100)),List.of(new GenericStack(key,100)));
                    var pattern=ModItems.LOOP_FACTORY_PATTERN.get().getDefaultInstance();
                    pattern.set(FactoryPatternData.TYPE.get(),new FactoryPatternData("get 100 "+selector+" from source\nput "+selector+" into storage\nwait 1 tick\nget 100 "+selector+" from storage\nput "+selector+" into source\ndone",provider.factoryId(),recipe));
                    var input=new KeyCounter();input.add(key,100);
                    var job=new FactoryJob(provider,(FactoryPatternDetails)appeng.api.crafting.PatternDetailsHelper.decodePattern(pattern,level),new KeyCounter[]{input});
                    job.tick();
                    if(subnet.getAvailableStacks().get(key)!=subnetBefore+100)throw new IllegalStateException("real subnet delivery: "+job.error());
                    var ops=level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                    job=FactoryJob.restore(provider,FactoryJob.Saved.CODEC.parse(ops,FactoryJob.Saved.CODEC.encodeStart(ops,job.save()).getOrThrow()).getOrThrow());
                    job.tick();
                    if(!job.finished() || subnet.getAvailableStacks().get(key)!=subnetBefore || main.getAvailableStacks().get(key)!=before+100)throw new IllegalStateException("real source return: "+job.error());
                    main.extract(key,100,Actionable.MODULATE,action);
                    row.put("realProviderSubnetAndMainReturn",true);row.put("passed",true);
                }catch(Throwable e){row.put("passed",false);row.put("failure",e.toString());}
                cases.add(row);
            }
            modTags(result);
            FactoryProbe.requirePoweredSharedNetwork(level,result,"networkAfterCompatibility");
            result.put("soulBlockCapability","not installed for this generation");
            boolean allCases=cases.stream().allMatch(c->Boolean.TRUE.equals(c.get("passed")));
            result.put("status",allCases&&!Boolean.FALSE.equals(result.get("modRegisteredTagsPassed"))?"passed":"failed");
        }catch(Throwable e){result.put("status","failed");result.put("failure",e.toString());}
        result.put("cases",cases);
        result.put("networkAtFinish",FactoryProbe.sharedNetworkState(level));
        result.put("notCovered",List.of("Induction-card native CPU FE allocation","registered AE key types without a known sample constructor","third-party world capability handlers beyond network, cell storage and native item/fluid/energy capabilities","Industrial Foregoing Souls and the mana/source machine buffers only exist on their own generation and are not AE key types there"));
        try {Files.writeString(Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("compatibility-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(Exception e){throw new RuntimeException(e);}
        passed="passed".equals(result.get("status"));finished=true;
    }
    /** Item and fluid tags shipped by the game and every loaded mod, resolved through the real tag registry. */
    private static void modTags(Map<String,Object> result) {
        var namespaces=new LinkedHashSet<String>();namespaces.add("c");
        for(var mod:net.neoforged.fml.ModList.get().getMods())namespaces.add(mod.getModId());
        var rows=new LinkedHashMap<String,Object>();String routeSelector=null;List<net.minecraft.world.item.Item> routeMembers=List.of();
        int sampled=0,members=0;
        for(var entrySet:net.minecraft.core.registries.BuiltInRegistries.ITEM.getTags().toList()) {
            var tag=entrySet.key();
            if(sampled>=12||!namespaces.contains(tag.location().getNamespace()))continue;
            var holder=entrySet;
            if(holder.size()==0)continue;
            var missing=new ArrayList<String>();var items=new ArrayList<net.minecraft.world.item.Item>();
            var selector="#"+tag.location();
            for(var entry:holder) {items.add(entry.value());members++;
                if(!FactoryResourceSelector.parse(selector).test(AEItemKey.of(entry.value())))
                    missing.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.value()).toString());}
            rows.put(tag.location().toString(),missing.isEmpty()?"all "+holder.size()+" item members match":"MISSED "+missing);
            if(routeSelector==null){routeSelector=selector;routeMembers=items;}
            sampled++;
        }
        for(var entrySet:net.minecraft.core.registries.BuiltInRegistries.FLUID.getTags().toList()) {
            var tag=entrySet.key();
            if(sampled>=16||!namespaces.contains(tag.location().getNamespace()))continue;
            var holder=entrySet;
            if(holder.size()==0)continue;
            var missing=new ArrayList<String>();var selector="#"+tag.location();
            for(var entry:holder) {members++;
                if(!FactoryResourceSelector.parse(selector).test(AEFluidKey.of(entry.value())))
                    missing.add(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(entry.value()).toString());}
            rows.put(tag.location().toString(),missing.isEmpty()?"all "+holder.size()+" fluid members match":"MISSED "+missing);
            sampled++;
        }
        result.put("modRegisteredTagSample",rows);
        result.put("modRegisteredTagMembers",members);
        if(routeSelector!=null) {
            try {result.put("modRegisteredTagRoute",routeSelector+" -> "+tagRoute(routeSelector,routeMembers));}
            catch(Throwable failure) {result.put("modRegisteredTagRoute",routeSelector+" -> FAILED "+failure);}
        }
        result.put("modRegisteredTagsPassed",sampled>0&&rows.values().stream().noneMatch(v->v.toString().startsWith("MISSED"))
            &&!String.valueOf(result.get("modRegisteredTagRoute")).contains("FAILED"));
    }
    private static String tagRoute(String selector,List<net.minecraft.world.item.Item> members) {
        var a=ModItems.LOOP_STORAGE_CELL_1K.get().getDefaultInstance();var b=ModItems.LOOP_STORAGE_CELL_1K.get().getDefaultInstance();
        var source=StorageCells.getCellInventory(a,null);var target=StorageCells.getCellInventory(b,null);var action=IActionSource.empty();
        if(source==null||target==null||members.isEmpty())throw new IllegalStateException("Missing cells or members");
        long inserted=0;for(var item:members) {long accepted=source.insert(AEItemKey.of(item),4,Actionable.MODULATE,action);
            if(accepted!=4)throw new IllegalStateException("tag source insertion rejected "+item);inserted+=4;}
        var host=new CellHost(source,target,selector);
        var vm=new FactoryMachine(FactoryCompiler.compile("import A,B\nget "+selector+" from A\nput "+selector+" into B\ndone",false));
        vm.tick(host);
        if(!vm.error().isEmpty()||!vm.stopped())throw new IllegalStateException("tag route did not finish: "+vm.error());
        long left=source.getAvailableStacks().get(AEItemKey.of(members.get(0)));
        long moved=target.getAvailableStacks().get(AEItemKey.of(members.get(0)));
        if(left!=0||moved!=4)throw new IllegalStateException("tag route moved "+moved+" of 4 and left "+left);
        return "moved "+inserted+" across "+members.size()+" tag members";
    }
    /** Loaded addons expose singleton keys for their own storage types; anything else is reported as unsampled. */
    static final class KeySamples {
        static AEKey flux(String energy) {
            if(!net.neoforged.fml.ModList.get().isLoaded("appflux"))return null;
            try {
                var type=Class.forName("com.glodblock.github.appflux.common.me.key.type.EnergyType");
                return (AEKey)Class.forName("com.glodblock.github.appflux.common.me.key.FluxKey").getMethod("of",type).invoke(null,type.getField(energy).get(null));
            } catch(ReflectiveOperationException failure) {throw new IllegalStateException("Unsupported appflux key API",failure);}
        }
        static AEKey forType(String typeId) {
            return switch(typeId) {
                case "ae2:i" -> AEItemKey.of(Items.IRON_INGOT);
                case "ae2:f" -> AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
                case "appflux:flux" -> flux("FE");
                default -> null;
            };
        }
        static String selector(AEKey key) {
            return switch(key.getType().getId().toString()) {
                case "ae2:i" -> "minecraft::item";
                case "ae2:f" -> "minecraft::fluid";
                case "appflux:flux" -> "neoforge::fe";
                default -> key.getType().getId().toString().replace(":","::");
            };
        }
        /** Addon cells are used when the addon ships one; otherwise the factory's own cell accepts the type. */
        static ItemStack cell(AEKey key) {
            String id=switch(key.getType().getId().toString()) {
                case "appflux:flux" -> "appflux:fe_1k_cell";
                default -> "";
            };
            if(!id.isEmpty()) {var item=BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
                if(item!=Items.AIR) {var stack=item.getDefaultInstance();
                    if(StorageCells.getCellInventory(stack,null)!=null)return stack;}}
            return ModItems.LOOP_STORAGE_CELL_1K.get().getDefaultInstance();
        }
    }
    /** Real cells behind controlled refusal adapters, distinct from third-party handler coverage. */
    private static void rejectedResources(Map<String,Object> result) {
        var source=StorageCells.getCellInventory(ModItems.LOOP_STORAGE_CELL_1K.get().getDefaultInstance(),null);
        var target=StorageCells.getCellInventory(ModItems.LOOP_STORAGE_CELL_1K.get().getDefaultInstance(),null);
        var action=IActionSource.empty();
        for(var item:List.of(Items.IRON_INGOT,Items.GOLD_INGOT,Items.COPPER_INGOT,Items.COAL))source.insert(AEItemKey.of(item),10,Actionable.MODULATE,action);
        var host=new CellHost(new RefusingStorage(source,true),new RefusingStorage(target,false),"minecraft::item");
        var vm=new FactoryMachine(FactoryCompiler.compile("import A,B\nget 40 minecraft::item from A\nput 40 minecraft::item into B\ndone",false));
        vm.tick(host);
        boolean ok=vm.stopped()&&vm.error().isEmpty()&&vm.snapshot().delay()==0
            &&source.getAvailableStacks().get(AEItemKey.of(Items.GOLD_INGOT))==10
            &&source.getAvailableStacks().get(AEItemKey.of(Items.COPPER_INGOT))==10
            &&source.getAvailableStacks().get(AEItemKey.of(Items.IRON_INGOT))==8
            &&target.getAvailableStacks().get(AEItemKey.of(Items.IRON_INGOT))==2
            &&target.getAvailableStacks().get(AEItemKey.of(Items.COAL))==10;
        result.put("resourceRefusalSameTick",ok);
        result.put("resourceRefusalFixture","native cells with controlled source/destination rejection adapters");
        if(!ok)throw new IllegalStateException("Per-resource rejection must preserve refused amounts and finish other resources in one tick");
    }
    private record RefusingStorage(MEStorage delegate,boolean source) implements MEStorage {
        public net.minecraft.network.chat.Component getDescription(){return delegate.getDescription();}
        public void getAvailableStacks(KeyCounter out){delegate.getAvailableStacks(out);}
        public long extract(AEKey key,long amount,Actionable mode,IActionSource action){
            if(source&&key.equals(AEItemKey.of(Items.GOLD_INGOT)))return 0;
            return delegate.extract(key,amount,mode,action);
        }
        public long insert(AEKey key,long amount,Actionable mode,IActionSource action){
            if(!source&&key.equals(AEItemKey.of(Items.COPPER_INGOT)))return 0;
            if(!source&&key.equals(AEItemKey.of(Items.IRON_INGOT)))amount=Math.min(amount,2);
            return delegate.insert(key,amount,mode,action);
        }
    }
    private static final class CellHost implements FactoryMachine.Host {
        final MEStorage a,b;final FactoryRoutes routes=new FactoryRoutes();
        CellHost(MEStorage a,MEStorage b,String selector){this.a=a;this.b=b;}
        MEStorage cell(String tag){return tag.equals("A")?a:b;}
        public long count(String tag,String resource){long count=0;for(var e:cell(tag).getAvailableStacks())if(FactoryResourceSelector.parse(resource).test(e.getKey()))count+=e.getLongValue();return count;}
        public void pulse(String tag,long ticks){throw new UnsupportedOperationException();}
        public long transfer(boolean get,String resource,String tag,String face,long limit){
            if(get){routes.declare(resource,tag,face,limit);return 0;}
            return routes.output(resource,tag,face,limit,(s,d,t,f,n)->FactoryTransfers.move(cell(s.tag()),cell(t),FactoryResourceSelector.parse(s.selector()).and(FactoryResourceSelector.parse(d)),n,IActionSource.empty(),x->{throw new IllegalStateException("Unexpected recovery");}));
        }
    }
}
