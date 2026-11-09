package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block-level GitHub-Flavored-Markdown parser.
 *
 * <p>Single pass over pre-split lines with explicit offset tracking, so every
 * block can be mapped back to a character range in the editor (this powers
 * preview-to-editor navigation and scroll synchronisation).</p>
 *
 * <p>Pure Java: no Android imports, no reflection, no third-party code.</p>
 */
public final class MdBlockParser {

    private static final Pattern ATX = Pattern.compile("^ {0,3}(#{1,6})(?:[ \\t]+(.*?))?[ \\t]*#*[ \\t]*$");
    private static final Pattern HR = Pattern.compile("^ {0,3}((?:\\*[ \\t]*){3,}|(?:-[ \\t]*){3,}|(?:_[ \\t]*){3,})$");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})[ \\t]*(.*)$");
    private static final Pattern LIST = Pattern.compile("^([ \\t]*)([-+*]|\\d{1,9}[.)])([ \\t]+)(.*)$");
    private static final Pattern TABLE_DELIM = Pattern.compile("^[ \\t]*\\|?[ \\t]*:?-{1,}:?[ \\t]*(\\|[ \\t]*:?-{1,}:?[ \\t]*)*\\|?[ \\t]*$");
    private static final Pattern REF_DEF = Pattern.compile("^ {0,3}\\[([^\\]^][^\\]]*)\\]:[ \\t]*(\\S+)(?:[ \\t]+[\"'(](.*?)[\"')])?[ \\t]*$");
    private static final Pattern FOOTNOTE_DEF = Pattern.compile("^ {0,3}\\[\\^([^\\]]+)\\]:[ \\t]*(.*)$");
    private static final Pattern SETEXT = Pattern.compile("^ {0,3}(=+|-+)[ \\t]*$");
    private static final Pattern IMAGE_ONLY = Pattern.compile("^ {0,3}!\\[([^\\]]*)\\]\\(([^)\\s]+)(?:[ \\t]+\"([^\"]*)\")?\\)[ \\t]*$");
    /**
     * CommonMark block-level HTML. The tag name must be followed by whitespace, '>' or
     * '/>', otherwise {@code <https://x>} and {@code <mail@host>}, which are autolinks and
     * not HTML, would be swallowed as raw HTML and rendered as nothing.
     */
    private static final Pattern DETAILS_START =
            Pattern.compile("^ {0,3}<details\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY_TAG =
            Pattern.compile("(?is)<summary[^>]*>(.*?)</summary>");
    private static final Pattern HTML_START = Pattern.compile(
            "^ {0,3}<(!--|/?[a-zA-Z][a-zA-Z0-9-]*(\\s|/?>|$)).*");
    private static final Pattern DELIM_CELL = Pattern.compile("^:?-{1,}:?$");

    public static final class Result {
        public final List<MdBlock> blocks;
        public final Map<String, String> refs;
        public final int lineCount;

        Result(List<MdBlock> blocks, Map<String, String> refs, int lineCount) {
            this.blocks = blocks;
            this.refs = refs;
            this.lineCount = lineCount;
        }

        public MdInlineParser.Refs refLookup() {
            return new MdInlineParser.Refs() {
                @Override
                public String lookup(String normalizedLabel) {
                    return refs.get(normalizedLabel);
                }
            };
        }
    }

    private static final class Line {
        final String text;
        final int start;
        final int end;
        final int number;

        Line(String text, int start, int end, int number) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.number = number;
        }

        boolean blank() {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c != ' ' && c != '\t' && c != '\r') return false;
            }
            return true;
        }
    }

    public static Result parse(String source) {
        String src = source == null ? "" : source;
        List<Line> lines = split(src);
        Map<String, String> refs = new LinkedHashMap<String, String>();
        List<Line> kept = extractReferences(lines, refs);
        List<MdBlock> blocks = new ArrayList<MdBlock>();
        parseBlocks(kept, 0, kept.size(), blocks, src.length());
        return new Result(blocks, refs, lines.size());
    }

    // ---------------------------------------------------------------------------------
    // line preparation
    // ---------------------------------------------------------------------------------

    private static List<Line> split(String src) {
        List<Line> out = new ArrayList<Line>(Math.max(8, src.length() / 40));
        int i = 0;
        int lineNo = 1;
        int n = src.length();
        while (i <= n) {
            int j = i;
            while (j < n && src.charAt(j) != '\n') j++;
            int end = j;
            if (end > i && src.charAt(end - 1) == '\r') end--;
            out.add(new Line(src.substring(i, end), i, end, lineNo++));
            if (j >= n) break;
            i = j + 1;
        }
        return out;
    }

    /** Pulls {@code [label]: url "title"} definitions out of the document. */
    private static List<Line> extractReferences(List<Line> lines, Map<String, String> refs) {
        List<Line> kept = new ArrayList<Line>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.blank()) {
                kept.add(line);
                continue;
            }
            Matcher m = REF_DEF.matcher(line.text);
            if (m.matches() && !line.text.contains("[^")) {
                refs.put(MdInlineParser.normalizeLabel(m.group(1)), m.group(2));
                kept.add(new Line("", line.start, line.end, line.number));
            } else {
                kept.add(line);
            }
        }
        return kept;
    }

    // ---------------------------------------------------------------------------------
    // block parsing
    // ---------------------------------------------------------------------------------

    private static void parseBlocks(List<Line> lines, int from, int to,
                                    List<MdBlock> out, int docLength) {
        int i = from;
        int guard = 0;
        while (i < to && guard++ < 20000) {
            Line line = lines.get(i);
            if (line.blank()) {
                i++;
                continue;
            }
            int indent = indentOf(line.text);

            Matcher fence = FENCE.matcher(line.text);
            if (fence.matches() && indent <= 3) {
                i = parseFence(lines, i, to, out, fence);
                continue;
            }

            Matcher fn = FOOTNOTE_DEF.matcher(line.text);
            if (fn.matches()) {
                i = parseFootnote(lines, i, to, out, fn);
                continue;
            }

            if (line.text.trim().startsWith("$$") && !line.text.trim().startsWith("$$$")) {
                int nextIndex = parseMath(lines, i, to, out);
                if (nextIndex > i) {
                    i = nextIndex;
                    continue;
                }
            }

            if (DETAILS_START.matcher(line.text).matches()) {
                i = parseDetails(lines, i, to, out, docLength);
                continue;
            }

            Matcher atx = ATX.matcher(line.text);
            if (atx.matches()) {
                MdBlock b = new MdBlock(MdBlock.HEADING);
                b.level = atx.group(1).length();
                b.text = atx.group(2) == null ? "" : atx.group(2).trim();
                mark(b, line, line);
                b.srcEnd = line.end;
                out.add(b);
                i++;
                continue;
            }

            if (HR.matcher(line.text).matches()) {
                MdBlock b = new MdBlock(MdBlock.HR);
                mark(b, line, line);
                out.add(b);
                i++;
                continue;
            }

            if (isTableStart(lines, i, to)) {
                i = parseTable(lines, i, to, out);
                continue;
            }

            if (line.text.length() > 0 && line.text.charAt(indent < line.text.length() ? indent : 0) == '>') {
                i = parseQuote(lines, i, to, out);
                continue;
            }

            Matcher list = LIST.matcher(line.text);
            if (list.matches()) {
                i = parseList(lines, i, to, out);
                continue;
            }

            if (indent >= 4 && indent < 6) {
                i = parseIndentedCode(lines, i, to, out);
                continue;
            }

            Matcher img = IMAGE_ONLY.matcher(line.text);
            // A run of image-only lines with no blank lines between them is a paragraph of
            // inline images (GitHub badge rows); a lone image is a block of its own.
            boolean badgeRow = img.matches() && i + 1 < to
                    && !lines.get(i + 1).blank()
                    && IMAGE_ONLY.matcher(lines.get(i + 1).text).matches();
            if (img.matches() && !badgeRow) {
                MdBlock b = new MdBlock(MdBlock.IMAGE);
                b.lang = img.group(1);
                b.url = img.group(2);
                b.text = img.group(3) == null ? "" : img.group(3);
                mark(b, line, line);
                b.srcEnd = line.end;
                out.add(b);
                i++;
                continue;
            }

            if (HTML_START.matcher(line.text).matches()) {
                i = parseHtml(lines, i, to, out);
                continue;
            }

            i = parseParagraph(lines, i, to, out, docLength);
        }
    }

    private static int parseFence(List<Line> lines, int i, int to,
                                  List<MdBlock> out, Matcher open) {
        Line startLine = lines.get(i);
        String fence = open.group(1);
        char fc = fence.charAt(0);
        int fenceLen = fence.length();
        String info = open.group(2) == null ? "" : open.group(2).trim();
        MdBlock b = new MdBlock(MdBlock.CODE);
        b.lang = firstToken(info);
        StringBuilder body = new StringBuilder();
        int j = i + 1;
        Line last = startLine;
        boolean closed = false;
        while (j < to) {
            Line cur = lines.get(j);
            String t = cur.text;
            int ind = indentOf(t);
            if (ind <= 3) {
                int run = fenceRun(t, ind, fc);
                if (run >= fenceLen) {
                    closed = true;
                    last = cur;
                    j++;
                    break;
                }
            }
            if (body.length() > 0) body.append('\n');
            body.append(stripColumns(t, Math.min(ind, indentOf(startLine.text))));
            last = cur;
            j++;
        }
        if (!closed) last = lines.get(Math.min(to - 1, j - 1));
        b.text = body.toString();
        mark(b, startLine, last);
        b.srcEnd = closed ? last.end : (to > i + 1 ? lines.get(to - 1).end : startLine.end);
        out.add(b);
        return closed ? j : to;
    }

    private static int parseFootnote(List<Line> lines, int i, int to,
                                     List<MdBlock> out, Matcher m) {
        Line start = lines.get(i);
        StringBuilder body = new StringBuilder(m.group(2) == null ? "" : m.group(2));
        int j = i + 1;
        Line last = start;
        while (j < to) {
            Line cur = lines.get(j);
            if (cur.blank()) break;
            if (indentOf(cur.text) < 2) break;
            body.append(' ').append(stripColumns(cur.text, Math.min(4, indentOf(cur.text))));
            last = cur;
            j++;
        }
        MdBlock b = new MdBlock(MdBlock.FOOTNOTE);
        b.footnoteId = m.group(1);
        b.text = body.toString();
        mark(b, start, last);
        b.srcEnd = last.end;
        out.add(b);
        return j;
    }

    private static int parseQuote(List<Line> lines, int i, int to, List<MdBlock> out) {
        List<Line> inner = new ArrayList<Line>();
        int j = i;
        Line last = lines.get(i);
        while (j < to) {
            Line cur = lines.get(j);
            if (cur.blank()) break;
            int ind = indentOf(cur.text);
            if (ind < cur.text.length() && cur.text.charAt(ind) == '>') {
                int k = ind + 1;
                if (k < cur.text.length() && (cur.text.charAt(k) == ' ' || cur.text.charAt(k) == '\t')) k++;
                inner.add(new Line(cur.text.substring(k), cur.start + k, cur.end, cur.number));
                last = cur;
                j++;
            } else if (indentOf(cur.text) > 0) {
                inner.add(cur);
                last = cur;
                j++;
            } else {
                break;
            }
        }
        MdBlock b = new MdBlock(MdBlock.QUOTE);
        mark(b, lines.get(i), last);
        b.srcEnd = last.end;
        parseBlocks(inner, 0, inner.size(), b.children, 0);
        out.add(b);
        return j;
    }

    private static int parseList(List<Line> lines, int i, int to, List<MdBlock> out) {
        int baseIndent = indentOf(lines.get(i).text);
        int j = i;
        int ordinal = 0;
        Line last = lines.get(i);
        while (j < to) {
            Line cur = lines.get(j);
            if (cur.blank()) break;
            int ind = indentOf(cur.text);
            Matcher m = LIST.matcher(cur.text);
            if (!m.matches() || ind != baseIndent) break;

            String marker = m.group(2);
            boolean ordered = Character.isDigit(marker.charAt(0));
            int number = 1;
            if (ordered) {
                String digits = marker.substring(0, marker.length() - 1);
                try {
                    number = Integer.parseInt(digits);
                } catch (NumberFormatException e) {
                    number = ordinal + 1;
                }
            }
            ordinal++;

            int contentCol = ind + marker.length() + m.group(3).length();
            String firstContent = m.group(4);

            List<Line> itemLines = new ArrayList<Line>();
            int prefix = ind + marker.length() + m.group(3).length();
            itemLines.add(new Line(firstContent, cur.start + prefix,
                    cur.start + prefix + firstContent.length(), cur.number));
            Line itemLast = cur;
            j++;

            while (j < to) {
                Line next = lines.get(j);
                if (next.blank()) {
                    // a blank line keeps the item open only if the block continues, deeper
                    int k = j + 1;
                    while (k < to && lines.get(k).blank()) k++;
                    if (k < to && indentOf(lines.get(k).text) > baseIndent) {
                        itemLines.add(new Line("", next.start, next.start, next.number));
                        itemLast = next;
                        j++;
                        continue;
                    }
                    break;
                }
                int nIndent = indentOf(next.text);
                if (nIndent > baseIndent) {
                    int strip = Math.min(nIndent, contentCol);
                    itemLines.add(new Line(stripColumns(next.text, strip), next.start + strip,
                            next.end, next.number));
                    itemLast = next;
                    j++;
                    continue;
                }
                if (LIST.matcher(next.text).matches() && nIndent == baseIndent) break;
                if (nIndent <= baseIndent) break;
            }

            Line itemStart = cur;
            MdBlock item = new MdBlock(MdBlock.LIST_ITEM);
            item.ordered = ordered;
            item.number = number;
            item.level = (baseIndent / 2) + 1;
            mark(item, itemStart, itemLast);

            // checkbox detection
            String raw = itemLines.isEmpty() ? "" : itemLines.get(0).text;
            int cb = checkboxState(raw);
            if (cb >= 0) {
                item.checkbox = true;
                item.checked = cb == 1;
                Line first = itemLines.get(0);
                int skip = raw.length() >= 4 ? 4 : raw.length();
                itemLines.set(0, new Line(raw.substring(skip).trim(), first.start + skip, first.end, first.number));
            }

            parseBlocks(itemLines, 0, itemLines.size(), item.children, 0);
            if (!item.children.isEmpty() && item.children.get(0).type == MdBlock.PARAGRAPH) {
                MdBlock para = item.children.remove(0);
                item.text = para.text;
                item.srcStart = para.srcStart;
                item.srcEnd = para.srcEnd;
                if (item.children.isEmpty()) {
                    item.srcEnd = Math.max(item.srcEnd, para.srcEnd);
                }
            } else {
                item.text = "";
                item.srcStart = itemLines.isEmpty() ? cur.start : itemLines.get(0).start;
                item.srcEnd = itemLast.end;
            }
            item.lineStart = cur.number;
            item.lineEnd = itemLast.number;

            out.add(item);
        }
        if (last == null) last = lines.get(i);
        return j;
    }

    /** @return 1 checked, 0 unchecked, -1 not a task item. */
    private static int checkboxState(String raw) {
        String t = raw.trim();
        if (t.length() < 3 || t.charAt(0) != '[') return -1;
        int close = t.indexOf(']');
        if (close < 0 || close > 3) return -1;
        String inner = t.substring(1, close);
        if (inner.length() == 0) return 0;
        if (inner.length() > 1) return -1;
        char c = inner.charAt(0);
        if (c == ' ') return 0;
        if (c == 'x' || c == 'X') return 1;
        return -1;
    }

    private static boolean isTableStart(List<Line> lines, int i, int to) {
        Line head = lines.get(i);
        if (head.text.indexOf('|') < 0) return false;
        int k = i + 1;
        while (k < to && lines.get(k).blank()) k++;
        if (k >= to) return false;
        String delim = lines.get(k).text.trim();
        if (delim.indexOf('|') < 0) return false;
        if (!TABLE_DELIM.matcher(delim).matches()) return false;
        return countCells(head.text) == countCells(delim);
    }

    private static int parseTable(List<Line> lines, int i, int to, List<MdBlock> out) {
        Line headLine = lines.get(i);
        MdTableData data = new MdTableData();
        List<String> heads = splitCells(headLine.text);
        for (int c = 0; c < heads.size(); c++) data.headers.add(heads.get(c));

        int k = i + 1;
        while (k < to && lines.get(k).blank()) k++;
        Line delimLine = lines.get(k);
        List<String> delimCells = splitCells(delimLine.text);
        for (int c = 0; c < data.headers.size(); c++) {
            String d = c < delimCells.size() ? delimCells.get(c).trim() : "---";
            if (!DELIM_CELL.matcher(d).matches()) d = "---";
            data.alignment.add(alignmentOf(d));
        }
        data.srcStart = headLine.start;
        data.srcEnd = delimLine.end;

        int j = k + 1;
        Line last = delimLine;
        while (j < to) {
            Line cur = lines.get(j);
            if (cur.blank()) break;
            if (cur.text.indexOf('|') < 0) break;
            if (FENCE.matcher(cur.text).matches()) break;
            if (ATX.matcher(cur.text).matches()) break;
            if (HR.matcher(cur.text).matches()) break;
            List<String> cells = splitCells(cur.text);
            String[] row = new String[data.headers.size()];
            for (int c = 0; c < row.length; c++) row[c] = c < cells.size() ? cells.get(c) : "";
            data.rows.add(row);
            data.srcEnd = cur.end;
            last = cur;
            j++;
        }
        data.normalize();

        MdBlock b = new MdBlock(MdBlock.TABLE);
        b.table = data;
        mark(b, headLine, last);
        b.srcEnd = data.srcEnd;
        out.add(b);
        return j;
    }

    private static int alignmentOf(String cell) {
        boolean left = cell.startsWith(":");
        boolean right = cell.endsWith(":");
        if (left && right) return MdTableData.ALIGN_CENTER;
        if (right) return MdTableData.ALIGN_RIGHT;
        if (left) return MdTableData.ALIGN_LEFT;
        return MdTableData.ALIGN_NONE;
    }

    private static int parseIndentedCode(List<Line> lines, int i, int to, List<MdBlock> out) {
        StringBuilder body = new StringBuilder();
        int j = i;
        Line last = lines.get(i);
        while (j < to) {
            Line cur = lines.get(j);
            if (!cur.blank() && indentOf(cur.text) < 4) break;
            if (body.length() > 0) body.append('\n');
            body.append(cur.blank() ? "" : rtrim(stripColumns(cur.text, 4)));
            if (!cur.blank()) last = cur;
            j++;
        }
        while (body.length() > 0 && body.charAt(body.length() - 1) == '\n') {
            body.setLength(body.length() - 1);
        }
        MdBlock b = new MdBlock(MdBlock.CODE);
        b.text = body.toString();
        b.lang = "";
        mark(b, lines.get(i), last);
        b.srcEnd = last.end;
        out.add(b);
        return j;
    }

    /** {@code $$ ... $$} on one line or across several. Returns {@code i} when not maths. */
    private static int parseMath(List<Line> lines, int i, int to, List<MdBlock> out) {
        Line first = lines.get(i);
        String text = first.text.trim();
        String body;
        int end = i;
        if (text.length() > 4 && text.endsWith("$$")) {
            body = text.substring(2, text.length() - 2).trim();
        } else {
            StringBuilder sb = new StringBuilder();
            String tail = text.length() > 2 ? text.substring(2).trim() : "";
            if (tail.length() > 0) sb.append(tail);
            int j = i + 1;
            boolean closed = false;
            while (j < to) {
                String t = lines.get(j).text;
                int close = t.indexOf("$$");
                if (close >= 0) {
                    String head = t.substring(0, close).trim();
                    if (head.length() > 0) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(head);
                    }
                    end = j;
                    closed = true;
                    break;
                }
                if (sb.length() > 0) sb.append('\n');
                sb.append(t);
                j++;
            }
            if (!closed) return i;
            body = sb.toString().trim();
        }
        if (body.length() == 0) return i;
        MdBlock b = new MdBlock(MdBlock.MATH);
        b.text = body;
        mark(b, first, lines.get(end));
        b.srcEnd = lines.get(end).end;
        out.add(b);
        return end + 1;
    }

    /**
     * {@code <details>} with an optional {@code <summary>}. The body is parsed as Markdown,
     * so READMEs that hide code samples or screenshots behind a disclosure keep working.
     */
    private static int parseDetails(List<Line> lines, int i, int to, List<MdBlock> out,
                                    int docLength) {
        Line first = lines.get(i);
        String summary = "";
        List<Line> body = new ArrayList<Line>();
        int j = i;
        int end = i;
        boolean closed = false;
        while (j < to) {
            String raw = lines.get(j).text;
            String t = raw.trim();
            if (j > i || !t.startsWith("<details")) {
                if (t.startsWith("</details")) {
                    end = j;
                    closed = true;
                    j++;
                    break;
                }
            }
            Matcher sum = SUMMARY_TAG.matcher(raw);
            String withoutSummary = raw;
            if (sum.find()) {
                summary = sum.group(1).trim();
                withoutSummary = (raw.substring(0, sum.start()) + raw.substring(sum.end())).trim();
            }
            String cleaned = withoutSummary
                    .replaceAll("(?i)</?details[^>]*>", "")
                    .trim();
            if (cleaned.length() > 0) body.add(new Line(cleaned, lines.get(j).start,
                    lines.get(j).end, lines.get(j).number));
            end = j;
            j++;
        }
        if (!closed) {
            // an unclosed <details> still renders, it just swallows the rest of the document
            end = Math.min(to - 1, j - 1);
        }
        MdBlock b = new MdBlock(MdBlock.DETAILS);
        b.text = summary;
        mark(b, first, lines.get(end));
        b.srcEnd = lines.get(end).end;
        b.level = 1;
        parseBlocks(body, 0, body.size(), b.children, docLength);
        out.add(b);
        return closed ? j : Math.max(i + 1, end + 1);
    }

    private static int parseHtml(List<Line> lines, int i, int to, List<MdBlock> out) {
        StringBuilder body = new StringBuilder();
        int j = i;
        Line last = lines.get(i);
        while (j < to) {
            Line cur = lines.get(j);
            if (cur.blank()) break;
            if (body.length() > 0) body.append('\n');
            body.append(cur.text);
            last = cur;
            j++;
        }
        MdBlock b = new MdBlock(MdBlock.HTML);
        b.text = body.toString();
        mark(b, lines.get(i), last);
        b.srcEnd = last.end;
        out.add(b);
        return j;
    }

    private static int parseParagraph(List<Line> lines, int i, int to,
                                      List<MdBlock> out, int docLength) {
        StringBuilder body = new StringBuilder();
        int j = i;
        Line last = lines.get(i);
        int blockStart = lines.get(i).start;
        int blockEnd = lines.get(i).end;
        int paragraphLines = 0;
        final int firstIndent = indentOf(lines.get(i).text);
        while (j < to) {
            Line cur = lines.get(j);
            if (j > i) {
                if (cur.blank()) break;
                Matcher setext = SETEXT.matcher(cur.text);
                if (setext.matches() && paragraphLines == 1) {
                    MdBlock b = new MdBlock(MdBlock.HEADING);
                    b.level = setext.group(1).charAt(0) == '=' ? 1 : 2;
                    b.text = body.toString().trim();
                    b.srcStart = blockStart;
                    b.srcEnd = cur.end;
                    b.lineStart = lines.get(i).number;
                    b.lineEnd = cur.number;
                    out.add(b);
                    return j + 1;
                }
                if (startsNewBlock(cur)) break;
                if (LIST.matcher(cur.text).matches() && indentOf(cur.text) <= firstIndent) break;
                if (isTableStart(lines, j, to)) break;
            }
            if (body.length() > 0) body.append('\n');
            String trimmed = rtrim(cur.text);
            if (trimmed.length() < cur.text.length() - 1) {
                // two or more trailing spaces are a hard break; keep exactly two so the
                // inline parser can tell it apart from a soft wrap
                trimmed = trimmed + "  ";
            }
            int lead = 0;
            if (j == i) {
                while (lead < trimmed.length() - 2
                        && (trimmed.charAt(lead) == ' ' || trimmed.charAt(lead) == '\t')) {
                    lead++;
                }
            }
            body.append(j == i ? trimmed.substring(lead) : trimmed);
            blockEnd = cur.end;
            last = cur;
            paragraphLines++;
            j++;
            if (body.length() > 400000) break;
        }
        MdBlock b = new MdBlock(MdBlock.PARAGRAPH);
        b.text = body.toString();
        b.srcStart = blockStart;
        b.srcEnd = blockEnd;
        b.lineStart = lines.get(i).number;
        b.lineEnd = last.number;
        out.add(b);
        return Math.max(j, i + 1);
    }

    private static boolean startsNewBlock(Line line) {
        String t = line.text;
        if (FENCE.matcher(t).matches()) return true;
        if (ATX.matcher(t).matches()) return true;
        if (HR.matcher(t).matches()) return true;
        if (FOOTNOTE_DEF.matcher(t).matches()) return true;
        int ind = indentOf(t);
        if (ind < t.length() && t.charAt(ind) == '>') return true;
        return false;
    }

    // ---------------------------------------------------------------------------------
    // cell / column helpers
    // ---------------------------------------------------------------------------------

    private static int countCells(String line) {
        return splitCells(line).size();
    }

    static List<String> splitCells(String line) {
        List<String> cells = new ArrayList<String>(8);
        StringBuilder cur = new StringBuilder();
        int start = 0;
        int end = line.length();
        while (start < end && (line.charAt(start) == ' ' || line.charAt(start) == '\t')) start++;
        boolean leading = start < end && line.charAt(start) == '|';
        if (leading) start++;
        int stop = end;
        while (stop > start && (line.charAt(stop - 1) == ' ' || line.charAt(stop - 1) == '\t')) stop--;
        boolean trailing = stop > start && line.charAt(stop - 1) == '|' && !isEscaped(line, stop - 1);
        if (trailing) stop--;
        for (int i = start; i < stop; i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < stop && line.charAt(i + 1) == '|') {
                cur.append('|');
                i++;
            } else if (c == '|') {
                cells.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cells.add(cur.toString().trim());
        if (cells.size() == 1 && cells.get(0).length() == 0) cells.clear();
        return cells;
    }

    private static boolean isEscaped(String s, int idx) {
        int count = 0;
        int i = idx - 1;
        while (i >= 0 && s.charAt(i) == '\\') {
            count++;
            i--;
        }
        return count % 2 == 1;
    }

    static int indentOf(String text) {
        int col = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') col++;
            else if (c == '\t') col += 4 - (col % 4);
            else break;
        }
        return col;
    }

    static String stripColumns(String text, int columns) {
        int col = 0;
        int i = 0;
        while (i < text.length() && col < columns) {
            char c = text.charAt(i);
            if (c == ' ') {
                col++;
                i++;
            } else if (c == '\t') {
                col += 4 - (col % 4);
                i++;
            } else {
                break;
            }
        }
        return text.substring(Math.min(i, text.length()));
    }

    private static int fenceRun(String text, int indent, char fc) {
        int i = indent;
        int run = 0;
        while (i + run < text.length() && text.charAt(i + run) == fc) run++;
        return run;
    }

    private static String firstToken(String info) {
        int i = 0;
        while (i < info.length() && !Character.isWhitespace(info.charAt(i))) i++;
        return info.substring(0, i).toLowerCase();
    }

    private static String rtrim(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t')) end--;
        return s.substring(0, end);
    }

    private static void mark(MdBlock block, Line from, Line to) {
        block.srcStart = from.start;
        block.srcEnd = to.end;
        block.lineStart = from.number;
        block.lineEnd = to.number;
    }
}
