package com.markultra.editor.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/** Small shared UI helpers. */
public final class UiUtil {

    private UiUtil() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static void toast(Context context, String message) {
        if (context == null || message == null) return;
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void toast(Context context, int resId) {
        if (context == null) return;
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
    }

    public static void copy(Context context, String text, String label) {
        ClipboardManager manager =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || text == null) return;
        manager.setPrimaryClip(ClipData.newPlainText(label == null ? "MarkUltra" : label, text));
    }

    public static String clipboard(Context context) {
        ClipboardManager manager =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) return "";
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return "";
        CharSequence text = clip.getItemAt(0).coerceToText(context);
        return text == null ? "" : text.toString();
    }

    public static boolean looksLikeUrl(String text) {
        if (text == null) return false;
        String t = text.trim();
        return (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("mailto:"))
                && t.indexOf(' ') < 0 && t.length() < 400;
    }

    /** Opens web, mail and phone URIs; returns false when nothing can handle them. */
    public static boolean openUri(Context context, String url) {
        if (context == null || url == null || url.trim().length() == 0) return false;
        String target = url.trim();
        try {
            if (target.startsWith("#")) return false;
            if (!target.contains("://") && !target.startsWith("mailto:")
                    && !target.startsWith("tel:")) {
                target = "https://" + target;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void shareText(Context context, String subject, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            context.startActivity(Intent.createChooser(intent,
                    context.getString(android.R.string.copy)));
        } catch (Exception ignored) {
        }
    }

    public static void haptic(View view) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    public static String relativeTime(long millis) {
        long delta = System.currentTimeMillis() - millis;
        if (delta < 60000L) return "just now";
        if (delta < 3600000L) return (delta / 60000L) + " min ago";
        if (delta < 86400000L) return (delta / 3600000L) + " h ago";
        if (delta < 604800000L) return (delta / 86400000L) + " d ago";
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(millis));
    }

    public static int blend(int from, int to, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        int a = (int) (((from >>> 24) & 0xFF) * (1 - r) + ((to >>> 24) & 0xFF) * r);
        int rr = (int) (((from >> 16) & 0xFF) * (1 - r) + ((to >> 16) & 0xFF) * r);
        int gg = (int) (((from >> 8) & 0xFF) * (1 - r) + ((to >> 8) & 0xFF) * r);
        int bb = (int) ((from & 0xFF) * (1 - r) + (to & 0xFF) * r);
        return (a << 24) | (rr << 16) | (gg << 8) | bb;
    }
}
