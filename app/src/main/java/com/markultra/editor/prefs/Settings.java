package com.markultra.editor.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single source of truth for every user preference. Values are read from
 * SharedPreferences without extra allocation; defaults are chosen so the app
 * is pleasant on first launch with zero configuration.
 */
public final class Settings {

    public static final String KEY_THEME_MODE = "theme_mode";          // system|light|dark
    public static final String KEY_AMOLED = "amoled";
    public static final String KEY_ACCENT = "accent";
    public static final String KEY_BASE_SIZE = "base_size";
    public static final String KEY_LINE_SPACING = "line_spacing";
    public static final String KEY_EDITOR_FONT = "editor_font";        // sans|serif|mono
    public static final String KEY_MONO_CODE = "mono_code";
    public static final String KEY_MONO_TABLES = "mono_tables";
    public static final String KEY_LOAD_IMAGES = "load_images";
    public static final String KEY_SMART_QUOTES = "smart_quotes";
    public static final String KEY_AUTO_PAIR = "auto_pair";
    public static final String KEY_AUTO_LISTS = "auto_lists";
    public static final String KEY_AUTO_INDENT = "auto_indent";
    public static final String KEY_HIGHLIGHT = "highlight";
    public static final String KEY_LINE_NUMBERS = "line_numbers";
    public static final String KEY_TYPEWRITER = "typewriter";
    public static final String KEY_SPELLCHECK = "spellcheck";
    public static final String KEY_AUTOSAVE = "autosave";
    public static final String KEY_AUTOSAVE_DELAY = "autosave_delay";
    public static final String KEY_SOFT_WRAP = "soft_wrap";
    public static final String KEY_HAPTIC = "haptic";
    public static final String KEY_STATUS_BAR = "status_bar";
    public static final String KEY_FOCUS_MODE = "focus_mode";
    public static final String KEY_TAB_WIDTH = "tab_width";
    public static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    public static final String KEY_SCROLL_SYNC = "scroll_sync";
    public static final String KEY_TOC_MAX_LEVEL = "toc_max_level";
    public static final String KEY_PREVIEW_SMART = "preview_smart_typography";
    public static final String KEY_LAST_URI = "last_uri";
    public static final String KEY_LAST_NAME = "last_name";
    public static final String KEY_RECENTS = "recents";
    public static final String KEY_DRAFT = "draft_text";
    public static final String KEY_DRAFT_DIRTY = "draft_dirty";
    public static final String KEY_ONBOARDED = "onboarded";
    public static final String KEY_SORT_PANE = "sort_pane";
    public static final String KEY_RICH = "rich_markdown";
    public static final String KEY_PASTE_RICH = "paste_rich";
    public static final String KEY_REVISION = "settings_revision";

    private static Settings instance;

    private final SharedPreferences sp;

    private Settings(Context context) {
        this.sp = context.getApplicationContext()
                .getSharedPreferences("markultra", Context.MODE_PRIVATE);
    }

    public static synchronized Settings get(Context context) {
        if (instance == null) instance = new Settings(context);
        return instance;
    }

    public SharedPreferences raw() {
        return sp;
    }

    // ---- theme ------------------------------------------------------------------

    public String themeMode() {
        return sp.getString(KEY_THEME_MODE, "system");
    }

    public void setThemeMode(String mode) {
        sp.edit().putString(KEY_THEME_MODE, mode).apply();
    }

    public boolean isAmoled() {
        return sp.getBoolean(KEY_AMOLED, false);
    }

    public void setAmoled(boolean amoled) {
        sp.edit().putBoolean(KEY_AMOLED, amoled).apply();
    }

    public String accent() {
        return sp.getString(KEY_ACCENT, "violet");
    }

    public void setAccent(String accent) {
        sp.edit().putString(KEY_ACCENT, accent).apply();
    }

    public float baseSize() {
        return sp.getFloat(KEY_BASE_SIZE, 16f);
    }

    public void setBaseSize(float size) {
        sp.edit().putFloat(KEY_BASE_SIZE, size).apply();
    }

    public float lineSpacing() {
        return sp.getFloat(KEY_LINE_SPACING, 1.18f);
    }

    public void setLineSpacing(float value) {
        sp.edit().putFloat(KEY_LINE_SPACING, value).apply();
    }

    public int editorFont() {
        return sp.getInt(KEY_EDITOR_FONT, 0);
    }

    public void setEditorFont(int which) {
        sp.edit().putInt(KEY_EDITOR_FONT, which).apply();
    }

    // ---- booleans ---------------------------------------------------------------

    public boolean is(String key, boolean def) {
        return sp.getBoolean(key, def);
    }

