package com.example.ae2lightoptimizer.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Tolerant source lexer; colors never alter compiler validation or editor text. */
public final class FactorySyntaxHighlighter {
    public enum Kind { PLAIN, KEYWORD, STRING, NUMBER, COMMENT, TAG, FUNCTION, OPERATOR, RESOURCE, REFERENCE }
    public record Span(int start, int end, Kind kind) {}
    private enum Context { NONE, RESOURCE, LABEL, CONDITION }
    private static final Set<String> SFM = Set.of("name", "every", "tick", "ticks", "second", "seconds",
            "redstone", "pulse", "do", "input", "output", "from", "to", "if", "then", "else", "forget", "end",
            "global", "g", "plus", "overall", "has", "must", "except", "side", "true", "false", "source", "storage");
    private static final Set<String> LOGIC = Set.of("and", "or", "not");
    private static final Set<String> COMPARISONS = Set.of("gt", "lt", "ge", "le", "eq");
    private static final Set<String> FACES = Set.of("up", "down", "north", "south", "east", "west", "top", "bottom", "null");
    private FactorySyntaxHighlighter() {}

    public static List<Span> highlight(String source) {
        if (source == null || source.isEmpty()) return List.of();
        return lex(source, Set.copyOf(FactoryCompletion.tags(source)), Set.copyOf(FactoryCompletion.functions(source)), SfmSyntax.recognizes(source));
    }
    public static List<Span> highlightLine(String line, Set<String> tags, Set<String> functions) {
        if (line == null || line.isEmpty()) return List.of();
        return lex(line, tags, functions, SfmSyntax.recognizes(line));
    }
    /** Intersect cached source spans with a native visual line without re-lexing wrapped fragments. */
    public static List<Span> slice(List<Span> source, int begin, int end) {
        if (begin < 0 || end < begin) throw new IllegalArgumentException("Invalid highlight range");
        int low=0, high=source.size();
        while(low<high) {int mid=(low+high)>>>1;if(source.get(mid).end()<=begin)low=mid+1;else high=mid;}
        var result=new ArrayList<Span>();
        for(int i=low;i<source.size() && source.get(i).start()<end;i++) {
            var s=source.get(i);result.add(new Span(Math.max(begin,s.start())-begin,Math.min(end,s.end())-begin,s.kind()));
        }
        return List.copyOf(result);
    }
    private static List<Span> lex(String text, Set<String> tags, Set<String> functions, boolean sfm) {
        var spans=new ArrayList<Span>();
        boolean first=true, conditionResource=false;
        Context context=Context.NONE;
        String previous="";
        for(int i=0;i<text.length();) {
            int start=i, cp=text.codePointAt(i);
            if(Character.isWhitespace(cp)) {
                do {
                    if(text.charAt(i)=='\n') {first=true;if(!sfm) {context=Context.NONE;previous="";}}
                    i+=Character.charCount(text.codePointAt(i));
                } while(i<text.length() && Character.isWhitespace(text.codePointAt(i)));
                spans.add(new Span(start,i,Kind.PLAIN));continue;
            }
            if(text.startsWith("//",i) || (text.startsWith("--",i) && sfm) || (cp=='#' && !sfm && (first || i+1==text.length() || Character.isWhitespace(text.charAt(i+1))))) {
                while(i<text.length() && text.charAt(i)!='\n' && text.charAt(i)!='\r')i++;
                spans.add(new Span(start,i,Kind.COMMENT));first=false;continue;
            }
            if(cp=='"') {
                i++;
                while(i<text.length()) {char c=text.charAt(i++);if(c=='\\' && i<text.length() && text.charAt(i)=='"')i++;else if(c=='"')break;}
                spans.add(new Span(start,i,Kind.STRING));first=false;previous="";continue;
            }
            boolean resourceTag=cp=='#' && i+1<text.length() && wordPart(text.codePointAt(i+1));
            if(wordPart(cp) || resourceTag) {
                if(resourceTag)i++;
                while(i<text.length() && wordPart(text.codePointAt(i)) && !text.startsWith("//",i) && !(sfm && text.startsWith("--",i)))i+=Character.charCount(text.codePointAt(i));
                String word=text.substring(start,i), lower=word.toLowerCase(Locale.ROOT);
                Kind kind;
                boolean qualified=resourceTag || word.indexOf(':')>=0 || word.indexOf('/')>=0 || word.indexOf('*')>=0 || word.indexOf('?')>=0;
                if(qualified)kind=Kind.RESOURCE;
                else if(word.matches("-?[0-9]+"))kind=Kind.NUMBER;
                else if(sfm && word.matches("[0-9]+[gG]")) {
                    spans.add(new Span(start,i-1,Kind.NUMBER));spans.add(new Span(i-1,i,Kind.KEYWORD));first=false;previous=lower;continue;
                }
                else if(word.matches("[PO][0-9]*"))kind=Kind.REFERENCE;
                else if(sfm) {
                    if(context==Context.CONDITION && (LOGIC.contains(lower) || COMPARISONS.contains(lower)))kind=Kind.OPERATOR;
                    else if(SFM.contains(lower))kind=Kind.KEYWORD;
                    else if(FACES.contains(lower) && nextWord(text,i).equalsIgnoreCase("side"))kind=Kind.KEYWORD;
                    else if(context==Context.LABEL || context==Context.CONDITION && !conditionResource)kind=Kind.TAG;
                    else if(context==Context.RESOURCE || context==Context.CONDITION)kind=Kind.RESOURCE;
                    else kind=Kind.PLAIN;
                } else {
                    if(FactoryCompiler.RESERVED.contains(word))kind=Kind.KEYWORD;
                    else if(LOGIC.contains(word) && context==Context.CONDITION)kind=Kind.OPERATOR;
                    else if(word.equals("in") && context==Context.CONDITION || FACES.contains(word) && previous.equals("on"))kind=Kind.KEYWORD;
                    else if(tags.contains(word) || previous.equals("import"))kind=Kind.TAG;
                    else if(functions.contains(word) || previous.equals("func"))kind=Kind.FUNCTION;
                    else kind=Kind.PLAIN;
                }
                spans.add(new Span(start,i,kind));
                if(sfm) {
                    context=switch(lower) {
                        case "input","output" -> Context.RESOURCE;
                        case "from","to","forget" -> Context.LABEL;
                        case "if","overall" -> Context.CONDITION;
                        case "then","else","end","every","name","do" -> Context.NONE;
                        default -> context;
                    };
                    if(lower.equals("has"))conditionResource=true;
                    else if(LOGIC.contains(lower) || lower.equals("if"))conditionResource=false;
                } else if(word.equals("if") || word.equals("while"))context=Context.CONDITION;
                previous=lower;first=false;continue;
            }
            if("<>!=(),+&#".indexOf(cp)>=0) {
                i++;
                if("<>!=".indexOf(cp)>=0 && i<text.length() && text.charAt(i)=='=')i++;
                spans.add(new Span(start,i,Kind.OPERATOR));first=false;continue;
            }
            i+=Character.charCount(cp);spans.add(new Span(start,i,Kind.PLAIN));first=false;
        }
        return List.copyOf(spans);
    }
    private static String nextWord(String text,int i) {
        while(i<text.length() && Character.isWhitespace(text.charAt(i)))i++;
        int start=i;while(i<text.length() && Character.isLetter(text.charAt(i)))i++;
        return text.substring(start,i);
    }
    private static boolean wordPart(int cp) {
        return Character.isLetterOrDigit(cp) || cp=='_' || cp==':' || cp=='/' || cp=='.' || cp=='-' || cp=='*' || cp=='?';
    }
}
