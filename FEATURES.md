# MarkUltra — Feature Catalogue

**282 features**, every one implemented in this source tree. Nothing in this list is
aspirational: each entry names the code that provides it, so it can be checked.
Features 163-202 arrived with 1.4: the full flexmark extension set behind a
GitHub/MarkUltra+ switch, and the quick-entry tools. Features 203-228 arrived with 1.5:
the file layer was rebuilt, so opening, saving, renaming, duplicating and recovering a
document behave the way a file manager expects. Features 229-252 arrived with 1.6: real
LaTeX typesetting with bundled fonts, maths authoring, and the Open button fixed.
Features 253-262 arrived with 1.7: the Pandoc constructs, the copy button, and contents
links that stay where they land. Features 263-274 arrived with 1.7.1: instant checkbox
writes, Graphviz/PlantUML/Vega rendering, `:::` containers, and CommonMark fence runs.
Features 275-282 arrived with 1.7.2: the engines load on a real WebView, and the preview
keeps the reader's place.

Rendering (1, 5, 18, 19, 25, 27, 33, 35, 37, 39 and all of section L) runs on
**flexmark-java → HTML → WebView** with a port of GitHub's own stylesheet; the parser in
`md/` is still used for outline, stats, lint, search and the plain-text/RTF export.

Legend for the source column: `MA` = `MainActivity`, `SA` = `SettingsActivity`,
`ME` = `editor/`, `MD` = `md/`, `UI` = `ui/`, `IO` = `io/`, `PR` = `prefs/`.

---

## A. Core editing & rendering (1–22)

| # | Feature | Where |
|---|---|---|
| 1 | Live GFM preview: flexmark renders GitHub's HTML, shown on GitHub's stylesheet | `MD/GfmHtml`, `MD/GfmCss`, `MA` |
| 2 | Three view modes: EDIT, SPLIT, PREVIEW | `MA` |
| 3 | Split pane with draggable-free equal weights and a 1 dp divider | `activity_main.xml` |
| 4 | Scroll position preserved across mode switches | `MA.setMode` |
| 5 | `#anchor` links jump to the matching heading (slugger ids, de-duplicated) | `MD/GfmHtml.addHeadingIds`, `MA` |
| 6 | Editor→preview scroll sync, toggleable | `MA.syncPreviewToEditor` |
| 7 | Typing latency hidden by an adaptive preview debounce (140 ms, 420 ms above 120 000 chars) | `MA.scheduleRender` |
| 8 | Status metrics computed off the typing path (cheap single-pass counter) | `MD/MdStats.quickStatusLine` |
| 9 | Full statistics available on demand (words, chars, paragraphs, read/speak time) | `MD/MdStats` |
| 10 | Live word / character / line / read-time status bar | `MA`, `view_status_bar.xml` |
| 11 | Inline syntax highlighting while typing | `ME/MarkdownHighlighter` |
| 12 | Highlighting automatically disabled above 300 000 characters (keeps input smooth) | `ME/MarkdownHighlighter` |
| 13 | Bold `**x**` | `MD/MdInlineParser` |
| 14 | Italic `*x*` and `_x_` | `MD/MdInlineParser` |
| 15 | Bold+italic `***x***` | `MD/MdInlineParser` |
| 16 | Strikethrough `~~x~~` | `MD/MdInlineParser` |
| 17 | Highlight `==x==` | `MD/MdInlineParser` |
| 18 | Inline code with GitHub's monospace stack and tinted background | `MD/GfmCss` |
| 19 | Fenced code blocks with a language class and 19 token colours | `MD/CodeHighlight`, `MD/GfmCss` |
| 20 | Indented (4-space) code blocks | `MD/MdBlockParser` |
| 21 | Hard line breaks (two trailing spaces, backslash); soft breaks behave as on GitHub | `MD/GfmHtml` |
| 22 | CommonMark flanking rules so `snake_case_word` and `2 * 3 * 4` stay literal | `MD/MdInlineParser.flanking` |

## B. Blocks (23–40)

| # | Feature | Where |
|---|---|---|
| 23 | ATX headings H1–H6 | `MD/MdBlockParser` |
| 24 | Setext headings (`===` / `---` underlines) | `MD/MdBlockParser` |
| 25 | Heading ramp 2em/1.5em/1.25em with the hairline under h1 and h2 | `MD/GfmCss` |
| 26 | Block quotes, nested to any depth | `MD/MdBlockParser` |
| 27 | Block quotes with GitHub's left rule, nesting to any depth | `MD/GfmCss` |
| 28 | Unordered lists (`-`, `*`, `+`) | `MD/MdBlockParser` |
| 29 | Ordered lists (`1.`, `1)`) with start-number preservation | `MD/MdBlockParser` |
| 30 | Nested lists at any depth, indented progressively | `MD/MdBlockParser` |
| 31 | Loose vs tight list handling (blank lines inside items) | `MD/MdBlockParser` |
| 32 | Task lists `- [ ]` / `- [x]` | `MD/MdBlockParser` |
| 33 | Tap a checkbox in the preview to toggle it in the source (JS bridge + offset-safe rewrite) | `MA.applyTaskState`, `MD/MdTasks` |
| 34 | GFM tables with `:---`, `:---:`, `---:` alignment | `MD/MdTableData` |
| 35 | Tables render as real bordered tables with zebra rows and per-column alignment | `MD/GfmCss` |
| 36 | Tap a table header cell to sort that column (numeric-aware, re-writes source) | `MA`, `MD/MdTableData.sortBy` |
| 37 | Horizontal rules, rendered as GitHub's 2 dp bar | `MD/GfmCss` |
| 38 | Raw HTML blocks: kept when allowed, sanitised by allow-list (scripts, `on*`, `javascript:` dropped) | `MD/HtmlSanitize` |
| 39 | Footnotes with GitHub markup: superscript refs, a footnotes section, back-links | `MD/GfmHtml` |
| 40 | YAML front matter detected and shown as a distinct block (not swallowed as a heading) | `MD/MdBlockParser` |

