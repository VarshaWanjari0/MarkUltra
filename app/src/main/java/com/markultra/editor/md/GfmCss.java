package com.markultra.editor.md;

/**
 * The stylesheet injected into the preview page.
 *
 * <p>It is a deliberately close port of GitHub's own rendered-markdown styling
 * (the {@code .markdown-body} rules from github/github-markdown-css, which in turn
 * follows github.com): the same type ramp (2em / 1.5em / 1.25em / 1em / .875em),
 * the same hairline under h1 and h2, the same table borders and zebra rows, the
 * same rounded code blocks, the same five alert colours and the same code token
 * palette. Keeping it as a template means one source of truth for light and dark.</p>
 *
 * <p>No Android imports: this class is compiled and exercised headlessly.</p>
 */
final class GfmCss {

    private GfmCss() {
    }

    static String build(GfmHtml.Options o) {
        boolean dark = o.dark;
        String bg = o.background();
        String fg = o.foreground();
        String muted = o.muted();
        String border = o.border();
        String surface = o.surface();
        String accent = o.accent();
        String codeBg = dark ? "rgba(101,108,118,.2)" : "rgba(129,139,152,.12)";
        String zebra = surface;

        String note = dark ? "#4493f8" : "#0969da";
        String tip = dark ? "#3fb950" : "#1a7f37";
        String important = dark ? "#ab7df8" : "#8250df";
        String warning = dark ? "#d29922" : "#9a6700";
        String caution = dark ? "#f85149" : "#d1242f";

        String kw = dark ? "#ff7b72" : "#cf222e";
        String str = dark ? "#a5d6ff" : "#0a3069";
        String num = dark ? "#79c0ff" : "#0550ae";
        String com = dark ? "#9198a1" : "#59636e";
        String fn = dark ? "#d2a8ff" : "#8250df";
        String var = dark ? "#ffa657" : "#953800";
        String tag = dark ? "#7ee787" : "#116329";

        String css = TEMPLATE
                .replace("@@FONT@@", String.valueOf(Math.round(16 * o.fontScale)))
                .replace("@@BG@@", bg)
                .replace("@@FG@@", fg)
                .replace("@@MUTED@@", muted)
                .replace("@@BORDER@@", border)
                .replace("@@SURFACE@@", surface)
                .replace("@@ACCENT@@", accent)
                .replace("@@CODEBG@@", codeBg)
                .replace("@@ZEBRA@@", zebra)
                .replace("@@NOTE@@", note)
                .replace("@@TIP@@", tip)
                .replace("@@IMPORTANT@@", important)
                .replace("@@WARNING@@", warning)
                .replace("@@CAUTION@@", caution)
                .replace("@@KW@@", kw)
                .replace("@@STR@@", str)
                .replace("@@NUM@@", num)
                .replace("@@COM@@", com)
                .replace("@@FN@@", fn)
                .replace("@@VAR@@", var)
                .replace("@@TAG@@", tag)
                .replace("@@MARK@@", dark ? "rgba(187,128,9,.15)" : "#fff8c5");
        if (dark) {
            // Graphviz, PlantUML and Vega draw black ink on a transparent canvas: on a
            // dark page that is unreadable, so their frame gets the paper they expect.
            css += ".mu-diagram .mu-rendered{background:#ffffff;border-radius:8px;padding:10px}\n";
        }
        return css;
    }

    private static final String TEMPLATE =
            "*,*::before,*::after{box-sizing:border-box}\n"
            + "html{-webkit-text-size-adjust:100%;text-size-adjust:100%}\n"
            + "body{margin:0;background:@@BG@@;color:@@FG@@;"
            + "font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",\"Noto Sans\",Roboto,"
            + "Helvetica,Arial,sans-serif;font-size:@@FONT@@px;line-height:1.5;"
            + "overflow-wrap:break-word;word-wrap:break-word}\n"
            + "::selection{background:rgba(9,105,218,.28)}\n"
            + ".markdown-body{padding:16px 16px 72px;max-width:980px;margin:0 auto}\n"
            + ".markdown-body>*:first-child{margin-top:0!important}\n"
            + ".markdown-body>*:last-child{margin-bottom:0!important}\n"

