package com.example.ae2lightoptimizer.omni;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
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
    public record Entry(String id, String name, int endpoints) {}
    private static final com.google.gson.Gson JSON = new com.google.gson.Gson();
    private static final appeng.menu.guisync.ClientActionKey<String> CREATE = new appeng.menu.guisync.ClientActionKey<>("omniCreate");
    private static final appeng.menu.guisync.ClientActionKey<String> SELECT = new appeng.menu.guisync.ClientActionKey<>("omniSelect");
    private static final appeng.menu.guisync.ClientActionKey<Integer> PAGE = new appeng.menu.guisync.ClientActionKey<>("omniPage");
    private final OmniConnectorBlockEntity endpoint;
    @GuiSync(0) public String selectedId = "";
    @GuiSync(1) public String selectedName = "";
    @GuiSync(2) public String rows = "[]";
    @GuiSync(3) public int page;
    @GuiSync(4) public int pages = 1;
    @GuiSync(5) public boolean invalidName;
    public OmniConnectorMenu(MenuType<?> type, int id, Inventory inventory, OmniConnectorBlockEntity endpoint) {
        super(type, id, inventory, endpoint); this.endpoint = endpoint;
        registerClientAction(CREATE, net.minecraft.network.codec.ByteBufCodecs.stringUtf8(192), this::create);
        registerClientAction(SELECT, net.minecraft.network.codec.ByteBufCodecs.stringUtf8(36), this::select);
        registerClientAction(PAGE, net.minecraft.network.codec.ByteBufCodecs.VAR_INT, this::setPage);
    }
    private boolean canConfigure() {
        return isServerSide() && getPlayer().containerMenu == this && stillValid(getPlayer())
                && appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(endpoint.getLevel(), endpoint.getBlockPos()), getPlayer());
    }
    public void create(String name) {
        if (isClientSide()) { sendClientAction(CREATE, name); return; }
        if (!canConfigure()) return;
        try {
            var link = OmniLinkSavedData.get(endpoint.getLevel().getServer()).create(name);
            endpoint.selectLink(link.id()); invalidName = false;
        } catch (IllegalArgumentException invalid) { invalidName = true; }
    }
    public void select(String id) {
        if (isClientSide()) { sendClientAction(SELECT, id); return; }
        if (!canConfigure()) return;
        if (id.isEmpty() || OmniLinkSavedData.get(endpoint.getLevel().getServer()).directory().get(id) != null) {
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
            var links = OmniLinkSavedData.get(server).directory().sorted();
            pages = Math.max(1, (links.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, pages - 1));
            selectedId = endpoint.linkId();
            var selected = OmniLinkSavedData.get(server).directory().get(selectedId);
            selectedName = selected == null ? "" : selected.name();
            rows = JSON.toJson(links.stream().skip((long) page * PAGE_SIZE).limit(PAGE_SIZE)
                    .map(link -> new Entry(link.id(), link.name(), OmniNetworks.get(server).count(link.id()))).toList());
        }
        super.broadcastChanges();
    }
}