## C. Links, images, references (41–47)

| # | Feature | Where |
|---|---|---|
| 41 | Inline links `[text](url)` with optional title | `MD/MdInlineParser` |
| 42 | Reference links `[text][id]` + `[id]: url` definitions | `MD/MdInlineParser`, `MD/MdBlockParser` |
| 43 | Shortcut references `[id]` | `MD/MdInlineParser` |
| 44 | Autolinks `<https://…>` | `MD/MdInlineParser` |
| 45 | Bare URL detection `https://…` | `MD/MdInlineParser` |
| 46 | Links open in the browser; `mailto:`, `tel:` and `#anchor` handled | `MA.onLink` |
| 47 | Images render for real: http(s), `data:`, `content://`, and README-relative paths | `MD/GfmHtml`, `IO/RelativeImage` |

## D. Toolbar & insertion (48–68)

| # | Feature | Where |
|---|---|---|
| 48 | Horizontal scrolling format bar pinned above the keyboard | `view_format_bar.xml` |
| 49 | Toggle bold / italic / strike / highlight / inline code | `UI/MarkdownActions` |
| 50 | Cycle heading level (H1→H2→…→H6→paragraph) | `ME/SmartEdit.cycleHeading` |
| 51 | Bullet list, ordered list, task list toggles that work on multi-line selections | `ME/SmartEdit.toggleLinePrefix` |
| 52 | Quote toggle | `UI/MarkdownActions.quote` |
| 53 | Horizontal rule insertion | `UI/MarkdownActions.horizontalRule` |
| 54 | Code fence insertion with language picker | `UI/MarkdownActions.codeBlock` |
| 55 | Link insertion via prompt, with selected text used as the label | `UI/MarkdownActions.link` |
| 56 | Image insertion via prompt, with selected text used as the alt | `UI/MarkdownActions.image` |
| 57 | Table grid picker: drag to choose rows × columns (up to 12×12) | `UI/TableGridView`, `UI/TableInsertDialog` |
| 58 | Paste CSV / TSV / Markdown into the picker to import a whole table | `UI/TableInsertDialog` |
| 59 | Live preview of the table being inserted | `UI/TableInsertDialog` |
| 60 | Footnote insertion with auto-incrementing label | `UI/MarkdownActions.footnote` |
| 61 | Insert date / time / date-time | `UI/MarkdownActions` |
| 62 | Insert current word count | `UI/MarkdownActions.insertWordCount` |
| 63 | Insert YAML front-matter template pre-filled with title and date | `UI/MarkdownActions.insertFrontMatter` |
| 64 | Insert collapsible `<details>` block | `UI/MarkdownActions.insertDetails` |
| 65 | Insert a table of contents built from the document's own headings | `UI/MarkdownActions.insertToc` |
| 66 | Insert a starter template (title, badges, sections) | `UI/MarkdownActions.insertTemplate` |
| 67 | Duplicate current line / selected lines | `UI/MarkdownActions.duplicateLine` |
| 68 | Delete current line(s), move line(s) up or down | `UI/MarkdownActions` |

## E. Text intelligence (69–81)

| # | Feature | Where |
|---|---|---|
| 69 | Auto-close `()`, `[]`, `{}`, `""`, `''`, and backticks | `ME/SmartEdit.autoClose` |
| 70 | Wrap the selection in brackets/quotes when they are typed around it | `ME/SmartEdit` |
| 71 | Type-over closing characters instead of duplicating them | `ME/SmartEdit` |
| 72 | Auto-continue lists: pressing Enter after `- item` starts a new item | `ME/SmartInput` |
| 73 | Auto-continue task lists, preserving `[ ]` state | `ME/SmartInput` |
| 74 | Auto-continue ordered lists with the number incremented | `ME/SmartInput` |
| 75 | Auto-continue block quotes | `ME/SmartInput` |
| 76 | Enter on an empty list item removes the marker (escape the list) | `ME/SmartInput` |
| 77 | Smart indent inside fenced code blocks | `ME/SmartInput` |
| 78 | Backspace deletes a whole auto-inserted pair | `ME/SmartEdit` |
| 79 | Tab / Shift-Tab indent and outdent (configurable width) | `ME/CodeEditor`, `ME/SmartEdit` |
| 80 | Tidy document: normalises spacing, heading levels, list prefixes, fence languages | `ME/SmartEdit.tidy` |
| 81 | Trim trailing whitespace across the document (code-fence aware) | `ME/SmartEdit.stripTrailingWhitespace` |

## F. Undo, find, navigation (82–92)

| # | Feature | Where |
|---|---|---|
| 82 | Undo / redo with 600 ms coalescing, 40 snapshots and a 6 M-character memory cap | `ME/UndoManager` |
| 83 | Undo history survives rotation and configuration changes (activity handles them in place) | `ME/UndoManager`, manifest `configChanges` |
| 84 | Undo/redo buttons disable themselves when the stack is empty | `MA.updateUndoButtons` |
| 85 | Find in document with match counter | `UI/Dialogs`, `MD/MdSearch` |
| 86 | Match navigation: previous / next with wrap-around | `MA.findNext` |
| 87 | Case-sensitive, whole-word and regex find | `MD/MdSearch` |
| 88 | Replace all with a count report | `MD/MdSearch.replaceAll` |
| 89 | Outline drawer built from headings, indented by level | `MD/MdToc`, `UI/Dialogs` |
| 90 | Tap an outline entry to jump to that heading in the editor | `MA` |
| 91 | Line numbers gutter (toggleable) that stays aligned during scrolling | `ME/CodeEditor` |
| 92 | Jump-to-line prompt | `UI/Dialogs.goToLine` |

