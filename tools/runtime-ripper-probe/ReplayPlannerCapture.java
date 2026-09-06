import com.example.ae2lightoptimizer.crafting.CraftingTakeoverPolicy;
import com.example.ae2lightoptimizer.crafting.GlobalCraftingPlan;
import com.example.ae2lightoptimizer.crafting.GlobalCraftingPlanner;
import com.example.ae2lightoptimizer.crafting.GlobalPlanRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Replays a real captured catalog to verify optimizer-only planning without starting another world. */
public final class ReplayPlannerCapture {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Gson json = new GsonBuilder().setPrettyPrinting().create();
        var captured = json.fromJson(Files.readString(directory.resolve("planner-request-1.json")), GlobalPlanRequest.class);
        Map<String, String> aliases = json.fromJson(Files.readString(directory.resolve("planner-resources-1.json")),
                new TypeToken<Map<String, String>>() { }.getType());
        String target = find(aliases, "minecraft:netherite_ingot");
        String scrap = find(aliases, "minecraft:netherite_scrap");
        String gold = find(aliases, "minecraft:gold_ingot");
        long order = 1_000_000L;
        var request = new GlobalPlanRequest(target, order, captured.availableStock(), captured.patterns(),
                captured.emittableResources(), false, captured.reservedCycles(), captured.budget());
        long began = System.nanoTime();
        GlobalCraftingPlan plan = new GlobalCraftingPlanner().plan(request);
        double elapsedMillis = (System.nanoTime() - began) / 1_000_000.0;
        require(plan.solved(), "Captured catalog optimizer-only request failed: " + plan.status());
        require(!plan.cyclic(), "Unused automatic reverse recipes must not require a ring terminal");
        require(plan.missingStock().isEmpty(), "Unexpected missing stock");
        require(plan.requiredStock().equals(Map.of(scrap, 4 * order, gold, 4 * order)), "Incorrect raw material balance");
        require(plan.patternCounts().values().stream().mapToLong(Long::longValue).sum() == order,
                "Unexpected recipe application count");
        var policy = new CraftingTakeoverPolicy(false, true);
        var owner = policy.ownerFor(CraftingTakeoverPolicy.GraphKind.ACYCLIC);
        require(owner == CraftingTakeoverPolicy.TakeoverOwner.SUPERCOMPUTING_INTERFACE,
                "Optimizer-only ownership changed");
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", true);
        result.put("kind", "offline production-planner replay of actual in-game captured catalog");
        result.put("boundary", "Planning compatibility only; this replay does not submit a native CPU job.");
        result.put("target", aliases.get(target));
        result.put("order", order);
        result.put("capturedPatterns", captured.patterns().size());
        Path productionJar = Path.of(GlobalCraftingPlanner.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        result.put("productionJar", productionJar.toString());
        result.put("productionSha256", java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(productionJar))));
        result.put("ringTerminalOnline", false);
        result.put("optimizerOnline", true);
        result.put("owner", owner);
        result.put("elapsedMillis", elapsedMillis);
        result.put("plan", plan);
        Files.writeString(directory.resolve("optimizer-only-captured-replay.json"), json.toJson(result));
        System.out.println("PASS: actual catalog, optimizer only, 1,000,000 netherite ingots, exact 4,000,000 scrap + gold; "
                + elapsedMillis + " ms");
    }

    private static String find(Map<String, String> aliases, String item) {
        return aliases.entrySet().stream().filter(entry -> entry.getValue().equals(item))
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Captured aliases do not contain " + item + ": " + aliases));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
