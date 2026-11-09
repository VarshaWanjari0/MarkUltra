package com.markultra.editor.editor;

/**
 * Line index for the editor.
 *
 * <p>Asking "which line is the caret on" by walking the document from character zero
 * costs a full pass over the file. On a megabyte note that happens on every keystroke -
 * once for the cursor label, once for the line-number gutter - and it is the single
 * biggest reason a large file feels sticky to type in.</p>
 *
 * <p>This class keeps the start offset of every line and splices it on each edit: only
 * the entries after the edit move, and typing at the end of a document (the common case)
 * costs nothing beyond the characters typed. A length mismatch, which is what an edit
 * made without a notification looks like, falls back to one rebuild.</p>
 */
public final class LineMap {

    private int[] starts = new int[256];
    /** Rebuild target for an edit: the same size as {@link #starts}, reused. */
    private int[] scratch = new int[256];
    /** Number of lines described; a document always has at least one. */
    private int count;
    /** Length of the text the map was built for, or -1 when it is not built yet. */
    private int length = -1;

    private int pendingStart;
    private int pendingRemovedNewlines;

    /** Rebuilds the whole map. Used on open and after a wholesale replacement. */
    public void build(CharSequence text) {
        int n = text == null ? 0 : text.length();
        ensureCapacity(n + 1);
        count = 1;
        starts[0] = 0;
        for (int i = 0; i < n; i++) {
            if (text.charAt(i) == '\n') {
                ensureCapacity(count + 1);
                starts[count++] = i + 1;
            }
        }
        length = n;
        pendingRemovedNewlines = 0;
        pendingStart = 0;
    }

    /** Records the range that is about to be replaced, before the text changes. */
    public void beforeChange(CharSequence old, int start, int removed) {
        // nothing to remember: the edit itself carries enough information
        pendingStart = start;
    }

    /**
     * Applies the edit to the index.
     *
     * <p>Position mapping is the whole trick: an offset at or before the edit keeps its
     * value, an offset at or after the replaced range moves by the length difference.
     * A mapped offset is a line start when it is zero or the character before it is a
     * newline, which is checked against the new text - so an inserted newline creates a
     * line and a deleted one merges two, without ever rescanning the document.</p>
     */
    public void afterChange(CharSequence now, int start, int removed, int inserted) {
        if (now == null) now = "";
        int n = now.length();
        if (length < 0 || length != n - inserted + removed) {
            // an edit we were not told about, or a replaced Editable: rebuild once
            build(now);
            return;
        }
        int changedAt = Math.max(0, Math.min(start, n));
        int removedEnd = changedAt + removed;
        int delta = inserted - removed;

        // 1. line starts up to and including the line the edit began in
        int head = lineOf(changedAt);
        int out = 0;
        for (int i = 0; i <= head; i++) scratch[out++] = starts[i];

        // 2. line starts created by the inserted text
        int insertEnd = Math.min(n, changedAt + inserted);
        for (int i = changedAt; i < insertEnd; i++) {
            if (now.charAt(i) == '\n' && i + 1 > scratch[out - 1]) scratch[out++] = i + 1;
        }

        // 3. line starts that survive after the edited range, shifted by the difference
        int tail = head;
        while (tail < count && starts[tail] <= changedAt) tail++;
        while (tail < count && starts[tail] < removedEnd) tail++;
        for (int i = tail; i < count; i++) {
            int mapped = starts[i] + delta;
            if (mapped <= scratch[out - 1]) continue;
            if (mapped == 0 || now.charAt(mapped - 1) == '\n') scratch[out++] = mapped;
        }
        count = out;
        for (int i = 0; i < out; i++) starts[i] = scratch[i];
        length = n;
        pendingStart = 0;
    }

    /** 1-based line number of {@code offset}. */
    public int lineNumberAt(CharSequence text, int offset) {
        check(text);
        return lineOf(offset) + 1;
    }

    /** Offset of the first character of the line containing {@code offset}. */
    public int lineStart(CharSequence text, int offset) {
        check(text);
        return starts[lineOf(offset)];
    }

    /** Offset of the first character of the line containing {@code offset}, ends excluded. */
    public int lineEnd(CharSequence text, int offset) {
        check(text);
        int line = lineOf(offset);
        int next = line + 1 < count ? starts[line + 1] - 1 : text.length();
        return Math.max(starts[line], next);
    }

    /** Offset of the first character of 1-based {@code line}, clamped to the text. */
    public int offsetForLine(CharSequence text, int line) {
        check(text);
        if (line <= 1) return 0;
        if (line > count) return text.length();
        return starts[line - 1];
    }

    public int lineCount(CharSequence text) {
        check(text);
        return count;
    }

    // ---------------------------------------------------------------------------------

    private void check(CharSequence text) {
        int n = text == null ? 0 : text.length();
        if (length != n) build(text);
    }

    private int lineOf(int offset) {
        int lo = 0;
        int hi = count - 1;
        int want = Math.max(0, offset);
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= want) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    private int firstStartAtOrAfter(int offset) {
        int lo = 0;
        int hi = count - 1;
        int want = Math.max(0, offset);
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (starts[mid] < want) lo = mid + 1; else hi = mid;
        }
        return starts[lo] < want ? count : lo;
    }

    private void ensureCapacity(int needed) {
        if (starts.length >= needed) return;
        int size = starts.length;
        while (size < needed) size <<= 1;
        int[] grown = new int[size];
        System.arraycopy(starts, 0, grown, 0, Math.min(starts.length, count));
        starts = grown;
        scratch = new int[size];
    }

    /** Line count of the last document this map saw; 0 before the first build. */
    public int knownLines() {
        return length < 0 ? 0 : count;
    }
}
