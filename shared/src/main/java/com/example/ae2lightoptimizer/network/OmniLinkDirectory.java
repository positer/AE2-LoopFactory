package com.example.ae2lightoptimizer.network;

import java.text.Normalizer;
import java.util.*;

/** World-local, stable link identifiers. A link name is never interpreted as a path or command. */
public final class OmniLinkDirectory {
    public static final int MAX_NAME_LENGTH = 48;
    /** Public links are visible to every player; private links are visible only to their owner. */
    public enum Visibility { PUBLIC, PRIVATE }
    /**
     * A named channel. {@code owner} is the creating player's UUID for private links and empty for public
     * links; legacy saves carry neither field and therefore load as public links without an owner.
     */
    public record Link(String id, String name, String owner, Visibility visibility) {
        public Link(String id, String name) { this(id, name, "", Visibility.PUBLIC); }
        public boolean visibleTo(String requester) {
            return visibility == Visibility.PUBLIC || (!owner.isEmpty() && owner.equals(requester));
        }
    }
    private final Map<String, Link> links = new LinkedHashMap<>();
    private final Map<String, Link> names = new HashMap<>();
    private List<Link> ordered;

    public OmniLinkDirectory() {}
    public OmniLinkDirectory(List<Link> saved) {
        for (var link : saved) {
            UUID.fromString(link.id());
            String name = validateName(link.name());
            var entry = new Link(link.id(), name, validateOwner(link.owner(), link.visibility()), link.visibility());
            if (links.putIfAbsent(entry.id(), entry) != null || names.putIfAbsent(key(entry), entry) != null)
                throw new IllegalArgumentException("Duplicate omni link");
        }
    }
    private static String key(Link link) {
        String name = link.name().toLowerCase(Locale.ROOT);
        // Public names stay a single global namespace; private names are scoped to their owner so two
        // players may both keep a private channel of the same name without seeing each other.
        return link.visibility() == Visibility.PUBLIC ? "p:" + name : "r:" + link.owner() + ':' + name;
    }
    private static String validateOwner(String owner, Visibility visibility) {
        String value = owner == null ? "" : owner.strip();
        if (visibility == Visibility.PRIVATE) {
            UUID.fromString(value); // a private link without a valid owner would be invisible to everyone
            return value;
        }
        return value.isEmpty() ? "" : UUID.fromString(value).toString();
    }
    public static String validateName(String input) {
        String name = Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
                || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7 || Character.getType(c) == Character.FORMAT
                    || Character.getType(c) == Character.SURROGATE || Character.getType(c) == Character.LINE_SEPARATOR
                    || Character.getType(c) == Character.PARAGRAPH_SEPARATOR)) throw new IllegalArgumentException("Invalid link name");
        return name;
    }
    public Link createOrGet(String input) { return createOrGet(input, Visibility.PUBLIC, ""); }
    public Link createOrGet(String input, Visibility visibility, String owner) {
        String name = validateName(input);
        var link = new Link(UUID.randomUUID().toString(), name, validateOwner(owner, visibility), visibility);
        var existing = names.get(key(link));
        if (existing != null) return existing;
        links.put(link.id(), link); names.put(key(link), link); ordered = null;
        return link;
    }
    public int size() { return links.size(); }
    public Link get(String id) { return links.get(id); }
    public List<Link> links() { return List.copyOf(links.values()); }
    /** Links the given player may see: every public link plus that player's own private links. */
    public List<Link> visibleTo(String requester) {
        return links.values().stream().filter(link -> link.visibleTo(requester)).toList();
    }
    public boolean canSee(String id, String requester) {
        var link = links.get(id);
        return link != null && link.visibleTo(requester);
    }
    public List<Link> sorted() {
        if (ordered == null) ordered = links.values().stream().sorted(ORDER).toList();
        return ordered;
    }
    /** Visible links in the same stable order the terminal uses. */
    public List<Link> sortedVisible(String requester) {
        return visibleTo(requester).stream().sorted(ORDER).toList();
    }
    private static final Comparator<Link> ORDER =
            Comparator.comparing(Link::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Link::id);
}
