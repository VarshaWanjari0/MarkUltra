package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parts of Pandoc-flavoured Markdown that GitHub does not have, and that real
 * documents from the wild contain anyway.
 *
 * <p>Everything here runs <em>before</em> flexmark, on the raw source, and produces either
 * standard Markdown or the same {@code <math-renderer>} element the rest of the pipeline
 * uses. Nothing is removed: a construct this class does not understand is left exactly as
 * it was, so the fallback is always "the user sees their text", never "the line vanished".</p>
 *
 * <ul>
 *   <li><b>Raw attribute spans</b> - {@code `\sum`{=tex}} and {@code `<kbd>`{=html}}: the
 *       content is taken as raw TeX or raw HTML, as Pandoc does.</li>
 *   <li><b>Raw attribute blocks</b> - {@code ```{=tex}} and {@code ```{=html}} fences.</li>
 *   <li><b>LaTeX delimiters</b> - {@code \[ ... \]} display maths and {@code \( ... \)}
 *       inline maths, including the Pandoc escapes ({@code \_}, {@code \^}) that markdown
 *       round trips leave inside them.</li>
 *   <li><b>Escaped alerts</b> - {@code > \[!NOTE\]}, which is how a document that escaped
 *       its own brackets still means to ask for an alert.</li>
 *   <li><b>Simple and multiline tables</b> - the Pandoc table that has no pipes at all:
 *       a header line, a line of dash runs that define the columns, then rows. They are
 *       converted to GitHub pipe tables so they render as real tables.</li>
 * </ul>
 */
final class PandocCompat {

    private PandocCompat() {
    }

    /** Inline span with a raw attribute: `text`{=tex} or `text`{=html}. */
    private static final Pattern RAW_SPAN = Pattern.compile(
            "`([^`\\n]+)`\\{\\s*=(tex|html|latex)\\s*\\}", Pattern.CASE_INSENSITIVE);
    /** A line of two or more dash runs: the column definition of a Pandoc table. */
    private static final Pattern DASH_RUNS = Pattern.compile("^(\\s*)((?:-{3,}[ \\t]+)+-{3,})\\s*$");
    /** A fence whose info string is a raw attribute. */
    private static final Pattern RAW_FENCE = Pattern.compile(
            "^\\s*(```|~~~)\\s*\\{\\s*=(tex|html|latex)\\s*\\}\\s*$", Pattern.CASE_INSENSITIVE);
    /** A block marker that can never be a table row. */
    private static final Pattern BLOCK_START = Pattern.compile(
            "^\\s*(#{1,6}\\s|```|~~~|>|[-*+]\\s|\\d+[.)]\\s|={3,}\\s*$|<[a-zA-Z/!?])");

    /**
     * Applies every Pandoc compatibility pass.
     *
     * @param markdown the document as the user wrote it
     * @param rich     true when MarkUltra+ syntax is on; escaped alerts are corrected in
     *                 both modes because their intent is unambiguous
     */
    static String apply(String markdown, boolean rich) {
        if (markdown == null || markdown.length() == 0) return "";
        // Alerts first: "> \[!NOTE\]" is an escaped alert, not a maths region.
        String out = unescapeAlerts(markdown);
        out = markTexSpans(out);               // `x`{=tex} -> sentinel-wrapped x
        out = latexDelimiters(out);            // \[ ... \] and \( ... \) -> maths
        out = rawBlocks(out);                  // ```{=tex} / ```{=html} outside a region
        out = finishTexSpans(out);             // any TeX span left -> inline maths
        out = simpleTables(out);
        out = subscripts(out);                 // H~2~O -> H<sub>2</sub>O
        out = frontMatter(out);                // the --- block at the top of the file
        return out;
    }

    /** Marks the content of a raw TeX span so the region pass can tell it apart. */
    private static final char TEX_OPEN = '\u0001';
    private static final char TEX_CLOSE = '\u0002';

    // -------------------------------------------------------------------------------------
    // escaped alert markers
    // -------------------------------------------------------------------------------------

