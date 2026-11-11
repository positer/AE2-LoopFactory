package com.example.ae2lightoptimizer.network;

import java.text.Normalizer;
import java.util.*;

/** World-local, stable link identifiers. A link name is never interpreted as a path or command. */
public final class OmniLinkDirectory {
    public static final int MAX_NAME_LENGTH = 48;
    public record Link(String id, String name) {}
    private final Map<String, Link> links = new LinkedHashMap<>();
    private final Map<String, Link> names = new HashMap<>();
    private List<Link> ordered;

    public OmniLinkDirectory() {}
    public OmniLinkDirectory(List<Link> saved) {
        for (var link : saved) {
            UUID.fromString(link.id());
            String name = validateName(link.name());
            var entry = new Link(link.id(), name);
            if (links.putIfAbsent(entry.id(), entry) != null || names.putIfAbsent(key(name), entry) != null)
                throw new IllegalArgumentException("Duplicate omni link");
        }
    }
    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
    public static String validateName(String input) {
        String name = Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
                || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7 || Character.getType(c) == Character.FORMAT
                    || Character.getType(c) == Character.SURROGATE || Character.getType(c) == Character.LINE_SEPARATOR
                    || Character.getType(c) == Character.PARAGRAPH_SEPARATOR)) throw new IllegalArgumentException("Invalid link name");
        return name;
    }
    public Link createOrGet(String input) {
        String name = validateName(input);
        var existing = names.get(key(name));
        if (existing != null) return existing;
        var link = new Link(UUID.randomUUID().toString(), name);
        links.put(link.id(), link); names.put(key(name), link); ordered = null;
        return link;
    }
    public int size() { return links.size(); }
    public Link get(String id) { return links.get(id); }
    public List<Link> links() { return List.copyOf(links.values()); }
    public List<Link> sorted() {
        if (ordered == null) ordered = links.values().stream().sorted(Comparator.comparing(Link::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Link::id)).toList();
        return ordered;
    }
}
