package com.markultra.editor.md;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Clipboard conversions: rich HTML to Markdown, and delimited text to a GFM table.
 *
 * <p>Both live outside Android so they are covered by the JVM self test. Failures are
 * silent by design: the caller falls back to pasting the text exactly as it arrived.</p>
 */
public final class PasteConvert {

    private PasteConvert() {
    }

    private static final int MAX_HTML = 2_000_000;

    /** True when the clipboard HTML is worth converting (more than a wrapper div). */
    public static boolean worthConverting(String html) {
        if (html == null) return false;
        String t = html.trim();
        if (t.length() < 12 || t.length() > MAX_HTML) return false;
        String lower = t.toLowerCase();
        // A single plain paragraph with no markup at all is not worth a round trip.
        boolean hasStructure = lower.contains("<p") || lower.contains("<li")
                || lower.contains("<h1") || lower.contains("<h2") || lower.contains("<h3")
                || lower.contains("<table") || lower.contains("<strong") || lower.contains("<em")
                || lower.contains("<code") || lower.contains("<pre") || lower.contains("<a ")
                || lower.contains("<br") || lower.contains("<blockquote");
        return hasStructure;
    }

    /** Converts HTML (web page, Word, Notion) to Markdown. Returns null on failure. */
    public static String htmlToMarkdown(String html) {
        if (!worthConverting(html)) return null;
        try {
            FlexmarkHtmlConverter converter = FlexmarkHtmlConverter.builder().build();
            String md = converter.convert(html);
            if (md == null) return null;
            md = md.replace("\u00a0", " ");
            md = md.replaceAll("[ \\t]+\\n", "\n");
            md = md.replaceAll("\\n{3,}", "\n\n");
            md = md.trim();
            return md.length() == 0 ? null : md;
        } catch (RuntimeException e) {
            return null;
        } catch (LinkageError e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ tables

    private static final char[] DELIMITERS = {'\t', ',', ';'};

    /** Picks the delimiter a block of text most consistently uses, or 0 if none. */
    static char sniffDelimiter(String text) {
        String[] lines = text.split("\n");
        if (lines.length < 2) return 0;
        for (int d = 0; d < DELIMITERS.length; d++) {
            char c = DELIMITERS[d];
            int expected = countOf(lines[0], c);
            if (expected == 0) continue;
            boolean consistent = true;
            int rows = Math.min(lines.length, 40);
            for (int i = 1; i < rows; i++) {
                if (lines[i].trim().length() == 0) continue;
                if (countOf(lines[i], c) != expected) {
                    consistent = false;
                    break;
                }
            }
            if (consistent) return c;
        }
        return 0;
    }

    private static int countOf(String line, char c) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) if (line.charAt(i) == c) n++;
        return n;
    }

    /** True when the text looks like something a spreadsheet exported. */
    public static boolean looksLikeTable(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.length() < 5 || t.length() > 200_000) return false;
        if (t.indexOf('<') >= 0) return false;
        char d = sniffDelimiter(t);
        if (d == 0) return false;
        String[] lines = t.split("\n");
        int rows = 0;
        for (int i = 0; i < lines.length; i++) if (lines[i].trim().length() > 0) rows++;
        return rows >= 2;
    }

    /** Splits one delimited line, honouring double quotes around a field. */
    static List<String> splitRow(String line, char delim) {
        List<String> out = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.length() == 0) {
                quoted = true;
            } else if (c == delim) {
                out.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        out.add(cell.toString().trim());
        return out;
    }

    /**
     * Converts delimited text to a GFM table: the first row becomes the header so the
     * result is a valid table on GitHub, where the header row is mandatory.
     */
    public static String tableToMarkdown(String text) {
        if (!looksLikeTable(text)) return null;
        String t = text.trim().replace("\u00a0", " ");
        char delim = sniffDelimiter(t);
        if (delim == 0) return null;
        String[] lines = t.split("\n");
        List<List<String>> rows = new ArrayList<List<String>>();
        int columns = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().length() == 0) continue;
            List<String> cells = splitRow(lines[i], delim);
            columns = Math.max(columns, cells.size());
            rows.add(cells);
        }
        if (rows.isEmpty() || columns < 2) return null;
        StringBuilder sb = new StringBuilder(rows.size() * columns * 12 + 64);
        for (int r = 0; r < rows.size(); r++) {
            List<String> cells = rows.get(r);
            sb.append('|');
            for (int c = 0; c < columns; c++) {
                String cell = c < cells.size() ? cells.get(c) : "";
                sb.append(' ').append(escapeCell(cell)).append(" |");
            }
            sb.append('\n');
            if (r == 0) {
                sb.append('|');
                for (int c = 0; c < columns; c++) {
                    List<String> sample = rows.size() > 1 ? rows.get(1) : cells;
                boolean numeric = c < sample.size() && isNumeric(sample.get(c));
                    sb.append(numeric ? " ---: |" : " :--- |");
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static boolean isNumeric(String value) {
        if (value.length() == 0) return false;
        String v = value.replace(",", "").replace("%", "").trim();
        if (v.length() == 0) return false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (!(Character.isDigit(c) || c == '.' || c == '-' || c == '+')) return false;
        }
        return true;
    }

    private static String escapeCell(String cell) {
        return cell.replace("|", "\\|").replace("\r", "").replace("\n", " ");
    }
}
