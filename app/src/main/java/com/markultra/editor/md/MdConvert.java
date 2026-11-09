package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline exporters: plain text, HTML (standalone or fragment) and RTF.
 * All three are generated from the same AST, so they always agree.
 */
public final class MdConvert {

    private MdConvert() {
    }

    // ---------------------------------------------------------------------------------
    // plain text
    // ---------------------------------------------------------------------------------

    public static String toPlainText(String markdown) {
        MdBlockParser.Result parsed = MdBlockParser.parse(markdown);
        StringBuilder sb = new StringBuilder(markdown.length());
        for (int i = 0; i < parsed.blocks.size(); i++) {
            MdBlock b = parsed.blocks.get(i);
            plainBlock(sb, b, parsed.refLookup(), 0);
        }
        return trimTrailing(sb).toString();
    }

    private static void plainBlock(StringBuilder sb, MdBlock b, MdInlineParser.Refs refs, int depth) {
        switch (b.type) {
            case MdBlock.HEADING:
                sb.append(inline(b.text, refs, true)).append('\n').append('\n');
                break;
            case MdBlock.PARAGRAPH:
                sb.append(inline(b.text, refs, true)).append('\n').append('\n');
                break;
            case MdBlock.CODE:
                sb.append(b.text).append('\n').append('\n');
                break;
            case MdBlock.QUOTE:
                for (int i = 0; i < b.children.size(); i++) {
                    StringBuilder inner = new StringBuilder();
                    plainBlock(inner, b.children.get(i), refs, depth + 1);
                    String[] rows = inner.toString().split("\n");
                    for (int r = 0; r < rows.length; r++) {
                        if (rows[r].length() > 0) sb.append("> ").append(rows[r]).append('\n');
                    }
                }
                sb.append('\n');
                break;
            case MdBlock.LIST_ITEM:
                for (int d = 0; d < depth; d++) sb.append("    ");
                if (b.checkbox) sb.append(b.checked ? "[x] " : "[ ] ");
                else if (b.ordered) sb.append(b.number).append(". ");
                else sb.append("- ");
                sb.append(inline(b.text, refs, true)).append('\n');
                for (int i = 0; i < b.children.size(); i++) {
                    plainBlock(sb, b.children.get(i), refs, depth + 1);
                }
                break;
            case MdBlock.TABLE:
                if (b.table == null) break;
                for (int c = 0; c < b.table.headers.size(); c++) {
                    if (c > 0) sb.append(" | ");
                    sb.append(inline(b.table.headers.get(c), refs, true));
                }
                sb.append('\n');
                for (int r = 0; r < b.table.rows.size(); r++) {
                    String[] row = b.table.rows.get(r);
                    for (int c = 0; c < row.length; c++) {
                        if (c > 0) sb.append(" | ");
                        sb.append(inline(row[c], refs, true));
                    }
                    sb.append('\n');
                }
                sb.append('\n');
                break;
            case MdBlock.HR:
                sb.append("----------------------------------------\n\n");
                break;
            case MdBlock.IMAGE:
                sb.append(inline(b.text, refs, true)).append('\n').append('\n');
                break;
            case MdBlock.FOOTNOTE:
                sb.append('[').append(b.footnoteId).append("] ")
                        .append(inline(b.text, refs, true)).append('\n');
                break;
            case MdBlock.HTML:
                sb.append(b.text.replaceAll("<[^>]+>", "")).append('\n').append('\n');
                break;
            default:
                break;
        }
    }

    private static String inline(String source, MdInlineParser.Refs refs, boolean strip) {
        if (source == null) return "";
        if (!strip) return source;
        return MdInlineParser.parse(source, refs).text;
    }

    // ---------------------------------------------------------------------------------
    // HTML
    // ---------------------------------------------------------------------------------

    public static String toHtmlFragment(String markdown) {
        MdBlockParser.Result parsed = MdBlockParser.parse(markdown);
        StringBuilder sb = new StringBuilder(markdown.length() * 2);
        htmlBlocks(sb, parsed.blocks, parsed.refLookup(), 0, false);
        return sb.toString();
    }

