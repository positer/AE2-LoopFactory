package com.example.ae2lightoptimizer.factory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import static com.example.ae2lightoptimizer.factory.FactoryProgram.*;

/** Indentation-sensitive compiler. It never executes source or accesses a Minecraft world. */
public final class FactoryCompiler {
    public static final int MAX_SOURCE_LENGTH = 65536;
    /** {@code get} and {@code put} without an explicit quantity move everything they can reach. */
    public static final long DEFAULT_AMOUNT = Long.MAX_VALUE;
    public static final Set<String> RESERVED = Set.of("import", "name", "get", "from", "put", "into",
            "on", "has", "if", "else", "do", "while", "wait", "redstone", "func", "end", "done",
            "must", "storage", "source", "true", "false", "tick", "s", "min", "break", "channel");
    /** Tag and function names accept any Unicode letter, so Chinese names work alongside ASCII ones. */
    private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");
    private final List<Line> lines = new ArrayList<>();
    private final List<Instruction> code = new ArrayList<>();
    private final Set<String> tags = new LinkedHashSet<>();
    private final Map<String, Integer> functions = new LinkedHashMap<>();
    private final boolean recipe;
    private final String source;
    private final int materialCount;
    private final int outputCount;
    private String name = "";
    private int cursor;
    private int depth;
    /** Current logistics channel; 0 is everything outside a {@code channel { ... }} block. */
    private int channel;
    private int channelCounter;
    /** Pending {@code break} jumps of the loops currently being compiled, innermost first. */
    private final java.util.ArrayDeque<java.util.List<Integer>> loopBreaks = new java.util.ArrayDeque<>();
    private record Line(int indent, String text, int number) {}

