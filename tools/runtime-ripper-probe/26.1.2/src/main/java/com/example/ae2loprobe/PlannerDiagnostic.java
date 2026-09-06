package com.example.ae2loprobe;

import appeng.api.stacks.AEKey;
import com.example.ae2lightoptimizer.crafting.GlobalCraftingPlan;
import com.example.ae2lightoptimizer.crafting.GlobalPlanRequest;
import com.example.ae2lightoptimizer.integration.Ae2GlobalCraftingOptimizer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.LoggerFactory;

/** Passive test-only snapshots; never alters the live planner request, result or service gates. */
public final class PlannerDiagnostic {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicInteger ATTEMPTS = new AtomicInteger();
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private PlannerDiagnostic() { }

    public static void begin() {
        if (Boolean.getBoolean("ae2lo.probe") && Core256MFixture.ENABLED) {
            CURRENT.set(new Context(ATTEMPTS.incrementAndGet()));
        }
    }

    public static void alias(AEKey key, String id) {
        var context = CURRENT.get();
        if (context != null) context.aliases.put(id, key.toString());
    }

    public static void request(GlobalPlanRequest request) {
        var context = CURRENT.get();
        if (context == null) return;
        write("planner-request-" + context.id + ".json", request);
        write("planner-resources-" + context.id + ".json", context.aliases);
    }

    public static void result(GlobalCraftingPlan plan) {
        var context = CURRENT.get();
        if (context == null) return;
        write("planner-result-" + context.id + ".json", plan);
        LoggerFactory.getLogger("AE2LO-Runtime-Probe").info(
                "Shared planner attempt {} status={} cyclic={} balanceIterations={} scheduleBatches={}",
                context.id, plan.status(), plan.cyclic(), plan.balanceIterations(), plan.scheduleBatches());
    }

    public static void outcome(Ae2GlobalCraftingOptimizer.OptimizationAttempt attempt) {
        var context = CURRENT.get();
        if (context == null) return;
        write("planner-bridge-" + context.id + ".json", Map.of("handled", attempt.handled(),
                "hasPlan", attempt.plan() != null, "bytes", attempt.plan() == null ? -1L : attempt.plan().bytes()));
        CURRENT.remove();
    }

    private static void write(String fileName, Object value) {
        try {
            var directory = Path.of(System.getProperty("ae2lo.probe.reportDir",
                    System.getProperty("ae2lo.probe.output", "ae2lo-probe-evidence"))).toAbsolutePath();
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(fileName), JSON.toJson(value), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            LoggerFactory.getLogger("AE2LO-Runtime-Probe").error("Cannot record passive planner snapshot", failure);
        }
    }

    private static final class Context {
        private final int id;
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private Context(int id) { this.id = id; }
    }
}
