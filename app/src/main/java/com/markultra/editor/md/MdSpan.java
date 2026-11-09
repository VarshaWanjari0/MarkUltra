package com.markultra.editor.md;

/**
 * A single inline styling instruction produced by {@link MdInlineParser}.
 * Offsets are relative to the plain-text produced by the parser (markers removed).
 * Pure Java: no Android dependency, so it can be unit-tested on a desktop JVM.
 */
public final class MdSpan {

    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int STRIKE = 3;
    public static final int CODE = 4;
    public static final int LINK = 5;
    public static final int IMAGE = 6;
    public static final int HIGHLIGHT = 7;
    public static final int AUTOLINK = 8;
    public static final int FOOTNOTE = 9;
    public static final int MATH = 10;

    public final int type;
    public int start;
    public int end;
    /** Link/image target, or the footnote id. Never null ("" when unused). */
    public final String url;

    public MdSpan(int type, int start, int end, String url) {
        this.type = type;
        this.start = start;
        this.end = end;
        this.url = url == null ? "" : url;
    }

    public int length() {
        return end - start;
    }

    @Override
    public String toString() {
        return "MdSpan{t=" + type + ", " + start + ".." + end + ", url=" + url + '}';
    }
}
