package com.markultra.editor.md;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Text transforming commands, kept free of Android so they can be tested on a plain JVM.
 *
 * <p>Each operation takes a string and returns a new one; the caller decides whether it
 * applies to the selection or to the whole document. Nothing here can throw: an operation
 * that cannot be applied (malformed JSON, bad base64) returns the input unchanged.</p>
 */
public final class TextTools {

    /** One operation: a stable id, a label for the menu, and the transform itself. */
    public static final class Op {
        public final String id;
        public final String label;
        public final String hint;

        Op(String id, String label, String hint) {
            this.id = id;
            this.label = label;
            this.hint = hint;
        }
    }

    private TextTools() {
    }

    private static final List<Op> OPS = new ArrayList<Op>();

    static {
        add("upper", "UPPERCASE", "Every letter upper case");
        add("lower", "lowercase", "Every letter lower case");
        add("title", "Title Case", "Capitalise each word");
        add("sentence", "Sentence case", "Capitalise the first letter of each sentence");
        add("camel", "camelCase", "Join words as an identifier");
        add("snake", "snake_case", "Join words with underscores");
        add("kebab", "kebab-case", "Join words with hyphens");
        add("sort", "Sort lines A-Z", "Alphabetical, case insensitive");
        add("sortdesc", "Sort lines Z-A", "Reverse alphabetical");
        add("reverse", "Reverse lines", "Last line first");
        add("dedupe", "Remove duplicate lines", "Keeps the first occurrence");
        add("blank", "Remove empty lines", "Collapses runs of blank lines");
        add("uniq", "Remove blank + duplicates", "Both at once");
        add("join", "Join lines into one paragraph", "Single spaces between lines");
        add("bullet", "Prefix each line with -", "Turn lines into a list");
        add("number", "Number each line", "1. 2. 3. and so on");
        add("check", "Turn lines into task list", "- [ ] before each line");
        add("trimws", "Trim trailing spaces", "Removes invisible whitespace");
        add("collapse", "Collapse blank lines", "At most one blank line between blocks");
        add("wrap", "Wrap at 80 columns", "Hard wraps long lines");
        add("urlenc", "URL encode", "Percent encoding");
        add("urldec", "URL decode", "Percent decoding");
        add("b64enc", "Base64 encode", "UTF-8 bytes");
        add("b64dec", "Base64 decode", "UTF-8 bytes");
        add("json", "JSON pretty print", "Two space indentation");
        add("jsonmin", "JSON minify", "One line, no spaces");
        add("htmlesc", "Escape HTML", "Angle brackets and ampersands");
        add("htmlunesc", "Unescape HTML", "The reverse");
        add("strip", "Strip Markdown", "Plain text, markers removed");
    }

    private static void add(String id, String label, String hint) {
        OPS.add(new Op(id, label, hint));
    }

    public static List<Op> ops() {
        return OPS;
    }

