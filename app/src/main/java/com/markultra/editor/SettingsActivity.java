package com.markultra.editor;

import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.markultra.editor.prefs.Settings;
import com.markultra.editor.prefs.ThemeController;
import com.markultra.editor.ui.ColorSwatch;
import com.markultra.editor.util.UiUtil;

import java.util.Locale;

/** All preferences, live-applied to the editor once the screen is closed. */
public class SettingsActivity extends AppCompatActivity {

    private Settings settings;
    private ThemeController.Colors colors;

    private TextView[] themeTabs;
    private TextView[] fontTabs;
    private TextView sizeValue;
    private TextView spacingValue;
    private TextView tabWidthValue;
    private TextView autosaveValue;
    private GridLayout accentGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = Settings.get(this);
        ThemeController controller = new ThemeController(this);
        colors = controller.colors(this);
        super.onCreate(savedInstanceState);
        controller.applySystemBars(this, colors);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        ((ImageButton) findViewById(R.id.btn_back)).setColorFilter(colors.text);

        sizeValue = findViewById(R.id.size_value);
        spacingValue = findViewById(R.id.spacing_value);
        tabWidthValue = findViewById(R.id.tab_width_value);
        autosaveValue = findViewById(R.id.autosave_value);
        accentGrid = findViewById(R.id.accent_grid);

        setupThemeGroup();
        setupSwitches();
        setupSliders();
        setupFontGroup();
        setupAccents();

        TextView version = findViewById(R.id.about_version);
        version.setText(getString(R.string.settings_version) + " " + versionName());
        version.setTextColor(colors.textMuted);

