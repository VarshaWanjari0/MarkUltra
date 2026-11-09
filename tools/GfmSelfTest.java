import com.markultra.editor.md.GfmHtml;
import com.markultra.editor.md.MdTasks;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless self-check for the GitHub rendering pipeline.
 *
 * <p>No JUnit, no emulator: compile it against the app's classes plus the flexmark jars
 * and run it. Every check is a plain assertion on the HTML that {@link GfmHtml}
 * produces for a snippet, so a regression in the parser configuration, the post
 * processors or the sanitiser fails loudly.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * javac -cp app/build/intermediates/javac/debug/classes:$(ls ~/.gradle/.../*.jar) \
 *       -d /tmp/selftest tools/GfmSelfTest.java
 * java -cp /tmp/selftest:... GfmSelfTest [extra.md]
 * </pre>
 */
public final class GfmSelfTest {

    private static int passed;
    private static final List<String> failures = new ArrayList<String>();

    public static void main(String[] args) throws Exception {
        String f = GfmHtml.fragment("");

        // ------------------------------------------------------------- headings
        f = GfmHtml.fragment("# Hello world");
        check("h1 tag", f.contains("<h1 id=\"hello-world\">"));
        f = GfmHtml.fragment("## Sub section");
        check("h2 tag", f.contains("<h2 id=\"sub-section\">"));
        f = GfmHtml.fragment("# A\n\n# A");
        check("duplicate slug", f.contains("id=\"a\"") && f.contains("id=\"a-1\""));
        f = GfmHtml.fragment("## 1. Getting *started*!");
        check("slug drops punctuation", f.contains("id=\"1-getting-started\""));
        f = GfmHtml.fragment("### `code` title");
        check("heading keeps inline html", f.contains("<code class=\"notranslate\">code</code>"));

        // --------------------------------------------------------------- inline
        check("bold", GfmHtml.fragment("**bold**").contains("<strong>bold</strong>"));
        check("italic", GfmHtml.fragment("_em_").contains("<em>em</em>"));
        check("strikethrough", GfmHtml.fragment("~~gone~~").contains("<del>gone</del>"));
        check("inline code", GfmHtml.fragment("a `b` c").contains("<code class=\"notranslate\">b</code>"));
        check("autolink", GfmHtml.fragment("see https://example.com now")
                .contains("<a href=\"https://example.com\">"));
        check("escape marker", GfmHtml.fragment("\\*not italic\\*").contains("*not italic*"));
        check("hard break", GfmHtml.fragment("a  \nb").contains("a<br"));
        check("emoji shortcode", GfmHtml.fragment(":rocket:").contains("\uD83D\uDE80"));

        // --------------------------------------------------------------- tables
        String tableMarkdown = "| Left | Centre | Right |\n|:-----|:------:|------:|\n"
                + "| a | b | c |\n| `x` | **y** | 1 |\n";
        f = GfmHtml.fragment(tableMarkdown);
        check("table element", f.contains("<table>"));
        check("table alignment", f.contains("align=\"left\"") && f.contains("align=\"center\"")
                && f.contains("align=\"right\""));
        check("table cells inline parsed", f.contains("<code class=\"notranslate\">x</code>")
                && f.contains("<strong>y</strong>"));
        check("no raw delimiter row", !f.contains(":-----"));

        // ----------------------------------------------------------- task lists
        f = GfmHtml.fragment("- [ ] open\n- [x] done\n");
        check("task list class", f.contains("contains-task-list"));
        check("task checkbox unchecked", f.contains("<input type=\"checkbox\""));
        check("task checkbox checked", f.contains("checked"));
        check("task bridge index", f.contains("data-task=\"0\"") && f.contains("data-task=\"1\""));

        // ------------------------------------------------------------ code fence
        f = GfmHtml.fragment("```java\nint x = 1; // note\n```\n");
        check("fence language class", f.contains("class=\"highlight highlight-source-java\""));
        check("keyword highlighted", GfmHtml.fragment("```java\npublic int x;\n```")
                .contains("<span class=\"pl-k\">public</span>"));
        check("type highlighted", GfmHtml.fragment("```java\npublic int x;\n```")
                .contains("<span class=\"pl-smi\">int</span>"));
        check("comment highlighted", f.contains("class=\"pl-c\""));
        check("fence escapes html", GfmHtml.fragment("```\n<div>\n```").contains("&lt;div&gt;"));
        check("fence wrapper is a div", GfmHtml.fragment("```java\nint x = 1;\n```")
                .contains("<div class=\"highlight"));
        check("fence pre is notranslate", GfmHtml.fragment("```\nx\n```")
                .contains("<pre class=\"notranslate\"><code class=\"notranslate\">"));
        check("no stray closing tag in maths", !GfmHtml.fragment("```math\nx\n```")
                .contains("</div></math-renderer>"));

        // --------------------------------------------------------------- maths
        f = GfmHtml.fragment("Math $E = mc^2$ here.");
        check("inline math element", f.contains("<math-renderer class=\"js-inline-math\"")
                && f.contains("data-tex=\"E = mc^2\""));
        check("inline math keeps source", f.contains(">$E = mc^2$</math-renderer>"));
        check("prose dollars untouched", GfmHtml.fragment("costs $5 and $6").contains("$5 and $6"));
        f = GfmHtml.fragment("$$\n\\frac{a}{b}\n$$");
        check("display math block", f.contains("class=\"js-display-math\"")
                && f.contains("\\frac{a}{b}"));

        // -------------------------------------------------------------- alerts
        String[] kinds = {"NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION"};
        for (int i = 0; i < kinds.length; i++) {
            f = GfmHtml.fragment("> [!" + kinds[i] + "]\n> Body text\n");
            String cls = "markdown-alert-" + kinds[i].toLowerCase();
            check("alert " + kinds[i], f.contains(cls) && f.contains("Body text")
                    && f.contains("markdown-alert-title"));
            check("alert " + kinds[i] + " has icon", f.contains("<svg viewBox=\"0 0 16 16\""));
        }
        check("alert does not leak marker",
                !GfmHtml.fragment("> [!NOTE]\n> x\n").contains("[!NOTE]"));

        // ------------------------------------------------------------- mermaid
        f = GfmHtml.fragment("```mermaid\ngraph TD; A-->B;\n```");
        check("mermaid pre", f.contains("<pre class=\"mermaid\">"));
        check("mermaid escaped", f.contains("A--&gt;B"));

        // ------------------------------------------------------ raw html blocks
        f = GfmHtml.fragment("<details>\n<summary>More</summary>\n\n**body**\n\n</details>");
        check("details kept", f.contains("<details>") && f.contains("<summary>More</summary>"));
        check("details body rendered", f.contains("<strong>body</strong>"));
        check("kbd kept", GfmHtml.fragment("<kbd>Ctrl</kbd>").contains("<kbd>Ctrl</kbd>"));
        check("center block", GfmHtml.fragment("<p align=\"center\">x</p>")
                .contains("align=\"center\""));
        check("badge row inline", GfmHtml.fragment(
                "![a](https://x/a.svg)\n![b](https://x/b.svg)")
                .split("<p>").length >= 2);

        // ----------------------------------------------------------- footnotes
        f = GfmHtml.fragment("Note[^1].\n\n[^1]: The text\n");
        check("footnote ref", f.contains("footnote-ref"));
        check("footnote body", f.contains("The text"));
        check("footnote backref", f.contains("footnote-backref"));

        // -------------------------------------------------------------- images
        f = GfmHtml.fragment("![alt text](https://x/y.png)");
        check("image tag", f.contains("<img src=\"https://x/y.png\" alt=\"alt text\""));
        GfmHtml.Options noImages = new GfmHtml.Options();
        noImages.images = false;
        f = GfmHtml.fragment("![alt text](https://x/y.png)", noImages);
        check("images off placeholder", f.contains("mu-img-alt") && !f.contains("<img"));

        // ----------------------------------------------------------- sanitizer
        f = GfmHtml.fragment("<script>alert(1)</script>\n\nok\n");
        check("script removed", !f.toLowerCase().contains("<script>alert"));
        check("script body removed", !f.contains("alert(1)"));
        f = GfmHtml.fragment("<img src=x onerror=\"alert(1)\">");
        check("onerror removed", !f.contains("onerror"));
        f = GfmHtml.fragment("[click](javascript:alert(1))");
        check("javascript url removed", !f.contains("javascript:"));
        f = GfmHtml.fragment("<iframe src=\"https://evil\"></iframe>");
        check("iframe removed", !f.contains("<iframe"));
        check("style block removed", !GfmHtml.fragment("<style>p{}</style>").contains("<style>"));

        // ------------------------------------------------------------- lists
        f = GfmHtml.fragment("- a\n  - b\n    - c\n");
        check("nested lists", f.split("<ul>", -1).length - 1 >= 3);
        f = GfmHtml.fragment("1. one\n2. two\n");
        check("ordered list", f.contains("<ol>"));
        check("blockquote", GfmHtml.fragment("> quote\n").contains("<blockquote>"));
        check("hr", GfmHtml.fragment("---\n").contains("<hr"));

        // ------------------------------------------------------------ task model
        String tasks = "- [ ] one\n  - [x] nested\n> - [ ] quoted\n- plain\n";
        check("task count", com.markultra.editor.md.MdTasks.count(tasks) == 3);
        check("task done count", com.markultra.editor.md.MdTasks.doneCount(tasks) == 1);
        String flipped = com.markultra.editor.md.MdTasks.toggle(tasks, 0, true);
        check("task toggle", flipped != null && flipped.startsWith("- [x] one"));
        check("task toggle nested", com.markultra.editor.md.MdTasks.toggle(tasks, 1, false)
                .contains("  - [ ] nested"));
        check("task toggle quoted", com.markultra.editor.md.MdTasks.toggle(tasks, 2, true)
                .contains("> - [x] quoted"));
        check("task no-op when already set", com.markultra.editor.md.MdTasks.toggle(tasks, 1, true) == null);
        check("task out of range", com.markultra.editor.md.MdTasks.toggle(tasks, 99, true) == null);
        check("task list ignores plain bullets", com.markultra.editor.md.MdTasks.count("- plain\n") == 0);

        // --------------------------------------------------- relative images
        String base = com.markultra.editor.io.RelativeImage.directoryOf("primary:docs/README.md");
        check("relative path joins", "primary:docs/banner.png".equals(
                com.markultra.editor.io.RelativeImage.join(base, "banner.png")));
        // one folder up from "primary:docs/" leaves the document id prefix behind
        check("relative parent collapses", "logo.png".equals(
                com.markultra.editor.io.RelativeImage.join(base, "../logo.png")));
        check("relative escape blocked",
                com.markultra.editor.io.RelativeImage.join(base, "../../../etc/passwd") == null);
        check("relative absolute stays rooted", "img/a.png".equals(
                com.markultra.editor.io.RelativeImage.join(base, "/img/a.png")));
        check("bundled assets pass through",
                com.markultra.editor.io.RelativeImage.isBundledAsset("katex.min.js")
                && !com.markultra.editor.io.RelativeImage.isBundledAsset("docs/a.png"));
        check("mime of png", "image/png".equals(
                com.markultra.editor.io.RelativeImage.mimeOf("x/Y.PNG")));

        // -------------------------------------------------------------- pages
        GfmHtml.Options light = new GfmHtml.Options();
        String page = GfmHtml.page("# t\n", light);
        check("page doctype", page.startsWith("<!doctype html>"));
        check("page has css", page.contains(".markdown-body"));
        check("page light colours", page.contains(light.background()));
        check("default light palette", "#ffffff".equals(light.background())
                && "#1f2328".equals(light.foreground()));
        check("page no katex when unused",
                !page.contains("src=\"" + GfmHtml.ASSET_BASE + "katex.min.js\""));
        check("page no mermaid when unused",
                !page.contains("src=\"" + GfmHtml.ASSET_BASE + "mermaid.min.js\""));
        GfmHtml.Options dark = new GfmHtml.Options();
        dark.dark = true;
        // no explicit palette: dark mode has to bring its own colours
        check("dark page", GfmHtml.page("# t", dark).contains("#0d1117"));
        check("dark default palette", "#0d1117".equals(dark.background())
                && "#e6edf3".equals(dark.foreground()));
        check("katex only when maths present",
                GfmHtml.page("$x^2$", light).contains("katex.min.js"));
        check("mermaid only when diagrams present",
                GfmHtml.page("```mermaid\ngraph TD;A-->B;\n```", light).contains("mermaid.min.js"));
        check("no assets folder hard coded in css",
                !GfmCssProxy.build(light).contains("android_asset"));

        // --- the page never asks a WebView for a file:// sub-resource
        String assetPage = GfmHtml.page("$x^2$\n\n```mermaid\ngraph TD;A-->B;\n```\n", light);
        check("page assets are https, not file://", !assetPage.contains("file:///android_asset"));
        check("page assets come from the app's own origin",
                assetPage.contains("https://appassets.androidplatform.net/assets/katex.min.js"));
        check("the head claims the engine names first",
                assetPage.indexOf(GfmHtml.CLAIM_SCRIPT) > 0
                        && assetPage.indexOf(GfmHtml.CLAIM_SCRIPT) < assetPage.indexOf("katex.min.js"));
        check("an engine's assignment is captured, not shadowed",
                GfmHtml.CLAIM_SCRIPT.contains("Object.defineProperty(window,name")
                        && GfmHtml.CLAIM_SCRIPT.contains("CAP[name]=v"));
        check("a failed engine reports what happened",
                assetPage.contains("function muRunText(")
                        && assetPage.contains(" threw ")
                        && assetPage.contains("ran but defined no"));
        check("a late engine is used when it lands",
                assetPage.contains("function muSignature(") && assetPage.contains("muWaiting"));
        check("diagram notes can name the real cause",
                assetPage.contains("muEngineError"));

        // --- an older WebView must not be the reason an engine fails
        check("the polyfills come before any engine",
                assetPage.indexOf(GfmHtml.COMPAT_JS) > 0
                        && assetPage.indexOf(GfmHtml.COMPAT_JS) < assetPage.indexOf("katex.min.js"));
        check("structuredClone is provided", GfmHtml.COMPAT_JS.contains("structuredClone"));
        check("Object.hasOwn is provided", GfmHtml.COMPAT_JS.contains("Object.hasOwn"));
        check("replaceAll is provided", GfmHtml.COMPAT_JS.contains("replaceAll"));
        check("flatMap is provided", GfmHtml.COMPAT_JS.contains("flatMap"));
        check("allSettled is provided", GfmHtml.COMPAT_JS.contains("allSettled"));
        check("WeakRef is provided", GfmHtml.COMPAT_JS.contains("WeakRef"));
        check("polyfills are written in ES5",
                GfmHtml.COMPAT_JS.indexOf("=>") < 0 && GfmHtml.COMPAT_JS.indexOf("const ") < 0
                        && GfmHtml.COMPAT_JS.indexOf("let ") < 0);
        check("the polyfill only fills gaps",
                GfmHtml.COMPAT_JS.contains("if(!Object.hasOwn)")
                        && GfmHtml.COMPAT_JS.contains("if(typeof structuredClone!=='function')"));

        // -------------------------------------------------------------- scale
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            big.append("## Section ").append(i).append("\n\nText with **bold** and `code` and a ")
                    .append("[link](https://example.com/").append(i).append(").\n\n")
                    .append("| a | b |\n|---|---|\n| 1 | 2 |\n\n");
        }
        long t0 = System.nanoTime();
        String bigHtml = GfmHtml.fragment(big.toString());
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        check("large document rendered", bigHtml.length() > 100_000);
        check("large document under 2 s (" + ms + " ms)", ms < 2000);

        // ------------------------------------------------------- external file
        if (args.length > 0) {
            String md = new String(Files.readAllBytes(Paths.get(args[0])), Charset.forName("UTF-8"));
            long start = System.nanoTime();
            String html = GfmHtml.page(md, light);
            long took = (System.nanoTime() - start) / 1_000_000L;
            check("README rendered in " + took + " ms", html.length() > md.length());
            check("README has no leaked task syntax", !html.contains("[ ]"));
            check("README has no leaked table pipes", !html.contains("|---"));
            check("README has no leaked emphasis", !html.contains("**"));
            System.out.println("README: " + md.length() + " chars -> " + html.length()
                    + " chars in " + took + " ms");
        }

        // ------------------------------------------------------------------ page
        // The page the WebView receives: same document the activity hands over.
        String doc = "# Title\n\n**bold** `code`\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n"
                + "- [x] done\n\n> [!NOTE]\n> note\n";
        GfmHtml.Options opts = new GfmHtml.Options();
        String full = GfmHtml.page(doc, opts);
        check("page has doctype", full.startsWith("<!doctype html>"));
        check("page carries github css", full.contains(".markdown-body") && page.contains("padding:16px"));
        check("page renders heading", full.contains("<h1 id=\"title\">Title</h1>"));
        check("page renders strong", full.contains("<strong>bold</strong>"));
        check("page renders code", full.contains("<code class=\"notranslate\">code</code>"));
        check("page renders table", full.contains("<table>") && full.contains("<th>a</th>"));
        check("page renders task list", full.contains("task-list-item"));
        check("page renders alert", full.contains("markdown-alert-note"));
        // the bootstrap names every engine it knows; what must not appear is a tag that
        // loads one, or the stylesheet that goes with it
        check("page omits katex when unused",
                !full.contains("src=\"" + GfmHtml.ASSET_BASE + "katex.min.js\"")
                        && !full.contains("katex.css"));
        check("page omits mermaid when unused",
                !full.contains("src=\"" + GfmHtml.ASSET_BASE + "mermaid.min.js\""));
        String mathPage = GfmHtml.page("$x$", opts);
        check("page pulls katex for maths", mathPage.contains("katex.min.js"));
        check("page pulls mermaid for diagrams", GfmHtml.page("```mermaid\ngraph TD;\n```", opts)
                .contains("mermaid.min.js"));
        check("dark page uses dark canvas", GfmHtml.page(doc, darkOpts()).contains("#0d1117"));

        // ------------------------------------------------------- MarkUltra+ mode
        GfmHtml.Options gh = new GfmHtml.Options();
        GfmHtml.Options rich = new GfmHtml.Options();
        rich.rich = true;
        String deflist = "Term\n: meaning\n";
        // github.com itself does not, but a document written for Pandoc expects it and
        // nothing about plain GFM changes because of it (see the 1.7 notes)
        check("github mode renders definition list", GfmHtml.fragment(deflist, gh).contains("<dl>"));
        check("rich mode renders definition list", GfmHtml.fragment(deflist, rich).contains("<dl>"));
        String admon = "!!! note \"Heads up\"\n    Body text.\n";
        check("rich mode renders admonition", GfmHtml.fragment(admon, rich)
                .contains("adm-block adm-note"));
        check("rich mode has no admonition in github mode",
                !GfmHtml.fragment(admon, gh).contains("admonition"));
        check("rich mode renders wiki link", GfmHtml.fragment("[[Note]]", rich)
                .contains("class=\"wikilink\" href=\"Note\""));
        check("github mode keeps wiki brackets", GfmHtml.fragment("[[Note]]", gh)
                .contains("[[Note]]"));
        check("rich mode renders superscript", GfmHtml.fragment("x^2^", rich).contains("<sup>"));
        check("rich mode renders insert", GfmHtml.fragment("++new++", rich).contains("<ins>"));
        check("rich mode renders abbreviation", GfmHtml.fragment(
                "*[HTML]: HyperText Markup Language\n\nHTML is fun\n", rich).contains("<abbr"));
        check("rich mode renders video", GfmHtml.fragment("![video](clip.mp4)\n", rich)
                .contains("<video controls"));
        check("rich mode renders audio", GfmHtml.fragment("![audio](clip.mp3)\n", rich)
                .contains("<audio controls"));
        check("github mode keeps media as an image", GfmHtml.fragment("![v](clip.mp4)\n", gh)
                .contains("<img"));
        check("rich mode keeps front matter as metadata", GfmHtml.fragment(
                "---\ntitle: Shown\n---\n\nBody\n", rich).contains("title: Shown"));
        check("github mode keeps maths untouched", GfmHtml.fragment("Math $x$", gh)
                .contains("<math-renderer"));
        check("rich mode keeps maths", GfmHtml.fragment("Math $x$", rich)
                .contains("<math-renderer"));

        // ------------------------------------------------------- text tools
        check("text tools upper", "ABC".equals(
                com.markultra.editor.md.TextTools.apply("upper", "abc")));
        check("text tools title", "Hello World".equals(
                com.markultra.editor.md.TextTools.apply("title", "hello world")));
        check("text tools snake", "hello_world".equals(
                com.markultra.editor.md.TextTools.apply("snake", "Hello World")));
        check("text tools camel", "helloWorld".equals(
                com.markultra.editor.md.TextTools.apply("camel", "hello world")));
        check("text tools sort", "a\nb\nc".equals(
                com.markultra.editor.md.TextTools.apply("sort", "c\na\nb")));
        check("text tools dedupe", "a\nb".equals(
                com.markultra.editor.md.TextTools.apply("dedupe", "a\nb\na")));
        check("text tools number", "1. a\n2. b".equals(
                com.markultra.editor.md.TextTools.apply("number", "a\nb")));
        check("text tools json", "{\n  \"a\": 1\n}".equals(
                com.markultra.editor.md.TextTools.apply("json", "{\"a\":1}")));
        check("text tools base64 round trip", "hello".equals(
                com.markultra.editor.md.TextTools.apply("b64dec",
                        com.markultra.editor.md.TextTools.apply("b64enc", "hello"))));
        check("text tools strip markdown", "Title\n\nbold".equals(
                com.markultra.editor.md.TextTools.apply("strip", "# Title\n\n**bold**")));
        check("text tools escape html", "&lt;b&gt;".equals(
                com.markultra.editor.md.TextTools.apply("htmlesc", "<b>")));

        // ------------------------------------------------------- paste convert
        String csv = "a,b,c\n1,2,3\n4,5,6\n";
        String table = com.markultra.editor.md.PasteConvert.tableToMarkdown(csv);
        check("csv to table header", table != null && table.startsWith("| a | b | c |"));
        check("csv to table alignment", table != null && table.contains("---:"));
        check("tsv to table", com.markultra.editor.md.PasteConvert.looksLikeTable("a\tb\n1\t2"));
        check("prose is not a table", !com.markultra.editor.md.PasteConvert
                .looksLikeTable("just one line of prose"));
        String md = com.markultra.editor.md.PasteConvert.htmlToMarkdown(
                "<p>Hello <strong>world</strong></p><ul><li>one</li><li>two</li></ul>");
        check("html to markdown", md != null && md.contains("**world**") && md.contains("one"));

        // ------------------------------------------------------- templates
        boolean cursorEverywhere = true;
        java.util.List<com.markultra.editor.md.Templates.Template> tpls =
                com.markultra.editor.md.Templates.all();
        for (int i = 0; i < tpls.size(); i++) {
            if (!tpls.get(i).body.contains(com.markultra.editor.md.Templates.CURSOR)) {
                cursorEverywhere = false;
            }
        }
        check("every template has a cursor", cursorEverywhere);
        check("templates are distinct", tpls.size() >= 8);

        // ------------------------------------------------------- text file codec
        com.markultra.editor.io.TextFile.Decoded plain =
                com.markultra.editor.io.TextFile.decode("a\nb\n".getBytes("UTF-8"));
        check("decodes plain utf8", "a\nb\n".equals(plain.text));
        check("eol detected lf", com.markultra.editor.io.TextFile.EOL_LF.equals(plain.eol));
        check("no bom by default", !plain.bom);

        byte[] crlfBytes = "a\r\nb\r\n".getBytes("UTF-8");
        com.markultra.editor.io.TextFile.Decoded crlf =
                com.markultra.editor.io.TextFile.decode(crlfBytes);
        check("crlf detected", com.markultra.editor.io.TextFile.EOL_CRLF.equals(crlf.eol));
        check("crlf normalised for editing", "a\nb\n".equals(crlf.text));
        byte[] back = com.markultra.editor.io.TextFile.encode(crlf.text, crlf.charset,
                crlf.eol, crlf.bom);
        check("crlf written back", "a\r\nb\r\n".equals(new String(back, "UTF-8")));

        byte[] bomBytes = new byte[]{ (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i' };
        com.markultra.editor.io.TextFile.Decoded bommed =
                com.markultra.editor.io.TextFile.decode(bomBytes);
        check("bom stripped", "hi".equals(bommed.text));
        check("bom remembered", bommed.bom);
        byte[] bommedBack = com.markultra.editor.io.TextFile.encode(bommed.text,
                bommed.charset, bommed.eol, bommed.bom);
        check("bom written back", bommedBack.length == 5 && (bommedBack[0] & 0xFF) == 0xEF);

        byte[] utf16Bytes = new byte[]{ (byte) 0xFF, (byte) 0xFE, 'h', 0, 'i', 0, '\n', 0 };
        com.markultra.editor.io.TextFile.Decoded utf16 =
                com.markultra.editor.io.TextFile.decode(utf16Bytes);
        check("utf16le detected", com.markultra.editor.io.TextFile.UTF16LE.equals(utf16.charset));
        check("utf16le decoded", "hi\n".equals(utf16.text));
        byte[] utf16Back = com.markultra.editor.io.TextFile.encode(utf16.text, utf16.charset,
                utf16.eol, utf16.bom);
        check("utf16le written back", utf16Back.length == 8
                && (utf16Back[0] & 0xFF) == 0xFF && utf16Back[6] == '\n' && utf16Back[7] == 0);

        byte[] utf16beBytes = new byte[]{ (byte) 0xFE, (byte) 0xFF, 0, 'h', 0, 'i' };
        check("utf16be detected", com.markultra.editor.io.TextFile.UTF16BE.equals(
                com.markultra.editor.io.TextFile.decode(utf16beBytes).charset));
        check("dominant eol wins", com.markultra.editor.io.TextFile.EOL_CRLF.equals(
                com.markultra.editor.io.TextFile.detectEol("a\r\nb\r\nc\n")));
        check("lone cr normalised", "a\nb".equals(
                com.markultra.editor.io.TextFile.normalise("a\rb")));
        check("empty file decodes", "".equals(
                com.markultra.editor.io.TextFile.decode(new byte[0]).text));

        check("binary detected", com.markultra.editor.io.TextFile.looksBinary(
                new byte[]{ 'a', 0, 'b' }));
        check("text is not binary", !com.markultra.editor.io.TextFile.looksBinary(
                "hello".getBytes("UTF-8")));
        check("utf16 is not binary", !com.markultra.editor.io.TextFile.looksBinary(
                new byte[]{ (byte) 0xFF, (byte) 0xFE, 'a', 0 }));

        check("size formatting", "512 B".equals(com.markultra.editor.io.TextFile.humanSize(512))
                && "1.5 KB".equals(com.markultra.editor.io.TextFile.humanSize(1536))
                && "2.0 MB".equals(com.markultra.editor.io.TextFile.humanSize(2 * 1024 * 1024)));
        check("extension of name", "md".equals(
                com.markultra.editor.io.TextFile.extensionOf("README.MD")));
        check("md extension added", "notes.md".equals(
                com.markultra.editor.io.TextFile.withMarkdownExtension("notes")));
        check("copy name", "notes (copy).md".equals(
                com.markultra.editor.io.TextFile.copyName("notes.md")));
        check("name sanitised", "evil.md".equals(
                com.markultra.editor.io.TextFile.sanitiseName("../evil.md")));
        check("empty name falls back", "Untitled.md".equals(
                com.markultra.editor.io.TextFile.sanitiseName("   ")));

        // ------------------------------------------------------------- maths (1.6)
        com.markultra.editor.md.GfmHtml.Options mo = new com.markultra.editor.md.GfmHtml.Options();
        String katexPage = com.markultra.editor.md.GfmHtml.page(
                "Inline $e^{i\\pi}+1=0$ and display:\n\n$$\n\\int_0^1 x^2\\,\\mathrm{d}x = \\frac{1}{3}\n$$\n", mo);
        check("math page loads katex css", katexPage.contains("href=\"" + GfmHtml.ASSET_BASE + "katex.css\""));
        check("math page loads katex js", katexPage.contains("src=\"" + GfmHtml.ASSET_BASE + "katex.min.js\""));
        check("katex renders html and mathml", katexPage.contains("output:'htmlAndMathml'"));
        check("math pass is re-runnable", katexPage.contains("window.muMath=function()"));
        check("macros exported", katexPage.contains("window.MU_MACROS"));
        check("macro \\R defined", katexPage.contains("MU_MACROS['\\\\R']"));
        check("macro \\abs defined", katexPage.contains("MU_MACROS['\\\\abs']"));
        check("display maths tagged", katexPage.contains("class=\"js-display-math\""));
        check("display maths keeps tex", katexPage.contains("data-tex=\"\\int_0^1"));
        String plainPage = com.markultra.editor.md.GfmHtml.page("no maths here", mo);
        check("no maths means no katex payload", !plainPage.contains("katex.css")
                && !plainPage.contains("src=\"" + GfmHtml.ASSET_BASE + "katex.min.js\""));

        String mathCss = com.markultra.editor.md.GfmHtml.page("$$x$$", mo);
        check("math css: display block centred", mathCss.contains(".katex-display{"));
        check("math css: raw tex fallback styled", mathCss.contains(".mu-math-raw{"));
        check("math css: mathml hidden from view", mathCss.contains(".katex-mathml{"));

        String exported = com.markultra.editor.md.MdConvert.toHtmlDocument(
                "# T\n\n$x^2$\n", "T", false);
        check("export uses github stylesheet", exported.contains("markdown-body")
                && exported.contains("data-tex="));
        check("export loads katex from cdn", exported.contains("cdn.jsdelivr.net/npm/katex"));

        // every library equation is real TeX: balanced braces and no placeholders
        boolean balanced = true;
        boolean delimited = true;
        int equations = 0;
        for (int i = 0; i < com.markultra.editor.md.Equations.DISPLAY.length; i++) {
            com.markultra.editor.md.Equations.Equation e =
                    com.markultra.editor.md.Equations.DISPLAY[i];
            equations++;
            int depth = 0;
            for (int c = 0; c < e.tex.length(); c++) {
                char ch = e.tex.charAt(c);
                if (ch == '{') depth++;
                else if (ch == '}') depth--;
                if (depth < 0) balanced = false;
            }
            if (depth != 0) balanced = false;
            String snippet = e.markdown();
            if (!snippet.startsWith("$$\n") || !snippet.endsWith("\n$$\n")) delimited = false;
        }
        for (int i = 0; i < com.markultra.editor.md.Equations.INLINE.length; i++) {
            com.markultra.editor.md.Equations.Equation e =
                    com.markultra.editor.md.Equations.INLINE[i];
            equations++;
            int depth = 0;
            for (int c = 0; c < e.tex.length(); c++) {
                char ch = e.tex.charAt(c);
                if (ch == '{') depth++;
                else if (ch == '}') depth--;
            }
            if (depth != 0) balanced = false;
            String snippet = e.markdown();
            if (!snippet.startsWith("$") || !snippet.endsWith("$") || snippet.startsWith("$$")) delimited = false;
        }
        check("equation library has " + equations + " snippets", equations >= 26);
        check("equation braces balanced", balanced);
        check("equation delimiters correct", delimited);
        check("equation search finds fractions", !com.markultra.editor.md.Equations
                .search("fraction").isEmpty());
        String renderedEquation = com.markultra.editor.md.GfmHtml.fragment(
                com.markultra.editor.md.Equations.DISPLAY[0].markdown(), mo);
        // the block path turns $$ ... $$ into a display maths element, not a paragraph
        check("library equation renders as maths",
                renderedEquation.contains("class=\"js-display-math\"")
                        && renderedEquation.contains("data-tex=\"x = \\frac{-b")
                        && !renderedEquation.contains("<p>$$"));

        // ------------------------------------------------- bundled KaTeX assets (1.6)
        // The maths bug that mattered was not in the renderer: the stylesheet shipped
        // with dangling "format()" descriptors, so every @font-face was invalid and maths
        // fell back to a serif. These checks keep that from coming back.
        java.io.File assets = new java.io.File("app/src/main/assets");
        if (!assets.isDirectory()) assets = new java.io.File("../app/src/main/assets");
        java.io.File katexCss = new java.io.File(assets, "katex.css");
        check("katex.css ships", katexCss.isFile());
        if (katexCss.isFile()) {
            String cssText = new String(Files.readAllBytes(katexCss.toPath()), Charset.forName("UTF-8"));
            int faces = 0;
            int at = 0;
            while ((at = cssText.indexOf("@font-face{", at)) >= 0) {
                int end = cssText.indexOf('}', at);
                if (end < 0) break;
                faces++;
                at = end;
            }
            check("katex.css declares 20 font faces", faces == 20);
            check("katex.css has no dangling format lists",
                    !cssText.contains("format(\"woff2\") format(\"woff\")"));
            check("katex.css points at the bundled fonts",
                    cssText.contains("url(fonts/KaTeX_Main-Regular.woff2)"));
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("url\\(fonts/([A-Za-z0-9_-]+\\.woff2)\\)")
                    .matcher(cssText);
            int found = 0;
            boolean allPresent = true;
            while (m.find()) {
                found++;
                if (!new java.io.File(new java.io.File(assets, "fonts"), m.group(1)).isFile()) {
                    allPresent = false;
                }
            }
            check("all " + found + " referenced font files exist", found == 20 && allPresent);
        }
        check("katex.min.js ships", new java.io.File(assets, "katex.min.js").isFile());
        check("mermaid.min.js ships", new java.io.File(assets, "mermaid.min.js").isFile());

        // =================================================================================
        // 1.7: the Pandoc and extended-Markdown constructs of the stress document
        // =================================================================================

        // ---- pandoc raw TeX spans
        f = GfmHtml.fragment("Inline: (`\\sum`{=tex}\\_{i=1}\\^{n} i = `\\frac{n(n+1)}{2}`{=tex}).");
        check("tex span becomes inline maths",
                f.contains("class=\"js-inline-math") && f.contains("data-tex=\"\\sum_{i=1}^{n}\""));
        check("tex span keeps the second expression", f.contains("data-tex=\"\\frac{n(n+1)}{2}\""));
        check("tex span leaves no markers", f.indexOf('\u0001') < 0 && f.indexOf("{=tex}") < 0);
        f = GfmHtml.fragment("Greek: (`\\alpha`{=tex}, `\\beta`{=tex}).");
        check("single command span is maths",
                f.contains("data-tex=\"\\alpha\"") && f.contains("data-tex=\"\\beta\""));
        f = GfmHtml.fragment("(`\\displaystyle `{=tex}x)");
        check("layout-only span stays text", f.indexOf("math-renderer") < 0
                && f.contains("displaystyle"));

        // ---- pandoc raw HTML spans and blocks
        f = GfmHtml.fragment("`<strong>`{=html}Bold`</strong>`{=html}");
        check("html span is live html", f.contains("<strong>Bold</strong>"));
        f = GfmHtml.fragment("```{=html}\n<mark>raw</mark>\n```\n");
        check("html block is kept as html", f.contains("<mark>raw</mark>"));
        f = GfmHtml.fragment("```{=html}\n<script>alert(1)</script><b>ok</b>\n```\n");
        check("html block is still sanitised", f.indexOf("<script") < 0
                && f.contains("<b>ok</b>"));

        // ---- latex delimiters
        f = GfmHtml.fragment("Block:\n\n\\[ x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a} \\]\n");
        check("bracket display maths", f.contains("js-display-math")
                && f.contains("data-tex=\"x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\""));
        f = GfmHtml.fragment("Inline \\(a^2 + b^2\\) here.");
        check("paren inline maths", f.contains("js-inline-math") && f.contains("data-tex=\"a^2 + b^2\""));
        f = GfmHtml.fragment("\\[ A =\n```{=tex}\n\\begin{bmatrix}\n1 & 2 \\\\\n3 & 4\n\\end{bmatrix}\n```\n\\]\n");
        check("raw tex block inside a region", f.contains("\\begin{bmatrix}")
                && !f.contains("```") && !f.contains("{=tex}"));
        f = GfmHtml.fragment("Formula: \\[ x = `\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}`{=tex} \\]");
        check("span inside display region", f.contains("data-tex=\"x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\""));
        f = GfmHtml.fragment("\\[not a link\\](https://example.com)");
        check("escaped bracket link is not maths", f.indexOf("math-renderer") < 0);

        // ---- maths survives the markdown parser untouched
        f = GfmHtml.fragment("\\[ f(x) = x^{n-1} + a_b \\]\n");
        check("no sup inside maths", !f.contains("<sup>{\\infty}") && !f.contains("x<sup>"));
        check("display maths keeps one line", f.indexOf("x^{n-1} + a_b") >= 0
                && f.indexOf("x^{n-1}<br") < 0);
        f = GfmHtml.fragment("```\n\\[x\\]\n\n  a   b\n  --- ---\n  1   2\n```\n");
        check("code block is never rewritten", f.contains("\\[x\\]") && f.indexOf("<table") < 0
                && f.indexOf("math-renderer") < 0);

        // ---- escaped alerts
        f = GfmHtml.fragment("> \\[!NOTE\\] This is a note alert.\n> Second line.\n");
        check("escaped alert renders", f.contains("markdown-alert-note"));
        check("escaped alert keeps its text", f.contains("This is a note alert.")
                && f.contains("Second line."));
        f = GfmHtml.fragment("> \\[!WARNING\\] Careful.\n");
        check("escaped warning renders", f.contains("markdown-alert-warning") && f.contains("Careful."));

        // ---- pandoc tables without pipes
        String simple = "  Feature           Supported? Notes\n"
                + "  --------------- ------------ -----------------\n"
                + "  Headings                 Yes H1--H6\n"
                + "  Bold                     Yes Strong emphasis\n";
        f = GfmHtml.fragment(simple);
        check("simple table becomes a table", f.contains("<table>"));
        check("simple table header", f.contains("<th>Feature</th>") && f.contains("<th>Notes</th>"));
        check("simple table body", f.contains("<td>Headings</td>") && f.contains(">Yes</td>"));
        check("simple table columns", f.contains("H1--H6"));
        // the exact geometry of the wide table in the stress document: a bottomless
        // sample would not test the column offsets that the rule is there to fix
        String wide = "  --------------------------------------------------------------------\n"
                + "            ID Component   Input        Expected     Priority   Remarks\n"
                + "                                        output                        \n"
                + "  ------------ ----------- ------------ ------------ ---------- -------\n"
                + "           001 Parser      `# Title`    Heading      High       Basic\n"
                + "\n"
                + "           002 Parser      `**text**`   Bold         High       Inline\n"
                + "                                                                   formatting\n";
        f = GfmHtml.fragment(wide);
        check("multiline table becomes a table", f.contains("<table>")
                && f.contains(">001</td>") && f.contains("<td>Parser</td>"));
        check("multiline table keeps both rows", f.contains(">002</td>") && f.contains("<td>Bold</td>"));
        check("multiline table joins wrapped cells", f.contains("Inline formatting"));
        check("multiline table drops the borders", f.indexOf("----") < 0);
        f = GfmHtml.fragment("  Test     Expected\n  ------   --------\n  Open     No crash\n");
        check("final test card table", f.contains("<td>Open</td>") && f.contains("<td>No crash</td>"));

        // ---- definition lists, superscript, subscript in the GitHub profile
        f = GfmHtml.fragment("Term A\n:   Definition A, with **bold**.\n");
        check("definition list in github mode", f.contains("<dl>") && f.contains("<dt>Term A</dt>")
                && f.contains("<dd>"));
        f = GfmHtml.fragment("x^2^ superscript");
        check("superscript in github mode", f.contains("<sup>2</sup>"));
        f = GfmHtml.fragment("H~2~O water");
        check("subscript", f.contains("<sub>2</sub>"));
        check("strikethrough is not subscript", GfmHtml.fragment("~~gone~~").contains("<del>gone</del>"));
        check("sub in code is literal",
                GfmHtml.fragment("`H~2~O`").contains("<code class=\"notranslate\">H~2~O</code>"));

        // ---- YAML front matter
        f = GfmHtml.fragment("---\nauthor: QA\ndate: 2026-10-30\ntags:\n- a\n- b\ntitle: T\n---\n\n# Body\n");
        check("front matter block", f.contains("yaml-front-matter") && f.contains("author: QA"));
        check("front matter is not a rule", f.indexOf("<hr") < 0);
        check("front matter keeps the body", f.contains("<h1 id=\"body\">Body</h1>"));
        f = GfmHtml.fragment("---\n\nJust prose that happens to start with a rule.\n\n---\n");
        check("rule and prose stay a rule", f.indexOf("<hr") >= 0 && f.indexOf("yaml-front-matter") < 0);

        // ---- escapes inside table code spans
        f = GfmHtml.fragment("| Expression | Meaning |\n|---|---|\n| `a \\| b` | pipe |\n");
        check("code span in a table shows a pipe",
                f.contains("<code class=\"notranslate\">a | b</code>"));

        // ---- every table of contents target has an id
        String[] slugs = {"table-of-contents", "heading-levels", "paragraphs-and-inline-formatting",
                "lists", "links-and-references", "images", "blockquotes-and-github-alerts",
                "code-blocks", "tables", "task-lists", "footnotes", "mathematics", "raw-html",
                "diagrams-and-mermaid", "emoji-and-unicode", "escaping-and-parser-edge-cases",
                "long-content-and-layout-stress", "renderer-test-checklist"};
        StringBuilder toc = new StringBuilder();
        for (int i = 0; i < slugs.length; i++) {
            String title = slugs[i].replace('-', ' ');
            title = title.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + title.substring(1);
            toc.append("# ").append(title).append("\n\ntext\n\n");
        }
        f = GfmHtml.fragment(toc.toString());
        boolean allSlugs = true;
        for (int i = 0; i < slugs.length; i++) {
            if (f.indexOf("id=\"" + slugs[i] + "\"") < 0) allSlugs = false;
        }
        check("every contents target has an id", allSlugs);
        check("toc link is kept",
                GfmHtml.fragment("[toc](#table-of-contents)").contains("href=\"#table-of-contents\""));

        // ---- copy button and anchor jump are in the page
        String codePage = GfmHtml.page("```js\nlet a = 1;\n```\n", new GfmHtml.Options());
        check("page has copy button script", codePage.contains("mu-copy"));
        check("page has anchor jump", codePage.contains("muAnchor"));
        check("page has scroll lock", codePage.contains("muLock"));
        check("copy button is hidden when printing",
                codePage.contains("@media print{.mu-copy{display:none"));
        check("anchor script is exposed", GfmHtml.anchorScript("tables").contains("muAnchor('tables')"));

        // ---- 1.7.1: diagrams, containers, fence runs, one-character task writes
        GfmHtml.Options richOpts = new GfmHtml.Options();
        richOpts.rich = true;
        String diagrams = GfmHtml.fragment("```graphviz\ndigraph G { a -> b; }\n```\n\n"
                + "```plantuml\n@startuml\nA -> B: hi\n@enduml\n```\n\n"
                + "```vega-lite\n{\"mark\":\"bar\"}\n```\n", richOpts);
        check("graphviz fence becomes a diagram block", diagrams.contains("data-lang=\"graphviz\""));
        check("plantuml fence becomes a diagram block", diagrams.contains("data-lang=\"plantuml\""));
        check("vega fence becomes a diagram block", diagrams.contains("data-lang=\"vega\""));
        check("diagram source is escaped, not executed",
                diagrams.contains("digraph G { a -&gt; b; }"));
        check("diagram blocks get no copy button", diagrams.contains("mu-nocopy"));
        String diagPage = GfmHtml.page("```graphviz\ndigraph G { a -> b; }\n```\n", richOpts);
        check("page loads the diagram runner",
                diagPage.contains(GfmHtml.ASSET_BASE + "diagrams.js"));
        String noDiagramPage = GfmHtml.page("plain text\n", richOpts);
        check("a plain document loads no diagram runner", !noDiagramPage.contains("diagrams.js"));

        String containers = GfmHtml.fragment("::: note\nBody text.\n:::\n\n"
                + "::: warning Custom title\nCareful.\n:::\n\n"
                + "::: details Click to expand\nHidden.\n:::\n", richOpts);
        check("::: note becomes an alert", containers.contains("markdown-alert-note"));
        check("container keeps its custom title", containers.contains("Custom title")
                && !containers.contains(":::"));
        check("::: details becomes a details element",
                containers.contains("<details") && containers.contains("<summary"));
        check("container body is rendered as Markdown",
                GfmHtml.fragment("::: note\n**bold** body\n:::\n", richOpts)
                        .contains("<strong>bold</strong>"));
        check("an unclosed container ends at the end of the document",
                GfmHtml.fragment("::: note\nbody\n", richOpts).contains("</div>"));

        String nested = GfmHtml.fragment("````markdown\n```js\nlet a = 1;\n```\n````\n\n"
                + "Water: H~2~O\n");
        check("a four-backtick fence closes on four backticks", nested.contains("let a = 1;"));
        check("a nested fence example keeps the rest of the document",
                nested.contains("H<sub>2</sub>O"));

        String mermaidPage = GfmHtml.page("```mermaid\nflowchart TD\nA-->B\n```\n", richOpts);
        check("mermaid diagrams are drawn one at a time",
                mermaidPage.contains("drawMermaid") && mermaidPage.contains("nodes:[mq[i]]"));
        check("copy buttons skip mermaid blocks",
                mermaidPage.contains("pre.classList.contains('mermaid')"));

        MdTasks.Task first = MdTasks.taskAt("- [ ] one\n- [x] two\n", 0);
        MdTasks.Task second = MdTasks.taskAt("- [ ] one\n- [x] two\n", 1);
        check("task offset points at the state character",
                first != null && first.boxCharIndex == 3 && !first.checked);
        check("second task offset and state",
                second != null && second.boxCharIndex == 13 && second.checked);
        check("no task past the end", MdTasks.taskAt("- [ ] one\n", 5) == null);
        String source = "- [ ] one\n- [ ] two\n";
        MdTasks.Task task = MdTasks.taskAt(source, 1);
        String written = source.substring(0, task.boxCharIndex) + "x"
                + source.substring(task.boxCharIndex + 1);
        check("one-character write equals a full toggle", written.equals(MdTasks.toggle(source, 1, true)));
        check("one-character write touches one character",
                written.length() == source.length() && written.charAt(task.boxCharIndex) == 'x');

        // ---- 1.7.2: engines are plain script tags, assets are served as assets
        String gvPage = GfmHtml.page("```graphviz\ndigraph G { a -> b; }\n```\n", richOpts);
        check("graphviz page loads the Graphviz engine by tag",
                gvPage.contains("<script src=\"" + GfmHtml.ASSET_BASE + "viz-global.js\">"));
        check("graphviz page loads the runner",
                gvPage.contains(GfmHtml.ASSET_BASE + "diagrams.js"));
        check("the page can obtain an engine over the bridge",
                gvPage.contains("Android.loadAsset"));
        // A heading called "Vega" or "Mermaid" becomes a property of window, so the
        // page must ask the engine's own API whether it is there.
        check("engine presence is decided by the api, not the global",
                gvPage.contains("muHas") && gvPage.contains("typeof v.run==='function'")
                        && gvPage.contains("typeof v.parse==='function'"));
        check("the runner waits for the engines",
                gvPage.contains("window.MU_DIAGRAMS_AUTORUN=false;"));
        check("a drawing attempt is retried",
                gvPage.contains("muRepair") && gvPage.contains("muBegin"));
        String chemPage = GfmHtml.page("Water is $\\ce{H2O}$ and $\\pu{1.5e-3 M}$.\n",
                richOpts);
        check("chemistry loads the mhchem extension",
                chemPage.contains("src=\"" + GfmHtml.ASSET_BASE + "mhchem.min.js\""));
        check("chemistry asks the bootstrap for it",
                chemPage.contains("window.MU_MHCHEM=true;"));
        // The list of the app's own files is what decides whether a request is answered
        // from the APK or looked up next to the open document. A file the page asks for
        // and this list does not name is a file the WebView never gets.
        String[] shippedNow = {"katex.min.js", "katex.css", "mhchem.min.js", "mermaid.min.js",
                "viz-global.js", "vega.min.js", "vega-lite.min.js", "plantuml.js",
                "diagrams.js"};
        boolean everyBundled = true;
        for (int i = 0; i < shippedNow.length; i++) {
            if (!com.markultra.editor.io.RelativeImage.isBundledAsset(shippedNow[i])) {
                everyBundled = false;
            }
        }
        check("every shipped script is served from the apk", everyBundled);
        check("the bridge refuses anything else",
                !com.markultra.editor.io.RelativeImage.isBundledAsset("../secret"));
        String plainMathPage = GfmHtml.page("Just $x^2$ here.\n", richOpts);
        check("ordinary maths does not load chemistry",
                !plainMathPage.contains("src=\"" + GfmHtml.ASSET_BASE + "mhchem.min.js\"")
                        && !plainMathPage.contains("MU_MHCHEM=true"));
        String ceMath = GfmHtml.fragment("$\\ce{2H2 + O2 -> 2H2O}$", richOpts);
        check("\\ce survives into the maths element",
                ceMath.contains("data-tex=\"\\ce{2H2 + O2 -&gt; 2H2O}\""));
        // The escaping chapter of the stress document: an escaped bracket is not the
        // start of display maths, or it swallows the prose that follows it.
        String escapes = GfmHtml.fragment(
                "\\[not a link](https://example.com)\n\nSome prose.\n\n"
                        + "Literal characters: \\* \\[ \\] \\( \\)\n", richOpts);
        check("an escaped bracket is not display maths",
                escapes.indexOf("js-display-math") < 0 && escapes.contains("Some prose."));
        String multi = GfmHtml.fragment(
                "\\[ a = b\n\nA new paragraph\n\n c \\]\n", richOpts);
        check("display maths never crosses a blank line",
                multi.indexOf("js-display-math") < 0);
        check("graphviz page does not tag Vega or PlantUML in",
                !gvPage.contains("src=\"" + GfmHtml.ASSET_BASE + "vega.min.js\"")
                        && !gvPage.contains("src=\"" + GfmHtml.ASSET_BASE + "plantuml.js\""));
        String umlPage = GfmHtml.page("```plantuml\n@startuml\nA -> B\n@enduml\n```\n", richOpts);
        check("plantuml page loads Graphviz first",
                umlPage.indexOf("viz-global.js") < umlPage.indexOf("plantuml.js"));
        String vegaPage = GfmHtml.page("```vega-lite\n{\"mark\":\"bar\"}\n```\n", richOpts);
        check("vega page loads both Vega bundles",
                vegaPage.contains(GfmHtml.ASSET_BASE + "vega.min.js")
                        && vegaPage.contains(GfmHtml.ASSET_BASE + "vega-lite.min.js"));
        check("vega page does not tag PlantUML in",
                !vegaPage.contains("src=\"" + GfmHtml.ASSET_BASE + "plantuml.js\""));
        check("plain page tags no engine in",
                !plainPage.contains("src=\"" + GfmHtml.ASSET_BASE + "viz-global.js\"")
                        && !plainPage.contains("src=\"" + GfmHtml.ASSET_BASE + "vega.min.js\""));
        check("position restore is in the page", plainPage.contains("muRestore"));

        String[] shipped = {"katex.min.js", "katex.css", "mermaid.min.js", "viz-global.js",
                "vega.min.js", "vega-lite.min.js", "plantuml.js", "diagrams.js",
                "fonts/KaTeX_Main-Regular.woff2"};
        boolean allBundled = true;
        for (int i = 0; i < shipped.length; i++) {
            if (!com.markultra.editor.io.RelativeImage.isBundledAsset(shipped[i])) allBundled = false;
        }
        check("every shipped asset is recognised as an app asset", allBundled);
        check("a document file is not an app asset",
                !com.markultra.editor.io.RelativeImage.isBundledAsset("notes.md"));
        check("a script is served as JavaScript",
                "application/javascript".equals(
                        com.markultra.editor.io.RelativeImage.mimeOf("viz-global.js")));
        check("a font is served as a font",
                "font/woff2".equals(
                        com.markultra.editor.io.RelativeImage.mimeOf("fonts/a.woff2")));
        check("a stylesheet is served as CSS",
                "text/css".equals(com.markultra.editor.io.RelativeImage.mimeOf("katex.css")));

        System.out.println();
        System.out.println("checks: pass=" + passed + " fail=" + failures.size());
        for (int i = 0; i < failures.size(); i++) System.out.println("  FAIL " + failures.get(i));
        if (!failures.isEmpty()) System.exit(1);
    }

    private static GfmHtml.Options darkOpts() {
        GfmHtml.Options o = new GfmHtml.Options();
        o.dark = true;
        o.background = "#0d1117";
        return o;
    }

    /** Small shim so the stylesheet can be inspected without making it public. */
    private static final class GfmCssProxy {
        static String build(GfmHtml.Options o) {
            String page = GfmHtml.page("x", o);
            int from = page.indexOf("<style>") + 7;
            int to = page.indexOf("</style>");
            return page.substring(from, to);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failures.add(name);
        }
    }
}