            // ---------------------------------------------------------- headings
            + "h1,h2,h3,h4,h5,h6{margin:24px 0 16px;font-weight:600;line-height:1.25}\n"
            + "h1{font-size:2em;padding-bottom:.3em;border-bottom:1px solid @@BORDER@@}\n"
            + "h2{font-size:1.5em;padding-bottom:.3em;border-bottom:1px solid @@BORDER@@}\n"
            + "h3{font-size:1.25em}\n"
            + "h4{font-size:1em}\n"
            + "h5{font-size:.875em}\n"
            + "h6{font-size:.85em;color:@@MUTED@@}\n"
            + "h1:first-child,h2:first-child,h3:first-child{margin-top:0}\n"

            // ------------------------------------------------------------ text
            + "p{margin:0 0 16px}\n"
            + "a{color:@@ACCENT@@;text-decoration:none}\n"
            + "a:hover{text-decoration:underline}\n"
            + "strong,b{font-weight:600}\n"
            + "em,i{font-style:italic}\n"
            + "del,s{text-decoration:line-through}\n"
            + "small{font-size:90%}\n"
            + "sub,sup{font-size:75%;line-height:0;position:relative;vertical-align:baseline}\n"
            + "sup{top:-.5em}\n"
            + "sub{bottom:-.25em}\n"

            // ----------------------------------------------------------- lists
            + "ul,ol{margin:0 0 16px;padding-left:2em}\n"
            + "ol ol,ul ol,ol ul,ul ul{margin-bottom:0;margin-top:0}\n"
            + "li{margin-top:.25em}\n"
            + "li>p{margin-top:16px}\n"
            + "dl{padding:0}dl dt{font-weight:600;margin-top:12px}\n"
            + "dl dd{margin:0 0 16px;padding:0 16px}\n"

            // ------------------------------------------------------ task lists
            // GitHub's own task-list rules: the native control, pulled into the list
            // marker column with a negative margin and tinted with accent-color
            + "ul.contains-task-list{list-style-type:none;padding-left:2em}\n"
            + "li.task-list-item{list-style-type:none}\n"
            + "li.task-list-item .task-list-item-checkbox{margin:0 .2em .25em -1.4em;"
            + "vertical-align:middle;accent-color:@@ACCENT@@;cursor:pointer}\n"
            + "li.task-list-item>p{margin:0}\n"

            // ------------------------------------------------------ blockquote
            + "blockquote{margin:0 0 16px;padding:0 1em;color:@@MUTED@@;"
            + "border-left:.25em solid @@BORDER@@}\n"
            + "blockquote>:first-child{margin-top:0}\n"
            + "blockquote>:last-child{margin-bottom:0}\n"

            // ----------------------------------------------------------- code
            + "code,tt,samp,kbd{font-family:ui-monospace,SFMono-Regular,\"SF Mono\",Menlo,Consolas,"
            + "\"Liberation Mono\",monospace}\n"
            + "code{font-size:85%;background:@@CODEBG@@;padding:.2em .4em;border-radius:6px;"
            + "white-space:break-spaces}\n"
            + "pre{margin:0 0 16px;padding:16px;overflow:auto;font-size:85%;line-height:1.45;"
            + "background:@@SURFACE@@;border-radius:6px;tab-size:8}\n"
            + "pre code{background:transparent;padding:0;border-radius:0;font-size:100%;"
            + "white-space:pre;word-break:normal;overflow-wrap:normal}\n"
            // github.com's token classes, with its colours
            + ".pl-k{color:@@KW@@}.pl-s{color:@@STR@@}.pl-c1{color:@@NUM@@}.pl-c{color:@@COM@@}\n"
            + ".pl-en{color:@@FN@@}.pl-v{color:@@VAR@@}.pl-smi{color:@@FG@@}.pl-s1{color:@@FG@@}\n"
            + ".pl-kos,.pl-pse{color:inherit}.pl-ent{color:@@TAG@@}.pl-e{color:@@FN@@}\n"
            + ".highlight{margin:0 0 16px;background:@@SURFACE@@;border-radius:6px}\n"
            + ".highlight pre{margin:0;background:transparent;border-radius:6px}\n"
            + ".notranslate{}"

            // ----------------------------------------------------------- table
            + "table{display:block;width:max-content;max-width:100%;overflow:auto;"
            + "border-spacing:0;border-collapse:collapse;margin:0 0 16px;font-size:90%}\n"
            + "table th,table td{padding:6px 13px;border:1px solid @@BORDER@@;vertical-align:top}\n"
            + "table th{font-weight:600;background:@@SURFACE@@}\n"
            + "table tr{background:@@BG@@}\n"
            + "table tr:nth-child(2n){background:@@ZEBRA@@}\n"
            + "table th[align=center],table td[align=center]{text-align:center}\n"
            + "table th[align=right],table td[align=right]{text-align:right}\n"