    /**
     * {@code > \[!NOTE\]} means the same as {@code > [!NOTE]}; a document that escaped its
     * brackets lost the alert in every viewer that only knows the plain form.
     */
    private static String unescapeAlerts(String markdown) {
        if (markdown.indexOf('\\') < 0) return markdown;
        String[] alerts = {"NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION"};
        String out = markdown;
        for (int i = 0; i < alerts.length; i++) {
            out = out.replace("\\[!" + alerts[i] + "\\]", "[!" + alerts[i] + "]");
        }
        return out;
    }

    // -------------------------------------------------------------------------------------
    // raw attribute blocks:  ```{=html}  and  ```{=tex}
    // -------------------------------------------------------------------------------------

    /**
     * A {@code {=html}} fence is raw HTML: the fence goes, the content stays and is parsed
     * as HTML. A {@code {=tex}} fence is raw TeX: it becomes a display maths element.
     */
    private static String rawBlocks(String markdown) {
        if (markdown.indexOf("{=") < 0) return markdown;
        String[] lines = markdown.split("\n", -1);
        StringBuilder out = new StringBuilder(markdown.length() + 64);
        int i = 0;
        while (i < lines.length) {
            Matcher fence = RAW_FENCE.matcher(lines[i]);
            if (!fence.matches()) {
                out.append(lines[i]);
                if (i < lines.length - 1) out.append('\n');
                i++;
                continue;
            }
            String marker = fence.group(1);
            String kind = fence.group(2).toLowerCase(Locale.ROOT);
            int end = i + 1;
            while (end < lines.length && !lines[end].trim().startsWith(marker)) end++;
            StringBuilder body = new StringBuilder();
            for (int k = i + 1; k < end; k++) {
                if (body.length() > 0) body.append('\n');
                body.append(lines[k]);
            }
            if (kind.startsWith("tex")) {
                out.append('\n').append(GfmHtml.mathBlock(body.toString().trim(), true))
                        .append("\n\n");
            } else {
                // raw HTML: keep the content, drop the fence, keep the line structure
                out.append(body).append('\n');
            }
            i = Math.min(end + 1, lines.length);
        }
        return out.toString();
    }

    // -------------------------------------------------------------------------------------
    // \[ ... \] and \( ... \)
    // -------------------------------------------------------------------------------------

    /**
     * Converts LaTeX display and inline maths.
     *
     * <p>Inside such a region the Pandoc escapes {@code \_}, {@code \^}, {@code \$},
     * {@code \%}, {@code \&} and {@code \#} are undone, because they exist only to stop
     * Markdown from touching a character that TeX needs. Every other backslash sequence is
     * left alone: {@code \\} in a matrix is a TeX line break and must survive.</p>
     */
    private static String latexDelimiters(String markdown) {
        if (markdown.indexOf("\\[") < 0 && markdown.indexOf("\\(") < 0) return markdown;
        List<int[]> code = codeRanges(markdown);
        String out = convertDelimited(markdown, "\\[", "\\]", true, code);
        code = codeRanges(out);
        out = convertDelimited(out, "\\(", "\\)", false, code);
        return out;
    }

    /**
     * The ranges of the document that are literal code: fenced blocks and inline code
     * spans. TeX delimiters and table rules inside them are text the user wants to see,
     * not syntax, so every pass that rewrites source has to skip these ranges.
     */
    /**
     * True when a line ends a fenced block opened with {@code marker}: the same character,
     * at least as many times, and nothing else on the line. A line such as
     * {@code ```python} opens a new block, it does not close the current one - treating it
     * as a closer puts every later block one fence out of step with the renderer, which
     * then leaves subscripts, tables and maths unprocessed for the rest of the document.
     */
    private static boolean closesFence(String line, String marker) {
        String t = line.trim();
        if (!t.startsWith(marker)) return false;
        for (int k = marker.length(); k < t.length(); k++) {
            if (!Character.isWhitespace(t.charAt(k))) return false;
        }
        return true;
    }

