package com.markultra.editor.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.markultra.editor.R;
import com.markultra.editor.editor.CodeEditor;
import com.markultra.editor.editor.SmartEdit;
import com.markultra.editor.io.FileController;
import com.markultra.editor.md.GfmHtml;
import com.markultra.editor.md.MdLint;
import com.markultra.editor.md.MdStats;
import com.markultra.editor.md.MdToc;
import com.markultra.editor.prefs.ThemeController;
import com.markultra.editor.util.UiUtil;

import java.util.ArrayList;
import java.util.List;

/** Factory for every dialog in the app, so screens stay free of construction noise. */
public final class Dialogs {

    public interface OnJump {
        void onJump(int line, int offset);
    }

    public interface OnText {
        void onText(String value);
    }

    private Dialogs() {
    }

    /**
     * Every dialog inherits `materialAlertDialogTheme` from the app theme, so the
     * palette, corner radius and button colours come from one place instead of a
     * per-dialog style override.
     */
    /**
     * Every dialog in the app comes from here.
     *
     * <p>The theme configures {@code materialAlertDialogTheme}, so the builder has to be
     * the Material one: an AppCompat {@code AlertDialog.Builder} would look for
     * {@code alertDialogTheme}, inflate a layout whose ids do not match the Material
     * overlay it was given, and crash while installing its content. One factory means
     * that pairing can never drift again.</p>
     */
    public static AlertDialog.Builder builder(Activity activity) {
        return new MaterialAlertDialogBuilder(activity);
    }

    // ---------------------------------------------------------------------------------
    // prompts
    // ---------------------------------------------------------------------------------

