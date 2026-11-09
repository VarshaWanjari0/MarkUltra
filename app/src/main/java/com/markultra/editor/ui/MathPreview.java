package com.markultra.editor.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.markultra.editor.R;
import com.markultra.editor.md.GfmHtml;
import com.markultra.editor.prefs.ThemeController;

/**
 * A live KaTeX field: type TeX, see the equation.
 *
 * <p>It is the same engine as the preview - the page is built by
 * {@link GfmHtml#page(String, GfmHtml.Options)} and loads KaTeX, its stylesheet and its
 * fonts from the APK - so what the dialog shows is exactly what the document will show,
 * down to the macros and the red error styling. No network, no second implementation.</p>
 */
public final class MathPreview {

    private MathPreview() {
    }

    /**
     * Opens the editor sheet.
     *
     * @param initial  TeX to start with, may be empty
     * @param display  true for a display equation, false for inline
     * @param onAccept receives the final TeX; the caller decides how it lands in the text
     */
    public static void show(final Activity activity, ThemeController.Colors colors,
                            String initial, boolean display, final OnMath onAccept) {
        if (activity == null || activity.isFinishing()) return;
        final boolean[] isDisplay = {display};

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        root.setPadding(pad, dp(activity, 8), pad, 0);

        final EditText input = new EditText(activity);
        input.setHint("\\frac{a}{b}");
        input.setText(initial == null ? "" : initial);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setTextColor(colors.text);
        input.setHintTextColor(colors.textMuted);
        root.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final WebView preview = new WebView(activity);
        preview.setBackgroundColor(colors.background);
        WebSettings ws = preview.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        // The page is local and comes from this app's own renderer. Its scripts are asked
        // for by name from the app's own origin, and answered by this client out of the
        // APK: blocking network loads would block them too.
        ws.setBlockNetworkLoads(false);
        preview.setWebViewClient(new com.markultra.editor.ui.AssetClient(activity));
        int height = dp(activity, 132);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));

        final AlertDialog dialog = Dialogs.builder(activity)
                .setTitle(R.string.math_title)
                .setView(root)
                .setPositiveButton(R.string.math_insert, null)
                .setNeutralButton(display ? R.string.math_inline : R.string.math_display, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();

        final Runnable render = new Runnable() {
            @Override
            public void run() {
                GfmHtml.Options o = new GfmHtml.Options();
                o.dark = colors.dark;
                String tex = input.getText().toString().trim();
                String markdown = tex.length() == 0
                        ? "$$\\textsf{type TeX above}$$"
                        : (isDisplay[0] ? "$$\n" + tex + "\n$$" : "$" + tex + "$");
                preview.loadDataWithBaseURL(GfmHtml.ASSET_BASE, GfmHtml.page(markdown, o),
                        "text/html", "utf-8", null);
            }
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                render.run();
            }
        });
        render.run();

        // the positive button is wired by hand so an empty field never inserts anything
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tex = input.getText().toString().trim();
                if (tex.length() == 0) return;
                dialog.dismiss();
                if (onAccept != null) onAccept.onMath(tex, isDisplay[0]);
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isDisplay[0] = !isDisplay[0];
                ((android.widget.Button) v).setText(isDisplay[0]
                        ? R.string.math_display : R.string.math_inline);
                render.run();
            }
        });
    }

    /** Receives the finished TeX. */
    public interface OnMath {
        void onMath(String tex, boolean display);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
