package com.example.ae2lightoptimizer.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.function.IntPredicate;

/**
 * Resource selection is a left-to-right expression over operands. {@code &} merges two operands and
 * {@code !} subtracts the right operand, so {@code A&B!C} means {@code (A ∪ B) \ C} and parentheses
 * override that order: {@code (A&B)!(C&D)}. Operands accept {@code *} for any number of characters
 * and {@code ?} for exactly one character, and {@code #namespace:path} selects every resource that
 * carries that item tag. No regex from source is executed.
 */
public final class FactorySelector {
    /** {@code P}: every expected input material of the bound recipe. */
    public static final int ALL_INPUTS = Integer.MAX_VALUE;
    /** {@code O}: every expected output product of the bound recipe. */
    public static final int ALL_OUTPUTS = Integer.MIN_VALUE;
    /** Resolves an item tag operand; the generation adapter supplies the registry-backed check. */
    @FunctionalInterface
    public interface TagLookup {
        TagLookup NONE = (tag, keyType, id) -> false;
        boolean contains(String tag, String keyType, String id);
    }
    /**
     * Resolves the complete recipe sets {@code P} and {@code O}. A recipe-free pattern must reject them,
     * so the default resolver reports the missing recipe instead of silently matching nothing.
     */
    @FunctionalInterface
    public interface RecipeSet {
        RecipeSet NONE = (kind, keyType, id) -> {
            throw new IllegalArgumentException((kind == ALL_INPUTS ? "P" : "O") + " requires a recipe-bound pattern");
        };
        boolean contains(int kind, String keyType, String id);
    }
    private record Member(String base, String type, String resource, int parameter, boolean tag) {}

    private sealed interface Node permits Leaf, Union, Difference, Many {}
    private record Leaf(Member member) implements Node {}
    private record Union(Node left, Node right) implements Node {}
    private record Difference(Node left, Node right) implements Node {}
    /** A comma separated exclusion list: {@code !(B,C,D)}. */
    private record Many(List<Node> children) implements Node { Many { children = List.copyOf(children); } }

    private final Node root;
    private final String canonical;

    private FactorySelector(Node root, String canonical) {
        this.root = root;
        this.canonical = canonical;
    }

    public static FactorySelector parse(String text) {
        if (text == null || text.length() > 4096) throw new IllegalArgumentException("Resource selector exceeds 4096 characters");
        var parser = new Parser(text);
        Node node = parser.select(0);
        parser.spaces();
        if (parser.cursor != text.length()) throw parser.error("Unexpected selector suffix");
        return new FactorySelector(node, render(node));
    }

    /** type is canonical (minecraft::item, minecraft::fluid, neoforge::fe, or addon::type). */
    public boolean matches(String keyType, String id) {
        return matches(keyType, id, ignored -> { throw new IllegalArgumentException("Recipe parameter requires assigned materials"); });
    }

    public boolean matches(String keyType, String id, IntPredicate parameters) {
        return matches(keyType, id, parameters, TagLookup.NONE);
    }

    public boolean matches(String keyType, String id, IntPredicate parameters, TagLookup tags) {
        return matches(keyType, id, parameters, tags, RecipeSet.NONE);
    }

    public boolean matches(String keyType, String id, IntPredicate parameters, TagLookup tags, RecipeSet sets) {
        return matches(root, keyType, id, parameters, tags == null ? TagLookup.NONE : tags, sets == null ? RecipeSet.NONE : sets);
    }

    private static boolean matches(Node node, String keyType, String id, IntPredicate parameters, TagLookup tags, RecipeSet sets) {
        if (node instanceof Leaf leaf) return memberMatches(leaf.member(), keyType, id, parameters, tags, sets);
        if (node instanceof Union union) return matches(union.left(), keyType, id, parameters, tags, sets) || matches(union.right(), keyType, id, parameters, tags, sets);
        if (node instanceof Difference difference)
            return matches(difference.left(), keyType, id, parameters, tags, sets) && !matches(difference.right(), keyType, id, parameters, tags, sets);
        for (var child : ((Many) node).children()) if (matches(child, keyType, id, parameters, tags, sets)) return true;
        return false;
    }

    private static boolean memberMatches(Member member, String keyType, String id, IntPredicate parameters, TagLookup tags, RecipeSet sets) {
        if (member.tag()) {
            if (!member.type().isEmpty() && !member.type().equals(keyType)) return false;
            return tags.contains(member.resource(), keyType, id);
        }
        if (member.parameter() == ALL_INPUTS || member.parameter() == ALL_OUTPUTS) return sets.contains(member.parameter(), keyType, id);
        if (member.parameter() != 0) return parameters.test(member.parameter());
        if (!member.type().isEmpty() && !member.type().equals(keyType)) return false;
        return glob(member.resource(), id);
    }