    public void set(String key, boolean value) {
        sp.edit().putBoolean(key, value).apply();
    }

    /**
     * MarkUltra+ syntax on top of GFM: definition lists, admonitions, wiki links,
     * attributes, abbreviations, sub/superscript, inserts, asides, front matter,
     * mentions, issue references and media tags. Off means "render exactly like GitHub".
     */
    public boolean richMarkdown() {
        return sp.getBoolean(KEY_RICH, false);
    }

    public void setRichMarkdown(boolean value) {
        sp.edit().putBoolean(KEY_RICH, value).apply();
    }

    /** Convert HTML on the clipboard into Markdown when pasting. */
    public boolean pasteRich() {
        return sp.getBoolean(KEY_PASTE_RICH, true);
    }

    public void setPasteRich(boolean value) {
        sp.edit().putBoolean(KEY_PASTE_RICH, value).apply();
    }

    public boolean monoCode() {
        return sp.getBoolean(KEY_MONO_CODE, true);
    }

    public boolean monoTables() {
        return sp.getBoolean(KEY_MONO_TABLES, true);
    }

    /** Render images in the preview by fetching them (needs the INTERNET permission). */
    public boolean loadImages() {
        return sp.getBoolean(KEY_LOAD_IMAGES, true);
    }

    public void setLoadImages(boolean value) {
        sp.edit().putBoolean(KEY_LOAD_IMAGES, value).apply();
    }

    public boolean smartQuotes() {
        return sp.getBoolean(KEY_SMART_QUOTES, false);
    }

    public boolean autoPair() {
        return sp.getBoolean(KEY_AUTO_PAIR, true);
    }

    public boolean autoLists() {
        return sp.getBoolean(KEY_AUTO_LISTS, true);
    }

    public boolean autoIndent() {
        return sp.getBoolean(KEY_AUTO_INDENT, true);
    }

    public boolean highlight() {
        return sp.getBoolean(KEY_HIGHLIGHT, true);
    }

    public boolean lineNumbers() {
        return sp.getBoolean(KEY_LINE_NUMBERS, false);
    }

    public boolean typewriter() {
        return sp.getBoolean(KEY_TYPEWRITER, false);
    }

    public boolean spellcheck() {
        return sp.getBoolean(KEY_SPELLCHECK, false);
    }

    public boolean autosave() {
        return sp.getBoolean(KEY_AUTOSAVE, true);
    }

    public int autosaveDelay() {
        return getInt(KEY_AUTOSAVE_DELAY, 2500);
    }

    public boolean softWrap() {
        return sp.getBoolean(KEY_SOFT_WRAP, true);
    }

    public boolean haptic() {
        return sp.getBoolean(KEY_HAPTIC, true);
    }

    public boolean statusBar() {
        return sp.getBoolean(KEY_STATUS_BAR, true);
    }

    public boolean focusMode() {
        return sp.getBoolean(KEY_FOCUS_MODE, false);
    }

    public boolean keepScreenOn() {
        return sp.getBoolean(KEY_KEEP_SCREEN_ON, false);
    }

    public boolean scrollSync() {
        return sp.getBoolean(KEY_SCROLL_SYNC, true);
    }

    public boolean previewSmartTypography() {
        return sp.getBoolean(KEY_PREVIEW_SMART, true);
    }

    public int tocMaxLevel() {
        return getInt(KEY_TOC_MAX_LEVEL, 4);
    }

    public int tabWidth() {
        return getInt(KEY_TAB_WIDTH, 4);
    }

    public int getInt(String key, int def) {
        return sp.getInt(key, def);
    }

    public void setInt(String key, int value) {
        sp.edit().putInt(key, value).apply();
    }

    /** Bumped by the settings screen so open editors know to refresh. */
    public int revision() {
        return sp.getInt(KEY_REVISION, 0);
    }

    public void bumpRevision() {
        sp.edit().putInt(KEY_REVISION, revision() + 1).apply();
    }

    public void resetAll() {
        String keep = getString(KEY_DRAFT, "");
        String uri = getString(KEY_LAST_URI, "");
        String name = getString(KEY_LAST_NAME, "");
        sp.edit().clear().apply();
        sp.edit()
                .putString(KEY_DRAFT, keep)
                .putString(KEY_LAST_URI, uri)
                .putString(KEY_LAST_NAME, name)
                .putBoolean(KEY_ONBOARDED, true)
                .apply();
    }

    public String getString(String key, String def) {
        return sp.getString(key, def);
    }

    public void setString(String key, String value) {
        sp.edit().putString(key, value).apply();
    }
}
