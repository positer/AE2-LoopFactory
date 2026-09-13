package com.example.ae2lightoptimizer.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** SFM-format structural reader. Routing clauses remain intact for semantic validation, not discarded. */
public final class SfmSyntax {
    public record Token(String text, boolean quoted, int line) {
        boolean is(String keyword) { return !quoted && text.equalsIgnoreCase(keyword); }
    }
    public record Program(String name, List<Trigger> triggers) { public Program { triggers = List.copyOf(triggers); } }
    public record Trigger(long ticks, boolean global, long offset, boolean redstone, List<Statement> body, int line) {
        public Trigger { body = List.copyOf(body); }
    }
    public sealed interface Statement permits Route, Forget, Conditional {}
    public record Route(boolean input, List<Token> resources, List<Token> destinations, int line) implements Statement {
        public Route { resources = List.copyOf(resources); destinations = List.copyOf(destinations); }
    }
    public record Forget(List<Token> labels, int line) implements Statement { public Forget { labels = List.copyOf(labels); } }
    public record Branch(List<Token> condition, List<Statement> body) { public Branch { condition = List.copyOf(condition); body = List.copyOf(body); } }
    public record Conditional(List<Branch> branches, List<Statement> otherwise, int line) implements Statement {
        public Conditional { branches = List.copyOf(branches); otherwise = List.copyOf(otherwise); }
    }
    private static final Set<String> STARTS = Set.of("input", "output", "from", "to", "if", "forget", "end", "else");
    private final List<Token> tokens;
    private int cursor;
    private SfmSyntax(String source) { tokens = lex(source); }

    public static boolean recognizes(String source) {
        if(source==null || source.length()>FactoryCompiler.MAX_SOURCE_LENGTH)return false;
        source = FactorySourceComments.maskSlashComments(source, true);
        int p=skipHeader(source,0);
        if(word(source,p,"every"))return true;
        if(!word(source,p,"name"))return false;
        p=skipHeader(source,p+4);
        if(p>=source.length()||source.charAt(p++)!='"')return false;
        while(p<source.length()) {
            char c=source.charAt(p++);
            if(c=='\\' && p<source.length() && source.charAt(p)=='"'){p++;continue;}
            if(c=='"')return word(source,skipHeader(source,p),"every");
        }
        return false;
    }
    private static boolean word(String source,int offset,String word) {
        return source.regionMatches(true,offset,word,0,word.length()) &&
                (offset+word.length()==source.length()||Character.isWhitespace(source.charAt(offset+word.length())));
    }
    private static int skipHeader(String source,int p) {
        while(p<source.length()) {
            if(Character.isWhitespace(source.charAt(p))){p++;continue;}
            if(source.startsWith("--",p)){while(p<source.length()&&source.charAt(p)!='\n')p++;continue;}
            break;
        }
        return p;
    }
    public static Program parse(String source) {
        var parser = new SfmSyntax(source);
        String name = "";
        if (parser.take("name")) {
            Token token = parser.next();
            if (!token.quoted) throw error(token.line, "SFM NAME requires a quoted name");
            if(token.text.isBlank()||token.text.length()>128||token.text.chars().anyMatch(Character::isISOControl))throw error(token.line,"Factory name must contain 1 to 128 printable characters");
            name = token.text;
        }
        var triggers = new ArrayList<Trigger>();
        while (parser.cursor < parser.tokens.size()) triggers.add(parser.trigger());
        if (triggers.isEmpty()) throw error(1, "SFM program requires an EVERY trigger");
        return new Program(name, triggers);
    }
    private Trigger trigger() {
        Token begin = require("every");
        boolean pulse = take("redstone");
        long ticks = 1, offset = 0;
        boolean global = false;
        if (pulse) require("pulse");
        else {
            if (peek().text.matches("[0-9]+[gG]?")) {
                Token number = next();
                global = number.text.endsWith("g") || number.text.endsWith("G");
                ticks = positive(global ? number.text.substring(0, number.text.length() - 1) : number.text, number.line);
            }
            global |= take("global") || take("g");
            if (take("plus") || take("+")) offset = nonnegative(next());
            Token unit = next();
            if (unit.is("seconds") || unit.is("second")) {
                try { ticks = Math.multiplyExact(ticks, 20); offset = Math.multiplyExact(offset, 20); }
                catch (ArithmeticException e) { throw error(unit.line, "SFM interval exceeds 64-bit ticks"); }
            } else if (!unit.is("ticks") && !unit.is("tick")) throw error(unit.line, "Expected TICKS or SECONDS");
        }
        require("do");
        var body = block(0);
        require("end");
        return new Trigger(ticks, global, offset, pulse, body, begin.line);
    }
    private List<Statement> block(int depth) {
        if (depth > 64) throw error(peek().line, "SFM blocks nest more than 64 levels");
        var result = new ArrayList<Statement>();
        while (cursor < tokens.size() && !peek().is("end") && !peek().is("else")) {
            Token token = next();
            if (token.is("if")) {
                var branches = new ArrayList<Branch>();
                var condition = until("then"); require("then");
                if (condition.isEmpty()) throw error(token.line, "Missing IF condition");
                branches.add(new Branch(condition, block(depth + 1)));
                List<Statement> otherwise = List.of();
                while (take("else")) {
                    if (take("if")) {
                        var otherCondition = until("then"); require("then");
                        if (otherCondition.isEmpty()) throw error(token.line, "Missing ELSE IF condition");
                        branches.add(new Branch(otherCondition, block(depth + 1)));
                    } else { otherwise = block(depth + 1); break; }
                }
                require("end");
                result.add(new Conditional(branches, otherwise, token.line));
            } else if (token.is("forget")) result.add(new Forget(clause(), token.line));
            else if (token.is("input") || token.is("output")) {
                boolean input = token.is("input");
                String direction = input ? "from" : "to";
                var resources = until(direction); require(direction);
                var destinations = clause();
                if (destinations.isEmpty()) throw error(token.line, "Missing SFM destination label");
                result.add(new Route(input, resources, destinations, token.line));
            } else if (token.is("from") || token.is("to")) {
                boolean input = token.is("from");
                String action = input ? "input" : "output";
                var destinations = until(action); require(action);
                if (destinations.isEmpty()) throw error(token.line, "Missing SFM destination label");
                result.add(new Route(input, clause(), destinations, token.line));
            } else throw error(token.line, "Unknown SFM statement: " + token.text);
        }
        return List.copyOf(result);
    }
    private List<Token> clause() {
        var result = new ArrayList<Token>();
        int parentheses = 0;
        while (cursor < tokens.size()) {
            var token = peek();
            if (parentheses == 0 && !token.quoted && STARTS.contains(token.text.toLowerCase(Locale.ROOT))) break;
            if (token.is("(")) parentheses++;
            if (token.is(")") && --parentheses < 0) throw error(token.line, "Unexpected closing parenthesis");
            result.add(next());
        }
        if (parentheses != 0) throw error(result.getFirst().line, "Unclosed SFM parentheses");
        return result;
    }
    private List<Token> until(String keyword) {
        var result = new ArrayList<Token>();
        while (cursor < tokens.size() && !peek().is(keyword)) {
            if (peek().is("end") || peek().is("every")) throw error(peek().line, "Expected " + keyword.toUpperCase(Locale.ROOT));
            result.add(next());
        }
        return result;
    }
    private Token peek() { if (cursor >= tokens.size()) throw error(tokens.isEmpty() ? 1 : tokens.getLast().line, "Unexpected end of SFM code"); return tokens.get(cursor); }
    private Token next() { Token token = peek(); cursor++; return token; }
    private boolean take(String text) { if (cursor < tokens.size() && peek().is(text)) { cursor++; return true; } return false; }
    private Token require(String text) { Token token = next(); if (!token.is(text)) throw error(token.line, "Expected " + text.toUpperCase(Locale.ROOT)); return token; }
    private static long positive(String value, int line) { long result = nonnegative(new Token(value, false, line)); if (result < 1) throw error(line, "Interval must be positive"); return result; }
    private static long nonnegative(Token token) { try { long value = Long.parseLong(token.text); if (value >= 0) return value; } catch (NumberFormatException ignored) {} throw error(token.line, "Expected nonnegative 64-bit integer"); }
    private static FactoryProgram.CompileException error(int line, String text) { return new FactoryProgram.CompileException(line, text); }

