package com.example.ae2lightoptimizer.factory;

import java.util.Set;
import java.util.function.ToLongBiFunction;
import java.util.regex.Pattern;

/** Exact signed 64-bit comparisons; never converts material counts to floating point. */
public final class FactoryExpression {
    private static final Pattern COMPARISON = Pattern.compile("^(.+?)\\s*(<=|>=|<|>|=)\\s*(.+)$");
    /** Sentinel tag used by a bare {@code has resource} to mean every machine tag of the program. */
    public static final String ALL_TAGS = "*";
    private FactoryExpression() {}

    /** Only reject a loop when its evaluated condition needs no world counts. */
    public static boolean alwaysTrue(String expression) {
        try { return evaluate(expression,(tag,resource)->{throw new UnknownCount();})!=0; }
        catch (IllegalArgumentException invalidOrDynamic) { return false; }
    }
    private static final class UnknownCount extends IllegalArgumentException {}

    public static long evaluate(String expression, ToLongBiFunction<String, String> counts) {
        return evaluate(expression,counts,0);
    }
    private static long evaluate(String expression,ToLongBiFunction<String,String> counts,int depth) {
        if(depth>64)throw new IllegalArgumentException("Boolean expression nests more than 64 levels");
        expression=expression.strip();
        int split=logical(expression," or ");
        if(split>=0)return evaluate(expression.substring(0,split),counts,depth+1)!=0 || evaluate(expression.substring(split+4),counts,depth+1)!=0?1:0;
        split=logical(expression," and ");
        if(split>=0)return evaluate(expression.substring(0,split),counts,depth+1)!=0 && evaluate(expression.substring(split+5),counts,depth+1)!=0?1:0;
        if(expression.startsWith("not "))return evaluate(expression.substring(4),counts,depth+1)==0?1:0;
        if(expression.startsWith("(")&&enclosed(expression))return evaluate(expression.substring(1,expression.length()-1),counts,depth+1);
        var comparison = COMPARISON.matcher(expression.strip());
        if (comparison.matches()) {
            long left = scalar(comparison.group(1).strip(), counts);
            long right = scalar(comparison.group(3).strip(), counts);
            boolean result = switch (comparison.group(2)) {
                case "<" -> left < right; case ">" -> left > right;
                case "<=" -> left <= right; case ">=" -> left >= right;
                case "=" -> left == right;
                default -> throw new IllegalArgumentException("Invalid comparison");
            };
            return result ? 1 : 0;
        }
        return scalar(expression.strip(), counts);
    }
    private static int logical(String text,String operator) {
        int depth=0;
        for(int i=0;i<text.length();i++) {
            if(text.charAt(i)=='(')depth++;
            if(text.charAt(i)==')')depth--;
            if(depth==0&&text.startsWith(operator,i))return i;
        }
        return -1;
    }
    private static boolean enclosed(String text) {
        int depth=0;
        for(int i=0;i<text.length();i++) {
            if(text.charAt(i)=='(')depth++;
            if(text.charAt(i)==')'&&--depth==0)return i==text.length()-1;
        }
        return false;
    }

    public static void validate(String expression, Set<String> tags, int line) {
        validate(expression, tags, line, false, 0);
    }
    public static void validate(String expression,Set<String> tags,int line,boolean recipe,int materialCount){validate(expression,tags,line,recipe,materialCount,recipe?Integer.MAX_VALUE:0);}
    public static void validate(String expression, Set<String> tags, int line, boolean recipe, int materialCount,int outputCount) {
        try {
            validateParts(expression, (tag, resource) -> {
                if (!tag.equals(ALL_TAGS))
                    for (var part : FactoryTags.expression(tag))
                        if (!tags.contains(part) && !part.equals("source") && !part.equals("storage"))
                            throw new IllegalArgumentException("Unknown tag: " + part);
                FactorySelector.parse(resource).validateParameters(recipe, materialCount,outputCount);
                return 0;
            },0);
        } catch (IllegalArgumentException error) { throw new FactoryProgram.CompileException(line, error.getMessage()); }
    }
    private static void validateParts(String text,ToLongBiFunction<String,String> counts,int depth) {
        if(depth>64)throw new IllegalArgumentException("Boolean expression nests more than 64 levels");
        text=text.strip();
        for(String operator:new String[]{" or "," and "}) {
            int split=logical(text,operator);
            if(split>=0){validateParts(text.substring(0,split),counts,depth+1);validateParts(text.substring(split+operator.length()),counts,depth+1);return;}
        }
        if(text.startsWith("not ")){validateParts(text.substring(4),counts,depth+1);return;}
        if(text.startsWith("(")&&enclosed(text)){validateParts(text.substring(1,text.length()-1),counts,depth+1);return;}
        evaluate(text,counts);
    }

    private static long scalar(String text, ToLongBiFunction<String, String> counts) {
        if (text.equals("true")) return 1;
        if (text.equals("false")) return 0;
        // "has resource in Tag" reads that one tag; a bare "has resource" sums every machine tag.
        if (text.startsWith("has ")) {
            String rest = text.substring(4).strip();
            int split = rest.lastIndexOf(" in ");
            if (split >= 0) {
                String resource = rest.substring(0, split).strip();
                String tag = rest.substring(split + 4).strip();
                if (resource.isEmpty() || tag.isEmpty()) throw new IllegalArgumentException("Invalid expression: " + text);
                return counts.applyAsLong(tag, resource);
            }
            if (rest.isEmpty()) throw new IllegalArgumentException("Invalid expression: " + text);
            return counts.applyAsLong(ALL_TAGS, rest);
        }
        int count=text.lastIndexOf(" has ");
        if (count>=0) return counts.applyAsLong(text.substring(0,count).strip(),text.substring(count+5).strip());
        try { return Long.parseLong(text); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid expression: " + text); }
    }
}
