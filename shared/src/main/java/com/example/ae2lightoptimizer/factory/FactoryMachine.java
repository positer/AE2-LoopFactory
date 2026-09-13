package com.example.ae2lightoptimizer.factory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resumable interpreter. Host changes and snapshots are saved together by the owning block entity. */
public final class FactoryMachine {
    /** A host can suspend before mutation when a network or target chunk is unavailable. */
    public static final class Pause extends RuntimeException {
        public Pause(String reason) { super(reason); }
    }
    public interface Host {
        long count(String tag, String resource);
        /** get declares a source and returns zero; put returns only the amount actually transferred. */
        long transfer(boolean get, String resource, String tag, String face, long limit);
        /** Channel aware overloads; a host that ignores channels keeps the legacy behaviour. */
        default long transfer(boolean get, String resource, String tag, String face, long limit, int channel) {
            return transfer(get, resource, tag, face, limit);
        }
        default void declare(String resource,String tag,String face,long limit,boolean must){transfer(true,resource,tag,face,limit);}
        default void declare(String resource,String tag,String face,long limit,boolean must,int channel){
            declare(resource,tag,face,limit,must);
        }
        default long requiredTransfer(String resource,String tag,String face,long limit){return 0;}
        /** Inherited GET MUST debt belongs to the same channel as the corresponding PUT. */
        default long requiredTransfer(String resource,String tag,String face,long limit,int channel){
            return requiredTransfer(resource,tag,face,limit);
        }
        /** Host persists pulse targets and their remaining duration, independently of later tag edits. */
        void pulse(String tag, long ticks);
        default void forget(String tag) { throw new IllegalStateException("Host does not support FORGET"); }
        default long gameTime() { return 0; }
        default boolean signal() { return false; }
        default boolean recipeComplete() { return false; }
    }
    public record Snapshot(int pc, long delay, List<Integer> returns, boolean stopped, String error,
            long recipeInstructions, long elapsed, boolean lastSignal,boolean pendingTransfer,long transferRemaining) {
        public Snapshot { returns = List.copyOf(returns); }
        public Snapshot(int pc,long delay,List<Integer> returns,boolean stopped,String error,long recipeInstructions) {
            this(pc,delay,returns,stopped,error,recipeInstructions,0,false,false,0);
        }
    }
    private record ControlState(int pc, List<Integer> returns, long progress) {}
    private static final int MAX_CALL_DEPTH = 64;
    private static final int INSTRUCTIONS_PER_TICK = 4096;
    private static final long RECIPE_INSTRUCTION_LIMIT = 1_000_000;
    private final FactoryProgram program;
    private final List<Integer> returns = new ArrayList<>();
    private int pc;
    private long delay;
    private boolean stopped;
    private String error = "";
    private long recipeInstructions;
    private String waitingReason = "";
    private long elapsed;
    private boolean lastSignal;
    private boolean pendingTransfer;
    private long transferRemaining;

    public FactoryMachine(FactoryProgram program) { this.program = program; }

    public static FactoryMachine restore(FactoryProgram program, Snapshot snapshot) {
        var machine = new FactoryMachine(program);
        if (snapshot.pc < 0 || snapshot.pc >= program.instructions().size() || snapshot.delay < 0
                || snapshot.returns.size() > MAX_CALL_DEPTH || snapshot.recipeInstructions < 0 || snapshot.elapsed < 0 || snapshot.transferRemaining < 0
                || snapshot.returns.stream().anyMatch(i -> i < 0 || i >= program.instructions().size()))
            throw new IllegalArgumentException("Invalid factory continuation");
        machine.pc = snapshot.pc;
        machine.delay = snapshot.delay;
        machine.returns.addAll(snapshot.returns);
        machine.stopped = snapshot.stopped;
        machine.error = snapshot.error;
        machine.recipeInstructions = snapshot.recipeInstructions;
        machine.elapsed = snapshot.elapsed; machine.lastSignal = snapshot.lastSignal;
        machine.pendingTransfer=snapshot.pendingTransfer;machine.transferRemaining=snapshot.transferRemaining;
        if(machine.pendingTransfer&&program.instructions().get(machine.pc).op()!=FactoryProgram.Op.PUT)throw new IllegalArgumentException("Invalid pending transfer continuation");
        return machine;
    }

    public Snapshot snapshot() { return new Snapshot(pc, delay, returns, stopped, error, recipeInstructions,elapsed,lastSignal,pendingTransfer,transferRemaining); }
    public boolean stopped() { return stopped; }
    public String error() { return error; }
    public String waitingReason() { return waitingReason; }

    private int effectiveChannel(int lexicalChannel) {
        if (lexicalChannel != 0) return lexicalChannel;
        // A function's default-channel instructions inherit their caller. Existing persisted return
        // addresses identify every call site, so waiting/reloaded jobs need no extra snapshot field.
        for (int i = returns.size() - 1; i >= 0; i--) {
            int callPc = returns.get(i) - 1;
            if (callPc < 0) continue;
            var call = program.instructions().get(callPc);
            if (call.op() == FactoryProgram.Op.CALL && call.target() != 0) return call.target();
        }
        return 0;
    }

