package com.example.ae2lightoptimizer.factory;

import java.util.ArrayList;
import java.util.List;

/** A factory's temporary state consists of source declarations, never extracted resources. */
public final class FactoryRoutes {
    public record Source(String selector, String tag, String face, long remaining,boolean must,int channel) {
        public Source(String selector,String tag,String face,long remaining,boolean must){this(selector,tag,face,remaining,must,0);}
        public Source(String selector,String tag,String face,long remaining){this(selector,tag,face,remaining,false);}
        public Source {
            FactorySelector.parse(selector);
            if (tag == null || tag.isBlank() || face == null || remaining < 0) throw new IllegalArgumentException("Invalid source declaration");
        }
    }
    @FunctionalInterface public interface Mover {
        long move(Source source, String destinationSelector, String destinationTag, String destinationFace, long limit);
    }
    private final List<Source> sources = new ArrayList<>();
    public void declare(String selector,String tag,String face,long limit){declare(selector,tag,face,limit,false);}
    public void declare(String selector, String tag, String face, long limit,boolean must) {
        declare(selector, tag, face, limit, must, 0);
    }
    /** Declares a source inside one logistics channel; channels never share a declaration. */
    public void declare(String selector, String tag, String face, long limit, boolean must, int channel) {
        if (limit < 1) throw new IllegalArgumentException("Source limit must be positive");
        String canonical = FactorySelector.parse(selector).canonical();
        // Re-declaring the same source refreshes its budget; a loop cannot accumulate phantom inventories.
        sources.removeIf(source -> source.selector.equals(canonical) && source.tag.equals(tag) && source.face.equals(face)
                && source.channel == channel);
        sources.add(new Source(canonical, tag, face, limit,must,channel));
    }
    public long output(String selector,String tag,String face,long limit,Mover mover){return output(selector,tag,face,limit,source->true,mover);}
    public long output(String selector, String tag, String face, long limit,java.util.function.Predicate<Source> include,Mover mover) {
        long moved = 0;
        for (int i = 0; i < sources.size() && moved < limit; i++) {
            Source source = sources.get(i);
            if(!include.test(source))continue;
            long allowed = Math.min(limit - moved, source.remaining);
            if (allowed == 0) continue;
            long committed = mover.move(source, selector, tag, face, allowed);
            if (committed < 0 || committed > allowed) throw new IllegalStateException("Invalid routing transfer result");
            sources.set(i, new Source(source.selector, source.tag, source.face, source.remaining - committed,source.must,source.channel));
            moved += committed;
        }
        return moved;
    }
    public void forget(String tag) { if (tag == null) sources.clear(); else sources.removeIf(source -> source.tag.equals(tag)); }
    public List<Source> snapshot() { return List.copyOf(sources); }
    public static FactoryRoutes restore(List<Source> snapshot) {
        var routes = new FactoryRoutes();
        for (var source : snapshot) routes.sources.add(new Source(source.selector, source.tag, source.face, source.remaining,source.must,source.channel));
        return routes;
    }
}
