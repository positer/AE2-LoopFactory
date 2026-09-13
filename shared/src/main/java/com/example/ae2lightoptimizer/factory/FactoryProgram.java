package com.example.ae2lightoptimizer.factory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, version-independent factory bytecode. Source line numbers survive compilation. */
public record FactoryProgram(String source, String name, Set<String> tags,
        List<Instruction> instructions, Map<String, Integer> functions, boolean recipe) {
    public FactoryProgram {
        tags = Set.copyOf(tags);
        instructions = List.copyOf(instructions);
        functions = Map.copyOf(functions);
    }

    public enum Op { GET, PUT, TEST, JUMP, WAIT, REDSTONE, CALL, RETURN, DONE, TIMER, FORGET, COMPLETE }
    public record Instruction(Op op, String argument, int target, long amount, int line, boolean must) {
        public Instruction(Op op,String argument,int target,long amount,int line){this(op,argument,target,amount,line,false);}
    }

    public static final class CompileException extends IllegalArgumentException {
        private final int line;
        public CompileException(int line, String message) {
            super("Line " + line + ": " + message);
            this.line = line;
        }
        public int line() { return line; }
    }
}
