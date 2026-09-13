package com.example.ae2lfprobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@Mod(value = "ae2lf_runtime_probe", dist = Dist.CLIENT)
public final class ClientBootstrap {
    private static boolean started;
    private static int ticks;
    private static int visualTicks;
    private static boolean disconnectRequested;
    private static String pendingShot = "";
    private static int matchingScreenTicks;
    private static long shutdownDeadline;
    private static final long WATCHDOG_NANOS = 600_000_000_000L;
    /** Backstop for a client loop that stops ticking entirely; the harness still records the real state. */
    private static void startWatchdog() {
        var watchdog = new Thread(() -> {
            try { Thread.sleep(WATCHDOG_NANOS / 1_000_000L); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            try {
                var server = Minecraft.getInstance().getSingleplayerServer();
                java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("shutdown-timeout.txt"),
                        "Client loop did not finish shutdown within " + (WATCHDOG_NANOS / 1_000_000_000L) + "s; server=" + (server != null)
                        + (server != null ? " shutdown=" + server.isShutdown() + " players=" + server.getPlayerList().getPlayerCount() : "")
                        + ". Functional evidence is unaffected; the harness exits explicitly.");
            } catch (Exception failure) { failure.printStackTrace(); }
            Runtime.getRuntime().halt(0);
        }, "ae2lf-shutdown-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }
    public ClientBootstrap() { NeoForge.EVENT_BUS.addListener(ClientBootstrap::tick); }
    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        // Generic visual capture hook: name the screen and the file, take one screenshot, clear the flag.
        String shot = System.getProperty("ae2lf.probe.screenshot", "");
        boolean matchingScreen = !shot.isEmpty() && mc.screen != null
                && mc.screen.getClass().getSimpleName().equals(System.getProperty("ae2lf.probe.screenshotScreen", "FactoryEditorScreen"));
        if (!shot.equals(pendingShot) || !matchingScreen) { pendingShot = shot; matchingScreenTicks = 0; }
        // A menu can exist before its first render; wait for native frames instead of capturing the old world.
        if (matchingScreen && ++matchingScreenTicks >= 8) {
            System.clearProperty("ae2lf.probe.screenshot");
            var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget());
            try (image) {
                var directory = java.nio.file.Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("screenshots");
                java.nio.file.Files.createDirectories(directory);
                image.writeToFile(directory.resolve(shot + ".png"));
            } catch (java.io.IOException failure) {
                throw new RuntimeException(failure);
            }
        }
        if (Boolean.getBoolean("ae2lf.probe.background")) {
            if (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_VISIBLE) != 0)
                throw new IllegalStateException("Background probe window became visible");
        }
        if (Boolean.getBoolean("ae2lf.probe.fullAudit") && RestartState.visualReady) {
            BackgroundAudit.tick(mc);
            return;
        }
        if (RestartState.visualReady && mc.player != null && mc.screen == null) {
            ++visualTicks;
            if (visualTicks < 35) {
                mc.player.getAbilities().flying=true;
                mc.player.setPos(4.5,102,10);mc.player.setYRot(180f);mc.player.setXRot(20f);
            }
            if (visualTicks == 40) {
            var captured = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget());
            try (captured) { captured.writeToFile(java.nio.file.Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("factory-assets.png")); }
            catch(java.io.IOException failure) { throw new RuntimeException(failure); }
            }
            if (visualTicks >= 80 && java.nio.file.Files.isRegularFile(java.nio.file.Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("factory-assets.png"))) {
                RestartState.visualReady=false;RestartState.exitRequested=true;
            }
        }
        if (RestartState.exitRequested) {
            RestartState.exitRequested = false;
            disconnectRequested = true;
            shutdownDeadline = System.nanoTime() + WATCHDOG_NANOS;
            startWatchdog();
            // Minecraft.disconnect performs an internal wait that stops firing client ticks; halt the
            // integrated server directly and let the normal tick loop observe the graceful shutdown.
            var server = mc.getSingleplayerServer();
            if (server != null) server.halt(false);
            return;
        }
        if (disconnectRequested) {
            var server = mc.getSingleplayerServer();
            if (server != null && !server.isShutdown()) {
                if (System.nanoTime() < shutdownDeadline) return;
                // Bounded shutdown: record the real state instead of hanging the harness forever.
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("shutdown-timeout.txt"),
                            "Integrated server still running after the bounded shutdown wait; isShutdown=" + server.isShutdown()
                            + " clientLevel=" + (mc.level != null) + " players=" + server.getPlayerList().getPlayerCount()
                            + ". Functional evidence is unaffected; process exits explicitly.");
                } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
                mc.stop();
                return;
            }
            if (server != null) return;
            try { java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("ae2lf.probe.reportDir")).resolve("normal-shutdown.txt"),
                    "Integrated server fully stopped; requesting normal process exit"); }
            catch(java.io.IOException failure) { throw new RuntimeException(failure); }
            mc.stop();
            return;
        }
        if (started || !Boolean.getBoolean("ae2lf.probe") || mc.level != null || mc.getOverlay() != null) return;
        if (!(mc.screen instanceof TitleScreen) && !(mc.screen != null &&
                mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) return;
        if (++ticks < 40) return;
        started = true;
        if (RestartState.RESUME) {
            String resume = System.getProperty("ae2lf.probe.world", "");
            if (!resume.matches("AE2LF-Factory-Probe-[0-9a-f-]{36}") ||
                    !java.nio.file.Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(resume).resolve("ae2lf-probe-owned.txt")))
                throw new IllegalStateException("Refusing an unowned resume world");
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            mc.createWorldOpenFlows().openWorld(resume, () -> { throw new IllegalStateException("Test world loading cancelled"); });
            return;
        }
        // Read-only inspection of a prepared copy of an existing world.
        String openWorld = System.getProperty("ae2lf.probe.openWorld", "");
        if (!openWorld.isEmpty()) {
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(8);
            mc.options.simulationDistance().set(8);
            mc.createWorldOpenFlows().openWorld(openWorld, () -> { throw new IllegalStateException("Inspect world loading cancelled"); });
            return;
        }
        String name = "AE2LF-Factory-Probe-" + java.util.UUID.randomUUID();
        if (java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(name)))
            throw new IllegalStateException("Refusing an existing world");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(4);
        mc.options.simulationDistance().set(5);
        mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        mc.getTutorial().stop();
        var settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0xAE210005L, false, false),
                WorldPresets::createNormalWorldDimensions, mc.screen);
    }
}