## G. Files (93–104)

| # | Feature | Where |
|---|---|---|
| 93 | Open `.md` / `.txt` through the Storage Access Framework | `IO/FileController.openIntent` |
| 94 | Create / Save / Save-as through SAF (no storage permission needed) | `IO/FileController` |
| 95 | Persistable URI permission taken on open, so writes keep working | `IO/FileController.takePersistablePermission` |
| 96 | Automatic write-back after edits, debounced by a configurable delay (0.5–5 s) | `MA.scheduleAutosave` |
| 97 | Autosave pauses when no file is open and resumes when one is | `MA` |
| 98 | External-change detection via last-modified timestamp, with Overwrite / Reload / Keep-as-copy dialog | `IO/FileController.changedSince`, `MA.askOverwriteExternal` |
| 99 | UTF-8 with BOM detection and preservation, CRLF normalisation | `IO/FileController` |
| 100 | Recent files list with per-entry removal and clear-all | `IO/FileController`, `UI/Dialogs` |
| 101 | Crash-safe draft snapshot on every pause and on a 1.5 s idle timer | `IO/FileController.saveDraft` |
| 102 | Draft restored on cold start, including which file it came from | `MA` |
| 103 | Unsaved-changes guard on back, open and new | `MA.confirmExit` |
| 104 | Share as text, and share as a real `.md` file through FileProvider | `MA.shareFile`, `xml/file_paths.xml` |

## H. Export (105–111)

| # | Feature | Where |
|---|---|---|
| 105 | Export to HTML (styled, self-contained, theme-aware) | `MD/MdConvert` |
| 106 | Export to plain text (markers stripped by the parser, not by regex) | `MD/MdConvert` |
| 107 | Export to RTF with real bold/italic/strike/monospace runs | `MD/MdConvert` |
| 108 | Copy the whole document to the clipboard | `UI/UiUtil` |
| 109 | Copy the rendered preview where possible | `MA` |
| 110 | Export never overwrites silently — it always uses the SAF create dialog | `MA.export` |
| 111 | HTML export embeds the current accent colour and honours darker/dark themes | `MD/MdConvert` |

## I. Appearance & themes (112–126)

| # | Feature | Where |
|---|---|---|
| 112 | Light, dark, and follow-system modes | `PR/ThemeController`, `SA` |
| 113 | True-black AMOLED mode | `PR/ThemeController` |
| 114 | 12 accent palettes | `PR/ThemeController.PALETTES` |
| 115 | Accent swatch picker with a selection ring | `UI/ColorSwatch`, `activity_settings.xml` |
| 116 | Palette drives editor, preview, gutter, dialogs and system bars consistently | `PR/ThemeController.applySystemBars` |
| 117 | Font size 12–32 sp with live preview | `SA` |
| 118 | Line spacing 1.0–2.0 | `SA` |
| 119 | Three type families: sans, serif, monospace | `SA`, `PR/ThemeController.Colors.typeface` |
| 120 | Monospace option for code spans independent of the body font | `MD/GfmCss` |
| 121 | Soft wrap toggle (off = horizontal scroll, for long tables) | `ME/CodeEditor.setSoftWrapEnabled` |
| 122 | Typewriter mode keeps the caret vertically centred while typing | `ME/CodeEditor` |
| 123 | Current-line highlight in the editor | `ME/CodeEditor` |
| 124 | Smart typography: curly quotes, en/em dashes, ellipsis (applied to the source before parsing) | `MD/GfmHtml`, `PR/Settings` |
| 125 | Focus mode hides every bar for distraction-free writing | `MA` |
| 126 | Settings changes apply on return without losing the document (revision check) | `PR/Settings.revision`, `MA.onResume` |

## J. Reliability, accessibility, politeness (127–136)

| # | Feature | Where |
|---|---|---|
| 127 | Zero permissions in the manifest — no network, storage, or contacts | `AndroidManifest.xml` |
| 128 | Renders on a single background thread with generation-based cancellation, so stale renders are dropped | `MA.renderPreview` |
| 129 | Hard limits everywhere (inline depth 12, 6 000-char scans, 20 000-line block guard) so no input can hang the UI | `md/*` |
| 130 | 48 dp minimum touch targets, content descriptions on every icon | `dimens.xml`, layouts |
| 131 | Full RTL support via `supportsRtl` and start/end attributes | manifest, layouts |
| 132 | Configuration-change handling keeps the caret and mode | `MA.onSaveInstanceState` |
| 133 | Keep-screen-on while editing (toggleable) | `MA.applyKeepScreenOn` |
| 134 | Haptic feedback on destructive actions (toggleable) | `UI/UiUtil` |
| 135 | Document checks: unclosed fences, empty links, duplicate headings, trailing whitespace, tabs, long lines | `MD/MdLint` |
| 136 | Onboarding cheat sheet shown on first launch, written in Markdown and rendered by the app's own engine | `MA.checkOnboarding`, `Dialogs.cheatSheet` |

## K. GitHub parity added in 1.1 (137–146)

