import com.example.ae2lightoptimizer.factory.FactoryMachine;
import com.example.ae2lightoptimizer.factory.FactoryProgram;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
/** JDK-only allocation probe: a yielding logistics loop inside 32 saved call frames. */
public final class FactoryPerf {
    private static volatile long sink;
    private static final class Host implements FactoryMachine.Host {
        @Override public long count(String tag, String resource) { return 0; }
        @Override public long transfer(boolean get, String resource, String tag, String face, long limit) {
            sink++;
            return 0;
        }
        @Override public void pulse(String tag, long ticks) {}
    }
    public static void main(String[] ignored) {
        var code = new ArrayList<FactoryProgram.Instruction>();
        var functions = new HashMap<String, Integer>();
        for (int i = 0; i < 32; i++) {
            functions.put("f" + i, i + 1);
            code.add(new FactoryProgram.Instruction(FactoryProgram.Op.CALL, "f" + i, 0, 0, 1));
        }
        for (int i = 0; i < 100; i++) {
            for (var op : List.of(FactoryProgram.Op.GET, FactoryProgram.Op.PUT)) {
                code.add(new FactoryProgram.Instruction(op, "minecraft:iron_ingot\u001fA\u001f\u001f0", 0, 64, 1));
            }
        }
        code.add(new FactoryProgram.Instruction(FactoryProgram.Op.WAIT, "", 0, 1, 1));
        code.add(new FactoryProgram.Instruction(FactoryProgram.Op.JUMP, "", 32, 0, 1));
        var machine = new FactoryMachine(new FactoryProgram("", "", Set.of(), code, functions, false));
        var host = new Host();
        for (int i = 0; i < 2000; i++) machine.tick(host);
        if (!machine.error().isEmpty()) throw new AssertionError(machine.error());
        var metrics = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long allocated = metrics.getThreadAllocatedBytes(thread), started = System.nanoTime();
        for (int i = 0; i < 5000; i++) machine.tick(host);
        long bytes = metrics.getThreadAllocatedBytes(thread) - allocated;
        long nanos = System.nanoTime() - started;
        System.out.println("{\"ticks\":5000,\"instructionsPerTick\":202,\"callDepth\":32,\"allocatedBytes\":"
                + bytes + ",\"elapsedNanos\":" + nanos + ",\"stopped\":" + machine.stopped()
                + ",\"error\":\"" + machine.error() + "\"}");
    }
}
