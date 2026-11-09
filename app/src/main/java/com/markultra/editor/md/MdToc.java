package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Table-of-contents extraction and generation. */
public final class MdToc {

    public static final class Entry {
        public final int level;
        public final String title;
        public final String anchor;
        public final int line;
        public final int offset;

        Entry(int level, String title, String anchor, int line, int offset) {
            this.level = level;
            this.title = title;
            this.anchor = anchor;
            this.line = line;
            this.offset = offset;
        }
    }

    private MdToc() {
    }

    public static List<Entry> entries(String markdown) {
        MdBlockParser.Result parsed = MdBlockParser.parse(markdown);
        return entries(parsed);
    }

    public static List<Entry> entries(MdBlockParser.Result parsed) {
        List<Entry> out = new ArrayList<Entry>();
        Map<String, Integer> used = new HashMap<String, Integer>();
        for (int i = 0; i < parsed.blocks.size(); i++) {
            MdBlock b = parsed.blocks.get(i);
            if (b.type != MdBlock.HEADING) continue;
            String plain = plain(b);
            if (plain.length() == 0) continue;
            String anchor = uniqueAnchor(plain, used);
            out.add(new Entry(b.level, plain, anchor, b.lineStart, b.srcStart));
        }
        return out;
    }

    /** Markdown for a TOC limited to the first {@code maxLevel} heading levels. */
    public static String toMarkdown(List<Entry> entries, int maxLevel) {
        StringBuilder sb = new StringBuilder(entries.size() * 40);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.level > maxLevel) continue;
            for (int k = 1; k < e.level; k++) sb.append("  ");
            sb.append("- [").append(escapeLinkText(e.title)).append("](#").append(e.anchor).append(")\n");
        }
        return sb.toString();
    }

    /** Line indentation map for the outline dialog. */
    public static String indentFor(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < level; i++) sb.append("    ");
        return sb.toString();
    }

    static String plain(MdBlock b) {
        InlineResult r = MdInlineParser.parse(b.text == null ? "" : b.text);
        String s = r.text.replace('\n', ' ').trim();
        while (s.contains("  ")) s = s.replace("  ", " ");
        return s;
    }

    static String uniqueAnchor(String title, Map<String, Integer> used) {
        String base = slug(title);
        Integer count = used.get(base);
        if (count == null) {
            used.put(base, 1);
            return base;
        }
        int next = count + 1;
        used.put(base, next);
        return base + "-" + next;
    }

    /** GitHub-compatible anchor slug. */
    public static String slug(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean dash = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                dash = false;
            } else if (c == ' ' || c == '-' || c == '_') {
                if (!dash && sb.length() > 0) {
                    sb.append('-');
                    dash = true;
                }
            }
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '-') end--;
        sb.setLength(end);
        return sb.length() == 0 ? "section" : sb.toString();
    }

    private static String escapeLinkText(String s) {
        return s.replace("[", "\\[").replace("]", "\\]");
    }
}