            // ---------------------------------------------------------- images
            + "img{max-width:100%;box-sizing:content-box}\n"
            + "p>img{vertical-align:middle}\n"
            + ".mu-img-alt{display:inline-block;padding:3px 10px;margin:2px 0;border:1px dashed "
            + "@@BORDER@@;border-radius:6px;color:@@MUTED@@;font-size:90%;"
            + "background:@@SURFACE@@}\n"
            + ".mu-img-broken{opacity:.55}\n"

            // ------------------------------------------------------------ misc
            + "hr{height:.25em;padding:0;margin:24px 0;background:@@BORDER@@;border:0}\n"
            + "kbd{display:inline-block;padding:3px 5px;font-size:11px;line-height:10px;"
            + "vertical-align:middle;background:@@SURFACE@@;border:1px solid @@BORDER@@;"
            + "border-radius:6px;box-shadow:inset 0 -1px 0 @@BORDER@@}\n"
            + "mark{background:@@MARK@@;color:inherit;padding:.2em 0}\n"
            + "details{margin:0 0 16px}\n"
            + "summary{cursor:pointer;font-weight:600;display:list-item}\n"
            + "summary::-webkit-details-marker{color:@@MUTED@@}\n"
            + "details[open]>summary{margin-bottom:8px}\n"
            + "center{display:block;text-align:center}\n"
            + "[align=center]{text-align:center}\n"
            + "video,audio,iframe{max-width:100%}\n"

            // ---------------------------------------------------------- alerts
            + ".markdown-alert{margin:0 0 16px;padding:8px 16px;border-left:.25em solid;"
            + "border-radius:6px}\n"
            + ".markdown-alert>:first-child{margin-top:0}\n"
            + ".markdown-alert>:last-child{margin-bottom:0}\n"
            + ".markdown-alert-title{display:flex;align-items:center;margin:0 0 8px;"
            + "font-weight:600;line-height:1.25}\n"
            + ".markdown-alert-title svg{height:16px;width:16px;margin-right:8px;fill:currentColor;"
            + "flex:none}\n"
            + ".sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;"
            + "overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border-width:0}\n"
            + ".markdown-alert-note{border-left-color:@@NOTE@@}\n"
            + ".markdown-alert-note .markdown-alert-title{color:@@NOTE@@}\n"
            + ".markdown-alert-tip{border-left-color:@@TIP@@}\n"
            + ".markdown-alert-tip .markdown-alert-title{color:@@TIP@@}\n"
            + ".markdown-alert-important{border-left-color:@@IMPORTANT@@}\n"
            + ".markdown-alert-important .markdown-alert-title{color:@@IMPORTANT@@}\n"
            + ".markdown-alert-warning{border-left-color:@@WARNING@@}\n"
            + ".markdown-alert-warning .markdown-alert-title{color:@@WARNING@@}\n"
            + ".markdown-alert-caution{border-left-color:@@CAUTION@@}\n"
            + ".markdown-alert-caution .markdown-alert-title{color:@@CAUTION@@}\n"

            // ------------------------------------------------------- footnotes
            + "section.footnotes,div.footnotes{margin-top:32px;padding-top:8px;"
            + "border-top:1px solid @@BORDER@@;font-size:.75em;color:@@MUTED@@}\n"
            + "section.footnotes ol,div.footnotes ol{padding-left:1.6em;margin-bottom:0}\n"
            + "sup.footnote-ref,sup[id^=fnref]{font-size:75%;line-height:0}\n"
            + "a.footnote-ref,a.footnote-backref{color:@@ACCENT@@}\n"
            + ".footnote-backref{margin-left:.4em;text-decoration:none}\n"

