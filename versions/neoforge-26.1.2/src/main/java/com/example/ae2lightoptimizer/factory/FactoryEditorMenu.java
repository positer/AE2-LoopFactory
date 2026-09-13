package com.example.ae2lightoptimizer.factory;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FactoryEditorMenu extends AEBaseMenu {
    public static final SlotSemantic PATTERN = appeng.menu.SlotSemantics.register("AE2LF_FACTORY_PATTERN", false, 1);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Ae2LightOptimizer.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<FactoryEditorMenu>> TYPE = MENUS.register("factory_editor",
            () -> MenuTypeBuilder.create(FactoryEditorMenu::new, FactoryEditorHost.class)
                    .buildUnregistered(Identifier.fromNamespaceAndPath(Ae2LightOptimizer.MOD_ID, "factory_editor")));
    private static final appeng.menu.guisync.ClientActionKey<CodeChunk> CHUNK = new appeng.menu.guisync.ClientActionKey<>("factoryCodeChunk");
    private static final appeng.menu.guisync.ClientActionKey<Void> RECIPE = new appeng.menu.guisync.ClientActionKey<>("factoryRecipePage");
    private static final appeng.menu.guisync.ClientActionKey<String> UPLOAD = new appeng.menu.guisync.ClientActionKey<>("factoryUpload");
    private static final appeng.menu.guisync.ClientActionKey<Void> NEXT_PROVIDERS=new appeng.menu.guisync.ClientActionKey<>("factoryNextProviders");
    private final FactoryEditorHost host;
    public String code = "";
    @GuiSync(20) public FactoryCodeText syncedCode=new FactoryCodeText("");
    public String code(){return isClientSide()?syncedCode.value():code;}
    public record CodeChunk(int index,int total,String text) {}
    private final StringBuilder incomingCode=new StringBuilder();
    private int incomingIndex,incomingTotal;
    @GuiSync(21) public String status = "";
    @GuiSync(24) public String providers = "";
    @GuiSync(25) public int providerPage;
    @GuiSync(26) public int providerPages=1;
    @GuiSync(27) public String runtimeError="";
    @GuiSync(28) public String runtimeWait="";
    public String displayStatus(){return status.isEmpty()||status.equals("Saved")?(!runtimeError.isEmpty()?runtimeError:!runtimeWait.isEmpty()?runtimeWait:status):status;}
    @GuiSync(23) public String titleKey = "block.ae2lightoptimizer.loop_factory_network_terminal";
    @GuiSync(22) public String factoryName = "";
    public FactoryEditorMenu(MenuType<?> type, int id, Inventory inventory, FactoryEditorHost host) {
        super(type, id, inventory, host);
        this.host = host;
        addSlot(new AppEngSlot(host.factoryPatterns(), 0) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FactoryPatternItem && (host.allowsRecipe() || !FactoryPatternData.get(stack).hasRecipe());
            }
            @Override public int getMaxStackSize() { return 1; }
            @Override public void setChanged() {
                super.setChanged();
                // Only an actual slot change loads code; never overwrite what the player is typing.
                if (isServerSide()) {
                    var data = FactoryPatternData.get(host.factoryPatterns().getStackInSlot(0));
                    if (!data.code().isEmpty() && !data.code().equals(code)) {
                        host.setFactoryDraft(data.code());
                        code = data.code();
                        broadcastChanges();
                    }
                }
            }
        }, PATTERN);
        createPlayerInventorySlots(inventory);
        registerClientAction(RECIPE, this::recipePage);
        registerClientAction(NEXT_PROVIDERS,this::nextProviders);
        registerClientAction(UPLOAD, net.minecraft.network.codec.ByteBufCodecs.stringUtf8(128), this::upload);
        registerClientAction(CHUNK,net.minecraft.network.codec.StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.VAR_INT,CodeChunk::index,net.minecraft.network.codec.ByteBufCodecs.VAR_INT,CodeChunk::total,net.minecraft.network.codec.ByteBufCodecs.stringUtf8(4096),CodeChunk::text,CodeChunk::new),this::acceptCodeChunk);
        if (isServerSide()) code = host.factoryDraft();
    }
    public void nextProviders() {
        if(isClientSide()){sendClientAction(NEXT_PROVIDERS);return;}
        if(stillValid(getPlayer())&&isPanel()){providerPage=(providerPage+1)%Math.max(1,providerPages);broadcastChanges();}
    }
    public void upload(String targetId) {
        if(isClientSide()){sendClientAction(UPLOAD, targetId);return;}
        if(!stillValid(getPlayer()) || !(host instanceof FactoryEncodingPanel panel)) return;
        var target=FactoryServer.providers(panel.editorGrid()).stream().filter(p -> p.factoryId().equals(targetId)).findFirst().orElse(null);
        if(target==null || !target.getLevel().mayInteract(getPlayer(), target.getBlockPos())) {
            status="Provider unavailable";return;
        }
        var stack=host.factoryPatterns().getStackInSlot(0);
        var data=FactoryPatternData.get(stack);
        if(!(stack.getItem() instanceof FactoryPatternItem) || !data.hasRecipe() || stack.getCount()!=1) {
            status="Encode a recipe first";return;
        }
        String imports=target.tags.names().isEmpty()?"":"import "+String.join(",",target.tags.names())+"\n";
        String body=host.factoryDraft().replaceAll("(?m)^import[^\r\n]*(?:\r?\n|$)", "");
        String source=SfmSyntax.recognizes(host.factoryDraft())?host.factoryDraft():imports+body;
        if(source.length()>FactoryCompiler.MAX_SOURCE_LENGTH){status="Line 1: Code exceeds 65536 characters";broadcastChanges();return;}
        // Show the exact auto-imported source being validated, including when upload is rejected.
        host.setFactoryDraft(source);code=source;
        try {
            var recipe=appeng.api.crafting.PatternDetailsHelper.decodePattern(data.recipe(),getPlayer().level());
            if(recipe==null)throw new IllegalArgumentException("Invalid recipe");
            FactoryCompiler.compile(source, true, recipe.getInputs().length,recipe.getOutputs().size());
            var slots=target.getLogic().getPatternInv();
            int empty=-1;
            for(int i=0;i<slots.size();i++)if(slots.getStackInSlot(i).isEmpty()){empty=i;break;}
            if(empty<0){status="Provider is full";return;}
            var uploaded=stack.copy();
            uploaded.set(FactoryPatternData.TYPE.get(),new FactoryPatternData(source,target.factoryId(),data.recipe()));
            if(!slots.isItemValid(empty,uploaded))throw new IllegalArgumentException("Provider rejected pattern");
            // Both inventories are local to this server thread; no external callback can extract between these updates.
            host.factoryPatterns().setItemDirect(0,ItemStack.EMPTY);
            slots.setItemDirect(empty,uploaded);
            host.setFactoryDraft(source);code=source;target.saveChanges();target.getLogic().updatePatterns();
            status="Uploaded";
        }catch(IllegalArgumentException error){status=error.getMessage();}
        broadcastChanges();
    }
    public boolean hasFactoryPattern() { return host.factoryPatterns().getStackInSlot(0).getItem() instanceof FactoryPatternItem; }
    public boolean hasRecipePattern() { return hasFactoryPattern() && FactoryPatternData.get(host.factoryPatterns().getStackInSlot(0)).hasRecipe(); }
    public boolean isPanel() { return host instanceof FactoryEncodingPanel; }
    public void recipePage() {
        if(isClientSide()){sendClientAction(RECIPE);return;}
        if(stillValid(getPlayer()) && host instanceof FactoryEncodingPanel panel)
            appeng.menu.MenuOpener.open(FactoryPanelRecipeMenu.TYPE.get(), getPlayer(), appeng.menu.locator.MenuLocators.forPart(panel));
    }
    private void acceptCodeChunk(CodeChunk chunk) {
        if(!isServerSide()||!stillValid(getPlayer())||chunk==null||chunk.text()==null||chunk.text().length()>4096||chunk.total()<1||chunk.total()>17)return;
        if(chunk.index()==0){incomingCode.setLength(0);incomingIndex=0;incomingTotal=chunk.total();}
        if(chunk.index()!=incomingIndex||chunk.total()!=incomingTotal){incomingCode.setLength(0);incomingIndex=0;incomingTotal=0;return;}
        incomingCode.append(chunk.text());incomingIndex++;
        if(incomingIndex==incomingTotal){String complete=incomingCode.toString();incomingCode.setLength(0);incomingIndex=0;incomingTotal=0;saveCode(complete);}
    }
    public void saveCode(String source) {
        if (isClientSide()) {
            if(source==null||source.length()>FactoryCompiler.MAX_SOURCE_LENGTH)return;
            var chunks=FactoryCodeChunks.split(source,4096); int total=chunks.size();
            for(int i=0;i<total;i++)sendClientAction(CHUNK,new CodeChunk(i,total,chunks.get(i)));
            return;
        }
        if (!stillValid(getPlayer()) || source == null || source.length() > FactoryCompiler.MAX_SOURCE_LENGTH) return;
        var stack = host.factoryPatterns().getStackInSlot(0);
        host.setFactoryDraft(source);
        code = source;
        if (!(stack.getItem() instanceof FactoryPatternItem)) { status = "Insert a Loop Factory Pattern"; return; }
        var old = FactoryPatternData.get(stack);
        if (!host.allowsRecipe() && old.hasRecipe()) { status = "This terminal accepts recipe-free patterns only"; return; }
        try {
            int materialCount=0,outputCount=0;
            if(old.hasRecipe()) {
                var recipe=appeng.api.crafting.PatternDetailsHelper.decodePattern(old.recipe(),getPlayer().level());
                if(recipe==null)throw new IllegalArgumentException("Invalid recipe");
                materialCount=recipe.getInputs().length;outputCount=recipe.getOutputs().size();
            }
            var program = FactoryCompiler.compile(source, old.hasRecipe(),materialCount,outputCount);
            var owner = host.editingFactory();
            var updated = stack.copy();
            updated.set(FactoryPatternData.TYPE.get(), new FactoryPatternData(source,
                    owner == null ? old.factoryId() : owner.factoryId(), old.recipe()));
            host.factoryPatterns().setItemDirect(0, updated);
            if (owner != null) { owner.tags.reconcile(program.tags()); owner.setFactoryName(program.name()); owner.saveChanges(); }
            status = "Saved";
        } catch (IllegalArgumentException error) { status = error.getMessage(); }
        broadcastChanges();
    }
    @Override public void broadcastChanges() {
        if (isServerSide()) {
            syncedCode=new FactoryCodeText(code);
            if(status.length()>512)status=status.substring(0,509)+"...";
            titleKey = host.editorTitleKey();
            var choices=isPanel()?FactoryServer.providers(host.editorGrid()):java.util.List.<FactoryBlockEntity>of();
            providerPages=Math.max(1,(choices.size()+31)/32);providerPage=Math.min(providerPage,providerPages-1);
            providers = choices.stream().skip((long)providerPage*32).limit(32)
                .map(p -> p.factoryId()+"|"+p.factoryName().substring(0,Math.min(128,p.factoryName().length())).replace("|", " ").replace("\n", " ")+"|"+p.getBlockPos().toShortString()+"|"+p.getFront().getName())
                .collect(java.util.stream.Collectors.joining("\n"));
            var owner = host.editingFactory();
            runtimeError=owner==null?"":owner.isProvider()?owner.getLogic().executionError():owner.executionError();
            runtimeWait=owner==null?"":owner.isProvider()?owner.getLogic().waitingStatus():owner.waitingStatus();
            if(runtimeError.length()>512)runtimeError=runtimeError.substring(0,509)+"...";
            factoryName = owner == null ? "Unbound" : owner.factoryName().substring(0,Math.min(128,owner.factoryName().length()));
        }
        super.broadcastChanges();
    }
}
