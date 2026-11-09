package com.markultra.editor.md;

import com.vladsch.flexmark.ext.abbreviation.AbbreviationExtension;
import com.vladsch.flexmark.ext.admonition.AdmonitionExtension;
import com.vladsch.flexmark.ext.aside.AsideExtension;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.definition.DefinitionExtension;
import com.vladsch.flexmark.ext.gfm.issues.GfmIssuesExtension;
import com.vladsch.flexmark.ext.gfm.users.GfmUsersExtension;
import com.vladsch.flexmark.ext.ins.InsExtension;
import com.vladsch.flexmark.ext.resizable.image.ResizableImageExtension;
import com.vladsch.flexmark.ext.superscript.SuperscriptExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.ext.emoji.EmojiImageType;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub flavoured Markdown to HTML.
 *
 * <p>The parsing is done by <b>flexmark-java</b> (the same family of libraries used by
 * many desktop Markdown tools), configured to match what github.com renders, and then
 * post-processed into the exact shape GitHub's DOM has: heading {@code id}s produced by
 * the github-slugger rules, alert blocks, highlighted fences, task list checkboxes,
 * {@code <details>} and {@code <kbd>} passthrough and real images.</p>
 *
 * <p>The output is a complete HTML document (stylesheet, KaTeX and Mermaid included)
 * meant for a {@code WebView}. Everything is pure Java plus flexmark, so the whole
 * pipeline is unit-testable without an emulator.</p>
 */
public final class GfmHtml {

    /**
     * Where the page's own files come from. The WebView answers this host through the
     * app's {@code shouldInterceptRequest}, so every script, stylesheet and font is an
     * ordinary same-origin https request: no file-access rule, no MIME guessing.
     */
    public static final String ASSET_HOST = "appassets.androidplatform.net";
    public static final String ASSET_BASE = "https://" + ASSET_HOST + "/assets/";

    /** Colours and switches for one render. */
    public static final class Options {
        public boolean dark;
        /**
         * True to parse the MarkUltra+ syntax on top of GFM: definition lists,
         * admonitions, wiki links, attributes, abbreviations, sub/superscript, inserts,
         * asides, front matter, {@code @mentions}, {@code #123} references and media tags.
         * Off by default, because that is the syntax github.com does not know.
         */
        public boolean rich;
        public float fontScale = 1f;
        public boolean images = true;
        /**
         * Colours are optional overrides. Leaving them null means "the GitHub palette for
         * this mode", which is what a caller who only sets {@link #dark} expects: the
         * defaults used to be light-only, so a dark page built without an explicit
         * palette came out light text on white.
         */
        public String background;
        public String foreground;
        public String muted;
        public String accent;
        public String borderColor;
        public String surfaceColor;

        public String background() {
            return background != null ? background : (dark ? "#0d1117" : "#ffffff");
        }

        public String foreground() {
            return foreground != null ? foreground : (dark ? "#e6edf3" : "#1f2328");
        }

        public String muted() {
            return muted != null ? muted : (dark ? "#9198a1" : "#59636e");
        }

        public String accent() {
            return accent != null ? accent : (dark ? "#4493f8" : "#0969da");
        }

        public String border() {
            if (borderColor != null) return borderColor;
            return dark ? "#3d444d" : "#d1d9e0";
        }

        public String surface() {
            if (surfaceColor != null) return surfaceColor;
            return dark ? "#151b23" : "#f6f8fa";
        }
    }

    private static final Pattern HEADING = Pattern.compile("<h([1-6])>(.*?)</h\\1>", Pattern.DOTALL);
    /**
     * Keeps typeset maths out of the Markdown parser's hands.
     *
     * <p>Everything before this point writes a {@code <math-renderer>} element into the
     * source. Markdown treats the text between two inline tags as ordinary text, so
     * {@code x^{n-1}} inside an equation came out as a superscript, a soft line break
     * became {@code <br>}, and a second pass over {@code $$...$$} nested one element
     * inside another. Each element is therefore swapped for a token before the parser
     * runs and put back afterwards, unchanged, which is the only way a formula survives a
     * round trip through a Markdown engine that was never told about maths.</p>
     */
    private static final class MathSink {
        private final java.util.List<String> elements = new java.util.ArrayList<String>();
        private final String prefix;

        MathSink(String markdown) {
            String p = "MUm4thBl0ck";
            while (markdown != null && markdown.contains(p)) p = p + "X";
            prefix = p;
        }

        /** Replaces every math element in {@code text} with a token. */
        String protect(String text) {
            if (text == null || text.indexOf("<math-renderer") < 0) return text;
            StringBuilder out = new StringBuilder(text.length());
            int i = 0;
            while (true) {
                int start = text.indexOf("<math-renderer", i);
                if (start < 0) {
                    out.append(text, i, text.length());
                    break;
                }
                int end = closeOf(text, start);
                if (end < 0) {
                    out.append(text, i, text.length());
                    break;
                }
                out.append(text, i, start);
                out.append(prefix).append(elements.size()).append("End");
                elements.add(text.substring(start, end));
                i = end;
            }
            return out.toString();
        }

        /** Puts the elements back, and drops the paragraph a lone element was wrapped in. */
        String restore(String html) {
            if (elements.isEmpty() || html == null) return html;
            String out = html;
            for (int i = 0; i < elements.size(); i++) {
                String token = prefix + i + "End";
                String element = elements.get(i);
                out = out.replace("<p>" + token + "</p>", element);
                out = out.replace(token, element);
            }
            return out;
        }

        /** Index just past the {@code </math-renderer>} that closes the element. */
        private static int closeOf(String text, int start) {
            int depth = 0;
            int i = start;
            while (i < text.length()) {
                int open = text.indexOf("<math-renderer", i);
                int close = text.indexOf("</math-renderer>", i);
                if (close < 0) return -1;
                if (open >= 0 && open < close) {
                    depth++;
                    i = open + 14;
                    continue;
                }
                depth--;
                i = close + 16;
                if (depth <= 0) return i;
            }
            return -1;
        }
    }

    private static final Pattern FENCE = Pattern.compile(
            "<pre><code(?: class=\"language-([^\"]*)\")?>(.*?)</code></pre>", Pattern.DOTALL);
    private static final Pattern IMG = Pattern.compile("<img\\b[^>]*>");
    private static final Pattern ALT_ATTR = Pattern.compile("alt=\"([^\"]*)\"");
    private static final Pattern TAG_TEXT = Pattern.compile("<[^>]*>");
    private static final Pattern CHECKBOX = Pattern.compile("<input\\b[^>]*type=\"checkbox\"[^>]*>");
    private static final Pattern HTML_TASK_LIST = Pattern.compile(
            "<ul>(?=\\s*<li class=\"task-list-item\")");

    private GfmHtml() {
    }

    /** Renders a document fragment only (used by tests and by the print/export path). */
    public static String fragment(String markdown) {
        return fragment(markdown, new Options());
    }

    public static String fragment(String markdown, Options o) {
        if (markdown == null) markdown = "";
        // Pandoc's raw attributes, \[ ... \] maths and pipe-less tables first: they
        // change the shape of the source, and everything after this point expects
        // GitHub Markdown or the math-renderer element.
        MathSink sink = new MathSink(markdown);
        String pre = sink.protect(PandocCompat.apply(markdown, o.rich));
        pre = Preprocess.apply(pre);
        // Preprocess makes maths of its own; take that out of the Markdown too, because
        // TeX is not Markdown and a caret or an underscore inside it is not emphasis
        pre = sink.protect(pre);
        String html = renderMarkdown(pre, o.rich);
        html = sink.restore(html);
        html = HtmlSanitize.apply(html);
        html = postProcess(html, o);
        return html;
    }

