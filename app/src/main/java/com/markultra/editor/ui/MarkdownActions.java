package com.markultra.editor.ui;

import android.app.Activity;
import android.text.Editable;

import com.markultra.editor.R;
import com.markultra.editor.editor.CodeEditor;
import com.markultra.editor.editor.SmartEdit;
import com.markultra.editor.md.MdToc;
import com.markultra.editor.prefs.Settings;
import com.markultra.editor.prefs.ThemeController;
import com.markultra.editor.util.UiUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Every toolbar and menu command, in one testable place. Commands only mutate the
 * editor text; the host refreshes preview, stats and undo state afterwards.
 */
public final class MarkdownActions {

    public interface Host {
        void afterCommand();

        void toast(String message);
    }

    private final Activity activity;
    private final CodeEditor editor;
    private final ThemeController.Colors colors;
    private final Settings settings;
    private final Host host;

    public MarkdownActions(Activity activity, CodeEditor editor, ThemeController.Colors colors,
                           Settings settings, Host host) {
        this.activity = activity;
        this.editor = editor;
        this.colors = colors;
        this.settings = settings;
        this.host = host;
    }

    private void done() {
        if (host != null) host.afterCommand();
    }

    // ---------------------------------------------------------------------------------
    // inline formatting
    // ---------------------------------------------------------------------------------

    public void bold() {
        SmartEdit.toggleInline(editor, "**");
        done();
    }

    public void italic() {
        SmartEdit.toggleInline(editor, "*");
        done();
    }

    public void strike() {
        SmartEdit.toggleInline(editor, "~~");
        done();
    }

    public void highlight() {
        SmartEdit.toggleInline(editor, "==");
        done();
    }

    public void inlineCode() {
        SmartEdit.toggleInline(editor, "`");
        done();
    }

    // ---------------------------------------------------------------------------------
    // blocks
    // ---------------------------------------------------------------------------------

    public void heading() {
        SmartEdit.cycleHeading(editor);
        done();
    }

    public void headingLevel(int level) {
        SmartEdit.setHeading(editor, level);
        done();
    }

    public void bulletList() {
        SmartEdit.toggleLinePrefix(editor, "- ");
        done();
    }

    public void orderedList() {
        SmartEdit.toggleLinePrefix(editor, "1. ");
        done();
    }

    public void taskList() {
        SmartEdit.toggleLinePrefix(editor, "- [ ] ");
        done();
    }

    public void quote() {
        SmartEdit.toggleLinePrefix(editor, "> ");
        done();
    }

    public void horizontalRule() {
        SmartEdit.insertBlock(editor, "---", -1);
        done();
    }

    public void codeBlock() {
        Dialogs.languagePicker(activity, colors, new Dialogs.OnText() {
            @Override
            public void onText(String value) {
                SmartEdit.insertCodeBlock(editor, value == null ? "" : value.trim());
                done();
            }
        });
    }

    public void link() {
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        String selected = start >= 0 && end > start
                ? editor.getText().subSequence(Math.min(start, end), Math.max(start, end)).toString()
                : "";
        String clipboard = UiUtil.clipboard(activity);
        String url = UiUtil.looksLikeUrl(clipboard) ? clipboard.trim() : "";
        Dialogs.prompt(activity, colors, activity.getString(R.string.dialog_link_title),
                activity.getString(R.string.dialog_link_text), selected,
                activity.getString(R.string.dialog_link_url), url,
                activity.getString(R.string.insert), true, new Dialogs.OnTextPair() {
                    @Override
                    public void onText(String first, String second) {
                        SmartEdit.insertLink(editor, first, second);
                        done();
                    }
                });
    }

    public void image() {
        String clipboard = UiUtil.clipboard(activity);
        String url = UiUtil.looksLikeUrl(clipboard) || looksLikePath(clipboard)
                ? clipboard.trim() : "";
        Dialogs.prompt(activity, colors, activity.getString(R.string.dialog_image_title),
                activity.getString(R.string.dialog_image_alt), "",
                activity.getString(R.string.dialog_image_url), url,
                activity.getString(R.string.insert), true, new Dialogs.OnTextPair() {
                    @Override
                    public void onText(String first, String second) {
                        String alt = first.length() == 0 ? "image" : first;
                        SmartEdit.insertText(editor, "![" + alt + "](" + second + ")");
                        done();
                    }
                });
    }

