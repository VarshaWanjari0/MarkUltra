package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.List;

/** Table payload plus the conversions the editor needs (Markdown, CSV, sorting). */
public final class MdTableData {

    public static final int ALIGN_NONE = 0;
    public static final int ALIGN_LEFT = 1;
    public static final int ALIGN_CENTER = 2;
    public static final int ALIGN_RIGHT = 3;

    public final List<String> headers = new ArrayList<String>(4);
    public final List<String[]> rows = new ArrayList<String[]>(8);
    public final List<Integer> alignment = new ArrayList<Integer>(4);
    /** Absolute character offsets of the table in the document. */
    public int srcStart = -1;
    public int srcEnd = -1;

    public int columns() {
        return headers.size();
    }

    public void normalize() {
        int cols = headers.size();
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length == cols) continue;
            String[] fixed = new String[cols];
            for (int c = 0; c < cols; c++) fixed[c] = c < row.length ? row[c] : "";
            rows.set(r, fixed);
        }
        while (alignment.size() < cols) alignment.add(ALIGN_NONE);
    }

    public String toMarkdown() {
        normalize();
        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        for (int c = 0; c < headers.size(); c++) {
            if (c > 0) sb.append(" | ");
            sb.append(escapeCell(headers.get(c)));
        }
        sb.append(" |\n|");
        for (int c = 0; c < headers.size(); c++) {
            int a = alignment.get(c);
            if (a == ALIGN_CENTER) sb.append(" :---: |");
            else if (a == ALIGN_RIGHT) sb.append(" ---: |");
            else if (a == ALIGN_LEFT) sb.append(" :--- |");
            else sb.append(" --- |");
        }
        sb.append('\n');
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            sb.append("| ");
            for (int c = 0; c < headers.size(); c++) {
                if (c > 0) sb.append(" | ");
                sb.append(escapeCell(row[c]));
            }
            sb.append(" |\n");
        }
        return sb.toString();
    }

    public String toCsv(char separator) {
        normalize();
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < headers.size(); c++) {
            if (c > 0) sb.append(separator);
            sb.append(csv(headers.get(c), separator));
        }
        sb.append('\n');
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            for (int c = 0; c < headers.size(); c++) {
                if (c > 0) sb.append(separator);
                sb.append(csv(row[c], separator));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public void sortBy(int column, boolean ascending) {
        normalize();
        final int col = column;
        final boolean asc = ascending;
        java.util.Collections.sort(rows, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                String x = col < a.length ? a[col] : "";
                String y = col < b.length ? b[col] : "";
                Double nx = number(x);
                Double ny = number(y);
                int r;
                if (nx != null && ny != null) r = nx.compareTo(ny);
                else r = x.compareToIgnoreCase(y);
                return asc ? r : -r;
            }
        });
    }

    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < headers.size(); c++) {
            if (c > 0) sb.append("  ");
            sb.append(headers.get(c));
        }
        for (int r = 0; r < rows.size(); r++) {
            sb.append('\n');
            String[] row = rows.get(r);
            for (int c = 0; c < row.length; c++) {
                if (c > 0) sb.append("  ");
                sb.append(row[c]);
            }
        }
        return sb.toString();
    }

    public List<String> headerList() {
        return headers;
    }

    private static Double number(String s) {
        String t = s.trim().replace(",", "");
        if (t.length() == 0) return null;
        try {
            return Double.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escapeCell(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '|') sb.append("\\|");
            else if (c == '\n') sb.append("<br>");
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String csv(String s, char sep) {
        if (s == null) return "";
        boolean quote = s.indexOf(sep) >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!quote) return s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    /** Parses a Markdown/CSV table body into this object (used by the import dialog). */
    public static MdTableData fromDelimited(String text, char separator) {
        MdTableData t = new MdTableData();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) continue;
            List<String> cells = splitDelimited(line, separator);
            if (i == 0 && t.headers.isEmpty()) {
                for (int c = 0; c < cells.size(); c++) t.headers.add(cells.get(c));
            } else {
                t.rows.add(cells.toArray(new String[0]));
            }
        }
        t.normalize();
        return t;
    }

    private static List<String> splitDelimited(String line, char sep) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == sep && !quoted) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }
}