| # | Feature | Where |
|---|---|---|
| 137 | 190 emoji shortcodes (`:rocket:`, `:+1:`, `:white_check_mark:`), unknown codes left literal | `MD/MdEmoji`, `MD/MdInlineParser` |
| 138 | GitHub alerts: `> [!NOTE]`, `TIP`, `IMPORTANT`, `WARNING`, `CAUTION` with GitHub icon, colour and bar | `MD/GfmHtml` |
| 139 | Fenced-code colouring for keywords, strings, numbers, comments, functions and types, with per-language comment styles | `MD/CodeHighlight` |
| 140 | `www.` autolinks (prefix supplied automatically) alongside bare-URL and `<angle>` autolinks | `MD/MdInlineParser` |
| 141 | Inline HTML formatting: `<b>`, `<strong>`, `<i>`, `<em>`, `<del>`, `<code>`, `<mark>`, `<kbd>` styled, unknown tags dropped, `<br>` becomes a break | `MD/MdInlineParser.htmlTag` |
| 142 | Raw HTML sanitised like GitHub: allow-list of tags and attributes, scripts and comments removed | `MD/HtmlSanitize` |
| 143 | Tight lists stay tight; a blank line between items makes a loose list, exactly as GitHub renders it | `MD/GfmHtml` |
| 144 | Soft line breaks and both hard-break forms (`two spaces`, `backslash`) render as real line breaks | `MD/MdInlineParser` |
| 145 | Angle autolinks (`<https://x>`, `<mail@host>`) are never mistaken for HTML blocks | `MD/MdBlockParser.HTML_START` |
| 146 | Keystroke safety: the document-change path, auto-pairing and undo history all catch `RuntimeException`, so a library fault can never crash typing | `MA.onDocumentChanged`, `ME/SmartInput`, `ME/UndoManager` |

## L. Real README rendering (147-156)

| # | Feature | Where |
|---|---|---|
| 147 | Tables render as real HTML tables: header tint, per-column alignment, zebra rows, borders | `MD/GfmCss` |
| 148 | Table cells keep inline formatting (bold, code, links, emoji) | `MD/GfmHtml` |
| 149 | Wide-glyph safe: browser layout handles emoji and CJK, so columns cannot drift | `MD/GfmCss` |
| 150 | Wide tables stay readable in a horizontally scrollable container instead of clipped columns | `MD/GfmCss` |
| 151 | Inline and display maths typeset by KaTeX (`$ ... $`, `$$ ... $$`, math fences) | `MD/GfmHtml` |
| 152 | Mermaid diagrams drawn by Mermaid 10, loaded only when a document uses one | `MD/GfmHtml` |
| 153 | details/summary rendered as a disclosure bar with an indented body | MD/MdBlockParser.parseDetails |
| 154 | Real images: scaled to the pane, badge rows stay inline, broken images fall back to alt text | `MD/GfmHtml`, `MD/GfmCss` |
| 155 | Images load over http(s), `data:`, `content://` and README-relative paths, with an on/off switch | `MA.setupPreview`, `IO/RelativeImage` |
| 156 | align=center / center blocks are centred, the way a README footer expects | `MD/GfmCss` |

---

## M. The rendering pipeline (157-162)

| # | Feature | Where |
|---|---|---|
| 157 | GFM parsed by flexmark-java, the reference CommonMark/GFM engine, not a hand-rolled subset | `MD/GfmHtml` |
| 158 | The stylesheet is a port of github-markdown-css, so headings, tables, code, alerts and footnotes match github.com | `MD/GfmCss` |
| 159 | Rendering runs on a worker thread with generation-based cancellation, so a slow document never blocks the keyboard | `MA.renderPreview` |
| 160 | KaTeX and Mermaid are bundled and injected only when the document needs them | `MD/GfmHtml.page`, `assets/` |
| 161 | A three-method JavaScript bridge (checkbox, table sort, scroll) and nothing else; file access limited to the document's folder | `MA.PreviewBridge`, `MA.openRelativeResource` |
| 162 | Every promise of the page is checked headlessly: the self test covers both render modes, the page assembly and the file codec | `tools/GfmSelfTest.java` |


## N. MarkUltra+ syntax - more than GitHub (163-176)

Every extension below is compiled in, runs only when the MarkUltra+ switch is on, and is
verified by the self test in both states. With the switch off the output is unchanged.

| # | Feature | Where |
|---|---|---|
| 163 | **GitHub / MarkUltra+ switch**, remembered across launches, on the first menu page | `PR/Settings.richMarkdown`, `MA` |
| 164 | Definition lists (`Term` / `: meaning`) | `MD/GfmHtml.richExtensions` |
| 165 | Admonitions (`!!! note "Title"`), styled as alerts | `MD/GfmHtml`, `MD/GfmCss .adm-*` |
| 166 | Wiki links `[[Note]]`, resolved to relative links and styled | `MD/GfmHtml.richWiki` |
| 167 | Attributes `{#id .class}` on headings, blocks and images | `MD/GfmHtml.richExtensions` |
| 168 | Abbreviations `*[HTML]: HyperText Markup Language` | `MD/GfmHtml.richExtensions` |
| 169 | Superscript `x^2^` and insert `++text++` | `MD/GfmHtml.richExtensions` |
| 170 | Asides (pull quotes) and YAML front matter that is parsed, not printed | `MD/GfmHtml.richExtensions` |
| 171 | `@mention` and `#123` links that resolve to GitHub | `MD/GfmHtml.richExtensions` |
| 172 | Real `<video>` / `<audio>` players for media URLs | `MD/GfmHtml.richMedia` |
| 173 | Resizable images with flexmark's size syntax, cleaned of HTML5 end tags | `MD/GfmHtml.postProcess` |
| 174 | Rich CSS for every MarkUltra+ block in both themes | `MD/GfmCss` |
| 175 | Sanitiser lets the new elements (`dl`, `dt`, `dd`, `abbr`, `video`, `audio`) through and nothing else | `MD/HtmlSanitize` |
| 176 | Both modes measured against github.com; the extra syntax never leaks into GitHub mode | `tools/GfmSelfTest.java` |

## O. Quick entry and time savers (177-202)

