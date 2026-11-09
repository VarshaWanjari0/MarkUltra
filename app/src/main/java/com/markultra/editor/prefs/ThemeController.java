package com.markultra.editor.prefs;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;


/**
 * Resolves the active palette (light / dark / AMOLED + accent) and produces the
 * colour sets consumed by the editor, the preview renderer and the chrome.
 */
public final class ThemeController {

    public static final class Palette {
        public final String id;
        public final String label;
        public final int light;
        public final int dark;

        Palette(String id, String label, int light, int dark) {
            this.id = id;
            this.label = label;
            this.light = light;
            this.dark = dark;
        }
    }

    /** Accent choices: twelve palettes, tuned for both themes. */
    public static final Palette[] PALETTES = {
            new Palette("violet", "Violet", 0xFF6C4CE0, 0xFFA78BFA),
            new Palette("blue", "Ocean", 0xFF1565C0, 0xFF64B5F6),
            new Palette("teal", "Teal", 0xFF00695C, 0xFF4DD0C4),
            new Palette("green", "Forest", 0xFF2E7D32, 0xFF81C784),
            new Palette("lime", "Lime", 0xFF558B2F, 0xFFC5E1A5),
            new Palette("amber", "Amber", 0xFF8D6E00, 0xFFFFD54F),
            new Palette("orange", "Sunset", 0xFFBF360C, 0xFFFFAB91),
            new Palette("pink", "Rose", 0xFFAD1457, 0xFFF48FB1),
            new Palette("red", "Crimson", 0xFFB71C1C, 0xFFEF9A9A),
            new Palette("indigo", "Indigo", 0xFF283593, 0xFF9FA8DA),
            new Palette("cyan", "Cyan", 0xFF00838F, 0xFF80DEEA),
            new Palette("slate", "Slate", 0xFF37474F, 0xFFB0BEC5),
    };

    /** Concrete colours for the current theme, ready to hand to views and spans. */
    public static final class Colors {
        public boolean dark;
        public boolean amoled;
        public int accent;
        public int onAccent;
        public int background;
        public int surface;
        public int surfaceAlt;
        public int text;
        public int textMuted;
        public int divider;
        public int gutter;
        public int caret;
        public int selection;
        public int lineHighlight;
        public int syntaxMarker;
        public int syntaxHeading;
        public int syntaxCode;
        public int syntaxLink;
        public int syntaxString;
        public int syntaxKeyword;
        public int syntaxNumber;
        public int syntaxComment;
        public int syntaxQuote;
        public int codeBackground;
        public int checkDone;
        public float textSizeSp;
        public float lineSpacing;
        public int fontIndex;

        public Typeface typeface() {
            switch (fontIndex) {
                case 1:
                    return Typeface.SERIF;
                case 2:
                    return Typeface.MONOSPACE;
                default:
                    return Typeface.SANS_SERIF;
            }
        }

        public String typefaceName() {
            switch (fontIndex) {
                case 1:
                    return "serif";
                case 2:
                    return "monospace";
                default:
                    return "sans-serif";
            }
        }
    }

    private final Settings settings;

    public ThemeController(Context context) {
        this.settings = Settings.get(context);
    }

    public static Palette palette(String id) {
        for (int i = 0; i < PALETTES.length; i++) {
            if (PALETTES[i].id.equals(id)) return PALETTES[i];
        }
        return PALETTES[0];
    }

    public static boolean isDarkMode(Context context, Settings settings) {
        String mode = settings.themeMode();
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    public Colors colors(Context context) {
        boolean dark = isDarkMode(context, settings);
        boolean amoled = dark && settings.isAmoled();
        Colors c = new Colors();
        c.dark = dark;
        c.amoled = amoled;
        Palette p = palette(settings.accent());
        c.accent = dark ? p.dark : p.light;
        c.onAccent = luminance(c.accent) > 0.55 ? 0xFF10131A : 0xFFFFFFFF;

        if (dark) {
            c.background = amoled ? 0xFF000000 : 0xFF0E1116;
            c.surface = amoled ? 0xFF050505 : 0xFF151922;
            c.surfaceAlt = amoled ? 0xFF0B0B0B : 0xFF1C222D;
            c.text = 0xFFE7EAF0;
            c.textMuted = 0xFF98A0AD;
            c.divider = amoled ? 0xFF1A1A1A : 0xFF262C36;
            c.gutter = 0xFF5E6673;
            c.caret = c.accent;
            c.selection = withAlpha(c.accent, 0x55);
            c.lineHighlight = 0x12FFFFFF;
            c.syntaxMarker = 0xFF5E6673;
            c.syntaxHeading = c.accent;
            c.syntaxCode = 0xFFE0B080;
            c.syntaxLink = 0xFF7FC4FF;
            c.syntaxString = 0xFF9CD67F;
            c.syntaxKeyword = 0xFFC792EA;
            c.syntaxNumber = 0xFFF0A868;
            c.syntaxComment = 0xFF6B7280;
            c.syntaxQuote = 0xFF9AA4B2;
            c.codeBackground = amoled ? 0xFF0A0A0A : 0xFF161A21;
            c.checkDone = 0xFF66BB6A;
        } else {
            c.background = 0xFFFBFBFD;
            c.surface = 0xFFFFFFFF;
            c.surfaceAlt = 0xFFF1F3F7;
            c.text = 0xFF1A1D23;
            c.textMuted = 0xFF5F6673;
            c.divider = 0xFFE2E6EE;
            c.gutter = 0xFFA8AFBB;
            c.caret = c.accent;
            c.selection = withAlpha(c.accent, 0x33);
            c.lineHighlight = 0x0A000000;
            c.syntaxMarker = 0xFFA8AFBB;
            c.syntaxHeading = c.accent;
            c.syntaxCode = 0xFF9A5B00;
            c.syntaxLink = 0xFF0969DA;
            c.syntaxString = 0xFF2F7D32;
            c.syntaxKeyword = 0xFF7B1FA2;
            c.syntaxNumber = 0xFFB25000;
            c.syntaxComment = 0xFF8C93A1;
            c.syntaxQuote = 0xFF5F6673;
            c.codeBackground = 0xFFF3F5F9;
            c.checkDone = 0xFF2E7D32;
        }
        c.textSizeSp = settings.baseSize();
        c.lineSpacing = settings.lineSpacing();
        c.fontIndex = settings.editorFont();
        return c;
    }


    /** Applies status/navigation bar tinting to match the palette. */
    public void applySystemBars(Activity activity, Colors c) {
        Window window = activity.getWindow();
        View decor = window.getDecorView();
        window.setStatusBarColor(c.background);
        window.setNavigationBarColor(c.background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        boolean lightIcons = c.dark;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController ctl = window.getInsetsController();
            if (ctl != null) {
                int appearance = lightIcons ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appearance |= lightIcons ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                }
                ctl.setSystemBarsAppearance(appearance,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            int flags = decor.getSystemUiVisibility();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (lightIcons) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (lightIcons) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static double luminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    public static int resolveAttrColor(Context context, int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)) return tv.data;
        return 0xFF888888;
    }

    /** Convenience: colour used for the "selected" state of a pill button. */
    public static int selectedPill(Colors c) {
        return withAlpha(c.accent, c.dark ? 0x33 : 0x1F);
    }

    public static int stringRes(boolean condition, int ifTrue, int ifFalse) {
        return condition ? ifTrue : ifFalse;
    }
}
