package com.markultra.editor.editor;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Snapshot based undo/redo. The platform EditText exposes no public undo API, and a
 * full operational-transform stack would be overkill for a text editor, so we keep
 * coalesced full-text snapshots with hard memory caps.
 */
public final class UndoManager implements TextWatcher {

    private static final long COALESCE_MS = 600L;
    private static final int MAX_SNAPSHOTS = 40;
    private static final long MAX_TOTAL_CHARS = 6_000_000L;

    private static final class Snapshot {
        final String text;
        final int selStart;
        final int selEnd;

        Snapshot(String text, int selStart, int selEnd) {
            this.text = text;
            this.selStart = selStart;
            this.selEnd = selEnd;
        }
    }

    private final CodeEditor editor;
    private final Deque<Snapshot> undo = new ArrayDeque<Snapshot>();
    private final Deque<Snapshot> redo = new ArrayDeque<Snapshot>();
    private long lastPush;
    private long totalChars;
    private boolean suspended;

    public UndoManager(CodeEditor editor) {
        this.editor = editor;
        reset();
    }

    public void reset() {
        undo.clear();
        redo.clear();
        totalChars = 0;
        push(editor.getText().toString(), editor.getSelectionStart(), editor.getSelectionEnd(), true);
    }

    public void suspend(boolean suspended) {
        this.suspended = suspended;
    }

    public boolean canUndo() {
        return undo.size() > 1;
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void forceSnapshot() {
        push(editor.getText().toString(), editor.getSelectionStart(),
                editor.getSelectionEnd(), true);
    }

    private void push(String text, int selStart, int selEnd, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastPush < COALESCE_MS) return;
        Snapshot last = undo.peekLast();
        if (last != null && last.text.equals(text)) return;
        undo.addLast(new Snapshot(text, selStart, selEnd));
        totalChars += text.length();
        lastPush = now;
        while (undo.size() > MAX_SNAPSHOTS) {
            Snapshot removed = undo.pollFirst();
            if (removed != null) totalChars -= removed.text.length();
        }
        while (totalChars > MAX_TOTAL_CHARS && undo.size() > 2) {
            Snapshot removed = undo.pollFirst();
            if (removed != null) totalChars -= removed.text.length();
        }
    }

    public boolean undo() {
        if (!canUndo()) return false;
        Snapshot current = undo.pollLast();
        if (current != null) redo.addLast(current);
        Snapshot previous = undo.peekLast();
        if (previous == null) return false;
        apply(previous);
        lastPush = System.currentTimeMillis();
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        Snapshot next = redo.pollLast();
        if (next == null) return false;
        undo.addLast(next);
        apply(next);
        lastPush = System.currentTimeMillis();
        return true;
    }

    private void apply(Snapshot snapshot) {
        suspended = true;
        try {
            editor.setText(snapshot.text);
            int length = editor.getText().length();
            int start = Math.max(0, Math.min(snapshot.selStart, length));
            int end = Math.max(0, Math.min(snapshot.selEnd, length));
            editor.setSelection(Math.min(start, end), Math.max(start, end));
        } finally {
            suspended = false;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (suspended) return;
        // Coalescing is decided before the document is copied: inside a typing burst
        // there is nothing to record, and copying a megabyte per keystroke just to
        // throw it away is what a large note used to feel like.
        long now = System.currentTimeMillis();
        long window = s.length() > LARGE_DOCUMENT ? COALESCE_LARGE_MS : COALESCE_MS;
        if (now - lastPush < window) return;
        try {
            push(s.toString(), editor.getSelectionStart(), editor.getSelectionEnd(), false);
        } catch (RuntimeException ignored) {
            // running out of memory for history must never cost the user their text
        }
    }

    /** Above this size the history window widens so the copy cost stays rare. */
    private static final int LARGE_DOCUMENT = 200_000;
    /** Coalescing window used for documents over {@link #LARGE_DOCUMENT}. */
    private static final long COALESCE_LARGE_MS = 1500L;
}