    /**
     * The exported file lives outside the APK, so it cannot use the bundled KaTeX: it
     * points at the same version on a CDN and typesets itself on load. Without a network
     * the TeX stays readable as text, which is the right failure for a document.
     */
    private static final String KATEX_HEAD =
            "<link rel=\"stylesheet\" crossorigin=\"anonymous\" "
            + "href=\"https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css\">\n"
            + "<script defer src=\"https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js\"></script>\n"
            + "<script defer src=\"https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js\" "
            + "onload=\"renderMathInElement(document.body,{delimiters:[{left:'$$',right:'$$',display:true},"
            + "{left:'$',right:'$',display:false},{left:'\\\\(',right:'\\\\)',display:false},"
            + "{left:'\\\\[',right:'\\\\]',display:true}],throwOnError:false,"
            + "ignoredTags:['script','noscript','style','textarea','pre','code']});\"></script>\n"
            + "<style>math-renderer{display:inline-block}math-renderer.js-display-math{display:block;"
            + "text-align:center;margin:16px 0}.katex-display{overflow-x:auto}</style>\n";

    /**
     * A complete, self-contained HTML file.
     *
     * <p>It is rendered by the same pipeline as the preview - flexmark plus the GitHub
     * stylesheet port - so an exported file is the document the user was looking at, not
     * a second, simpler rendering of it. Equations keep their KaTeX markup; because the
     * file leaves the APK, the KaTeX stylesheet and script are loaded from a CDN.</p>
     */
    public static String toHtmlDocument(String markdown, String title, boolean dark) {
        String md = markdown == null ? "" : markdown;
        GfmHtml.Options o = new GfmHtml.Options();
        o.dark = dark;
        String body = GfmHtml.fragment(md, o);
        boolean hasMath = body.contains("data-tex=");
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<meta name=\"color-scheme\" content=\"" + (dark ? "dark" : "light") + "\">\n"
                + "<title>" + esc(title == null ? "Document" : title) + "</title>\n"
                + "<style>\n" + GfmCss.build(o) + "\n</style>\n"
                + (hasMath ? KATEX_HEAD : "")
                + "</head>\n<body>\n<article class=\"markdown-body\">\n" + body
                + "\n</article>\n</body>\n</html>\n";
    }

    private static void htmlBlocks(StringBuilder sb, List<MdBlock> blocks,
                                   MdInlineParser.Refs refs, int depth, boolean tight) {
        for (int i = 0; i < blocks.size(); i++) {
            MdBlock b = blocks.get(i);
            switch (b.type) {
                case MdBlock.HEADING:
                    sb.append("<h").append(b.level).append(" id=\"")
                            .append(MdToc.slug(MdInlineParser.parse(b.text, refs).text))
                            .append("\">").append(htmlInline(b.text, refs)).append("</h").append(b.level)
                            .append(">\n");
                    break;
                case MdBlock.PARAGRAPH:
                    sb.append(tight ? "" : "<p>").append(htmlInline(b.text, refs))
                            .append(tight ? "\n" : "</p>\n");
                    break;
                case MdBlock.CODE:
                    sb.append("<pre><code");
                    if (b.lang != null && b.lang.length() > 0) {
                        sb.append(" class=\"language-").append(escAttr(b.lang)).append('"');
                    }
                    sb.append('>').append(esc(b.text)).append("</code></pre>\n");
                    break;
                case MdBlock.QUOTE:
                    sb.append("<blockquote>\n");
                    htmlBlocks(sb, b.children, refs, depth + 1, false);
                    sb.append("</blockquote>\n");
                    break;
                case MdBlock.LIST_ITEM:
                    if (i == 0 || blocks.get(i - 1).type != MdBlock.LIST_ITEM
                            || blocks.get(i - 1).ordered != b.ordered) {
                        sb.append(b.ordered ? "<ol>\n" : "<ul>\n");
                    }
                    boolean task = b.checkbox;
                    sb.append("<li").append(task ? " class=\"task\"" : "").append('>');
                    if (task) {
                        sb.append("<input type=\"checkbox\" disabled")
                                .append(b.checked ? " checked" : "").append("> ");
                    }
                    sb.append(htmlInline(b.text, refs));
                    if (!b.children.isEmpty()) {
                        sb.append('\n');
                        htmlBlocks(sb, b.children, refs, depth + 1, true);
                    }
                    sb.append("</li>\n");
                    if (i == blocks.size() - 1 || blocks.get(i + 1).type != MdBlock.LIST_ITEM
                            || blocks.get(i + 1).ordered != b.ordered) {
                        sb.append(b.ordered ? "</ol>\n" : "</ul>\n");
                    }
                    break;
                case MdBlock.TABLE:
                    htmlTable(sb, b, refs);
                    break;
                case MdBlock.HR:
                    sb.append("<hr>\n");
                    break;
                case MdBlock.IMAGE:
                    sb.append("<figure><img src=\"").append(escAttr(b.url)).append("\" alt=\"")
                            .append(escAttr(b.lang)).append("\">");
                    if (b.text != null && b.text.length() > 0) {
                        sb.append("<figcaption>").append(esc(b.text)).append("</figcaption>");
                    }
                    sb.append("</figure>\n");
                    break;
                case MdBlock.FOOTNOTE:
                    sb.append("<div class=\"footnote\" id=\"fn-").append(escAttr(b.footnoteId))
                            .append("\"><sup>").append(esc(b.footnoteId)).append("</sup> ")
                            .append(htmlInline(b.text, refs)).append("</div>\n");
                    break;
                case MdBlock.HTML:
                    sb.append(b.text).append('\n');
                    break;
                default:
                    break;
            }
        }
    }

