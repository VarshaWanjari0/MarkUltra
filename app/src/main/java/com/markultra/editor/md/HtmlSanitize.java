package com.markultra.editor.md;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allow-list based HTML sanitiser, the same idea as the SanitizationFilter github.com
 * runs over rendered Markdown: script and style blocks disappear entirely, only known
 * tags survive, only known attributes survive, and any URL that is not http(s), mail,
 * a fragment, {@code data:image/...} or a content/file URI is dropped.
 *
 * <p>Raw HTML is what makes a README's {@code <details>}, {@code <kbd>},
 * {@code <p align="center">} and badge layout work, so it cannot simply be escaped.</p>
 */
final class HtmlSanitize {

    private HtmlSanitize() {
    }

    private static final Pattern SCRIPT = Pattern.compile(
            "<\\s*(script|style|iframe|object|embed|form|meta|link|base)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SELF_CLOSING_DANGER = Pattern.compile(
            "<\\s*(script|style|iframe|object|embed|form|meta|link|base)\\b[^>]*/?>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile(
            "<(/?)([a-zA-Z][a-zA-Z0-9-]*)((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:][a-zA-Z0-9_:.\\-]*)(?:\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?",
            Pattern.DOTALL);

    private static final Set<String> TAGS = new HashSet<String>(Arrays.asList(
            "a", "abbr", "article", "aside", "b", "bdi", "bdo", "blockquote", "br", "caption", "math-renderer", "svg",
            "center", "cite", "code", "col", "colgroup", "dd", "del", "details", "dfn", "div",
            "dl", "dt", "em", "figcaption", "figure", "footer", "h1", "h2", "h3", "h4", "h5",
            "h6", "header", "hr", "i", "img", "input", "ins", "kbd", "li", "main", "mark", "nav",
            "ol", "p", "picture", "pre", "q", "rp", "rt", "ruby", "s", "samp", "section", "small",
            "source", "span", "strike", "strong", "sub", "summary", "sup", "table", "tbody", "td",
            "tfoot", "th", "thead", "time", "tr", "track", "tt", "u", "ul", "var", "video",
            "audio", "wbr", "svg", "path", "circle", "ellipse", "line", "polyline", "polygon",
            "rect", "g", "use", "text", "tspan", "defs", "symbol", "title", "math", "semantics",
            "math-renderer",
            "mrow", "mi", "mo", "mn", "msup", "msub", "mfrac", "msqrt", "annotation"));

    private static final Set<String> ATTRS = new HashSet<String>(Arrays.asList(
            "href", "src", "alt", "title", "align", "width", "height", "class", "id", "colspan",
            "rowspan", "start", "type", "checked", "disabled", "open", "lang", "dir", "valign",
            "viewbox", "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
            "stroke-dasharray", "cx", "cy", "r", "rx", "ry", "d", "points", "x", "y", "x1", "y1",
            "x2", "y2", "transform", "fill-rule", "clip-rule", "opacity", "aria-hidden", "role",
            "loading", "decoding", "referrerpolicy", "target", "rel", "data-tex", "data-task",
            "controls", "loop", "muted", "poster", "preload", "kind", "srclang", "label",
            "readonly", "value", "scope", "headers", "span", "data-lang", "data-rendered"));

    static String apply(String html) {
        if (html == null || html.length() == 0) return "";
        String cleaned = SCRIPT.matcher(html).replaceAll("");
        cleaned = SELF_CLOSING_DANGER.matcher(cleaned).replaceAll("");
        cleaned = COMMENT.matcher(cleaned).replaceAll("");
        Matcher m = TAG.matcher(cleaned);
        StringBuffer out = new StringBuffer(cleaned.length());
        while (m.find()) {
            String closing = m.group(1);
            String name = m.group(2).toLowerCase(Locale.ROOT);
            String attrs = m.group(3);
            if (!TAGS.contains(name)) {
                m.appendReplacement(out, "");
                continue;
            }
            if (name.equals("input")) {
                String lower = attrs.toLowerCase(Locale.ROOT);
                if (!lower.contains("type=\"checkbox\"") && !lower.contains("type='checkbox'")
                        && !lower.contains("type=checkbox")) {
                    m.appendReplacement(out, "");
                    continue;
                }
            }
            String rebuilt = closing.length() > 0 ? "</" + name + ">"
                    : "<" + name + rebuildAttrs(name, attrs) + ">";
            m.appendReplacement(out, Matcher.quoteReplacement(rebuilt));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String rebuildAttrs(String tag, String attrs) {
        if (attrs == null || attrs.length() == 0) return "";
        StringBuilder out = new StringBuilder(attrs.length());
        Matcher m = ATTR.matcher(attrs);
        while (m.find()) {
            String name = m.group(1);
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("on")) continue;
            if (!ATTRS.contains(lower)) continue;
            String value = m.group(3) != null ? m.group(3)
                    : m.group(4) != null ? m.group(4) : m.group(5);
            if (value == null) {
                out.append(' ').append(name);
                continue;
            }
            if (lower.equals("href") || lower.equals("src")) {
                if (!safeUrl(value)) continue;
            }
            out.append(' ').append(name).append("=\"").append(value.replace("\"", "&quot;"))
                    .append('"');
        }
        return out.toString();
    }

    private static boolean safeUrl(String url) {
        String v = url.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("javascript:") || v.startsWith("vbscript:") || v.startsWith("file:")) {
            return false;
        }
        if (v.startsWith("data:")) return v.startsWith("data:image/");
        return true;
    }

    // ---------------------------------------------------------------- escaping

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    static String escapeAttr(String text) {
        return escape(text).replace("\"", "&quot;").replace("\n", "&#10;");
    }

    static String unescape(String text) {
        if (text.indexOf('&') < 0) return text;
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '&') {
                int semi = text.indexOf(';', i);
                if (semi > i && semi - i <= 8) {
                    String entity = text.substring(i + 1, semi);
                    String replacement = entity(entity);
                    if (replacement != null) {
                        out.append(replacement);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String entity(String name) {
        if (name.equals("amp")) return "&";
        if (name.equals("lt")) return "<";
        if (name.equals("gt")) return ">";
        if (name.equals("quot")) return "\"";
        if (name.equals("apos") || name.equals("#39") || name.equals("#x27")) return "'";
        if (name.equals("nbsp") || name.equals("#160")) return "\u00A0";
        if (name.equals("hellip")) return "\u2026";
        if (name.equals("mdash")) return "\u2014";
        if (name.equals("ndash")) return "\u2013";
        if (name.equals("copy")) return "\u00A9";
        if (name.startsWith("#x") || name.startsWith("#X")) {
            try {
                return new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (name.startsWith("#")) {
            try {
                return new String(Character.toChars(Integer.parseInt(name.substring(1))));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
