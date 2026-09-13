package com.example.ae2lightoptimizer.factory;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Dimension-local block positions; the owning factory provides dimension and network membership. */
public final class FactoryTags {
    private final Map<String, LinkedHashSet<Long>> bindings = new LinkedHashMap<>();

    public void reconcile(Collection<String> imported) {
        bindings.keySet().retainAll(imported);
        imported.stream().sorted().forEach(tag -> bindings.computeIfAbsent(tag, ignored -> new LinkedHashSet<>()));
    }

    public boolean tag(String tag, long position, Predicate<Long> belongsToNetwork) {
        var positions = bindings.get(tag);
        return positions != null && belongsToNetwork.test(position) && positions.add(position);
    }

    public boolean remove(String tag, long position) {
        var positions = bindings.get(tag);
        return positions != null && positions.remove(position);
    }

    public List<String> names() { return List.copyOf(bindings.keySet()); }
    public Set<Long> positions(String tag) {
        var positions = bindings.get(tag);
        return positions == null ? Set.of() : Set.copyOf(positions);
    }
    public Map<String, List<Long>> snapshot() {
        var result = new LinkedHashMap<String, List<Long>>();
        bindings.forEach((tag, positions) -> result.put(tag, List.copyOf(positions)));
        return result;
    }
    public static FactoryTags restore(Map<String, List<Long>> snapshot) {
        var result = new FactoryTags();
        for (var entry : snapshot.entrySet()) {
            if(!validName(entry.getKey()))throw new IllegalArgumentException("Invalid saved factory tag");
            result.bindings.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return result;
    }
    public static boolean validName(String value) {
        return value!=null && !value.isBlank() && value.length()<=128 && !FactoryCompiler.RESERVED.contains(value) && !value.matches("[PO][0-9]+")
                && !value.equals("P") && !value.equals("O")
                && value.chars().noneMatch(c->Character.isISOControl(c)||"<>=|()".indexOf(c)>=0)
                && !value.contains(" and ") && !value.contains(" or ");
    }

    /**
     * Splits a machine tag expression such as {@code A&B} into its parts. The union is resolved once
     * per instruction, so a machine bound to several of the listed tags is still handled a single time.
     */
    public static List<String> expression(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Missing machine tag");
        var parts = new ArrayList<String>();
        for (String part : text.split("&", -1)) {
            String name = part.strip();
            if (name.isEmpty()) throw new IllegalArgumentException("Invalid machine tag expression: " + text);
            parts.add(name);
        }
        return List.copyOf(parts);
    }

    public static boolean compound(String text) {
        return text != null && text.indexOf('&') >= 0;
    }
}