    public Set<Integer> parameters() {
        var result = new LinkedHashSet<Integer>();
        collectParameters(root, result);
        return Set.copyOf(result);
    }

    private static void collectParameters(Node node, Set<Integer> result) {
        if (node instanceof Leaf leaf) {
            int parameter = leaf.member().parameter();
            if (parameter != 0 && parameter != ALL_INPUTS && parameter != ALL_OUTPUTS) result.add(parameter);
            return;
        }
        if (node instanceof Union union) { collectParameters(union.left(), result); collectParameters(union.right(), result); return; }
        if (node instanceof Difference difference) { collectParameters(difference.left(), result); collectParameters(difference.right(), result); return; }
        for (var child : ((Many) node).children()) collectParameters(child, result);
    }

    /** True when the selector uses the complete recipe sets {@code P} or {@code O}. */
    public boolean usesRecipeSets() {
        return usesRecipeSets(root);
    }

    private static boolean usesRecipeSets(Node node) {
        if (node instanceof Leaf leaf) return leaf.member().parameter() == ALL_INPUTS || leaf.member().parameter() == ALL_OUTPUTS;
        if (node instanceof Union union) return usesRecipeSets(union.left()) || usesRecipeSets(union.right());
        if (node instanceof Difference difference) return usesRecipeSets(difference.left()) || usesRecipeSets(difference.right());
        for (var child : ((Many) node).children()) if (usesRecipeSets(child)) return true;
        return false;
    }

    /** Conservative overlap for resource declarations; concrete IDs include exclusion checks. */
    public boolean mayOverlap(FactorySelector other) {
        var mine = new ArrayList<Member>();
        var theirs = new ArrayList<Member>();
        collectMembers(root, mine);
        collectMembers(other.root, theirs);
        for (var left : mine) for (var right : theirs) if (membersMayOverlap(left, right)) return true;
        return false;
    }

    private static void collectMembers(Node node, List<Member> result) {
        if (node instanceof Leaf leaf) { result.add(leaf.member()); return; }
        if (node instanceof Union union) { collectMembers(union.left(), result); collectMembers(union.right(), result); return; }
        if (node instanceof Difference difference) { collectMembers(difference.left(), result); collectMembers(difference.right(), result); return; }
        for (var child : ((Many) node).children()) collectMembers(child, result);
    }

    private static boolean membersMayOverlap(Member mine, Member theirs) {
        if (!mine.type().isEmpty() && !theirs.type().isEmpty() && !mine.type().equals(theirs.type())) return false;
        if (mine.tag() || theirs.tag()) return true;
        if (mine.parameter() != 0 || theirs.parameter() != 0) return true;
        if (wildcard(mine.resource()) || wildcard(theirs.resource())) return true;
        return mine.resource().equals(theirs.resource());
    }

    private static boolean wildcard(String resource) {
        return resource.indexOf('*') >= 0 || resource.indexOf('?') >= 0;
    }

    /**
     * Constant-stack wildcard matching; never invokes a backtracking regex on factory code.
     * {@code *} matches any run of characters, {@code ?} matches exactly one character.
     */
    private static boolean glob(String pattern, String value) {
        int p=0,v=0,star=-1,retry=0;
        while(v<value.length()) {
            if(p<pattern.length() && pattern.charAt(p)=='*') {
                while(p<pattern.length() && pattern.charAt(p)=='*')p++;
                star=p;retry=v;
                if(p==pattern.length())return true;
            } else if(p<pattern.length() && (pattern.charAt(p)=='?' || pattern.charAt(p)==value.charAt(v))) {p++;v++;}
            else if(star>=0) {p=star;v=++retry;}
            else return false;
        }
        while(p<pattern.length() && pattern.charAt(p)=='*')p++;
        return p==pattern.length();
    }

    public void validateParameters(boolean recipe,int materialCount){validateParameters(recipe,materialCount,recipe?Integer.MAX_VALUE:0);}
    public void validateParameters(boolean recipe, int materialCount,int outputCount) {
        if (usesRecipeSets() && !recipe) throw new IllegalArgumentException("P and O require a recipe-bound pattern");
        if (usesRecipeSets() && (materialCount <= 0 || outputCount <= 0))
            throw new IllegalArgumentException("P and O require a recipe with at least one material and one output");
        for (int index : parameters()) {
            String name=index<0?"O"+(-index):"P"+index;
            if (!recipe) throw new IllegalArgumentException(name + " requires a recipe");
            int count=index<0?outputCount:materialCount;
            if (Math.abs(index)>count) throw new IllegalArgumentException(name + " exceeds the recipe's " + count + (index<0?" outputs":" materials"));
        }
    }

    public String canonical() {
        return canonical;
    }