    /** Renders a full, self contained HTML page. */
    public static String page(String markdown, Options o) {
        StringBuilder sb = new StringBuilder(markdown == null ? 0 : markdown.length() * 2 + 8192);
        String body = fragment(markdown, o);
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,")
                .append("maximum-scale=3,user-scalable=yes\">")
                .append("<meta name=\"color-scheme\" content=\"")
                .append(o.dark ? "dark" : "light")
                .append("\"><style>").append(GfmCss.build(o)).append("</style>")
                // Claimed before a single engine tag is parsed. A heading called
                // "Mermaid" gives that element an id, an id becomes a property of
                // window, and from then on window.mermaid is the headline - so the
                // page could not tell whether the engine had loaded, and the engine's
                // own assignment appeared to do nothing. Interposing a setter first
                // catches whatever the engine assigns, whatever else shares the name.
                // the polyfills first: an engine must never be the thing that
                // discovers the WebView is a year old
                .append("<script>").append(COMPAT_JS).append("</script>")
                .append("<script>").append(CLAIM_SCRIPT).append("</script></head>")
                .append("<body><article class=\"markdown-body\">")
                .append(body)
                .append("</article>");
        // Only pay for the renderers the document actually needs: Mermaid alone is
        // 3 MB of JavaScript, so a plain README never loads it.
        if (body.contains("data-tex=")) {
            // KaTeX's own stylesheet, with the twenty WOFF2 faces next to it. Both live
            // in the APK: maths is typeset offline, with no web fonts to fetch.
            sb.append("<link rel=\"stylesheet\" href=\"").append(ASSET_BASE).append("katex.css\">");
            sb.append("<script src=\"").append(ASSET_BASE).append("katex.min.js\"></script>");
            // \ce{2H2 + O2 -> 2H2O} and \pu{1.5e-3 M} are the chemistry notation
            // Pandoc and GitHub both accept; KaTeX needs the mhchem extension for them.
            if (body.contains("\\ce{") || body.contains("\\pu{")
                    || body.contains("\\ce ") || body.contains("mhchem")) {
                sb.append("<script src=\"").append(ASSET_BASE).append("mhchem.min.js\"></script>");
                // the bootstrap asks for the extension by name; a flag is cheaper and
                // safer to emit than a CSS selector with a dot in its value
                sb.append("<script>window.MU_MHCHEM=true;</script>");
            }
        }
        if (body.contains("class=\"mermaid\"")) {
            sb.append("<script src=\"").append(ASSET_BASE).append("mermaid.min.js\"></script>");
        }
        // Graphviz, PlantUML and Vega-Lite are five megabytes together, so a document
        // without diagrams pays nothing for them. When it does have them, the engines are
        // plain <script> tags in the page - the same mechanism KaTeX and Mermaid use, and
        // the only one a WebView loads without asking questions about file access. An
        // injector that loaded engines at runtime is why the diagrams did not appear on
        // the device: a tag the page asks for can be refused, a static tag is not.
        if (body.contains("class=\"mu-diagram")) {
            if (body.contains("data-lang=\"graphviz\"") || body.contains("data-lang=\"plantuml\"")) {
                sb.append("<script src=\"").append(ASSET_BASE).append("viz-global.js\"></script>");
            }
            if (body.contains("data-lang=\"vega\"")) {
                sb.append("<script src=\"").append(ASSET_BASE).append("vega.min.js\"></script>");
                sb.append("<script src=\"").append(ASSET_BASE).append("vega-lite.min.js\"></script>");
            }
            if (body.contains("data-lang=\"plantuml\"")) {
                // PlantUML lays its diagrams out with the Graphviz engine loaded above
                sb.append("<script src=\"").append(ASSET_BASE).append("plantuml.js\"></script>");
            }
            // the runner must not draw before the engines are in place: the bootstrap below
        // fetches whatever the tags failed to load and only then calls it
        sb.append("<script>window.MU_DIAGRAMS_AUTORUN=false;</script>");
        sb.append("<script src=\"").append(ASSET_BASE).append("diagrams.js\"></script>");
        }
        sb.append("<script>").append(bootstrap(o.dark)).append("</script>")
                .append("</body></html>");
        return sb.toString();
    }

    // =================================================================================
    // flexmark
    // =================================================================================