    private FactoryCompiler(String source, boolean recipe, int materialCount,int outputCount) {
        if (source == null || source.length() > MAX_SOURCE_LENGTH) throw error(1, "Code exceeds 65536 characters");
        this.source = source;
        this.recipe = recipe;
        this.materialCount = materialCount;this.outputCount=outputCount;
        var raw = FactorySourceComments.maskSlashComments(source, false).replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < raw.length; i++) {
            if (raw[i].contains("\t")) throw error(i + 1, "Use spaces, not tabs, for indentation");
            String text = raw[i].stripLeading();
            if (!text.isBlank() && !text.startsWith("#")) lines.add(new Line(raw[i].length() - text.length(), text.stripTrailing(), i + 1));
        }
    }

    public static FactoryProgram compile(String source, boolean recipe) {
        return compile(source, recipe, recipe ? Integer.MAX_VALUE : 0);
    }

    public static FactoryProgram compile(String source,boolean recipe,int materialCount){return compile(source,recipe,materialCount,recipe?Integer.MAX_VALUE:0);}
    public static FactoryProgram compile(String source, boolean recipe, int materialCount,int outputCount) {
        if (materialCount < 0||outputCount<0) throw new IllegalArgumentException("Negative material count");
        if (SfmSyntax.recognizes(source)) return SfmCompiler.compile(source,recipe,materialCount,outputCount);
        var compiler = new FactoryCompiler(source, recipe, materialCount,outputCount);
        compiler.block(0, false);
        compiler.emit(Op.DONE, "", 0, 0, compiler.lines.isEmpty() ? 1 : compiler.lines.getLast().number);
        for (var instruction : compiler.code) {
            if (instruction.op() == Op.CALL && !compiler.functions.containsKey(instruction.argument()))
                throw error(instruction.line(), "Unknown function: " + instruction.argument());
            if (instruction.op()==Op.GET||instruction.op()==Op.PUT) {
                String tag=instruction.argument().split(" ",-1)[1];
                for (var part : tagParts(tag, instruction.line()))
                    if(!compiler.tags.contains(part)&&!part.equals("storage")&&!part.equals("source"))throw error(instruction.line(),"Import tag before use: "+part);
            }
            if(instruction.op()==Op.REDSTONE)
                for (var part : tagParts(instruction.argument(), instruction.line()))
                    if(!compiler.tags.contains(part))throw error(instruction.line(),"Unknown tag: "+part);
            if (instruction.op() == Op.TEST) FactoryExpression.validate(instruction.argument(), compiler.tags, instruction.line(), recipe, materialCount,outputCount);
        }
        return new FactoryProgram(source, compiler.name, compiler.tags, compiler.code, compiler.functions, recipe);
    }

    private void block(int indent, boolean nested) {
        while (cursor < lines.size()) {
            Line line = lines.get(cursor);
            if (line.indent < indent) return;
            if (line.indent > indent) throw error(line.number, "Unexpected indentation");
            if (line.text.equals("end") || line.text.equals("else") || line.text.startsWith("else ")) {
                if (!nested) throw error(line.number, "Unmatched " + line.text);
                return;
            }
            cursor++;
            statement(line, nested);
        }
    }

    private void statement(Line line, boolean nested) {
        String text = line.text;
        if (text.startsWith("import ")) {
            if (nested) throw error(line.number, "import must be at top level");
            for (var tag : text.substring(7).split(",", -1)) { checkIdentifier(tag.strip(), line.number); if(functions.containsKey(tag.strip()))throw error(line.number,"Duplicate name: "+tag.strip()); tags.add(tag.strip()); }
        } else if (text.startsWith("name ")) {
            if (nested || !text.matches("name \"[^\"]{1,128}\"")) throw error(line.number, "Expected top-level name \"Factory name\"");
            name = text.substring(6, text.length() - 1);
        } else if (text.startsWith("if ") || text.startsWith("while ")) {
            boolean loop = text.startsWith("while ");
            if (!text.endsWith(" do")) throw error(line.number, "Expected do after condition");
            String condition = text.substring(loop ? 6 : 3, text.length() - 3).strip();
            if (condition.isEmpty()) throw error(line.number, "Missing condition");
            if (recipe && loop && FactoryExpression.alwaysTrue(condition))
                throw error(line.number, "Recipe patterns cannot contain unconditional loops");
            int test = emit(Op.TEST, condition, -1, 0, line.number);
            var pendingBreaks = loop ? new ArrayList<Integer>() : null;
            if (loop) loopBreaks.push(pendingBreaks);
            child(line);
            if (loop) {
                loopBreaks.pop();
                emit(Op.JUMP, "", test, 0, line.number);
                patch(test, code.size());
                // break leaves the loop: every pending jump lands after the loop's back jump.
                for (int jump : pendingBreaks) patch(jump, code.size());
            } else if (cursor < lines.size() && lines.get(cursor).indent == line.indent
                    && (lines.get(cursor).text.equals("else") || lines.get(cursor).text.startsWith("else "))) {
                Line otherwise = lines.get(cursor++);
                int skip = emit(Op.JUMP, "", -1, 0, otherwise.number);
                patch(test, code.size());
                if (otherwise.text.equals("else")) child(otherwise);
                else statement(new Line(line.indent, otherwise.text.substring(5).strip(), otherwise.number), true);
                patch(skip, code.size());
            } else patch(test, code.size());
        } else if (text.startsWith("func ")) {
            String id = text.substring(5).strip();
            checkIdentifier(id, line.number);
            if (functions.containsKey(id) || tags.contains(id)) throw error(line.number, "Duplicate name: " + id);
            int skip = emit(Op.JUMP, "", -1, 0, line.number);
            functions.put(id, code.size());
            child(line);
            if (cursor >= lines.size() || lines.get(cursor).indent != line.indent || !lines.get(cursor).text.equals("end"))
                throw error(line.number, "Function requires matching end");
            cursor++;
            emit(Op.RETURN, "", 0, 0, line.number);
            patch(skip, code.size());
        } else if (text.startsWith("get ") || text.startsWith("put ")) {
            boolean get = text.startsWith("get ");
            String[] parts = text.substring(4).split(get ? " from " : " into ", -1);
            if (parts.length != 2 || parts[0].isBlank()) throw error(line.number, "Expected resource " + (get ? "from" : "into") + " tag [on face]");
            String[] destination = parts[1].split(" on(?: +|$)", -1);
            if (destination.length > 2) throw error(line.number, "Invalid face clause");
            String tag = destination[0].strip();
            String face = destination.length == 1 ? "" : destination[1].strip();
            if (!tag.equals("source") && !tag.equals("storage")) checkTagExpression(tag,line.number);
            if (destination.length == 2 && face.isEmpty()) throw error(line.number,"Unknown face: <empty>");
            if (!face.isEmpty() && !Set.of("up", "down", "north", "south", "east", "west").contains(face)) throw error(line.number, "Unknown face: " + face);
            String resource = parts[0].strip();
            boolean must=resource.startsWith("must ");
            if(must)resource=resource.substring(5).strip();
            long amount = Long.MAX_VALUE;
            var quantity = Pattern.compile("^([0-9]+)\\s+(.+)$").matcher(resource);
            if (quantity.matches()) {
                amount = positive(quantity.group(1), line.number);
                resource = quantity.group(2);
            }
            if(must&&amount==Long.MAX_VALUE&&!quantity.matches())throw error(line.number,"must requires a positive quantity");
            // An unwritten quantity means one stack, not everything the selector can reach.
            if(amount==Long.MAX_VALUE)amount=DEFAULT_AMOUNT;
            try {
                var selector = FactorySelector.parse(resource);
                selector.validateParameters(recipe, materialCount,outputCount);
                resource = selector.canonical();
            }
            catch (IllegalArgumentException invalid) { throw error(line.number, invalid.getMessage()); }
            code.add(new Instruction(get ? Op.GET : Op.PUT,resource+" "+tag+" "+face+" "+channel,0,amount,line.number,must));
        } else if (text.startsWith("wait ") || text.startsWith("redstone ")) {
            boolean redstone = text.startsWith("redstone ");
            String[] words = text.split(" +");
            if (words.length != (redstone ? 4 : 3)) throw error(line.number, "Expected " + (redstone ? "redstone tag number unit" : "wait number unit"));
            String tag = redstone ? words[1] : "";
            long ticks = positive(words[redstone ? 2 : 1], line.number);
            long multiplier = switch (words[words.length - 1]) { case "tick" -> 1; case "s" -> 20; case "min" -> 1200; default -> throw error(line.number, "Unknown time unit"); };
            try { ticks = Math.multiplyExact(ticks, multiplier); } catch (ArithmeticException e) { throw error(line.number, "Duration overflows 64-bit ticks"); }
            emit(redstone ? Op.REDSTONE : Op.WAIT, tag, 0, ticks, line.number);
        } else if (text.equals("done")) emit(Op.DONE, "", 0, 0, line.number);
        else if (text.equals("channel")) {
            if (channel != 0) throw error(line.number, "channel cannot nest");
            channel = ++channelCounter;
            child(line);
            channel = 0;
        }
        else if (text.equals("break")) {
            // Inside a loop break leaves that loop; at the root it stops the program like done.
            if (loopBreaks.isEmpty()) emit(Op.DONE, "", 0, 0, line.number);
            else loopBreaks.peek().add(emit(Op.JUMP, "", -1, 0, line.number));
        }
        else {
            checkIdentifier(text, line.number);
            // Preserve lexical channel at the call site for inherited routing in functions.
            emit(Op.CALL, text, channel, 0, line.number);
        }
    }

    private void child(Line parent) {
        if (cursor >= lines.size() || lines.get(cursor).indent <= parent.indent) throw error(parent.number, "Expected indented block");
        if (depth >= 64) throw error(parent.number, "Code blocks nest more than 64 levels");
        depth++;
        try { block(lines.get(cursor).indent, true); }
        finally { depth--; }
    }
    private int emit(Op op, String arg, int target, long amount, int line) {
        code.add(new Instruction(op, arg, target, amount, line));
        return code.size() - 1;
    }
    private void patch(int index, int target) {
        var old = code.get(index);
        code.set(index, new Instruction(old.op(), old.argument(), target, old.amount(), old.line(),old.must()));
    }
    private static void checkIdentifier(String value, int line) {
        if (value.matches("[PO][0-9]*") || value.length()>128 || !IDENTIFIER.matcher(value).matches() || RESERVED.contains(value)) throw error(line, "Invalid or reserved name: " + value);
    }

    /** {@code A&B} addresses several machine tags; every part is validated like a single tag name. */
    private void checkTagExpression(String value, int line) {
        for (var part : tagParts(value, line)) {
            if (part.equals("source") || part.equals("storage")) continue;
            checkIdentifier(part, line);
        }
    }

    private static java.util.List<String> tagParts(String value, int line) {
        try { return FactoryTags.expression(value); }
        catch (IllegalArgumentException invalid) { throw error(line, invalid.getMessage()); }
    }
    private static long positive(String value, int line) {
        try { long result = Long.parseLong(value); if (result > 0) return result; } catch (NumberFormatException ignored) { }
        throw error(line, "Expected positive 64-bit integer");
    }
    private static CompileException error(int line, String message) { return new CompileException(line, message); }
}