| # | Feature | Where |
|---|---|---|
| 177 | **Command palette** - every command in one searchable list | `UI/QuickPanel`, `MA.showPalette` |
| 178 | **Slash menu** - typing `/` at the start of a line opens the block inserter | `MA.setupEditor`, `MA.openSlashMenu` |
| 179 | **Block inserter** - 25 blocks: headings, lists, alerts, table, mermaid, maths, details, footnote, TOC, definition list, admonition, wiki link | `MA.blockItems` |
| 180 | **Template gallery** - README, changelog, meeting notes, ADR, issue, blog post, checklist, documentation page | `MD/Templates`, `MA.showTemplates` |
| 181 | Every template lands the caret where writing starts, not at offset 0 | `Templates.CURSOR`, `MA.applyTemplate` |
| 182 | Replacing a document with a template asks first | `MA.applyTemplate` |
| 183 | **Paste HTML as Markdown** - web pages, Word, Notion, automatically on paste | `MD/PasteConvert.htmlToMarkdown`, `ME/CodeEditor.setPasteInterceptor` |
| 184 | Paste conversion can be switched off, and falls back to plain text if it fails | `PR/Settings.pasteRich` |
| 185 | **Paste CSV / TSV as a table** - sniffed delimiter, quoted fields, numeric columns right-aligned | `MD/PasteConvert.tableToMarkdown` |
| 186 | 29 **text tools** that work on the selection or the whole document: case, sort, dedupe, numbering, wrapping, URL/Base64/JSON/HTML | `MD/TextTools`, `MA.showTextTools` |
| 187 | Text tools never throw: a bad operation returns the text unchanged | `TextTools.apply` |
| 188 | **Export PDF** through the system print service, from the live preview | `MA.exportPdf` |
| 189 | **Read aloud** with the platform speech engine, and a stop command | `MA.toggleReadAloud` |
| 190 | Document tools - tidy, strip whitespace, sort, duplicate and move lines - all in the palette too | `MA.commandItems` |
| 191 | Navigation tools - find, go to line, outline, checks, statistics - in the palette | `MA.commandItems` |
| 192 | View commands - edit/split/preview, focus, wrap, theme - in the palette | `MA.commandItems` |
| 193 | Palette searches titles and hints, so "pdf" finds the export and "h2" the heading | `UI/QuickPanel.RowAdapter.filter` |
| 194 | Palette keeps the keyboard up and the list filtered live | `UI/QuickPanel` |
| 195 | Every palette row is 48 dp and carries a hint line | `UI/QuickPanel.RowAdapter` |
| 196 | Slash menu deletes its own `/` before inserting, so no stray character is left behind | `MA.openSlashMenu` |
| 197 | Slash menu only fires at the start of an empty line, never inside a sentence | `MA.setupEditor` |
| 198 | Inserted blocks move the view to split so the result is visible | `MA.insertBlock` |
| 199 | Templates, text tools and blocks all reuse one tested list component | `UI/QuickPanel` |
| 200 | New commands are wired to the existing menu, so nothing is unreachable | `res/menu/menu_main.xml` |
| 201 | Text tools, paste conversion and templates are pure Java, covered by the JVM self test | `tools/GfmSelfTest.java` |
| 202 | Self checks on every release: parity in both modes, text tools, paste conversions, templates, page assembly | `tools/GfmSelfTest.java` |

## P. The file layer, rebuilt in 1.5 (203-228)

| # | Feature | Where |
|---|---|---|
| 203 | Opened files get read **and** write access with a persistable grant, so Save works now and after a reboot | `IO/FileController.openIntent`, `MA.savePersistable` |
| 204 | Every read and every write runs on one IO thread; storage is never touched on the UI thread | `MA.submit`, `MA.ioExecutor` |
| 205 | Reads decode UTF-8, UTF-16LE and UTF-16BE, remember the BOM, and normalise line endings for editing | `IO/TextFile.decode` |
| 206 | Saves write the file back in its own charset, BOM and line endings - CRLF stays CRLF | `IO/TextFile.encode`, `MA.documentEol` |
| 207 | Files above 16 MB are refused with a plain message instead of an out-of-memory crash | `IO/TextFile.MAX_BYTES`, `IO/FileController.TooLargeException` |
| 208 | Files that are not text are refused by a NUL scan, instead of filling the editor with mojibake | `IO/TextFile.looksBinary`, `IO/FileController.NotTextException` |
| 209 | Saves try `wt`, `w`, `rwt` and a plain stream in turn, so cloud providers that reject one mode still save | `IO/FileController.writeDocument` |
| 210 | Read chunks are 64 KB, not 8 KB, and the whole read happens off the UI thread | `IO/FileController.readBytes` |
| 211 | Every open failure says what happened: too large, not text, permission expired, or the provider's own message | `MA.reportOpenFailure` |
| 212 | A recent file that will not open offers to remove itself from the recent list | `MA.reportOpenFailure` |
| 213 | Every save failure names the file and the reason, and offers **Save as** or **Keep draft** | `MA.reportSaveFailure` |
| 214 | Opening another file asks before discarding unsaved work: Save, Discard or Cancel | `MA.requestOpenDocument` |
| 215 | New document and every template ask before replacing unsaved text | `MA.newDocument`, `MA.runWithDiscardGuard` |
| 216 | Work queued behind a save runs only after the write finished, so a template can never overwrite the old file with new text | `MA.afterSaveAction`, `MA.runAfterSave` |
| 217 | A save that finishes while typing continues leaves the document marked unsaved | `MA.postSave` |
| 218 | **Duplicate** writes a copy and keeps editing the original | `MA.duplicateDocument` |
| 219 | **Rename** runs on the IO thread, survives providers that refuse, and offers Save as instead of failing silently | `MA.renameDocument` |
| 220 | **File details**: name, size, lines, modified time, line endings, encoding and BOM | `MA.showFileDetails`, `IO/TextFile.humanSize` |
| 221 | A **File actions** sheet groups rename, duplicate, details, save as and share file | `MA.showFileActions`, `UI/QuickPanel` |
| 222 | Draft recovery is one preference commit, skipped when the text did not change, capped at 2 M characters | `IO/FileController.saveDraft`, `MA.saveDraftNow` |
| 223 | The last file reopens on a cold start when there is no draft to restore | `MA.reopenLastFile` |
| 224 | Share targets: VIEW, EDIT, SEND, SEND_MULTIPLE, PROCESS_TEXT and shared text, all behind the unsaved-work guard | `MA.handleExternalIntent`, `MA.openSharedText` |
| 225 | Intent filters fixed: type filters and scheme filters are separate, so file managers that send no MIME type can open into the app | `AndroidManifest.xml` |
| 226 | The status line counts words on a `CharSequence`, so no document copy happens per keystroke | `MD/MdStats.quickStatusLine`, `MA.updateStatus` |
| 227 | HTML, RTF, TXT and file-share exports are built on the IO thread and stay out of the typing path | `MA.export`, `MA.shareFile` |
| 228 | The title line says what is happening - Opening…, Saving…, Not saved - and quitting writes the file before the activity finishes | `MA.updateTitleState`, `MA.confirmExit` |

