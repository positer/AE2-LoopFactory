package com.example.ae2lightoptimizer.factory;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.locator.MenuHostLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Physically independent main/subnet nodes; a provider only forwards AE energy between them. */
public final class FactoryBlockEntity extends AENetworkedBlockEntity implements PatternProviderLogicHost, FactoryEditorHost {
    private final IManagedGridNode subnet;
    private final FactoryProviderLogic logic;
    private String factoryId = UUID.randomUUID().toString();
    private String factoryName = "";
    public FactoryTags tags = new FactoryTags();
    public String draft = "";
    private final java.util.Map<Long, Long> pulses = new java.util.HashMap<>();
    private final java.util.List<FactoryJob> terminalJobs = new java.util.ArrayList<>();
    private boolean lastRedstone;
    private boolean loadingPatterns;
    private FactoryPatternData installedTerminalProgram = FactoryPatternData.EMPTY;
    private boolean terminalRestartPending;
    private boolean recoveryDropHandled;
    private final FactoryRecovery recovery=new FactoryRecovery();
    private String executionError = "";
    public String executionError() { return executionError; }
    public int activeJobCount() { return terminalJobs.size(); }
    public String waitingStatus(){return !recovery.isEmpty()?recovery.waitingStatus():terminalJobs.stream().map(FactoryJob::waitingStatus).filter(s->!s.isEmpty()).findFirst().orElse("");}
    public String recoveryWaitingStatus(){return recovery.waitingStatus();}
    public boolean powers(BlockPos pos) {
        return getLevel() != null && pulses.getOrDefault(pos.asLong(), Long.MIN_VALUE) > getLevel().getGameTime();
    }
    public void pulse(String tag, long ticks) {
        if (ticks < 1) throw new IllegalArgumentException("Pulse duration must be positive");
        var union = new java.util.LinkedHashSet<Long>();
        for (String part : FactoryTags.expression(tag)) union.addAll(tags.positions(part));
        var targets = union.stream().map(BlockPos::of).toList();
        for (var target : targets) if (!getLevel().hasChunkAt(target))
            throw new FactoryMachine.Pause("Redstone target chunk is unloaded");
        long deadline = Math.addExact(getLevel().getGameTime(), ticks);
        for (var target : targets) if (FactoryServer.contains(factoryGrid(), target)) {
            pulses.merge(target.asLong(), deadline, Math::max);
            notifyPulse(target);
        }
        saveChanges();
    }
    private void notifyPulse(BlockPos target) {
        if (getLevel() != null && getLevel().hasChunkAt(target))
            getLevel().neighborChanged(target, getBlockState().getBlock(), null);
    }
    private void tickPulses() {
        var expired = pulses.entrySet().stream().filter(e -> e.getValue() <= getLevel().getGameTime())
                .map(java.util.Map.Entry::getKey).toList();
        for (long target : expired) { pulses.remove(target); notifyPulse(BlockPos.of(target)); }
        if (!expired.isEmpty()) saveChanges();
    }
    /** Cancel continuations, but retain resources already owned by the old runs. */
    private void refreshTerminalExecution() {
        recovery.add(FactoryRecovery.collect(FactoryRecovery.Cargo.EMPTY,
                terminalJobs.stream().map(FactoryJob::save).toList(), java.util.List.of()));
        terminalJobs.clear();
        executionError = "";
        terminalRestartPending = true;
        var cancelledPulses = java.util.List.copyOf(pulses.keySet());
        pulses.clear();
        for (long target : cancelledPulses) notifyPulse(BlockPos.of(target));
        saveChanges();
    }
    private void tickTerminal() {
        if (kind() != FactoryBlock.Kind.TERMINAL) return;
        boolean powered = FactoryServer.signal(this);
        var stack = editorPatterns.getStackInSlot(0);
        var installed = FactoryPatternData.get(stack);
        boolean executable = stack.getItem() instanceof FactoryPatternItem && !installed.hasRecipe()
                && installed.factoryId().equals(factoryId) && !installed.code().isBlank();
        boolean scheduled = executable && SfmSyntax.recognizes(installed.code());
        boolean owner = FactoryServer.owner(factoryGrid()) == this;
        // A held signal must allow WAIT/MUST and scheduled programs to make progress.
        if (owner && powered && !lastRedstone) refreshTerminalExecution();
        if (owner && (terminalRestartPending
                || (scheduled && terminalJobs.isEmpty() && executionError.isEmpty()))) {
            terminalRestartPending = false;
            if (executable) {
                try { terminalJobs.add(new FactoryJob(this, installed)); }
                catch (IllegalArgumentException failure) { executionError = failure.getMessage(); }
            }
            saveChanges();
        }
        if (owner && lastRedstone != powered) { lastRedstone = powered; saveChanges(); }
        if (!terminalJobs.isEmpty()) {
            for (var job : java.util.List.copyOf(terminalJobs)) {
                job.tick();
                if (!job.error().isEmpty()) executionError = job.error();
            }
            terminalJobs.removeIf(job -> job.finished() || (!job.error().isEmpty() && !job.hasBufferedResources()));
            saveChanges();
        }
    }
    private final appeng.util.inv.AppEngInternalInventory editorPatterns = new appeng.util.inv.AppEngInternalInventory(1) {
        @Override protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            if(!loadingPatterns){
                // A pattern that already carries code replaces the editor; a blank pattern changes nothing.
                var data = FactoryPatternData.get(getStackInSlot(0));
                if(!data.code().isEmpty() && !data.code().equals(draft)) setFactoryDraft(data.code());
                if (kind() == FactoryBlock.Kind.TERMINAL && !data.equals(installedTerminalProgram)) {
                    installedTerminalProgram = data;
                    refreshTerminalExecution();
                }
            }
            saveChanges();
        }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof FactoryPatternItem && !FactoryPatternData.get(stack).hasRecipe();
        }
    };
    @Override public appeng.api.inventories.InternalInventory factoryPatterns() { return editorPatterns; }
    @Override public String factoryDraft() { return draft; }
    @Override public void setFactoryDraft(String value) { draft = value; saveChanges(); }
    @Override public IGrid editorGrid() { return factoryGrid(); }
    // The terminal runs its own recipe-free program, so saving code here must bind to the terminal
    // itself and never depend on the transient network-ownership election finishing first.
    @Override public FactoryBlockEntity editingFactory() {
        return kind() == FactoryBlock.Kind.TERMINAL ? this : FactoryServer.owner(factoryGrid());
    }
    @Override public boolean allowsRecipe() { return false; }
    public FactoryBlockEntity(BlockPos pos, BlockState state) {
        super(FactoryContent.ENTITY.get(), pos, state);
        // Factory blocks are a control network, not an AE energy consumer. A cable + terminal must be active without a power source.
        getMainNode().setFlags(new appeng.api.networking.GridFlags[0]);
        getMainNode().setIdlePowerUsage(0);
        subnet = GridHelper.createManagedNode(this, new IGridNodeListener<FactoryBlockEntity>() {
            @Override public void onSaveChanges(FactoryBlockEntity owner, IGridNode node) { owner.saveChanges(); }
        }).setInWorldNode(true).setTagName("factorySubnet").setVisualRepresentation(state.getBlock());
        logic = isProvider() ? new FactoryProviderLogic(this) : null;
    }
    public FactoryBlock.Kind kind() { return ((FactoryBlock) getBlockState().getBlock()).kind(); }
    public boolean isProvider() { return kind() == FactoryBlock.Kind.PROVIDER; }
    public String factoryId() { return factoryId; }
    public String factoryName() { return factoryName.isEmpty() ? factoryId : factoryName; }
    public void setFactoryName(String name) { if(!name.isEmpty() && !name.equals(factoryName)){factoryName=name;saveChanges();} }
    public IGrid factoryGrid() { return isProvider() ? subnet.getGrid() : getMainNode().getGrid(); }
    public boolean isolated() { return !isProvider() || factoryGrid() != getMainNode().getGrid(); }
    @Override public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (UniqueNetworkServices.disconnected(this)) return Set.of();
        var sides = EnumSet.allOf(Direction.class);
        if (isProvider()) sides.remove(getFront());
        return sides;
    }
    @Override public IGridNode getGridNode(Direction side) {
        return isProvider() && side == getFront() ? subnet.getNode() : getMainNode().getNode();
    }
    @Override public void onReady() {
        super.onReady();
        if (isProvider()) { subnet.setExposedOnSides(EnumSet.of(getFront())); subnet.create(getLevel(), getBlockPos()); logic.updatePatterns(); }
        FactoryServer.add(this);
        if(kind()!=FactoryBlock.Kind.CABLE)UniqueNetworkServices.add(this,isProvider()?subnet:getMainNode(),UniqueNetworkServices.Kind.FACTORY,isProvider());
    }
    @Override protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        if (subnet != null && isProvider()) subnet.setExposedOnSides(EnumSet.of(getFront()));
    }
    @Override public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (logic != null) logic.onMainNodeStateChanged();
        markForUpdate();
    }
    @Override public void setRemoved() { subnet.destroy(); FactoryServer.remove(this); UniqueNetworkServices.remove(this); super.setRemoved(); }
    @Override public void onChunkUnloaded() { subnet.destroy(); FactoryServer.remove(this); UniqueNetworkServices.remove(this); super.onChunkUnloaded(); }
    @Override public void addAdditionalDrops(net.minecraft.world.level.Level level,BlockPos pos,java.util.List<ItemStack> drops) {
        if(recoveryDropHandled)return;
        if(retainsRecoveryDrop()) {
            // Capture before clearing; repeated removal/wrench callbacks cannot release the same buffers twice.
            var carrier=FactoryRecovery.capture(this,physicalRecoveryCargo());
            // Native provider configuration and return inventory still drop as their original items.
            var configuration=new java.util.ArrayList<ItemStack>();
            if(logic!=null)logic.addDrops(configuration);
            for(var stack:editorPatterns)if(!stack.isEmpty())configuration.add(stack.copy());
            drops.add(carrier);drops.addAll(configuration);recoveryDropHandled=true;clearContent();return;
        }
        super.addAdditionalDrops(level,pos,drops);
        if(logic!=null)logic.addDrops(drops);
        for(var stack:editorPatterns)if(!stack.isEmpty())drops.add(stack.copy());
        for(var job:terminalJobs)job.addDrops(drops);
    }
    boolean retainsRecoveryDrop() {
        return recoveryDropHandled||!physicalRecoveryCargo().isEmpty();
    }
    private FactoryRecovery.Cargo physicalRecoveryCargo() {
        var jobs=new java.util.ArrayList<FactoryJob.Saved>();
        for(var job:terminalJobs)jobs.add(job.save());
        if(logic!=null)jobs.addAll(logic.saveJobs());
        return FactoryRecovery.collect(recovery.snapshot(),jobs,logic==null?java.util.List.of():logic.induction().snapshot());
    }
    @Override public void importSettings(appeng.util.SettingsFrom source,net.minecraft.core.component.DataComponentMap settings,Player player) {
        // Strip cargo before generic AE settings import and from vanilla-applied item components.
        super.importSettings(source,FactoryRecovery.withoutCargo(settings),player);
        setComponents(FactoryRecovery.withoutCargo(components()));
        if(source==appeng.util.SettingsFrom.DISMANTLE_ITEM) {
            recovery.add(FactoryRecovery.decode(this,settings));saveChanges();
        }
    }
    @Override public void clearContent() {
        super.clearContent();if(logic!=null)logic.clearContent();editorPatterns.clear();terminalJobs.clear();recovery.clear();
        terminalRestartPending=false;installedTerminalProgram=FactoryPatternData.EMPTY;
    }
    public void tickFactory() {
        recovery.tick(this);
        tickPulses();
        if (isProvider() && isolated() && getMainNode().getGrid() != null && factoryGrid() != null) {
            var from = getMainNode().getGrid().getEnergyService();
            var to = factoryGrid().getEnergyService();
            double demand = to.getEnergyDemand(1024);
            double available = from.extractAEPower(demand, Actionable.SIMULATE, PowerMultiplier.ONE);
            if (available > 0) {
                double extracted = from.extractAEPower(available, Actionable.MODULATE, PowerMultiplier.ONE);
                double excess = to.injectPower(extracted, Actionable.MODULATE);
                if (excess > 0) from.injectPower(excess, Actionable.MODULATE);
            }
        }
        if (logic != null) logic.tick();
        tickTerminal();
    }
    @Override public FactoryProviderLogic getLogic() { return logic; }
    @Override public EnumSet<Direction> getTargets() { return EnumSet.of(getFront()); }
    @Override public AEItemKey getTerminalIcon() { return AEItemKey.of(getBlockState().getBlock()); }
    @Override public ItemStack getMainMenuIcon() { return getBlockState().getBlock().asItem().getDefaultInstance(); }
    @Override public void openMenu(Player player, MenuHostLocator locator) {
        if (isProvider()) PatternProviderLogicHost.super.openMenu(player, locator);
        else appeng.menu.MenuOpener.open(FactoryEditorMenu.TYPE.get(), player, locator);
    }
    @Override public void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.store("FactoryResourceReturns",FactoryRecovery.Cargo.CODEC,recovery.snapshot());
        tag.putString("FactoryId", factoryId);
        tag.putString("FactoryName", factoryName);
        tag.putBoolean("FactoryLastRedstone", lastRedstone);
        tag.putBoolean("FactoryTerminalRestartPending", terminalRestartPending);
        tag.putString("FactoryExecutionError", executionError);
        tag.store("FactoryPulses",FactoryPatternData.LARGE_TEXT,new com.google.gson.Gson().toJson(pulses));
        tag.store("FactoryTerminalJobs", FactoryJob.Saved.CODEC.listOf(), terminalJobs.stream().map(FactoryJob::save).toList());
        editorPatterns.writeToNBT(tag, "FactoryEditorPattern");
        tag.store("FactoryDraft",FactoryPatternData.TEXT,draft);
        tag.store("FactoryTags",FactoryPatternData.LARGE_TEXT,new com.google.gson.Gson().toJson(tags.snapshot()));
        if (isProvider()) { subnet.serialize(tag); logic.writeToNBT(tag); }
    }
    @Override public void loadTag(ValueInput tag) {
        super.loadTag(tag);
        recovery.clear();recovery.add(tag.read("FactoryResourceReturns",FactoryRecovery.Cargo.CODEC).orElse(FactoryRecovery.Cargo.EMPTY));
        lastRedstone = tag.getBooleanOr("FactoryLastRedstone", false);
        terminalRestartPending = tag.getBooleanOr("FactoryTerminalRestartPending", false);
        executionError = tag.getStringOr("FactoryExecutionError", "");
        pulses.clear();
        String pulseData = tag.read("FactoryPulses",FactoryPatternData.LARGE_TEXT).orElse("");
        if (!pulseData.isBlank()) pulses.putAll(new com.google.gson.Gson().fromJson(pulseData,
                new com.google.gson.reflect.TypeToken<java.util.Map<Long, Long>>() {}.getType()));
        terminalJobs.clear();
        for (var saved : tag.read("FactoryTerminalJobs", FactoryJob.Saved.CODEC.listOf()).orElse(java.util.List.of()))
            terminalJobs.add(FactoryJob.restore(this, saved));
        loadingPatterns=true;
        try {editorPatterns.readFromNBT(tag, "FactoryEditorPattern");} finally {loadingPatterns=false;}
        installedTerminalProgram = FactoryPatternData.get(editorPatterns.getStackInSlot(0));
        factoryName = tag.getStringOr("FactoryName", "");
        if (tag.getString("FactoryId").isPresent()) factoryId = tag.getString("FactoryId").orElse("" );
        draft = tag.read("FactoryDraft",FactoryPatternData.TEXT).orElse("");
        if (tag.read("FactoryTags",FactoryPatternData.LARGE_TEXT).isPresent()) tags = FactoryTags.restore(new com.google.gson.Gson().fromJson(tag.read("FactoryTags",FactoryPatternData.LARGE_TEXT).orElse(""),
                new com.google.gson.reflect.TypeToken<java.util.Map<String, java.util.List<Long>>>() {}.getType()));
        if (isProvider()) { subnet.deserialize(tag); logic.readFromNBT(tag); }
    }
}