    /** Shared options for both profiles; everything here is also true for GitHub. */
    private static MutableDataSet baseOptions(List<Extension> extensions) {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, extensions);
        options.set(EmojiExtension.USE_IMAGE_TYPE, EmojiImageType.UNICODE_ONLY);
        options.set(Parser.THEMATIC_BREAK_RELAXED_START, true);
        // GitHub turns a single newline inside a paragraph into <br>: that is what
        // github.com does for READMEs and comments, and it is visible in its HTML
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        options.set(HtmlRenderer.ESCAPE_HTML, false);
        options.set(HtmlRenderer.SUPPRESS_HTML, false);
        options.set(HtmlRenderer.GENERATE_HEADER_ID, false);
        return options;
    }

    /**
     * The extensions that are always on: github.com's own six (tables, strikethrough,
     * tasks, links, footnotes, emoji) plus the two that documents written for other
     * Markdown engines use and that never change the meaning of ordinary prose, a
     * definition list and a superscript. A reader who pastes a Pandoc or Python-Markdown
     * document expects both to render without switching the editor to MarkUltra+.
     */
    private static List<Extension> githubExtensions() {
        return Arrays.<Extension>asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create(),
                FootnoteExtension.create(),
                EmojiExtension.create(),
                DefinitionExtension.create(),
                SuperscriptExtension.create());
    }

    /**
     * GitHub plus everything flexmark can do. These are the extensions that make
     * MarkUltra render more than github.com does; none of them changes the meaning of
     * plain GFM, so a GitHub document renders the same in either mode.
     */
    private static List<Extension> richExtensions() {
        List<Extension> list = new ArrayList<Extension>(githubExtensions());
        list.add(AdmonitionExtension.create());
        list.add(AbbreviationExtension.create());
        list.add(AttributesExtension.create());
        list.add(WikiLinkExtension.create());
        list.add(AsideExtension.create());
        list.add(YamlFrontMatterExtension.create());
        list.add(GfmUsersExtension.create());
        list.add(GfmIssuesExtension.create());
        list.add(InsExtension.create());
        list.add(ResizableImageExtension.create());
        return list;
    }

    private static final class Engine {
        final Parser parser;
        final HtmlRenderer renderer;

        Engine(MutableDataSet options) {
            this.parser = Parser.builder(options).build();
            this.renderer = HtmlRenderer.builder(options).build();
        }
    }

    private static final Engine GITHUB = new Engine(baseOptions(githubExtensions()));
    private static final Engine RICH = new Engine(baseOptions(richExtensions()));

    private static String renderMarkdown(String markdown, boolean rich) {
        Engine engine = rich ? RICH : GITHUB;
        Node document = engine.parser.parse(markdown);
        return engine.renderer.render(document);
    }

    // =================================================================================
    // post processing: heading ids, code fences, task lists, images
    // =================================================================================

    private static String postProcess(String html, Options o) {
        html = tableCodePipes(html);
        html = githubFootnotes(html);
        html = addHeadingIds(html);
        html = highlightFences(html, o);
        html = markInlineCode(html);
        if (o.rich) html = richMedia(richWiki(html));
        // resizable images come back from flexmark with an end tag; HTML has none
        html = html.replace("</img>", "");
        html = wrapImages(html);
        html = enableTaskLists(html);
        if (!o.images) html = dropImages(html);
        return html;
    }

    /**
     * Rewrites flexmark's footnote markup into the exact shape github.com renders:
     * a {@code <section data-footnotes>} with an accessible heading, {@code user-content-}
     * prefixed ids, and the return arrow inside the note paragraph.
     */
    private static String githubFootnotes(String html) {
        int start = html.indexOf("<div class=\"footnotes\">");
        if (start < 0) return html;
        String head = html.substring(0, start);
        String tail = html.substring(start);

        tail = tail.replaceFirst(
                "<div class=\"footnotes\">\\s*<hr\\s*/?>\\s*<ol>",
                "<section data-footnotes class=\"footnotes\"><h2 id=\"footnote-label\" "
                        + "class=\"sr-only\">Footnotes</h2>\n<ol>");
        int close = tail.lastIndexOf("</ol>");
        if (close >= 0) {
            int divClose = tail.indexOf("</div>", close);
            if (divClose >= 0) {
                tail = tail.substring(0, divClose) + "</section>" + tail.substring(divClose + 6);
            }
        }
        tail = LI_ID.matcher(tail).replaceAll("<li id=\"user-content-fn-$1\">");
        tail = BACKREF_ANCHOR.matcher(tail).replaceAll(
                "<a href=\"#user-content-fnref-$1\" data-footnote-backref=\"\" "
                        + "aria-label=\"Back to reference\" class=\"data-footnote-backref\">\u21A9</a>");
        // GitHub keeps the return arrow inside the note paragraph
        tail = BACKREF_FOLD.matcher(tail).replaceAll(" $1</p>\n</li>");
        head = REF_LINK.matcher(head).replaceAll(
                "<sup><a href=\"#user-content-fn-$1\" id=\"user-content-fnref-$1\" "
                        + "data-footnote-ref=\"\" aria-describedby=\"footnote-label\" "
                        + "class=\"footnote-ref\">$2</a></sup>");
        return head + tail;
    }

    /** {@code </p>} followed by one or more return arrows, then {@code </li>}. */
    private static final Pattern BACKREF_FOLD = Pattern.compile(
            "</p>\\s*((?:<a [^>]*data-footnote-backref[^>]*>[^<]*</a>\\s*)+)</li>");
    private static final Pattern BACKREF_ANCHOR = Pattern.compile(
            "<a href=\"#fnref-([^\"]+)\" class=\"footnote-backref\">[^<]*</a>");
    private static final Pattern LI_ID = Pattern.compile("<li id=\"fn-([^\"]+)\">");
    private static final Pattern REF_LINK = Pattern.compile(
            "<sup id=\"fnref-[^\"]+\"\\s*>\\s*<a class=\"footnote-ref\" "
                    + "href=\"#fn-([^\"]+)\">([^<]*)</a>\\s*</sup>");

    /** Gives every heading the id github.com would give it, de-duplicated like GitHub. */
    private static String addHeadingIds(String html) {
        Matcher m = HEADING.matcher(html);
        StringBuffer out = new StringBuffer(html.length() + 64);
        Map<String, Integer> used = new HashMap<String, Integer>();
        while (m.find()) {
            String level = m.group(1);
            String inner = m.group(2);
            String text = TAG_TEXT.matcher(inner).replaceAll("");
            String slug = slug(HtmlSanitize.unescape(text));
            Integer seen = used.get(slug);
            String id = slug;
            if (seen != null) {
                int next = seen.intValue() + 1;
                used.put(slug, Integer.valueOf(next));
                id = slug + "-" + next;
            } else {
                used.put(slug, Integer.valueOf(0));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(
                    "<h" + level + " id=\"" + id + "\">" + inner + "</h" + level + ">"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** github-slugger: lower case, drop punctuation, spaces to hyphens. */
    static String slug(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == ' ') {
                sb.append('-');
            } else if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** The maths element the page renders, for callers outside this class. */
    static String mathBlock(String tex, boolean display) {
        String body = tex == null ? "" : tex;
        return mathTag(display, body, display ? "$$" + body + "$$" : "$" + body + "$");
    }

    /** github.com emits maths as a {@code <math-renderer>} element that keeps the raw TeX
     *  as its text content, so scripts - and copy/paste - can still see the source. */
    private static String mathTag(boolean display, String tex, String text) {
        return "<math-renderer class=\"" + (display ? "js-display-math" : "js-inline-math")
                + "\" style=\"display: " + (display ? "block" : "inline-block")
                + "\" data-tex=\"" + HtmlSanitize.escapeAttr(tex) + "\">"
                + HtmlSanitize.escape(text) + "</math-renderer>";
    }

    /**
     * GitHub unescapes {@code \|} inside a table cell before the inline parser runs, so a
     * code span in a table shows a pipe. The table parser here keeps the backslash inside
     * the code span, which leaves a stray character in the one place a reader is most
     * likely to copy from, so the escapes are undone in the rendered cells instead.
     */
    private static String tableCodePipes(String html) {
        if (html.indexOf("<table") < 0 || html.indexOf("\\|") < 0) return html;
        StringBuilder out = new StringBuilder(html.length());
        int i = 0;
        while (true) {
            int start = html.indexOf("<table", i);
            if (start < 0) {
                out.append(html, i, html.length());
                break;
            }
            int end = html.indexOf("</table>", start);
            if (end < 0) {
                out.append(html, i, html.length());
                break;
            }
            end += 8;
            out.append(html, i, start);
            out.append(unescapePipesInCode(html.substring(start, end)));
            i = end;
        }
        return out.toString();
    }

    /** Replaces {@code \|} with {@code |} inside the {@code <code>} elements of a table. */
    private static String unescapePipesInCode(String table) {
        StringBuilder out = new StringBuilder(table.length());
        int i = 0;
        while (true) {
            int open = table.indexOf("<code", i);
            if (open < 0) {
                out.append(table, i, table.length());
                break;
            }
            int body = table.indexOf('>', open);
            if (body < 0) {
                out.append(table, i, table.length());
                break;
            }
            body++;
            int close = table.indexOf("</code>", body);
            if (close < 0) {
                out.append(table, i, table.length());
                break;
            }
            out.append(table, i, body);
            out.append(table.substring(body, close).replace("\\|", "|"));
            i = close;
        }
        return out.toString();
    }

    private static String highlightFences(String html, Options o) {
        Matcher m = FENCE.matcher(html);
        StringBuffer out = new StringBuffer(html.length() + 256);
        while (m.find()) {
            String lang = m.group(1) == null ? "" : m.group(1).trim();
            String raw = HtmlSanitize.unescape(m.group(2));
            if (raw.endsWith("\n")) raw = raw.substring(0, raw.length() - 1);
            String replacement;
            if (lang.equalsIgnoreCase("mermaid")) {
                replacement = "<pre class=\"mermaid\">" + HtmlSanitize.escape(raw) + "</pre>";
            } else if (CodeHighlight.isMathLanguage(lang)) {
                replacement = mathTag(true, raw, "$$" + raw + "$$");
            } else if (lang.length() == 0) {
                // no language: GitHub uses the plain block form
                replacement = "<pre class=\"notranslate\"><code class=\"notranslate\">"
                        + HtmlSanitize.escape(raw) + "</code></pre>";
            } else {
                // GitHub's highlighted form: no <code>, a linguist-named wrapper
                replacement = "<div class=\"highlight highlight-source-" + linguist(lang)
                        + "\"><pre class=\"notranslate\">"
                        + CodeHighlight.highlight(raw, lang) + "</pre></div>";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Makes the checkboxes live so tapping one rewrites the source document. */
    private static String enableTaskLists(String html) {
        Matcher ul = HTML_TASK_LIST.matcher(html);
        String withClass = ul.replaceAll("<ul class=\"contains-task-list\">");
        Matcher cb = CHECKBOX.matcher(withClass);
        StringBuffer out = new StringBuffer(withClass.length() + 128);
        int index = 0;
        while (cb.find()) {
            String tag = cb.group();
            boolean checked = tag.contains("checked");
            String replacement = "<input type=\"checkbox\" class=\"task-list-item-checkbox\""
                    + (checked ? " checked" : "") + " data-task=\"" + index + "\">";
            index++;
            cb.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        cb.appendTail(out);
        return out.toString();
    }

    /** GitHub names the highlight wrapper after the language linguist detected. */
    static String linguist(String lang) {
        String l = lang.trim().toLowerCase(Locale.ROOT);
        int dot = l.lastIndexOf('.');
        if (dot >= 0) l = l.substring(dot + 1);
        if (l.equals("bash") || l.equals("sh") || l.equals("zsh") || l.equals("shell")
                || l.equals("console") || l.equals("shell-session")) return "shell";
        if (l.equals("js") || l.equals("mjs") || l.equals("cjs") || l.equals("node"))
            return "javascript";
        if (l.equals("ts")) return "typescript";
        if (l.equals("py")) return "python";
        if (l.equals("yml")) return "yaml";
        if (l.equals("rb")) return "ruby";
        if (l.equals("kt") || l.equals("kts")) return "kotlin";
        if (l.equals("c++") || l.equals("cc") || l.equals("hpp")) return "cpp";
        if (l.equals("cs")) return "csharp";
        if (l.equals("htm")) return "html";
        if (l.equals("patch")) return "diff";
        return l;
    }

    /**
     * GitHub renders a Markdown image as a link to the image with the bitmap inside, and
     * caps the width. (It also routes the bytes through its camo proxy; that is GitHub
     * infrastructure, not Markdown, so it is deliberately not copied here.)
     */
    private static final Pattern MEDIA_VIDEO = Pattern.compile(
            "\\.(mp4|webm|ogv|mov|m4v)(\\?|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_AUDIO = Pattern.compile(
            "\\.(mp3|wav|m4a|ogg|flac|aac)(\\?|$)", Pattern.CASE_INSENSITIVE);

    /**
     * MarkUltra+ only: an image whose URL is a media file becomes a real player instead of
     * a broken picture. github.com would show a broken image here, which is why this only
     * runs when the extra syntax is switched on.
     */
    private static final Pattern PLAIN_LINK = Pattern.compile(
            "<a href=\"([^\"]+)\"(?! class=)>([^<]*)</a>");

    /**
     * Marks wiki links so they can be styled like the rest of MarkUltra+. flexmark keeps no
     * marker on the anchor, but the shape is recognisable: a relative target whose slug is
     * the visible text.
     */
    private static String richWiki(String html) {
        Matcher m = PLAIN_LINK.matcher(html);
        StringBuffer out = new StringBuffer(html.length() + 32);
        while (m.find()) {
            String href = m.group(1);
            String text = m.group(2);
            boolean relative = href.indexOf("://") < 0 && !href.startsWith("#")
                    && !href.startsWith("/") && !href.startsWith("mailto:");
            String slug = text.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
            if (relative && slug.equals(href.toLowerCase(Locale.ROOT))) {
                m.appendReplacement(out, Matcher.quoteReplacement(
                        "<a class=\"wikilink\" href=\"" + href + "\">" + text + "</a>"));
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String richMedia(String html) {
        Matcher m = IMG.matcher(html);
        StringBuilder out = new StringBuilder(html.length() + 64);
        int copied = 0;
        while (m.find()) {
            String tag = m.group();
            String src = attribute(tag, "src");
            if (src == null) continue;
            boolean video = MEDIA_VIDEO.matcher(src).find();
            boolean audio = !video && MEDIA_AUDIO.matcher(src).find();
            if (!video && !audio) continue;
            out.append(html, copied, m.start());
            if (video) {
                out.append("<video controls preload=\"metadata\" src=\"").append(src)
                        .append("\" style=\"max-width:100%\"></video>");
            } else {
                out.append("<audio controls preload=\"metadata\" src=\"").append(src)
                        .append("\" style=\"width:100%\"></audio>");
            }
            copied = m.end();
        }
        if (copied == 0) return html;
        out.append(html.substring(copied));
        return out.toString();
    }

    private static String wrapImages(String html) {
        Matcher m = IMG.matcher(html);
        StringBuilder out = new StringBuilder(html.length() + 64);
        int copied = 0;
        while (m.find()) {
            String tag = m.group();
            int lastOpen = html.lastIndexOf("<a ", m.start());
            int lastClose = html.lastIndexOf("</a>", m.start());
            boolean insideLink = lastOpen > lastClose;
            String src = attribute(tag, "src");
            out.append(html, copied, m.start());
            if (insideLink || src == null || src.startsWith("data:")) {
                out.append(tag);
            } else {
                String fixed = tag.contains("style=") ? tag
                        : tag.substring(0, tag.length() - 1) + " style=\"max-width: 100%;\">";
                out.append("<a target=\"_blank\" rel=\"noopener noreferrer nofollow\" href=\"")
                        .append(src).append("\">").append(fixed).append("</a>");
            }
            copied = m.end();
        }
        out.append(html.substring(copied));
        return out.toString();
    }

    private static String attribute(String tag, String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    /** Inline code carries GitHub's {@code notranslate} class. */
    private static String markInlineCode(String html) {
        return html.replace("<code>", "<code class=\"notranslate\">");
    }

    private static String dropImages(String html) {
        Matcher m = IMG.matcher(html);
        StringBuffer out = new StringBuffer(html.length());
        while (m.find()) {
            String tag = m.group();
            Matcher alt = ALT_ATTR.matcher(tag);
            String text = alt.find() ? alt.group(1) : "";
            if (text.length() == 0) text = "image";
            m.appendReplacement(out, Matcher.quoteReplacement(
                    "<span class=\"mu-img-alt\">" + text + "</span>"));
        }
        m.appendTail(out);
        return out.toString();
    }

    // =================================================================================
    // source pre-processing: alerts, math, mermaid
    // =================================================================================

    /** Line based rewrites that flexmark cannot express as extensions. */
    private static final class Preprocess {

        private static final String[][] ALERTS = {
                {"NOTE", "note", "M8 1.6a6.4 6.4 0 1 0 0 12.8A6.4 6.4 0 0 0 8 1.6ZM8 7v4.4"},
                {"TIP", "tip", "M8 1.6a4.6 4.6 0 0 0-2.7 8.3V12h5.4v-2.1A4.6 4.6 0 0 0 8 1.6ZM6 13h4"},
                {"IMPORTANT", "important", "M2.4 2.4h11.2v11.2H2.4zM8 5v3.6M8 10.4v.2"},
                {"WARNING", "warning", "M8 1.8 1.4 13.4h13.2L8 1.8Zm0 3.6v3.4M8 11v.2"},
                {"CAUTION", "caution", "M5 1.8h6L14.2 5v6L11 14.2H5L1.8 11V5L5 1.8ZM8 5v3.6M8 10.4v.2"}
        };

        private static String apply(String markdown) {
            String[] lines = markdown.split("\n", -1);
            StringBuilder out = new StringBuilder(markdown.length() + 128);
            int i = 0;
            while (i < lines.length) {
                String line = lines[i];
                String trimmed = line.trim();

                // ---- fenced block: mermaid and math become real elements
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    // CommonMark counts the opening run: a fence opened with four ticks is
                    // closed by four or more, never by the three that a nested example
                    // inside it uses. Matching on three characters instead loses track of
                    // where the block ends and swallows the rest of the document.
                    char fenceChar = trimmed.charAt(0);
                    int run = 0;
                    while (run < trimmed.length() && trimmed.charAt(run) == fenceChar) run++;
                    String info = trimmed.substring(run).trim();
                    int end = i + 1;
                    while (end < lines.length && !isFenceClose(lines[end], fenceChar, run)) end++;
                    String lang = info.toLowerCase(Locale.ROOT);
                    if (lang.startsWith("mermaid")) {
                        StringBuilder body = new StringBuilder();
                        for (int k = i + 1; k < end; k++) {
                            if (body.length() > 0) body.append('\n');
                            body.append(lines[k]);
                        }
                        out.append("\n<pre class=\"mermaid\">")
                                .append(HtmlSanitize.escape(body.toString()))
                                .append("</pre>\n\n");
                        i = Math.min(end + 1, lines.length);
                        continue;
                    }
                    if (CodeHighlight.isMathLanguage(lang)) {
                        StringBuilder body = new StringBuilder();
                        for (int k = i + 1; k < end; k++) {
                            if (body.length() > 0) body.append('\n');
                            body.append(lines[k]);
                        }
                        out.append('\n').append(mathTag(true, body.toString(), "$$" + body + "$$"))
                                .append("\n\n");
                        i = Math.min(end + 1, lines.length);
                        continue;
                    }
                    // ---- Graphviz, PlantUML and Vega-Lite: kept as source, drawn by
                    // assets/diagrams.js once the page is up. Mermaid is not the only
                    // diagram language a document can contain, and these three have no
                    // Mermaid equivalent at all.
                    String diagram = diagramLang(lang);
                    if (diagram != null) {
                        StringBuilder body = new StringBuilder();
                        for (int k = i + 1; k < end; k++) {
                            if (body.length() > 0) body.append('\n');
                            body.append(lines[k]);
                        }
                        out.append("\n<div class=\"mu-diagram mu-nocopy\" data-lang=\"").append(diagram)
                                .append("\"><pre class=\"mu-src\">")
                                .append(HtmlSanitize.escape(body.toString()))
                                .append("</pre></div>\n\n");
                        i = Math.min(end + 1, lines.length);
                        continue;
                    }
                    for (int k = i; k <= end && k < lines.length; k++) out.append(lines[k]).append('\n');
                    i = end + 1;
                    continue;
                }

                // ---- fenced containers:  ::: note  ...  :::
                if (trimmed.startsWith(":::") && trimmed.length() > 3) {
                    String head = trimmed.substring(3).trim();
                    int close = i + 1;
                    while (close < lines.length && !isContainerClose(lines[close])) close++;
                    int bodyEnd = Math.min(close, lines.length);
                    StringBuilder body = new StringBuilder();
                    for (int k = i + 1; k < bodyEnd; k++) {
                        body.append(lines[k]).append('\n');
                    }
                    int space = head.indexOf(' ');
                    String kind = (space < 0 ? head : head.substring(0, space)).toLowerCase(Locale.ROOT);
                    String title = space < 0 ? "" : head.substring(space + 1).trim();
                    int alertKind = alertIndex(kind);
                    String into = alertKind >= 0
                            ? "<div class=\"markdown-alert markdown-alert-" + ALERTS[alertKind][1] + "\">\n"
                              + "<p class=\"markdown-alert-title\">" + icon(ALERTS[alertKind][2])
                              + (title.length() == 0 ? titleCase(ALERTS[alertKind][0]) : HtmlSanitize.escape(title))
                              + "</p>\n\n"
                            : "<div class=\"mu-container mu-container-" + safeName(kind) + "\">\n"
                              + (title.length() == 0 ? "" : "<p class=\"mu-container-title\">"
                              + HtmlSanitize.escape(title) + "</p>\n\n");
                    String ending = "</div>\n\n";
                    if (kind.equals("details")) {
                        into = "<details class=\"mu-container mu-details\">\n<summary class=\"mu-container-title\">"
                                + (title.length() == 0 ? "Details" : HtmlSanitize.escape(title))
                                + "</summary>\n\n";
                        ending = "</details>\n\n";
                    }
                    out.append('\n').append(into).append(body).append('\n').append(ending);
                    i = Math.min(bodyEnd + 1, lines.length);
                    continue;
                }

                // ---- display maths on its own lines:  $$  ...  $$
                if (trimmed.equals("$$")) {
                    int end = i + 1;
                    while (end < lines.length && !lines[end].trim().equals("$$")) end++;
                    StringBuilder body = new StringBuilder();
                    for (int k = i + 1; k < end && k < lines.length; k++) {
                        if (body.length() > 0) body.append('\n');
                        body.append(lines[k]);
                    }
                    out.append("\n<p>").append(mathTag(true, body.toString(),
                            "$$\n" + body + "\n$$")).append("</p>\n\n");
                    i = Math.min(end + 1, lines.length);
                    continue;
                }

                // ---- GitHub alerts:  > [!NOTE]
                int alert = alertKind(line);
                if (alert >= 0) {
                    String[] meta = ALERTS[alert];
                    StringBuilder body = new StringBuilder();
                    // "> [!NOTE] text" keeps the text: the marker shares its line with
                    // the first sentence in most documents written by hand
                    String rest = alertRemainder(line);
                    if (rest.trim().length() > 0) body.append(rest.trim()).append('\n');
                    int k = i + 1;
                    while (k < lines.length) {
                        String next = lines[k];
                        String t = next.trim();
                        if (t.startsWith(">")) {
                            body.append(stripQuote(next)).append('\n');
                            k++;
                        } else if (t.length() == 0) {
                            // the blockquote (and so the alert) ends at a blank line
                            break;
                        } else {
                            break;
                        }
                    }
                    if (body.toString().trim().length() == 0) {
                        // GitHub leaves an alert marker with no content as a quote
                        for (int k2 = i; k2 < k; k2++) out.append(lines[k2]).append('\n');
                        i = k;
                        continue;
                    }
                    out.append("\n<div class=\"markdown-alert markdown-alert-").append(meta[1])
                            .append("\">\n<p class=\"markdown-alert-title\">")
                            .append(icon(meta[2]))
                            // github.com puts the label straight into the title
                            // paragraph, without a wrapper element
                            .append(titleCase(meta[0])).append("</p>\n\n")
                            .append(body.toString().trim())
                            .append("\n\n</div>\n\n");
                    i = k;
                    continue;
                }

                out.append(mathInline(line)).append('\n');
                i++;
            }
            return out.toString();
        }

        /** A {@code :::} line ends the container that opened it. */
        private static boolean isContainerClose(String line) {
            String t = line.trim();
            int n = 0;
            while (n < t.length() && t.charAt(n) == ':') n++;
            if (n < 3) return false;
            for (int k = n; k < t.length(); k++) {
                if (!Character.isWhitespace(t.charAt(k))) return false;
            }
            return true;
        }

        /** The alert index for a container name, or -1 when it is a plain container. */
        private static int alertIndex(String kind) {
            for (int i = 0; i < ALERTS.length; i++) {
                if (ALERTS[i][0].equalsIgnoreCase(kind) || ALERTS[i][1].equalsIgnoreCase(kind)) return i;
            }
            return -1;
        }

        /** A container name safe to put in a class attribute. */
        private static String safeName(String kind) {
            StringBuilder sb = new StringBuilder(kind.length());
            for (int i = 0; i < kind.length(); i++) {
                char c = kind.charAt(i);
                sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '-');
            }
            return sb.length() == 0 ? "note" : sb.toString();
        }

        /** True when {@code line} is a closing fence for a run of {@code run} ticks. */
        private static boolean isFenceClose(String line, char fenceChar, int run) {
            String t = line.trim();
            int n = 0;
            while (n < t.length() && t.charAt(n) == fenceChar) n++;
            if (n < run) return false;
            for (int k = n; k < t.length(); k++) {
                if (!Character.isWhitespace(t.charAt(k))) return false;
            }
            return true;
        }

        /** The diagram engine a fence belongs to, or null for an ordinary code block. */
        private static String diagramLang(String info) {
            if (info == null) return null;
            String lang = info.trim().toLowerCase(Locale.ROOT);
            int space = lang.indexOf(' ');
            if (space > 0) lang = lang.substring(0, space);
            if (lang.equals("graphviz") || lang.equals("dot") || lang.equals("gv")
                    || lang.equals("graph")) return "graphviz";
            if (lang.equals("plantuml") || lang.equals("puml") || lang.equals("plant-uml")
                    || lang.equals("uml")) return "plantuml";
            if (lang.equals("vega") || lang.equals("vega-lite") || lang.equals("vegalite")
                    || lang.equals("vega_lite")) return "vega";
            return null;
        }

        private static int alertKind(String line) {
            String t = line.trim();
            if (t.length() < 8 || t.charAt(0) != '>') return -1;
            String rest = t.substring(1).trim();
            if (rest.length() < 5 || rest.charAt(0) != '[' || rest.charAt(1) != '!') return -1;
            int end = rest.indexOf(']');
            if (end < 0) return -1;
            String kind = rest.substring(2, end).trim().toUpperCase(Locale.ROOT);
            for (int i = 0; i < ALERTS.length; i++) {
                if (ALERTS[i][0].equals(kind)) return i;
            }
            return -1;
        }

        /** The text that follows the {@code ]} of an alert marker on the same line. */
        private static String alertRemainder(String line) {
            int bracket = line.indexOf('[');
            if (bracket < 0) return "";
            int close = line.indexOf(']', bracket);
            if (close < 0) return "";
            return line.substring(close + 1);
        }

        private static String stripQuote(String line) {
            String t = line;
            int spaces = 0;
            while (spaces < t.length() && (t.charAt(spaces) == ' ' || t.charAt(spaces) == '\t')) spaces++;
            int at = t.indexOf('>', spaces);
            if (at < 0) return line;
            String rest = t.substring(at + 1);
            if (rest.startsWith(" ")) rest = rest.substring(1);
            return rest;
        }

        private static String titleCase(String upper) {
            return upper.charAt(0) + upper.substring(1).toLowerCase(Locale.ROOT);
        }

        private static String icon(String path) {
            return "<svg viewBox=\"0 0 16 16\" aria-hidden=\"true\"><path d=\"" + path
                    + "\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.5\" "
                    + "stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>";
        }

        // ------------------------------------------------------------------- math

        private static String mathInline(String line) {
            if (line.indexOf('$') < 0) return line;
            StringBuilder out = new StringBuilder(line.length() + 16);
            int i = 0;
            int len = line.length();
            while (i < len) {
                char c = line.charAt(i);
                if (c == '\\' && i + 1 < len && line.charAt(i + 1) == '$') {
                    out.append("\\$");
                    i += 2;
                    continue;
                }
                if (c == '`') {
                    int run = 1;
                    while (i + run < len && line.charAt(i + run) == '`') run++;
                    String fence = line.substring(i, i + run);
                    int close = line.indexOf(fence, i + run);
                    int stop = close < 0 ? len : close + run;
                    out.append(line, i, stop);
                    i = stop;
                    continue;
                }
                if (c == '$') {
                    boolean display = line.startsWith("$$", i);
                    int from = i + (display ? 2 : 1);
                    int close = findClose(line, from, display);
                    if (close > from) {
                        String tex = line.substring(from, close);
                        if (looksLikeMath(tex)) {
                            // github.com wraps maths in a <math-renderer> element and
                            // lets its script typeset it; the same element is used here
                            out.append(mathTag(display, tex,
                                    display ? "$$" + tex + "$$" : "$" + tex + "$"));
                            i = close + (display ? 2 : 1);
                            continue;
                        }
                    }
                }
                out.append(c);
                i++;
            }
            return out.toString();
        }

        private static int findClose(String line, int from, boolean display) {
            int i = from;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (display) {
                    if (line.startsWith("$$", i)) return i;
                } else if (c == '$') {
                    return i;
                }
                i++;
            }
            return -1;
        }

        /** Keeps prose like "it costs $5 and $6" out of the maths path. */
        static boolean looksLikeMath(String tex) {
            String t = tex.trim();
            if (t.length() == 0) return false;
            if (t.charAt(0) == ' ' || t.charAt(t.length() - 1) == ' ') return false;
            if (t.length() == 1) return Character.isLetter(t.charAt(0));
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == '\\' || c == '^' || c == '_' || c == '{' || c == '}'
                        || c == '=' || c == '+' || c == '/' || c == '<' || c == '>'
                        || c == '\u00D7' || c == '\u00B7') {
                    return true;
                }
            }
            // a bare identifier or a number with a leading minus
            if (t.startsWith("-") && t.length() > 1) return true;
            for (int i = 0; i < t.length(); i++) {
                if (!Character.isLetterOrDigit(t.charAt(i))) return false;
            }
            return true;
        }
    }

    // =================================================================================
    // in page script
    // =================================================================================

    /**
     * TeX commands the editor understands on top of KaTeX's own. They are declared here,
     * in Java, and escaped once on the way out: a hand-escaped copy in a JavaScript
     * string literal is exactly how {@code \R} silently became {@code R} before.
     */
    private static final String[][] MACROS = {
            {"\\R", "\\mathbb{R}"},
            {"\\N", "\\mathbb{N}"},
            {"\\Z", "\\mathbb{Z}"},
            {"\\Q", "\\mathbb{Q}"},
            {"\\C", "\\mathbb{C}"},
            {"\\dd", "\\mathop{}\\!\\mathrm{d}"},
            {"\\abs", "\\left|#1\\right|"},
            {"\\norm", "\\left\\|#1\\right\\|"},
            {"\\ceil", "\\left\\lceil#1\\right\\rceil"},
            {"\\floor", "\\left\\lfloor#1\\right\\rfloor"},
            {"\\eval", "\\left.#1\\right|"},
            {"\\bra", "\\langle #1 |"},
            {"\\ket", "| #1 \\rangle"},
    };

    /** The macro table as JavaScript, declared on {@code window} so it can be checked. */
    static String macroScript() {
        StringBuilder sb = new StringBuilder("window.MU_MACROS=window.MU_MACROS||{};");
        for (int i = 0; i < MACROS.length; i++) {
            sb.append("window.MU_MACROS['").append(jsString(MACROS[i][0]))
                    .append("']='").append(jsString(MACROS[i][1])).append("';");
        }
        return sb.toString();
    }

    /** Escapes a TeX snippet for a single quoted JavaScript literal. */
    private static String jsString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '\'') sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    /** Runs the maths pass again, for a page whose DOM was patched from outside. */
    static final String MATH_REFRESH = "try{window.muMath&&window.muMath();}catch(e){}";

    /**
     * JavaScript that jumps to an anchor inside the page and returns the resulting
     * scroll fraction. The page is never navigated: a fragment link used to be a real
     * load of {@code file:///android_asset/#id}, which threw the document away and
     * then restored the previous scroll position - the "jumps and comes back" bug.
     */
    public static String anchorScript(String id) {
        return "window.muAnchor('" + jsString(id == null ? "" : id) + "')";
    }

    /**
     * The JavaScript an engine expects and an older WebView does not have.
     *
     * <p>Mermaid 10 calls {@code structuredClone} (Chrome 98 and later) the moment it is
     * loaded; Vega 5 calls {@code Object.hasOwn} (Chrome 93); Vega-Lite calls
     * {@code String.prototype.replaceAll} and {@code flatMap}. On a phone whose System
     * WebView is a year or two behind, every one of those is a failed engine - and the
     * failure looks exactly like "the engine did not load". These are small, plain
     * equivalents, written in ES5 so they parse everywhere, and they run inline in the
     * head before a single engine is fetched.</p>
     */
    public static final String COMPAT_JS =
            "(function(){"
            + "var g=typeof globalThis!=='undefined'?globalThis:window;"
            + "function def(o,k,v){try{Object.defineProperty(o,k,{configurable:true,"
            + "writable:true,value:v});}catch(e){try{o[k]=v;}catch(e2){}}}"
            + "if(typeof g.globalThis==='undefined')def(g,'globalThis',g);"
            + "if(!Object.hasOwn)def(Object,'hasOwn',function(o,k){"
            + "return Object.prototype.hasOwnProperty.call(o,k);});"
            + "if(!Object.fromEntries)def(Object,'fromEntries',function(it){"
            + "var out={},a=[],i=0;if(it&&typeof it[Symbol.iterator]==='function'){"
            + "var s=it[Symbol.iterator](),n;while(!(n=s.next()).done)a.push(n.value);}"
            + "else if(it&&typeof it.length==='number'){for(i=0;i<it.length;i++)a.push(it[i]);}"
            + "for(i=0;i<a.length;i++)out[a[i][0]]=a[i][1];return out;});"
            // structuredClone: mermaid deep-copies its default configuration at load
            + "if(typeof structuredClone!=='function')def(g,'structuredClone',function(value){"
            + "var seen=[];function clone(v){"
            + "if(v===null||typeof v!=='object')return v;"
            + "for(var i=0;i<seen.length;i+=2)if(seen[i]===v)return seen[i+1];"
            + "var out;"
            + "if(v instanceof Date)return new Date(v.getTime());"
            + "if(v instanceof RegExp)return new RegExp(v.source,v.flags!==undefined?v.flags:"
            + "(v.global?'g':'')+(v.ignoreCase?'i':'')+(v.multiline?'m':''));"
            + "if(typeof Map!=='undefined'&&v instanceof Map){out=new Map();seen.push(v,out);"
            + "v.forEach(function(val,key){out.set(clone(key),clone(val));});return out;}"
            + "if(typeof Set!=='undefined'&&v instanceof Set){out=new Set();seen.push(v,out);"
            + "v.forEach(function(val){out.add(clone(val));});return out;}"
            + "if(typeof ArrayBuffer!=='undefined'&&v instanceof ArrayBuffer)return v.slice(0);"
            + "if(typeof ArrayBuffer!=='undefined'&&ArrayBuffer.isView&&ArrayBuffer.isView(v))"
            + "return new v.constructor(v.buffer.slice(0),v.byteOffset,v.length);"
            + "if(Object.prototype.toString.call(v)==='[object Array]'){out=[];seen.push(v,out);"
            + "for(var j=0;j<v.length;j++)out[j]=clone(v[j]);return out;}"
            + "if(v instanceof Error){out=new v.constructor(v.message);out.name=v.name;"
            + "seen.push(v,out);return out;}"
            + "out={};seen.push(v,out);"
            + "for(var k in v)if(Object.prototype.hasOwnProperty.call(v,k))out[k]=clone(v[k]);"
            + "return out;}"
            + "return clone(value);});"
            + "function escapeRe(s){return String(s).replace(/[.*+?^$()|[\\]\\\\]/g,"
            + "'\\\\$&');}"
            + "if(!String.prototype.replaceAll)def(String.prototype,'replaceAll',"
            + "function(search,replacement){"
            + "if(search instanceof RegExp){if(!search.global)throw new TypeError("
            + "'replaceAll must be called with a global RegExp');"
            + "return this.replace(search,replacement);}"
            + "var text=String(this),find=String(search);"
            + "if(find===''){var out='',chars=text.split('');"
            + "for(var i=0;i<chars.length;i++){out+=typeof replacement==='function'?"
            + "replacement('',i,text):replacement;out+=chars[i];}"
            + "return out+(typeof replacement==='function'?"
            + "replacement('',chars.length,text):replacement);}"
            + "if(typeof replacement==='function'){"
            + "return text.replace(new RegExp(escapeRe(find),'g'),replacement);}"
            + "return text.split(find).join(replacement);});"
            + "function at(n){n=Math.trunc(n)||0;if(n<0)n+=this.length;"
            + "return n<0||n>=this.length?undefined:this[n];}"
            + "if(!Array.prototype.at)def(Array.prototype,'at',at);"
            + "if(!String.prototype.at)def(String.prototype,'at',at);"
            + "if(!Array.prototype.flat)def(Array.prototype,'flat',function(depth){"
            + "var d=depth===undefined?1:Math.trunc(depth)||0,out=[];"
            + "function walk(a){for(var i=0;i<a.length;i++){if(Array.isArray(a[i])&&d>0){"
            + "d--;walk(a[i]);d++;}else out.push(a[i]);}}"
            + "walk(this);return out;});"
            + "if(!Array.prototype.flatMap)def(Array.prototype,'flatMap',function(fn,thisArg){"
            + "var out=[],i;for(i=0;i<this.length;i++){"
            + "if(!(i in this))continue;var v=fn.call(thisArg,this[i],i,this);"
            + "if(Array.isArray(v)){for(var j=0;j<v.length;j++)out.push(v[j]);}else out.push(v);}"
            + "return out;});"
            + "if(!String.prototype.trimStart)def(String.prototype,'trimStart',function(){"
            + "return String(this).replace(/^\\s+/,'');});"
            + "if(!String.prototype.trimEnd)def(String.prototype,'trimEnd',function(){"
            + "return String(this).replace(/\\s+$/,'');});"
            + "if(!String.prototype.padStart)def(String.prototype,'padStart',function(n,pad){"
            + "var s=String(this);n=n|0;var fill=pad===undefined?' ':String(pad);"
            + "while(s.length<n)s=fill+s;return s;}) ;"
            + "if(!Promise.allSettled)def(Promise,'allSettled',function(list){"
            + "return Promise.all([].slice.call(list).map(function(p){"
            + "return Promise.resolve(p).then(function(v){"
            + "return{status:'fulfilled',value:v};},function(e){"
            + "return{status:'rejected',reason:e};});}));});"
            + "if(!Promise.any)def(Promise,'any',function(list){"
            + "return new Promise(function(resolve,reject){"
            + "var items=[].slice.call(list),left=items.length,errors=[];"
            + "if(!left){reject(new Error('all promises were rejected'));return;}"
            + "items.forEach(function(p,i){Promise.resolve(p).then(resolve,function(e){"
            + "errors[i]=e;if(--left===0)reject(errors[0]);});});});});"
            + "if(typeof g.queueMicrotask!=='function')def(g,'queueMicrotask',function(fn){"
            + "Promise.resolve().then(fn);});"
            // plantuml.js uses both; a reference is better than a crash
            + "if(typeof g.WeakRef!=='function')def(g,'WeakRef',function(o){"
            + "this.deref=function(){return o;};});"
            + "if(typeof g.FinalizationRegistry!=='function')def(g,'FinalizationRegistry',"
            + "function(){this.register=function(){};this.unregister=function(){};"
            + "this.cleanupSome=function(){};});"
            + "})();";

    /**
     * Runs in the head, before the engines. Every name an engine publishes is claimed
     * with an accessor, so the value is caught in {@code window.MU_CAP} even when the
     * document already has an element with that id.
     */
    public static final String CLAIM_SCRIPT =
            "(function(){var N=['katex','KaTeX','mhchem','mermaid','vega','vegaLite','Viz','PlantUML'];"
            + "var CAP=window.MU_CAP=window.MU_CAP||{};"
            + "for(var i=0;i<N.length;i++){(function(name){"
            + "var now=null;try{now=window[name];}catch(e){}"
            + "if(now&&!now.nodeType)CAP[name]=now;"
            + "try{Object.defineProperty(window,name,{configurable:true,enumerable:true,"
            + "get:function(){return CAP[name];},set:function(v){CAP[name]=v;}});}catch(e){}"
            + "})(N[i]);}})();";

    private static final String BOOTSTRAP =
            "(function(){"
            + "function all(s){return Array.prototype.slice.call(document.querySelectorAll(s));}"
            // ---- engines, with a way in that cannot be refused
            // A WebView may refuse to load a file:// sub-resource of a page built with
            // loadDataWithBaseURL, and it does so silently. The script tags are still the
            // fast path; when one of them produced no global, the text of the file is
            // pulled over the JavaScript bridge and run from the page itself, which no
            // URL policy can block.
            + "var ENGINE_ERR={};var BRIDGED={};"
            // window.<name> is not the engine when the document has an element with that
            // id; MU_CAP (installed in the head, before the engines ran) holds what the
            // engine actually assigned.
            + "function muGet(name){var c=window.MU_CAP;var v=c?c[name]:null;"
            + "return v?v:window[name];}"
            + "window.addEventListener('error',function(ev){"
            + "if(ev&&ev.target&&ev.target.tagName==='SCRIPT'){ENGINE_ERR[String(ev.target.src)]='load failed';}}"
            + ",true);"
            // A global is not proof that an engine is there. Any element with id="mermaid"
            // or id="vega" in the document - a heading called "Vega", say - becomes a
            // property of window, so `if (window.vega)` is true for a headline, and the
            // engine the page needs is never fetched. Ask the engine itself instead.
            + "function muHas(name){"
            + "var v=muGet(name);if(!v)return false;"
            + "if(name==='mermaid')return typeof v.run==='function'&&typeof v.initialize==='function';"
            + "if(name==='katex')return typeof v.render==='function';"
            + "if(name==='mhchem'){try{window.katex.renderToString('\\ce{H2O}',{throwOnError:true});"
            + "return true;}catch(e){return false;}}"
            + "if(name==='Viz')return typeof v.instance==='function';"
            + "if(name==='vega')return typeof v.parse==='function';"
            + "if(name==='vegaLite')return typeof v.compile==='function';"
            + "if(name==='PlantUML')return typeof v.renderToString==='function';"
            + "return true;}"
            // Runs a file's text in the page and returns what went wrong, if anything.
            // A script element is used rather than eval() because a bundle's UMD wrapper
            // passes `this` as its global: inside a strict-mode eval that is undefined and
            // the engine silently fails to publish itself. window.onerror is borrowed for
            // the duration, so a syntax error - an engine built for a newer JavaScript
            // than this WebView has - is reported instead of swallowed.
            + "function muRunText(code){"
            + "var msg=null,prev=window.onerror;"
            + "window.onerror=function(m){if(!msg)msg=String(m);return true;};"
            + "try{var s=document.createElement('script');s.textContent=code;"
            + "(document.head||document.documentElement).appendChild(s);}"
            + "catch(e){msg=String(e&&e.message||e);}"
            + "window.onerror=prev;return msg;}"
            + "function muInject(code){muRunText(code);}"
            + "function muInjectScoped(code){muRunText('(function(){'+code+'\\n})();');}"
            + "function muEngine(globalName,file,done){"
            + "if(muHas(globalName)){done(null);return;}"
            + "var code='';try{code=Android.loadAsset(file);}catch(e){code='';}"
            + "if(!code||code.length<50){"
            + "ENGINE_ERR[file]='the app returned no bytes for '+file;done(ENGINE_ERR[file]);return;}"
            + "BRIDGED[file]=true;"
            + "var bad=muRunText(code);"
            + "if(muHas(globalName)){done(null);return;}"
            + "ENGINE_ERR[file]='assets/'+file+(bad?(' threw '+bad)"
            + ":(' ran but defined no '+globalName));"
            + "done(ENGINE_ERR[file]);}"
            // the reason a diagram engine is missing, for the block that needed it
            + "window.muEngineError=function(file){return ENGINE_ERR[file]||'';};"
            + "function muEnsure(list,index,done){"
            + "if(index>=list.length){done();return;}"
            + "muEngine(list[index][0],list[index][1],function(err){"
            + "if(err)ENGINE_ERR[list[index][1]]=err;"
            + "muEnsure(list,index+1,done);});}"
            + "window.muDiagramsAfter=null;"
            + macroScript()
            + "window.muMath=function(){all('[data-tex]').forEach(function(el){"
            + "if(el.querySelector('.katex'))return;"
            + "var tex=el.getAttribute('data-tex');"
            + "el.setAttribute('title',tex);"
            + "if(!muHas('katex')){el.className+=' mu-math-raw';el.textContent=tex;return;}"
            + "try{katex.render(tex,el,{throwOnError:false,errorColor:'#d1242f',"
            + "displayMode:el.classList.contains('js-display-math'),"
            + "output:'htmlAndMathml',strict:'ignore',trust:false,"
            + "macros:window.MU_MACROS});}"
            + "catch(e){el.className+=' mu-math-raw';el.textContent=tex;}});};"
            + "function muStart(){"
            + "try{window.muMath();}catch(e){}"
            + "try{if(muHas('mermaid')){mermaid.initialize({startOnLoad:false,"
            + "securityLevel:'strict',theme:" + "@@MERMAID_THEME@@"
            + ",flowchart:{useMaxWidth:true},fontFamily:'inherit'});"
            // One diagram at a time. Rendering the whole set in one run() call makes
            // mermaid lay several diagrams out at once, each measuring text in a shared
            // scratch element; in a document with sixteen of them about half come back
            // as "Syntax error in text". Drawn in sequence every one of them is correct,
            // and a diagram that fails only costs its own block.
            + "var mq=[].slice.call(document.querySelectorAll('pre.mermaid:not([data-processed]),"
            + "div.mermaid:not([data-processed])'));"
            + "(function drawMermaid(i){if(i>=mq.length)return;"
            + "mermaid.run({nodes:[mq[i]]}).then(function(){drawMermaid(i+1);},"
            + "function(){drawMermaid(i+1);});})(0);}else{"
            + "all('pre.mermaid').forEach(function(p){"
            + "if(p.querySelector('.mu-diagram-error-note'))return;"
            + "var n=document.createElement('p');"
            + "n.className='mu-diagram-error-note';"
            + "n.textContent='mermaid: '+(ENGINE_ERR['mermaid.min.js']||'the diagram engine did not start');"
            + "p.appendChild(n);});}}catch(e){}"
            + "try{all('input.task-list-item-checkbox').forEach(function(cb){"
            + "cb.disabled=false;cb.addEventListener('change',function(){"
            + "try{Android.toggleTask(parseInt(cb.getAttribute('data-task'),10),cb.checked);}"
            + "catch(e){}});});}catch(e){}"
            // A diagram that cannot be drawn must say so. An engine that failed to load
            // and a runner that never started look identical otherwise: a code block
            // where a picture belongs, and no way to tell the two apart.
            + "try{if(!window.muDiagrams){all('.mu-diagram').forEach(function(b){"
            + "if(b.getAttribute('data-rendered'))return;"
            + "if(b.querySelector('.mu-diagram-error-note'))return;"
            + "var n=document.createElement('p');n.className='mu-diagram-error-note';"
            + "n.textContent=(b.getAttribute('data-lang')||'diagram')+"
            + "': the diagram runner did not start';b.appendChild(n);});}}catch(e){}"
            + "try{all('img').forEach(function(img){img.addEventListener('error',function(){"
            + "img.classList.add('mu-img-broken');"
            + "var alt=img.getAttribute('alt')||'';"
            + "var box=document.createElement('span');box.className='mu-img-alt';"
            + "box.textContent=alt.length?alt:'image';"
            + "if(img.parentNode){img.parentNode.replaceChild(box,img);}});});}catch(e){}"
            // the function ends here: everything below runs as the page loads
            + "}"
            // ---- scroll reporting, with a lock so a jump is not reported as a scroll
            + "var last=0,lockUntil=0;"
            + "window.muLock=function(ms){lockUntil=Date.now()+(ms||1200);};"
            + "window.addEventListener('scroll',function(){var now=Date.now();"
            + "if(now<lockUntil)return;if(now-last<80)return;last=now;"
            + "var h=document.documentElement.scrollHeight-window.innerHeight;"
            + "var f=h>0?(window.scrollY||document.body.scrollTop)/h:0;"
            + "try{Android.previewScroll(f);}catch(e){}},{passive:true});"
            + "window.muScrollTo=function(f){var h=document.documentElement.scrollHeight"
            + "-window.innerHeight;if(h>0)window.scrollTo(0,Math.round(h*f));"
            + "window.muLock(1200);};"
            // Restoring a position is not one scrollTo call: images and diagrams arrive
            // after the page is "finished", the document grows, and the same fraction then
            // points somewhere else - in practice at the top. Follow the document while it
            // settles, and stop the moment the reader touches it.
            + "window.muRestore=function(f){if(!(f>0))return;window.muLock(1800);"
            + "var last=-1,stop=false,until=Date.now()+4000;"
            + "function done(){stop=true;}"
            + "try{document.addEventListener('touchstart',done,{passive:true,once:true});"
            + "document.addEventListener('wheel',done,{passive:true,once:true});"
            + "document.addEventListener('keydown',done,{once:true});}catch(e){}"
            + "(function tick(){if(stop||Date.now()>until)return;"
            + "var h=document.documentElement.scrollHeight-window.innerHeight;"
            + "if(h>0&&Math.abs(h-last)>1){last=h;window.scrollTo(0,Math.round(h*f));}"
            + "setTimeout(tick,90);})();};"
            + "window.muShowAnchor=function(id){try{var el=document.getElementById(id);"
            + "if(el)el.scrollIntoView({block:'start'});}catch(e){}};"
            // ---- in-page anchors never leave the document
            + "window.muAnchor=function(id){try{var el=document.getElementById(id);"
            + "if(!el){var all2=all('[id]');for(var i=0;i<all2.length;i++){"
            + "if(all2[i].id.toLowerCase()===String(id).toLowerCase()){el=all2[i];break;}}}"
            + "if(!el)return -1;window.muLock(1400);"
            + "el.scrollIntoView({block:'start',behavior:'auto'});"
            + "var h=document.documentElement.scrollHeight-window.innerHeight;"
            + "return h>0?(window.scrollY||document.body.scrollTop)/h:0;}catch(e){return -1;}};"
            + "try{all('a[href^=\"#\"]').forEach(function(a){"
            + "a.addEventListener('click',function(ev){ev.preventDefault();"
            + "var id=decodeURIComponent(a.getAttribute('href').slice(1));"
            + "window.muLock(1400);"
            + "try{Android.openAnchor(id);}catch(e){window.muAnchor(id);}});});}catch(e){}"
            // ---- a copy button on every code block
            + "try{all('pre').forEach(function(pre,i){"
            // A Mermaid block is a <pre> only until it is drawn. Adding the button to it
            // while mermaid.run() is still reading the element puts the word "Copy" into
            // the diagram source, and half the diagrams in a long document fail with a
            // syntax error that says nothing about the real cause.
            + "if(pre.classList.contains('mermaid')||pre.closest('.mu-nocopy')"
            + "||pre.querySelector('.mu-copy'))return;"
            + "pre.classList.add('mu-has-copy');"
            + "var btn=document.createElement('button');btn.type='button';"
            + "btn.className='mu-copy';btn.setAttribute('aria-label','Copy code');"
            + "btn.textContent='Copy';"
            + "btn.addEventListener('click',function(ev){ev.preventDefault();ev.stopPropagation();"
            + "var code=pre.querySelector('code');"
            + "var text=(code?code.innerText:pre.innerText)||'';"
            + "text=text.replace(/\\n$/,'');"
            + "try{Android.copyCode(text);}catch(e){"
            + "if(navigator.clipboard){navigator.clipboard.writeText(text);}}"
            + "btn.textContent='Copied';btn.classList.add('mu-copied');"
            + "setTimeout(function(){btn.textContent='Copy';"
            + "btn.classList.remove('mu-copied');},1400);});"
            + "pre.appendChild(btn);});}catch(e){}"
            // ---- pick the engines this page needs, fetch the ones that did not load,
            // then start everything that draws
            + "var need=[];"
            + "if(document.querySelector('[data-tex]'))need.push(['katex','katex.min.js']);"
            + "if(window.MU_MHCHEM)need.push(['mhchem','mhchem.min.js']);"
            + "if(document.querySelector('.mermaid'))need.push(['mermaid','mermaid.min.js']);"
            + "if(document.querySelector('.mu-diagram[data-lang=graphviz],.mu-diagram[data-lang=plantuml]'))"
            + "need.push(['Viz','viz-global.js']);"
            + "if(document.querySelector('.mu-diagram[data-lang=plantuml]'))"
            + "need.push(['PlantUML','plantuml.js']);"
            + "if(document.querySelector('.mu-diagram[data-lang=vega]')){"
            + "need.push(['vega','vega.min.js']);need.push(['vegaLite','vega-lite.min.js']);}"
            // an engine fetched over the bridge arrives after the tags that depend on it
            // have already run, so the dependent is loaded again to bind to it
            + "function muRepair(done){"
            + "var again=[];"
            + "if(BRIDGED['vega.min.js']||BRIDGED['vega-lite.min.js']){"
            + "if(document.querySelector('.mu-diagram[data-lang=vega]'))again.push('vega-lite.min.js');}"
            + "if(BRIDGED['viz-global.js']&&document.querySelector('.mu-diagram[data-lang=plantuml]'))"
            + "again.push('plantuml.js');"
            + "var i=0;(function step(){if(i>=again.length){done();return;}"
            + "var code='';try{code=Android.loadAsset(again[i++]);}catch(e){code='';}"
            + "if(code&&code.length>50){try{muInjectScoped(code);}catch(e2){}}"
            + "setTimeout(step,0);})();}"
            + "var began=false;"
            + "function muBegin(){began=true;"
            + "try{muStart();}catch(e){}"
            + "try{if(window.muDiagrams)window.muDiagrams();}catch(e){}"
            + "try{if(window.muKaTeXContrib)window.muKaTeXContrib();}catch(e){}}"
            + "muEnsure(need,0,function(){muRepair(muBegin);});"
            // Whatever is still missing after five seconds is drawn with whatever is
            // here, rather than leaving the reader looking at source code.
            + "setTimeout(function(){if(!began)muBegin();},5000);"
            // And an engine that arrives after that - a slow bridge copy of a three
            // megabyte bundle, a script the WebView queued behind something else - is
            // used when it lands instead of being ignored forever. It draws again when
            // the set of loaded engines changes while something is still waiting.
            + "function muWaiting(){"
            + "var t=all('[data-tex]');for(var i=0;i<t.length;i++){"
            + "if(!t[i].querySelector('.katex'))return true;}"
            + "return !!document.querySelector('.mu-diagram[data-rendered=failed],'"
            + " +'pre.mermaid:not([data-processed])');}"
            + "function muSignature(){var s='';for(var i=0;i<need.length;i++){"
            + "s+=muHas(need[i][0])?'1':'0';}return s+(began?'+':'-');}"
            + "var heals=0,lastSignature='';(function heal(){"
            + "if(heals++>60)return;"
            + "setTimeout(function(){"
            + "var signature=muSignature();"
            + "if(began&&signature!==lastSignature&&muWaiting()){"
            + "lastSignature=signature;"
            + "try{muStart();}catch(e){}"
            + "try{if(window.muDiagrams)window.muDiagrams();}catch(e){}}"
            + "heal();},500);})();"
            + "})();";

    static String bootstrap(boolean dark) {
        return BOOTSTRAP.replace("@@MERMAID_THEME@@", dark ? "'dark'" : "'default'");
    }

}
