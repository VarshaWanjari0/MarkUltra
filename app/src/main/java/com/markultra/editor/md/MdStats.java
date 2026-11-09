package com.markultra.editor.md;

import java.util.List;

/** Live document statistics for the status bar. */
public final class MdStats {

    public int words;
    public int characters;
    public int charactersNoSpaces;
    public int lines;
    public int paragraphs;
    public int sentences;
    public int headings;
    public int codeBlocks;
    public int tables;
    public int links;
    public int images;
    public int todos;
    public int todosDone;
    public int footnotes;
    public int quotes;
    public int listItems;

    public int readingSeconds() {
        return words <= 0 ? 0 : (int) Math.ceil(words / 220.0 * 60.0);
    }

    public int speakingSeconds() {
        return words <= 0 ? 0 : (int) Math.ceil(words / 130.0 * 60.0);
    }

    public int longestLine(MdBlockParser.Result parsed) {
        int max = 0;
        for (int i = 0; i < parsed.blocks.size(); i++) {
            MdBlock b = parsed.blocks.get(i);
            if (b.type == MdBlock.CODE) {
                String[] rows = b.text.split("\n", -1);
                for (int r = 0; r < rows.length; r++) max = Math.max(max, rows[r].length());
            }
        }
        return max;
    }

    /**
     * Status-bar text computed with a single O(n) pass and no Markdown parsing,
     * so it stays cheap enough to run while the user is typing.
     */
    public static String quickStatusLine(String markdown) {
        return quickStatusLine((CharSequence) markdown);
    }

    /**
     * Same count, read straight off the editor's {@code Editable}: no full copy of the
     * document is made, which is what keeps the status bar cheap on a megabyte file.
     */
    public static String quickStatusLine(CharSequence markdown) {
        CharSequence src = markdown == null ? "" : markdown;
        int words = 0;
        int lines = 1;
        boolean inWord = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '\n') lines++;
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (!inWord) {
                words++;
                inWord = true;
            }
        }
        StringBuilder sb = new StringBuilder(48);
        sb.append(words).append(words == 1 ? " word" : " words");
        sb.append(" \u00B7 ").append(src.length()).append(" chars");
        sb.append(" \u00B7 ").append(lines).append(lines == 1 ? " line" : " lines");
        int seconds = words <= 0 ? 0 : (int) Math.ceil(words / 220.0 * 60.0);
        if (seconds > 0) sb.append(" \u00B7 ").append(readLabel(seconds)).append(" read");
        return sb.toString();
    }

    public static MdStats of(String markdown) {
        MdBlockParser.Result parsed = MdBlockParser.parse(markdown);
        return of(markdown, parsed);
    }

    public static MdStats of(String markdown, MdBlockParser.Result parsed) {
        MdStats s = new MdStats();
        String src = markdown == null ? "" : markdown;
        s.characters = src.length();
        int noSpace = 0;
        boolean inWord = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '\n') s.lines++;
            if (!Character.isWhitespace(c)) {
                noSpace++;
                if (!inWord) {
                    s.words++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
            if (c == '.' || c == '!' || c == '?') s.sentences++;
        }
        s.charactersNoSpaces = noSpace;
        if (src.length() > 0 && !src.endsWith("\n")) s.lines++;

        List<MdBlock> blocks = parsed.blocks;
        for (int i = 0; i < blocks.size(); i++) {
            countBlock(s, blocks.get(i));
        }
        if (s.sentences == 0 && s.words > 0) s.sentences = 1;
        return s;
    }

    private static void countBlock(MdStats s, MdBlock b) {
        switch (b.type) {
            case MdBlock.PARAGRAPH:
                s.paragraphs++;
                countInline(s, b.text);
                break;
            case MdBlock.HEADING:
                s.headings++;
                countInline(s, b.text);
                break;
            case MdBlock.CODE:
                s.codeBlocks++;
                break;
            case MdBlock.TABLE:
                s.tables++;
                if (b.table != null) {
                    s.words += countWords(b.table.toPlainText());
                }
                break;
            case MdBlock.QUOTE:
                s.quotes++;
                for (int i = 0; i < b.children.size(); i++) countBlock(s, b.children.get(i));
                break;
            case MdBlock.LIST_ITEM:
                s.listItems++;
                if (b.checkbox) {
                    s.todos++;
                    if (b.checked) s.todosDone++;
                }
                countInline(s, b.text);
                for (int i = 0; i < b.children.size(); i++) countBlock(s, b.children.get(i));
                break;
            case MdBlock.FOOTNOTE:
                s.footnotes++;
                countInline(s, b.text);
                break;
            case MdBlock.IMAGE:
                s.images++;
                break;
            default:
                break;
        }
    }

    private static void countInline(MdStats s, String text) {
        if (text == null || text.length() == 0) return;
        InlineResult r = MdInlineParser.parse(text);
        for (int i = 0; i < r.spans.size(); i++) {
            MdSpan sp = r.spans.get(i);
            if (sp.type == MdSpan.LINK || sp.type == MdSpan.AUTOLINK) s.links++;
            else if (sp.type == MdSpan.IMAGE) s.images++;
        }
    }

    private static int countWords(String text) {
        if (text == null || text.length() == 0) return 0;
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (!inWord) {
                count++;
                inWord = true;
            }
        }
        return count;
    }

    /** Compact status line, e.g. "128 words - 2 min read - 14 lines". */
    public String statusLine() {
        StringBuilder sb = new StringBuilder(48);
        sb.append(words).append(words == 1 ? " word" : " words");
        if (characters > 0) sb.append(" \u00B7 ").append(characters).append(" chars");
        sb.append(" \u00B7 ").append(lines).append(lines == 1 ? " line" : " lines");
        int sec = readingSeconds();
        if (sec > 0) sb.append(" \u00B7 ").append(readLabel(sec)).append(" read");
        if (todos > 0) sb.append(" \u00B7 ").append(todosDone).append('/').append(todos).append(" tasks");
        return sb.toString();
    }

    public static String readLabel(int seconds) {
        if (seconds < 60) return "under 1 min";
        int min = Math.round(seconds / 60f);
        return min + " min";
    }
}
