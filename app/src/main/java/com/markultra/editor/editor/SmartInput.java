package com.markultra.editor.editor;

import android.text.Editable;
import android.text.TextWatcher;

import com.markultra.editor.prefs.Settings;

/**
 * Soft-keyboard friendly smart input. IMEs deliver Enter and brackets as text
 * edits rather than key events, so both features are implemented as a TextWatcher.
 */
public final class SmartInput implements TextWatcher {

    /** Every opener and the closer it pairs with, index for index. */
    private static final String OPENERS = "([{\"'`";
    private static final String CLOSERS = ")]}\"'`";

    private final CodeEditor editor;
    private final Settings settings;
    private boolean busy;
    private boolean pendingNewline;
    private char pendingChar;
    private int pendingBefore;
    private int pendingCount;

    public SmartInput(CodeEditor editor, Settings settings) {
        this.editor = editor;
        this.settings = settings;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        pendingBefore = count;
        pendingCount = after;
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        pendingNewline = false;
        pendingChar = 0;
        if (busy || count != 1 || before != 0) return;
        int caret = editor.getSelectionStart();
        if (caret <= 0 || caret > s.length()) return;
        char inserted = s.charAt(caret - 1);
        if (inserted == '\n') {
            pendingNewline = true;
        } else if (settings.autoPair()
                && (OPENERS.indexOf(inserted) >= 0 || CLOSERS.indexOf(inserted) >= 0)) {
            // closers are handled too: typing ")" next to an auto-inserted ")" must
            // step over it, not insert a second one
            pendingChar = inserted;
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (busy) return;
        // Typing assistance is a convenience: if anything below fails, the character
        // the user typed must still land. Never let it escape into the IME.
        try {
            if (pendingNewline) {
                pendingNewline = false;
                continueList(s);
            } else if (pendingChar != 0) {
                char typed = pendingChar;
                pendingChar = 0;
                applyPair(s, typed);
            }
        } catch (RuntimeException e) {
            pendingNewline = false;
            pendingChar = 0;
        }
    }

    // ---------------------------------------------------------------------------------
    // list continuation
    // ---------------------------------------------------------------------------------

    private void continueList(Editable s) {
        if (!settings.autoLists()) return;
        int caret = editor.getSelectionStart();
        if (caret < 1) return;
        int lineStart = SmartEdit.lineStart(s, caret - 1);
        String lineBefore = s.subSequence(lineStart, caret - 1).toString();
        String continuation = SmartEdit.continuationFor(lineBefore);
        if (continuation == null) {
            autoIndent(s);
            return;
        }
        String content = lineBefore.substring(
                Math.min(lineBefore.length(), SmartEdit.markerPrefixLength(lineBefore)));
        busy = true;
        try {
            if (content.trim().length() == 0) {
                // empty item: drop the marker so the user can leave the list
                int from = lineStart;
                int to = caret - 1;
                s.delete(from, to);
                editor.setSelection(Math.min(from, s.length()));
            } else {
                s.insert(caret, continuation);
                editor.setSelection(Math.min(caret + continuation.length(), s.length()));
            }
        } finally {
            busy = false;
        }
    }

    private void autoIndent(Editable s) {
        if (!settings.autoIndent()) return;
        int caret = editor.getSelectionStart();
        if (caret < 2) return;
        int lineStart = SmartEdit.lineStart(s, caret - 1);
        String lineBefore = s.subSequence(lineStart, caret - 1).toString();
        int indent = 0;
        while (indent < lineBefore.length()
                && (lineBefore.charAt(indent) == ' ' || lineBefore.charAt(indent) == '\t')) {
            indent++;
        }
        if (indent == 0 || lineBefore.trim().length() == 0) return;
        String pad = lineBefore.substring(0, indent);
        busy = true;
        try {
            s.insert(caret, pad);
            editor.setSelection(Math.min(caret + pad.length(), s.length()));
        } finally {
            busy = false;
        }
    }

    // ---------------------------------------------------------------------------------
    // auto pairs
    // ---------------------------------------------------------------------------------

    private void applyPair(Editable s, char typed) {
        int caret = editor.getSelectionStart();
        if (caret < 1 || caret > s.length()) return;
        char prev = caret >= 2 ? s.charAt(caret - 2) : 0;
        char next = caret < s.length() ? s.charAt(caret) : 0;

        // typing a closer that already sits under the caret: step over it instead of
        // doubling it, so "()" + ")" stays "()" and the caret ends up past the bracket
        if (CLOSERS.indexOf(typed) >= 0 && caret < s.length()
                && s.charAt(caret - 1) == typed && s.charAt(caret) == typed) {
            busy = true;
            try {
                s.delete(caret - 1, caret);
            } finally {
                busy = false;
            }
            return;
        }
        // never double up on an existing closer
        if (next == typed && CLOSERS.indexOf(typed) >= 0) {
            busy = true;
            try {
                s.delete(caret - 1, caret);
                editor.setSelection(caret - 1);
            } finally {
                busy = false;
            }
            return;
        }
        // apostrophes inside words are contractions, not quotes
        if ((typed == '\'' || typed == '"') && isWordChar(prev)) return;
        // do not wrap when the previous character is itself a marker
        if (prev == '\\') return;
        // never add a pair when its closer already sits at the caret
        int closerIndex = OPENERS.indexOf(typed);
        if (closerIndex >= 0 && closerIndex < CLOSERS.length() && next == CLOSERS.charAt(closerIndex)) return;
        if (typed == '`' && prev == '`') return;

        int pairIndex = OPENERS.indexOf(typed);
        if (pairIndex < 0 || pairIndex >= CLOSERS.length()) return;
        String close = String.valueOf(CLOSERS.charAt(pairIndex));
        busy = true;
        try {
            s.insert(caret, close);
            editor.setSelection(caret);
        } finally {
            busy = false;
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