        findViewById(R.id.reset_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.resetAll();
                settings.bumpRevision();
                UiUtil.toast(SettingsActivity.this, R.string.settings_reset_done);
                recreate();
            }
        });
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    // ---------------------------------------------------------------------------------
    // theme
    // ---------------------------------------------------------------------------------

    private void setupThemeGroup() {
        TextView system = findViewById(R.id.theme_system);
        TextView light = findViewById(R.id.theme_light);
        TextView dark = findViewById(R.id.theme_dark);
        themeTabs = new TextView[]{system, light, dark};

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String mode = v.getId() == R.id.theme_light ? "light"
                        : (v.getId() == R.id.theme_dark ? "dark" : "system");
                settings.setThemeMode(mode);
                settings.bumpRevision();
                refreshThemeTabs();
                recreate();
            }
        };
        system.setOnClickListener(listener);
        light.setOnClickListener(listener);
        dark.setOnClickListener(listener);
        refreshThemeTabs();

        SwitchCompat amoled = findViewById(R.id.switch_amoled);
        amoled.setChecked(settings.isAmoled());
        amoled.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                settings.setAmoled(isChecked);
                settings.bumpRevision();
            }
        });

        styleSwitch(amoled);
    }

    private void refreshThemeTabs() {
        String mode = settings.themeMode();
        TextView[] tabs = themeTabs;
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = (i == 0 && "system".equals(mode))
                    || (i == 1 && "light".equals(mode))
                    || (i == 2 && "dark".equals(mode));
            tabs[i].setSelected(selected);
            tabs[i].setBackgroundResource(R.drawable.bg_tab_pill);
            tabs[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    selected ? ThemeController.withAlpha(colors.accent, 0x33) : 0x00000000));
            tabs[i].setTextColor(selected ? colors.accent : colors.textMuted);
        }
    }

    private void setupFontGroup() {
        TextView sans = findViewById(R.id.font_sans);
        TextView serif = findViewById(R.id.font_serif);
        TextView mono = findViewById(R.id.font_mono);
        fontTabs = new TextView[]{sans, serif, mono};
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int index = v.getId() == R.id.font_serif ? 1 : (v.getId() == R.id.font_mono ? 2 : 0);
                settings.setEditorFont(index);
                settings.bumpRevision();
                refreshFontTabs();
            }
        };
        sans.setOnClickListener(listener);
        serif.setOnClickListener(listener);
        mono.setOnClickListener(listener);
        refreshFontTabs();
    }

    private void refreshFontTabs() {
        int index = settings.editorFont();
        for (int i = 0; i < fontTabs.length; i++) {
            boolean selected = i == index;
            fontTabs[i].setSelected(selected);
            fontTabs[i].setBackgroundResource(R.drawable.bg_tab_pill);
            fontTabs[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    selected ? ThemeController.withAlpha(colors.accent, 0x33) : 0x00000000));
            fontTabs[i].setTextColor(selected ? colors.accent : colors.textMuted);
        }
    }

    private void setupAccents() {
        accentGrid.removeAllViews();
        int swatchSize = UiUtil.dp(this, 52);
        for (int i = 0; i < ThemeController.PALETTES.length; i++) {
            final ThemeController.Palette palette = ThemeController.PALETTES[i];
            ColorSwatch swatch = new ColorSwatch(this);
            swatch.setColor(colors.dark ? palette.dark : palette.light);
            swatch.setPalette(colors.text, colors.surfaceAlt);
            swatch.setSelectedSwatch(palette.id.equals(settings.accent()));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = swatchSize;
            params.height = swatchSize;
            params.setMargins(0, UiUtil.dp(this, 2), UiUtil.dp(this, 4), UiUtil.dp(this, 2));
            swatch.setLayoutParams(params);
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.setAccent(palette.id);
                    settings.bumpRevision();
                    recreate();
                }
            });
            accentGrid.addView(swatch);
        }
    }

    // ---------------------------------------------------------------------------------
    // switches
    // ---------------------------------------------------------------------------------

    private void setupSwitches() {
        bindSwitch(R.id.switch_highlight, Settings.KEY_HIGHLIGHT, settings.highlight());
        bindSwitch(R.id.switch_line_numbers, Settings.KEY_LINE_NUMBERS, settings.lineNumbers());
        bindSwitch(R.id.switch_typewriter, Settings.KEY_TYPEWRITER, settings.typewriter());
        bindSwitch(R.id.switch_autopair, Settings.KEY_AUTO_PAIR, settings.autoPair());
        bindSwitch(R.id.switch_autolists, Settings.KEY_AUTO_LISTS, settings.autoLists());
        bindSwitch(R.id.switch_autoindent, Settings.KEY_AUTO_INDENT, settings.autoIndent());
        bindSwitch(R.id.switch_spellcheck, Settings.KEY_SPELLCHECK, settings.spellcheck());
        bindSwitch(R.id.switch_softwrap, Settings.KEY_SOFT_WRAP, settings.softWrap());
        bindSwitch(R.id.switch_load_images, Settings.KEY_LOAD_IMAGES, settings.loadImages());
        bindSwitch(R.id.switch_autosave, Settings.KEY_AUTOSAVE, settings.autosave());
        bindSwitch(R.id.switch_keep_on, Settings.KEY_KEEP_SCREEN_ON, settings.keepScreenOn());
        bindSwitch(R.id.switch_smart, Settings.KEY_PREVIEW_SMART, settings.previewSmartTypography());
        bindSwitch(R.id.switch_mono, Settings.KEY_MONO_CODE, settings.monoCode());
        bindSwitch(R.id.switch_scroll_sync, Settings.KEY_SCROLL_SYNC, settings.scrollSync());
    }

    private void bindSwitch(int id, final String key, boolean initial) {
        SwitchCompat view = findViewById(id);
        view.setChecked(initial);
        styleSwitch(view);
        view.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean checked) {
                settings.set(key, checked);
                settings.bumpRevision();
            }
        });
    }

    private void styleSwitch(SwitchCompat view) {
        try {
            view.setButtonTintList(android.content.res.ColorStateList.valueOf(colors.accent));
            view.setTrackTintList(android.content.res.ColorStateList.valueOf(
                    ThemeController.withAlpha(colors.accent, 0x66)));
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------------------------
    // sliders
    // ---------------------------------------------------------------------------------

    private void setupSliders() {
        SeekBar size = findViewById(R.id.seek_size);
        int sizeProgress = Math.max(0, Math.min(12, Math.round(settings.baseSize()) - 12));
        size.setProgress(sizeProgress);
        sizeValue.setText(String.format(Locale.US, "%dsp", Math.round(settings.baseSize())));
        size.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 12 + progress;
                sizeValue.setText(value + "sp");
                if (fromUser) {
                    settings.setBaseSize(value);
                    settings.bumpRevision();
                }
            }
        });

        SeekBar spacing = findViewById(R.id.seek_spacing);
        int spacingProgress = Math.max(0, Math.min(10,
                Math.round((settings.lineSpacing() - 1f) * 10f)));
        spacing.setProgress(spacingProgress);
        spacingValue.setText(String.format(Locale.US, "%.1fx", settings.lineSpacing()));
        spacing.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 1f + progress * 0.1f;
                spacingValue.setText(String.format(Locale.US, "%.1fx", value));
                if (fromUser) {
                    settings.setLineSpacing(value);
                    settings.bumpRevision();
                }
            }
        });

        SeekBar tabs = findViewById(R.id.seek_tab);
        int tabProgress = settings.tabWidth() == 2 ? 0 : (settings.tabWidth() == 8 ? 2 : 1);
        tabs.setProgress(tabProgress);
        tabWidthValue.setText(String.valueOf(settings.tabWidth()));
        tabs.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int width = progress == 0 ? 2 : (progress == 1 ? 4 : 8);
                tabWidthValue.setText(String.valueOf(width));
                if (fromUser) settings.setInt(Settings.KEY_TAB_WIDTH, width);
            }
        });

        SeekBar toc = findViewById(R.id.seek_toc);
        final TextView tocValue = findViewById(R.id.toc_level_value);
        int tocProgress = Math.max(0, Math.min(4, settings.tocMaxLevel() - 2));
        toc.setProgress(tocProgress);
        tocValue.setText(String.valueOf(settings.tocMaxLevel()));
        toc.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int level = 2 + progress;
                tocValue.setText(String.valueOf(level));
                if (fromUser) {
                    settings.setInt(Settings.KEY_TOC_MAX_LEVEL, level);
                    settings.bumpRevision();
                }
            }
        });

        SeekBar autosave = findViewById(R.id.seek_autosave);
        int autosaveProgress = Math.max(0, Math.min(9,
                Math.round((settings.autosaveDelay() - 500) / 500f)));
        autosave.setProgress(autosaveProgress);
        autosaveValue.setText(String.format(Locale.US, "%.1fs", settings.autosaveDelay() / 1000f));
        autosave.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 500 + progress * 500;
                autosaveValue.setText(String.format(Locale.US, "%.1fs", value / 1000f));
                if (fromUser) settings.setInt(Settings.KEY_AUTOSAVE_DELAY, value);
            }
        });
    }

    /** Default no-op implementations so each slider only overrides what it needs. */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