            // ---------------------------------------------------- math/mermaid
            + "math-renderer{font-family:\"Latin Modern Math\",\"Cambria Math\","
            + "\"Times New Roman\",serif}\n"
            + ".js-inline-math{display:inline-block;white-space:nowrap}\n"
            + ".js-display-math{display:block;margin:16px 0;text-align:center;overflow-x:auto;"
            + "overflow-y:hidden;padding:4px 0}\n"
            + ".katex-display{display:block;margin:16px 0;overflow-x:auto;overflow-y:hidden;"
            + "text-align:center}\n"
            + ".mermaid{margin:16px 0;text-align:center;overflow-x:auto;background:transparent}\n"
            + ".mermaid svg{max-width:100%;height:auto}\n"
            + ".mu-mermaid-fail{font-size:85%;color:@@MUTED@@}\n"
            // --------------------------------------------------- graphviz/plantuml/vega
            // A diagram block keeps the shape of a code block until its engine has
            // drawn it, then the picture replaces the source in the same frame.
            + ".mu-diagram{margin:16px 0;text-align:center;overflow-x:auto}\n"
            + ".mu-diagram .mu-rendered{display:block;min-height:0}\n"
            + ".mu-diagram .mu-rendered svg{max-width:100%;height:auto}\n"
            + ".mu-diagram .mu-src{margin:0;text-align:left;font-size:82%;"
            + "opacity:.72;white-space:pre-wrap;word-break:break-word}\n"
            + ".mu-diagram[data-rendered=pending] .mu-src{opacity:.45}\n"
            + ".mu-diagram-error .mu-src{opacity:1}\n"
            + ".mu-diagram-error-note{margin:6px 0 0;font-size:82%;color:@@CAUTION@@;"
            + "text-align:left;white-space:pre-wrap}\n"
            // ------------------------------------------------------- ::: containers
            + ".mu-container{margin:16px 0;padding:10px 14px;border:1px solid @@BORDER@@;"
            + "border-left:3px solid @@ACCENT@@;border-radius:6px;background:@@SURFACE@@}\n"
            + ".mu-container-title{margin:0 0 6px;font-weight:600;color:@@FG@@}\n"
            + ".mu-container>*:last-child{margin-bottom:0}\n"
            + "details.mu-details>summary{cursor:pointer;list-style:none}\n"
            + "details.mu-details>summary::-webkit-details-marker{display:none}\n"
            + "details.mu-details>summary::before{content:'\\25B8';display:inline-block;"
            + "width:1em;color:@@MUTED@@}\n"
            + "details.mu-details[open]>summary::before{content:'\\25BE'}\n"
            + "details.mu-details[open]>summary{margin-bottom:6px}\n"

