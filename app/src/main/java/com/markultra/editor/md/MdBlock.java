package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.List;

/**
 * A single block-level node of a parsed Markdown document.
 * Mutable public fields keep allocation low and the code short.
 */
public final class MdBlock {

    public static final int PARAGRAPH = 0;
    public static final int HEADING = 1;
    public static final int CODE = 2;
    public static final int QUOTE = 3;
    public static final int LIST_ITEM = 4;
    public static final int TABLE = 5;
    public static final int HR = 6;
    public static final int HTML = 7;
    public static final int IMAGE = 8;
    public static final int FOOTNOTE = 9;
    /** Display maths: the {@code $$ ... $$} block, stored as LaTeX in {@link #text}. */
    public static final int MATH = 10;
    /** {@code <details>} block: {@link #text} is the summary, children are the body. */
    public static final int DETAILS = 11;

    public int type;

    /** Heading level (1-6), quote nesting depth (1+) or list nesting (1+). */
    public int level = 1;

    /** True for ordered list items. */
    public boolean ordered;
    /** Ordinal for ordered items (1 based). */
    public int number = 1;
    /** Task-list syntax detected. */
    public boolean checkbox;
    /** Task-list state when {@link #checkbox}. */
    public boolean checked;

    /** Inline Markdown source for paragraphs, headings and list items. */
    public String text = "";
    /** Fence info string for code blocks, alt text for standalone images. */
    public String lang = "";
    /** Link target for standalone images. */
    public String url = "";
    /** Footnote identifier for footnote definitions. */
    public String footnoteId = "";
    /** Table payload for {@link #TABLE}. */
    public MdTableData table;

    /** Character offsets into the original document (inclusive/exclusive). */
    public int srcStart;
    public int srcEnd;
    /** 1-based line numbers (inclusive). */
    public int lineStart;
    public int lineEnd;

    /** Nested blocks (quote contents, list item children). */
    public final List<MdBlock> children = new ArrayList<MdBlock>(4);

    public MdBlock(int type) {
        this.type = type;
    }

    public boolean isContainer() {
        return type == QUOTE || type == LIST_ITEM;
    }

    /** Plain text of the block without children (used for search and TOC). */
    public String rawText() {
        if (type == CODE) return text;
        if (type == TABLE && table != null) return table.toPlainText();
        return text;
    }

    @Override
    public String toString() {
        return "MdBlock{type=" + type + ", level=" + level + ", text='" + text + "'}";
    }
}
