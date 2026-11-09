package com.markultra.editor.md;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task list support: finds the {@code - [ ]} markers a document contains and flips one
 * of them, which is what a tap on a checkbox in the preview has to do to the source.
 *
 * <p>Order matters. The checkboxes in the rendered page are numbered in document order,
 * so {@code index} here means "the n-th task marker in the Markdown", counting markers
 * inside block quotes and nested lists too. Pure Java, no Android imports: the rule is
 * unit tested headlessly.</p>
 */
public final class MdTasks {

    /** {@code [-*+] [ ]} at the start of a line, optionally inside quotation markers. */
    private static final Pattern TASK = Pattern.compile(
            "(?m)^([ \\t]*(?:>[ \\t]*)*[-*+][ \\t]+\\[)([ xX])(\\])");

    private MdTasks() {
    }

    /** A single task marker: absolute offsets of the state character. */
    public static final class Task {
        public final int boxCharIndex;
        public final boolean checked;

        Task(int boxCharIndex, boolean checked) {
            this.boxCharIndex = boxCharIndex;
            this.checked = checked;
        }
    }

    /** Every task marker in the document, in order. */
    public static List<Task> find(String markdown) {
        List<Task> tasks = new ArrayList<Task>();
        if (markdown == null || markdown.length() == 0) return tasks;
        Matcher m = TASK.matcher(markdown);
        while (m.find()) {
            char state = m.group(2).charAt(0);
            tasks.add(new Task(m.start(2), state == 'x' || state == 'X'));
        }
        return tasks;
    }

    public static int count(String markdown) {
        return find(markdown).size();
    }

    /** The task at {@code index}, or {@code null} when the document has no such task. */
    public static Task taskAt(String markdown, int index) {
        if (markdown == null || index < 0) return null;
        List<Task> tasks = find(markdown);
        return index < tasks.size() ? tasks.get(index) : null;
    }

    /**
     * Returns the document with task {@code index} set to {@code checked}, or
     * {@code null} when there is no such task or it already has that state.
     */
    public static String toggle(String markdown, int index, boolean checked) {
        if (markdown == null) return null;
        List<Task> tasks = find(markdown);
        if (index < 0 || index >= tasks.size()) return null;
        Task task = tasks.get(index);
        if (task.checked == checked) return null;
        int at = task.boxCharIndex;
        if (at < 0 || at >= markdown.length()) return null;
        char replacement = checked ? 'x' : ' ';
        return markdown.substring(0, at) + replacement + markdown.substring(at + 1);
    }

    /** How many of the tasks are done, for the status line. */
    public static int doneCount(String markdown) {
        List<Task> tasks = find(markdown);
        int done = 0;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).checked) done++;
        }
        return done;
    }
}
