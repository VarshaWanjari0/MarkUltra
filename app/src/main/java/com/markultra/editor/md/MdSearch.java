package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Literal and regular-expression search used by the in-editor find bar. */
public final class MdSearch {

    public static final class Hit {
        public final int start;
        public final int end;
        public final int line;
        public final String preview;

        Hit(int start, int end, int line, String preview) {
            this.start = start;
            this.end = end;
            this.line = line;
            this.preview = preview;
        }
    }

    private MdSearch() {
    }

    public static List<Hit> findAll(String text, String query, boolean caseSensitive,
                                    boolean wholeWord, boolean regex) {
        List<Hit> hits = new ArrayList<Hit>();
        if (text == null || query == null || query.length() == 0) return hits;
        Pattern p;
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            String q = regex ? query : Pattern.quote(query);
            if (wholeWord) q = "\\b" + q + "\\b";
            p = Pattern.compile(q, flags);
        } catch (Exception e) {
            return hits;
        }
        Matcher m = p.matcher(text);
        int from = 0;
        int guard = 0;
        while (m.find(from) && guard++ < 5000) {
            int s = m.start();
            int e = m.end();
            if (e == s) {
                from = e + 1;
                continue;
            }
            hits.add(new Hit(s, e, lineOf(text, s), preview(text, s, e)));
            from = e;
        }
        return hits;
    }

    public static int lineOf(String text, int offset) {
        int line = 1;
        int end = Math.min(offset, text.length());
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    public static int lineStart(String text, int offset) {
        int i = Math.min(offset, text.length()) - 1;
        while (i >= 0 && text.charAt(i) != '\n') i--;
        return i + 1;
    }

    public static int lineEnd(String text, int offset) {
        int i = Math.max(0, Math.min(offset, text.length()));
        while (i < text.length() && text.charAt(i) != '\n') i++;
        return i;
    }

    private static String preview(String text, int start, int end) {
        int from = Math.max(0, start - 24);
        int to = Math.min(text.length(), end + 24);
        String s = text.substring(from, to).replace('\n', ' ');
        return (from > 0 ? "..." : "") + s.trim() + (to < text.length() ? "..." : "");
    }

    /** Replaces every match; used by find &amp; replace. */
    public static String replaceAll(String text, String query, String replacement,
                                    boolean caseSensitive, boolean wholeWord, boolean regex) {
        if (text == null || query == null || query.length() == 0) return text == null ? "" : text;
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            String q = regex ? query : Pattern.quote(query);
            if (wholeWord) q = "\\b" + q + "\\b";
            return Pattern.compile(q, flags).matcher(text)
                    .replaceAll(regex ? replacement : Matcher.quoteReplacement(replacement));
        } catch (Exception e) {
            return text;
        }
    }
}
