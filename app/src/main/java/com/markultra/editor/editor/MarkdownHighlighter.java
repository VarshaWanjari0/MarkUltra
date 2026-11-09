package com.markultra.editor.editor;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.widget.EditText;

import com.markultra.editor.prefs.ThemeController;

/**
 * Live Markdown syntax highlighting for the raw editor.
 *
 * <p>Works on the visible structure of every line: headings, quotes, list markers,
 * task boxes, rules, tables, fenced code and inline spans. Marker characters are
 * dimmed instead of hidden, so the text always round-trips byte-for-byte.</p>
 *
 * <p>Highlighting is <b>incremental</b>. Re-colouring the whole document on every
 * keystroke is what makes a large file feel heavy: on a 60 KB note that is thousands of
 * spans removed and re-added for one character, dozens of times a second. Here only the
 * lines the edit touched are rebuilt, and the fenced-code map is patched rather than
 * rescanned when the edit cannot have changed it.</p>
 */
public final class MarkdownHighlighter implements TextWatcher {

    /**
     * Above this size the span work is skipped (the text stays fully editable). The
     * incremental pass keeps typing cheap well past this point, but a document this
     * large is usually a log or a dump where colours are not worth the memory.
     */
    private static final int MAX_HIGHLIGHT_LENGTH = 600_000;

    /**
     * Above this size only the line structure is coloured - headings, lists, quotes,
     * rules and code blocks - and inline emphasis, code and links are left plain.
     * Every span a document carries has to be shifted by the text engine on every
     * keystroke, so a note with tens of thousands of them cannot feel light however
     * cleverly it is repainted. Line styles are a couple per line; inline styles are a
     * couple per word.
     */
    private static final int INLINE_SPAN_LIMIT = 400_000;

    /**
     * Span count above which a whole-document pass drops inline styling, and the lower
     * count that turns it back on. Every span is shifted by the text engine on every
     * keystroke, so density matters more than length: a short table-heavy file can be
     * as expensive as a long novel. The budget is measured on a real pass, so a
     * lightly marked-up megabyte keeps its colours.
     */
    private static final int SPAN_BUDGET = 6_000;

    private final EditText editor;
    private ThemeController.Colors colors;
    private boolean enabled = true;
    private boolean applying;

    /** Character range the pending edit touched, or -1 for "the whole document". */
    private int dirtyFrom = -1;
    private int dirtyTo = -1;
    private int changeStart;
    private int changeBefore;
    private int changeCount;
    /** True when the pending edit may have created, moved or destroyed a fence. */
    private boolean structuralChange = true;

    /** Fenced blocks of the document: [start, end) including both marker lines. */
    private int[] blockStarts = new int[8];
    private int[] blockEnds = new int[8];
    /** True for a block whose closing fence is missing (it runs to the end). */
    private boolean[] blockOpen = new boolean[8];
    private int blocks;
    private boolean fenceCacheValid;
    /** True once a pass has shown that inline styling would cost too many spans. */
    private boolean lean;
    /** Spans created by the pass in progress, when it covers the whole document. */
    private int spansAdded;
    private boolean countingSpans;
    /** Where the pending edit began, before the text changed. */
    private int editAt;

    public MarkdownHighlighter(EditText editor, ThemeController.Colors colors) {
        this.editor = editor;
        this.colors = colors;
    }

