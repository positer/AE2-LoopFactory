package com.example.ae2loprobe;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Test-only client: creates a new isolated world and captures native rendered frames. */
@Mod(value = "ae2lo_runtime_probe", dist = Dist.CLIENT)
public final class ClientBootstrap {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String WORLD_PREFIX = "AE2LO-Ripper-Probe-";
    private static final long SEED = 0xAE210004L;
    private static boolean attempted;
    private static int titleTicks;
    private static String pendingScreenshot;
    private static int screenshotDelay;
    private static final java.util.Set<String> CAPTURED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final String UI_SCREENSHOT = "ae2lo-ripper-ui.png";
    private static boolean uiRequested;
    private static int uiDelay = 20;
    private static final long SHUTDOWN_READY_TIMEOUT_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
    private static volatile boolean shutdownPreparationStarted;
    private static volatile boolean shutdownPreparationFailed;
    private static volatile boolean serverHaltRequested;
    private static boolean stopRequested;
    private static volatile net.minecraft.client.server.IntegratedServer shutdownServer;
    private static String readinessPhase = "before_ticket_release";
    private static long readinessPhaseStarted;
    private static int consecutiveReadyTicks;
    private static int lastReadinessSampleTick = -100;
    private static boolean oldPauseOnLostFocus;
    private static long hiddenClientTicks;
    private static long visibleObservations;
    private static int lastWindowVisible = -1;
    private static boolean hiddenNormalStopObserved;
    private static int oldRenderDistance;
    private static int oldSimulationDistance;
    private static net.minecraft.client.tutorial.TutorialSteps oldTutorialStep;

