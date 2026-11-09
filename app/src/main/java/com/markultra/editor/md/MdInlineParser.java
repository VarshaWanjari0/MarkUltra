package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact GitHub-Flavored-Markdown inline parser.
 *
 * <p>Produces marker-free plain text plus {@link MdSpan} instructions with exact
 * offsets, which keeps the Android renderer trivial and fast.</p>
 *
 * <p>Supported inline syntax: backslash escapes, code spans, images, inline links,
 * reference links, footnote references, autolinks (&lt;https://...&gt;, &lt;mail@host&gt;),
 * bare URLs, bold, italic, combined bold+italic, strikethrough, ==highlight==,
 * HTML entities and (optionally) smart typography.</p>
 *
 * <p>Pure Java, zero Android imports, zero regular expressions on the hot path:
 * unit-testable on a desktop JVM and cheap on low-end devices.</p>
 */
public final class MdInlineParser {

    /** Reference definitions: normalized label -> url. */
    public interface Refs {
        String lookup(String normalizedLabel);
    }

    private static final int MAX_SCAN = 6000;
    private static final int MAX_DEPTH = 12;

    private String s;
    private int n;
    private Refs refs;
    private boolean smart;

    /** Open inline-HTML formatting tags: parallel stacks of span type and output offset. */
    private int[] htmlType = new int[4];
    private int[] htmlStart = new int[4];
    private int htmlDepth;
    private int work;

    public static InlineResult parse(String source) {
        return parse(source, null, false);
    }

    public static InlineResult parse(String source, Refs refs) {
        return parse(source, refs, false);
    }

    public static InlineResult parse(String source, Refs refs, boolean smartQuotes) {
        String src = source == null ? "" : source;
        MdInlineParser p = new MdInlineParser();
        p.s = src;
        p.n = src.length();
        p.refs = refs;
        p.smart = smartQuotes;
        StringBuilder out = new StringBuilder(src.length() + 24);
        List<MdSpan> spans = new ArrayList<MdSpan>(8);
        p.inline(0, p.n, out, spans, 0);
        p.closeOpenHtml(out, spans);
        return new InlineResult(out.toString(), spans);
    }

    /** True when {@code tag} is a well-formed inline HTML tag body: name plus attributes. */
    private static boolean isHtmlTagName(String tag) {
        if (tag.length() == 0) return false;
        int i = 0;
        if (tag.charAt(0) == '/') i = 1;
        if (i >= tag.length()) return false;
        char first = tag.charAt(i);
        if (!Character.isLetter(first)) return false;
        int nameEnd = i;
        while (nameEnd < tag.length()) {
            char c = tag.charAt(nameEnd);
            if (Character.isLetterOrDigit(c) || c == '-') nameEnd++;
            else break;
        }
        String name = tag.substring(i, nameEnd);
        String rest = tag.substring(nameEnd);
        // "<b>", "<b/>", "<b class=x>" -- but never the "<b@host" of a malformed autolink
        return rest.length() == 0 || rest.equals("/") || rest.startsWith("/")
                || rest.charAt(0) == ' ' || rest.charAt(0) == '\t';
    }

    /** True for a {@code $...$} maths run at {@code i}. */
    private boolean isInlineMathStart(int i, int to) {
        if (i + 2 >= to) return false;
        char after = s.charAt(i + 1);
        if (after == ' ' || after == '\t' || after == '$' || after == '\n') return false;
        if (i > 0 && s.charAt(i - 1) == '\\') return false;
        int close = s.indexOf('$', i + 1);
        if (close < 0 || close >= to) return false;
        if (close == i + 1) return false;
        if (s.charAt(close - 1) == ' ') return false;
        String body = s.substring(i + 1, close);
        return body.indexOf('\n') < 0 && MdMath.looksLikeMath(body);
    }

    /** Applies one inline HTML tag, dropping the markup and keeping the text. */
    private void htmlTag(String tag, StringBuilder out, List<MdSpan> spans) {
        boolean closing = tag.charAt(0) == '/';
        int i = closing ? 1 : 0;
        int nameEnd = i;
        while (nameEnd < tag.length()) {
            char c = tag.charAt(nameEnd);
            if (Character.isLetterOrDigit(c) || c == '-') nameEnd++;
            else break;
        }
        String name = tag.substring(i, nameEnd).toLowerCase(java.util.Locale.US);
        boolean selfClosing = tag.endsWith("/");

        if (name.equals("br")) {
            out.append('\n');
            return;
        }
        if (name.equals("wbr")) return;

        int type = htmlSpanType(name);
        if (type == 0) return;          // unknown tag: GitHub drops the markup only

        if (closing) {
            for (int k = htmlDepth - 1; k >= 0; k--) {
                if (htmlType[k] != type) continue;
                if (k == htmlDepth - 1) {
                    emitHtmlSpan(out, spans, htmlType[k], htmlStart[k]);
                    htmlDepth--;
                }
                return;
            }
            return;
        }
        if (selfClosing) {
            int st = out.length();
            emitHtmlSpan(out, spans, type, st);   // empty element, nothing to style
            return;
        }
        if (htmlDepth == htmlType.length) {
            htmlType = java.util.Arrays.copyOf(htmlType, htmlDepth * 2);
            htmlStart = java.util.Arrays.copyOf(htmlStart, htmlDepth * 2);
        }
        htmlType[htmlDepth] = type;
        htmlStart[htmlDepth] = out.length();
        htmlDepth++;
    }

    private static int htmlSpanType(String name) {
        if (name.equals("b") || name.equals("strong")) return MdSpan.BOLD;
        if (name.equals("i") || name.equals("em")) return MdSpan.ITALIC;
        if (name.equals("del") || name.equals("s") || name.equals("strike")) return MdSpan.STRIKE;
        if (name.equals("code") || name.equals("kbd") || name.equals("samp") || name.equals("tt")) {
            return MdSpan.CODE;
        }
        if (name.equals("mark")) return MdSpan.HIGHLIGHT;
        return 0;
    }

    private static void emitHtmlSpan(StringBuilder out, List<MdSpan> spans, int type, int from) {
        int end = out.length();
        if (end > from) spans.add(new MdSpan(type, from, end, ""));
    }

    private void closeOpenHtml(StringBuilder out, List<MdSpan> spans) {
        while (htmlDepth > 0) {
            htmlDepth--;
            emitHtmlSpan(out, spans, htmlType[htmlDepth], htmlStart[htmlDepth]);
        }
    }

    // ---------------------------------------------------------------------------------
    // main inline loop
    // ---------------------------------------------------------------------------------

    private void inline(int from, int to, StringBuilder out, List<MdSpan> spans, int depth) {
        if (to > n) to = n;
        int i = from;
        while (i < to) {
            char c = s.charAt(i);

            // ---- escapes ---------------------------------------------------------
            if (c == '\\' && i + 1 < to && isAsciiPunct(s.charAt(i + 1))) {
                out.append(s.charAt(i + 1));
                i += 2;
                continue;
            }

            // ---- code span -------------------------------------------------------
            if (c == '`') {
                int run = runLen(i, to, '`');
                int close = findBacktickClose(i + run, to, run);
                if (close > 0) {
                    int a = i + run;
                    int b = close;
                    if (b - a >= 2 && s.charAt(a) == ' ' && s.charAt(b - 1) == ' ') {
                        a++;
                        b--;
                    }
                    int st = out.length();
                    out.append(s, a, b);
                    spans.add(new MdSpan(MdSpan.CODE, st, out.length(), ""));
                    i = close + run;
                    continue;
                }
                appendRun(out, '`', run);
                i += run;
                continue;
            }

            // ---- image -----------------------------------------------------------
            if (c == '!' && i + 1 < to && s.charAt(i + 1) == '[') {
                int end = tryLink(out, spans, i, true, to, depth);
                if (end > 0) {
                    i = end;
                    continue;
                }
                out.append('!');
                i++;
                continue;
            }

            // ---- link / footnote -------------------------------------------------
            if (c == '[') {
                int end = tryLink(out, spans, i, false, to, depth);
                if (end > 0) {
                    i = end;
                    continue;
                }
                if (i + 2 < to && s.charAt(i + 1) == '^') {
                    int close = indexOf(']', i + 2, to);
                    if (close > i + 2 && close - (i + 2) <= 80) {
                        String id = s.substring(i + 2, close);
                        int st = out.length();
                        out.append(id);
                        spans.add(new MdSpan(MdSpan.FOOTNOTE, st, out.length(), id));
                        i = close + 1;
                        continue;
                    }
                }
                out.append('[');
                i++;
                continue;
            }

            // ---- angle autolinks -------------------------------------------------
            if (c == '<') {
                int close = indexOf('>', i + 1, to);
                if (close > i + 1 && close - i <= 500) {
                    String inner = s.substring(i + 1, close);
                    if (isAbsoluteUrl(inner)) {
                        int st = out.length();
                        out.append(inner);
                        spans.add(new MdSpan(MdSpan.AUTOLINK, st, out.length(), inner));
                        i = close + 1;
                        continue;
                    }
                    if (isEmail(inner)) {
                        int st = out.length();
                        out.append(inner);
                        spans.add(new MdSpan(MdSpan.AUTOLINK, st, out.length(), "mailto:" + inner));
                        i = close + 1;
                        continue;
                    }
                }
            }

            // ---- inline maths ($E = mc^2$) ---------------------------------------
            if (c == '$' && isInlineMathStart(i, to)) {
                int close = s.indexOf('$', i + 1);
                String tex = s.substring(i + 1, close);
                String plain = MdMath.toUnicode(tex);
                int st = out.length();
                out.append(plain);
                if (out.length() > st) {
                    spans.add(new MdSpan(MdSpan.MATH, st, out.length(), tex));
                }
                i = close + 1;
                continue;
            }

            // ---- inline HTML ------------------------------------------------------
            // GitHub keeps the text of inline HTML and applies the formatting tags.
            // Autolinks were tried first, so <https://x> never reaches this branch.
            if (c == '<') {
                int tagClose = indexOf('>', i + 1, to);
                if (tagClose > i + 1 && tagClose - i <= 200) {
                    String tag = s.substring(i + 1, tagClose).trim();
                    if (isHtmlTagName(tag)) {
                        htmlTag(tag, out, spans);
                        i = tagClose + 1;
                        continue;
                    }
                }
            }

            // ---- bare url --------------------------------------------------------
            if ((c == 'h' || c == 'H') && isUrlBoundary(i, from) && i + 8 < to) {
                String url = scanBareUrl(i, to);
                if (url != null) {
                    int st = out.length();
                    out.append(url);
                    spans.add(new MdSpan(MdSpan.AUTOLINK, st, out.length(), url));
                    i += url.length();
                    continue;
                }
            }

            // ---- GFM www. autolink -----------------------------------------------
            if ((c == 'w' || c == 'W') && isUrlBoundary(i, from) && i + 5 < to
                    && (s.startsWith("www.", i) || s.startsWith("WWW.", i))) {
                int j = i;
                while (j < to) {
                    char w = s.charAt(j);
                    if (Character.isWhitespace(w) || w == '<' || w == '"' || w == '\''
                            || w == '`' || w == '|' || w == ']' || w == '}') break;
                    j++;
                }
                while (j > i) {
                    char last = s.charAt(j - 1);
                    if (last == '.' || last == ',' || last == ';' || last == ':'
                            || last == '!' || last == '?' || last == ')') {
                        j--;
                    } else {
                        break;
                    }
                }
                String host = s.substring(i, j);
                if (host.indexOf('.', 4) >= 0) {
                    int st = out.length();
                    out.append(host);
                    spans.add(new MdSpan(MdSpan.AUTOLINK, st, out.length(), "https://" + host));
                    i = j;
                    continue;
                }
            }

            // ---- GFM emoji shortcode ---------------------------------------------
            if (c == ':' && i + 2 < to) {
                int end = -1;
                for (int k = i + 1; k < to && k - i <= 32; k++) {
                    char e = s.charAt(k);
                    if (e == ':') {
                        end = k;
                        break;
                    }
                    boolean ok = (e >= 'a' && e <= 'z') || (e >= 'A' && e <= 'Z')
                            || (e >= '0' && e <= '9') || e == '_' || e == '+' || e == '-';
                    if (!ok) break;
                }
                if (end > i + 1) {
                    String code = s.substring(i + 1, end).toLowerCase(java.util.Locale.US);
                    String emoji = MdEmoji.lookup(code);
                    if (emoji != null) {
                        out.append(emoji);
                        i = end + 1;
                        continue;
                    }
                }
            }

            // ---- strikethrough ---------------------------------------------------
            if (c == '~' && i + 1 < to && s.charAt(i + 1) == '~') {
                int close = findPairClose(i + 2, to, "~~");
                if (close > i + 2) {
                    if (depth < MAX_DEPTH) {
                        int st = out.length();
                        inline(i + 2, close, out, spans, depth + 1);
                        spans.add(new MdSpan(MdSpan.STRIKE, st, out.length(), ""));
                    } else {
                        out.append(s, i + 2, close);
                    }
                    i = close + 2;
                    continue;
                }
            }

            // ---- highlight -------------------------------------------------------
            if (c == '=' && i + 1 < to && s.charAt(i + 1) == '=') {
                int close = findPairClose(i + 2, to, "==");
                if (close > i + 2) {
                    if (depth < MAX_DEPTH) {
                        int st = out.length();
                        inline(i + 2, close, out, spans, depth + 1);
                        spans.add(new MdSpan(MdSpan.HIGHLIGHT, st, out.length(), ""));
                    } else {
                        out.append(s, i + 2, close);
                    }
                    i = close + 2;
                    continue;
                }
            }

            // ---- emphasis --------------------------------------------------------
            if ((c == '*' || c == '_') && depth < MAX_DEPTH) {
                int run = runLen(i, to, c);
                char prev = i > from ? s.charAt(i - 1) : 0;
                char next = i + run < to ? s.charAt(i + run) : 0;
                if (canOpen(c, prev, next)) {
                    int[] closer = findEmphasisCloser(i + run, to, c, run);
                    if (closer != null) {
                        int closerStart = closer[0];
                        int closerRun = closer[1];
                        int use = Math.min(run, closerRun);
                        if (use > 3) use = 3;
                        int st = out.length();
                        if (depth < MAX_DEPTH) {
                            inline(i + run, closerStart, out, spans, depth + 1);
                        } else {
                            out.append(s, i + run, closerStart);
                        }
                        int en = out.length();
                        if (use >= 2) spans.add(new MdSpan(MdSpan.BOLD, st, en, ""));
                        if (use == 1 || use >= 3) spans.add(new MdSpan(MdSpan.ITALIC, st, en, ""));
                        int leftoverOpen = run - use;
                        int leftoverClose = closerRun - use;
                        if (leftoverOpen > 0) {
                            // keep the surplus markers in front of the content
                            StringBuilder literal = new StringBuilder(leftoverOpen);
                            appendRun(literal, c, leftoverOpen);
                            insertBefore(out, spans, st, literal);
                        }
                        for (int k = 0; k < leftoverClose; k++) out.append(c);
                        i = closerStart + closerRun;
                        continue;
                    }
                }
                appendRun(out, c, run);
                i += run;
                continue;
            }

            // ---- entities / smart punctuation ------------------------------------
            if (c == '&') {
                int next = tryEntity(out, i, to);
                if (next > 0) {
                    i = next;
                    continue;
                }
            }
            if (smart) {
                int next = trySmart(out, i, to, from);
                if (next > 0) {
                    i = next;
                    continue;
                }
            }

            // ---- line breaks -----------------------------------------------------
            // A trailing backslash or two trailing spaces produce a hard break and a
            // plain newline is a soft break; github.com shows both as a line break.
            if (c == '\n') {
                int len = out.length();
                int trimTo = len;
                while (trimTo > 0 && out.charAt(trimTo - 1) == ' ') trimTo--;
                boolean hardSpaces = trimTo > 0 && len - trimTo >= 2;
                if (hardSpaces) {
                    out.setLength(trimTo);
                    out.append('\n');
                } else if (len > 0 && out.charAt(len - 1) == '\\') {
                    out.setLength(len - 1);
                    out.append('\n');
                } else {
                    // GitHub emits a newline here and HTML collapses it, so a soft wrap
                    // inside a paragraph shows as a space, not a line break.
                    out.append(' ');
                }
                i++;
                while (i < to && s.charAt(i) == ' ') i++;
                continue;
            }

            out.append(c);
            i++;
        }
    }

    // ---------------------------------------------------------------------------------
    // emphasis
    // ---------------------------------------------------------------------------------

    private static boolean isWs(char c) {
        return c == 0 || Character.isWhitespace(c);
    }

    private static boolean isPunct(char c) {
        return c != 0 && (isAsciiPunct(c) || !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
    }

    private static boolean leftFlanking(char openChar, char prev, char next) {
        return !isWs(next) && (!isPunct(next) || isWs(prev) || isPunct(prev));
    }

    private static boolean rightFlanking(char openChar, char prev, char next) {
        return !isWs(prev) && (!isPunct(prev) || isWs(next) || isPunct(next));
    }

    static boolean canOpen(char ch, char prev, char next) {
        boolean lf = leftFlanking(ch, prev, next);
        boolean rf = rightFlanking(ch, prev, next);
        if (ch == '_') return lf && (!rf || isPunct(prev));
        return lf;
    }

    static boolean canClose(char ch, char prev, char next) {
        boolean lf = leftFlanking(ch, prev, next);
        boolean rf = rightFlanking(ch, prev, next);
        if (ch == '_') return rf && (!lf || isPunct(next));
        return rf;
    }

    /**
     * Locates the best closing run for an opening delimiter run.
     * A run prefers a closer of the same parity (single '*' closes a single '*'),
     * which stops an outer emphasis from swallowing an inner bold run.
     *
     * @return {closerStart, closerRunLength} or null
     */
    private int[] findEmphasisCloser(int from, int to, char ch, int openRun) {
        int[] fallback = null;
        boolean wantOdd = (openRun % 2) == 1;
        int budget = MAX_SCAN;
        int j = from;
        while (j < to && budget-- > 0) {
            char c = s.charAt(j);
            if (c == '\\') { j += 2; continue; }
            if (c == '`') {
                int run = runLen(j, to, '`');
                int close = findBacktickClose(j + run, to, run);
                j = close > 0 ? close + run : j + run;
                continue;
            }
            if (c == ch) {
                int run = runLen(j, to, ch);
                char prev = s.charAt(j - 1);
                char next = j + run < to ? s.charAt(j + run) : 0;
                if (canClose(ch, prev, next)) {
                    boolean odd = (run % 2) == 1;
                    if (wantOdd && odd) return new int[]{j, run};
                    if (!wantOdd && !odd) return new int[]{j, run};
                    if (fallback == null) fallback = new int[]{j, run};
                }
                j += run;
                continue;
            }
            j++;
        }
        return fallback;
    }

    /** Nearest unescaped, non-empty {@code delim} pair body; index of opening delim. */
    private int findPairClose(int from, int to, String delim) {
        int budget = MAX_SCAN;
        int j = from;
        while (j < to - 1 && budget-- > 0) {
            if (s.charAt(j) == '\\') { j += 2; continue; }
            if (s.startsWith(delim, j)) {
                if (j > from && !Character.isWhitespace(s.charAt(j - 1))) return j;
                j += delim.length();
                continue;
            }
            j++;
        }
        return -1;
    }

    // ---------------------------------------------------------------------------------
    // links & images
    // ---------------------------------------------------------------------------------

    /** @return index just past the construct, or -1 when it is not a link. */
    private int tryLink(StringBuilder out, List<MdSpan> spans, int start,
                        boolean image, int to, int depth) {
        int bracket = image ? start + 1 : start;
        if (bracket >= to || s.charAt(bracket) != '[') return -1;
        int close = findMatching(']', bracket, to);
        if (close < 0) return -1;
        String label = s.substring(bracket + 1, close);

        String url = null;
        int end = -1;

        int after = close + 1;
        if (after < to && s.charAt(after) == '(') {
            int paren = findMatching(')', after, to);
            if (paren > 0) {
                url = parseDestination(s.substring(after + 1, paren));
                end = paren + 1;
            }
        }
        if (url == null && after < to && s.charAt(after) == '[') {
            int close2 = indexOf(']', after + 1, to);
            if (close2 > 0) {
                String refLabel = s.substring(after + 1, close2);
                if (refLabel.length() == 0) refLabel = label;
                if (refs != null) url = refs.lookup(normalizeLabel(refLabel));
                if (url != null) end = close2 + 1;
            }
        }
        if (url == null && refs != null && label.length() > 0) {
            url = refs.lookup(normalizeLabel(label));
            if (url != null) end = after;
        }
        if (url == null) return -1;

        int st = out.length();
        if (image) {
            String alt = label.length() == 0 ? "image" : label;
            out.append('\u2B1C');              // frame marker, replaced by an icon later
            out.append(' ');
            out.append(alt);
            spans.add(new MdSpan(MdSpan.IMAGE, st, out.length(), url));
        } else {
            if (depth < MAX_DEPTH) {
                inline(bracket + 1, close, out, spans, depth + 1);
            } else {
                out.append(label);
            }
            spans.add(new MdSpan(MdSpan.LINK, st, out.length(), url));
        }
        return end;
    }

    private static String parseDestination(String inner) {
        String t = inner.trim();
        if (t.length() == 0) return "";
        if (t.charAt(0) == '<') {
            int gt = t.indexOf('>');
            if (gt > 0) return t.substring(1, gt);
        }
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"' || c == '\'') quoted = !quoted;
            else if (!quoted && c == '(') depth++;
            else if (!quoted && c == ')') depth--;
            else if (!quoted && depth == 0 && (c == ' ' || c == '\t')) return t.substring(0, i);
        }
        return t;
    }

    /** Finds the matching closing char, skipping escapes, code spans and nesting. */
    private int findMatching(char close, int open, int to) {
        char openChar = s.charAt(open);
        int depth = 0;
        int i = open;
        while (i < to) {
            char c = s.charAt(i);
            if (c == '\\') { i += 2; continue; }
            if (c == '`') {
                int run = runLen(i, to, '`');
                int cl = findBacktickClose(i + run, to, run);
                if (cl > 0) { i = cl + run; continue; }
                i += run;
                continue;
            }
            if (c == openChar) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    // ---------------------------------------------------------------------------------
    // small utilities
    // ---------------------------------------------------------------------------------

    private static void insertBefore(StringBuilder out, List<MdSpan> spans, int at, CharSequence text) {
        out.insert(at, text);
        int len = text.length();
        for (int i = 0; i < spans.size(); i++) {
            MdSpan sp = spans.get(i);
            if (sp.start >= at) {
                sp.start += len;
                sp.end += len;
            } else if (sp.end > at) {
                sp.end += len;
            }
        }
    }

    private int tryEntity(StringBuilder out, int i, int to) {
        int semi = indexOf(';', i + 1, to);
        if (semi < 0 || semi - i > 10) return -1;
        String name = s.substring(i + 1, semi);
        String rep = null;
        try {
            if (name.startsWith("#x") || name.startsWith("#X")) {
                rep = new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
            } else if (name.startsWith("#")) {
                rep = new String(Character.toChars(Integer.parseInt(name.substring(1))));
            }
        } catch (Exception ignored) {
            return -1;
        }
        if (rep == null) {
            if ("amp".equals(name)) rep = "&";
            else if ("lt".equals(name)) rep = "<";
            else if ("gt".equals(name)) rep = ">";
            else if ("quot".equals(name)) rep = "\"";
            else if ("apos".equals(name)) rep = "'";
            else if ("nbsp".equals(name)) rep = "\u00A0";
            else if ("copy".equals(name)) rep = "\u00A9";
            else if ("reg".equals(name)) rep = "\u00AE";
            else if ("trade".equals(name)) rep = "\u2122";
            else if ("hellip".equals(name)) rep = "\u2026";
            else if ("mdash".equals(name)) rep = "\u2014";
            else if ("ndash".equals(name)) rep = "\u2013";
            else if ("laquo".equals(name)) rep = "\u00AB";
            else if ("raquo".equals(name)) rep = "\u00BB";
            else if ("times".equals(name)) rep = "\u00D7";
            else if ("divide".equals(name)) rep = "\u00F7";
            else if ("deg".equals(name)) rep = "\u00B0";
            else if ("plusmn".equals(name)) rep = "\u00B1";
            else if ("le".equals(name)) rep = "\u2264";
            else if ("ge".equals(name)) rep = "\u2265";
            else if ("ne".equals(name)) rep = "\u2260";
            else if ("rarr".equals(name)) rep = "\u2192";
            else if ("larr".equals(name)) rep = "\u2190";
            else if ("check".equals(name)) rep = "\u2713";
        }
        if (rep == null) return -1;
        out.append(rep);
        return semi + 1;
    }

    private int trySmart(StringBuilder out, int i, int to, int from) {
        char c = s.charAt(i);
        char prev = i > from ? s.charAt(i - 1) : 0;
        if (c == '"') {
            boolean opening = isWs(prev) || "([{\u2014".indexOf(prev) >= 0;
            out.append(opening ? '\u201C' : '\u201D');
            return i + 1;
        }
        if (c == '\'') {
            boolean between = i > 0 && i + 1 < to
                    && Character.isLetterOrDigit(s.charAt(i - 1))
                    && Character.isLetterOrDigit(s.charAt(i + 1));
            if (between) {
                out.append('\u2019');
            } else {
                out.append(isWs(prev) ? '\u2018' : '\u2019');
            }
            return i + 1;
        }
        if (c == '-' && i + 2 < to && s.charAt(i + 1) == '-' && s.charAt(i + 2) == '-'
                && (i + 3 >= to || s.charAt(i + 3) != '-')) {
            out.append('\u2014');
            return i + 3;
        }
        if (c == '-' && i + 1 < to && s.charAt(i + 1) == '-'
                && (i + 2 >= to || s.charAt(i + 2) != '-')) {
            out.append('\u2013');
            return i + 2;
        }
        if (c == '.' && i + 2 < to && s.charAt(i + 1) == '.' && s.charAt(i + 2) == '.') {
            out.append('\u2026');
            return i + 3;
        }
        return -1;
    }

    private boolean isAbsoluteUrl(String u) {
        return u.startsWith("http://") || u.startsWith("https://")
                || u.startsWith("mailto:") || u.startsWith("ftp://")
                || u.startsWith("file://") || u.startsWith("tel:");
    }

    private boolean isEmail(String u) {
        int at = u.indexOf('@');
        if (at <= 0 || at == u.length() - 1) return false;
        if (u.indexOf(' ', at) >= 0) return false;
        return u.lastIndexOf('.') > at;
    }

    private boolean isUrlBoundary(int i, int from) {
        if (i <= from) return true;
        char p = s.charAt(i - 1);
        return !(Character.isLetterOrDigit(p) || p == '/' || p == '.' || p == '_' || p == '-'
                || p == '@' || p == ':');
    }

    /** @return the trimmed URL text starting at {@code i}, or null. */
    private String scanBareUrl(int i, int to) {
        boolean secure = true;
        if (!s.startsWith("https://", i) && !s.startsWith("HTTPS://", i)) {
            if (!s.startsWith("http://", i) && !s.startsWith("HTTP://", i)) return null;
            secure = false;
        }
        int schemeLen = secure ? 8 : 7;
        int j = i + schemeLen;
        int begin = j;
        while (j < to) {
            char c = s.charAt(j);
            if (Character.isWhitespace(c) || c == '<' || c == '"' || c == '\''
                    || c == '`' || c == '|' || c == ']' || c == '}') break;
            if (c == ')' && parenDepth(i, j) == 0) break;
            j++;
        }
        if (j - begin < 3) return null;
        // sentence punctuation is not part of the url
        while (j > begin) {
            char last = s.charAt(j - 1);
            if (last == '.' || last == ',' || last == ';' || last == ':' || last == '!'
                    || last == '?' || last == ')' || last == '*') {
                j--;
            } else {
                break;
            }
        }
        if (j - begin < 3) return null;
        return s.substring(i, j);
    }

    private int parenDepth(int from, int to) {
        int d = 0;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c == '(') d++;
            else if (c == ')') d--;
        }
        return d;
    }

    private int indexOf(char ch, int from, int to) {
        for (int i = from; i < to; i++) if (s.charAt(i) == ch) return i;
        return -1;
    }

    private int runLen(int from, int to, char ch) {
        int i = from;
        while (i < to && s.charAt(i) == ch) i++;
        return i - from;
    }

    private int findBacktickClose(int from, int to, int run) {
        int i = from;
        while (i < to) {
            if (s.charAt(i) == '`') {
                int r = runLen(i, to, '`');
                if (r == run) return i;
                i += r;
            } else {
                i++;
            }
        }
        return -1;
    }

    private static void appendRun(StringBuilder out, char ch, int count) {
        for (int i = 0; i < count; i++) out.append(ch);
    }

    static String normalizeLabel(String label) {
        StringBuilder sb = new StringBuilder(label.length());
        boolean space = false;
        for (int i = 0; i < label.length(); i++) {
            char c = Character.toLowerCase(label.charAt(i));
            if (Character.isWhitespace(c)) {
                space = true;
            } else {
                if (space && sb.length() > 0) sb.append(' ');
                space = false;
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    static boolean isAsciiPunct(char c) {
        return (c >= '!' && c <= '/') || (c >= ':' && c <= '@')
                || (c >= '[' && c <= '`') || (c >= '{' && c <= '~');
    }
}
