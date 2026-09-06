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
    private static final long SHUTDOWN_SETTLE_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
    private static boolean shutdownPreparationStarted;
    private static boolean stopRequested;
    private static volatile long savedAtNanos;
    private static volatile int savedAtServerTick;
    private static boolean oldPauseOnLostFocus;
    private static int oldRenderDistance;
    private static int oldSimulationDistance;
    private static net.minecraft.client.tutorial.TutorialSteps oldTutorialStep;
    private static net.minecraft.client.GraphicsPreset oldGraphicsPreset;

    public ClientBootstrap() {
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::onClientTick);
        LOG.info("AE2LO client probe bootstrap armed; only a brand-new {} world can be created", WORLD_PREFIX);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
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

    /** Timing experiment for vanilla chunk unload shutdown; the server keeps ticking throughout. */
    private static void advanceNormalShutdown(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || stopRequested) return;
        if (!shutdownPreparationStarted) {
            shutdownPreparationStarted = true;
            shutdownEvent(minecraft, "prepare_requested", "world=" + server.getWorldData().getLevelName());
            server.execute(() -> {
                if (!server.getWorldData().getLevelName().startsWith(WORLD_PREFIX)) {
                    shutdownEvent(minecraft, "prepare_refused", "Integrated world changed; auto-exit remains disabled");
                    return;
                }
                try {
                    var level = server.overworld();
                    // These are exactly the nine temporary tickets installed by RuntimeRipperProbe.start.
                    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                        boolean removed = level.setChunkForced(x, z, false);
                        shutdownEvent(minecraft, "ticket_released", "chunk=" + x + "," + z + " removed=" + removed);
                    }
                    shutdownEvent(minecraft, "save_begin", "serverTick=" + server.getTickCount());
                    long began = System.nanoTime();
                    boolean saved = server.saveEverything(false, true, true);
                    shutdownEvent(minecraft, "save_complete", "saved=" + saved + " elapsedMillis="
                            + (System.nanoTime() - began) / 1_000_000 + " serverTick=" + server.getTickCount());
                    savedAtServerTick = server.getTickCount();
                    savedAtNanos = System.nanoTime();
                    shutdownEvent(minecraft, "settling", "Keep normal client/server ticking for 25 seconds after pre-save");
                } catch (Throwable error) {
                    LOG.error("AE2LO probe pre-save failed; leaving client open without forced exit", error);
                    shutdownEvent(minecraft, "prepare_failed", error.toString());
                }
            });
            return;
        }
        long completedAt = savedAtNanos;
        if (completedAt == 0 || System.nanoTime() - completedAt < SHUTDOWN_SETTLE_NANOS) return;
        stopRequested = true;
        shutdownEvent(minecraft, "settled", "elapsedMillis=" + (System.nanoTime() - completedAt) / 1_000_000
                + " serverTicks=" + (server.getTickCount() - savedAtServerTick));
        shutdownEvent(minecraft, "disconnect_begin", "Keep renderDistance=" + minecraft.options.renderDistance().get()
                + " simulationDistance=" + minecraft.options.simulationDistance().get()
                + "; native disconnect waits for integrated server shutdown");
        minecraft.disconnect(new TitleScreen(), false);
        shutdownEvent(minecraft, "disconnect_complete", "serverShutdown=" + server.isShutdown()
                + " integratedServerPresent=" + (minecraft.getSingleplayerServer() != null));
        if (!server.isShutdown() || minecraft.getSingleplayerServer() != null) {
            shutdownEvent(minecraft, "disconnect_incomplete", "Do not restore distances or force process exit while server remains alive");
            return;
        }
        minecraft.options.graphicsPreset().set(oldGraphicsPreset);
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
        oldGraphicsPreset = minecraft.options.graphicsPreset().get();
        oldRenderDistance = minecraft.options.renderDistance().get();
        oldSimulationDistance = minecraft.options.simulationDistance().get();
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.renderDistance().set(6);
        minecraft.options.simulationDistance().set(5);
        shutdownEvent(minecraft, "options_overridden", "renderDistance=" + oldRenderDistance + "->6 simulationDistance="
                + oldSimulationDistance + "->5 pauseOnLostFocus=" + oldPauseOnLostFocus + "->false; in-memory only");
        var settings = new LevelSettings(worldId, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
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
            Screenshot.grab(evidenceDirectory(minecraft).toFile(), fileName, minecraft.getMainRenderTarget(), 1,
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
            if (!UI_SCREENSHOT.equals(fileName)) ProbeState.screenshotCompleted = fileName;
            LOG.info("AE2LO native frame saved {}: {}", path.toAbsolutePath(), message.getString());
        } else {
            LOG.error("AE2LO native frame capture failed {}: {}", path.toAbsolutePath(), message.getString());
        }
    }
}