    public ClientBootstrap() {
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::onServerTick);
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::onServerStopping);
        LOG.info("AE2LO client probe bootstrap armed; only a brand-new {} world can be created", WORLD_PREFIX);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        verifyHiddenWindow(minecraft);
        // The normal disconnect may clear the client level before the next tick. Keep observing
        // the exact server we halted instead of losing shutdown progress behind the world guard.
        if (shutdownPreparationStarted) {
            advanceNormalShutdown(minecraft);
            return;
        }
        if (!attempted && Boolean.getBoolean("ae2lo.probe")
                && minecraft.level == null && minecraft.getSingleplayerServer() == null
                && minecraft.getOverlay() == null && (minecraft.screen instanceof TitleScreen
                || minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) {
            if (++titleTicks >= 40) {
                attempted = true;
                createIsolatedWorld(minecraft);
            }
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        if (!minecraft.getSingleplayerServer().getWorldData().getLevelName().startsWith(WORLD_PREFIX)) return;

        var request = ProbeState.screenshotRequest;
        if (request != null && !CAPTURED.contains(request)) {
            if (!request.equals(pendingScreenshot)) {
                pendingScreenshot = request;
                screenshotDelay = 20;
            }
            if (screenshotDelay > 0 && --screenshotDelay == 0) capture(request);
        }
        if (ProbeState.finished && request != null && CAPTURED.contains(request)) {
            if (!uiRequested) {
                uiRequested = true;
                var playerId = minecraft.player.getUUID();
                var server = minecraft.getSingleplayerServer();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    var entity = server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(-2, 100, 0));
                    if (player != null && entity instanceof com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity ripper) {
                        // Native menus enforce distance; keep this test player within the provider's use radius.
                        player.connection.teleport(-4.5, 102, 3.5, -145f, 20f);
                        ripper.openMenu(player, appeng.menu.locator.MenuLocators.forBlockEntity(ripper));
                        LOG.info("AE2LO probe opened the production Crafting Ripper menu for visual validation");
                    } else LOG.error("AE2LO probe could not find its production ripper/player for UI validation");
                });
            }
            if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("CraftingRipperScreen")
                    && !CAPTURED.contains(UI_SCREENSHOT) && uiDelay > 0 && --uiDelay == 0) capture(UI_SCREENSHOT);
            if (CAPTURED.contains(UI_SCREENSHOT) && Boolean.getBoolean("ae2lo.probe.autoExit")) {
                advanceNormalShutdown(minecraft);
            }
        }
    }

    /** Observe every real client tick; the launcher agent, not this observer, hides the window. */
    private static void verifyHiddenWindow(Minecraft minecraft) {
        if (!Boolean.getBoolean("ae2lo.probe") || !Boolean.getBoolean("ae2lo.probe.background")) return;
        hiddenClientTicks++;
        lastWindowVisible = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(
                minecraft.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_VISIBLE);
        if (lastWindowVisible != 0) visibleObservations++;
        if (hiddenClientTicks == 1 || hiddenClientTicks % 100 == 0 || lastWindowVisible != 0) {
            writeHiddenWindowState(minecraft);
        }
        if (lastWindowVisible != 0) {
            throw new IllegalStateException("Background ripper probe window became visible at client tick " + hiddenClientTicks);
        }
    }

    private static void writeHiddenWindowState(Minecraft minecraft) {
        if (!Boolean.getBoolean("ae2lo.probe.background")) return;
        var state = new java.util.LinkedHashMap<String, Object>();
        state.put("observedAt", java.time.Instant.now().toString());
        state.put("clientTicksChecked", hiddenClientTicks);
        state.put("visibleObservations", visibleObservations);
        state.put("lastWindowVisible", lastWindowVisible);
        state.put("normalStopObserved", hiddenNormalStopObserved);
        state.put("phase", ProbeState.phase);
        state.put("shutdownBudgetHookApplied", java.util.Arrays.stream(net.minecraft.server.MinecraftServer.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("advanceShutdownChunks")));
        state.put("world", minecraft.getSingleplayerServer() == null ? "" :
                minecraft.getSingleplayerServer().getWorldData().getLevelName());
        state.put("productionCodeSource", String.valueOf(
                com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity.class
                        .getProtectionDomain().getCodeSource().getLocation()));
        state.put("probeCodeSource", String.valueOf(ClientBootstrap.class.getProtectionDomain().getCodeSource().getLocation()));
        try {
            Files.writeString(evidenceDirectory(minecraft).resolve("hidden-window-state.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(state));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot persist hidden-window evidence", error);
        }
    }

    /** The server thread owns preparation; the client only observes the exact server's completion. */
    private static void advanceNormalShutdown(Minecraft minecraft) {
        if (stopRequested) return;
        if (shutdownServer != null) {
            completeNormalShutdown(minecraft);
            return;
        }
        var server = minecraft.getSingleplayerServer();
        if (server == null) return;
        if (!shutdownPreparationStarted) {
            shutdownServer = server;
            shutdownPreparationStarted = true;
            shutdownEvent(minecraft, "prepare_requested", "world=" + server.getWorldData().getLevelName());
        }
    }

    private static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        var server = shutdownServer;
        if (!shutdownPreparationStarted || shutdownPreparationFailed || serverHaltRequested
                || server == null || event.getServer() != server) return;
        var minecraft = Minecraft.getInstance();
        try {
            if (!server.getWorldData().getLevelName().startsWith(WORLD_PREFIX)) {
                throw new IllegalStateException("Integrated world changed during shutdown preparation");
            }
            if (readinessPhaseStarted == 0) readinessPhaseStarted = System.nanoTime();
            var observation = ShutdownReadiness.capture(server, readinessPhase);
            if (observation.tick() - lastReadinessSampleTick >= 20) {
                observation.write(evidenceDirectory(minecraft), "chunk-readiness-latest.json");
                lastReadinessSampleTick = observation.tick();
            }
            if (!observation.ready()) {
                consecutiveReadyTicks = 0;
                if (System.nanoTime() - readinessPhaseStarted > SHUTDOWN_READY_TIMEOUT_NANOS) {
                    observation.write(evidenceDirectory(minecraft), "chunk-readiness-timeout.json");
                    throw new IllegalStateException("Native chunk readiness did not settle within 180 seconds in " + readinessPhase);
                }
                return;
            }
            if (++consecutiveReadyTicks < 2) return;
            observation.write(evidenceDirectory(minecraft), "chunk-readiness-" + readinessPhase + ".json");
            shutdownEvent(minecraft, "chunk_readiness_passed", "phase=" + readinessPhase
                    + " consecutiveServerTicks=" + consecutiveReadyTicks + " serverTick=" + observation.tick());
            if (readinessPhase.equals("before_ticket_release")) {
                // Release only this fixture's nine tickets, after their generation claims settled.
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    boolean removed = server.overworld().setChunkForced(x, z, false);
                    shutdownEvent(minecraft, "ticket_released", "chunk=" + x + "," + z + " removed=" + removed);
                }
                readinessPhase = "after_ticket_release";
            } else if (readinessPhase.equals("after_ticket_release")) {
                shutdownEvent(minecraft, "save_begin", "serverTick=" + server.getTickCount());
                long began = System.nanoTime();
                boolean saved = server.saveEverything(false, true, true);
                shutdownEvent(minecraft, "save_complete", "saved=" + saved + " elapsedMillis="
                        + (System.nanoTime() - began) / 1_000_000 + " serverTick=" + server.getTickCount());
                if (!saved) throw new IllegalStateException("Native pre-save did not report success");
                readinessPhase = "after_pre_save";
            } else {
                // The final predicate and native halt occur in the same server-thread callback.
                // Do not enter Minecraft.disconnect while the native server is still running.
                serverHaltRequested = true;
                shutdownEvent(minecraft, "server_halt_requested", "All native holders ready and lifecycle queues drained on server thread");
                server.halt(false);
            }
            consecutiveReadyTicks = 0;
            readinessPhaseStarted = System.nanoTime();
            lastReadinessSampleTick = -100;
        } catch (Throwable error) {
            shutdownPreparationFailed = true;
            LOG.error("AE2LO native shutdown preparation failed; leaving client open without forced exit", error);
            shutdownEvent(minecraft, "prepare_failed", error.toString());
        }
    }

    private static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        if (event.getServer() != shutdownServer || !serverHaltRequested) return;
        try {
            ShutdownReadiness.capture(event.getServer(), "native_stopping_event")
                    .write(evidenceDirectory(Minecraft.getInstance()), "chunk-readiness-native-stopping.json");
        } catch (Throwable error) {
            shutdownPreparationFailed = true;
            shutdownEvent(Minecraft.getInstance(), "prepare_failed", "Cannot record native stopping state: " + error);
        }
    }

    private static void completeNormalShutdown(Minecraft minecraft) {
        var server = shutdownServer;
        if (server == null || !serverHaltRequested || shutdownPreparationFailed || !server.isShutdown()) return;
        var currentServer = minecraft.getSingleplayerServer();
        if (currentServer != null && currentServer != server) {
            throw new IllegalStateException("Refusing to disconnect a different integrated server during probe shutdown");
        }
        stopRequested = true;
        shutdownEvent(minecraft, "server_shutdown_observed", "serverShutdown=true; native server finished before explicit client teardown");
        shutdownEvent(minecraft, "disconnect_begin", "Keep renderDistance=" + minecraft.options.renderDistance().get()
                + " simulationDistance=" + minecraft.options.simulationDistance().get()
                + "; integrated server already stopped");
        if (currentServer != null) minecraft.disconnect(new TitleScreen(), false);
        shutdownEvent(minecraft, "disconnect_complete", "serverShutdown=" + server.isShutdown()
                + " integratedServerPresent=" + (minecraft.getSingleplayerServer() != null));
        if (!server.isShutdown() || minecraft.getSingleplayerServer() != null) {
            shutdownEvent(minecraft, "disconnect_incomplete", "Do not restore distances or force process exit while server remains alive");
            return;
        }
        minecraft.options.renderDistance().set(oldRenderDistance);
        minecraft.options.simulationDistance().set(oldSimulationDistance);
        minecraft.getTutorial().stop();
        minecraft.options.tutorialStep = oldTutorialStep;
        shutdownEvent(minecraft, "tutorial_restored", "step=" + oldTutorialStep + "; integrated server already disconnected");
        minecraft.options.pauseOnLostFocus = oldPauseOnLostFocus;
        shutdownEvent(minecraft, "options_restored", "renderDistance=" + oldRenderDistance
                + " simulationDistance=" + oldSimulationDistance + " pauseOnLostFocus=" + oldPauseOnLostFocus
                + "; helper does not call Options.save");
        shutdownEvent(minecraft, "normal_stop_requested", "Calling Minecraft.stop after native integrated-server disconnect completed");
        hiddenNormalStopObserved = true;
        writeHiddenWindowState(minecraft);
        minecraft.stop();
    }

    private static synchronized void shutdownEvent(Minecraft minecraft, String phase, String detail) {
        String line = java.time.Instant.now() + " " + phase + " " + detail;
        LOG.info("AE2LO probe shutdown experiment: {}", line);
        try {
            Files.writeString(evidenceDirectory(minecraft).resolve("client-shutdown.log"), line + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException error) {
            LOG.error("Cannot persist client shutdown evidence", error);
        }
    }

    private static void createIsolatedWorld(Minecraft minecraft) {
        String defaultName = WORLD_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        String worldId = System.getProperty("ae2lo.probe.world", defaultName);
        if (!worldId.matches("AE2LO-Ripper-Probe-[A-Za-z0-9_.-]+")) {
            LOG.error("Refusing unsafe/non-probe world id {}", worldId);
            return;
        }
        var saves = minecraft.gameDirectory.toPath().toAbsolutePath().normalize().resolve("saves");
        var target = saves.resolve(worldId).normalize();
        if (!target.getParent().equals(saves) || Files.exists(target)) {
            LOG.error("Refusing existing or non-isolated world path {}", target);
            return;
        }
        // In-memory only: the harness must continue ticking while the evidence collector reads logs.
        // No Options.save call is made by this test helper.
        oldTutorialStep = minecraft.options.tutorialStep;
        minecraft.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        minecraft.getTutorial().stop();
        shutdownEvent(minecraft, "tutorial_suspended", "step=" + oldTutorialStep + "->NONE; in-memory only, no Tutorial.setStep or Options.save");
        oldPauseOnLostFocus = minecraft.options.pauseOnLostFocus;
        oldRenderDistance = minecraft.options.renderDistance().get();
        oldSimulationDistance = minecraft.options.simulationDistance().get();
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.renderDistance().set(6);
        minecraft.options.simulationDistance().set(5);
        shutdownEvent(minecraft, "options_overridden", "renderDistance=" + oldRenderDistance + "->6 simulationDistance="
                + oldSimulationDistance + "->5 pauseOnLostFocus=" + oldPauseOnLostFocus + "->false; in-memory only");
        var settings = new LevelSettings(worldId, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new net.minecraft.world.level.GameRules(), WorldDataConfiguration.DEFAULT);
        LOG.info("AE2LO client probe creating NEW world {} at {} with seed {}", worldId, target, SEED);
        minecraft.createWorldOpenFlows().createFreshLevel(worldId, settings,
                new WorldOptions(SEED, false, false), WorldPresets::createNormalWorldDimensions, minecraft.screen);
    }

    /** Safe from the server thread; actual framebuffer access is dispatched to the client/render thread. */
    public static void capture(String fileName) {
        if (fileName == null || !fileName.matches("ae2lo-ripper-[A-Za-z0-9_.-]+\\.png")) {
            throw new IllegalArgumentException("Unexpected probe screenshot name: " + fileName);
        }
        var minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            LOG.info("AE2LO native frame capture requested: {} phase={} screen={}", fileName, ProbeState.phase,
                    minecraft.screen == null ? "world" : minecraft.screen.getClass().getName());
            Screenshot.grab(evidenceDirectory(minecraft).toFile(), fileName, minecraft.getMainRenderTarget(),
                    message -> onScreenshotComplete(minecraft, fileName, message));
        });
    }

    private static java.nio.file.Path evidenceDirectory(Minecraft minecraft) {
        var path = java.nio.file.Path.of(System.getProperty("ae2lo.probe.reportDir",
                System.getProperty("ae2lo.probe.output", minecraft.gameDirectory.getAbsolutePath())))
                .toAbsolutePath().normalize();
        try { Files.createDirectories(path); }
        catch (java.io.IOException error) { throw new IllegalStateException("Cannot create probe evidence directory", error); }
        return path;
    }

    private static void onScreenshotComplete(Minecraft minecraft, String fileName, net.minecraft.network.chat.Component message) {
        var path = evidenceDirectory(minecraft).resolve("screenshots").resolve(fileName);
        if (Files.isRegularFile(path)) {
            CAPTURED.add(fileName);
            writeHiddenWindowState(minecraft);
            if (!UI_SCREENSHOT.equals(fileName)) ProbeState.screenshotCompleted = fileName;
            LOG.info("AE2LO native frame saved {}: {}", path.toAbsolutePath(), message.getString());
        } else {
            LOG.error("AE2LO native frame capture failed {}: {}", path.toAbsolutePath(), message.getString());
        }
    }
}
