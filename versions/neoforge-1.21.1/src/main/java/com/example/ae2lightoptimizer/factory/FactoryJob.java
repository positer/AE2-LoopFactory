package com.example.ae2lightoptimizer.factory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

/** Durable execution owns source declarations; only provider jobs contain allocated physical inputs. */
public final class FactoryJob implements FactoryMachine.Host {
    public record Saved(FactoryPatternData data, List<GenericStack> input, String routes,
            List<GenericStack> output, List<GenericStack> expected, List<List<GenericStack>> parameters, String continuation, List<String> admissions, List<String> nativeRecovery) {
        public static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                FactoryPatternData.CODEC.fieldOf("program").forGetter(Saved::data),
                GenericStack.CODEC.listOf().fieldOf("source").forGetter(Saved::input),
                FactoryPatternData.LARGE_TEXT.fieldOf("routes").forGetter(Saved::routes),
                GenericStack.CODEC.listOf().fieldOf("output").forGetter(Saved::output),
                GenericStack.CODEC.listOf().fieldOf("expected").forGetter(Saved::expected),
                GenericStack.CODEC.listOf().listOf().fieldOf("parameters").forGetter(Saved::parameters),
                Codec.STRING.fieldOf("continuation").forGetter(Saved::continuation),
                Codec.STRING.listOf().optionalFieldOf("admissions", List.of()).forGetter(Saved::admissions),
                FactoryPatternData.LARGE_TEXT.listOf().optionalFieldOf("nativeRecovery",List.of()).forGetter(Saved::nativeRecovery)
        ).apply(i, Saved::new));
    }
    private final List<String> nativeRecovery=new ArrayList<>();
    private appeng.api.stacks.AEKey primary;
    private final List<List<GenericStack>> parameters;
    private final FactoryBlockEntity host;
    private final FactoryPatternData data;
    private final FactoryMachine machine;
    private final FactoryBuffer input;
    private final FactoryRoutes routes;
    private final FactoryBuffer output;
    private final KeyCounter expected = new KeyCounter();
    public FactoryJob(FactoryBlockEntity host, FactoryPatternDetails pattern, KeyCounter[] inputs) {
        this.host = host; this.data = pattern.data();
        primary = pattern.getPrimaryOutput().what();
                parameters = new ArrayList<>();
        for (var counter : inputs) {
            var values = new ArrayList<GenericStack>();
            for (var entry : counter) if (entry.getLongValue() > 0) values.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            parameters.add(List.copyOf(values));
        }
        machine = new FactoryMachine(FactoryCompiler.compile(data.code(), true, parameters.size(),pattern.getOutputs().size()));
        input = new FactoryBuffer(); routes = new FactoryRoutes(); output = new FactoryBuffer();
        for (var counter : inputs) for (var entry : counter) {
            if (entry.getLongValue() > 0 && input.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, action()) != entry.getLongValue())
                throw new IllegalArgumentException("Factory source capacity exceeded");
        }
        for (var stack : pattern.getOutputs()) expected.add(stack.what(), stack.amount());
    }
    public FactoryJob(FactoryBlockEntity host, FactoryPatternData script) {
        if (script.hasRecipe()) throw new IllegalArgumentException("Standalone factory requires a recipe-free pattern");
        this.host = host; data = script; parameters = List.of(); primary = null;
        machine = new FactoryMachine(FactoryCompiler.compile(data.code(), false, 0));
        input = new FactoryBuffer(); routes = new FactoryRoutes(); output = new FactoryBuffer();
    }
    private FactoryJob(FactoryBlockEntity host, Saved saved) {
        nativeRecovery.addAll(saved.nativeRecovery());
        this.host = host; data = saved.data(); parameters = saved.parameters().stream().map(List::copyOf).toList();
        // Block entity NBT loads before its level is attached. Resolve the saved recipe lazily.
        machine = FactoryMachine.restore(FactoryCompiler.compile(data.code(), data.hasRecipe(), parameters.size()), new com.google.gson.Gson().fromJson(saved.continuation(), FactoryMachine.Snapshot.class));
        input = FactoryBuffer.restore(saved.input()); routes = FactoryRoutes.restore(new com.google.gson.Gson().fromJson(saved.routes(), new com.google.gson.reflect.TypeToken<List<FactoryRoutes.Source>>() {}.getType())); output = FactoryBuffer.restore(saved.output());
        for (var stack : saved.expected()) expected.add(stack.what(), stack.amount());
    }
    public static FactoryJob restore(FactoryBlockEntity host, Saved saved) { return new FactoryJob(host, saved); }
    private java.util.Set<appeng.api.stacks.AEKey> parameterKeys(int index) {
        if(index<0) {
            var recipe=appeng.api.crafting.PatternDetailsHelper.decodePattern(data.recipe(),host.getLevel());
            if(recipe==null||-index>recipe.getOutputs().size())throw new IllegalArgumentException("Invalid recipe output O"+(-index));
            return java.util.Set.of(recipe.getOutputs().get(-index-1).what());
        }
        if (index < 1 || index > parameters.size()) throw new IllegalArgumentException("Invalid recipe material P" + index);
        return parameters.get(index - 1).stream().map(GenericStack::what).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private java.util.Set<appeng.api.stacks.AEKey> recipeInputs,recipeOutputs;
    /**
     * Complete expected sets of the bound recipe: {@code P} unions every material of every input slot and
     * {@code O} unions every declared output product, resolved through the same recipe the pattern carries.
     */
    private java.util.Set<appeng.api.stacks.AEKey> recipeSet(int kind) {
        if(!data.hasRecipe())throw new IllegalArgumentException("P and O require a recipe-bound pattern");
        if(kind==FactorySelector.ALL_INPUTS) {
            if(recipeInputs==null) {
                var keys=new java.util.LinkedHashSet<appeng.api.stacks.AEKey>();
                for(var material:parameters)for(var stack:material)keys.add(stack.what());
                recipeInputs=java.util.Set.copyOf(keys);
            }
            return recipeInputs;
        }
        if(recipeOutputs==null) {
            var recipe=appeng.api.crafting.PatternDetailsHelper.decodePattern(data.recipe(),host.getLevel());
            if(recipe==null)throw new IllegalArgumentException("Invalid saved recipe");
            var keys=new java.util.LinkedHashSet<appeng.api.stacks.AEKey>();
            for(var stack:recipe.getOutputs())keys.add(stack.what());
            recipeOutputs=java.util.Set.copyOf(keys);
        }
        return recipeOutputs;
    }
    private final FactorySelector.RecipeSet recipeSets=(kind,keyType,id)->{
        for(var key:recipeSet(kind))if(FactoryResourceSelector.matchesKey(key,keyType,id))return true;
        return false;
    };
    private IActionSource action() { return IActionSource.ofMachine(host); }
    public Saved save() {
        var remaining = new ArrayList<GenericStack>();
        for (var entry : expected) if (entry.getLongValue() > 0) remaining.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        return new Saved(data, input.snapshot(), new com.google.gson.Gson().toJson(routes.snapshot()), output.snapshot(), remaining, parameters,
                new com.google.gson.Gson().toJson(machine.snapshot()), List.of(),List.copyOf(nativeRecovery));
    }
    public void tick() {
        if (host.factoryGrid() == null || !host.isolated() || FactoryServer.owner(host.factoryGrid()) != host) return;
        machine.tick(this);
        var network = host.getMainNode().getGrid().getStorageService().getInventory();
        for (var stack : output.snapshot()) {
            long sent = network.insert(stack.what(), stack.amount(), Actionable.MODULATE, action());
            output.extract(stack.what(), sent, Actionable.MODULATE, action());
            expected.set(stack.what(), Math.max(0, expected.get(stack.what()) - sent));
            if (sent > 0) host.getLogic().returned(new GenericStack(stack.what(), sent));
        }
        expected.removeZeros();
    }
    /** Only this job's successful source return repays its primary output debt. */
    public boolean awaitingPrimaryReturn() {
        if (!data.hasRecipe()) return false;
        if (primary == null) {
            if (host.getLevel() == null) return true;
            var pattern = appeng.api.crafting.PatternDetailsHelper.decodePattern(data.recipe(), host.getLevel());
            if (pattern == null) return true; // Invalid saved recipes must never release an unpaid gate.
            primary = pattern.getPrimaryOutput().what();
        }
        return expected.get(primary) > 0;
    }
    public boolean finished() { return nativeRecovery.isEmpty() && machine.stopped() && machine.error().isEmpty() && input.isEmpty() && output.isEmpty() && expected.isEmpty(); }
    public String error() { return machine.error(); }
    public String waitingStatus(){return machine.waitingReason();}
    public void addDrops(java.util.List<net.minecraft.world.item.ItemStack> drops) {
        for(var stack:input.snapshot())stack.what().addDrops(stack.amount(),drops,host.getLevel(),host.getBlockPos());
        for(var stack:output.snapshot())stack.what().addDrops(stack.amount(),drops,host.getLevel(),host.getBlockPos());
    }
    public boolean scheduled() { return SfmSyntax.recognizes(data.code()); }
    public boolean hasBufferedResources() { return !nativeRecovery.isEmpty() || !input.isEmpty() || !output.isEmpty(); }
    @Override public void forget(String tag) { routes.forget(tag); }
    @Override public long gameTime() { return host.getLevel().getGameTime(); }
    @Override public boolean signal() { return FactoryServer.signal(host); }
    @Override public boolean recipeComplete() { return data.hasRecipe() && expected.isEmpty() && input.isEmpty() && output.isEmpty(); }
    @Override public long count(String tag, String resource) {
        if (tag.equals(FactoryExpression.ALL_TAGS)) {
            // A bare "has resource" sums every machine tag bound to this program.
            long total = 0;
            for (String name : host.tags.names()) {
                if (!machineTag(name)) continue;
                total = Math.addExact(total, count(name, resource));
            }
            return total;
        }
        if(machineTag(tag))return FactoryNativeTransfers.count((ServerLevel)host.getLevel(),machinePositions(tag),nativeFilter(resource));
        var filter = FactoryResourceSelector.parse(resource, this::parameterKeys, recipeSets);
        long result = 0;
        for (var storage : targets(tag, "", true)) for (var stack : storage.storage().getAvailableStacks()) {
            if (filter.test(stack.getKey())) result = Math.addExact(result, stack.getLongValue());
        }
        return result;
    }
    @Override public void declare(String resource,String tag,String face,long limit,boolean must){routes.declare(resource,tag,face,limit,must);}
    @Override public void declare(String resource,String tag,String face,long limit,boolean must,int channel){routes.declare(resource,tag,face,limit,must,channel);}
    @Override public long requiredTransfer(String resource,String tag,String face,long limit) {
        return requiredTransfer(resource,tag,face,limit,0);
    }
    @Override public long requiredTransfer(String resource,String tag,String face,long limit,int channel) {
        long required=0;
        for(var source:routes.snapshot())if(source.channel()==channel&&source.must()&&selectorsOverlap(source.selector(),resource)) {
            required=required>Long.MAX_VALUE-source.remaining()?Long.MAX_VALUE:required+source.remaining();
        }
        return Math.min(limit,required);
    }
    private boolean selectorsOverlap(String a,String b) {
        var first=FactorySelector.parse(a);var second=FactorySelector.parse(b);
        var parametersToCheck=new java.util.LinkedHashSet<Integer>(first.parameters());parametersToCheck.addAll(second.parameters());
        if(!parametersToCheck.isEmpty()) {
            var filter=FactoryResourceSelector.parse(a,this::parameterKeys,recipeSets).and(FactoryResourceSelector.parse(b,this::parameterKeys,recipeSets));
            for(int parameter:parametersToCheck)if(parameterKeys(parameter).stream().anyMatch(filter))return true;
            return false;
        }
        return first.mayOverlap(second);
    }
    @Override public long transfer(boolean get, String resource, String tag, String face, long limit) {
        return transfer(get, resource, tag, face, limit, 0);
    }
    @Override public long transfer(boolean get, String resource, String tag, String face, long limit, int channel) {
        if (get) {
            routes.declare(resource, tag, face, limit, false, channel);
            return 0;
        }
        boolean mustOnly=routes.snapshot().stream().anyMatch(s->s.channel()==channel&&s.must()&&s.remaining()>0&&selectorsOverlap(s.selector(),resource));
        long committed = routes.output(resource, tag, face, limit,s->s.channel()==channel&&(!mustOnly||s.must()), (source, destinationFilter, destinationTag, destinationFace, allowed) -> {
            if(machineTag(source.tag())&&machineTag(destinationTag))return FactoryNativeTransfers.move((ServerLevel)host.getLevel(),expressionPositions(source.tag()),nativeFace(source.face()),expressionPositions(destinationTag),nativeFace(destinationFace),nativeFilter(source.selector()).and(nativeFilter(destinationFilter)),allowed,nativeRecovery::add);
            var sourceFilter = FactoryResourceSelector.parse(source.selector(), this::parameterKeys, recipeSets);
            var outputFilter = FactoryResourceSelector.parse(destinationFilter, this::parameterKeys, recipeSets);
            long moved = 0;
            // Resolve both lists before mutation. Unloaded or invalid targets cannot cause partial declaration execution.
            var origins = targets(source.tag(), source.face(), true).stream().map(Target::snapshot).toList();
            var destinations = targets(destinationTag, destinationFace, false);
            for (var origin : origins) for (var destination : destinations) {
                if (origin.sameEndpoint(destination) || moved >= allowed) continue;
                moved += FactoryTransfers.move(origin.storage(), destination.storage(), sourceFilter.and(outputFilter), allowed - moved, action(), stack -> { if (input.insert(stack.what(), stack.amount(), Actionable.MODULATE, action()) != stack.amount()) throw new IllegalStateException("Provider recovery source is full"); });
            }
            return moved;
        });
        return committed;
    }
    private static boolean machineTag(String tag){return !tag.equals("source")&&!tag.equals("storage");}
    private static Direction nativeFace(String face){return face.isEmpty()?null:Direction.valueOf(face.toUpperCase(java.util.Locale.ROOT));}
    private List<BlockPos> machinePositions(String tag){return FactoryServer.members(host.factoryGrid(),host.tags.positions(tag));}
    /** {@code A&B} resolves to the union of the listed machine tags, without repeating a machine. */
    private List<BlockPos> expressionPositions(String tag) {
        if (!FactoryTags.compound(tag)) return machinePositions(tag);
        var union = new java.util.LinkedHashSet<BlockPos>();
        for (String part : FactoryTags.expression(tag)) union.addAll(machinePositions(part));
        return List.copyOf(union);
    }
    private java.util.function.Predicate<FactoryNativeTransfers.Resource> nativeFilter(String text){var selector=FactorySelector.parse(text);return resource->selector.matches(resource.type(),resource.id(),i->parameterKeys(i).stream().anyMatch(resource::matchesParameter),FactoryResourceSelector.RESOURCE_TAGS,recipeSets);}
    private record Target(MEStorage storage, BlockPos position, String face,MEStorage physical) {
        Target(MEStorage storage,BlockPos position,String face){this(storage,position,face,storage);}
        Target snapshot(){return new Target(new FactorySourceView(storage),position,face,physical);}
        boolean sameEndpoint(Target other) {
            return position==null ? other.position==null&&physical==other.physical : position.equals(other.position)&&face.equals(other.face);
        }
    }
    private List<Target> targets(String tag, String face, boolean get) {
        if (tag.equals("source")) { if (!host.isProvider()) throw new IllegalArgumentException("source is only available to provider tasks"); return get ? List.of(new Target(input,null,""),new Target(host.getLogic().induction(),null,"")) : List.of(new Target(output,null,"")); }
        if (tag.equals("storage")) return List.of(new Target(host.factoryGrid().getStorageService().getInventory(),null,""));
        var level = (ServerLevel) host.getLevel();
        Direction side = face.isEmpty() ? null : Direction.valueOf(face.toUpperCase(java.util.Locale.ROOT));
        var targets = new ArrayList<Target>();
        for (BlockPos pos : machinePositions(tag)) {
            for(var storage:FactoryNativeTransfers.storageBridges(level,pos,side))targets.add(new Target(storage,pos,face));
        }
        return targets;
    }
    @Override public void pulse(String tag, long ticks) { host.pulse(tag, ticks); }
}