    private static void htmlTable(StringBuilder sb, MdBlock b, MdInlineParser.Refs refs) {
        if (b.table == null) return;
        sb.append("<table>\n<thead><tr>");
        for (int c = 0; c < b.table.headers.size(); c++) {
            sb.append("<th").append(alignAttr(b.table, c)).append('>')
                    .append(htmlInline(b.table.headers.get(c), refs)).append("</th>");
        }
        sb.append("</tr></thead>\n<tbody>\n");
        for (int r = 0; r < b.table.rows.size(); r++) {
            String[] row = b.table.rows.get(r);
            sb.append("<tr>");
            for (int c = 0; c < b.table.headers.size(); c++) {
                sb.append("<td").append(alignAttr(b.table, c)).append('>')
                        .append(htmlInline(c < row.length ? row[c] : "", refs)).append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n</table>\n");
    }

    private static String alignAttr(MdTableData t, int col) {
        if (col >= t.alignment.size()) return "";
        int a = t.alignment.get(col);
        if (a == MdTableData.ALIGN_CENTER) return " style=\"text-align:center\"";
        if (a == MdTableData.ALIGN_RIGHT) return " style=\"text-align:right\"";
        if (a == MdTableData.ALIGN_LEFT) return " style=\"text-align:left\"";
        return "";
    }

    static String htmlInline(String source, MdInlineParser.Refs refs) {
        if (source == null) return "";
        InlineResult r = MdInlineParser.parse(source, refs, false);
        // Inline spans are always properly nested (built by recursive descent), and a
        // span created later always encloses the ones created earlier. Emitting opens in
        // descending index order and closes in ascending index order therefore produces
        // well-formed HTML.
        final int count = r.spans.size();
        int[] events = new int[count * 2];
        for (int i = 0; i < count; i++) {
            events[i] = r.spans.get(i).start;
            events[count + i] = r.spans.get(i).end;
        }
        StringBuilder out = new StringBuilder(r.text.length() + 32);
        int pos = 0;
        while (pos <= r.text.length()) {
            // closes first, inner spans first
            for (int i = 0; i < count; i++) {
                if (r.spans.get(i).end == pos) out.append(closeTag(r.spans.get(i)));
            }
            if (pos == r.text.length()) break;
            // then opens, outer spans first
            for (int i = count - 1; i >= 0; i--) {
                if (r.spans.get(i).start == pos) out.append(openTag(r.spans.get(i)));
            }
            char c = r.text.charAt(pos);
            if (c == '\n') out.append("<br>\n");
            else out.append(escChar(c));
            pos++;
        }
        return out.toString();
    }

    private static String openTag(MdSpan s) {
        switch (s.type) {
            case MdSpan.BOLD: return "<strong>";
            case MdSpan.ITALIC: return "<em>";
            case MdSpan.STRIKE: return "<del>";
            case MdSpan.CODE: return "<code>";
            case MdSpan.LINK:
            case MdSpan.AUTOLINK: return "<a href=\"" + escAttr(s.url) + "\">";
            case MdSpan.IMAGE: return "<em class=\"img\">";
            case MdSpan.HIGHLIGHT: return "<mark>";
            case MdSpan.FOOTNOTE: return "<sup class=\"fn\">";
            default: return "";
        }
    }

    private static String closeTag(MdSpan s) {
        switch (s.type) {
            case MdSpan.BOLD: return "</strong>";
            case MdSpan.ITALIC: return "</em>";
            case MdSpan.STRIKE: return "</del>";
            case MdSpan.CODE: return "</code>";
            case MdSpan.LINK:
            case MdSpan.AUTOLINK: return "</a>";
            case MdSpan.IMAGE: return "</em>";
            case MdSpan.HIGHLIGHT: return "</mark>";
            case MdSpan.FOOTNOTE: return "</sup>";
            default: return "";
        }
    }

    // ---------------------------------------------------------------------------------
    // RTF
    // ---------------------------------------------------------------------------------

    public static String toRtf(String markdown, String title) {
        MdBlockParser.Result parsed = MdBlockParser.parse(markdown);
        StringBuilder sb = new StringBuilder(markdown.length() * 2);
        sb.append("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Helvetica;}{\\f1 Courier New;}}\n");
        sb.append("{\\info{\\title ").append(rtfEsc(title == null ? "Document" : title)).append("}}\n");
        sb.append("\\viewkind4\\uc1\\pard\\f0\\fs24\n");
        rtfBlocks(sb, parsed.blocks, parsed.refLookup(), 0);
        sb.append("}\n");
        return sb.toString();
    }

    private static void rtfBlocks(StringBuilder sb, List<MdBlock> blocks,
                                  MdInlineParser.Refs refs, int depth) {
        for (int i = 0; i < blocks.size(); i++) {
            MdBlock b = blocks.get(i);
            switch (b.type) {
                case MdBlock.HEADING: {
                    int size = Math.max(24, 40 - (b.level - 1) * 3);
                    sb.append("\\pard\\sb240\\sa120\\b\\fs").append(size).append(' ');
                    rtfInline(sb, b.text, refs);
                    sb.append("\\b0\\fs24\\par\n");
                    break;
                }
                case MdBlock.PARAGRAPH:
                    sb.append("\\pard\\sa160 ");
                    rtfInline(sb, b.text, refs);
                    sb.append("\\par\n");
                    break;
                case MdBlock.CODE:
                    sb.append("\\pard\\f1\\fs20\\sb120\\sa120 ");
                    sb.append(rtfEsc(b.text).replace("\n", "\\line "));
                    sb.append("\\f0\\fs24\\par\n");
                    break;
                case MdBlock.QUOTE:
                    sb.append("\\pard\\li480\\sa160 ");
                    for (int c = 0; c < b.children.size(); c++) {
                        MdBlock child = b.children.get(c);
                        rtfInline(sb, child.text == null ? "" : child.text, refs);
                        sb.append("\\line ");
                    }
                    sb.append("\\par\n");
                    break;
                case MdBlock.LIST_ITEM:
                    sb.append("\\pard\\li").append(360 + depth * 360).append("\\fi-240\\sa60 ");
                    if (b.checkbox) sb.append(b.checked ? "\\u9745? " : "\\u9744? ");
                    else if (b.ordered) sb.append(b.number).append(". ");
                    else sb.append("\\u8226? ");
                    rtfInline(sb, b.text, refs);
                    sb.append("\\par\n");
                    for (int c = 0; c < b.children.size(); c++) {
                        rtfBlocks(sb, b.children.subList(c, c + 1), refs, depth + 1);
                    }
                    break;
                case MdBlock.TABLE:
                    if (b.table == null) break;
                    for (int r = -1; r < b.table.rows.size(); r++) {
                        sb.append("\\pard\\sa40 ");
                        if (r < 0) {
                            for (int c = 0; c < b.table.headers.size(); c++) {
                                if (c > 0) sb.append(" | ");
                                sb.append("\\b ").append(rtfEsc(b.table.headers.get(c))).append("\\b0 ");
                            }
                        } else {
                            String[] row = b.table.rows.get(r);
                            for (int c = 0; c < row.length; c++) {
                                if (c > 0) sb.append(" | ");
                                sb.append(rtfEsc(row[c]));
                            }
                        }
                        sb.append("\\par\n");
                    }
                    sb.append("\\pard\\par\n");
                    break;
                case MdBlock.HR:
                    sb.append("\\pard\\brdrb\\brdrs\\brdrw10\\brsp20\\par\\pard\\par\n");
                    break;
                case MdBlock.IMAGE:
                    sb.append("\\pard\\i [image: ").append(rtfEsc(b.lang)).append("]\\i0\\par\n");
                    break;
                case MdBlock.FOOTNOTE:
                    sb.append("\\pard\\fs20 [").append(rtfEsc(b.footnoteId)).append("] ");
                    rtfInline(sb, b.text, refs);
                    sb.append("\\fs24\\par\n");
                    break;
                case MdBlock.HTML:
                    break;
                default:
                    break;
            }
        }
    }

    private static void rtfInline(StringBuilder sb, String source, MdInlineParser.Refs refs) {
        if (source == null) return;
        InlineResult r = MdInlineParser.parse(source, refs, true);
        int pos = 0;
        List<MdSpan> spans = r.spans;
        while (pos < r.text.length()) {
            // find the next boundary
            int next = r.text.length();
            for (int i = 0; i < spans.size(); i++) {
                MdSpan s = spans.get(i);
                if (s.start > pos && s.start < next) next = s.start;
            }
            int end = next;
            boolean bold = false;
            boolean italic = false;
            boolean strike = false;
            boolean code = false;
            String url = null;
            for (int i = 0; i < spans.size(); i++) {
                MdSpan s = spans.get(i);
                if (s.start <= pos && s.end >= pos) {
                    if (s.type == MdSpan.BOLD) bold = true;
                    else if (s.type == MdSpan.ITALIC) italic = true;
                    else if (s.type == MdSpan.STRIKE) strike = true;
                    else if (s.type == MdSpan.CODE) code = true;
                    else if (s.type == MdSpan.LINK || s.type == MdSpan.AUTOLINK) url = s.url;
                }
            }
            if (bold) sb.append("\\b ");
            if (italic) sb.append("\\i ");
            if (strike) sb.append("\\strike ");
            if (code) sb.append("\\f1 ");
            String chunk = r.text.substring(pos, end);
            sb.append(rtfEsc(chunk).replace("\n", "\\line "));
            if (url != null && url.length() > 0 && !url.equals(chunk)) {
                sb.append(" (").append(rtfEsc(url)).append(')');
            }
            if (code) sb.append("\\f0 ");
            if (strike) sb.append("\\strike0 ");
            if (italic) sb.append("\\i0 ");
            if (bold) sb.append("\\b0 ");
            pos = end;
        }
    }

    // ---------------------------------------------------------------------------------
    // escaping
    // ---------------------------------------------------------------------------------

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) sb.append(escChar(s.charAt(i)));
        return sb.toString();
    }

    static String escChar(char c) {
        switch (c) {
            case '&': return "&amp;";
            case '<': return "&lt;";
            case '>': return "&gt;";
            case '"': return "&quot;";
            case '\'': return "&#39;";
            default: return String.valueOf(c);
        }
    }

    static String escAttr(String s) {
        return esc(s).replace("\n", " ");
    }

    static String rtfEsc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '{' || c == '}') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\line ");
            else if (c == '\r') continue;
            else if (c < 128) sb.append(c);
            else if (c < 256) sb.append("\\'").append(hex2((int) c));
            else {
                sb.append("\\u").append((int) c).append('?');
            }
        }
        return sb.toString();
    }

    private static String hex2(int value) {
        final char[] digits = "0123456789abcdef".toCharArray();
        int v = value & 0xFF;
        return new String(new char[]{digits[(v >> 4) & 0xF], digits[v & 0xF]});
    }

    private static StringBuilder trimTrailing(StringBuilder sb) {
        int end = sb.length();
        while (end > 0 && (sb.charAt(end - 1) == '\n' || sb.charAt(end - 1) == ' ')) end--;
        sb.setLength(end);
        return sb;
    }
}