    public void setColors(ThemeController.Colors colors) {
        this.colors = colors;
        refresh();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            refresh();
        } else {
            clearAll(editor.getText());
        }
    }

    /** Rebuilds every span. Used on open, on a theme change and after a programmatic edit. */
    public void refresh() {
        if (!enabled) return;
        dirtyFrom = -1;
        dirtyTo = -1;
        fenceCacheValid = false;
        apply(editor.getText());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        changeStart = start;
        changeBefore = count;
        changeCount = after;
        structuralChange = touchesFenceLine(s, start, start + count);
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        changeStart = start;
        changeBefore = before;
        changeCount = count;
        if (!structuralChange) {
            structuralChange = touchesFenceLine(s, start, start + count);
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (!enabled || applying) return;
        // The replaced range is what has to be re-considered: text before `start` is
        // untouched, and text after it only moved.
        dirtyFrom = changeStart;
        dirtyTo = Math.min(s.length(), changeStart + changeCount);
        apply(s);
    }

    private void clearAll(Editable e) {
        if (e == null) return;
        applying = true;
        Object[] spans = e.getSpans(0, e.length(), Object.class);
        for (int i = 0; i < spans.length; i++) {
            if (owns(spans[i])) e.removeSpan(spans[i]);
        }
        applying = false;
    }

    /**
     * True for the span types this class creates. Anything else - the find bar's
     * highlight, an IME's composing region - is left alone.
     */
    private static boolean owns(Object span) {
        return span instanceof ForegroundColorSpan || span instanceof BackgroundColorSpan
                || span instanceof StyleSpan || span instanceof TypefaceSpan
                || span instanceof StrikethroughSpan;
    }

    private void apply(Editable e) {
        if (e == null || colors == null) return;
        if (e.length() > MAX_HIGHLIGHT_LENGTH) {
            clearAll(e);
            return;
        }
        applying = true;
        try {
            int length = e.length();
            boolean wholeDocument = dirtyFrom < 0;
            int from;
            int to;
            if (wholeDocument) {
                from = 0;
                to = length;
            } else {
                int start = Math.max(0, Math.min(dirtyFrom, length));
                int end = Math.max(start, Math.min(dirtyTo, length));
                from = lineStart(e, start);
                to = lineEnd(e, end);
                // one line of context on each side: a delimiter can close on the next
                // line, and a list marker can change how the line below reads
                from = lineStart(e, Math.max(0, from - 1));
                to = lineEnd(e, Math.min(length, to + 1));
            }
            int delta = wholeDocument ? 0 : changeCount - changeBefore;
            editAt = Math.max(0, Math.min(changeStart, length));
            dirtyFrom = -1;
            dirtyTo = -1;
            changeBefore = 0;
            changeCount = 0;

            // 1. keep the fenced-block map current
            if (wholeDocument || !fenceCacheValid) {
                if (wholeDocument) lean = predictLean(e, length);
                scanFences(e, length);
                fenceCacheValid = true;
                structuralChange = false;
            } else if (structuralChange) {
                structuralChange = false;
                int[] oldStarts = copy(blockStarts, blocks);
                int[] oldEnds = copy(blockEnds, blocks);
                int oldCount = blocks;
                scanFences(e, length);
                // If the blocks are the same ones as before, only the lines the user
                // touched can have changed. If they are not, everything after the first
                // block that moved is suspect: that is the half of the document whose
                // code state may have flipped, so it is repainted in one go. This only
                // happens on the keystroke that creates, moves or removes a fence.
                int first = firstDifference(oldStarts, oldEnds, oldCount, delta);
                if (first >= 0) {
                    from = Math.min(from, first);
                    to = length;
                }
            } else {
                // the edit cannot have moved a fence, only shifted the offsets after it
                shiftBlocks(editAt, delta);
            }

            // 2. grow the range over any span that reaches past it. A run of code lines
            //    is painted by one span, so repainting the middle of a listing would
            //    otherwise remove the colour from the lines that were not repainted.
            for (int pass = 0; pass < 4; pass++) {
                int grownFrom = from;
                int grownTo = to;
                Object[] all = e.getSpans(from, to, Object.class);
                for (int i = 0; i < all.length; i++) {
                    if (!owns(all[i])) continue;
                    int s0 = e.getSpanStart(all[i]);
                    int t0 = e.getSpanEnd(all[i]);
                    if (s0 >= to || t0 <= from) continue;
                    grownFrom = Math.min(grownFrom, s0);
                    grownTo = Math.max(grownTo, t0);
                }
                if (grownFrom == from && grownTo == to) break;
                from = grownFrom;
                to = grownTo;
            }

            // 3. drop only the spans in that range, then rebuild those lines
            Object[] spans = e.getSpans(from, to, Object.class);
            for (int i = 0; i < spans.length; i++) {
                if (!owns(spans[i])) continue;
                int s = e.getSpanStart(spans[i]);
                int t = e.getSpanEnd(spans[i]);
                if (s >= to || t <= from) continue;
                e.removeSpan(spans[i]);
            }

            // A dense document is coloured by line, not by word: headings, lists,
            // quotes, code blocks - and no inline emphasis, code or link styling.
            // Every inline span has to be shifted by the text engine on every key,
            // so density, not size, is what makes typing slow.
            boolean inlineStyles = !lean && length <= INLINE_SPAN_LIMIT;
            int lineStart = from;
            // consecutive code lines share one span: a hundred-line listing is then
            // painted with two spans instead of two hundred
            int codeStart = -1;
            int codeEnd = -1;
            while (lineStart <= to && lineStart < length) {
                int lineEnd = lineStart;
                while (lineEnd < length && e.charAt(lineEnd) != '\n') lineEnd++;
                if (lineEnd > to && lineStart > from) break;
                boolean code = isCodeLine(e, lineStart) && !isMarkerLine(e, lineStart);
                if (code) {
                    if (codeStart < 0) codeStart = lineStart;
                    codeEnd = lineEnd;
                } else if (codeStart >= 0) {
                    paintCode(e, codeStart, codeEnd);
                    codeStart = -1;
                }
                if (!code) highlightLine(e, lineStart, lineEnd, inlineStyles);
                lineStart = lineEnd + 1;
            }
            if (codeStart >= 0) paintCode(e, codeStart, codeEnd);
            countingSpans = false;
        } catch (RuntimeException ignored) {
            // never let highlighting break typing: fall back to a clean rebuild
            try {
                clearAll(e);
                fenceCacheValid = false;
            } catch (RuntimeException ignoredToo) {
                // nothing else to do; the text is still editable and intact
            }
        } finally {
            applying = false;
        }
    }

    /**
     * Decides up front whether a whole document can afford inline styling.
     *
     * <p>A span is not free: the text engine keeps them in sorted arrays, so adding one
     * costs a copy of the ones already there and shifting one costs the same on every
     * keystroke. A document covered in emphasis, inline code and links reaches tens of
     * thousands of spans, which is both slow to paint and slow to type in. Sampling the
     * first twenty thousand characters gives a good estimate of that density for the
     * price of one cheap pass, and a mis-estimate only changes how much colour the file
     * gets.</p>
     */
    private static boolean predictLean(CharSequence e, int length) {
        if (length <= 40_000) return false;
        int sample = Math.min(length, 20_000);
        int marks = 0;
        for (int i = 0; i < sample; i++) {
            char c = e.charAt(i);
            if (c == '`' || c == '*' || c == '[' || c == '_' || c == '~') marks++;
        }
        double perChar = marks / (double) sample;
        // A construct costs two to four spans (bold: the style plus its two markers,
        // inline code: colour, typeface, background plus markers), so counting marks
        // and multiplying by three is the safer estimate. It has to over-estimate:
        // choosing lean for a document that would have fitted costs colour, choosing
        // full for one that would not costs typing speed on every single key.
        return perChar * length * 3.0 > SPAN_BUDGET;
    }

    private static int[] copy(int[] source, int count) {
        int[] out = new int[count];
        System.arraycopy(source, 0, out, 0, count);
        return out;
    }

    /**
     * Offset of the first block that is not where it used to be, or -1 when the fence
     * structure is untouched. Old offsets after the edit have moved by {@code delta}.
     */
    private int firstDifference(int[] oldStarts, int[] oldEnds, int oldCount, int delta) {
        if (oldCount != blocks) return 0;
        for (int i = 0; i < oldCount; i++) {
            int start = oldStarts[i] >= editAt ? oldStarts[i] + delta : oldStarts[i];
            int end = oldEnds[i] >= editAt ? oldEnds[i] + delta : oldEnds[i];
            if (start != blockStarts[i] || end != blockEnds[i]) {
                return Math.min(start, blockStarts[i]);
            }
        }
        return -1;
    }

    /** First character of the line containing {@code at}. */
    private static int lineStart(CharSequence e, int at) {
        int i = Math.max(0, Math.min(at, e.length()));
        while (i > 0 && e.charAt(i - 1) != '\n') i--;
        return i;
    }

    /** Last character of the line containing {@code at}, not counting the newline. */
    private static int lineEnd(CharSequence e, int at) {
        int i = Math.max(0, Math.min(at, e.length()));
        while (i < e.length() && e.charAt(i) != '\n') i++;
        return i;
    }

    /**
     * True when any line fully or partly inside {@code from..to} could be a fence
     * marker. A fence is only ever created, moved or destroyed by touching one of
     * those lines, so this is the test that decides between patching the block map and
     * rebuilding it.
     */
    private static boolean touchesFenceLine(CharSequence s, int from, int to) {
        if (s == null) return true;
        int i = lineStart(s, from);
        int limit = Math.min(s.length(), Math.max(from, to));
        while (i <= limit) {
            int end = i;
            while (end < s.length() && s.charAt(end) != '\n') end++;
            int p = i;
            while (p < end && (s.charAt(p) == ' ' || s.charAt(p) == '\t')) p++;
            if (p < end && (s.charAt(p) == '`' || s.charAt(p) == '~')) return true;
            if (end >= s.length()) break;
            i = end + 1;
        }
        return false;
    }

    /**
     * Rebuilds the fenced-block map from the characters alone: no span is touched and
     * no line is copied, so a megabyte of prose costs one pass over the characters.
     */
    private void scanFences(Editable e, int length) {
        blocks = 0;
        int lineStart = 0;
        boolean inFence = false;
        char fenceChar = 0;
        int fenceRun = 0;
        int openStart = 0;
        while (lineStart <= length) {
            int lineEnd = lineStart;
            while (lineEnd < length && e.charAt(lineEnd) != '\n') lineEnd++;
            int first = lineStart;
            while (first < lineEnd && (e.charAt(first) == ' ' || e.charAt(first) == '\t')) first++;
            char c = first < lineEnd ? e.charAt(first) : 0;
            int run = 0;
            while (first + run < lineEnd && e.charAt(first + run) == c) run++;
            boolean fenceLine = (c == '`' || c == '~') && run >= 3;
            if (fenceLine) {
                int rest = first + run;
                boolean infoOnly = true;
                if (inFence) {
                    // a closing fence may only repeat the character, and may not be
                    // shorter than the opening one; anything else is content
                    for (int i = rest; i < lineEnd; i++) {
                        if (e.charAt(i) != ' ' && e.charAt(i) != '\t') {
                            infoOnly = false;
                            break;
                        }
                    }
                }
                if (!inFence) {
                    inFence = true;
                    fenceChar = c;
                    fenceRun = run;
                    openStart = lineStart;
                } else if (c == fenceChar && run >= fenceRun && infoOnly) {
                    inFence = false;
                    addBlock(openStart, lineEnd, false);
                }
            }
            if (lineEnd >= length) break;
            lineStart = lineEnd + 1;
        }
        if (inFence) addBlock(openStart, length, true);
    }

    private void addBlock(int start, int end, boolean open) {
        if (blocks == blockStarts.length) {
            int size = Math.max(8, blocks * 2);
            int[] a = new int[size];
            int[] b = new int[size];
            boolean[] c = new boolean[size];
            System.arraycopy(blockStarts, 0, a, 0, blocks);
            System.arraycopy(blockEnds, 0, b, 0, blocks);
            System.arraycopy(blockOpen, 0, c, 0, blocks);
            blockStarts = a;
            blockEnds = b;
            blockOpen = c;
        }
        blockStarts[blocks] = start;
        blockEnds[blocks] = end;
        blockOpen[blocks] = open;
        blocks++;
    }

    /** Moves the block offsets after a non-structural edit at {@code at}. */
    private void shiftBlocks(int at, int delta) {
        if (delta == 0) return;
        for (int i = 0; i < blocks; i++) {
            if (blockStarts[i] >= at) {
                blockStarts[i] += delta;
                blockEnds[i] += delta;
            } else if (blockEnds[i] >= at) {
                blockEnds[i] += delta;
            } else if (blockOpen[i]) {
                blockEnds[i] += delta;
            }
        }
    }

    /** Index of the block whose span contains {@code at}, or -1. */
    private int blockAt(int at) {
        int lo = 0;
        int hi = blocks - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (at < blockStarts[mid]) hi = mid - 1;
            else if (at > blockEnds[mid]) lo = mid + 1;
            else return mid;
        }
        return -1;
    }

    /** True when {@code at} is on an opening or closing fence line. */
    private boolean isMarkerLine(CharSequence e, int at) {
        int block = blockAt(at);
        if (block < 0) return false;
        int line = lineStart(e, at);
        if (line == blockStarts[block]) return true;
        return !blockOpen[block] && lineEnd(e, blockEnds[block]) == lineEnd(e, line);
    }

    /** True when the line containing {@code at} is code inside a fenced block. */
    private boolean isCodeLine(CharSequence e, int at) {
        int block = blockAt(at);
        if (block < 0) return false;
        return !isMarkerLine(e, at);
    }

    /** Paints a run of code lines with one colour span and one monospace span. */
    private void paintCode(Editable e, int start, int end) {
        if (end <= start) return;
        color(e, start, end, colors.syntaxCode);
        e.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (countingSpans) spansAdded++;
    }

    /** One line of the document, with the fence state that applies to it. */
    private void highlightLine(Editable e, int start, int end, boolean inlineStyles) {
        if (isMarkerLine(e, start)) {
            dim(e, start, end);
            return;
        }
        highlightStructure(e, start, end, e.subSequence(start, end).toString(), inlineStyles);
    }

    private void dim(Editable e, int start, int end) {
        color(e, start, end, colors.syntaxMarker);
        style(e, start, end, Typeface.NORMAL);
    }

    private void highlightStructure(Editable e, int start, int end, String line, boolean inlineStyles) {
        if (end <= start) return;
        String trimmed = line.trim();
        int firstNonSpace = 0;
        while (firstNonSpace < line.length() && (line.charAt(firstNonSpace) == ' '
                || line.charAt(firstNonSpace) == '\t')) {
            firstNonSpace++;
        }

        if (trimmed.startsWith("#")) {
            int hashes = 0;
            while (hashes < trimmed.length() && trimmed.charAt(hashes) == '#') hashes++;
            if (hashes >= 1 && hashes <= 6 && (hashes == trimmed.length()
                    || trimmed.charAt(hashes) == ' ')) {
                color(e, start, end, colors.syntaxHeading);
                e.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (countingSpans) spansAdded++;
                color(e, start + firstNonSpace, start + firstNonSpace + hashes, colors.syntaxMarker);
                if (inlineStyles) inline(e, start + firstNonSpace + hashes, end);
                return;
            }
        }
        if (trimmed.startsWith(">")) {
            int k = firstNonSpace;
            while (k < end - start && line.charAt(k) == '>') {
                color(e, start + k, start + k + 1, colors.syntaxMarker);
                k++;
                if (k < line.length() && line.charAt(k) == ' ') k++;
            }
            color(e, start + k, end, colors.syntaxQuote);
            e.setSpan(new StyleSpan(Typeface.ITALIC), start + k, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (countingSpans) spansAdded++;
            if (inlineStyles) inline(e, start + firstNonSpace, end);
            return;
        }
        if (isRule(trimmed)) {
            color(e, start, end, colors.syntaxMarker);
            return;
        }
        // list marker
        int m = 0;
        int i = firstNonSpace;
        boolean bullet = false;
        if (i < line.length() && (line.charAt(i) == '-' || line.charAt(i) == '*'
                || line.charAt(i) == '+') && i + 1 < line.length() && line.charAt(i + 1) == ' ') {
            bullet = true;
            m = 1;
        } else {
            int digits = 0;
            int j = i;
            while (j < line.length() && Character.isDigit(line.charAt(j))) {
                digits++;
                j++;
            }
            if (digits > 0 && j < line.length() && (line.charAt(j) == '.' || line.charAt(j) == ')')
                    && j + 1 < line.length() && line.charAt(j + 1) == ' ') {
                bullet = true;
                m = j - i + 1;
            }
        }
        if (bullet) {
            int markerStart = start + i;
            color(e, markerStart, markerStart + m, colors.accent);
            int after = i + m;
            while (after < line.length() && line.charAt(after) == ' ') after++;
            if (after + 2 < line.length() && line.charAt(after) == '[') {
                int close = line.indexOf(']', after);
                if (close > after && close - after <= 3) {
                    color(e, start + after, start + close + 1, colors.checkDone);
                    if (inlineStyles) inline(e, start + close + 1, end);
                    return;
                }
            }
            if (inlineStyles) inline(e, start + after, end);
            return;
        }
        if (trimmed.startsWith("|")) {
            for (int k = firstNonSpace; k < line.length(); k++) {
                if (line.charAt(k) == '|') color(e, start + k, start + k + 1, colors.syntaxMarker);
            }
            if (inlineStyles) inline(e, start, end);
            return;
        }
        if (inlineStyles) inline(e, start, end);
    }

    private static boolean isRule(String t) {
        if (t.length() < 3) return false;
        char c = t.charAt(0);
        if (c != '-' && c != '*' && c != '_') return false;
        int count = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == c) count++;
            else if (ch != ' ') return false;
        }
        return count >= 3;
    }

    /**
     * Inline constructs inside a single line.
     *
     * <p>In a lean document (see {@link #predictLean}) the marker characters keep their
     * normal colour: the emphasis, code and links are still styled, but the two or three
     * extra spans that only dim a comma-sized delimiter are not worth their share of the
     * per-keystroke cost.</p>
     */
    private void inline(Editable e, int from, int to) {
        int i = from;
        int guard = 0;
        while (i < to && guard++ < 4000) {
            char c = e.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '`') {
                int run = runLength(e, i, to, '`');
                int close = indexOfRun(e, i + run, to, '`', run);
                if (close > 0) {
                    if (!lean) {
                        color(e, i, i + run, colors.syntaxMarker);
                        color(e, close, close + run, colors.syntaxMarker);
                    }
                    e.setSpan(new TypefaceSpan("monospace"), i + run, close, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    color(e, i + run, close, colors.syntaxCode);
                    e.setSpan(new BackgroundColorSpan(colors.codeBackground), i + run, close,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (countingSpans) spansAdded += 2;
                    i = close + run;
                    continue;
                }
                i += run;
                continue;
            }
            if (c == '*' || c == '_') {
                int run = runLength(e, i, to, c);
                int close = indexOfRun(e, i + run, to, c, Math.min(2, run));
                if (close > i + run) {
                    int use = Math.min(2, run);
                    if (!lean) {
                        color(e, i, i + use, colors.syntaxMarker);
                        color(e, close, close + use, colors.syntaxMarker);
                    }
                    int style = use == 2 ? Typeface.BOLD : Typeface.ITALIC;
                    e.setSpan(new StyleSpan(style), i + use, close, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (countingSpans) spansAdded++;
                    i = close + use;
                    continue;
                }
                i += run;
                continue;
            }
            if (c == '~' && i + 1 < to && e.charAt(i + 1) == '~') {
                int close = indexOfRun(e, i + 2, to, '~', 2);
                if (close > 0) {
                    if (!lean) {
                        color(e, i, i + 2, colors.syntaxMarker);
                        color(e, close, close + 2, colors.syntaxMarker);
                    }
                    e.setSpan(new StrikethroughSpan(), i + 2, close, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (countingSpans) spansAdded++;
                    i = close + 2;
                    continue;
                }
                i += 2;
                continue;
            }
            if (c == '=' && i + 1 < to && e.charAt(i + 1) == '=') {
                int close = indexOfRun(e, i + 2, to, '=', 2);
                if (close > 0) {
                    if (!lean) {
                        color(e, i, i + 2, colors.syntaxMarker);
                        color(e, close, close + 2, colors.syntaxMarker);
                    }
                    e.setSpan(new BackgroundColorSpan(ThemeController.withAlpha(colors.accent, 0x44)),
                            i + 2, close, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    if (countingSpans) spansAdded++;
                    i = close + 2;
                    continue;
                }
                i += 2;
                continue;
            }
            if (c == '[') {
                int close = -1;
                for (int k = i + 1; k < to; k++) {
                    if (e.charAt(k) == ']') { close = k; break; }
                }
                if (close > i && close < to) {
                    if (!lean) {
                        color(e, i, i + 1, colors.syntaxMarker);
                        color(e, close, close + 1, colors.syntaxMarker);
                    }
                    color(e, i + 1, close, colors.syntaxLink);
                    if (close + 1 < to && e.charAt(close + 1) == '(') {
                        int paren = -1;
                        for (int k = close + 2; k < to; k++) {
                            if (e.charAt(k) == ')') { paren = k; break; }
                        }
                        if (paren > 0) {
                            if (!lean) {
                                color(e, close + 1, close + 2, colors.syntaxMarker);
                                color(e, paren, paren + 1, colors.syntaxMarker);
                            }
                            color(e, close + 2, paren, colors.syntaxMarker);
                            i = paren + 1;
                            continue;
                        }
                    }
                    i = close + 1;
                    continue;
                }
                i++;
                continue;
            }
            if ((c == 'h' || c == 'H') && i + 5 <= to) {
                // bare http(s) links are links in GFM and are coloured like them here
                boolean http = to - i >= 7 && e.charAt(i + 1) == 't' && e.charAt(i + 2) == 't'
                        && e.charAt(i + 3) == 'p' && e.charAt(i + 4) == ':';
                boolean https = to - i >= 8 && http && e.charAt(i + 5) == 's';
                if (https || http) {
                    int j = i;
                    while (j < to && !Character.isWhitespace(e.charAt(j))
                            && e.charAt(j) != '<' && e.charAt(j) != '>') {
                        j++;
                    }
                    color(e, i, j, colors.syntaxLink);
                    i = Math.max(j, i + 1);
                    continue;
                }
                i++;
                continue;
            }
            i++;
        }
    }

    private static int runLength(Editable e, int at, int to, char c) {
        int n = 0;
        while (at + n < to && e.charAt(at + n) == c) n++;
        return Math.max(1, n);
    }

    private static int indexOfRun(Editable e, int from, int to, char c, int run) {
        int i = from;
        while (i + run <= to) {
            if (e.charAt(i) == '\\') {
                i += 2;
                continue;
            }
            if (e.charAt(i) == c) {
                int n = 0;
                while (i + n < to && e.charAt(i + n) == c) n++;
                if (n >= run && i + n < to) return i;
                i += Math.max(1, n);
                continue;
            }
            i++;
        }
        return -1;
    }

    private void color(Editable e, int start, int end, int color) {
        if (start >= end || start < 0 || end > e.length() || colors == null) return;
        e.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (countingSpans) spansAdded++;
    }

    private void style(Editable e, int start, int end, int style) {
        if (start >= end || start < 0 || end > e.length()) return;
        e.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (countingSpans) spansAdded++;
    }
}
