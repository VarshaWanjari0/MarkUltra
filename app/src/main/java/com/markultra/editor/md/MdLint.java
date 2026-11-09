package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast, heuristic Markdown linter. Powers the "Structure" panel: heading outline,
 * link targets and the issues that most often break rendering on GitHub.
 */
public final class MdLint {

    public static final int INFO = 0;
    public static final int WARNING = 1;
    public static final int ERROR = 2;

    public static final class Issue {
        public final int line;
        public final int severity;
        public final String message;

        Issue(int line, int severity, String message) {
            this.line = line;
            this.severity = severity;
            this.message = message;
        }

        @Override
        public String toString() {
            return "L" + line + " " + message;
        }
    }

    public static final class Report {
        public final List<Issue> issues = new ArrayList<Issue>();
        public final List<MdBlock> headings = new ArrayList<MdBlock>();
        public final List<String> linkTargets = new ArrayList<String>();
        public final Map<String, Integer> anchorUse = new HashMap<String, Integer>();

        public int errors() {
            int n = 0;
            for (int i = 0; i < issues.size(); i++) if (issues.get(i).severity == ERROR) n++;
            return n;
        }

        public int warnings() {
            int n = 0;
            for (int i = 0; i < issues.size(); i++) if (issues.get(i).severity == WARNING) n++;
            return n;
        }
    }

    private MdLint() {
    }

    public static Report check(String markdown) {
        Report report = new Report();
        String src = markdown == null ? "" : markdown;
        MdBlockParser.Result parsed = MdBlockParser.parse(src);
        String[] lines = src.split("\n", -1);

        int h1 = 0;
        int lastHeadingLevel = 0;
        char fenceChar = 0;
        int fenceLine = 0;
        boolean inFence = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            int lineNo = i + 1;

            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                char fc = trimmed.charAt(0);
                if (!inFence) {
                    inFence = true;
                    fenceChar = fc;
                    fenceLine = lineNo;
                } else if (fc == fenceChar) {
                    inFence = false;
                }
                continue;
            }
            if (inFence) {
                if (line.length() > 200) {
                    report.issues.add(new Issue(lineNo, INFO,
                            "Very long code line (" + line.length() + " chars)"));
                }
                continue;
            }
            if (trimmed.length() == 0) continue;

            if (line.indexOf('\t') >= 0) {
                report.issues.add(new Issue(lineNo, INFO,
                        "Tab character: use spaces for predictable list nesting"));
            }
            if (line.length() > 160) {
                report.issues.add(new Issue(lineNo, INFO,
                        "Long line (" + line.length() + " chars)"));
            }
        }
        if (inFence) {
            report.issues.add(new Issue(fenceLine, ERROR, "Code fence is never closed"));
        }

        List<MdBlock> blocks = parsed.blocks;
        for (int i = 0; i < blocks.size(); i++) {
            MdBlock b = blocks.get(i);
            if (b.type == MdBlock.HEADING) {
                report.headings.add(b);
                if (b.level == 1) h1++;
                if (lastHeadingLevel > 0 && b.level > lastHeadingLevel + 1) {
                    report.issues.add(new Issue(b.lineStart, WARNING,
                            "Heading level jumps from H" + lastHeadingLevel + " to H" + b.level));
                }
                lastHeadingLevel = b.level;
                String plain = MdToc.plain(b);
                if (plain.length() == 0) {
                    report.issues.add(new Issue(b.lineStart, WARNING, "Empty heading"));
                }
                String anchor = MdToc.slug(plain);
                Integer used = report.anchorUse.get(anchor);
                report.anchorUse.put(anchor, used == null ? 1 : used + 1);
                if (used != null) {
                    report.issues.add(new Issue(b.lineStart, WARNING,
                            "Duplicate anchor: two headings slugify to #" + anchor));
                }
            } else if (b.type == MdBlock.PARAGRAPH || b.type == MdBlock.LIST_ITEM
                    || b.type == MdBlock.HEADING) {
                collectLinks(b.text, b.lineStart, report);
            }
            if (b.type == MdBlock.TABLE && b.table != null) {
                for (int r = 0; r < b.table.rows.size(); r++) {
                    if (b.table.rows.get(r).length != b.table.headers.size()) {
                        report.issues.add(new Issue(b.lineStart + r + 2, WARNING,
                                "Table row has " + b.table.rows.get(r).length
                                        + " cells, header has " + b.table.headers.size()));
                    }
                }
            }
        }

        if (h1 == 0 && !blocks.isEmpty()) {
            report.issues.add(new Issue(1, INFO, "Document has no H1 title"));
        } else if (h1 > 1) {
            report.issues.add(new Issue(1, WARNING, h1 + " H1 headings: most renderers expect one"));
        }
        if (src.length() > 0 && !src.endsWith("\n")) {
            report.issues.add(new Issue(lines.length, INFO, "No newline at end of file"));
        }
        // unbalanced emphasis / brackets, line by line
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("```")) continue;
            int open = 0;
            int close = 0;
            for (int k = 0; k < line.length(); k++) {
                char c = line.charAt(k);
                if (c == '\\') { k++; continue; }
                if (c == '[') open++;
                else if (c == ']') close++;
            }
            if (close > open) {
                report.issues.add(new Issue(i + 1, WARNING, "Unmatched ']' on this line"));
            }
        }
        return report;
    }

    private static void collectLinks(String text, int line, Report report) {
        if (text == null || text.length() == 0) return;
        InlineResult r = MdInlineParser.parse(text);
        for (int i = 0; i < r.spans.size(); i++) {
            MdSpan s = r.spans.get(i);
            if (s.type == MdSpan.LINK || s.type == MdSpan.AUTOLINK || s.type == MdSpan.IMAGE) {
                report.linkTargets.add(s.url);
                if (s.url.length() == 0) {
                    report.issues.add(new Issue(line, WARNING, "Link with an empty target"));
                }
            }
        }
    }

    /** Flattens the outline into "indent title (line)" rows for the structure sheet. */
    public static List<String[]> outlineRows(String markdown) {
        List<String[]> rows = new ArrayList<String[]>();
        MdBlockParser.Result parsed = MdBlockParser.parse(markdown);
        List<MdToc.Entry> entries = MdToc.entries(parsed);
        for (int i = 0; i < entries.size(); i++) {
            MdToc.Entry e = entries.get(i);
            rows.add(new String[]{
                    MdToc.indentFor(e.level) + e.title,
                    String.valueOf(e.line),
                    String.valueOf(e.offset)
            });
        }
        return rows;
    }
}
