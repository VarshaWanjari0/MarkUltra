package com.markultra.editor.editor;

import android.text.Editable;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All "smart editing" behaviour in one place: auto-pairs, list continuation,
 * indentation, line operations and Markdown formatting toggles.
 *
 * <p>Every method works on the current selection of the given {@link EditText}
 * and is written to be a no-op rather than an exception on odd input.</p>
 */
public final class SmartEdit {

    public static final String[] EOL_INSERTS = {"", "\n"};

    /** Runtime configuration mirror of the relevant settings. */
    public static final class Config {
        public boolean autoPair = true;
        public boolean autoLists = true;
        public boolean autoIndent = true;
        public int tabWidth = 4;
    }

    private SmartEdit() {
    }

    // ---------------------------------------------------------------------------------
    // enter key
    // ---------------------------------------------------------------------------------

    /** @return true when the event was handled and should not fall through to the IME. */
    public static boolean handleEnter(EditText editor, Config cfg) {
        Editable text = editor.getText();
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        if (selStart < 0 || selEnd < 0) return false;
        int lineStart = lineStart(text, selStart);
        String before = text.subSequence(lineStart, Math.max(lineStart, selStart)).toString();

        if (cfg.autoLists) {
            String cont = continuationFor(before);
            if (cont != null) {
                String content = before.substring(markerPrefixLength(before));
                if (content.trim().length() == 0) {
                    // empty list item: clear the marker and leave the list
                    text.replace(lineStart, selStart, "");
                    editor.setSelection(lineStart);
                    return true;
                }
                replaceSelection(editor, "\n" + cont);
                return true;
            }
        }
        if (cfg.autoIndent) {
            int indent = 0;
            while (indent < before.length() && (before.charAt(indent) == ' '
                    || before.charAt(indent) == '\t')) {
                indent++;
            }
            if (indent > 0 && before.trim().length() > 0) {
                String pad = before.substring(0, indent);
                replaceSelection(editor, "\n" + pad);
                return true;
            }
        }
        return false;
    }

    /** Marker that the next line should repeat, or null when this is not a list line. */
    public static String continuationFor(String lineBeforeCaret) {
        if (lineBeforeCaret == null) return null;
        String line = lineBeforeCaret;
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        String indent = line.substring(0, i);
        if (i >= line.length()) return null;
        char c = line.charAt(i);
        if (c == '>') {
            return indent + "> ";
        }
        if (c == '-' || c == '*' || c == '+') {
            if (i + 1 >= line.length() || line.charAt(i + 1) != ' ') return null;
            String rest = line.substring(i + 2);
            String task = taskPrefix(rest);
            if (task != null) return indent + c + " [ ] ";
            return indent + c + " ";
        }
        int j = i;
        while (j < line.length() && Character.isDigit(line.charAt(j))) j++;
        if (j > i && j < line.length() && (line.charAt(j) == '.' || line.charAt(j) == ')')
                && j + 1 < line.length() && line.charAt(j + 1) == ' ') {
            int n = 1;
            try {
                n = Integer.parseInt(line.substring(i, j));
            } catch (NumberFormatException ignored) {
                n = 1;
            }
            return indent + (n + 1) + line.charAt(j) + " ";
        }
        return null;
    }

    private static String taskPrefix(String rest) {
        if (rest.length() >= 3 && rest.charAt(0) == '[' && rest.charAt(2) == ']') {
            char state = rest.charAt(1);
            if (state == ' ' || state == 'x' || state == 'X') return "[ ] ";
        }
        return null;
    }

    /** Length of the list/quote marker (plus indent) at the start of {@code line}. */
    public static int markerPrefixLength(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        if (i >= line.length()) return i;
        char c = line.charAt(i);
        if (c == '>') {
            int k = i + 1;
            if (k < line.length() && line.charAt(k) == ' ') k++;
            return k;
        }
        if (c == '-' || c == '*' || c == '+') {
            if (i + 1 >= line.length() || line.charAt(i + 1) != ' ') return i;
            int k = i + 2;
            String rest = line.substring(k);
            if (rest.length() >= 3 && rest.charAt(0) == '[' && rest.charAt(2) == ']') {
                char state = rest.charAt(1);
                if (state == ' ' || state == 'x' || state == 'X') {
                    k += 2;
                    if (k < line.length() && line.charAt(k) == ' ') k++;
                }
            }
            return k;
        }
        int j = i;
        while (j < line.length() && Character.isDigit(line.charAt(j))) j++;
        if (j > i && j < line.length() && (line.charAt(j) == '.' || line.charAt(j) == ')')
                && j + 1 < line.length() && line.charAt(j + 1) == ' ') {
            return j + 2;
        }
        return i;
    }

