package com.example.ae2lightoptimizer.factory;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.example.ae2lightoptimizer.mixin.PatternProviderLogicInvoker;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public final class FactoryProviderLogic extends PatternProviderLogic {
    private final FactoryBlockEntity host;
    private FactoryBuffer induction=new FactoryBuffer();
    public FactoryBuffer induction(){return induction;}
    private final InternalInventory slots;
    private final List<FactoryJob> jobs = new ArrayList<>();
    public FactoryProviderLogic(FactoryBlockEntity host) {
        super(host.getMainNode(), host);
        this.host = host;
        var delegate = super.getPatternInv();
        slots = new appeng.api.inventories.BaseInternalInventory() {
            @Override public int size() { return delegate.size(); }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public ItemStack getStackInSlot(int slot) { return delegate.getStackInSlot(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return accepts(stack); }
            @Override public void setItemDirect(int slot, ItemStack stack) { delegate.setItemDirect(slot, stack); }
        };
    }
    public boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof FactoryPatternItem && FactoryPatternData.get(stack).factoryId().equals(host.factoryId())
                && FactoryPatternData.get(stack).hasRecipe();
    }
    @Override public InternalInventory getPatternInv() { return slots; }
    @Override public List<IPatternDetails> getAvailablePatterns() {
        if (host == null || host.getLevel() == null || !host.isolated()) return List.of();
        var result = new ArrayList<IPatternDetails>();
        for (var stack : slots) if (accepts(stack)) {
            try { var pattern = PatternDetailsHelper.decodePattern(stack, host.getLevel()); if (pattern != null) result.add(pattern); }
            catch (IllegalArgumentException ignored) { /* Invalid imported code must never enter the crafting service. */ }
        }
        return List.copyOf(result);
    }
    @Override public void updatePatterns() { if (host != null) ICraftingProvider.requestUpdate(host.getMainNode()); }
    @Override public boolean isBusy() {
        return jobs.size() >= 16 || (isBlocking() && jobs.stream().anyMatch(job -> !job.finished()))
                || getCraftingLockedReason() != appeng.api.config.LockCraftingMode.NONE;
    }
    @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        if (!(pattern instanceof FactoryPatternDetails factory) || isBusy()
                || !host.isolated() || FactoryServer.owner(host.factoryGrid()) != host
                || !factory.data().factoryId().equals(host.factoryId()) || !getAvailablePatterns().contains(pattern)) return false;
        final FactoryJob job;
        try { job = new FactoryJob(host, factory, inputs); } catch (IllegalArgumentException e) { return false; }
        jobs.add(job);
        ((PatternProviderLogicInvoker) (Object) this).ae2lightoptimizer$onPushPatternSuccess(pattern);
        saveChanges();
        return true;
    }
    @Override public void addDrops(java.util.List<ItemStack> drops) {super.addDrops(drops);}
    @Override public void clearContent() {super.clearContent();jobs.clear();induction=new FactoryBuffer();}
    public void returned(GenericStack stack) { ((PatternProviderLogicInvoker) (Object) this).ae2lightoptimizer$onStackReturnedToNetwork(stack); }
    public String waitingStatus(){return !host.recoveryWaitingStatus().isEmpty()?host.recoveryWaitingStatus():jobs.stream().map(FactoryJob::waitingStatus).filter(s->!s.isEmpty()).findFirst().orElse("");}
    public String executionError(){return jobs.stream().map(FactoryJob::error).filter(error->!error.isEmpty()).findFirst().orElse("");}
    public List<FactoryJob.Saved> saveJobs() { return jobs.stream().map(FactoryJob::save).toList(); }
    public void tick() {
        FactoryInduction.tick(host,induction);
        if (jobs.isEmpty()) return;
        // Each admitted task owns a continuation; blocking admits no new task until complete cleanup.
        for (var job : List.copyOf(jobs)) job.tick();
        jobs.removeIf(FactoryJob::finished);
        saveChanges();
    }
    @Override public void writeToNBT(net.minecraft.world.level.storage.ValueOutput tag) {
        super.writeToNBT(tag);
        tag.store("FactoryInduction",GenericStack.CODEC.listOf(),induction.snapshot());
        tag.store("FactoryJobs", FactoryJob.Saved.CODEC.listOf(), jobs.stream().map(FactoryJob::save).toList());
    }
    @Override public void readFromNBT(net.minecraft.world.level.storage.ValueInput tag) {
        super.readFromNBT(tag);
        induction=FactoryBuffer.restore(tag.read("FactoryInduction",GenericStack.CODEC.listOf()).orElse(List.of()));
        jobs.clear();
        for (var saved : tag.read("FactoryJobs", FactoryJob.Saved.CODEC.listOf()).orElse(List.of())) jobs.add(FactoryJob.restore(host, saved));
    }
}