    /**
     * Two-field prompt used for links and images.
     */
    public static void prompt(final Activity activity, ThemeController.Colors colors,
                              String title, String label1, String value1,
                              String label2, String value2, String positive,
                              final boolean urlSecond, final OnTextPair callback) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_prompt, null, false);
        TextView titleView = view.findViewById(R.id.prompt_title);
        TextView labelOne = view.findViewById(R.id.prompt_label1);
        final EditText inputOne = view.findViewById(R.id.prompt_input1);
        TextView labelTwo = view.findViewById(R.id.prompt_label2);
        final EditText inputTwo = view.findViewById(R.id.prompt_input2);

        titleView.setText(title);
        titleView.setTextColor(colors.text);
        labelOne.setText(label1);
        labelTwo.setText(label2);
        labelOne.setTextColor(colors.textMuted);
        labelTwo.setTextColor(colors.textMuted);
        inputOne.setText(value1);
        inputTwo.setText(value2);
        if (urlSecond) inputTwo.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);

        final AlertDialog dialog = builder(activity)
                .setView(view)
                .setPositiveButton(positive, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) {
                    callback.onText(inputOne.getText().toString().trim(),
                            inputTwo.getText().toString().trim());
                }
                dialog.dismiss();
            }
        });
        inputOne.requestFocus();
    }

    public interface OnTextPair {
        void onText(String first, String second);
    }

    /** Single-line prompt that returns a string. */
    public static void inputPrompt(final Activity activity, ThemeController.Colors colors,
                                   String title, String hint, String initial,
                                   final OnText callback) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setText(initial);
        input.setTextColor(colors.text);
        input.setHintTextColor(colors.textMuted);
        input.setSingleLine(true);
        int pad = UiUtil.dp(activity, 20);
        input.setPadding(pad, UiUtil.dp(activity, 10), pad, UiUtil.dp(activity, 10));
        final AlertDialog dialog = builder(activity)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) callback.onText(input.getText().toString());
                dialog.dismiss();
            }
        });
        input.requestFocus();
    }

    public static void confirm(Activity activity, String title, String message,
                               String positive, final Runnable onConfirm) {
        builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (onConfirm != null) onConfirm.run();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // language picker for code blocks
    // ---------------------------------------------------------------------------------

    private static final String[] LANGUAGES = {
            "text", "java", "kotlin", "python", "javascript", "typescript", "json", "xml",
            "html", "css", "bash", "sql", "c", "cpp", "csharp", "go", "rust", "php",
            "ruby", "swift", "yaml", "markdown", "diff", "dockerfile", "makefile"
    };

    public static void languagePicker(final Activity activity, ThemeController.Colors colors,
                                      final OnText callback) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtil.dp(activity, 18);
        container.setPadding(pad, UiUtil.dp(activity, 10), pad, UiUtil.dp(activity, 6));

        final TextView title = new TextView(activity);
        title.setText(R.string.dialog_code_lang);
        title.setTextSize(17f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(colors.text);
        container.addView(title);

        final EditText custom = new EditText(activity);
        custom.setHint(R.string.dialog_code_lang_hint);
        custom.setSingleLine(true);
        custom.setTextColor(colors.text);
        custom.setHintTextColor(colors.textMuted);
        container.addView(custom);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] holder = new AlertDialog[1];
        for (int i = 0; i < LANGUAGES.length; i++) {
            final String language = LANGUAGES[i];
            TextView row = new TextView(activity);
            row.setText(language);
            row.setTextSize(15f);
            row.setTextColor(colors.text);
            row.setPadding(UiUtil.dp(activity, 8), UiUtil.dp(activity, 12),
                    UiUtil.dp(activity, 8), UiUtil.dp(activity, 12));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (callback != null) callback.onText(language);
                    if (holder[0] != null) holder[0].dismiss();
                }
            });
            list.addView(row);
        }
        scroll.addView(list);
        container.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiUtil.dp(activity, 240)));

        holder[0] = builder(activity)
                .setView(container)
                .setPositiveButton(R.string.use_this, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (callback != null) callback.onText(custom.getText().toString().trim());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // navigation
    // ---------------------------------------------------------------------------------

    public static void goToLine(final Activity activity, final CodeEditor editor,
                                ThemeController.Colors colors) {
        inputPrompt(activity, colors, activity.getString(R.string.dialog_goto),
                activity.getString(R.string.dialog_goto), "", new OnText() {
                    @Override
                    public void onText(String value) {
                        try {
                            int line = Integer.parseInt(value.trim());
                            int offset = SmartEdit.offsetForLine(editor.getText(), Math.max(1, line));
                            editor.setSelection(Math.min(offset, editor.getText().length()));
                            editor.requestFocus();
                        } catch (NumberFormatException ignored) {
                        }
                    }
                });
    }

    public static void outline(final Activity activity, String markdown,
                               ThemeController.Colors colors, final OnJump onJump) {
        final List<MdToc.Entry> entries = MdToc.entries(markdown);
        if (entries.isEmpty()) {
            builder(activity)
                    .setTitle(R.string.outline_title)
                    .setMessage(R.string.outline_empty)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }
        String[] rows = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            MdToc.Entry e = entries.get(i);
            StringBuilder label = new StringBuilder();
            for (int k = 1; k < e.level; k++) label.append("   ");
            if (e.level > 1) label.append("\u2022 ");
            label.append(e.title);
            rows[i] = label.toString();
        }
        final AlertDialog dialog = builder(activity)
                .setTitle(R.string.outline_title)
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        MdToc.Entry e = entries.get(which);
                        if (onJump != null) onJump.onJump(e.line, e.offset);
                    }
                })
                .setNeutralButton(R.string.close, null)
                .create();
        dialog.show();
    }

    // ---------------------------------------------------------------------------------
    // statistics
    // ---------------------------------------------------------------------------------

    public static void stats(Activity activity, String markdown, ThemeController.Colors colors) {
        MdStats s = MdStats.of(markdown);
        String[][] rows = {
                {activity.getString(R.string.stats_words), String.valueOf(s.words)},
                {activity.getString(R.string.stats_chars), String.valueOf(s.characters)},
                {activity.getString(R.string.stats_chars_no_space), String.valueOf(s.charactersNoSpaces)},
                {activity.getString(R.string.stats_lines), String.valueOf(s.lines)},
                {activity.getString(R.string.stats_paragraphs), String.valueOf(s.paragraphs)},
                {activity.getString(R.string.stats_sentences), String.valueOf(s.sentences)},
                {activity.getString(R.string.stats_reading), MdStats.readLabel(s.readingSeconds())},
                {activity.getString(R.string.stats_speaking), MdStats.readLabel(s.speakingSeconds())},
                {activity.getString(R.string.stats_headings), String.valueOf(s.headings)},
                {activity.getString(R.string.stats_links), String.valueOf(s.links)},
                {activity.getString(R.string.stats_images), String.valueOf(s.images)},
                {activity.getString(R.string.stats_tables), String.valueOf(s.tables)},
                {activity.getString(R.string.stats_code), String.valueOf(s.codeBlocks)},
                {activity.getString(R.string.stats_tasks), s.todosDone + " / " + s.todos},
        };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.length; i++) {
            sb.append(rows[i][0]).append(":  ").append(rows[i][1]).append('\n');
        }
        TextView view = new TextView(activity);
        view.setText(sb.toString().trim());
        view.setTextSize(15f);
        view.setTextColor(colors.text);
        view.setLineSpacing(UiUtil.dp(activity, 6), 1f);
        int pad = UiUtil.dp(activity, 22);
        view.setPadding(pad, UiUtil.dp(activity, 12), pad, UiUtil.dp(activity, 8));
        builder(activity)
                .setTitle(R.string.menu_stats)
                .setView(view)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // lint / structure checks
    // ---------------------------------------------------------------------------------

    public static void issues(final Activity activity, String markdown,
                              ThemeController.Colors colors, final OnJump onJump) {
        final MdLint.Report report = MdLint.check(markdown);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = UiUtil.dp(activity, 20);
        container.setPadding(pad, UiUtil.dp(activity, 10), pad, UiUtil.dp(activity, 6));

        TextView summary = new TextView(activity);
        summary.setText(activity.getString(R.string.issues_summary,
                report.errors(), report.warnings()));
        summary.setTextColor(colors.textMuted);
        summary.setTextSize(12f);
        container.addView(summary);

        if (report.issues.isEmpty()) {
            TextView clean = new TextView(activity);
            clean.setText(R.string.issues_clean);
            clean.setTextColor(colors.checkDone);
            clean.setPadding(0, UiUtil.dp(activity, 18), 0, UiUtil.dp(activity, 18));
            clean.setTextSize(15f);
            container.addView(clean);
        } else {
            ScrollView scroll = new ScrollView(activity);
            LinearLayout list = new LinearLayout(activity);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < report.issues.size(); i++) {
                final MdLint.Issue issue = report.issues.get(i);
                TextView row = new TextView(activity);
                SpannableString text = new SpannableString("L" + issue.line + "  " + issue.message);
                int color = issue.severity == MdLint.ERROR ? 0xFFE53935
                        : (issue.severity == MdLint.WARNING ? colors.accent : colors.textMuted);
                text.setSpan(new ForegroundColorSpan(color), 0, ("L" + issue.line).length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new StyleSpan(Typeface.BOLD), 0, ("L" + issue.line).length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                row.setText(text);
                row.setTextSize(14f);
                row.setTextColor(colors.text);
                row.setPadding(0, UiUtil.dp(activity, 10), 0, UiUtil.dp(activity, 10));
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (onJump != null) onJump.onJump(issue.line, -1);
                    }
                });
                list.addView(row);
            }
            scroll.addView(list);
            container.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiUtil.dp(activity, 260)));
        }

        builder(activity)
                .setTitle(R.string.issues_title)
                .setView(container)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // recents
    // ---------------------------------------------------------------------------------

    public static void recents(final Activity activity, final FileController files,
                               final OnText callback, final Runnable onCleared) {
        final List<FileController.Recent> list = files.recents();
        if (list.isEmpty()) {
            builder(activity)
                    .setTitle(R.string.menu_recent)
                    .setMessage(R.string.recent_empty)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }
        String[] rows = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            FileController.Recent r = list.get(i);
            rows[i] = r.name + "\n" + UiUtil.relativeTime(r.time);
        }
        final AlertDialog dialog = builder(activity)
                .setTitle(R.string.menu_recent)
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (callback != null) callback.onText(list.get(which).uri);
                    }
                })
                .setNeutralButton(R.string.clear_recents, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        files.clearRecents();
                        if (onCleared != null) onCleared.run();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
    }

    // ---------------------------------------------------------------------------------
    // cheat sheet (rendered with our own engine)
    // ---------------------------------------------------------------------------------

    private static final String CHEATSHEET =
            "# Markdown cheat sheet\n\n"
                    + "## Text\n\n"
                    + "- `**bold**` -> **bold**\n"
                    + "- `*italic*` -> *italic*\n"
                    + "- `***both***` -> ***both***\n"
                    + "- `~~strike~~` -> ~~strike~~\n"
                    + "- `==mark==` -> ==mark==\n"
                    + "- `` `code` `` -> `code`\n\n"
                    + "## Structure\n\n"
                    + "- `# H1` ... `###### H6`\n"
                    + "- `> quote` for a block quote\n"
                    + "- `---` for a horizontal rule\n"
                    + "- `- item` or `1. item` for lists\n"
                    + "- `- [ ] task` and `- [x] done`\n\n"
                    + "## Links and images\n\n"
                    + "- `[text](https://example.com)`\n"
                    + "- `[text](url \"title\")`\n"
                    + "- `![alt](image.png)`\n"
                    + "- `<https://autolink.example>`\n"
                    + "- `[ref]` with `[ref]: https://example.com`\n\n"
                    + "## Tables\n\n"
                    + "```\n| Left | Center | Right |\n|:-----|:------:|------:|\n| a    | b      | c     |\n```\n\n"
                    + "## Code\n\n"
                    + "````\n```java\nint answer = 42;\n```\n````\n\n"
                    + "## Footnotes\n\n"
                    + "`Text with a note[^1]` plus `[^1]: The note.`\n\n"
                    + "## Extras\n\n"
                    + "- Hard break: two spaces at the end of a line\n"
                    + "- Escape any marker with a backslash: `\\*not italic\\*`\n"
                    + "- Front matter and `<details>` blocks stay as plain text\n";

    /**
     * The cheat sheet is rendered by the same pipeline as the preview, inside a WebView,
     * so what the user learns here is exactly what the preview will show.
     */
    public static void cheatSheet(Activity activity, ThemeController.Colors colors) {
        WebView view = new WebView(activity);
        WebSettings ws = view.getSettings();
        ws.setJavaScriptEnabled(false);
        ws.setBlockNetworkImage(true);
        view.setBackgroundColor(colors.background);

        GfmHtml.Options options = new GfmHtml.Options();
        options.dark = colors.dark;
        options.background = hex(colors.background);
        options.foreground = hex(colors.text);
        options.muted = hex(colors.textMuted);
        options.accent = hex(colors.accent);
        options.borderColor = hex(colors.divider);
        options.surfaceColor = hex(colors.surfaceAlt);
        options.fontScale = 0.9f;
        // assets are unreachable inside a dialog WebView, and the cheat sheet uses none
        String html = GfmHtml.page(CHEATSHEET, options).replace(GfmHtml.ASSET_BASE, "");
        view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);

        FrameLayout container = new FrameLayout(activity);
        container.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        AlertDialog dialog = builder(activity)
                .setTitle(R.string.cheatsheet_title)
                .setView(container)
                .setPositiveButton(R.string.close, null)
                .create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int height = Math.round(
                    activity.getResources().getDisplayMetrics().heightPixels * 0.72f);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height);
        }
    }

    private static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    // ---------------------------------------------------------------------------------
    // about
    // ---------------------------------------------------------------------------------

    public static void about(Activity activity, ThemeController.Colors colors, String version) {
        String body = activity.getString(R.string.about_body)
                + "\n\n" + activity.getString(R.string.settings_version) + ": " + version
                + "\n" + activity.getString(R.string.settings_offline);
        builder(activity)
                .setTitle(R.string.about_title)
                .setMessage(body)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    // ---------------------------------------------------------------------------------
    // generic action list (overflow sheet)
    // ---------------------------------------------------------------------------------

    public static final class Action {
        public final String label;
        public final Runnable run;
        public final boolean checked;
        public final boolean checkable;

        public Action(String label, Runnable run) {
            this(label, run, false, false);
        }

        public Action(String label, Runnable run, boolean checked, boolean checkable) {
            this.label = label;
            this.run = run;
            this.checked = checked;
            this.checkable = checkable;
        }
    }

    public static void actions(final Activity activity, String title,
                               final List<Action> actions) {
        String[] labels = new String[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            Action a = actions.get(i);
            labels[i] = a.checkable ? ((a.checked ? "\u2713  " : "\u2003  ") + a.label) : a.label;
        }
        builder(activity)
                .setTitle(title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Runnable run = actions.get(which).run;
                        if (run != null) run.run();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