    // ---------------------------------------------------------------------------------
    // tabs and indentation
    // ---------------------------------------------------------------------------------

    public static boolean handleTab(EditText editor, Config cfg, boolean shift) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0 || end < 0) return false;
        if (start == end && !shift) {
            int lineStart = lineStart(text, start);
            int col = start - lineStart;
            int width = Math.max(1, cfg.tabWidth);
            int spaces = width - (col % width);
            text.insert(start, spaces(spaces));
            editor.setSelection(start + spaces);
            return true;
        }
        indentLines(editor, shift, cfg.tabWidth);
        return true;
    }

    public static void indentLines(EditText editor, boolean outdent, int tabWidth) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0 || end < 0) return;
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        int from = lineStart(text, start);
        int to = lineEnd(text, end);
        String block = text.subSequence(from, to).toString();
        String[] lines = block.split("\n", -1);
        StringBuilder sb = new StringBuilder(block.length() + lines.length * tabWidth);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            String line = lines[i];
            if (outdent) {
                int remove = 0;
                while (remove < line.length() && remove < tabWidth && line.charAt(remove) == ' ') remove++;
                if (remove == 0 && line.startsWith("\t")) remove = 1;
                sb.append(line.substring(remove));
            } else if (line.length() > 0) {
                for (int k = 0; k < tabWidth; k++) sb.append(' ');
                sb.append(line);
            } else {
                sb.append(line);
            }
        }
        int caret = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        String replaced = sb.toString();
        text.replace(from, to, replaced);
        int delta = replaced.length() - block.length();
        int newCaret = Math.max(from, Math.min(from + replaced.length(), caret + delta));
        editor.setSelection(newCaret);
    }

    // ---------------------------------------------------------------------------------
    // auto pairs
    // ---------------------------------------------------------------------------------

    private static final String PAIRS_OPEN = "([{\"\'`";
    private static final String PAIRS_CLOSE = ")]}\"\'`";

    public static boolean handleAutoPair(EditText editor, Config cfg, char typed) {
        if (!cfg.autoPair) return false;
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start != end) return false;
        char prev = start > 0 ? text.charAt(start - 1) : 0;

        // skip over an existing closing character instead of doubling it
        int idx = PAIRS_CLOSE.indexOf(typed);
        if (idx >= 0 && start < text.length() && text.charAt(start) == typed) {
            editor.setSelection(start + 1);
            return true;
        }
        if (typed == '"' && start > 0 && isWordChar(prev)) return false;
        if (typed == '\'' && start > 0 && isWordChar(prev)) return false;

        if (PAIRS_OPEN.indexOf(typed) >= 0) {
            String close = String.valueOf(typed);
            replaceSelection(editor, typed + close);
            int caret = editor.getSelectionStart();
            editor.setSelection(caret - 1);
            return true;
        }
        return false;
    }

    /** Wraps the selection (or inserts an empty pair) with the given markers. */
    public static void wrapSelection(EditText editor, String open, String close) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        String selected = text.subSequence(start, end).toString();
        text.replace(start, end, open + selected + close);
        if (selected.length() == 0) {
            editor.setSelection(start + open.length());
        } else {
            editor.setSelection(start + open.length() + selected.length());
        }
    }

    /** Toggles a symmetric marker around the selection, unwrapping when present. */
    public static void toggleInline(EditText editor, String marker) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        int m = marker.length();
        String selected = text.subSequence(start, end).toString();
        boolean wrapped = selected.length() >= m * 2
                && selected.startsWith(marker) && selected.endsWith(marker);
        if (wrapped) {
            String inner = selected.substring(m, selected.length() - m);
            text.replace(start, end, inner);
            editor.setSelection(start + inner.length());
            return;
        }
        boolean wrappedOutside = start >= m && end + m <= text.length()
                && text.subSequence(start - m, start).toString().equals(marker)
                && text.subSequence(end, end + m).toString().equals(marker);
        if (wrappedOutside) {
            text.delete(end, end + m);
            text.delete(start - m, start);
            editor.setSelection(Math.max(0, start - m) + selected.length());
            return;
        }
        text.replace(start, end, marker + selected + marker);
        if (selected.length() == 0) {
            editor.setSelection(start + m);
        } else {
            editor.setSelection(start + m + selected.length());
        }
    }

    // ---------------------------------------------------------------------------------
    // line level formatting
    // ---------------------------------------------------------------------------------

    /** Adds or removes a literal line prefix on every selected line. */
    public static void toggleLinePrefix(EditText editor, String prefix) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        int from = lineStart(text, start);
        int to = lineEnd(text, end);
        String block = text.subSequence(from, to).toString();
        String[] lines = block.split("\n", -1);
        boolean all = true;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].startsWith(prefix)) {
                all = false;
                break;
            }
        }
        StringBuilder sb = new StringBuilder(block.length() + lines.length * prefix.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            if (all) {
                sb.append(stripPrefix(lines[i], prefix));
            } else {
                int indentEnd = 0;
                while (indentEnd < lines[i].length() && (lines[i].charAt(indentEnd) == ' '
                        || lines[i].charAt(indentEnd) == '\t')) {
                    indentEnd++;
                }
                sb.append(lines[i].substring(0, indentEnd)).append(prefix)
                        .append(stripPrefix(lines[i].substring(indentEnd), prefix));
            }
        }
        int caretLine = lineIndex(block, start - from);
        int before = lineLength(block, caretLine);
        String updated = sb.toString();
        text.replace(from, to, updated);
        if (start != end) {
            // keep the block selected so the command can be toggled straight back
            editor.setSelection(from, Math.min(text.length(), from + updated.length()));
            return;
        }
        int after = lineLength(updated, caretLine);
        // the caret moves with the text it was sitting in (so "- " leaves it just past " "),
        // but it never ends up outside the line it started on
        int lineStartAbs = from + lineStartIndex(updated, caretLine);
        int lineEndAbs = lineStartAbs + after;
        int caret = start + (after - before);
        caret = Math.max(lineStartAbs, Math.min(caret, lineEndAbs));
        editor.setSelection(Math.max(0, Math.min(caret, text.length())));
    }

    /** Index of the line that contains {@code offset} inside {@code block}. */
    private static int lineIndex(String block, int offset) {
        int line = 0;
        int limit = Math.max(0, Math.min(offset, block.length()));
        for (int i = 0; i < limit; i++) {
            if (block.charAt(i) == '\n') line++;
        }
        return line;
    }

    /** Absolute offset of line {@code index} inside {@code block}. */
    private static int lineStartIndex(String block, int index) {
        int line = 0;
        for (int i = 0; i < block.length(); i++) {
            if (block.charAt(i) == '\n') {
                line++;
                if (line == index) return i + 1;
            }
        }
        return 0;
    }

    /** Length of line {@code index} inside {@code block}, excluding the newline. */
    private static int lineLength(String block, int index) {
        int line = 0;
        int start = 0;
        for (int i = 0; i < block.length(); i++) {
            if (block.charAt(i) == '\n') {
                if (line == index) return i - start;
                line++;
                start = i + 1;
            }
        }
        return block.length() - start;
    }

    private static String stripPrefix(String line, String prefix) {
        if (line.startsWith(prefix)) return line.substring(prefix.length());
        String trimmed = line.trim();
        if (prefix.startsWith(">") && trimmed.startsWith(">")) {
            int i = line.indexOf('>');
            return line.substring(0, i) + line.substring(i + 1).replaceFirst("^ ", "");
        }
        if (prefix.startsWith("#")) {
            int i = line.indexOf('#');
            if (i >= 0) {
                int j = i;
                while (j < line.length() && line.charAt(j) == '#') j++;
                if (j < line.length() && line.charAt(j) == ' ') j++;
                return line.substring(0, i) + line.substring(j);
            }
        }
        return line;
    }

    /** Cycles H1 to H2 to H3 ... then back to plain text. */
    public static void cycleHeading(EditText editor) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int from = lineStart(text, start);
        int to = lineEnd(text, start);
        String line = text.subSequence(from, to).toString();
        int indent = 0;
        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }
        String body = line.substring(indent);
        int level = 0;
        while (level < body.length() && body.charAt(level) == '#') level++;
        if (level > 0 && (level >= body.length() || body.charAt(level) != ' ')) level = 0;
        String content = level > 0 ? body.substring(level + 1) : body;
        int next = level >= 6 ? 0 : level + 1;
        StringBuilder sb = new StringBuilder();
        sb.append(line, 0, indent);
        if (next > 0) {
            for (int i = 0; i < next; i++) sb.append('#');
            sb.append(' ');
        }
        sb.append(content);
        text.replace(from, to, sb.toString());
        editor.setSelection(Math.min(from + sb.length(), text.length()));
    }

    /** Applies a heading level directly (0 removes the heading). */
    public static void setHeading(EditText editor, int level) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int from = lineStart(text, start);
        int to = lineEnd(text, start);
        String line = text.subSequence(from, to).toString();
        String body = line.trim();
        int hashes = 0;
        while (hashes < body.length() && body.charAt(hashes) == '#') hashes++;
        if (hashes > 0 && (hashes >= body.length() || body.charAt(hashes) != ' ')) hashes = 0;
        String content = hashes > 0 ? body.substring(hashes + 1) : body;
        StringBuilder sb = new StringBuilder();
        if (level > 0) {
            for (int i = 0; i < level; i++) sb.append('#');
            sb.append(' ');
        }
        sb.append(content);
        text.replace(from, to, sb.toString());
        editor.setSelection(Math.min(from + sb.length(), text.length()));
    }

    // ---------------------------------------------------------------------------------
    // line operations
    // ---------------------------------------------------------------------------------

    public static void duplicateLines(EditText editor) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        int from = lineStart(text, start);
        int to = lineEnd(text, end);
        String block = text.subSequence(from, to).toString();
        text.insert(to, "\n" + block);
        editor.setSelection(end + block.length() + 1);
    }

    public static boolean moveLines(EditText editor, int direction) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        int from = lineStart(text, start);
        int to = lineEnd(text, end);
        if (direction < 0) {
            if (from == 0) return false;
            int prevStart = lineStart(text, from - 1);
            String prev = text.subSequence(prevStart, from - 1).toString();
            String block = text.subSequence(from, to).toString();
            text.replace(prevStart, to, block + "\n" + prev);
            int newStart = prevStart + block.length() + 1;
            editor.setSelection(newStart);
            return true;
        }
        if (to >= text.length()) return false;
        int nextEnd = lineEnd(text, Math.min(text.length(), to + 1));
        String next = text.subSequence(to + 1, nextEnd).toString();
        String block = text.subSequence(from, to).toString();
        text.replace(from, nextEnd, next + "\n" + block);
        editor.setSelection(from + next.length() + 1);
        return true;
    }

    public static void deleteLines(EditText editor) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        int from = lineStart(text, start);
        int to = lineEnd(text, end);
        if (to < text.length()) to++;
        else if (from > 0) from--;
        text.delete(Math.max(0, from), Math.min(text.length(), Math.max(from, to)));
        editor.setSelection(Math.min(from, text.length()));
    }

    public static void sortLines(EditText editor, boolean reverse) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        int from = lineStart(text, start);
        int to = lineEnd(text, end);
        String block = text.subSequence(from, to).toString();
        String[] lines = block.split("\n", -1);
        List<String> list = new ArrayList<String>(lines.length);
        for (int i = 0; i < lines.length; i++) list.add(lines[i]);
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        if (reverse) Collections.reverse(list);
        StringBuilder sb = new StringBuilder(block.length());
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(list.get(i));
        }
        text.replace(from, to, sb.toString());
        editor.setSelection(Math.min(from + sb.length(), text.length()));
    }

    public enum CaseMode {UPPER, LOWER, TITLE, SENTENCE}

    public static void transformCase(EditText editor, CaseMode mode) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start == end) {
            int from = lineStart(text, start);
            int to = lineEnd(text, start);
            start = from;
            end = to;
        }
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        String sel = text.subSequence(start, end).toString();
        String out;
        switch (mode) {
            case UPPER:
                out = sel.toUpperCase();
                break;
            case LOWER:
                out = sel.toLowerCase();
                break;
            case TITLE:
                out = toTitle(sel);
                break;
            default:
                out = toSentence(sel);
                break;
        }
        text.replace(start, end, out);
        editor.setSelection(start + out.length());
    }

    public static String toTitle(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean wordStart = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(wordStart ? Character.toUpperCase(c) : Character.toLowerCase(c));
                wordStart = false;
            } else {
                sb.append(c);
                wordStart = Character.isWhitespace(c) || c == '-' || c == '_';
            }
        }
        return sb.toString();
    }

    public static String toSentence(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (start && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                start = false;
            } else {
                sb.append(Character.toLowerCase(c));
                if (c == '.' || c == '!' || c == '?') start = true;
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------------------
    // block insertion
    // ---------------------------------------------------------------------------------

    /** Inserts a block with blank lines around it and puts the caret inside when possible. */
    public static void insertBlock(EditText editor, String block, int caretOffsetFromStart) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        int len = text.length();
        String prefix;
        if (start == 0) {
            prefix = "";
        } else if (text.charAt(start - 1) != '\n') {
            prefix = "\n\n";
        } else if (start >= 2 && text.charAt(start - 2) != '\n') {
            prefix = "\n";
        } else {
            prefix = "";
        }
        String suffix;
        if (end >= len) {
            suffix = block.endsWith("\n") ? "" : "\n";
        } else if (text.charAt(end) != '\n') {
            suffix = "\n\n";
        } else if (end + 1 < len && text.charAt(end + 1) != '\n') {
            suffix = "\n";
        } else {
            suffix = "";
        }
        text.replace(start, end, prefix + block + suffix);
        int caret = start + prefix.length()
                + (caretOffsetFromStart >= 0
                ? Math.min(caretOffsetFromStart, block.length()) : block.length());
        editor.setSelection(Math.max(0, Math.min(caret, text.length())));
    }

    /** Inserts text at the caret exactly (no extra blank lines). */
    public static void insertText(EditText editor, String value) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        text.replace(start, end, value);
        editor.setSelection(start + value.length());
    }

    /** Wraps the selection in a link, or inserts a link template. */
    public static void insertLink(EditText editor, String label, String url) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        String selected = text.subSequence(start, end).toString();
        String textPart = label != null && label.length() > 0 ? label
                : (selected.length() > 0 ? selected : "link text");
        String urlPart = url == null ? "" : url;
        String insertion = "[" + textPart + "](" + urlPart + ")";
        text.replace(start, end, insertion);
        if (urlPart.length() == 0) {
            int caret = start + insertion.length() - 1;
            editor.setSelection(caret);
        } else {
            editor.setSelection(start + insertion.length());
        }
    }

    /** Wraps the selection in a fenced code block with an optional language tag. */
    public static void insertCodeBlock(EditText editor, String language) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        String selected = text.subSequence(start, end).toString();
        String lang = language == null ? "" : language;
        String block = "```" + lang + "\n" + selected + (selected.endsWith("\n") ? "" : "\n") + "```";
        boolean inline = !selected.contains("\n") && selected.length() > 0
                && !selected.contains("`");
        if (inline && lang.length() == 0) {
            text.replace(start, end, "`" + selected + "`");
            editor.setSelection(start + selected.length() + 2);
            return;
        }
        insertBlock(editor, block, selected.length() == 0 ? 3 + lang.length() + 1 : -1);
    }

    // ---------------------------------------------------------------------------------
    // document helpers
    // ---------------------------------------------------------------------------------

    /** Normalises whitespace and structure without touching semantics. */
    public static String tidy(String markdown) {
        if (markdown == null) return "";
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder sb = new StringBuilder(markdown.length() + 32);
        int blankRun = 0;
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) inFence = !inFence;
            if (!inFence) {
                int end = line.length();
                int trailing = 0;
                while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
                    if (line.charAt(end - 1) == ' ') trailing++;
                    end--;
                }
                boolean hardBreak = trailing == 2 && end > 0;
                line = hardBreak ? line.substring(0, end + 2) : line.substring(0, end);
                trimmed = line.trim();
                if (trimmed.length() == 0) {
                    blankRun++;
                    if (blankRun > 1) continue;
                    sb.append('\n');
                    continue;
                }
                blankRun = 0;
                if (trimmed.startsWith("#") && sb.length() > 0
                        && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
            }
            sb.append(line).append('\n');
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n'
                && sb.length() > 1 && sb.charAt(sb.length() - 2) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        return sb.toString();
    }

    /** Removes trailing spaces/tabs on every line, outside code fences. */
    public static String stripTrailingWhitespace(String markdown) {
        String[] lines = markdown.split("\n", -1);
        StringBuilder sb = new StringBuilder(markdown.length());
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) inFence = !inFence;
            if (!inFence) {
                int end = line.length();
                while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) end--;
                line = line.substring(0, end);
            }
            sb.append(line);
            if (i < lines.length - 1) sb.append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------------------
    // caret helpers
    // ---------------------------------------------------------------------------------

    public static int lineStart(CharSequence text, int offset) {
        int i = Math.min(offset, text.length()) - 1;
        while (i >= 0 && text.charAt(i) != '\n') i--;
        return i + 1;
    }

    public static int lineEnd(CharSequence text, int offset) {
        int i = Math.max(0, Math.min(offset, text.length()));
        while (i < text.length() && text.charAt(i) != '\n') i++;
        return i;
    }

    public static int lineNumber(CharSequence text, int offset) {
        int line = 1;
        int end = Math.min(offset, text.length());
        for (int i = 0; i < end; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    public static int offsetForLine(CharSequence text, int line) {
        if (line <= 1) return 0;
        int current = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                current++;
                if (current == line) return i + 1;
            }
        }
        return text.length();
    }

    public static String spaces(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(' ');
        return sb.toString();
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static void replaceSelection(EditText editor, String value) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0 || end < 0) return;
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        text.replace(start, end, value);
        editor.setSelection(start + value.length());
    }
}
