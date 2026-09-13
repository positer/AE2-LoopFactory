package com.example.ae2lightoptimizer.factory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.IntFunction;

/** One operation's capability views. Resolve fallback faces only when traversal reaches them. */
public final class FactoryLazyPorts<P> implements Iterable<P> {
    private final int candidates;
    private final IntFunction<P> resolver;
    private final ArrayList<P> resolved = new ArrayList<>();
    private int nextCandidate;
    public FactoryLazyPorts(int candidates, IntFunction<P> resolver) {
        if (candidates < 0) throw new IllegalArgumentException("Negative candidate count");
        this.candidates = candidates;
        this.resolver = resolver;
    }
    private boolean ensure(int index) {
        while (resolved.size() <= index && nextCandidate < candidates) {
            P port = resolver.apply(nextCandidate++);
            if (port != null) resolved.add(port);
        }
        return index < resolved.size();
    }
    public P first() { return ensure(0) ? resolved.getFirst() : null; }
    @Override public Iterator<P> iterator() {
        return new Iterator<>() {
            private int index;
            public boolean hasNext() { return ensure(index); }
            public P next() {
                if (!hasNext()) throw new NoSuchElementException();
                return resolved.get(index++);
            }
        };
    }
}