    private static boolean looksLikePath(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.length() == 0 || t.indexOf('\n') >= 0) return false;
        String lower = t.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg");
    }

    public void table() {
        TableInsertDialog.show(activity, colors, new TableInsertDialog.OnTable() {
            @Override
            public void onTable(String markdown, int caretOffsetFromStart) {
                SmartEdit.insertBlock(editor, markdown, caretOffsetFromStart);
                done();
            }
        });
    }

    public void footnote() {
        Editable text = editor.getText();
        String full = text.toString();
        int next = 1;
        int guard = 0;
        while (full.contains("[^" + next + "]") && guard++ < 200) next++;
        String id = String.valueOf(next);
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0) start = text.length();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        text.replace(start, end, "[^" + id + "]");
        editor.setSelection(start + id.length() + 3);
        String definition = "\n\n[^" + id + "]: ";
        text.insert(text.length(), definition);
        done();
    }

    // ---------------------------------------------------------------------------------
    // insertions
    // ---------------------------------------------------------------------------------

    public void insertDate() {
        SmartEdit.insertText(editor,
                new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        done();
    }

    public void insertTime() {
        SmartEdit.insertText(editor,
                new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        done();
    }

    public void insertDateTime() {
        SmartEdit.insertText(editor,
                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
        done();
    }

    public void insertWordCount() {
        com.markultra.editor.md.MdStats stats =
                com.markultra.editor.md.MdStats.of(editor.getText().toString());
        String label = activity.getString(R.string.word_count_inserted, stats.words);
        SmartEdit.insertText(editor, " " + stats.words + " words ");
        if (host != null) host.toast(label);
        done();
    }

    public void insertFrontMatter() {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String block = "---\ntitle: Untitled\ndate: " + date + "\ntags: []\n---\n\n";
        Editable text = editor.getText();
        int start = Math.min(editor.getSelectionStart(), text.length());
        text.insert(Math.max(0, start), block);
        editor.setSelection(Math.min(text.length(), start + block.length()));
        done();
    }

    public void insertDetails() {
        SmartEdit.insertBlock(editor, "<details>\n<summary>Summary</summary>\n\nContent\n\n</details>", -1);
        done();
    }

    public void insertToc() {
        List<MdToc.Entry> entries = MdToc.entries(editor.getText().toString());
        if (entries.isEmpty()) {
            if (host != null) host.toast(activity.getString(R.string.outline_empty));
            return;
        }
        String toc = MdToc.toMarkdown(entries, Math.max(2, settings.tocMaxLevel()));
        SmartEdit.insertBlock(editor, toc.trim(), -1);
        done();
    }

    public void insertTemplate(final String name, final String body) {
        Dialogs.confirm(activity, activity.getString(R.string.menu_new), name,
                activity.getString(R.string.insert), new Runnable() {
                    @Override
                    public void run() {
                        Editable text = editor.getText();
                        text.replace(0, text.length(), body);
                        editor.setSelection(0);
                        done();
                    }
                });
    }

    // ---------------------------------------------------------------------------------
    // document operations
    // ---------------------------------------------------------------------------------

    public void tidy() {
        String tidy = SmartEdit.tidy(editor.getText().toString());
        replaceAll(tidy);
    }

    public void stripWhitespace() {
        String stripped = SmartEdit.stripTrailingWhitespace(editor.getText().toString());
        replaceAll(stripped);
    }

    private void replaceAll(String value) {
        Editable text = editor.getText();
        int caret = editor.getSelectionStart();
        text.replace(0, text.length(), value);
        editor.setSelection(Math.max(0, Math.min(caret, text.length())));
        done();
    }

    public void sortLines(boolean reverse) {
        SmartEdit.sortLines(editor, reverse);
        done();
    }

    public void duplicateLine() {
        SmartEdit.duplicateLines(editor);
        done();
    }

    public void deleteLine() {
        SmartEdit.deleteLines(editor);
        done();
    }

    public void moveLine(int direction) {
        SmartEdit.moveLines(editor, direction);
        done();
    }

    public void transformCase(SmartEdit.CaseMode mode) {
        SmartEdit.transformCase(editor, mode);
        done();
    }

    public void indent(boolean outdent) {
        SmartEdit.indentLines(editor, outdent, settings.tabWidth());
        done();
    }
}
