package com.example.ae2lightoptimizer.crafting;

/** Assigns each graph class to exactly one active network service. */
public record CraftingTakeoverPolicy(boolean ringTerminalOnline, boolean optimizerOnline) {
    public boolean hasActiveService() {
        return ringTerminalOnline || optimizerOnline;
    }

    public TakeoverOwner ownerFor(GraphKind graphKind) {
        return switch (graphKind) {
            case ACYCLIC -> optimizerOnline
                    ? TakeoverOwner.SUPERCOMPUTING_INTERFACE
                    : TakeoverOwner.ORIGINAL_AE2;
            case CYCLIC -> ringTerminalOnline
                    ? TakeoverOwner.RECIPE_RING_TERMINAL
                    : TakeoverOwner.ORIGINAL_AE2;
        };
    }

    public boolean accepts(GraphKind graphKind) {
        return ownerFor(graphKind) != TakeoverOwner.ORIGINAL_AE2;
    }

    public enum GraphKind {
        ACYCLIC,
        CYCLIC
    }

    public enum TakeoverOwner {
        ORIGINAL_AE2,
        RECIPE_RING_TERMINAL,
        SUPERCOMPUTING_INTERFACE
    }
}
