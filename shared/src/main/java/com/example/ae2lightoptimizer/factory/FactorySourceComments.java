package com.example.ae2lightoptimizer.factory;

/** Masks slash comments without changing source offsets, newlines, or quoted content. */
final class FactorySourceComments {
    private FactorySourceComments() {}

    static String maskSlashComments(String source, boolean sfm) {
        var result = new StringBuilder(source);
        boolean quoted = false;
        boolean first = true;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n' || c == '\r') { first = true; continue; }
            if (quoted) {
                // SFM accepts escaped quotes; keep the scanner consistent with its string reader.
                if (c == '\\' && i + 1 < source.length() && source.charAt(i + 1) == '"') i++;
                else if (c == '"') quoted = false;
                continue;
            }
            boolean slash = source.startsWith("//", i);
            boolean existing = sfm ? source.startsWith("--", i) : first && c == '#';
            if (slash || existing) {
                while (i < source.length() && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
                    if (slash) result.setCharAt(i, ' ');
                    i++;
                }
                i--; continue;
            }
            if (c == '"') quoted = true;
            if (!Character.isWhitespace(c)) first = false;
        }
        return result.toString();
    }
}