    public static List<Token> lex(String source) {
        if (source == null || source.length() > FactoryCompiler.MAX_SOURCE_LENGTH) throw error(1, "SFM code exceeds 65536 characters");
        source = FactorySourceComments.maskSlashComments(source, true);
        var result = new ArrayList<Token>();
        int line = 1;
        for (int cursor = 0; cursor < source.length();) {
            char ch = source.charAt(cursor);
            if (Character.isWhitespace(ch)) { if (ch == '\n') line++; cursor++; continue; }
            if (ch == '-' && cursor + 1 < source.length() && source.charAt(cursor + 1) == '-') {
                while (cursor < source.length() && source.charAt(cursor) != '\n') cursor++;
                continue;
            }
            int startLine = line;
            if (ch == '"') {
                var value = new StringBuilder(); cursor++;
                boolean closed = false;
                while (cursor < source.length()) {
                    char current = source.charAt(cursor++);
                    if (current == '"') { closed = true; break; }
                    if (current == '\\' && cursor < source.length() && source.charAt(cursor) == '"') current = source.charAt(cursor++);
                    if (current == '\n') line++;
                    value.append(current);
                }
                if (!closed) throw error(startLine, "Unclosed SFM string");
                result.add(new Token(value.toString(), true, startLine));
            } else if (",()!+<>=#".indexOf(ch) >= 0) {
                String token = String.valueOf(ch); cursor++;
                if ((ch == '<' || ch == '>') && cursor < source.length() && source.charAt(cursor) == '=') { token += '='; cursor++; }
                result.add(new Token(token, false, startLine));
            } else {
                int start = cursor;
                while (cursor < source.length() && !Character.isWhitespace(source.charAt(cursor))
                        && ",()!+<>=#\"".indexOf(source.charAt(cursor)) < 0
                        && !(source.charAt(cursor) == '-' && cursor + 1 < source.length() && source.charAt(cursor + 1) == '-')) cursor++;
                if (cursor == start) throw error(line, "Unexpected SFM character");
                result.add(new Token(source.substring(start, cursor), false, startLine));
            }
        }
        return List.copyOf(result);
    }
}