    private static List<int[]> codeRanges(String s) {
        List<int[]> out = new ArrayList<int[]>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            int eol = s.indexOf('\n', i);
            if (eol < 0) eol = n;
            String line = s.substring(i, eol);
            String trim = line.trim();
            if (trim.startsWith("```") || trim.startsWith("~~~")) {
                char f = trim.charAt(0);
                int run = 0;
                while (run < trim.length() && trim.charAt(run) == f) run++;
                String marker = trim.substring(0, run);
                int end = n;
                int j = eol + 1;
                while (j < n) {
                    int e2 = s.indexOf('\n', j);
                    if (e2 < 0) e2 = n;
                    if (closesFence(s.substring(j, e2), marker)) {
                        end = e2;
                        break;
                    }
                    j = e2 + 1;
                }
                out.add(new int[]{i, end});
                i = Math.min(end + 1, n);
                continue;
            }
            int k = 0;
            while (k < line.length()) {
                if (line.charAt(k) == '`') {
                    int run = 0;
                    while (k + run < line.length() && line.charAt(k + run) == '`') run++;
                    String mark = line.substring(k, k + run);
                    int close = line.indexOf(mark, k + run);
                    if (close >= 0) {
                        out.add(new int[]{i + k, i + close + run});
                        k = close + run;
                        continue;
                    }
                    k += run;
                } else {
                    k++;
                }
            }
            i = eol + 1;
        }
        return out;
    }

    private static boolean inside(List<int[]> ranges, int index) {
        for (int i = 0; i < ranges.size(); i++) {
            int[] r = ranges.get(i);
            if (index >= r[0] && index < r[1]) return true;
        }
        return false;
    }

    private static String convertDelimited(String markdown, String open, String close,
                                           boolean display, List<int[]> code) {
        StringBuilder out = new StringBuilder(markdown.length());
        int from = 0;
        while (true) {
            int start = markdown.indexOf(open, from);
            if (start < 0) break;
            if (inside(code, start)) {
                out.append(markdown, from, start + open.length());
                from = start + open.length();
                continue;
            }
            int end = markdown.indexOf(close, start + open.length());
            if (end < 0) break;
            // A display region may wrap lines, but never a paragraph break. Without this
            // rule a stray "\[" - the escaping section of a document is full of them -
            // pairs with the next "\]" hundreds of lines below and swallows the prose
            // between the two into one broken equation.
            if (display) {
                String candidate = markdown.substring(start + open.length(), end);
                if (candidate.indexOf("\n\n") >= 0 || candidate.indexOf("\r\n\r\n") >= 0
                        || candidate.indexOf("\n\r\n") >= 0) {
                    out.append(markdown, from, start + open.length());
                    from = start + open.length();
                    continue;
                }
            }
            int after = end + close.length();
            // "\[not a link\](url)" is an escaped bracket followed by a link target,
            // which is exactly what the escaping section of a document uses it for
            if (after < markdown.length()) {
                char next = markdown.charAt(after);
                if (next == '(' || next == '[' || next == ':') {
                    out.append(markdown, from, after);
                    from = after;
                    continue;
                }
            }
            String tex = cleanRegion(markdown.substring(start + open.length(), end));
            if (tex.trim().length() == 0) {
                out.append(markdown, from, end + close.length());
                from = end + close.length();
                continue;
            }
            out.append(markdown, from, start);
            out.append(display ? "\n" : "");
            out.append(GfmHtml.mathBlock(tex.trim(), display));
            out.append(display ? "\n" : "");
            from = end + close.length();
        }
        out.append(markdown.substring(from));
        return out.toString();
    }

    /**
     * Cleans the inside of a {@code \[ ... \]} or {@code \( ... \)} region: raw TeX
     * fences lose their fence lines (the matrix inside a display block is written that
     * way), TeX span markers disappear, and the markdown escapes are undone.
     */
    private static String cleanRegion(String region) {
        StringBuilder out = new StringBuilder(region.length());
        String[] lines = region.split("\n", -1);
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean fenceLine = RAW_FENCE.matcher(line).matches()
                    || line.trim().startsWith("```") || line.trim().startsWith("~~~");
            if (fenceLine) {
                inFence = !inFence;
                continue;
            }
            if (out.length() > 0 || i > 0) out.append('\n');
            out.append(line);
        }
        String flat = out.toString().replace(String.valueOf(TEX_OPEN), "")
                .replace(String.valueOf(TEX_CLOSE), "");
        return unescapeMath(flat).trim();
    }

    /**
     * Undoes the markdown escapes that only exist because the TeX travelled through
     * Markdown: {@code \_ x \_} is TeX for a subscript, not for a literal underscore.
     */
    static String unescapeMath(String tex) {
        if (tex == null || tex.indexOf('\\') < 0) return tex == null ? "" : tex;
        StringBuilder out = new StringBuilder(tex.length());
        for (int i = 0; i < tex.length(); i++) {
            char c = tex.charAt(i);
            if (c == '\\' && i + 1 < tex.length()) {
                char next = tex.charAt(i + 1);
                if (next == '_' || next == '^' || next == '$' || next == '%' || next == '&'
                        || next == '#') {
                    out.append(next);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    // -------------------------------------------------------------------------------------
    // raw attribute spans:  `\alpha`{=tex}
    // -------------------------------------------------------------------------------------

    /**
     * Unwraps {@code `...`{=tex}} and {@code `...`{=html}}.
     *
     * <p>TeX spans become inline maths and HTML spans become live HTML. The wrapper is
     * removed even when the content is not maths, because the wrapper is what a Pandoc
     * round trip left behind; showing the user backticks and braces would be the bug.</p>
     */
    private static String markTexSpans(String markdown) {
        if (markdown.indexOf("{=") < 0) return markdown;
        Matcher m = RAW_SPAN.matcher(markdown);
        StringBuffer out = new StringBuffer(markdown.length());
        while (m.find()) {
            String content = m.group(1);
            String kind = m.group(2).toLowerCase(Locale.ROOT);
            // raw HTML is live HTML; raw TeX is marked so it can become maths, either as
            // part of a \[ ... \] region or on its own
            String replacement = kind.startsWith("tex")
                    ? TEX_OPEN + content + TEX_CLOSE
                    : content;
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Commands that only set layout in TeX and produce no glyph of their own. */
    private static final String[] LAYOUT_ONLY = {
            "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
            "left", "right", "limits", "nolimits", "quad", "qquad", "big", "Big",
    };

    /**
     * Turns whatever is left of a marked TeX span into inline maths.
     *
     * <p>A span becomes maths when it holds a TeX command that draws something, which is
     * what "`\\alpha`{=tex}" is asking for. Commands that only set layout, such as
     * {@code \\displaystyle}, stay as text: typesetting them would delete the word from
     * the sentence and leave a gap where the reader expects to see something.</p>
     *
     * <p>Pandoc writes a subscript that follows a raw span as {@code \\_{...}} outside the
     * span, so a maths span also absorbs the groups that follow it. That is what turns
     * "`\\sum`{=tex}\\_{i=1}\\^{n}" into one expression instead of a sum followed by a
     * stray underscore.</p>
     */
    private static String finishTexSpans(String markdown) {
        if (markdown.indexOf(TEX_OPEN) < 0) return markdown;
        StringBuilder out = new StringBuilder(markdown.length());
        int i = 0;
        while (i < markdown.length()) {
            char c = markdown.charAt(i);
            if (c != TEX_OPEN) {
                out.append(c);
                i++;
                continue;
            }
            int end = markdown.indexOf(TEX_CLOSE, i + 1);
            if (end < 0) {
                i++;
                continue;
            }
            StringBuilder tex = new StringBuilder(markdown.substring(i + 1, end));
            int pos = end + 1;
            if (!isMathSpan(tex.toString())) {
                out.append(tex);
                i = end + 1;
                continue;
            }
            // absorb subscripts, superscripts and neighbouring spans into this expression
            while (true) {
                if (pos < markdown.length() && markdown.charAt(pos) == TEX_OPEN) {
                    int next = markdown.indexOf(TEX_CLOSE, pos + 1);
                    if (next < 0) break;
                    tex.append(markdown, pos + 1, next);
                    pos = next + 1;
                    continue;
                }
                if (pos < markdown.length() && (markdown.charAt(pos) == '_'
                        || markdown.charAt(pos) == '^')) {
                    tex.append(markdown.charAt(pos));
                    pos++;
                    continue;
                }
                if (pos + 1 < markdown.length() && markdown.charAt(pos) == '\\'
                        && (markdown.charAt(pos + 1) == '_' || markdown.charAt(pos + 1) == '^')) {
                    tex.append(markdown.charAt(pos + 1));
                    pos += 2;
                    continue;
                }
                if (pos < markdown.length() && markdown.charAt(pos) == '{') {
                    int close = matchingBrace(markdown, pos);
                    if (close < 0) break;
                    tex.append(stripMarkers(unescapeMath(markdown.substring(pos, close + 1))));
                    pos = close + 1;
                    continue;
                }
                break;
            }
            out.append(GfmHtml.mathBlock(tex.toString().trim(), false));
            i = pos;
        }
        return out.toString();
    }

    /** Index of the brace that closes the group opened at {@code open}, or -1. */
    private static int matchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            } else if (c == '\n') {
                return -1;
            }
        }
        return -1;
    }

    private static String stripMarkers(String s) {
        return s.replace(String.valueOf(TEX_OPEN), "").replace(String.valueOf(TEX_CLOSE), "");
    }

    /** True when a raw TeX span is meant to be typeset, not quoted. */
    private static boolean isMathSpan(String content) {
        String text = content.trim();
        if (text.length() == 0) return false;
        if (text.indexOf('$') >= 0 || text.indexOf('=') >= 0 || text.indexOf('|') >= 0) return true;
        if (text.indexOf('\\') < 0) return false;
        String bare = text.replace("\\", "").trim();
        for (int i = 0; i < LAYOUT_ONLY.length; i++) {
            if (bare.equals(LAYOUT_ONLY[i])) return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------------------
    // YAML front matter
    // -------------------------------------------------------------------------------------

    /** A line that reads as a YAML key: {@code author: Markdown Renderer QA}. */
    private static final Pattern YAML_KEY = Pattern.compile("^[A-Za-z0-9_.-]+:.*$");

    /**
     * The {@code ---} block many documents start with is metadata, not a horizontal rule
     * followed by a paragraph. It is shown as one block of small monospace text, which is
     * what the rich profile does too, instead of an unterminated pair of rules with the
     * keys spilled into the page. A document that opens with a rule and ordinary prose
     * does not match the key test and is left alone.
     */
    private static String frontMatter(String markdown) {
        if (!markdown.startsWith("---")) return markdown;
        String[] lines = markdown.split("\n", -1);
        if (lines.length < 3 || !lines[0].trim().equals("---")) return markdown;
        int close = -1;
        boolean keyed = false;
        for (int i = 1; i < lines.length; i++) {
            String trim = lines[i].trim();
            if (trim.equals("---") || trim.equals("...")) {
                close = i;
                break;
            }
            if (trim.length() == 0) continue;
            if (YAML_KEY.matcher(trim).matches()) keyed = true;
            else if (!trim.startsWith("-") && !Character.isWhitespace(lines[i].charAt(0))) {
                return markdown;      // not YAML: a prose line that is not indented
            }
            if (i > 80) return markdown;
        }
        if (close < 0 || !keyed) return markdown;
        StringBuilder body = new StringBuilder();
        for (int i = 1; i < close; i++) {
            if (body.length() > 0) body.append('\n');
            body.append(lines[i]);
        }
        StringBuilder out = new StringBuilder(markdown.length());
        out.append("\n<div class=\"yaml-front-matter\">\n<pre>")
                .append(HtmlSanitize.escape(body.toString()))
                .append("</pre>\n</div>\n\n");
        for (int i = close + 1; i < lines.length; i++) {
            out.append(lines[i]);
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    // -------------------------------------------------------------------------------------
    // subscripts
    // -------------------------------------------------------------------------------------

    /** A subscript written the Python-Markdown way: one tilde on each side. */
    private static final Pattern SUBSCRIPT = Pattern.compile(
            "(?<!~)~([^~\\s<](?:[^~\\n<]*[^~\\s<])?)~(?!~)");

    /**
     * {@code H~2~O} is a subscript in Pandoc, Python-Markdown and MultiMarkdown, and
     * flexmark ships an extension for a superscript but none for a subscript, so this
     * pass writes the element the renderer would have written. Strikethrough uses two
     * tildes and is left alone, and so is anything inside a code span or a code block.
     */
    private static String subscripts(String markdown) {
        if (markdown.indexOf('~') < 0) return markdown;
        List<int[]> code = codeRanges(markdown);
        Matcher m = SUBSCRIPT.matcher(markdown);
        StringBuffer out = new StringBuffer(markdown.length() + 32);
        while (m.find()) {
            if (inside(code, m.start())) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                continue;
            }
            m.appendReplacement(out, Matcher.quoteReplacement("<sub>" + m.group(1) + "</sub>"));
        }
        m.appendTail(out);
        return out.toString();
    }

    // -------------------------------------------------------------------------------------
    // Pandoc simple and multiline tables
    // -------------------------------------------------------------------------------------

    /**
     * Converts a table that has no pipes into a GitHub pipe table.
     *
     * <p>Pandoc writes three shapes and all three are in circulation:</p>
     * <ul>
     *   <li><b>simple</b> - a header line, a line of dash runs that fixes the columns, then
     *       one row per line;</li>
     *   <li><b>multiline</b> - the same, wrapped in a full-width rule above and below, and
     *       allowed to hold a header and cells that run over several lines;</li>
     *   <li><b>grid</b> - the same with {@code +} corners, which becomes a plain rule line
     *       once the corners are ignored.</li>
     * </ul>
     *
     * <p>Without this pass the reader sees a paragraph of dashes, the header swallowed as
     * indented code, and every wrapped line as a row of blanks. Column alignment survives:
     * a cell that hugs the right edge of its column stays right aligned.</p>
     */
    private static String simpleTables(String markdown) {
        if (markdown.indexOf("--") < 0) return markdown;
        String[] lines = markdown.split("\n", -1);
        boolean[] fenced = fencedLines(lines);
        StringBuilder out = new StringBuilder(markdown.length() + 128);
        int i = 0;
        while (i < lines.length) {
            if (fenced[i] || !isTableCandidate(lines, i)) {
                out.append(lines[i]);
                if (i < lines.length - 1) out.append('\n');
                i++;
                continue;
            }
            int headerStart = i;
            int ruleIndex = i + 1;
            if (isBorderLine(lines[i])) {
                // the border is the line above the header, not the column rule itself
                ruleIndex = -1;
                for (int k = i + 1; k < Math.min(lines.length, i + 6); k++) {
                    String probe = lines[k];
                    if (probe.trim().length() == 0) break;
                    if (BLOCK_START.matcher(probe).find()) break;
                    if (isBorderLine(probe)) {
                        ruleIndex = k;
                        break;
                    }
                }
                if (ruleIndex < 0) {
                    out.append(lines[i]);
                    if (i < lines.length - 1) out.append('\n');
                    i++;
                    continue;
                }
                headerStart = i + 1;
            }
            int[] bounds = columnBounds(lines[ruleIndex]);
            if (bounds.length < 4) {
                out.append(lines[i]);
                if (i < lines.length - 1) out.append('\n');
                i++;
                continue;
            }
            Table table = buildTable(lines, headerStart, ruleIndex, bounds);
            if (table == null) {
                out.append(lines[i]);
                if (i < lines.length - 1) out.append('\n');
                i++;
                continue;
            }
            out.append(table.pipe);
            i = table.next;
            if (i < lines.length) out.append('\n');
        }
        return out.toString();
    }

    /** A converted pipe table and the index of the first line after it. */
    private static final class Table {
        final String pipe;
        final int next;

        Table(String pipe, int next) {
            this.pipe = pipe;
            this.next = next;
        }
    }

    /** Flags the lines that belong to a fenced code block, fences included. */
    private static boolean[] fencedLines(String[] lines) {
        boolean[] fenced = new boolean[lines.length];
        String open = null;
        for (int i = 0; i < lines.length; i++) {
            String trim = lines[i].trim();
            if (open == null) {
                if (trim.startsWith("```") || trim.startsWith("~~~")) {
                    open = trim.substring(0, 3);
                    fenced[i] = true;
                }
            } else {
                fenced[i] = true;
                if (trim.startsWith(open)) open = null;
            }
        }
        return fenced;
    }

    /** True when line {@code i} belongs to a pipe-less table. */
    private static boolean isTableCandidate(String[] lines, int i) {
        if (i + 1 >= lines.length) return false;
        if (lines[i].trim().length() == 0) return false;
        if (BLOCK_START.matcher(lines[i]).find() && !isBorderLine(lines[i])) return false;
        if (isBorderLine(lines[i])) {
            for (int k = i + 1; k < Math.min(lines.length, i + 6); k++) {
                String probe = lines[k];
                if (probe.trim().length() == 0) return false;
                if (BLOCK_START.matcher(probe).find()) return false;
                if (isBorderLine(probe)) return columnBounds(probe).length >= 4;
            }
            return false;
        }
        return isBorderLine(lines[i + 1]) && columnBounds(lines[i + 1]).length >= 4;
    }

    /**
     * A full-width rule of a table: at least two runs of three or more dashes, possibly
     * with the {@code +} corners and {@code :} alignment marks of a grid table.
     */
    private static boolean isBorderLine(String line) {
        String trimmed = line.trim();
        if (trimmed.length() < 6) return false;
        int runs = 0;
        int run = 0;
        boolean other = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '-') {
                run++;
            } else if (c == ' ' || c == '\t' || c == '+') {
                if (run >= 3) runs++;
                run = 0;
            } else if (c == ':') {
                if (run >= 3) runs++;
                run = 0;
            } else {
                other = true;
                break;
            }
        }
        if (run >= 3) runs++;
        return !other && runs >= 1;
    }

    /** Start offsets of every dash run of the rule line, plus the end of each run. */
    private static int[] columnBounds(String rule) {
        List<Integer> starts = new ArrayList<Integer>();
        List<Integer> ends = new ArrayList<Integer>();
        int i = 0;
        while (i < rule.length()) {
            if (rule.charAt(i) == '-') {
                int start = i;
                while (i < rule.length() && rule.charAt(i) == '-') i++;
                starts.add(start);
                ends.add(i);
            } else {
                i++;
            }
        }
        int[] bounds = new int[starts.size() * 2];
        for (int k = 0; k < starts.size(); k++) {
            bounds[k * 2] = starts.get(k);
            bounds[k * 2 + 1] = ends.get(k);
        }
        return bounds;
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return i;
    }

    /**
     * A body line of the table: it starts inside the first column, is not a new block and
     * is not the bottom border. This is what keeps the paragraph after a table from being
     * eaten as its last row.
     */
    private static boolean isRowLike(String line, int[] bounds) {
        if (line.trim().length() == 0) return false;
        if (BLOCK_START.matcher(line).find()) return false;
        if (isBorderLine(line)) return false;
        return indentOf(line) >= Math.max(0, bounds[0] - 1);
    }

    /** Builds the pipe table, or null when the shape does not hold up. */
    private static Table buildTable(String[] lines, int headerStart, int ruleIndex, int[] bounds) {
        int columns = bounds.length / 2;
        if (columns < 2) return null;
        if (headerStart >= ruleIndex) return null;
        String[] header = new String[columns];
        for (int c = 0; c < columns; c++) header[c] = "";
        for (int h = headerStart; h < ruleIndex; h++) {
            String[] pieces = splitRow(lines[h], bounds);
            for (int c = 0; c < columns; c++) {
                if (pieces[c].trim().length() == 0) continue;
                header[c] = header[c].length() == 0 ? pieces[c].trim() : header[c] + " " + pieces[c].trim();
            }
        }
        List<String[]> rows = new ArrayList<String[]>();
        int[] gaps = new int[columns * 2];       // summed left/right padding per column
        int[] seen = new int[columns];
        int i = ruleIndex + 1;
        String[] row = null;
        int index = i;
        while (i < lines.length) {
            String line = lines[i];
            if (line.trim().length() == 0) {
                if (row != null) {
                    rows.add(row);
                    row = null;
                }
                int next = i + 1;
                if (next < lines.length && !isRowLike(lines[next], bounds)) break;
                if (next >= lines.length) break;
                i = next;
                index = i;
                continue;
            }
            if (isBorderLine(line)) {            // bottom border of a multiline table
                i++;
                break;
            }
            if (!isRowLike(line, bounds)) break;
            String[] pieces = splitRow(line, bounds);
            boolean continuation = row != null && pieces[0].trim().length() == 0;
            if (!continuation) {
                if (row != null) rows.add(row);
                row = new String[columns];
                for (int c = 0; c < columns; c++) row[c] = "";
            }
            for (int c = 0; c < columns; c++) {
                String piece = pieces[c].trim();
                if (piece.length() == 0) continue;
                row[c] = row[c].length() == 0 ? piece : row[c] + " " + piece;
                int[] pad = paddingOf(pieces[c]);
                if (pad != null) {
                    gaps[c * 2] += pad[0];
                    gaps[c * 2 + 1] += pad[1];
                    seen[c]++;
                }
            }
            i++;
            index = i;
        }
        if (row != null) rows.add(row);
        if (rows.isEmpty()) return null;

        StringBuilder out = new StringBuilder();
        out.append("| ");
        for (int c = 0; c < columns; c++) {
            if (c > 0) out.append(" | ");
            out.append(escapeCell(header[c]));
        }
        out.append(" |\n|");
        for (int c = 0; c < columns; c++) {
            String align = alignment(gaps[c * 2], gaps[c * 2 + 1], seen[c]);
            if ("center".equals(align)) out.append(":---:");
            else if ("right".equals(align)) out.append("---:");
            else out.append("---");
            out.append('|');
        }
        out.append('\n');
        for (int r = 0; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            out.append("| ");
            for (int c = 0; c < columns; c++) {
                if (c > 0) out.append(" | ");
                out.append(escapeCell(cells[c]));
            }
            out.append(" |\n");
        }
        return new Table(out.toString(), index);
    }

    /** Left and right padding of the raw (untrimmed) cell, or null when it is blank. */
    private static int[] paddingOf(String raw) {
        int left = 0;
        while (left < raw.length() && raw.charAt(left) == ' ') left++;
        int right = 0;
        int end = raw.length() - 1;
        while (end >= 0 && raw.charAt(end) == ' ') {
            right++;
            end--;
        }
        if (end < 0) return null;
        return new int[]{left, right};
    }

    /**
     * The alignment of a column, read from where its cells sit: text that hugs the right
     * edge of the column is right aligned, text centred in the column is centred, and
     * everything else is left aligned.
     */
    private static String alignment(int leftPadding, int rightPadding, int samples) {
        if (samples == 0) return "left";
        int left = leftPadding / samples;
        int right = rightPadding / samples;
        if (right <= 1 && left > 1) return "right";
        if (left > 1 && right > 1) return "center";
        return "left";
    }

    /** Slices one line into its columns; the last column runs to the end of the line. */
    private static String[] splitRow(String line, int[] bounds) {
        int columns = bounds.length / 2;
        String[] cells = new String[columns];
        for (int c = 0; c < columns; c++) {
            int start = bounds[c * 2];
            int end = c == columns - 1 ? line.length() : bounds[c * 2 + 1];
            if (start >= line.length()) {
                cells[c] = "";
                continue;
            }
            end = Math.min(Math.max(end, start), line.length());
            cells[c] = line.substring(start, end);
        }
        return cells;
    }

    /** A pipe table cell cannot contain a bare pipe. */
    private static String escapeCell(String cell) {
        String value = cell == null ? "" : cell.trim();
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '|' && (i == 0 || value.charAt(i - 1) != '\\')) out.append('\\');
            out.append(c);
        }
        return out.toString();
    }
}