    public void tick(Host host) {
        if (stopped) return;
        waitingReason = "";
        boolean sfm=!program.instructions().isEmpty() && program.instructions().getFirst().op()==FactoryProgram.Op.COMPLETE;
        boolean pulse=false;
        if(sfm) {elapsed=elapsed==Long.MAX_VALUE?1:elapsed+1;boolean signal=host.signal();pulse=signal&&!lastSignal;lastSignal=signal;}
        if (delay > 0 && --delay > 0) return;
        Set<ControlState> visited = new HashSet<>();
        long progress = 0;
        try {
            for (int steps = 0; steps < INSTRUCTIONS_PER_TICK; steps++) {
                if (!visited.add(new ControlState(pc, List.copyOf(returns), progress)))
                    throw new IllegalStateException("Loop makes no progress; use wait or redstone for at least 1 tick");
                if(program.recipe()&&++recipeInstructions>RECIPE_INSTRUCTION_LIMIT)throw new IllegalStateException("Recipe execution exceeded its finite instruction budget");
                var instruction = program.instructions().get(pc);
                switch (instruction.op()) {
                    case GET, PUT -> {
                        String[] arguments = instruction.argument().split(instruction.argument().contains("\u001f")?"\u001f":" ", -1);
                        int channel = effectiveChannel(arguments.length > 3 ? Integer.parseInt(arguments[3].strip()) : 0);
                        if(instruction.op()==FactoryProgram.Op.GET) {
                            // Channel 0 keeps the legacy signature so existing hosts stay authoritative.
                            if(channel==0)host.declare(arguments[0],arguments[1],arguments[2],instruction.amount(),instruction.must());
                            else host.declare(arguments[0],arguments[1],arguments[2],instruction.amount(),instruction.must(),channel);
                            pc++;break;
                        }
                        if(pendingTransfer&&!instruction.must()) {
                            // Old snapshots may include another channel's GET MUST debt. Only reduce
                            // an inherited remainder; explicit PUT MUST and already moved stock stay intact.
                            long required=host.requiredTransfer(arguments[0],arguments[1],arguments[2],instruction.amount(),channel);
                            transferRemaining=Math.min(transferRemaining,required);
                            if(transferRemaining==0) {
                                pendingTransfer=false;
                                pc++;break; // This PUT already started: never replay it as an ordinary transfer.
                            }
                        }
                        if(!pendingTransfer) {
                            long required=instruction.must()?instruction.amount():host.requiredTransfer(arguments[0],arguments[1],arguments[2],instruction.amount(),channel);
                            if(required>0){pendingTransfer=true;transferRemaining=required;}
                        }
                        long requested=pendingTransfer?transferRemaining:instruction.amount();
                        long moved=channel==0
                                ?host.transfer(false,arguments[0],arguments[1],arguments[2],requested)
                                :host.transfer(false,arguments[0],arguments[1],arguments[2],requested,channel);
                        if(moved<0||moved>requested)throw new IllegalStateException("Invalid committed transfer amount");
                        if(moved>0)progress++;
                        if(pendingTransfer)transferRemaining-=moved;
                        if(pendingTransfer&&transferRemaining>0) {
                            if(program.recipe())recipeInstructions--;
                            waitingReason="Waiting for transfer resources or destination capacity";return;
                        }
                        pendingTransfer=false;
                        pc++;
                    }
                    case TEST -> pc = FactoryExpression.evaluate(instruction.argument(), host::count) != 0 ? pc + 1 : instruction.target();
                    case TIMER -> {
                        String[] args=instruction.argument().split(" ");
                        long offset=Long.parseLong(args[1]);
                        long clock=args[0].equals("global")?host.gameTime():elapsed;
                        boolean due=args[0].equals("pulse")?pulse:clock>=offset && Math.floorMod(clock-offset,instruction.amount())==0;
                        pc=due?pc+1:instruction.target();
                    }
                    case FORGET -> {host.forget(instruction.argument().isEmpty()?null:instruction.argument());pc++;}
                    case COMPLETE -> {if(program.recipe()&&host.recipeComplete()){stopped=true;return;}pc++;}
                    case JUMP -> pc = instruction.target();
                    case WAIT, REDSTONE -> {
                        if (instruction.op() == FactoryProgram.Op.REDSTONE) host.pulse(instruction.argument(), instruction.amount());
                        delay = instruction.amount();
                        pc++;
                        return;
                    }
                    case CALL -> {
                        if (returns.size() >= MAX_CALL_DEPTH) throw new IllegalStateException("Function recursion exceeds 64 frames");
                        returns.add(pc + 1);
                        pc = program.functions().get(instruction.argument());
                    }
                    case RETURN -> {
                        if (returns.isEmpty()) throw new IllegalStateException("Return outside function");
                        pc = returns.removeLast();
                    }
                    case DONE -> { stopped = true; return; }
                }
            }
            // Never silently defer or skip an instruction when the same-tick watchdog is exhausted.
            throw new IllegalStateException("Execution did not yield within 4096 instructions; insert wait 1 tick");
        } catch (Pause pause) {
            waitingReason = pause.getMessage();
            if(program.recipe())recipeInstructions--;
        } catch (RuntimeException failure) {
            stopped = true;
            int line = program.instructions().get(Math.min(pc, program.instructions().size() - 1)).line();
            error = "Line " + line + ": " + failure.getMessage();
        }
    }
}
