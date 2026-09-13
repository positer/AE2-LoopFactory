package com.example.ae2lightoptimizer.factory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure indentation and source-symbol helpers shared by both client adapters. */
public final class FactoryCompletion {
    public static final int INDENT_WIDTH = 4;
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\h*import\\h+(.+?)\\h*$");
    private static final Pattern FUNCTION = Pattern.compile("(?m)^\\h*func\\h+([\\p{L}_][\\p{L}\\p{N}_]*)\\h*$");

    public record Edit(String text, int cursor) {
        public Edit {
            if (text == null) throw new IllegalArgumentException("Null text");
            cursor = Math.max(0, Math.min(cursor, text.length()));
        }
    }

    private FactoryCompletion() {}

    public static Edit newline(String source, int selectionStart, int selectionEnd) {
        String text = source == null ? "" : source;
        int start = clamp(Math.min(selectionStart, selectionEnd), 0, text.length());
        int end = clamp(Math.max(selectionStart, selectionEnd), 0, text.length());
        int lineStart = lineStart(text, end);
        int indentEnd = lineStart;
        while (indentEnd < text.length() && text.charAt(indentEnd) == ' ') indentEnd++;
        String inserted = "\n" + text.substring(lineStart, indentEnd);
        return new Edit(text.substring(0, start) + inserted + text.substring(end), start + inserted.length());
    }

    public static Edit indent(String source, int selectionStart, int selectionEnd, boolean outdent) {
        String text = source == null ? "" : source;
        int start = clamp(Math.min(selectionStart, selectionEnd), 0, text.length());
        int end = clamp(Math.max(selectionStart, selectionEnd), 0, text.length());
        if (start == end) {
            int lineStart = lineStart(text, start);
            if (!outdent) {
                return new Edit(text.substring(0, lineStart) + " ".repeat(INDENT_WIDTH) + text.substring(lineStart),
                        start + INDENT_WIDTH);
            }
            int remove = 0;
            while (remove < INDENT_WIDTH && lineStart + remove < text.length()
                    && text.charAt(lineStart + remove) == ' ') remove++;
            return new Edit(text.substring(0, lineStart) + text.substring(lineStart + remove),
                    Math.max(lineStart, start - remove));
        }

        int firstLine = lineStart(text, start);
        int lastLine = lineStart(text, Math.max(start, end - 1));
        StringBuilder output = new StringBuilder(text.length() + INDENT_WIDTH * 4);
        int copied = 0;
        int line = firstLine;
        int startDelta = 0;
        int endDelta = 0;
        while (line <= lastLine) {
            output.append(text, copied, line);
            int lineEnd = lineEnd(text, line);
            String content = text.substring(line, lineEnd);
            int delta;
            if (outdent) {
                int remove = 0;
                while (remove < INDENT_WIDTH && remove < content.length() && content.charAt(remove) == ' ') remove++;
                output.append(content, remove, content.length());
                delta = -remove;
            } else {
                output.append(" ".repeat(INDENT_WIDTH)).append(content);
                delta = INDENT_WIDTH;
            }
            if (line <= start) startDelta += delta;
            if (line < end) endDelta += delta;
            copied = lineEnd;
            line = lineEnd < text.length() ? lineEnd + 1 : text.length();
        }
        output.append(text, copied, text.length());
        int newStart = clamp(start + startDelta, 0, output.length());
        int newEnd = clamp(end + endDelta, 0, output.length());
        return new Edit(output.toString(), newEnd == start ? newStart : newEnd);
    }

    public static List<String> tags(String source) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (source == null) return List.of();
        Matcher matcher = IMPORT.matcher(FactorySourceComments.maskSlashComments(source, false));
        while (matcher.find()) {
            for (String value : matcher.group(1).split(",")) {
                String tag = value.strip();
                if (!tag.isEmpty() && tag.matches("[\\p{L}_][\\p{L}\\p{N}_]*")) tags.add(tag);
            }
        }
        return List.copyOf(tags);
    }

    public static List<String> functions(String source) {
        LinkedHashSet<String> functions = new LinkedHashSet<>();
        if (source == null) return List.of();
        Matcher matcher = FUNCTION.matcher(FactorySourceComments.maskSlashComments(source, false));
        while (matcher.find()) functions.add(matcher.group(1));
        return List.copyOf(functions);
    }

    private static int lineStart(String text, int cursor) {
        int position = clamp(cursor, 0, text.length());
        return text.lastIndexOf('\n', Math.max(0, position - 1)) + 1;
    }

    private static int lineEnd(String text, int cursor) {
        int position = clamp(cursor, 0, text.length());
        int end = text.indexOf('\n', position);
        return end < 0 ? text.length() : end;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
