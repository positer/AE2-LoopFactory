package com.example.ae2loprobe;

import com.example.ae2lightoptimizer.client.CraftingRipperScreen;
import com.example.ae2lightoptimizer.client.RipperImportButton;
import com.example.ae2lightoptimizer.menu.CraftingRipperMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Clicks the actual production button and captures native normal/hover/disabled frames. */
final class RipperImportClient {
    private static int step, ticks, elapsed, originalScale;
    private static String originalLanguage;
    private static volatile String failure;
    private static volatile boolean finished;
    private static CompletableFuture<?> reload;
    private static final java.util.List<Object> frames = new java.util.ArrayList<>();
    private static final LinkedHashMap<String, Object> checks = new LinkedHashMap<>();
    private static void next(int value) { step = value; elapsed = 0; }
    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (Throwable error) { failure = error.toString(); }
        });
    }
    private static RipperImportButton button(CraftingRipperScreen screen) throws Exception {
        var field = CraftingRipperScreen.class.getDeclaredField("importPatterns"); field.setAccessible(true);
        return (RipperImportButton) field.get(screen);
    }
    private static void hover(Minecraft mc, RipperImportButton button) throws Exception {
        double x = (button.getX() + 8) * (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double y = (button.getY() + 8) * (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        var xField = mc.mouseHandler.getClass().getDeclaredField("xpos"); xField.setAccessible(true); xField.setDouble(mc.mouseHandler, x);
        var yField = mc.mouseHandler.getClass().getDeclaredField("ypos"); yField.setAccessible(true); yField.setDouble(mc.mouseHandler, y);
    }
    private static void click(CraftingRipperScreen screen, RipperImportButton button) {
        screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(button.getX() + 8, button.getY() + 8,
                new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
    }
    private static void language(Minecraft mc, String locale, int scale) {
        mc.options.guiScale().set(scale); mc.resizeGui();
        mc.getLanguageManager().setSelected(locale); mc.options.languageCode = locale; reload = mc.reloadResourcePacks();
    }
    private static boolean loaded(Minecraft mc) { return reload != null && reload.isDone() && mc.getOverlay() == null; }
    private static void frame(String suffix) throws Exception {
        var mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        if (suffix.endsWith("-1x")) RipperImportFixture.check(scale == 1, "Actual GUI scale is not 1");
        if (suffix.endsWith("-2x")) RipperImportFixture.check(scale == 2, "Actual GUI scale is not 2");
        var button = button((CraftingRipperScreen) mc.screen);
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        frames.add(java.util.Map.of("name", suffix, "guiScale", scale, "language", mc.options.languageCode,
                "width", mc.getWindow().getWidth(), "height", mc.getWindow().getHeight(),
                "buttonActive", button.active, "mouseGui", List.of(mouseX, mouseY),
                "tooltip", button.getTooltipMessage().stream().map(net.minecraft.network.chat.Component::getString).toList()));
        ClientBootstrap.capture("ae2lo-ripper-import-" + suffix + ".png");
    }

    static boolean tick(Minecraft mc) {
        if (!DynamicPatternFixture.enabled) return true;
        if (RipperImportFixture.failure != null) throw new IllegalStateException(RipperImportFixture.failure);
        if (failure != null) throw new IllegalStateException(failure);
        if (finished) return true;
        ticks++; elapsed++;
        if (ticks > 1500) throw new IllegalStateException("Pattern import UI timeout at stage " + step);
        try {
            if (step == 0) {
                originalScale = mc.options.guiScale().get(); originalLanguage = mc.options.languageCode;
                server(mc, player -> { try { RipperImportFixture.prepare(player); } catch (Exception e) { throw new IllegalStateException(e); } });
                next(1); return false;
            }
            if (step == 1) {
                if (!RipperImportFixture.prepared) return false;
                if (!RipperImportFixture.ready) { if (ticks % 5 == 0) server(mc, p -> RipperImportFixture.awaitReady()); return false; }
                language(mc, "en_us", 1); next(2); return false;
            }
            if (step == 12) {
                if (RipperImportFixture.closed && ticks % 5 == 0 && !RipperImportFixture.cancelled)
                    server(mc, RipperImportFixture::verifyCancellation);
                if (RipperImportFixture.cancelled) next(13);
                return false;
            }
            if (!(mc.screen instanceof CraftingRipperScreen screen)) return false;
            var menu = (CraftingRipperMenu) mc.player.containerMenu;
            var button = button(screen);
            switch (step) {
                case 2 -> {
                    if (!loaded(mc)) { elapsed = 0; return false; }
                    if (elapsed < 30) return false;
                    RipperImportFixture.check(button.active && button.getWidth() == 16 && button.getHeight() == 16,
                            "Import button must use active native 16x16 chrome");
                    RipperImportFixture.check(button.getX() == screen.getGuiLeft() + 170 && button.getY() == screen.getGuiTop() + 24,
                            "Import button layout differs");
                    checks.put("nativeIconButton", true); checks.put("buttonBounds", List.of(170, 24, 16, 16));
                    frame("en-normal-1x"); hover(mc, button); next(3);
                }
                case 3 -> {
                    hover(mc, button); if (elapsed < 20) return false;
                    frame("en-hover-1x"); language(mc, "zh_cn", 1); next(4);
                }
                case 4 -> {
                    if (!loaded(mc)) { elapsed = 0; return false; }
                    if (elapsed < 30) return false;
                    hover(mc, button); if (elapsed < 50) return false;
                    RipperImportFixture.check(button.getMessage().getString().equals("导入网络样板"), "Chinese button translation missing");
                    frame("zh-hover-1x"); click(screen, button); next(5);
                }
                case 5 -> {
                    if (menu.importingPatterns || menu.importedPatterns != RipperImportFixture.FIRST_IMPORTED) return false;
                    server(mc, RipperImportFixture::verifyFirst); next(6);
                }
                case 6 -> {
                    if (!RipperImportFixture.firstVerified) return false;
                    hover(mc, button); frame("zh-complete-1x"); language(mc, "en_us", 2); next(7);
                }
                case 7 -> {
                    if (!loaded(mc)) { elapsed = 0; return false; }
                    if (elapsed < 30) return false;
                    hover(mc, button); if (elapsed < 50) return false;
                    frame("en-hover-2x"); click(screen, button); next(8);
                }
                case 8 -> {
                    if (elapsed < 15 || menu.importingPatterns || menu.importedPatterns != 0) return false;
                    server(mc, RipperImportFixture::seedCancellation); next(9);
                }
                case 9 -> {
                    if (!RipperImportFixture.cancelSeeded) return false;
                    click(screen, button); next(10);
                }
                case 10 -> {
                    if (!menu.importingPatterns || menu.importedPatterns == 0 || button.active) { elapsed = 0; return false; }
                    hover(mc, button); if (elapsed < 8) return false;
                    frame("running-2x");
                    server(mc, RipperImportFixture::closeDuringImport); next(12);
                }
                case 13 -> {
                    if (!menu.networkAvailable || !button.active || elapsed < 10) return false;
                    click(screen, button); next(14);
                }
                case 14 -> {
                    if (menu.importingPatterns || menu.importedPatterns != RipperImportFixture.CANCEL_SUPPLY - RipperImportFixture.cancelledCount) return false;
                    server(mc, p -> { RipperImportFixture.verifyFinal(p); RipperImportFixture.disconnect(); }); next(15);
                }
                case 15 -> {
                    if (menu.networkAvailable || button.active || !RipperImportFixture.finalVerified) { elapsed = 0; return false; }
                    hover(mc, button); if (elapsed < 25) return false;
                    frame("offline-2x"); click(screen, button);
                    server(mc, p -> {
                        var serverMenu = (CraftingRipperMenu) p.containerMenu;
                        long count = serverMenu.importedPatterns; serverMenu.importPatterns();
                        RipperImportFixture.check(!serverMenu.importingPatterns && serverMenu.importedPatterns == count, "Offline action mutated import state");
                        RipperImportFixture.reconnect();
                    });
                    checks.put("offlineClickRejected", true); checks.put("runningClickDisabled", true);
                    next(16);
                }
                case 16 -> {
                    if (!menu.networkAvailable || elapsed < 30) return false;
                    language(mc, "zh_cn", 2); next(17);
                }
                case 17 -> {
                    if (!loaded(mc)) { elapsed = 0; return false; }
                    if (elapsed < 30) return false;
                    hover(mc, button); if (elapsed < 50) return false;
                    frame("zh-hover-2x"); language(mc, originalLanguage, originalScale); next(18);
                }
                case 18 -> {
                    if (!loaded(mc)) { elapsed = 0; return false; }
                    if (elapsed < 30) return false;
                    frame("restored");
                    checks.put("languages", List.of("en_us", "zh_cn")); checks.put("guiScales", List.of(1, 2));
                    checks.put("frames", frames);
                    server(mc, p -> {
                        try { RipperImportFixture.finish(checks); finished = true; }
                        catch (Exception e) { throw new IllegalStateException(e); }
                    }); next(19);
                }
            }
        } catch (Exception error) { throw new IllegalStateException(error); }
        return false;
    }
}
