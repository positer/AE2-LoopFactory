package com.example.ae2lightoptimizer.omni;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import com.example.ae2lightoptimizer.network.OmniLinkDirectory;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.*;
import java.util.List;

public final class OmniConnectorMenu extends AEBaseMenu {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "ae2lightoptimizer");
    public static final DeferredHolder<MenuType<?>, MenuType<OmniConnectorMenu>> TYPE = MENUS.register("omnidirectional_connector",
            () -> MenuTypeBuilder.create(OmniConnectorMenu::new, OmniConnectorBlockEntity.class)
                    .buildUnregistered(Identifier.fromNamespaceAndPath("ae2lightoptimizer", "omnidirectional_connector")));
    public static final int PAGE_SIZE = 6;
    public record Entry(String id, String name, int endpoints, boolean privateLink) {}
    private static final com.google.gson.Gson JSON = new com.google.gson.Gson();
    private static final appeng.menu.guisync.ClientActionKey<String> CREATE = new appeng.menu.guisync.ClientActionKey<>("omniCreate");
    private static final appeng.menu.guisync.ClientActionKey<String> SELECT = new appeng.menu.guisync.ClientActionKey<>("omniSelect");
    private static final appeng.menu.guisync.ClientActionKey<Integer> PAGE = new appeng.menu.guisync.ClientActionKey<>("omniPage");
    private static final appeng.menu.guisync.ClientActionKey<Boolean> CREATE_PRIVATE = new appeng.menu.guisync.ClientActionKey<>("omniCreatePrivate");
    private final OmniConnectorBlockEntity endpoint;
    @GuiSync(0) public String selectedId = "";
    @GuiSync(1) public String selectedName = "";
    @GuiSync(2) public String rows = "[]";
    @GuiSync(3) public int page;
    @GuiSync(4) public int pages = 1;
    @GuiSync(5) public boolean invalidName;
    @GuiSync(6) public boolean createPrivate;
    @GuiSync(7) public boolean selectedPrivate;
    public OmniConnectorMenu(MenuType<?> type, int id, Inventory inventory, OmniConnectorBlockEntity endpoint) {
        super(type, id, inventory, endpoint); this.endpoint = endpoint;
        registerClientAction(CREATE, net.minecraft.network.codec.ByteBufCodecs.stringUtf8(192), this::create);
        registerClientAction(SELECT, net.minecraft.network.codec.ByteBufCodecs.stringUtf8(36), this::select);
        registerClientAction(PAGE, net.minecraft.network.codec.ByteBufCodecs.VAR_INT, this::setPage);
        registerClientAction(CREATE_PRIVATE, net.minecraft.network.codec.ByteBufCodecs.BOOL, this::setCreatePrivate);
    }
    private boolean canConfigure() {
        return isServerSide() && getPlayer().containerMenu == this && stillValid(getPlayer())
                && appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(endpoint.getLevel(), endpoint.getBlockPos()), getPlayer());
    }
    private String playerId() { return getPlayer().getUUID().toString(); }
    public void setCreatePrivate(boolean value) {
        if (isClientSide()) { sendClientAction(CREATE_PRIVATE, value); return; }
        createPrivate = value;
    }
    public void create(String name) {
        if (isClientSide()) { sendClientAction(CREATE, name); return; }
        if (!canConfigure()) return;
        try {
            var visibility = createPrivate ? OmniLinkDirectory.Visibility.PRIVATE : OmniLinkDirectory.Visibility.PUBLIC;
            var link = OmniLinkSavedData.get(endpoint.getLevel().getServer()).create(name, visibility, playerId());
            endpoint.selectLink(link.id()); invalidName = false;
        } catch (IllegalArgumentException invalid) { invalidName = true; }
    }
    public void select(String id) {
        if (isClientSide()) { sendClientAction(SELECT, id); return; }
        if (!canConfigure()) return;
        if (id.isEmpty()) { endpoint.selectLink(""); invalidName = false; return; }
        // A player may only bind a channel that is visible to them: public channels and their own private ones.
        if (OmniLinkSavedData.get(endpoint.getLevel().getServer()).directory().canSee(id, playerId())) {
            endpoint.selectLink(id); invalidName = false;
        }
    }
    public void setPage(int value) {
        if (isClientSide()) { sendClientAction(PAGE, value); return; }
        if (canConfigure()) page = Math.max(0, Math.min(value, pages - 1));
    }
    public List<Entry> entries() { return java.util.Arrays.asList(JSON.fromJson(rows, Entry[].class)); }
    @Override public void broadcastChanges() {
        if (isServerSide()) {
            var server = endpoint.getLevel().getServer();
            var links = OmniLinkSavedData.get(server).directory().sortedVisible(playerId());
            pages = Math.max(1, (links.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, pages - 1));
            selectedId = endpoint.linkId();
            var selected = OmniLinkSavedData.get(server).directory().get(selectedId);
            boolean visible = selected != null && selected.visibleTo(playerId());
            selectedName = visible ? selected.name() : "";
            selectedPrivate = visible && selected.visibility() == OmniLinkDirectory.Visibility.PRIVATE;
            rows = JSON.toJson(links.stream().skip((long) page * PAGE_SIZE).limit(PAGE_SIZE)
                    .map(link -> new Entry(link.id(), link.name(), OmniNetworks.get(server).count(link.id()),
                            link.visibility() == OmniLinkDirectory.Visibility.PRIVATE)).toList());
        }
        super.broadcastChanges();
    }
}