            // ------------------------------------------ MarkUltra+ (beyond GitHub)
            + "dl{margin:0 0 16px}\n"
            + "dt{font-weight:600;margin-top:8px}\n"
            + "dd{margin:0 0 4px 1.5em;color:@@MUTED@@}\n"
            + ".adm-hidden{display:none}\n"
            + ".adm-icon{display:none}\n"
            + ".adm-block{margin:0 0 16px;padding:8px 16px;border-left:.25em solid @@NOTE@@;"
            + "border-radius:4px;background:@@SURFACE@@}\n"
            + ".adm-heading{font-weight:600;color:@@NOTE@@;margin:0 0 6px;display:flex;"
            + "align-items:center;gap:8px}\n"
            + ".adm-body>:first-child{margin-top:0}\n"
            + ".adm-body>:last-child{margin-bottom:0}\n"
            + ".adm-note{border-left-color:@@NOTE@@}\n"
            + ".adm-note .adm-heading{color:@@NOTE@@}\n"
            + ".adm-tip{border-left-color:@@TIP@@}\n"
            + ".adm-tip .adm-heading{color:@@TIP@@}\n"
            + ".adm-important{border-left-color:@@IMPORTANT@@}\n"
            + ".adm-important .adm-heading{color:@@IMPORTANT@@}\n"
            + ".adm-warning{border-left-color:@@WARNING@@}\n"
            + ".adm-warning .adm-heading{color:@@WARNING@@}\n"
            + ".adm-caution{border-left-color:@@CAUTION@@}\n"
            + ".adm-caution .adm-heading{color:@@CAUTION@@}\n"
            + ".admonition{margin:0 0 16px;padding:8px 16px;border-left:.25em solid;"
            + "border-radius:4px;background:@@SURFACE@@}\n"
            + ".admonition>:first-child{margin-top:0}\n"
            + ".admonition>:last-child{margin-bottom:0}\n"
            + ".admonition-title{font-weight:600;display:flex;align-items:center;gap:8px}\n"
            + ".admonition.note{border-left-color:@@NOTE@@}\n"
            + ".admonition.note .admonition-title{color:@@NOTE@@}\n"
            + ".admonition.tip{border-left-color:@@TIP@@}\n"
            + ".admonition.tip .admonition-title{color:@@TIP@@}\n"
            + ".admonition.important{border-left-color:@@IMPORTANT@@}\n"
            + ".admonition.important .admonition-title{color:@@IMPORTANT@@}\n"
            + ".admonition.warning{border-left-color:@@WARNING@@}\n"
            + ".admonition.warning .admonition-title{color:@@WARNING@@}\n"
            + ".admonition.caution{border-left-color:@@CAUTION@@}\n"
            + ".admonition.caution .admonition-title{color:@@CAUTION@@}\n"
            + ".markdown-aside{border-left:4px solid @@BORDER@@;padding:0 16px;"
            + "color:@@MUTED@@;margin:0 0 16px}\n"
            + "ins{text-decoration:none;border-bottom:1px solid @@TIP@@}\n"
            + "sup{font-size:75%;line-height:0}\n"
            + "abbr[title]{text-decoration:underline dotted;cursor:help}\n"
            + "a.wikilink{color:@@ACCENT@@;border-bottom:1px dashed @@ACCENT@@}\n"
            + "a.wikilink-new{color:@@CAUTION@@}\n"
            + "video,audio,picture{max-width:100%;display:block;margin:0 0 16px}\n"
            + "video{height:auto;border-radius:6px;background:#000}\n"
            + "audio{width:100%}\n"
            + ".yaml-front-matter .yaml-front-matter-title{margin:0 0 4px}\n"
            // the metadata block at the top of a document: readable, clearly not body text
            + ".yaml-front-matter{font-size:85%;margin:0 0 16px;padding:10px 14px;"
            + "border:1px solid @@BORDER@@;border-radius:6px;background:@@CODEBG@@;"
            + "color:@@MUTED@@}\n"
            + ".yaml-front-matter pre{margin:0;padding:0;background:none;border:0;"
            + "white-space:pre-wrap;word-break:break-word;font-size:100%}\n"
            // ------------------------------------------------- copy button on code blocks
            // The button lives in the pre, top right, and only appears on hover or after
            // a tap: on a phone there is no hover, so it is always faintly visible.
            + "pre.mu-has-copy{position:relative}\n"
            + ".mu-copy{position:absolute;top:6px;right:6px;z-index:2;font:600 11px/1 "
            + "ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;letter-spacing:.02em;"
            + "padding:5px 9px;border-radius:6px;cursor:pointer;border:1px solid @@BORDER@@;"
            + "background:@@SURFACE@@;color:@@MUTED@@;opacity:.72;transition:opacity .15s}\n"
            + ".mu-copy:hover,.mu-copy:focus{opacity:1;color:@@FG@@}\n"
            + ".mu-copy.mu-copied{background:@@TIP@@;border-color:@@TIP@@;color:#fff;opacity:1}\n"
            + "@media (hover:hover){pre:hover .mu-copy{opacity:1}}\n"
            + "pre{padding-right:64px}\n"
            + ".mu-diff-add{color:@@TIP@@}\n"
            + ".mu-diff-del{color:@@CAUTION@@}\n"
            + ".mu-diff-meta{color:@@MUTED@@}\n"
            // a printed page has no clipboard, so the button is not part of the PDF
            + "@media print{.mu-copy{display:none!important}pre{padding-right:16px}}\n"
            // ------------------------------------------------------------ maths (KaTeX)
            // The KaTeX stylesheet owns the glyph layout; these rules only make it sit
            // correctly in a document: display maths scrolls instead of overflowing, an
            // equation that KaTeX could not parse stays visible as TeX, and the raw
            // <math-renderer> text is never shown twice.
            + "math-renderer{display:inline-block;max-width:100%;vertical-align:baseline}\n"
            + "math-renderer.js-display-math{display:block;text-align:center;margin:16px 0}\n"
            + ".katex{font-size:1.06em;text-rendering:auto}\n"
            + ".katex-display{margin:0;overflow-x:auto;overflow-y:hidden;padding:2px 0}\n"
            + ".katex-display>.katex{white-space:nowrap}\n"
            + ".katex .katex-mathml{position:absolute;clip:rect(1px,1px,1px,1px);"
            + "padding:0;border:0;height:1px;width:1px;overflow:hidden}\n"
            + ".mu-math-raw{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;"
            + "font-size:88%;background:@@CODEBG@@;color:@@CAUTION@@;padding:1px 5px;"
            + "border-radius:4px;white-space:pre-wrap;word-break:break-word}\n"
            + ".katex-error{color:@@CAUTION@@}\n"
            + "math{font-size:1.06em}\n"
            + "@media (max-width:480px){.katex{font-size:1em}"
            + ".katex-display{font-size:.95em}}\n"

            // ------------------------------------------------------- footnotes
            + "@media (max-width:480px){.markdown-body{padding:12px 14px 72px}"
            + "table{font-size:85%}pre{padding:12px}}\n";
}