## Q. Maths and document opening, 1.6 (229-252)

| # | Feature | Where |
|---|---|---|
| 229 | Opening a document survives the activity being recreated: the read and its result live in a `ViewModel`, not in the screen | `IO/DocumentLoader` |
| 230 | A result that arrives while no screen is attached waits for the next one instead of being dropped | `IO/DocumentLoader.deliver` |
| 231 | A second open supersedes the running one (generation counter) instead of being refused with a toast | `IO/DocumentLoader.load`, `MA.openUri` |
| 232 | A picker that returns OK without an address says so, and the address is read from `ClipData` too | `MA.pickedUri`, `MA.registerLaunchers` |
| 233 | Devices with no `ACTION_OPEN_DOCUMENT` provider fall back to `ACTION_GET_CONTENT` | `MA.launchOpenPicker` |
| 234 | Reopening the last file never races a document the picker is about to deliver | `MA.reopenLastFile` |
| 235 | KaTeX's real stylesheet ships in the APK and is loaded only when the document contains maths | `assets/katex.css`, `MD/GfmHtml.page` |
| 236 | All 20 KaTeX WOFF2 faces ship too: maths typesets offline with no web fonts | `assets/fonts/` |
| 237 | Maths is rendered as KaTeX HTML for the screen with MathML underneath for copy, search and screen readers | `MD/GfmHtml.BOOTSTRAP` |
| 238 | Thirteen TeX macros (`\R`, `\N`, `\abs`, `\norm`, `\dd`, `\eval`, `\bra`, `\ket`, …) generated from a Java table | `MD/GfmHtml.MACROS`, `macroScript` |
| 239 | An equation that fails to parse stays visible as styled TeX with its error, never a blank box | `MD/GfmHtml.BOOTSTRAP`, `GfmCss` |
| 240 | Display maths is centred and scrolls horizontally instead of overflowing the page | `MD/GfmCss` |
| 241 | **Equation library**: 16 display and 10 inline snippets, all real TeX, insertable in one tap | `MD/Equations`, `MA.showEquationSheet` |
| 242 | **Live TeX field**: type it, see it, with the same engine, stylesheet, fonts and error styling as the preview | `UI/MathPreview` |
| 243 | The live field toggles between inline and display without losing the text | `UI/MathPreview.show` |
| 244 | **Equation preview at the cursor** reads the `$…$` / `$$…$$` span around the caret, typesets it and writes edits back into that span | `MA.peekMathAtCaret` |
| 245 | Maths span scanner pairs delimiters in order over a bounded window: `price $5` never opens a span, escaped dollars never count, `$$` pairs only with `$$` | `MA.mathSpanAt` |
| 246 | **Wrap in maths**: `$selection$`, and unwraps a selection that is already wrapped | `MA.wrapInMath` |
| 247 | Display equations are inserted as fenced blocks with blank lines around them, inline maths wraps the selection | `MA.insertMath` |
| 248 | **Insert image from device**: the file is copied next to the document over SAF so `![](diagram.png)` is a real relative link | `MA.insertImageFromDevice`, `IO/FileController.copyNextTo` |
| 249 | When the folder refuses the copy, the picked address is inserted and the preview streams the bytes itself | `MA.openContentImage`, `MA.handlePickedImage` |
| 250 | HTML export is rendered by the preview pipeline (flexmark + GitHub stylesheet), and keeps KaTeX markup with a CDN engine | `MD/MdConvert.toHtmlDocument` |
| 251 | `GfmHtml.Options` is theme-aware: `dark = true` is enough, no palette required | `MD/GfmHtml.Options` |
| 252 | 191 headless checks, including the bundled KaTeX stylesheet: 20 well-formed `@font-face` blocks whose font files all exist | `tools/GfmSelfTest.java` |

## N. 1.7 — other dialects, and the contents pane (253–262)