    private static Member member(String base) {
        if (base.startsWith("#")) {
            int colon = base.indexOf(':', 1);
            if (colon < 2 || colon == base.length() - 1)
                throw new IllegalArgumentException("Item tag selector must be written as #namespace:path: " + base);
            // The tag keeps its own resource type open: the generation adapter resolves it against the
            // tag registry of whatever key type the operand is tested with, so mods and datapacks that
            // add item, fluid or other vanilla-backed tags work without a code change.
            return new Member(base, "", base.substring(1), 0, true);
        }
        int parameter = 0;
        if (base.equals("P") || base.equals("O")) return new Member(base, "", base, base.equals("P") ? ALL_INPUTS : ALL_OUTPUTS, false);
        if (base.startsWith("P") || base.startsWith("O")) {
            int parsed;
            try { parsed = Integer.parseInt(base.substring(1)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid recipe parameter: " + base); }
            if (parsed < 1 || base.charAt(1) == '0') throw new IllegalArgumentException("Recipe parameters start at P1 or O1");
            parameter = base.startsWith("O") ? -parsed : parsed;
        }
        int separator = base.indexOf("::");
        if (separator < 0) return new Member(base, "", base, parameter, false);
        int slash = base.indexOf('/', separator + 2);
        String type = slash < 0 ? base : base.substring(0, slash);
        String id = slash < 0 ? "*" : base.substring(slash + 1);
        return new Member(base, type, id, parameter, false);
    }

    private static String render(Node node) {
        if (node instanceof Leaf leaf) return leaf.member().base();
        // Both operators associate to the left, so only a right-hand nested expression needs parentheses.
        if (node instanceof Union union) return render(union.left()) + "&" + renderRight(union.right());
        if (node instanceof Difference difference) return render(difference.left()) + "!" + renderRight(difference.right());
        StringBuilder text = new StringBuilder("(");
        var children = ((Many) node).children();
        for (int index = 0; index < children.size(); index++) {
            if (index > 0) text.append(',');
            text.append(render(children.get(index)));
        }
        return text.append(')').toString();
    }

    private static String renderRight(Node node) {
        if (node instanceof Leaf leaf) return leaf.member().base();
        if (node instanceof Many) return render(node);
        return "(" + render(node) + ")";
    }

    private static final class Parser {
        private final String text;
        private int cursor;
        Parser(String text) { this.text = text; }
        void spaces() { while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++; }
        IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at selector column " + (cursor + 1)); }

        /** Left-to-right merge and subtraction; parentheses enter through {@link #operand(int)}. */
        Node select(int depth) {
            if (depth > 32) throw error("Selector groups nest more than 32 levels");
            Node node = operand(depth);
            while (true) {
                int mark = cursor;
                spaces();
                if (cursor >= text.length()) { cursor = mark; return node; }
                char operator = text.charAt(cursor);
                if (operator != '&' && operator != '!') { cursor = mark; return node; }
                cursor++;
                spaces();
                Node right = operator == '&' ? operand(depth) : subtraction(depth);
                node = operator == '&' ? new Union(node, right) : new Difference(node, right);
            }
        }

        /** The right side of {@code !}: either one operand or a comma separated exclusion list. */
        Node subtraction(int depth) {
            if (cursor < text.length() && text.charAt(cursor) == '(') {
                cursor++;
                var list = new ArrayList<Node>();
                list.add(select(depth + 1));
                spaces();
                while (cursor < text.length() && text.charAt(cursor) == ',') {
                    cursor++;
                    list.add(select(depth + 1));
                    spaces();
                }
                if (cursor >= text.length() || text.charAt(cursor) != ')') throw error("Missing closing exclusion parenthesis");
                cursor++;
                return list.size() == 1 ? list.get(0) : new Many(list);
            }
            return operand(depth);
        }

        Node operand(int depth) {
            spaces();
            if (cursor < text.length() && text.charAt(cursor) == '(') {
                cursor++;
                Node inner = select(depth + 1);
                spaces();
                if (cursor >= text.length() || text.charAt(cursor) != ')') throw error("Missing closing parenthesis");
                cursor++;
                return inner;
            }
            int start = cursor;
            while (cursor < text.length() && "abcdefghijklmnopqrstuvwxyzPO0123456789_.*:/-?#".indexOf(text.charAt(cursor)) >= 0) cursor++;
            if (cursor == start) throw error("Expected resource or resource type");
            String base = text.substring(start, cursor);
            if ((base.contains("P")||base.contains("O")) && !base.equals("P") && !base.equals("O") && !base.matches("[PO][1-9][0-9]*"))
                throw error("Expected the recipe sets P and O or one material P1, P2, ... / output O1, O2, ...");
            if (base.contains(":::")) throw error("Invalid resource type separator");
            return new Leaf(member(base));
        }
    }
}