    /** Applies an operation by id. Unknown ids and failures return the input untouched. */
    public static String apply(String id, String text) {
        if (text == null) return "";
        try {
            return applyUnsafe(id, text);
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static String applyUnsafe(String id, String text) {
        if ("upper".equals(id)) return text.toUpperCase(Locale.ROOT);
        if ("lower".equals(id)) return text.toLowerCase(Locale.ROOT);
        if ("title".equals(id)) return toTitle(text);
        if ("sentence".equals(id)) return toSentence(text);
        if ("camel".equals(id)) return camel(text);
        if ("snake".equals(id)) return joinWords(text, "_");
        if ("kebab".equals(id)) return joinWords(text, "-");
        if ("sort".equals(id)) return sortLines(text, false);
        if ("sortdesc".equals(id)) return sortLines(text, true);
        if ("reverse".equals(id)) {
            List<String> lines = lines(text);
            Collections.reverse(lines);
            return join(lines, text);
        }
        if ("dedupe".equals(id)) {
            LinkedHashSet<String> set = new LinkedHashSet<String>(lines(text));
            return join(new ArrayList<String>(set), text);
        }
        if ("blank".equals(id)) return removeEmpty(text);
        if ("uniq".equals(id)) {
            LinkedHashSet<String> set = new LinkedHashSet<String>(lines(text));
            return removeEmpty(join(new ArrayList<String>(set), text));
        }
        if ("join".equals(id)) {
            StringBuilder sb = new StringBuilder();
            List<String> ls = lines(text);
            for (int i = 0; i < ls.size(); i++) {
                String t = ls.get(i).trim();
                if (t.length() == 0) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(t);
            }
            return sb.toString();
        }
        if ("bullet".equals(id)) return prefix(text, "- ");
        if ("number".equals(id)) return number(text);
        if ("check".equals(id)) return prefix(text, "- [ ] ");
        if ("trimws".equals(id)) return trimRight(text);
        if ("collapse".equals(id)) return collapse(text);
        if ("wrap".equals(id)) return wrap(text, 80);
        if ("urlenc".equals(id)) return urlEncode(text);
        if ("urldec".equals(id)) return urlDecode(text);
        if ("b64enc".equals(id)) return Base64Text.encode(text);
        if ("b64dec".equals(id)) return Base64Text.decode(text);
        if ("json".equals(id)) return Json.pretty(text);
        if ("jsonmin".equals(id)) return Json.minify(text);
        if ("htmlesc".equals(id)) return escapeHtml(text);
        if ("htmlunesc".equals(id)) return unescapeHtml(text);
        if ("strip".equals(id)) return stripMarkdown(text);
        return text;
    }

    // ---------------------------------------------------------------- word helpers

    private static String[] words(String text) {
        String cleaned = text.replaceAll("[`*_#>*\\[\\]()!]+", " ");
        return cleaned.trim().split("[^A-Za-z0-9]+");
    }

    private static String camel(String text) {
        String[] parts = words(text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() == 0) continue;
            if (i == 0) {
                sb.append(p.toLowerCase(Locale.ROOT));
            } else {
                sb.append(Character.toUpperCase(p.charAt(0)))
                        .append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static String joinWords(String text, String sep) {
        String[] parts = words(text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() == 0) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(parts[i].toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    public static String toTitle(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean start = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (start && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                start = false;
            } else {
                sb.append(c);
                if (c == ' ' || c == '\n' || c == '\t' || c == '-' || c == '_') start = true;
            }
        }
        return sb.toString();
    }

    public static String toSentence(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean start = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (start && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                start = false;
            } else {
                sb.append(c);
                if (c == '.' || c == '!' || c == '?' || c == '\n') start = true;
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- line helpers

    private static List<String> lines(String text) {
        return new ArrayList<String>(Arrays.asList(text.split("\n", -1)));
    }

    private static String join(List<String> lines, String original) {
        String eol = original.indexOf("\r\n") >= 0 ? "\r\n" : "\n";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append(eol);
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    private static String sortLines(String text, boolean reverse) {
        List<String> ls = lines(text);
        boolean trailingNewline = text.endsWith("\n");
        if (trailingNewline && !ls.isEmpty()) ls.remove(ls.size() - 1);
        Collections.sort(ls, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareToIgnoreCase(b);
            }
        });
        if (reverse) Collections.reverse(ls);
        String out = join(ls, text);
        return trailingNewline ? out + "\n" : out;
    }

    private static String removeEmpty(String text) {
        List<String> ls = lines(text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ls.size(); i++) {
            if (ls.get(i).trim().length() == 0) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(ls.get(i));
        }
        return sb.toString();
    }

    private static String prefix(String text, String prefix) {
        List<String> ls = lines(text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ls.size(); i++) {
            if (i > 0) sb.append('\n');
            String line = ls.get(i);
            sb.append(line.trim().length() == 0 ? line : prefix + line);
        }
        return sb.toString();
    }

    private static String number(String text) {
        List<String> ls = lines(text);
        StringBuilder sb = new StringBuilder();
        int n = 1;
        for (int i = 0; i < ls.size(); i++) {
            if (i > 0) sb.append('\n');
            String line = ls.get(i);
            if (line.trim().length() == 0) {
                sb.append(line);
            } else {
                sb.append(n++).append(". ").append(line);
            }
        }
        return sb.toString();
    }

    private static String trimRight(String text) {
        String[] ls = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < ls.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(ls[i].replaceAll("[ \\t]+$", ""));
        }
        return sb.toString();
    }

    private static String collapse(String text) {
        return text.replaceAll("\\n{3,}", "\n\n");
    }

    private static String wrap(String text, int width) {
        String[] paragraphs = text.split("\n\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 32);
        for (int p = 0; p < paragraphs.length; p++) {
            if (p > 0) out.append("\n\n");
            String para = paragraphs[p];
            if (para.startsWith("```") || para.startsWith("    ") || para.startsWith("|")) {
                out.append(para);
                continue;
            }
            String[] ws = para.split("\\s+");
            int col = 0;
            for (int i = 0; i < ws.length; i++) {
                String w = ws[i];
                if (w.length() == 0) continue;
                if (col > 0 && col + 1 + w.length() > width) {
                    out.append('\n');
                    col = 0;
                } else if (col > 0) {
                    out.append(' ');
                    col++;
                }
                out.append(w);
                col += w.length();
            }
        }
        return out.toString();
    }

    private static String urlEncode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return text;
        }
    }

    private static String urlDecode(String text) {
        try {
            return URLDecoder.decode(text.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return text;
        } catch (IllegalArgumentException e) {
            return text;
        }
    }

    // ---------------------------------------------------------------- encodings

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String unescapeHtml(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    /** Removes the markers that would otherwise show up as text, without touching code. */
    private static String stripMarkdown(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        String[] ls = text.split("\n", -1);
        boolean inFence = false;
        for (int i = 0; i < ls.length; i++) {
            String line = ls[i];
            if (line.trim().startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (!inFence) {
                line = line.replaceAll("^\\s{0,3}#{1,6}\\s+", "");
                line = line.replaceAll("^\\s*[-*+]\\s+(\\[[ xX]\\]\\s+)?", "");
                line = line.replaceAll("^\\s*>\\s?", "");
                line = line.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
                line = line.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
                line = line.replaceAll("(\\*\\*|__)(.*?)\\1", "$2");
                line = line.replaceAll("(\\*|_)(.*?)\\1", "$2");
                line = line.replaceAll("~~(.*?)~~", "$1");
                line = line.replaceAll("`([^`]*)`", "$1");
            }
            sb.append(line);
            if (i < ls.length - 1) sb.append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- base64/json

    /** Minimal Base64 without android.util, so the class stays JVM-testable. */
    static final class Base64Text {
        private static final char[] MAP =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

        static String encode(String text) {
            byte[] data;
            try {
                data = text.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                return text;
            }
            StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
            for (int i = 0; i < data.length; i += 3) {
                int b0 = data[i] & 0xFF;
                int b1 = i + 1 < data.length ? data[i + 1] & 0xFF : 0;
                int b2 = i + 2 < data.length ? data[i + 2] & 0xFF : 0;
                sb.append(MAP[b0 >> 2]);
                sb.append(MAP[((b0 & 0x03) << 4) | (b1 >> 4)]);
                sb.append(i + 1 < data.length ? MAP[((b1 & 0x0F) << 2) | (b2 >> 6)] : '=');
                sb.append(i + 2 < data.length ? MAP[b2 & 0x3F] : '=');
            }
            return sb.toString();
        }

        static String decode(String text) {
            StringBuilder clean = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '=' || indexOf(c) >= 0) clean.append(c);
            }
            String s = clean.toString();
            int len = s.length();
            if (len == 0 || len % 4 != 0) return text;
            byte[] out = new byte[len / 4 * 3];
            int o = 0;
            for (int i = 0; i < len; i += 4) {
                int c0 = indexOf(s.charAt(i));
                int c1 = indexOf(s.charAt(i + 1));
                int c2 = s.charAt(i + 2) == '=' ? -1 : indexOf(s.charAt(i + 2));
                int c3 = s.charAt(i + 3) == '=' ? -1 : indexOf(s.charAt(i + 3));
                if (c0 < 0 || c1 < 0) return text;
                out[o++] = (byte) ((c0 << 2) | (c1 >> 4));
                if (c2 >= 0) out[o++] = (byte) (((c1 & 0x0F) << 4) | (c2 >> 2));
                if (c3 >= 0) out[o++] = (byte) (((c2 & 0x03) << 6) | c3);
            }
            try {
                return new String(out, 0, o, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return text;
            }
        }

        private static int indexOf(char c) {
            for (int i = 0; i < MAP.length; i++) if (MAP[i] == c) return i;
            return -1;
        }
    }

    /** Tiny JSON formatter: no dependencies, no recursive descent parser, quote aware. */
    static final class Json {
        static String pretty(String text) {
            String compact = minify(text);
            if (compact == null) return text;
            StringBuilder sb = new StringBuilder(compact.length() + 64);
            int indent = 0;
            boolean inString = false;
            for (int i = 0; i < compact.length(); i++) {
                char c = compact.charAt(i);
                if (inString) {
                    sb.append(c);
                    if (c == '\\' && i + 1 < compact.length()) {
                        sb.append(compact.charAt(++i));
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                switch (c) {
                    case '"':
                        inString = true;
                        sb.append(c);
                        break;
                    case '{':
                    case '[':
                        sb.append(c);
                        indent++;
                        newline(sb, indent);
                        break;
                    case '}':
                    case ']':
                        indent = Math.max(0, indent - 1);
                        newline(sb, indent);
                        sb.append(c);
                        break;
                    case ',':
                        sb.append(c);
                        newline(sb, indent);
                        break;
                    case ':':
                        sb.append(": ");
                        break;
                    default:
                        sb.append(c);
                }
            }
            return sb.toString();
        }

        private static void newline(StringBuilder sb, int indent) {
            sb.append('\n');
            for (int i = 0; i < indent; i++) sb.append("  ");
        }

        static String minify(String text) {
            if (text == null || text.trim().length() == 0) return null;
            String t = text.trim();
            if (!(t.startsWith("{") || t.startsWith("["))) return null;
            StringBuilder sb = new StringBuilder(t.length());
            boolean inString = false;
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (inString) {
                    sb.append(c);
                    if (c == '\\' && i + 1 < t.length()) {
                        sb.append(t.charAt(++i));
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                    sb.append(c);
                } else if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    continue;
                } else if (c == ',' || c == ':' || c == '{' || c == '}' || c == '[' || c == ']') {
                    sb.append(c);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