| # | Feature | Where |
|---|---|---|
| 253 | **Copy button on every code block**: copies the exact source through the app's clipboard helper, with a `Copied` flash, a toast and a haptic tick; absent from print and PDF | `MD/GfmHtml` (bootstrap), `MA.copyCode`, `MD/GfmCss` |
| 254 | **Contents links stay put**: a `#anchor` link is intercepted before the `WebView` can navigate and resolved in the page, with a scroll lock on both sides so the two-way sync cannot bounce it back | `MA.shouldOverrideUrlLoading`, `MA.jumpToAnchor`, `MD/GfmHtml.muAnchor` |
| 255 | Pandoc **raw TeX spans** `` `\sum`{=tex} `` become inline maths; a following `\_{...}` is pulled into the same expression | `MD/PandocCompat.markTexSpans`, `finishTexSpans` |
| 256 | Pandoc **raw HTML spans and blocks** (`` `<kbd>`{=html} ``, ```` ```{=html} ````) pass through the sanitiser like any other raw HTML | `MD/PandocCompat.rawBlocks` |
| 257 | **`\[ … \]` and `\( … \)` maths**, with Pandoc's `\_`/`\^`/`\$` escapes undone and `\\` kept as a TeX break | `MD/PandocCompat.latexDelimiters` |
| 258 | Maths is lifted out of the source before parsing and put back byte for byte, so `x^{n-1}` cannot become a superscript and no `<br>` lands inside an equation | `MD/GfmHtml.MathSink` |
| 259 | **Pandoc tables without pipes** - simple, multiline and grid - converted to real tables, wrapped cells re-joined, alignment inferred from the column geometry | `MD/PandocCompat.simpleTables` |
| 260 | **YAML front matter** shown once as a metadata card, and never mistaken for a horizontal rule | `MD/PandocCompat.frontMatter`, `MD/GfmCss` |
| 261 | **Definition lists, superscript and subscript in GitHub mode**, plus `diff` fences coloured line by line and `regex` fences left literal | `MD/GfmHtml`, `MD/PandocCompat.subscripts`, `MD/CodeHighlight.diff` |
| 262 | 236 headless checks: every 1.7 construct, the sanitised raw-HTML block, the 18 contents targets, and the copy/anchor scripts | `tools/GfmSelfTest.java` |

## O. 1.7.1 — instant checkboxes and three more diagram languages (263–274)

| # | Feature | Where |
|---|---|---|
| 263 | **A checkbox tapped in the preview is written back as one character**, at the offset `MdTasks` already knows: no full-document replace, no re-layout of the other 2 900 lines, no 60 KB undo snapshot | `MA.applyTaskState`, `MD/MdTasks.taskAt` |
| 264 | **Graphviz fences** (`graphviz`, `dot`, `gv`, `graph`) drawn by Graphviz compiled to WebAssembly, offline | `assets/viz-global.js`, `assets/diagrams.js` |
| 265 | **PlantUML fences** (`plantuml`, `puml`, `uml`) drawn by the real PlantUML engine compiled to JavaScript; a block pasted without `@startuml` is wrapped for you | `assets/plantuml.js`, `assets/diagrams.js` |
| 266 | **Vega and Vega-Lite fences** drawn as SVG, with the chart widened to the column it has to fill | `assets/vega*.js`, `assets/diagrams.js` |
| 267 | Diagram engines load **only when the document contains that language**, once per page, and a failure leaves the source visible with the reason | `assets/diagrams.js`, `MD/GfmHtml.page` |
| 268 | **`::: note` / `::: warning` / `::: tip` / `::: important` / `::: caution`** containers render as GitHub alerts, with an optional custom title | `MD/GfmHtml.Preprocess` |
| 269 | **`::: details Title`** containers render as a collapsible `<details>` with a real disclosure triangle | `MD/GfmHtml.Preprocess`, `MD/GfmCss` |
| 270 | **Fences opened with four or more backticks** now close only on a run at least as long as the opener, so nesting examples no longer desynchronise the rest of the document | `MD/GfmHtml.Preprocess.isFenceClose`, `MD/PandocCompat.closesFence` |
| 271 | Subscripts, tables and maths keep working **after a nested-fence example**, because the Pandoc pass and the renderer now agree on where a code block ends | `MD/PandocCompat.codeRanges` |
| 272 | 282 headless checks still pass, and the 63 KB stress document renders 16 Mermaid + 3 Graphviz/PlantUML/Vega diagrams, 26 tables, 33 KaTeX expressions and 4 containers with **zero page errors** | `tools/GfmSelfTest.java`, browser audit |
| 273 | **Mermaid diagrams are drawn one at a time**; drawing sixteen at once made about half of them fail with mermaid's generic *Syntax error in text*, because concurrent layout shares one text-measuring element | `MD/GfmHtml` (bootstrap) |
| 274 | **The copy button never lands inside a Mermaid block**: the pass that decorates `<pre>` elements now skips diagrams, which used to inject the word `Copy` into a block mermaid was still reading | `MD/GfmHtml` (bootstrap) |

## P. 1.7.2 — engines that load on a phone, and a preview that stays put (275–282)

| # | Feature | Where |
|---|---|---|
| 275 | **App assets are served by the app**: a `file:///android_asset/` request for a script, stylesheet or font is answered from the AssetManager with the right MIME type, instead of being mistaken for a file next to the open document | `MA.serveBundledAsset`, `IO/RelativeImage.isBundledAsset` |
| 276 | `mimeOf` knows `.js`, `.wasm`, `.woff`, `.woff2`, `.ttf`, `.html` and `.txt`: a script served as `application/octet-stream` is a script the WebView refuses to run | `IO/RelativeImage.mimeOf` |
| 277 | **Engine scripts are static tags** in the page, emitted per language: Graphviz before PlantUML, Vega before Vega-Lite, only when the document has such a block | `MD/GfmHtml.page` |
| 278 | `diagrams.js` only draws: no runtime loader, no fetch, no waiting on a script that might never arrive | `assets/diagrams.js` |
| 279 | **Every diagram bundle is transpiled to the Android 10 baseline** (WebView 74): Mermaid 10.9.1, Viz 3.31, Vega 5, Vega-Lite 5 and PlantUML 1.2026.9 all run on a WebView that cannot parse ES2020+ | `assets/*.js`, Babel preset-env |
| 280 | **A failed diagram says why**: "the Graphviz engine did not load" and "the diagram runner did not start" are different messages, and neither one is a blank block | `assets/diagrams.js`, `MD/GfmHtml` bootstrap |
| 281 | **Coming back from a link keeps your place**: an unchanged document is not re-rendered on resume, the scroll fraction is re-applied while the page settles (images, diagrams), and the anchor guard no longer swallows the restore | `MA.onResume`, `MA.needsRender`, `MD/GfmHtml.muRestore` |
| 282 | **The reading position survives rotation and theme switches**: it is saved with the instance state, and a newly opened document always starts at its own top | `MA.onSaveInstanceState`, `MA.applyDocument` |

## Q. 1.7.3 — engines that cannot be fooled, a chemistry extension, and typing that keeps up (283–292)

| # | Feature | Where |
|---|---|---|
| 283 | **An engine is probed by its API, not by its name**: an element with `id="mermaid"` or `id="vega"` (any heading called *Mermaid* or *Vega*) becomes a `window` property and made the page believe the engine was loaded | `MD/GfmHtml` bootstrap (`muHas`) |
| 284 | **A refused script arrives over the JavaScript bridge**: the page asks the app for the file text and runs it inline, which no URL policy can block; every engine supports both routes | `MA.loadAsset`, `MD/GfmHtml` (`muEngine`) |
| 285 | **Dependent engines are re-bound** after a late arrival: Vega-Lite is loaded again on top of a bridged Vega, PlantUML on top of a bridged Graphviz | `MD/GfmHtml` (`muRepair`) |
| 286 | **A watchdog draws at six seconds** with whatever engines are present, so a stalled load degrades to some diagrams instead of a blank page | `MD/GfmHtml` (`muBegin`) |
| 287 | **The diagram runner waits for the page**, and a block that failed before its engine arrived is retried and its stale note removed | `assets/diagrams.js`, `MD/GfmHtml` |
| 288 | **Chemistry**: KaTeX's mhchem extension, so `\ce{2H2 + O2 -> 2H2O}` and `\pu{1.5e-3 M}` typeset; loaded only for documents that use them | `MD/GfmHtml.page`, `assets/mhchem.min.js` |
| 289 | **Incremental highlighting**: a keystroke repaints only the touched lines (and the fenced block around them), not the document - 2.8 ms instead of 76 ms at 30 KB, 8.4 ms instead of 1.4 s at 120 KB | `ED/MarkdownHighlighter` |
| 290 | **A line index for the gutter and the cursor label**, spliced on every edit instead of two full scans per keystroke | `ED/LineMap`, `ED/CodeEditor`, `MA.updateCursorPosition` |
| 291 | **Undo decides before it copies**, so a megabyte document is not duplicated per keystroke only to be discarded | `ED/UndoManager` |
| 292 | **Display maths never crosses a paragraph break**, so an escaped `\[` in a document's escaping chapter stops swallowing the prose after it (33 of 33 maths expressions in the stress document now typeset) | `MD/PandocCompat.convertDelimited` |
| 293 | **Every engine loads on a real WebView**, because the page's scripts, stylesheet and fonts are asked for from `https://appassets.androidplatform.net/assets/…` and answered from the APK by `shouldInterceptRequest` - never by `file://`, which a WebView may refuse for a page built with `loadDataWithBaseURL` | `MD/GfmHtml.ASSET_BASE`, `MainActivity`, `ui/AssetClient` |
| 294 | **A heading called "Mermaid" can no longer impersonate the engine**: the head claims every engine name with an accessor before any engine loads, so the engine's own assignment is captured even when `window.mermaid` is an `<h2>` | `MD/GfmHtml.CLAIM_SCRIPT` |
| 295 | **A failed engine reports the real reason** - the exception it threw, or that the app returned no bytes - in the block that needed it, instead of "did not load" | `MD/GfmHtml` runner, `assets/diagrams.js` |
| 296 | **An engine that arrives late is still used**: the page redraws when the set of loaded engines changes while a diagram or an equation is still waiting | `MD/GfmHtml` healer |
| 297 | **Chemistry typesets inline and in display**: `\ce{2H2 + O2 -> 2H2O}`, `\ce{CH4 + 2O2 -> CO2 + 2H2O}`, `\pu{1.5e-3 M}`, with the mhchem extension now included in the files the bridge may deliver | `MD/GfmHtml`, `assets/mhchem.min.js` |
| 298 | **KaTeX's fonts load from the same origin as its stylesheet** (`url(fonts/…)`), so maths keeps its real typeface without a `file://` request | `assets/katex.css` |
| 299 | **The math preview dialog and the cheat sheet use the same asset origin**, so the dialog typesets exactly like the preview | `ui/MathPreview`, `ui/Dialogs`, `ui/AssetClient` |
| 300 | **The engines no longer need a new phone**: `structuredClone`, `Object.hasOwn`, `replaceAll`, `flatMap`, `allSettled`, `WeakRef` and nine more are polyfilled in the page's head, in ES5, only when missing - so Mermaid, Vega and Vega-Lite run on a WebView as old as Chrome 84 | `MD/GfmHtml.COMPAT_JS` |
| 301 | **Assets are served as UTF-8**, so a script cannot be decoded as Latin-1 and fail to parse | `MainActivity.serveBundledAsset`, `ui/AssetClient` |
| 302 | Verified on Chromium 84, 90 and 129: every engine, with tags working, with tags blocked, and with headings shadowing the engine names - Mermaid, Vega, Vega-Lite, Graphviz, PlantUML, KaTeX and mhchem all render | browser harness |

---

### Nice extras that did not fit the list

* Every colour is defined in `values/colors.xml` + `values-night/colors.xml`, so the
  system dark mode works even before the app's own theme setting is touched.
* All 26 icons are vector drawables — the entire icon set costs **31 KB** and scales to
  any density.
* The app contains no `Thread.sleep`, no `System.gc`, no `synchronized` on the UI thread,
  and no unbounded loops over the document.
