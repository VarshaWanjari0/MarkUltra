package com.markultra.editor.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One searchable list used for three things: the command palette, the block inserter and
 * the slash menu. Typing filters on both the title and the hint, so "table" finds the
 * table block, "pdf" finds the export command and "h2" finds the heading.
 */
public final class QuickPanel {

    /** One row: what it says, what it does. */
    public static final class Item {
        public final String title;
        public final String hint;
        public final Runnable action;

        public Item(String title, String hint, Runnable action) {
            this.title = title;
            this.hint = hint == null ? "" : hint;
            this.action = action;
        }
    }

    private QuickPanel() {
    }

    public static void show(final Activity activity, CharSequence title, List<Item> items) {
        show(activity, title, null, items);
    }

    /**
     * Shows the panel.
     *
     * @param query pre-filled search text (the slash menu passes nothing, the palette can
     *              pass the word the user started typing)
     */
    public static void show(final Activity activity, CharSequence title, String query,
                            final List<Item> items) {
        if (activity == null || activity.isFinishing()) return;
        final Context ctx = activity;

        final int pad = dp(ctx, 16);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(ctx, 4), 0, 0);

        final EditText search = new EditText(ctx);
        search.setSingleLine(true);
        search.setHint("Search commands and blocks");
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setPadding(pad, dp(ctx, 8), pad, dp(ctx, 8));
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ListView list = new ListView(ctx);
        list.setDivider(null);
        list.setDividerHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(list, lp);

        final RowAdapter adapter = new RowAdapter(ctx, items);
        list.setAdapter(adapter);
        if (query != null && query.length() > 0) {
            search.setText(query);
            search.setSelection(search.getText().length());
            adapter.filter(query);
        }

        final AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
            }
        });

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Item item = adapter.visible(position);
                dialog.dismiss();
                if (item != null && item.action != null) item.action.run();
            }
        });
        search.requestFocus();
    }

    // ------------------------------------------------------------------ adapter

    private static final class RowAdapter extends BaseAdapter {
        private final Context ctx;
        private final List<Item> all;
        private final List<Item> visible = new ArrayList<Item>();

        RowAdapter(Context ctx, List<Item> items) {
            this.ctx = ctx;
            if (items != null) all = items;
            else all = new ArrayList<Item>();
            visible.addAll(all);
        }

        void filter(String query) {
            visible.clear();
            if (query == null || query.trim().length() == 0) {
                visible.addAll(all);
            } else {
                String q = query.trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < all.size(); i++) {
                    Item item = all.get(i);
                    if (item.title.toLowerCase(Locale.ROOT).contains(q)
                            || item.hint.toLowerCase(Locale.ROOT).contains(q)) {
                        visible.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        Item visible(int position) {
            if (position < 0 || position >= visible.size()) return null;
            return visible.get(position);
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public Object getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView hint;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                title = (TextView) row.getChildAt(0);
                hint = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.VERTICAL);
                int pad = dp(ctx, 16);
                row.setPadding(pad, dp(ctx, 10), pad, dp(ctx, 10));
                title = new TextView(ctx);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                title.setTextColor(primary());
                hint = new TextView(ctx);
                hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                hint.setTextColor(secondary());
                row.addView(title);
                row.addView(hint);
                TextView spacer = new TextView(ctx);
                spacer.setHeight(dp(ctx, 1));
            }
            Item item = visible.get(position);
            title.setText(item.title);
            if (item.hint.length() == 0) {
                hint.setVisibility(View.GONE);
            } else {
                hint.setVisibility(View.VISIBLE);
                hint.setText(item.hint);
            }
            boolean emphasised = item.hint.length() == 0;
            title.setTypeface(null, emphasised ? Typeface.BOLD : Typeface.NORMAL);
            row.setGravity(Gravity.START);
            return row;
        }

        private int primary() {
            TypedValue v = new TypedValue();
            if (ctx.getTheme().resolveAttribute(android.R.attr.textColorPrimary, v, true)
                    && v.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(ctx, v.resourceId);
            }
            return v.data;
        }

        private int secondary() {
            TypedValue v = new TypedValue();
            if (ctx.getTheme().resolveAttribute(android.R.attr.textColorSecondary, v, true)
                    && v.resourceId != 0) {
                return androidx.core.content.ContextCompat.getColor(ctx, v.resourceId);
            }
            return v.data;
        }
    }

    private static int dp(Context ctx, float value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
