package com.markultra.editor.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.markultra.editor.R;
import com.markultra.editor.md.MdTableData;
import com.markultra.editor.prefs.ThemeController;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive table builder: drag a grid, pick an alignment, optionally name the
 * columns, or paste rows from a spreadsheet / CSV to import them straight away.
 */
public final class TableInsertDialog {

    public interface OnTable {
        void onTable(String markdown, int caretOffsetFromStart);
    }

    private TableInsertDialog() {
    }

    public static void show(final Activity activity, final ThemeController.Colors colors,
                            final OnTable callback) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.dialog_table_picker, null, false);

        final TableGridView grid = view.findViewById(R.id.table_grid);
        final TextView sizeLabel = view.findViewById(R.id.table_size_label);
        final EditText headers = view.findViewById(R.id.table_headers);
        final EditText paste = view.findViewById(R.id.table_paste);
        final TextView alignNone = view.findViewById(R.id.align_none);
        final TextView alignLeft = view.findViewById(R.id.align_left);
        final TextView alignCenter = view.findViewById(R.id.align_center);
        final TextView alignRight = view.findViewById(R.id.align_right);

        grid.setColors(colors.accent, colors.textMuted);
        final int[] alignment = {MdTableData.ALIGN_NONE};
        final TextView[] chips = {alignNone, alignLeft, alignCenter, alignRight};

        updateSizeLabel(sizeLabel, grid.selectedRows(), grid.selectedColumns());
        grid.setOnSizePicked(new TableGridView.OnSizePicked() {
            @Override
            public void onSizePicked(int rows, int columns) {
                updateSizeLabel(sizeLabel, rows, columns);
            }
        });

        View.OnClickListener alignClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = v.getId();
                if (id == R.id.align_left) alignment[0] = MdTableData.ALIGN_LEFT;
                else if (id == R.id.align_center) alignment[0] = MdTableData.ALIGN_CENTER;
                else if (id == R.id.align_right) alignment[0] = MdTableData.ALIGN_RIGHT;
                else alignment[0] = MdTableData.ALIGN_NONE;
                for (int i = 0; i < chips.length; i++) {
                    boolean selected = (i == 0 && alignment[0] == MdTableData.ALIGN_NONE)
                            || (i == 1 && alignment[0] == MdTableData.ALIGN_LEFT)
                            || (i == 2 && alignment[0] == MdTableData.ALIGN_CENTER)
                            || (i == 3 && alignment[0] == MdTableData.ALIGN_RIGHT);
                    chips[i].setBackgroundResource(selected
                            ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
                    chips[i].setTextColor(selected ? colors.accent : colors.textMuted);
                }
            }
        };
        alignNone.setOnClickListener(alignClick);
        alignLeft.setOnClickListener(alignClick);
        alignCenter.setOnClickListener(alignClick);
        alignRight.setOnClickListener(alignClick);
        alignNone.setBackgroundResource(R.drawable.bg_chip_selected);
        alignNone.setTextColor(colors.accent);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setPositiveButton(R.string.insert, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pasted = paste.getText().toString().trim();
                String markdown;
                int caret;
                if (pasted.length() > 0) {
                    markdown = fromPastedText(pasted, alignment[0]);
                    caret = 2;
                } else {
                    markdown = fromGrid(grid.selectedColumns(), grid.selectedRows(),
                            headers.getText().toString(), alignment[0], 2);
                    caret = 2;
                }
                if (callback != null) callback.onTable(markdown, caret);
                dialog.dismiss();
            }
        });
    }

    private static void updateSizeLabel(TextView label, int rows, int columns) {
        label.setText(label.getContext().getString(R.string.dialog_table_size, columns, rows));
    }

    /** Builds an empty table with optional header labels. */
    static String fromGrid(int columns, int rows, String headerInput, int alignment, int bodyRows) {
        MdTableData data = new MdTableData();
        List<String> labels = splitLabels(headerInput);
        for (int c = 0; c < columns; c++) {
            String label = c < labels.size() && labels.get(c).length() > 0
                    ? labels.get(c) : "Column " + (c + 1);
            data.headers.add(label);
        }
        for (int c = 0; c < columns; c++) data.alignment.add(alignment);
        for (int r = 1; r < rows; r++) {
            String[] row = new String[columns];
            for (int c = 0; c < columns; c++) row[c] = " ";
            data.rows.add(row);
        }
        return data.toMarkdown();
    }

    /** Parses pasted delimiter-separated text (tabs, commas, semicolons or pipes). */
    static String fromPastedText(String text, int alignment) {
        String[] lines = text.split("\n");
        char separator = detectSeparator(text);
        MdTableData data = new MdTableData();
        List<String[]> parsed = new ArrayList<String[]>();
        int columns = 1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().length() == 0) continue;
            if (isMarkdownDelimiterRow(line)) continue;
            List<String> cells = splitLine(line, separator);
            columns = Math.max(columns, cells.size());
            parsed.add(cells.toArray(new String[0]));
        }
        if (parsed.isEmpty()) return fromGrid(3, 3, "", alignment, 2);
        String[] head = parsed.remove(0);
        for (int c = 0; c < columns; c++) {
            data.headers.add(c < head.length && head[c].length() > 0 ? head[c] : "Column " + (c + 1));
            data.alignment.add(alignment);
        }
        for (int r = 0; r < parsed.size(); r++) {
            String[] source = parsed.get(r);
            String[] row = new String[columns];
            for (int c = 0; c < columns; c++) row[c] = c < source.length ? source[c] : "";
            data.rows.add(row);
        }
        if (data.rows.isEmpty()) {
            String[] empty = new String[columns];
            for (int c = 0; c < columns; c++) empty[c] = " ";
            data.rows.add(empty);
        }
        return data.toMarkdown();
    }

    private static boolean isMarkdownDelimiterRow(String line) {
        String t = line.trim();
        if (t.length() == 0) return false;
        int dashes = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '-') dashes++;
            else if (c != '|' && c != ':' && c != ' ' && c != '\t') return false;
        }
        return dashes >= 2;
    }

    private static char detectSeparator(String text) {
        int tabs = count(text, '\t');
        int commas = count(text, ',');
        int semis = count(text, ';');
        int pipes = count(text, '|');
        if (tabs > 0 && tabs >= commas && tabs >= semis && tabs >= pipes) return '\t';
        if (pipes > 0 && pipes >= commas && pipes >= semis) return '|';
        if (semis > commas) return ';';
        return ',';
    }

    private static int count(String text, char c) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == c) n++;
        return n;
    }

    private static List<String> splitLine(String line, char separator) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == separator && !quoted) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static List<String> splitLabels(String input) {
        List<String> labels = new ArrayList<String>();
        if (input == null) return labels;
        String[] parts = input.split(",");
        for (int i = 0; i < parts.length; i++) labels.add(parts[i].trim());
        return labels;
    }

    /** Convenience for other screens that need a starter table. */
    public static String simple(int columns, int rows) {
        return fromGrid(columns, rows, "", MdTableData.ALIGN_NONE, rows - 1);
    }

    private static Context ctx(View view) {
        return view.getContext();
    }
}
