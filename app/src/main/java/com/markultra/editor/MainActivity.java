package com.markultra.editor;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.speech.tts.TextToSpeech;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.markultra.editor.editor.CodeEditor;
import com.markultra.editor.editor.MarkdownHighlighter;
import com.markultra.editor.editor.SmartEdit;
import com.markultra.editor.editor.SmartInput;
import com.markultra.editor.editor.UndoManager;
import androidx.lifecycle.ViewModelProvider;

import com.markultra.editor.io.DocumentLoader;
import com.markultra.editor.io.FileController;
import com.markultra.editor.io.TextFile;
import com.markultra.editor.md.Equations;
import com.markultra.editor.md.GfmHtml;
import com.markultra.editor.md.MdBlock;
import com.markultra.editor.md.MdBlockParser;
import com.markultra.editor.md.MdConvert;
import com.markultra.editor.md.MdSearch;
import com.markultra.editor.md.MdStats;
import com.markultra.editor.md.MdTableData;
import com.markultra.editor.md.MdTasks;
import com.markultra.editor.md.MdToc;
import com.markultra.editor.md.PasteConvert;
import com.markultra.editor.md.Templates;
import com.markultra.editor.md.TextTools;
import com.markultra.editor.prefs.Settings;
import com.markultra.editor.prefs.ThemeController;
import com.markultra.editor.ui.Dialogs;
import com.markultra.editor.ui.MarkdownActions;
import com.markultra.editor.ui.MathPreview;
import com.markultra.editor.ui.QuickPanel;
import com.markultra.editor.util.UiUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The single screen of MarkUltra: editor, preview, file handling and every command.
 */
public class MainActivity extends AppCompatActivity
        implements MarkdownActions.Host {

    private static final int MODE_EDIT = 0;
    private static final int MODE_SPLIT = 1;
    private static final int MODE_PREVIEW = 2;

    private static final int PENDING_NONE = 0;
    private static final int PENDING_SAVE_AS = 1;
    private static final int PENDING_EXPORT = 2;
    private static final int PENDING_DUPLICATE = 3;

    private static final String TAG = "MarkUltra";

    private static final String STATE_MODE = "mode";
    private static final String STATE_FOCUS = "focus";
    private static final String STATE_FIND = "find";

    private static final long LARGE_DOCUMENT = 400_000;

    // ---- state --------------------------------------------------------------------
    private Settings settings;
    private ThemeController themeController;
    private ThemeController.Colors colors;
    private FileController files;
    private MarkdownActions actions;

    private Uri currentUri;
    private String currentName = "Untitled.md";
    private boolean dirty;
    private boolean internalChange;
    private boolean syncing;
    private boolean focusMode;
    private int mode = MODE_SPLIT;
    private int pendingAction = PENDING_NONE;
    private String pendingContent;
    private String pendingName;
    private String pendingMime;
    private int lastSortedColumn = -1;
    private boolean lastSortAscending = true;
    private long lastSaveTime;
    /** Timestamp of the open file as reported by its provider, 0 when unknown. */
    private long diskModified;
    /** Set while the "changed on disk" dialog is up, so autosave does not retry. */
    private boolean conflictPending;
    private int settingsRevision;

    /** Set while the slash menu is opening, so the text watcher does not fire twice. */
    private boolean slashPending;
    /** Off the UI thread: every read and every write of a document. */
    private java.util.concurrent.ExecutorService ioExecutor;
    /** How the open document is written back: charset, BOM and line endings. */
    private String documentEol = TextFile.EOL_LF;
    private String documentCharset = TextFile.UTF8;
    private boolean documentBom;
    /** A background save is running, so the title line says so. */
    private boolean saving;
    /** The last background save failed, so the title line keeps saying so. */
    private boolean saveFailed;
    /** Non-null while a long file operation is in flight ("Opening..."). */
    private String busyLabel;
    /** Owns the read of a document across activity recreation. */
    private DocumentLoader loader;
    /** True from the moment a picker returns until the loader has delivered its result. */
    private boolean openInFlight;
    /** Scroll sync stays quiet until this time, so an anchor jump is not fought. */
    private long anchorGuardUntil;
    /** The exact source the page was last built from, so a resume does not rebuild it. */
    private String lastRenderedMarkdown;
    /** Set when the open picker has to start once the pending save-as finishes. */
    private boolean pendingOpenAfterSave;
    /** Work that replaces the document and must not run before the save finishes. */
    private Runnable afterSaveAction;
    /** Text last written as a draft, so an unchanged document is not written again. */
    private String lastDraftText = "";
    /** Set while the activity is being torn down, to stop draft churn. */
    private boolean recoveryPaused;
    /** Drafts above this size are not kept: preferences are not a document store. */
    private static final int MAX_DRAFT_CHARS = 2_000_000;
    /** Text to speech for the read aloud command; created on first use. */
    private TextToSpeech tts;

    private long lastRenderMs;
    /** Monotonic id of the newest render request; stale results are dropped. */
    private int renderGeneration;
    private java.util.concurrent.ExecutorService renderExecutor;
    /**
     * Host the preview's own files are served from.
     *
     * <p>Not {@code file://}. A WebView is allowed to refuse every file: sub-resource of
     * a page built with {@code loadDataWithBaseURL} - with no error, and with the script
     * simply never running - and newer WebViews refuse by default. An {@code https}
     * address that only this app answers ({@link #shouldInterceptRequest}) is an ordinary
     * same-origin load as far as the WebView is concerned: no file-access rule applies,
     * the MIME type is whatever we say it is, and the response is cacheable. It is also
     * the mechanism Google's own {@code WebViewAssetLoader} uses for exactly this.</p>
     */
    private static final String ASSET_HOST = com.markultra.editor.md.GfmHtml.ASSET_HOST;
    private static final String PREVIEW_BASE = com.markultra.editor.md.GfmHtml.ASSET_BASE;
    /** Scroll position of the preview as a fraction, kept across re-renders. */
    private float previewFraction;
    /** Last column sorted from the preview, so a second tap reverses the order. */
    private String lastSortKey = "";
    /** True once the current page has finished loading its assets. */
    private boolean previewReady;

    // ---- views --------------------------------------------------------------------
    private CodeEditor editor;
    private WebView preview;
    private View previewEmpty;
    private View paneEditor;
    private View panePreview;
    private View splitDivider;
    private View topBar;
    private View tabsContainer;
    private View statusBar;
    private View findBarRoot;
    private View formatBarScroll;
    private TextView fileName;
    private TextView fileState;
    private TextView statusStats;
    private TextView statusCursor;
    private TextView tabEdit;
    private TextView tabSplit;
    private TextView tabPreview;
    private EditText findQuery;
    private EditText replaceQuery;
    private TextView findCounter;
    private TextView toggleCase;
    private TextView toggleWord;
    private TextView toggleRegex;
    private ImageButton btnUndo;
    private ImageButton btnRedo;

    // ---- helpers ------------------------------------------------------------------
    private MarkdownHighlighter highlighter;
    private UndoManager undoManager;
    private SmartInput smartInput;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<MdSearch.Hit> hits = new ArrayList<MdSearch.Hit>();
    private int hitIndex = -1;
    private boolean caseSensitive;
    private boolean wholeWord;
    private boolean useRegex;

    private final Runnable renderTask = new Runnable() {
        @Override
        public void run() {
            renderPreview();
        }
    };

    private final Runnable autosaveTask = new Runnable() {
        @Override
        public void run() {
            autosaveNow();
        }
    };

    private final Runnable draftTask = new Runnable() {
        @Override
        public void run() {
            saveDraftNow();
        }
    };

    private final Runnable statusTask = new Runnable() {
        @Override
        public void run() {
            updateStatus();
        }
    };

    private final Runnable searchTask = new Runnable() {
        @Override
        public void run() {
            runSearch(false);
        }
    };

    private ActivityResultLauncher<Intent> openLauncher;
    private ActivityResultLauncher<Intent> createLauncher;
    private ActivityResultLauncher<Intent> imageLauncher;

    // =================================================================================
    // lifecycle
    // =================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = Settings.get(this);
        themeController = new ThemeController(this);
        colors = themeController.colors(this);
        themeController.applySystemBars(this, colors);
        setContentView(R.layout.activity_main);

        files = new FileController(this);
        renderExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        loader = new ViewModelProvider(this).get(DocumentLoader.class);
        bindViews();
        applyColors();
        registerLaunchers();
        setupEditor();
        setupTopBar();
        setupTabs();
        setupFormatBar();
        setupFindBar();
        setupStatusBar();
        setupPreview();
        setupScrollSync();
        setupBackHandling();

        actions = new MarkdownActions(this, editor, colors, settings, this);

        int restoredMode = savedInstanceState != null ? savedInstanceState.getInt(STATE_MODE, MODE_SPLIT)
                : Settings.get(this).getInt("view_mode", MODE_SPLIT);
        focusMode = savedInstanceState != null && savedInstanceState.getBoolean(STATE_FOCUS, false);
        setMode(restoredMode, false);

        if (savedInstanceState != null) {
            String uriString = savedInstanceState.getString("uri");
            if (uriString != null && uriString.length() > 0) currentUri = Uri.parse(uriString);
            String name = savedInstanceState.getString("name");
            if (name != null && name.length() > 0) currentName = name;
            dirty = savedInstanceState.getBoolean("dirty", false);
            // A rotation, a theme switch or a return from the background can recreate the
            // screen; the reader's place in the document is part of the state, not a
            // detail to be discovered again from the top of the preview.
            previewFraction = savedInstanceState.getFloat("preview_fraction", 0f);
            updateTitleState();
            updateStatus();
            renderPreview();
        } else {
            restoreDraftOrHandleIntent();
        }
        applyFocusMode(focusMode, false);
        applyKeepScreenOn();
        updateUndoButtons();
        settingsRevision = settings.revision();
        checkOnboarding();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExternalIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_MODE, mode);
        outState.putBoolean(STATE_FOCUS, focusMode);
        outState.putString("uri", currentUri == null ? "" : currentUri.toString());
        outState.putString("name", currentName);
        outState.putBoolean("dirty", dirty);
        outState.putFloat("preview_fraction", previewFraction);
        saveDraftNow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settings.revision() != settingsRevision) {
            recreate();
            return;
        }
        if (loader != null) {
            // a document that finished loading while this screen was away is applied now
            loader.setListener(loaderListener);
        }
        if (mode != MODE_EDIT && needsRender()) scheduleRender(60);
    }

    @Override
    protected void onPause() {
        if (loader != null) loader.clearListener(loaderListener);
        super.onPause();
        saveDraftNow();
        if (settings.autosave() && currentUri != null && dirty) {
            autosaveNow();
        }
    }

    /** Receives documents opened by {@link DocumentLoader}, on the main thread. */
    private final DocumentLoader.Listener loaderListener = new DocumentLoader.Listener() {
        @Override
        public void onDocumentOpened(DocumentLoader.Loaded loaded) {
            openInFlightSet(false);
            setBusy(null);
            applyDocument(loaded.uri, loaded.name, loaded.document, loaded.modified);
        }

        @Override
        public void onDocumentFailed(Uri uri, IOException error, boolean fromRecents) {
            openInFlightSet(false);
            setBusy(null);
            reportOpenFailure(uri, fromRecents, error);
        }

        @Override
        public void onOpenStarted() {
            setBusy(getString(R.string.opening));
        }
    };

    /** Kept for the picker result path, which needs the busy flag before the loader runs. */
    private void openInFlightSet(boolean value) {
        openInFlight = value;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(renderTask);
        handler.removeCallbacks(autosaveTask);
        handler.removeCallbacks(draftTask);
        handler.removeCallbacks(searchTask);
        handler.removeCallbacks(statusTask);
        if (renderExecutor != null) renderExecutor.shutdownNow();
        // shutdown, not shutdownNow: a save that is already writing must finish
        recoveryPaused = true;
        if (ioExecutor != null) ioExecutor.shutdown();
        super.onDestroy();
    }

    // =================================================================================
    // view setup
    // =================================================================================

    private void bindViews() {
        editor = findViewById(R.id.editor);
        preview = findViewById(R.id.preview);
        previewEmpty = findViewById(R.id.preview_empty);
        paneEditor = findViewById(R.id.pane_editor);
        panePreview = findViewById(R.id.pane_preview);
        splitDivider = findViewById(R.id.split_divider);
        topBar = findViewById(R.id.top_bar);
        tabsContainer = findViewById(R.id.tabs_container);
        statusBar = findViewById(R.id.status_bar);
        findBarRoot = findViewById(R.id.find_bar_root);
        formatBarScroll = findViewById(R.id.format_bar_scroll);
        fileName = findViewById(R.id.file_name);
        fileState = findViewById(R.id.file_state);
        statusStats = findViewById(R.id.status_stats);
        statusCursor = findViewById(R.id.status_cursor);
        tabEdit = findViewById(R.id.tab_edit);
        tabSplit = findViewById(R.id.tab_split);
        tabPreview = findViewById(R.id.tab_preview);
        findQuery = findViewById(R.id.find_query);
        replaceQuery = findViewById(R.id.replace_query);
        findCounter = findViewById(R.id.find_counter);
        toggleCase = findViewById(R.id.toggle_case);
        toggleWord = findViewById(R.id.toggle_word);
        toggleRegex = findViewById(R.id.toggle_regex);
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
    }

    private void applyColors() {
        findViewById(R.id.root).setBackgroundColor(colors.background);
        fileName.setTextColor(colors.text);
        fileState.setTextColor(colors.textMuted);
        statusStats.setTextColor(colors.textMuted);
        statusCursor.setTextColor(colors.textMuted);
        // The page is always the lighter of the two surfaces, so the sheet reads as a
        // page in both themes: light -> grey surround + paper; dark -> deep surround +
        // a slightly lifted page.
        int pageColor = colors.dark ? colors.surface : colors.background;
        int surround = colors.dark ? colors.background : colors.surfaceAlt;
        panePreview.setBackgroundColor(surround);
        View card = findViewById(R.id.preview_card);
        if (card != null) {
            android.graphics.drawable.GradientDrawable sheet =
                    new android.graphics.drawable.GradientDrawable();
            sheet.setColor(pageColor);
            sheet.setCornerRadius(UiUtil.dp(this, 14));
            sheet.setStroke(UiUtil.dp(this, 1), colors.divider);
            card.setBackground(sheet);
        }
        preview.setBackgroundColor(pageColor);

        int[] icons = {R.id.btn_menu, R.id.btn_undo, R.id.btn_redo,
                R.id.btn_save, R.id.btn_find_prev, R.id.btn_find_next,
                R.id.btn_find_close};
        for (int i = 0; i < icons.length; i++) {
            View view = findViewById(icons[i]);
            if (view instanceof ImageView) {
                ((ImageView) view).setColorFilter(colors.text);
            }
        }
        ImageButton keyboard = findViewById(R.id.btn_keyboard);
        if (keyboard != null) keyboard.setColorFilter(colors.textMuted);

        findQuery.setTextColor(colors.text);
        findQuery.setHintTextColor(colors.textMuted);
        replaceQuery.setTextColor(colors.text);
        replaceQuery.setHintTextColor(colors.textMuted);
        findCounter.setTextColor(colors.textMuted);

        editor.setEditorColors(colors.text, colors.textMuted, colors.selection,
                colors.gutter, 0x00000000, colors.lineHighlight);
        editor.setEditorTypeface(colors.typeface());
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, colors.textSizeSp);
        editor.setLineSpacing(0f, Math.max(1f, colors.lineSpacing));

        applyTabColors();
    }

    private void applyTabColors() {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_selected},
                new int[]{}
        };
        int selected = ThemeController.withAlpha(colors.accent, 0x33);
        ColorStateList list = new ColorStateList(states, new int[]{selected, 0x00000000});
        TextView[] tabs = {tabEdit, tabSplit, tabPreview};
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setBackgroundResource(R.drawable.bg_tab_pill);
            tabs[i].setBackgroundTintList(list);
        }
        updateTabStates();
    }

    private void setupEditor() {
        editor.setSaveEnabled(false);
        editor.setEditorColors(colors.text, colors.textMuted, colors.selection,
                colors.gutter, 0x00000000, colors.lineHighlight);
        editor.setEditorTypeface(colors.typeface());
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, colors.textSizeSp);
        editor.setLineSpacing(0f, Math.max(1f, colors.lineSpacing));
        editor.setLineNumbers(settings.lineNumbers());
        editor.setTypewriter(settings.typewriter());
        editor.setSoftWrapEnabled(settings.softWrap());
        editor.setSpellCheck(settings.spellcheck());

        highlighter = new MarkdownHighlighter(editor, colors);
        highlighter.setEnabled(settings.highlight());
        editor.addTextChangedListener(highlighter);

        smartInput = new SmartInput(editor, settings);
        editor.addTextChangedListener(smartInput);

        undoManager = new UndoManager(editor);
        editor.addTextChangedListener(undoManager);

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count == 1 && !slashPending && start >= 0 && start < s.length()
                        && s.charAt(start) == '/'
                        && (start == 0 || s.charAt(start - 1) == '\n')) {
                    slashPending = true;
                    final int offset = start;
                    editor.post(new Runnable() {
                        @Override
                        public void run() {
                            slashPending = false;
                            openSlashMenu(offset);
                        }
                    });
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                onDocumentChanged();
            }
        });
        editor.setPasteInterceptor(new CodeEditor.PasteInterceptor() {
            @Override
            public boolean onPaste() {
                return handlePaste();
            }
        });
        editor.setOnSelectionChangedListener(new CodeEditor.OnSelectionChangedListener() {
            @Override
            public void onSelectionChanged(int start, int end) {
                updateCursorPosition();
            }
        });
    }

    private void setupTopBar() {
        findViewById(R.id.btn_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMainMenu(v);
            }
        });
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveDocument();
            }
        });
        findViewById(R.id.btn_undo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (undoManager.undo()) {
                    afterCommand();
                } else {
                    toast(getString(R.string.undo_nothing));
                }
            }
        });
        findViewById(R.id.btn_redo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (undoManager.redo()) {
                    afterCommand();
                } else {
                    toast(getString(R.string.redo_nothing));
                }
            }
        });
        findViewById(R.id.btn_find).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFind(true);
            }
        });
        fileName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileMenu(v);
            }
        });
    }

    private void setupTabs() {
        tabEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_EDIT, true);
            }
        });
        tabSplit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_SPLIT, true);
            }
        });
        tabPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMode(MODE_PREVIEW, true);
            }
        });
        tabSplit.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                swapPaneHeights();
                return true;
            }
        });
    }

    private void setupFormatBar() {
        bindChip(R.id.fmt_bold, new Runnable() {
            @Override
            public void run() {
                actions.bold();
            }
        });
        bindChip(R.id.fmt_italic, new Runnable() {
            @Override
            public void run() {
                actions.italic();
            }
        });
        bindChip(R.id.fmt_strike, new Runnable() {
            @Override
            public void run() {
                actions.strike();
            }
        });
        bindChip(R.id.fmt_code, new Runnable() {
            @Override
            public void run() {
                actions.inlineCode();
            }
        });
        bindChip(R.id.fmt_heading, new Runnable() {
            @Override
            public void run() {
                actions.heading();
            }
        });
        bindChip(R.id.fmt_list, new Runnable() {
            @Override
            public void run() {
                actions.bulletList();
            }
        });
        bindChip(R.id.fmt_ordered, new Runnable() {
            @Override
            public void run() {
                actions.orderedList();
            }
        });
        bindChip(R.id.fmt_task, new Runnable() {
            @Override
            public void run() {
                actions.taskList();
            }
        });
        bindChip(R.id.fmt_quote, new Runnable() {
            @Override
            public void run() {
                actions.quote();
            }
        });
        bindChip(R.id.fmt_table, new Runnable() {
            @Override
            public void run() {
                actions.table();
            }
        });
        bindChip(R.id.fmt_link, new Runnable() {
            @Override
            public void run() {
                actions.link();
            }
        });
        bindChip(R.id.fmt_image, new Runnable() {
            @Override
            public void run() {
                actions.image();
            }
        });
        bindChip(R.id.fmt_codeblock, new Runnable() {
            @Override
            public void run() {
                actions.codeBlock();
            }
        });
        bindChip(R.id.fmt_hr, new Runnable() {
            @Override
            public void run() {
                actions.horizontalRule();
            }
        });
        bindChip(R.id.fmt_footnote, new Runnable() {
            @Override
            public void run() {
                actions.footnote();
            }
        });
        bindChip(R.id.fmt_more, new Runnable() {
            @Override
            public void run() {
                showInsertMenu();
            }
        });
    }

    private void bindChip(int id, final Runnable action) {
        View view = findViewById(id);
        if (view == null) return;
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (settings.haptic()) UiUtil.haptic(v);
                action.run();
            }
        });
    }

    private void setupStatusBar() {
        findViewById(R.id.btn_keyboard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard();
            }
        });
    }

    /**
     * Prepares the preview WebView: JavaScript on (needed for KaTeX, Mermaid and the
     * task checkboxes), a client that sends real links to the browser and keeps
     * fragment links inside the page, and the bridge object the page calls back into.
     */
    private void setupPreview() {
        WebSettings ws = preview.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(false);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setTextZoom(100);
        ws.setBlockNetworkImage(!settings.loadImages());
        ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        // The page's origin is https now, and a document may still reference an http
        // image. Mixing is allowed so those keep loading, exactly as they did when the
        // page was a file:// document.
        try {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        } catch (RuntimeException ignored) {
            // an older WebView allows it already
        }
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        // The preview is our own HTML. Letting its file:// origin read the app's own
        // assets is the difference between KaTeX and Mermaid loading and not: on some
        // WebView builds the restriction is enforced for sub-resources of a page loaded
        // with loadDataWithBaseURL, and the scripts fail with no error at all.
        try {
            ws.setAllowFileAccessFromFileURLs(true);
            ws.setAllowUniversalAccessFromFileURLs(true);
        } catch (RuntimeException ignored) {
            // removed in a future WebView; the bridge loader below does not depend on it
        }
        preview.setVerticalScrollBarEnabled(false);
        preview.setHorizontalScrollBarEnabled(false);
        preview.setOverScrollMode(View.OVER_SCROLL_NEVER);
        preview.addJavascriptInterface(new PreviewBridge(), "Android");
        preview.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                // A link to a file next to the document arrives under the page's own
                // origin. It is not an internet address: let the WebView load it from the
                // intercept above instead of handing it to a browser.
                if (ASSET_HOST.equals(uri.getHost())) return false;
                if (scheme.startsWith("http") || scheme.equals("mailto") || scheme.equals("tel")
                        || scheme.equals("market")) {
                    if (!UiUtil.openUri(MainActivity.this, uri.toString())) {
                        toast(getString(R.string.no_app_for_link));
                    }
                    return true;
                }
                if (uri.getFragment() != null && (uri.getPath() == null
                        || uri.getPath().length() == 0 || "/android_asset/".equals(uri.getPath())
                        || (ASSET_HOST.equals(uri.getHost()) && "/assets/".equals(uri.getPath())))) {
                    // A same-page fragment. Returning false here made the WebView load
                    // file:///android_asset/#id as a new document: the page was thrown
                    // away, and onPageFinished restored the old scroll position, which is
                    // why a table-of-contents tap jumped and came straight back.
                    jumpToAnchor(uri.getFragment(), true);
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              WebResourceRequest request) {
                Uri url = request.getUrl();
                if (url == null) return null;
                // an image picked from another provider: stream the bytes, because the
                // WebView has no grant of its own and would show a broken picture
                if ("content".equals(url.getScheme())) {
                    return openContentImage(url);
                }
                String path = url.getPath();
                // the page's own origin: every script, stylesheet, font and relative
                // image it asks for arrives here, as an ordinary https request
                if (ASSET_HOST.equals(url.getHost()) && path != null
                        && path.startsWith("/assets/")) {
                    String name = path.substring("/assets/".length());
                    if (com.markultra.editor.io.RelativeImage.isBundledAsset(name)) {
                        WebResourceResponse bundled = serveBundledAsset(name);
                        if (bundled != null) return bundled;
                    }
                    return openRelativeResource(name);
                }
                if (!"file".equals(url.getScheme())) return null;
                if (path == null || !path.startsWith("/android_asset/")) return null;
                String relative = path.substring("/android_asset/".length());
                if (com.markultra.editor.io.RelativeImage.isBundledAsset(relative)) {
                    return serveBundledAsset(relative);
                }
                return openRelativeResource(relative);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                previewReady = true;
                // The position is restored even while the anchor guard is up: the guard
                // is there to stop the page reporting its own jump back into the editor,
                // and letting it swallow the restore is what sent the reader to the top
                // of the document when a re-render landed just after a link was followed.
                if (previewFraction > 0.001f) {
                    view.evaluateJavascript("muRestore(" + previewFraction + ")", null);
                }
            }
        });
        preview.setWebChromeClient(new WebChromeClient());
    }

    /**
     * Serves one of the app's own files from {@code assets/} to the page.
     *
     * <p>The page asks for {@code file:///android_asset/<name>}; answering here, from the
     * AssetManager and with the right MIME type, means the scripts do not depend on the
     * WebView's file access rules, on the document being a plain file, or on the asset
     * being present under a path the WebView is willing to read. A JavaScript file served
     * as {@code application/octet-stream} is not executed at all, which is one way a page
     * can silently end up with no diagram engines.</p>
     */
    private WebResourceResponse serveBundledAsset(String relative) {
        try {
            java.io.InputStream in = getAssets().open(relative);
            java.util.Map<String, String> headers = new java.util.HashMap<String, String>();
            headers.put("Access-Control-Allow-Origin", "*");
            // read from the APK on every load rather than from a cache that survives an
            // app update: a stale Mermaid is a diagram that does not draw
            headers.put("Cache-Control", "no-cache");
            String mime = com.markultra.editor.io.RelativeImage.mimeOf(relative);
            if ("application/json".equals(mime)) mime = "application/javascript";
            // Say UTF-8 rather than leaving it to the document. A script decoded as
            // Latin-1 is a script that does not parse, and its engine never appears.
            String enc = mime.startsWith("text/") || mime.endsWith("javascript")
                    || mime.endsWith("json") || mime.endsWith("svg+xml") ? "UTF-8" : null;
            return new WebResourceResponse(mime, enc, 200, "OK", headers, in);
        } catch (java.io.IOException e) {
            // a name this build does not ship: let the WebView look for it itself
            return null;
        }
    }

    /** Serves a picked {@code content://} image to the preview. */
    private WebResourceResponse openContentImage(Uri uri) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            String type = getContentResolver().getType(uri);
            if (type == null) type = "image/png";
            java.util.Map<String, String> headers = new java.util.HashMap<String, String>();
            headers.put("Access-Control-Allow-Origin", "*");
            return new WebResourceResponse(type, null, 200, "OK", headers, in);
        } catch (Exception e) {
            // a missing grant or a moved file: let the page show its own broken state
            return null;
        }
    }

    /**
     * Jumps to a heading of the open document.
     *
     * <p>Three things have to agree for this to look like a jump rather than a glitch:
     * the page must not be navigated (it is not - this is JavaScript), the scroll
     * reporter must not treat the jump as a user scroll and drag the editor along
     * mid-flight, and in split mode the editor should end up at the same section.</p>
     *
     * @param id       the fragment, with or without the leading {@code #}
     * @param fromPage true when the WebView raised the navigation itself
     */
    private void jumpToAnchor(String id, boolean fromPage) {
        if (id == null) return;
        final String anchor = id.startsWith("#") ? id.substring(1) : id;
        if (anchor.length() == 0 || preview == null || !previewReady) return;
        anchorGuardUntil = SystemClock.uptimeMillis() + 1200;
        preview.evaluateJavascript(GfmHtml.anchorScript(anchor),
                new android.webkit.ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        // the page answers with the scroll fraction it landed on, or -1
                        float fraction = previewFraction;
                        try {
                            if (value != null && value.length() > 0 && !"null".equals(value)) {
                                fraction = Float.parseFloat(value.replace("\"", ""));
                            }
                        } catch (NumberFormatException ignored) {
                            // keep the previous fraction: the page still scrolled
                        }
                        if (fraction < 0f) {
                            toast(getString(R.string.anchor_missing, anchor));
                            return;
                        }
                        previewFraction = Math.max(0f, Math.min(1f, fraction));
                        syncEditorToFraction(previewFraction);
                    }
                });
    }

    /** Moves the editor to a relative position without echoing back into the page. */
    private void syncEditorToFraction(float fraction) {
        if (!settings.scrollSync() || mode != MODE_SPLIT) return;
        Layout layout = editor.getLayout();
        if (layout == null) return;
        int range = layout.getHeight() + editor.getTotalPaddingTop()
                + editor.getTotalPaddingBottom() - editor.getHeight();
        if (range <= 0) return;
        syncing = true;
        editor.scrollTo(0, Math.round(range * fraction));
        syncing = false;
    }

    /**
     * Serves a relative image (or any relative file) of the open document, so a README
     * that points at {@code docs/banner.png} shows the banner, exactly as on GitHub.
     * Works for files opened through SAF and for files opened from disk.
     */
    private WebResourceResponse openRelativeResource(String relative) {
        if (currentUri == null) return null;
        try {
            String scheme = currentUri.getScheme() == null ? "" : currentUri.getScheme();
            if ("file".equals(scheme)) {
                String open = currentUri.getPath();
                String target = com.markultra.editor.io.RelativeImage.join(
                        com.markultra.editor.io.RelativeImage.directoryOf(open), relative);
                if (target == null) return null;
                java.io.File file = new java.io.File(target);
                if (!file.isFile()) return null;
                return new WebResourceResponse(
                        com.markultra.editor.io.RelativeImage.mimeOf(target), null,
                        new java.io.FileInputStream(file));
            }
            if ("content".equals(scheme)) {
                // the document id of a SAF document ends with its path; swapping the
                // file name walks to a sibling document in the same tree
                String docId = DocumentsContract.getDocumentId(currentUri);
                String dir = com.markultra.editor.io.RelativeImage.directoryOf(docId);
                String wanted = com.markultra.editor.io.RelativeImage.join(dir, relative);
                if (wanted == null) return null;
                String tree = DocumentsContract.getTreeDocumentId(currentUri);
                Uri sibling = DocumentsContract.buildDocumentUriUsingTree(currentUri, wanted);
                java.io.InputStream in = getContentResolver().openInputStream(sibling);
                if (in == null) return null;
                return new WebResourceResponse(
                        com.markultra.editor.io.RelativeImage.mimeOf(wanted), null, in);
            }
        } catch (IllegalArgumentException e) {
            // not a tree document, or the sibling does not exist
            return null;
        } catch (java.io.FileNotFoundException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        }
        return null;
    }

    /** Everything the page is allowed to call: the whole JS surface of the app. */
    private final class PreviewBridge {

        @JavascriptInterface
        public void toggleTask(final int index, final boolean checked) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    applyTaskState(index, checked);
                }
            });
        }

        @JavascriptInterface
        public void sortTable(final int tableIndex, final int column, final String header) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    sortTableFromPreview(tableIndex, column, header);
                }
            });
        }

        /** A code block's copy button was tapped: put the exact source on the clipboard. */
        /**
         * The source of one of the app's own script files.
         *
         * <p>This exists because a WebView can refuse to load a {@code file://} sub-resource
         * of a page built with {@code loadDataWithBaseURL} - silently, with no console
         * error, which is what "the engine did not load" turned out to be on a real
         * device. Handing the bytes over the JavaScript bridge cannot be refused by any
         * URL policy, so it is the guaranteed path, used when the script tag did not
         * produce its global.</p>
         */
        @JavascriptInterface
        public String loadAsset(final String name) {
            if (!com.markultra.editor.io.RelativeImage.isBundledAsset(name)) return "";
            try {
                java.io.InputStream in = getAssets().open(name);
                byte[] buffer = new byte[8192];
                java.io.ByteArrayOutputStream out =
                        new java.io.ByteArrayOutputStream(64 * 1024);
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                in.close();
                return out.toString("UTF-8");
            } catch (java.io.IOException e) {
                return "";
            }
        }

        @JavascriptInterface
        public void copyCode(final String text) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (text == null || text.length() == 0) return;
                    UiUtil.copy(MainActivity.this, text, getString(R.string.code_copied));
                    toast(getString(R.string.code_copied));
                    UiUtil.haptic(editor);
                }
            });
        }

        /** A link inside the document points at one of its own headings. */
        @JavascriptInterface
        public void openAnchor(final String id) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    jumpToAnchor(id, false);
                }
            });
        }

        @JavascriptInterface
        public void previewScroll(final float fraction) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    applyPreviewScroll(fraction);
                }
            });
        }
    }

    /** A checkbox was tapped: write the same state back into the Markdown. */
    private void applyTaskState(int index, boolean checked) {
        Editable live = editor.getText();
        // One character, changed in place. Rebuilding the whole document here is what made
        // a tap feel slow: a full replace throws away the text layout of every line, the
        // undo snapshot and every highlight span, then re-measures the entire file (tens
        // of thousands of characters) before the screen can respond.
        MdTasks.Task task = MdTasks.taskAt(live.toString(), index);
        if (task == null || task.checked == checked) return;
        int at = task.boxCharIndex;
        if (at < 0 || at >= live.length()) return;
        int caret = editor.getSelectionStart();
        internalChange = true;
        try {
            live.replace(at, at + 1, checked ? "x" : " ");
        } finally {
            internalChange = false;
        }
        editor.setSelection(Math.max(0, Math.min(caret, live.length())));
        dirty = true;
        updateTitleState();
        updateStatus();
        scheduleAutosave();
        scheduleDraft();
        toast(getString(R.string.checkbox_toggled));
    }

    /** A table header was tapped: sort that column in the source and re-render. */
    private void sortTableFromPreview(int tableIndex, int column, String header) {
        MdBlockParser.Result parsed = MdBlockParser.parse(editor.getText().toString());
        List<MdBlock> blocks = parsed.blocks;
        int seen = 0;
        for (int b = 0; b < blocks.size(); b++) {
            MdBlock block = blocks.get(b);
            if (block.type != MdBlock.TABLE || block.table == null) continue;
            MdTableData data = block.table;
            boolean matchesIndex = seen == tableIndex;
            boolean matchesHeader = header != null && column >= 0
                    && column < data.headers.size()
                    && data.headers.get(column).trim().equalsIgnoreCase(header.trim());
            seen++;
            if (!matchesIndex && !matchesHeader) continue;
            if (column < 0 || column >= data.headers.size()) return;
            String key = block.srcStart + ":" + column;
            boolean ascending = !key.equals(lastSortKey) || !lastSortAscending;
            lastSortKey = key;
            lastSortAscending = ascending;
            data.sortBy(column, ascending);
            String markdown = data.toMarkdown();
            if (markdown.endsWith("\n")) markdown = markdown.substring(0, markdown.length() - 1);
            Editable live = editor.getText();
            int from = Math.max(0, Math.min(data.srcStart, live.length()));
            int to = Math.max(from, Math.min(data.srcEnd, live.length()));
            internalChange = true;
            live.replace(from, to, markdown);
            internalChange = false;
            dirty = true;
            updateTitleState();
            scheduleAutosave();
            scheduleDraft();
            renderPreview();
            toast(getString(R.string.table_sorted, column + 1));
            return;
        }
    }

    /** The page scrolled: move the editor to the same relative position. */
    private void applyPreviewScroll(float fraction) {
        long now = SystemClock.uptimeMillis();
        if (now < anchorGuardUntil) {
            // the page is still settling after a jump: remember where it landed but do
            // not mirror it into the editor, which would bounce the page back
            previewFraction = Math.max(0f, Math.min(1f, fraction));
            return;
        }
        previewFraction = Math.max(0f, Math.min(1f, fraction));
        if (syncing || !settings.scrollSync() || mode != MODE_SPLIT) return;
        Layout layout = editor.getLayout();
        if (layout == null) return;
        int range = layout.getHeight() + editor.getTotalPaddingTop()
                + editor.getTotalPaddingBottom() - editor.getHeight();
        if (range <= 0) return;
        syncing = true;
        editor.scrollTo(0, Math.round(range * previewFraction));
        syncing = false;
    }

    private void setupScrollSync() {
        editor.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (mode == MODE_SPLIT && settings.scrollSync()) syncPreviewToEditor();
            }
        });
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (findBarRoot.getVisibility() == View.VISIBLE) {
                    showFind(false);
                    return;
                }
                if (focusMode) {
                    applyFocusMode(false, true);
                    return;
                }
                if (dirty) {
                    confirmExit();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    /**
     * Shows the cheat sheet once, on the first launch.
     *
     * <p>The flag is set before the dialog is built, and the dialog itself is guarded: a
     * window or theme problem while showing it must never cost the user their first run,
     * and the guide is a tap away in the menu anyway.</p>
     */
    private void checkOnboarding() {
        if (settings.is(Settings.KEY_ONBOARDED, false)) return;
        settings.set(Settings.KEY_ONBOARDED, true);
        try {
            Dialogs.cheatSheet(this, colors);
        } catch (RuntimeException e) {
            Log.w(TAG, "cheat sheet dialog could not be shown", e);
        }
    }

    // =================================================================================
    // mode handling
    // =================================================================================

    private void setMode(int newMode, boolean persist) {
        mode = newMode;
        paneEditor.setVisibility(newMode == MODE_PREVIEW ? View.GONE : View.VISIBLE);
        panePreview.setVisibility(newMode == MODE_EDIT ? View.GONE : View.VISIBLE);
        splitDivider.setVisibility(newMode == MODE_SPLIT ? View.VISIBLE : View.GONE);

        android.widget.LinearLayout.LayoutParams editorParams =
                (android.widget.LinearLayout.LayoutParams) paneEditor.getLayoutParams();
        android.widget.LinearLayout.LayoutParams previewParams =
                (android.widget.LinearLayout.LayoutParams) panePreview.getLayoutParams();
        editorParams.weight = 1f;
        previewParams.weight = 1f;
        paneEditor.setLayoutParams(editorParams);
        panePreview.setLayoutParams(previewParams);

        updateTabStates();
        // the format bar is a keyboard aid: in preview only there is no raw text to
        // format, so it steps out of the way
        View formatBar = findViewById(R.id.format_bar_scroll);
        View formatDivider = findViewById(R.id.format_bar_divider);
        if (formatBar != null) {
            formatBar.setVisibility(newMode == MODE_PREVIEW ? View.GONE : View.VISIBLE);
        }
        if (formatDivider != null) {
            formatDivider.setVisibility(newMode == MODE_PREVIEW ? View.GONE : View.VISIBLE);
        }
        if (newMode != MODE_EDIT) renderPreview();
        if (persist) settings.setInt("view_mode", newMode);
    }

    private void updateTabStates() {
        tabEdit.setSelected(mode == MODE_EDIT);
        tabSplit.setSelected(mode == MODE_SPLIT);
        tabPreview.setSelected(mode == MODE_PREVIEW);
        tabEdit.setTextColor(mode == MODE_EDIT ? colors.accent : colors.textMuted);
        tabSplit.setTextColor(mode == MODE_SPLIT ? colors.accent : colors.textMuted);
        tabPreview.setTextColor(mode == MODE_PREVIEW ? colors.accent : colors.textMuted);
    }

    private void swapPaneHeights() {
        if (mode != MODE_SPLIT) return;
        android.widget.LinearLayout.LayoutParams editorParams =
                (android.widget.LinearLayout.LayoutParams) paneEditor.getLayoutParams();
        android.widget.LinearLayout.LayoutParams previewParams =
                (android.widget.LinearLayout.LayoutParams) panePreview.getLayoutParams();
        float e = editorParams.weight;
        editorParams.weight = previewParams.weight;
        previewParams.weight = e;
        paneEditor.setLayoutParams(editorParams);
        panePreview.setLayoutParams(previewParams);
    }

    private void applyFocusMode(boolean enabled, boolean announce) {
        focusMode = enabled;
        int visibility = enabled ? View.GONE : View.VISIBLE;
        topBar.setVisibility(visibility);
        tabsContainer.setVisibility(visibility);
        formatBarScroll.setVisibility(visibility);
        statusBar.setVisibility(visibility);
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(android.view.WindowInsets.Type.systemBars());
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(android.view.WindowInsets.Type.systemBars());
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
        if (announce) toast(enabled ? getString(R.string.focus_on) : getString(R.string.focus_off));
    }

    // =================================================================================
    // document change pipeline
    // =================================================================================

    private void onDocumentChanged() {
        if (internalChange) return;
        dirty = true;
        try {
            updateTitleState();
            scheduleStatus();
            updateUndoButtons();
            scheduleRender();
            scheduleAutosave();
            scheduleDraft();
            if (findBarRoot != null && findBarRoot.getVisibility() == View.VISIBLE) {
                scheduleSearch();
            }
        } catch (RuntimeException e) {
            // A UI refresh problem must never interrupt typing or lose the edit.
            Log.w(TAG, "document refresh failed", e);
        }
    }

    private void scheduleRender() {
        handler.removeCallbacks(renderTask);
        long delay = editor.getText().length() > 120_000 ? 420 : 140;
        handler.postDelayed(renderTask, delay);
    }

    private void scheduleRender(long delay) {
        handler.removeCallbacks(renderTask);
        handler.postDelayed(renderTask, delay);
    }

    private void scheduleAutosave() {
        handler.removeCallbacks(autosaveTask);
        if (!settings.autosave()) return;
        handler.postDelayed(autosaveTask, Math.max(800, settings.autosaveDelay()));
    }

    private void scheduleDraft() {
        handler.removeCallbacks(draftTask);
        handler.postDelayed(draftTask, 1500);
    }

    private void scheduleStatus() {
        handler.removeCallbacks(statusTask);
        handler.postDelayed(statusTask, 220);
    }

    private void scheduleSearch() {
        handler.removeCallbacks(searchTask);
        handler.postDelayed(searchTask, 220);
    }

    /**
     * Renders off the UI thread with flexmark, then hands the finished page to the
     * WebView. Every request gets a generation number; a result that arrives after a
     * newer request is discarded, so fast typing never shows stale text and never
     * blocks the input path, whatever the document size.
     */
    private void renderPreview() {
        if (mode == MODE_EDIT) return;
        final String markdown = editor.getText().toString();
        if (markdown.length() > LARGE_DOCUMENT && mode != MODE_PREVIEW) {
            showPreviewNotice(getString(R.string.large_document, markdown.length() / 1024));
            return;
        }
        previewEmpty.setVisibility(markdown.trim().length() == 0 ? View.VISIBLE : View.GONE);
        preview.setVisibility(markdown.trim().length() == 0 ? View.INVISIBLE : View.VISIBLE);
        final GfmHtml.Options options = previewOptions();
        final int generation = ++renderGeneration;
        final long start = SystemClock.uptimeMillis();
        renderExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final String html;
                try {
                    html = GfmHtml.page(markdown, options);
                } catch (RuntimeException e) {
                    Log.e(TAG, "render failed", e);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            showPreviewNotice(getString(R.string.render_failed));
                        }
                    });
                    return;
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || generation != renderGeneration) return;
                        previewReady = false;
                        lastRenderMs = SystemClock.uptimeMillis() - start;
                        lastRenderedMarkdown = markdown;
                        preview.loadDataWithBaseURL(PREVIEW_BASE, html, "text/html", "utf-8", null);
                        updateStatus();
                    }
                });
            }
        });
    }

    /**
     * True when the page on screen no longer matches the document.
     *
     * <p>Coming back from a link in the browser used to rebuild the page unconditionally,
     * which threw away the rendered document and the reader's place in it. Nothing in the
     * document changed while the app was away, so there is nothing to rebuild.</p>
     */
    private boolean needsRender() {
        if (preview == null) return true;
        String markdown = editor.getText().toString();
        if (lastRenderedMarkdown == null) return true;
        if (markdown.length() != lastRenderedMarkdown.length()) return true;
        return !markdown.equals(lastRenderedMarkdown);
    }

    /** Colour and size options for the GitHub style page, taken from the app theme. */
    private GfmHtml.Options previewOptions() {
        GfmHtml.Options o = new GfmHtml.Options();
        o.dark = colors.dark;
        o.background = hex(colors.dark ? colors.surface : colors.background);
        o.foreground = hex(colors.text);
        o.muted = hex(colors.textMuted);
        o.accent = hex(colors.accent);
        o.borderColor = hex(colors.divider);
        o.surfaceColor = hex(colors.surfaceAlt);
        o.fontScale = Math.max(0.8f, Math.min(1.6f, colors.textSizeSp / 16f));
        o.images = settings.loadImages();
        o.rich = settings.richMarkdown();
        return o;
    }

    private static String escapeHtml(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&') sb.append("&amp;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    /** Short styled message in the preview pane (too large to render, render failure). */
    private void showPreviewNotice(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<style>body{margin:0;padding:32px 20px;font-family:-apple-system,")
                .append("Segoe UI,Roboto,sans-serif;background:").append(hex(colors.background))
                .append(";color:").append(hex(colors.textMuted))
                .append(";font-size:15px;line-height:1.5;text-align:center}</style></head>")
                .append("<body>").append(escapeHtml(message)).append("</body></html>");
        previewEmpty.setVisibility(View.GONE);
        preview.setVisibility(View.VISIBLE);
        preview.loadDataWithBaseURL(PREVIEW_BASE, sb.toString(), "text/html", "utf-8", null);
    }

    private void updateTitleState() {
        fileName.setText(currentName);
        if (busyLabel != null) {
            fileState.setText(busyLabel);
            fileState.setTextColor(colors.accent);
            return;
        }
        StringBuilder state = new StringBuilder();
        if (saving) {
            state.append(getString(R.string.saving));
        } else if (saveFailed) {
            state.append(getString(R.string.not_saved));
        } else if (dirty) {
            state.append(getString(R.string.unsaved));
        } else if (lastSaveTime > 0) {
            state.append(getString(R.string.saved)).append(" \u00B7 ")
                    .append(UiUtil.relativeTime(lastSaveTime));
        } else {
            state.append(getString(R.string.saved));
        }
        if (currentUri == null) state.append(" \u00B7 ").append(getString(R.string.local_draft));
        fileState.setText(state.toString());
        fileState.setTextColor(dirty || saveFailed ? colors.accent : colors.textMuted);
    }

    private void updateStatus() {
        // passed as a CharSequence: counting words must not copy the whole document
        statusStats.setText(MdStats.quickStatusLine(editor.getText()));
        updateCursorPosition();
        if (mode != MODE_EDIT && lastRenderMs > 0) {
            statusStats.setText(statusStats.getText() + " \u00B7 " + lastRenderMs + " ms");
        }
    }

    private void updateCursorPosition() {
        int caret = editor.getSelectionStart();
        if (caret < 0) caret = 0;
        // the editor keeps a line index across edits: walking the document from the top
        // for the cursor label is what made typing in a large file drag
        com.markultra.editor.editor.LineMap map = editor.lineMap();
        int line = map.lineNumberAt(editor.getText(), caret);
        int lineStart = map.lineStart(editor.getText(), caret);
        statusCursor.setText("Ln " + line + ", Col " + (caret - lineStart + 1));
    }

    private void updateUndoButtons() {
        btnUndo.setAlpha(undoManager.canUndo() ? 1f : 0.35f);
        btnRedo.setAlpha(undoManager.canRedo() ? 1f : 0.35f);
    }

    // =================================================================================
    // MarkdownActions.Host
    // =================================================================================

    @Override
    public void afterCommand() {
        dirty = true;
        updateTitleState();
        updateStatus();
        updateUndoButtons();
        renderPreview();
        scheduleAutosave();
        scheduleDraft();
    }

    @Override
    public void toast(String message) {
        UiUtil.toast(this, message);
    }

    private void jumpToAnchor(String anchor) {
        List<MdToc.Entry> entries = MdToc.entries(editor.getText().toString());
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).anchor.equalsIgnoreCase(anchor)) {
                jumpToOffset(entries.get(i).offset);
                return;
            }
        }
        toast(getString(R.string.footnote_not_found, anchor));
    }

    private void jumpToOffset(int offset) {
        int safe = Math.max(0, Math.min(offset, editor.getText().length()));
        if (mode == MODE_PREVIEW) setMode(MODE_SPLIT, true);
        editor.requestFocus();
        editor.setSelection(safe);
        handler.post(new Runnable() {
            @Override
            public void run() {
                editor.centerCaretNow();
            }
        });
    }

    // =================================================================================
    // scroll sync
    // =================================================================================

    /** The editor scrolled: move the page to the same relative position. */
    private void syncPreviewToEditor() {
        if (syncing || !previewReady) return;
        Layout layout = editor.getLayout();
        if (layout == null) return;
        int range = layout.getHeight() + editor.getTotalPaddingTop()
                + editor.getTotalPaddingBottom() - editor.getHeight();
        if (range <= 0) return;
        float fraction = Math.max(0f, Math.min(1f, editor.getScrollY() / (float) range));
        previewFraction = fraction;
        syncing = true;
        preview.evaluateJavascript("muScrollTo(" + fraction + ")", null);
        syncing = false;
    }

    // =================================================================================
    // find & replace
    // =================================================================================

    private void setupFindBar() {
        findViewById(R.id.btn_find_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFind(false);
            }
        });
        findViewById(R.id.btn_find_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findStep(1);
            }
        });
        findViewById(R.id.btn_find_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findStep(-1);
            }
        });
        findViewById(R.id.btn_replace_one).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                replaceCurrent();
            }
        });
        findViewById(R.id.btn_replace_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                replaceAll();
            }
        });
        toggleCase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                caseSensitive = !caseSensitive;
                styleToggle(toggleCase, caseSensitive);
                runSearch(true);
            }
        });
        toggleWord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wholeWord = !wholeWord;
                styleToggle(toggleWord, wholeWord);
                runSearch(true);
            }
        });
        toggleRegex.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                useRegex = !useRegex;
                styleToggle(toggleRegex, useRegex);
                runSearch(true);
            }
        });
        findQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch();
            }
        });
        findQuery.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                findStep(1);
                return true;
            }
        });
        styleToggle(toggleCase, false);
        styleToggle(toggleWord, false);
        styleToggle(toggleRegex, false);
    }

    private void styleToggle(TextView view, boolean active) {
        view.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        view.setTextColor(active ? colors.accent : colors.textMuted);
    }

    private void showFind(boolean show) {
        findBarRoot.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            findQuery.requestFocus();
            String selected = selectedText();
            if (selected.length() > 0 && selected.length() < 80 && selected.indexOf('\n') < 0) {
                findQuery.setText(selected);
                findQuery.setSelection(findQuery.getText().length());
            }
            showKeyboard(findQuery);
            runSearch(true);
        } else {
            hideKeyboard();
            editor.requestFocus();
        }
    }

    private String selectedText() {
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0 || end <= start) return "";
        return editor.getText().subSequence(Math.min(start, end), Math.max(start, end)).toString();
    }

    private void runSearch(boolean jumpToFirst) {
        String query = findQuery == null ? "" : findQuery.getText().toString();
        hits.clear();
        hitIndex = -1;
        if (query.length() > 0) {
            hits.addAll(MdSearch.findAll(editor.getText().toString(), query,
                    caseSensitive, wholeWord, useRegex));
        }
        if (hits.isEmpty()) {
            findCounter.setText(query.length() == 0 ? "" : getString(R.string.find_no_results));
            return;
        }
        if (jumpToFirst) {
            int caret = editor.getSelectionStart();
            hitIndex = 0;
            for (int i = 0; i < hits.size(); i++) {
                if (hits.get(i).start >= caret) {
                    hitIndex = i;
                    break;
                }
            }
            selectHit();
        } else {
            findCounter.setText(getString(R.string.find_count, 1, hits.size()));
        }
    }

    private void findStep(int direction) {
        if (hits.isEmpty()) {
            runSearch(true);
            return;
        }
        hitIndex = (hitIndex + direction + hits.size()) % hits.size();
        selectHit();
    }

    private void selectHit() {
        if (hitIndex < 0 || hitIndex >= hits.size()) return;
        MdSearch.Hit hit = hits.get(hitIndex);
        if (mode == MODE_PREVIEW) setMode(MODE_SPLIT, true);
        editor.setSelection(hit.start, hit.end);
        editor.requestFocus();
        findCounter.setText(getString(R.string.find_count, hitIndex + 1, hits.size()));
    }

    private void replaceCurrent() {
        if (hitIndex < 0 || hitIndex >= hits.size()) return;
        MdSearch.Hit hit = hits.get(hitIndex);
        Editable text = editor.getText();
        text.replace(hit.start, hit.end, replaceQuery.getText().toString());
        dirty = true;
        updateTitleState();
        runSearch(true);
        scheduleRender();
    }

    private void replaceAll() {
        String query = findQuery.getText().toString();
        if (query.length() == 0) return;
        String source = editor.getText().toString();
        String replaced = MdSearch.replaceAll(source, query, replaceQuery.getText().toString(),
                caseSensitive, wholeWord, useRegex);
        int count = hits.size();
        internalChange = true;
        editor.setText(replaced);
        internalChange = false;
        undoManager.forceSnapshot();
        dirty = true;
        updateTitleState();
        updateStatus();
        runSearch(false);
        renderPreview();
        scheduleDraft();
        toast(getString(R.string.replaced_count, count));
    }

    // =================================================================================
    // file operations
    // =================================================================================

    private void registerLaunchers() {
        openLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() != RESULT_OK) {
                            Log.i(TAG, "open picker cancelled");
                            return;
                        }
                        Uri uri = pickedUri(result);
                        if (uri == null) {
                            // The picker said OK but handed over no address. Reporting it
                            // beats the old silent return, which looked like a dead button.
                            Log.w(TAG, "open picker returned no uri");
                            toast(getString(R.string.open_no_uri));
                            return;
                        }
                        files.takePersistablePermission(uri, true);
                        openUri(uri);
                    }
                });

        imageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() != RESULT_OK) return;
                        Uri picked = pickedUri(result);
                        if (picked == null) {
                            toast(getString(R.string.open_no_uri));
                            return;
                        }
                        handlePickedImage(picked);
                    }
                });

        createLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            pendingAction = PENDING_NONE;
                            pendingOpenAfterSave = false;
                            pendingContent = null;
                            afterSaveAction = null;
                            return;
                        }
                        Uri uri = result.getData().getData();
                        if (uri == null) {
                            pendingAction = PENDING_NONE;
                            return;
                        }
                        files.takePersistablePermission(uri, true);
                        handleCreatedDocument(uri);
                    }
                });
    }

    /**
     * The save-as, export and duplicate pickers all come back here. Every one of them
     * writes on the IO thread: a 4 MB document used to be copied on the UI thread while
     * the picker animation was still running.
     */
    private void handleCreatedDocument(final Uri uri) {
        savePersistable(uri);
        final int action = pendingAction;
        final String payload = pendingContent;
        pendingAction = PENDING_NONE;
        pendingContent = null;
        if (uri == null) {
            pendingOpenAfterSave = false;
            return;
        }
        if (action == PENDING_SAVE_AS) {
            final String text = editor.getText().toString();
            setBusy(getString(R.string.saving));
            submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        files.writeDocument(uri, text, documentCharset, documentEol, documentBom);
                        final String name = files.displayName(uri);
                        final long modified = files.lastModified(uri);
                        files.addRecent(uri, name);
                        files.saveDraft("", uri, name, false);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                clearBusy();
                                currentUri = uri;
                                currentName = name;
                                // saving as does not force the document clean when the
                                // user kept typing while the bytes were written
                                dirty = !editor.getText().toString().equals(text);
                                saveFailed = false;
                                lastSaveTime = System.currentTimeMillis();
                                diskModified = modified;
                                lastDraftText = "";
                                updateTitleState();
                                toast(getString(R.string.saved));
                                runAfterSave();
                                resumePendingOpen();
                            }
                        });
                    } catch (final IOException e) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                clearBusy();
                                pendingOpenAfterSave = false;
                                afterSaveAction = null;
                                reportSaveFailure(uri, e);
                            }
                        });
                    }
                }
            });
        } else if (action == PENDING_EXPORT) {
            final String data = payload == null ? "" : payload;
            submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        // an export is a new file: it gets standard UTF-8 and LF
                        files.writeDocument(uri, data, TextFile.UTF8, TextFile.EOL_LF, false);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                toast(getString(R.string.saved));
                            }
                        });
                    } catch (final IOException e) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                reportSaveFailure(uri, e);
                            }
                        });
                    }
                }
            });
        } else if (action == PENDING_DUPLICATE) {
            final String text = editor.getText().toString();
            setBusy(getString(R.string.saving));
            submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        files.writeDocument(uri, text, documentCharset, documentEol, documentBom);
                        final String name = files.displayName(uri);
                        files.addRecent(uri, name);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                clearBusy();
                                toast(getString(R.string.copy_created, name));
                            }
                        });
                    } catch (final IOException e) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                clearBusy();
                                reportSaveFailure(uri, e);
                            }
                        });
                    }
                }
            });
        }
    }

    /** Runs the work that was waiting for the document to reach the disk. */
    private void runAfterSave() {
        Runnable next = afterSaveAction;
        afterSaveAction = null;
        if (next != null) next.run();
    }

    /** Starts the open picker that was waiting for a save to finish. */
    private void resumePendingOpen() {
        if (!pendingOpenAfterSave) return;
        pendingOpenAfterSave = false;
        requestOpenDocument();
    }

    /** Runs file work off the UI thread; work posted after teardown is dropped. */
    private void submit(Runnable task) {
        try {
            ioExecutor.execute(task);
        } catch (RuntimeException ignored) {
            // the activity is already going away: the draft on disk is the safety net
        }
    }

    private void setBusy(String label) {
        busyLabel = label;
        updateTitleState();
    }

    private void clearBusy() {
        busyLabel = null;
        updateTitleState();
    }

    private void openUri(Uri uri) {
        openUri(uri, false);
    }

    /**
     * Opens a document on the IO thread, with a real error message when it fails.
     *
     * <p>The old path ran an 8 KB buffered copy on the UI thread and turned every
     * failure into the same "could not open" toast, which is why a missing permission
     * looked exactly like a corrupt file.</p>
     */
    private void openUri(final Uri uri, final boolean fromRecents) {
        if (uri == null) return;
        if (loader == null) {
            // no view model yet (the activity is being built): read through the caller
            loader = new ViewModelProvider(this).get(DocumentLoader.class);
        }
        openInFlight = true;
        // The loader supersedes any read still running, so a second tap on Open wins
        // instead of being dropped with a "please wait" toast.
        loader.setListener(loaderListener);
        loader.load(this, uri, fromRecents);
    }



    private void reportOpenFailure(final Uri uri, boolean fromRecents, IOException e) {
        String reason;
        if (e instanceof FileController.TooLargeException) {
            reason = getString(R.string.open_too_large, TextFile.humanSize(TextFile.MAX_BYTES));
        } else if (e instanceof FileController.NotTextException) {
            reason = getString(R.string.open_binary);
        } else if (e instanceof FileController.PermissionLostException) {
            reason = getString(R.string.open_permission);
        } else {
            String detail = e.getMessage();
            reason = detail == null || detail.length() == 0
                    ? getString(R.string.open_failed) : detail;
        }
        if (fromRecents) {
            final String target = uri.toString();
            Dialogs.builder(this)
                    .setTitle(R.string.open_failed_title)
                    .setMessage(getString(R.string.open_missing, shortName(target), reason))
                    .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            files.removeRecent(target);
                            toast(getString(R.string.recent_removed));
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            Dialogs.builder(this)
                    .setTitle(R.string.open_failed_title)
                    .setMessage(getString(R.string.open_failed_detail, reason))
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    /** "content://com.android.externalstorage.documents/tree/x%3ANotes.md" -> "Notes.md". */
    private static String shortName(String uri) {
        if (uri == null) return "";
        int cut = Math.max(uri.lastIndexOf('/'), uri.lastIndexOf(':'));
        String tail = cut >= 0 && cut + 1 < uri.length() ? uri.substring(cut + 1) : uri;
        try {
            tail = Uri.decode(tail);
        } catch (RuntimeException ignored) {
            // decoding is cosmetic, keep the raw tail
        }
        return tail.length() == 0 ? uri : tail;
    }

    /** Replaces the document on screen with an opened one and resets the write shape. */
    private void applyDocument(Uri uri, String name, TextFile.Decoded document, long modified) {
        diskModified = modified;
        conflictPending = false;
        internalChange = true;
        editor.setText(document.text);
        editor.setSelection(0);
        internalChange = false;
        undoManager.reset();
        highlighter.refresh();
        currentUri = uri;
        currentName = name == null || name.length() == 0 ? "Untitled.md" : name;
        documentCharset = document.charset;
        documentEol = document.eol;
        documentBom = document.bom;
        dirty = false;
        saveFailed = false;
        saving = false;
        lastSaveTime = 0;
        lastDraftText = "";
        // a new document opens at its own beginning: keeping the fraction of the document
        // that was on screen would drop the reader into the middle of this one
        previewFraction = 0f;
        lastRenderedMarkdown = null;
        // the file on disk is the truth now, so there is nothing to recover
        files.saveDraft("", uri, currentName, false);
        updateTitleState();
        updateStatus();
        updateUndoButtons();
        renderPreview();
    }

    /**
     * Opens the picker, but never over unsaved work. Previously "Open" replaced the
     * document outright and the edits that were on screen were simply gone.
     */
    private void requestOpenDocument() {
        if (openInFlight) {
            toast(getString(R.string.busy));
            return;
        }
        if (!dirty) {
            launchOpenPicker();
            return;
        }
        Dialogs.builder(this)
                .setTitle(R.string.open_guard_title)
                .setMessage(getString(R.string.open_guard_body, currentName))
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pendingOpenAfterSave = true;
                        if (currentUri == null) saveAs();
                        else saveDocument();
                    }
                })
                .setNeutralButton(R.string.discard, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // the open replaces the document, so the edits are gone on purpose
                        dirty = false;
                        saveFailed = false;
                        launchOpenPicker();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * The address of the chosen document.
     *
     * <p>Nearly every picker returns it as the intent data, but a few OEM file managers
     * put it in the clip data instead, and the previous version simply returned in that
     * case: the picker closed and nothing happened.</p>
     */
    private static Uri pickedUri(ActivityResult result) {
        Intent data = result.getData();
        if (data == null) return null;
        Uri uri = data.getData();
        if (uri != null) return uri;
        ClipData clip = data.getClipData();
        if (clip != null && clip.getItemCount() > 0) {
            return clip.getItemAt(0).getUri();
        }
        return null;
    }

    private void launchOpenPicker() {
        Intent intent = FileController.openIntent();
        try {
            openLauncher.launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            // a device without a document provider can still browse with a content picker
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.addCategory(Intent.CATEGORY_OPENABLE);
                fallback.setType("*/*");
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                openLauncher.launch(fallback);
            } catch (RuntimeException again) {
                toast(getString(R.string.no_picker));
            }
        } catch (RuntimeException e) {
            toast(getString(R.string.no_picker));
        }
    }
    private void restoreDraftOrHandleIntent() {
        Intent intent = getIntent();
        if (handleExternalIntent(intent)) return;
        String draft = files.draftText();
        Uri last = files.lastUri();
        String name = files.lastName();
        if (draft != null && draft.length() > 0) {
            internalChange = true;
            editor.setText(draft);
            editor.setSelection(0);
            internalChange = false;
            undoManager.reset();
            highlighter.refresh();
            currentUri = last;
            if (name != null && name.length() > 0) currentName = name;
            dirty = files.draftDirty();
            documentEol = TextFile.EOL_LF;
            documentCharset = TextFile.UTF8;
            documentBom = false;
            diskModified = last == null ? 0L : files.lastModified(last);
            updateTitleState();
            updateStatus();
            updateUndoButtons();
        } else {
            updateTitleState();
            updateStatus();
            reopenLastFile();
        }
        renderPreview();
    }

    /**
     * Cold start with no draft: put the previous file back on screen instead of an empty
     * page. The existence check runs off the UI thread and a missing file is ignored.
     */
    private void reopenLastFile() {
        final Uri last = files.lastUri();
        if (last == null) return;
        // A user who is already opening something wins; the previous file must never be
        // loaded over a document the picker is about to deliver.
        final int generation = loader == null ? 0 : loader.generation();
        submit(new Runnable() {
            @Override
            public void run() {
                final boolean present = files.exists(last);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (loader == null || loader.generation() != generation) return;
                        if (present && !loader.isLoading() && editor.getText().length() == 0) {
                            openUri(last, false);
                        }
                    }
                });
            }
        });
    }

    /** Keeps the grant so a reopened file still works after a reboot. */
    private void savePersistable(Uri uri) {
        if (uri == null) return;
        try {
            files.takePersistablePermission(uri,
                    getIntent() != null
                            && (getIntent().getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);
        } catch (RuntimeException ignored) {
            // the grant is a bonus, not a requirement
        }
    }

    private boolean handleExternalIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        Uri uri = intent.getData();
        if (uri == null && Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            java.util.ArrayList<Uri> streams =
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (streams != null && streams.size() > 0) uri = streams.get(0);
        }
        if (uri == null && Intent.ACTION_SEND.equals(action)) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri == null) {
            // text shared from another app, or selected text handed over by the system
            CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (shared == null || shared.length() == 0) {
                shared = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            }
            if (shared == null || shared.length() == 0) return false;
            String title = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            openSharedText(shared.toString(),
                    title == null || title.trim().length() == 0
                            ? getString(R.string.shared_name)
                            : TextFile.withMarkdownExtension(title));
            return true;
        }
        boolean readAccess = Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)
                || Intent.ACTION_SEND.equals(action)
                || Intent.ACTION_SEND_MULTIPLE.equals(action);
        if (!readAccess) return false;
        files.takePersistablePermission(uri, false);
        openUriGuarded(uri);
        return true;
    }

    /** Opens a document somebody handed to the app, after guarding unsaved work. */
    private void openUriGuarded(final Uri uri) {
        runWithDiscardGuard(new Runnable() {
            @Override
            public void run() {
                openUri(uri, false);
            }
        });
    }

    /** Turns text handed over by another app into a new, unsaved document. */
    private void openSharedText(final String text, final String name) {
        runWithDiscardGuard(new Runnable() {
            @Override
            public void run() {
                internalChange = true;
                editor.setText(TextFile.normalise(text));
                editor.setSelection(0);
                internalChange = false;
                undoManager.reset();
                highlighter.refresh();
                currentUri = null;
                currentName = TextFile.withMarkdownExtension(name);
                documentEol = TextFile.EOL_LF;
                documentCharset = TextFile.UTF8;
                documentBom = false;
                dirty = true;
                lastSaveTime = 0;
                diskModified = 0L;
                updateTitleState();
                updateStatus();
                updateUndoButtons();
                renderPreview();
                toast(getString(R.string.shared_loaded));
            }
        });
    }

    /**
     * Runs something that replaces the document, after making sure the current one is
     * not lost. Save writes first; Discard continues immediately.
     */
    private void runWithDiscardGuard(final Runnable action) {
        if (!dirty) {
            action.run();
            return;
        }
        Dialogs.builder(this)
                .setTitle(R.string.open_guard_title)
                .setMessage(getString(R.string.open_guard_body, currentName))
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // the action replaces the document, so it runs only after the
                        // write finished - otherwise it would save the new text instead
                        afterSaveAction = action;
                        if (currentUri == null) saveAs();
                        else saveDocument();
                    }
                })
                .setNeutralButton(R.string.discard, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // the action replaces the document, so it is no longer unsaved
                        dirty = false;
                        action.run();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Writes the document on the IO thread. The title line shows "Saving..." while it
     * runs, and the document is only marked clean if the text still matches what was
     * written: typing during a save no longer loses the "unsaved" flag.
     */
    private void saveDocument() {
        if (currentUri == null) {
            saveAs();
            return;
        }
        if (saving) return;
        final Uri uri = currentUri;
        final String text = editor.getText().toString();
        final String charset = documentCharset;
        final String eol = documentEol;
        final boolean bom = documentBom;
        saving = true;
        saveFailed = false;
        updateTitleState();
        submit(new Runnable() {
            @Override
            public void run() {
                try {
                    files.writeDocument(uri, text, charset, eol, bom);
                    final long modified = files.lastModified(uri);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            postSave(uri, text, modified, true);
                        }
                    });
                } catch (final IOException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            saving = false;
                            saveFailed = true;
                            pendingOpenAfterSave = false;
                            afterSaveAction = null;
                            updateTitleState();
                            reportSaveFailure(uri, e);
                        }
                    });
                }
            }
        });
    }

    /** Marks the document clean, but only when it still holds what went to disk. */
    private void postSave(Uri uri, String written, long modified, boolean announce) {
        saving = false;
        if (uri != null && uri.equals(currentUri)) {
            diskModified = modified;
        }
        lastSaveTime = System.currentTimeMillis();
        if (editor.getText().toString().equals(written)) {
            dirty = false;
            saveFailed = false;
            lastDraftText = "";
            files.saveDraft("", currentUri, currentName, false);
        } else {
            // typing continued while the bytes were being written
            dirty = true;
            scheduleAutosave();
        }
        updateTitleState();
        if (announce) toast(getString(R.string.saved));
        runAfterSave();
        resumePendingOpen();
    }

    /** One dialog that says what went wrong, instead of a bare "save failed" toast. */
    private void reportSaveFailure(final Uri uri, IOException e) {
        String detail = e.getMessage();
        if (detail == null || detail.length() == 0) detail = getString(R.string.save_failed);
        final String reason = detail;
        if (e instanceof FileController.PermissionLostException) {
            // the grant is gone: this file cannot be written again, only saved elsewhere
            offerSaveAs(e);
            return;
        }
        if (e instanceof FileController.TooLargeException) {
            Dialogs.builder(this)
                    .setTitle(R.string.save_failed_title)
                    .setMessage(getString(R.string.save_too_large,
                            TextFile.humanSize(TextFile.MAX_BYTES)))
                    .setPositiveButton(R.string.save_as, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            saveAs();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        String name = currentName == null || currentName.length() == 0
                ? "Untitled.md" : currentName;
        Dialogs.builder(this)
                .setTitle(R.string.save_failed_title)
                .setMessage(getString(R.string.save_failed_body, name, reason))
                .setPositiveButton(R.string.save_as, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveAs();
                    }
                })
                .setNeutralButton(R.string.draft_saved, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // keep the text recoverable even though the file is not writable
                        lastDraftText = "";
                        saveDraftNow();
                        toast(getString(R.string.draft_saved));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** The file cannot be written in place any more: suggest somewhere else. */
    private void offerSaveAs(IOException e) {
        saveFailed = true;
        updateTitleState();
        String detail = e == null || e.getMessage() == null
                ? getString(R.string.save_failed) : e.getMessage();
        Dialogs.builder(this)
                .setTitle(R.string.save_failed_title)
                .setMessage(getString(R.string.save_elsewhere, detail))
                .setPositiveButton(R.string.save_as, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveAs();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveAs() {
        if (openInFlight) {
            toast(getString(R.string.busy));
            return;
        }
        pendingAction = PENDING_SAVE_AS;
        String base = currentName == null || currentName.trim().length() == 0
                ? "Untitled.md" : currentName;
        String suggestion = TextFile.withMarkdownExtension(base);
        try {
            createLauncher.launch(
                    FileController.createIntent(suggestion, FileController.MIME_MARKDOWN));
        } catch (RuntimeException e) {
            pendingAction = PENDING_NONE;
            toast(getString(R.string.no_picker));
        }
    }
    /**
     * Autosave runs off the UI thread, and the "changed on disk" check runs there too:
     * a provider that hangs for a second while answering a query used to freeze typing.
     */
    private void autosaveNow() {
        if (!settings.autosave() || currentUri == null || !dirty || conflictPending) return;
        if (saving || openInFlight) return;
        final Uri uri = currentUri;
        final long known = diskModified;
        final String text = editor.getText().toString();
        final String charset = documentCharset;
        final String eol = documentEol;
        final boolean bom = documentBom;
        saving = true;
        submit(new Runnable() {
            @Override
            public void run() {
                if (files.changedSince(uri, known)) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            saving = false;
                            updateTitleState();
                            askOverwriteExternal();
                        }
                    });
                    return;
                }
                try {
                    files.writeDocument(uri, text, charset, eol, bom);
                    final long modified = files.lastModified(uri);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            postSave(uri, text, modified, false);
                        }
                    });
                } catch (final IOException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            saving = false;
                            saveFailed = true;
                            updateTitleState();
                        }
                    });
                }
            }
        });
    }

    /**
     * The file was modified by another app. Autosave stops until the user decides,
     * so a background write can never destroy someone else's changes.
     */
    private void askOverwriteExternal() {
        conflictPending = true;
        Dialogs.builder(this)
                .setTitle(R.string.conflict_title)
                .setMessage(R.string.conflict_body)
                .setPositiveButton(R.string.conflict_overwrite, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conflictPending = false;
                        diskModified = 0L;
                        autosaveNow();
                    }
                })
                .setNeutralButton(R.string.conflict_reload, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conflictPending = false;
                        openUri(currentUri);
                    }
                })
                .setNegativeButton(R.string.conflict_keep, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conflictPending = false;
                        saveDraftNow();
                        toast(getString(R.string.draft_saved));
                    }
                })
                .show();
    }

    private void saveDraftNow() {
        if (editor == null || recoveryPaused) return;
        // Nothing to recover when the file on disk is already the truth, and rewriting an
        // unchanged megabyte to preferences on every pause is pure waste. Both checks come
        // before the copy so a clean document costs nothing.
        if (!dirty && currentUri != null) {
            if (lastDraftText.length() > 0) {
                files.saveDraft("", currentUri, currentName, false);
                lastDraftText = "";
            }
            return;
        }
        if (editor.getText().length() > MAX_DRAFT_CHARS) {
            // a draft this large would stall the UI thread it is written from; the file
            // itself and the undo history still protect the work
            return;
        }
        String text = editor.getText().toString();
        if (text.equals(lastDraftText)) return;
        lastDraftText = text;
        files.saveDraft(text, currentUri, currentName, dirty);
    }

    /**
     * Exports are converted on the IO thread: turning a long document into HTML or RTF
     * parses every block, which used to freeze the UI for a second before the picker.
     */
    private void export(final String mime, final String extension) {
        // one export at a time: a second picker on top of the first is never intended
        if (pendingAction != PENDING_NONE || busyLabel != null) return;
        final String text = editor.getText().toString();
        final String title = currentName == null ? "document" : currentName;
        final boolean dark = colors.dark;
        setBusy(getString(R.string.exporting));
        submit(new Runnable() {
            @Override
            public void run() {
                final String content;
                try {
                    if (FileController.MIME_HTML.equals(mime)) {
                        content = MdConvert.toHtmlDocument(text, title, dark);
                    } else if (FileController.MIME_RTF.equals(mime)) {
                        content = MdConvert.toRtf(text, title);
                    } else {
                        content = MdConvert.toPlainText(text);
                    }
                } catch (RuntimeException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            clearBusy();
                            toast(getString(R.string.export_failed));
                        }
                    });
                    return;
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        clearBusy();
                        pendingAction = PENDING_EXPORT;
                        pendingContent = content;
                        pendingMime = mime;
                        String base = currentName == null ? "document" : currentName;
                        int dot = base.lastIndexOf('.');
                        if (dot > 0) base = base.substring(0, dot);
                        pendingName = base + extension;
                        try {
                            createLauncher.launch(
                                    FileController.createIntent(pendingName, mime));
                        } catch (RuntimeException e) {
                            pendingAction = PENDING_NONE;
                            pendingContent = null;
                            toast(getString(R.string.no_picker));
                        }
                    }
                });
            }
        });
    }

    private void confirmExit() {
        Dialogs.builder(this)
                .setTitle(R.string.confirm_discard_title)
                .setMessage(getString(R.string.confirm_discard_body, currentName))
                .setPositiveButton(R.string.save, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        // blocking on purpose: the activity is about to go away, and a
                        // background write would be cut off with it
                        if (currentUri == null) {
                            saveAs();
                            return;
                        }
                        try {
                            String text = editor.getText().toString();
                            files.writeDocument(currentUri, text, documentCharset, documentEol,
                                    documentBom);
                            dirty = false;
                            // the file on disk is the truth now, so the draft can go
                            files.saveDraft("", currentUri, currentName, false);
                            lastDraftText = "";
                            finish();
                        } catch (IOException e) {
                            offerSaveAs(e);
                        }
                    }
                })
                .setNeutralButton(R.string.discard, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        files.clearDraft();
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void renameDocument() {
        if (currentUri == null) {
            Dialogs.inputPrompt(this, colors, getString(R.string.dialog_rename),
                    getString(R.string.dialog_rename), currentName, new Dialogs.OnText() {
                        @Override
                        public void onText(String value) {
                            if (value != null && value.trim().length() > 0) {
                                currentName = TextFile.withMarkdownExtension(value.trim());
                                updateTitleState();
                                dirty = true;
                                saveDocument();
                            }
                        }
                    });
            return;
        }
        Dialogs.inputPrompt(this, colors, getString(R.string.dialog_rename),
                getString(R.string.dialog_rename), currentName, new Dialogs.OnText() {
                    @Override
                    public void onText(String value) {
                        final String name = value == null ? "" : value.trim();
                        if (name.length() == 0) return;
                        final Uri before = currentUri;
                        submit(new Runnable() {
                            @Override
                            public void run() {
                                Uri renamed = null;
                                try {
                                    renamed = DocumentsContract.renameDocument(
                                            getContentResolver(), before, name);
                                } catch (Exception ignored) {
                                    // providers that cannot rename report null or throw
                                }
                                final Uri target = renamed;
                                final String renamedName =
                                        target == null ? null : files.displayName(target);
                                final long modified = target == null ? 0L
                                        : files.lastModified(target);
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (target == null) {
                                            // the provider has no rename: offer the way out
                                            Dialogs.builder(MainActivity.this)
                                                    .setTitle(R.string.dialog_rename)
                                                    .setMessage(R.string.rename_unsupported)
                                                    .setPositiveButton(R.string.save_as,
                                                            new DialogInterface.OnClickListener() {
                                                                @Override
                                                                public void onClick(
                                                                        DialogInterface dialog,
                                                                        int which) {
                                                                    saveAs();
                                                                }
                                                            })
                                                    .setNegativeButton(R.string.cancel, null)
                                                    .show();
                                            return;
                                        }
                                        currentUri = target;
                                        currentName = renamedName;
                                        if (!target.equals(before)) {
                                            files.removeRecent(before.toString());
                                        }
                                        files.addRecent(target, currentName);
                                        diskModified = modified;
                                        updateTitleState();
                                        toast(getString(R.string.renamed, currentName));
                                    }
                                });
                            }
                        });
                    }
                });
    }

    // =================================================================================
    // 1.6: maths authoring and images from the device
    // =================================================================================

    /** The equation library: sixteen display equations, ten inline snippets, all real TeX. */
    private void showEquationSheet() {
        List<QuickPanel.Item> items = new ArrayList<QuickPanel.Item>();
        for (int i = 0; i < Equations.DISPLAY.length; i++) {
            final Equations.Equation equation = Equations.DISPLAY[i];
            items.add(item(equation.title, equation.hint, new Runnable() {
                public void run() {
                    insertMath(equation.tex, true);
                }
            }));
        }
        for (int i = 0; i < Equations.INLINE.length; i++) {
            final Equations.Equation equation = Equations.INLINE[i];
            items.add(item(equation.title, "inline \u00b7 " + equation.hint, new Runnable() {
                public void run() {
                    insertMath(equation.tex, false);
                }
            }));
        }
        items.add(item(getString(R.string.math_custom), getString(R.string.math_custom_hint),
                new Runnable() {
                    public void run() {
                        MathPreview.show(MainActivity.this, colors, "", true,
                                new MathPreview.OnMath() {
                                    @Override
                                    public void onMath(String tex, boolean display) {
                                        insertMath(tex, display);
                                    }
                                });
                    }
                }));
        QuickPanel.show(this, getString(R.string.math_title), items);
    }

    /**
     * Inserts TeX as markdown maths. A display equation goes on its own lines with blank
     * lines around it, because a {@code $$} block glued to a paragraph is a paragraph in
     * CommonMark; inline maths wraps the selection when there is one.
     */
    private void insertMath(String tex, boolean display) {
        if (tex == null || tex.trim().length() == 0) return;
        String body = tex.trim();
        if (!display) {
            int start = editor.getSelectionStart();
            int end = editor.getSelectionEnd();
            if (start > end) {
                int swap = start;
                start = end;
                end = swap;
            }
            Editable text = editor.getText();
            if (end > start) {
                // wrap what is selected instead of replacing it
                String selected = text.subSequence(start, end).toString();
                text.replace(start, end, "$" + selected + "$");
                editor.setSelection(start + selected.length() + 2);
                return;
            }
            insertBlock("$" + body + "$", body.length() + 1);
            return;
        }
        insertBlock("$$\n" + body + "\n$$\n", body.length() + 4);
        if (mode == MODE_PREVIEW) setMode(MODE_SPLIT, true);
    }

    /** Wraps the selection in inline maths, or inserts a pair at the caret. */
    private void wrapInMath() {
        int start = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
        int end = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
        Editable text = editor.getText();
        if (end > start) {
            String selected = text.subSequence(start, end).toString();
            if (selected.startsWith("$") && selected.endsWith("$") && selected.length() > 2) {
                text.replace(start, end, selected.substring(1, selected.length() - 1));
                editor.setSelection(start + selected.length() - 2);
            } else {
                text.replace(start, end, "$" + selected + "$");
                editor.setSelection(start + selected.length() + 2);
            }
            return;
        }
        text.insert(start, "$$");
        editor.setSelection(start + 1);
    }

    /**
     * Shows the equation around the caret, typeset.
     *
     * <p>Reading TeX inside a wall of prose is the one part of maths authoring that a
     * highlighter cannot help with, so this puts the rendered form one tap away without
     * leaving the document: it finds the enclosing {@code $...$} or {@code $$...$$} span
     * and opens the same KaTeX dialog the inserter uses.</p>
     */
    private void peekMathAtCaret() {
        String text = editor.getText().toString();
        int caret = Math.max(0, Math.min(editor.getSelectionStart(), text.length()));
        int[] span = mathSpanAt(text, caret);
        if (span == null) {
            toast(getString(R.string.math_none));
            return;
        }
        boolean display = span[2] == 1;
        String tex = text.substring(span[0], span[1]);
        if (display && tex.startsWith("$$") && tex.endsWith("$$") && tex.length() >= 4) {
            tex = tex.substring(2, tex.length() - 2).trim();
        } else if (tex.startsWith("$") && tex.endsWith("$") && tex.length() >= 2) {
            tex = tex.substring(1, tex.length() - 1);
        }
        final int from = span[0];
        final int to = span[1];
        MathPreview.show(this, colors, tex, display, new MathPreview.OnMath() {
            @Override
            public void onMath(String updated, boolean stillDisplay) {
                Editable body = editor.getText();
                if (to > body.length()) return;
                String replacement = stillDisplay
                        ? "$$\n" + updated + "\n$$"
                        : "$" + updated + "$";
                body.replace(from, to, replacement);
            }
        });
    }

    /**
     * The maths span around an offset: {@code [start, end, display]} or null.
     *
     * <p>Delimiters are paired in document order inside a window around the caret, which
     * is what makes the answer right in the cases a backwards scan gets wrong: the dollar
     * just before the caret is usually the <em>closing</em> one, and a {@code $$} block has
     * to pair with its own kind. A dollar followed by a digit ("costs $5") never opens a
     * span, and an escaped dollar never counts at all.</p>
     */
    static int[] mathSpanAt(String text, int offset) {
        if (text == null || text.length() == 0) return null;
        int caret = Math.max(0, Math.min(offset, text.length()));
        int from = Math.max(0, caret - 4096);
        int to = Math.min(text.length(), caret + 4096);

        int capacity = 64;
        int[] pos = new int[capacity];
        int[] len = new int[capacity];
        int count = 0;
        for (int p = from; p < to; p++) {
            if (text.charAt(p) != '$') continue;
            if (isEscaped(text, p)) continue;
            int width;
            if (p + 1 < text.length() && text.charAt(p + 1) == '$') {
                width = 2;
            } else {
                if (p + 1 < text.length() && Character.isDigit(text.charAt(p + 1))) continue;
                width = 1;
            }
            if (count == capacity) {
                capacity *= 2;
                int[] grownPos = new int[capacity];
                int[] grownLen = new int[capacity];
                System.arraycopy(pos, 0, grownPos, 0, count);
                System.arraycopy(len, 0, grownLen, 0, count);
                pos = grownPos;
                len = grownLen;
            }
            pos[count] = p;
            len[count] = width;
            count++;
            if (width == 2) p++;
        }

        // pair delimiters in order; a span covers [open, close + width)
        for (int i = 0; i + 1 < count; i++) {
            if (len[i] != len[i + 1]) continue;
            int open = pos[i];
            int close = pos[i + 1];
            int end = close + len[i + 1];
            if (caret >= open && caret <= end) {
                return new int[]{open, end, len[i] == 2 ? 1 : 0};
            }
            i++;   // consumed as a pair
        }
        return null;
    }

    private static boolean isEscaped(String text, int index) {
        int slashes = 0;
        for (int p = index - 1; p >= 0 && text.charAt(p) == '\\'; p--) slashes++;
        return (slashes % 2) == 1;
    }

    /**
     * Adds an image from the device.
     *
     * <p>Two paths, and the user is told which one happened. When the open document lives
     * in a folder that accepts new files, the image is copied next to it and the document
     * gets a relative link, which is what a README needs and what keeps working when the
     * folder is zipped or pushed to a repository. When that is not possible the picked
     * address is inserted directly and the preview serves the bytes itself.</p>
     */
    private void insertImageFromDevice() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            imageLauncher.launch(intent);
        } catch (RuntimeException e) {
            toast(getString(R.string.no_picker));
        }
    }

    private void handlePickedImage(final Uri image) {
        if (image == null) return;
        final String name = files.displayName(image);
        final String safe = TextFile.sanitiseName(name).replaceAll("[^A-Za-z0-9._-]", "-");
        setBusy(getString(R.string.image_copying));
        submit(new Runnable() {
            @Override
            public void run() {
                String inserted = null;
                boolean copied = false;
                try {
                    inserted = files.copyNextTo(currentUri, image, safe);
                    copied = inserted != null;
                } catch (IOException ignored) {
                    // the folder refused the copy: fall back to the picked address
                }
                final String path = copied ? inserted : image.toString();
                final boolean wasCopied = copied;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        clearBusy();
                        String alt = TextFile.extensionOf(safe).length() > 0
                                ? safe.substring(0, safe.lastIndexOf('.')) : safe;
                        insertBlock("![" + alt + "](" + path + ")", 2 + alt.length());
                        toast(getString(wasCopied ? R.string.image_copied : R.string.image_linked,
                                wasCopied ? path : name));
                        renderPreview();
                    }
                });
            }
        });
    }

    // =================================================================================
    // 1.5: file actions - rename, duplicate, details
    // =================================================================================

    /** The "File actions" sheet: everything that acts on the file rather than the text. */
    private void showFileActions() {
        List<QuickPanel.Item> items = new ArrayList<QuickPanel.Item>();
        items.add(item(getString(R.string.action_rename),
                getString(R.string.action_rename_hint), new Runnable() {
                    @Override
                    public void run() {
                        renameDocument();
                    }
                }));
        items.add(item(getString(R.string.action_duplicate),
                getString(R.string.action_duplicate_hint), new Runnable() {
                    @Override
                    public void run() {
                        duplicateDocument();
                    }
                }));
        items.add(item(getString(R.string.action_details),
                getString(R.string.action_details_hint), new Runnable() {
                    @Override
                    public void run() {
                        showFileDetails();
                    }
                }));
        items.add(item(getString(R.string.save_as),
                getString(R.string.save_as_hint), new Runnable() {
                    @Override
                    public void run() {
                        saveAs();
                    }
                }));
        items.add(item(getString(R.string.menu_share_file),
                getString(R.string.action_share_hint), new Runnable() {
                    @Override
                    public void run() {
                        shareFile();
                    }
                }));
        QuickPanel.show(this, getString(R.string.file_actions), items);
    }

    /** Writes a copy and keeps editing the original, which is what "duplicate" means. */
    private void duplicateDocument() {
        if (openInFlight) {
            toast(getString(R.string.busy));
            return;
        }
        pendingAction = PENDING_DUPLICATE;
        String base = currentName == null || currentName.trim().length() == 0
                ? "Untitled.md" : currentName;
        String suggestion = TextFile.copyName(base);
        try {
            createLauncher.launch(
                    FileController.createIntent(suggestion, FileController.MIME_MARKDOWN));
        } catch (RuntimeException e) {
            pendingAction = PENDING_NONE;
            toast(getString(R.string.no_picker));
        }
    }

    /** Name, size, change time, line count, line endings and encoding of the open file. */
    private void showFileDetails() {
        final Uri uri = currentUri;
        final String name = currentName == null ? "Untitled.md" : currentName;
        final String charset = documentCharset;
        final String eol = documentEol;
        final boolean bom = documentBom;
        final String text = editor.getText().toString();
        submit(new Runnable() {
            @Override
            public void run() {
                final long reported = uri == null ? -1L : files.sizeOf(uri);
                final long modified = uri == null ? 0L : files.lastModified(uri);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder body = new StringBuilder();
                        body.append(getString(R.string.details_name, name));
                        long size = reported;
                        if (size < 0L) {
                            // providers without a size column: the text itself is the answer
                            size = TextFile.encode(text, charset, eol, bom).length;
                        }
                        body.append('\n').append(getString(R.string.details_size,
                                TextFile.humanSize(size)));
                        body.append('\n').append(getString(R.string.details_lines,
                                String.valueOf(countLines(text))));
                        body.append('\n').append(getString(R.string.details_modified,
                                modified > 0L ? UiUtil.relativeTime(modified)
                                        : getString(R.string.details_unknown)));
                        body.append('\n').append(getString(R.string.details_eol,
                                TextFile.EOL_CRLF.equals(eol)
                                        ? getString(R.string.details_windows)
                                        : getString(R.string.details_unix)));
                        body.append('\n').append(getString(R.string.details_encoding,
                                charset + (bom ? getString(R.string.details_bom) : "")));
                        Dialogs.builder(MainActivity.this)
                                .setTitle(R.string.action_details)
                                .setMessage(body.toString())
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }
                });
            }
        });
    }

    private static int countLines(String text) {
        if (text == null || text.length() == 0) return 0;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    // =================================================================================
    // 1.4: palette, blocks, templates, text tools, paste, read aloud, PDF
    // =================================================================================

    private QuickPanel.Item item(String title, String hint, Runnable action) {
        return new QuickPanel.Item(title, hint, action);
    }

    private void showPalette() {
        QuickPanel.show(this, getString(R.string.palette_title), commandItems());
    }

    /** The slash menu: opens on a "/" typed at the start of an empty line. */
    private void openSlashMenu(int slashOffset) {
        try {
            Editable text = editor.getText();
            if (slashOffset < text.length() && text.charAt(slashOffset) == '/') {
                text.delete(slashOffset, slashOffset + 1);
                editor.setSelection(Math.min(slashOffset, text.length()));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "slash menu", e);
            return;
        }
        QuickPanel.show(this, getString(R.string.blocks_title), blockItems());
    }

    // ---------------------------------------------------------------- commands

    private List<QuickPanel.Item> commandItems() {
        List<QuickPanel.Item> items = new ArrayList<QuickPanel.Item>();

        items.add(item("New document", "File", new Runnable() {
            public void run() {
                newDocument();
            }
        }));
        items.add(item("New from template", "File", new Runnable() {
            public void run() {
                showTemplates();
            }
        }));
        items.add(item(getString(R.string.math_insert), "Maths - equation library",
                new Runnable() {
                    public void run() {
                        showEquationSheet();
                    }
                }));
        items.add(item(getString(R.string.math_custom), "Maths - live TeX editor",
                new Runnable() {
                    public void run() {
                        MathPreview.show(MainActivity.this, colors, "", true,
                                new MathPreview.OnMath() {
                                    @Override
                                    public void onMath(String tex, boolean display) {
                                        insertMath(tex, display);
                                    }
                                });
                    }
                }));
        items.add(item(getString(R.string.math_peek), "Maths - render the span at the cursor",
                new Runnable() {
                    public void run() {
                        peekMathAtCaret();
                    }
                }));
        items.add(item("Wrap in maths", "Maths - $selection$", new Runnable() {
            public void run() {
                wrapInMath();
            }
        }));
        items.add(item(getString(R.string.image_pick), "Insert - copy the file next to the document",
                new Runnable() {
                    public void run() {
                        insertImageFromDevice();
                    }
                }));
        items.add(item("Open file", "File", new Runnable() {
            public void run() {
                requestOpenDocument();
            }
        }));
        items.add(item("File actions", "Rename, duplicate, details", new Runnable() {
            public void run() {
                showFileActions();
            }
        }));
        items.add(item("Rename file", "File", new Runnable() {
            public void run() {
                renameDocument();
            }
        }));
        items.add(item("Duplicate file", "File - keeps editing this one", new Runnable() {
            public void run() {
                duplicateDocument();
            }
        }));
        items.add(item("File details", "Size, change time, line endings", new Runnable() {
            public void run() {
                showFileDetails();
            }
        }));
        items.add(item("Save", "File", new Runnable() {
            public void run() {
                if (currentUri == null) saveAs();
                else saveDocument();
            }
        }));
        items.add(item("Save as", "File", new Runnable() {
            public void run() {
                saveAs();
            }
        }));
        items.add(item("Recent files", "File", new Runnable() {
            public void run() {
                Dialogs.recents(MainActivity.this, files, new Dialogs.OnText() {
                    public void onText(String value) {
                        if (value != null) openUri(Uri.parse(value), true);
                    }
                }, new Runnable() {
                    public void run() {
                        toast(getString(R.string.clear_recents));
                    }
                });
            }
        }));
        items.add(item("Export HTML", "Share", new Runnable() {
            public void run() {
                export(FileController.MIME_HTML, ".html");
            }
        }));
        items.add(item("Export PDF", "Share - through the system print service", new Runnable() {
            public void run() {
                exportPdf();
            }
        }));
        items.add(item("Export plain text", "Share", new Runnable() {
            public void run() {
                export(FileController.MIME_TEXT, ".txt");
            }
        }));
        items.add(item("Share as file", "Share", new Runnable() {
            public void run() {
                shareFile();
            }
        }));
        items.add(item("Copy whole document", "Share", new Runnable() {
            public void run() {
                UiUtil.copy(MainActivity.this, editor.getText().toString(), currentName);
                toast(getString(R.string.copy_ok));
            }
        }));
        items.add(item("Paste as Markdown", "Convert HTML on the clipboard", new Runnable() {
            public void run() {
                pasteAsMarkdown();
            }
        }));
        items.add(item("Paste as table", "CSV or TSV on the clipboard", new Runnable() {
            public void run() {
                pasteAsTable();
            }
        }));

        items.add(item("Bold", "Format", new Runnable() {
            public void run() {
                actions.bold();
            }
        }));
        items.add(item("Italic", "Format", new Runnable() {
            public void run() {
                actions.italic();
            }
        }));
        items.add(item("Strikethrough", "Format", new Runnable() {
            public void run() {
                actions.strike();
            }
        }));
        items.add(item("Inline code", "Format", new Runnable() {
            public void run() {
                actions.inlineCode();
            }
        }));
        items.add(item("Link", "Format", new Runnable() {
            public void run() {
                actions.link();
            }
        }));
        items.add(item("Image", "Format", new Runnable() {
            public void run() {
                actions.image();
            }
        }));
        items.add(item("Table", "Format", new Runnable() {
            public void run() {
                actions.table();
            }
        }));
        items.add(item("Code block", "Format", new Runnable() {
            public void run() {
                actions.codeBlock();
            }
        }));
        items.add(item("Quote", "Format", new Runnable() {
            public void run() {
                actions.quote();
            }
        }));
        items.add(item("Bulleted list", "Format", new Runnable() {
            public void run() {
                actions.bulletList();
            }
        }));
        items.add(item("Numbered list", "Format", new Runnable() {
            public void run() {
                actions.orderedList();
            }
        }));
        items.add(item("Task list", "Format", new Runnable() {
            public void run() {
                actions.taskList();
            }
        }));
        items.add(item("Horizontal rule", "Format", new Runnable() {
            public void run() {
                actions.horizontalRule();
            }
        }));

        items.add(item("Insert table of contents", "Structure", new Runnable() {
            public void run() {
                actions.insertToc();
            }
        }));
        items.add(item("Insert front matter", "Structure", new Runnable() {
            public void run() {
                actions.insertFrontMatter();
            }
        }));
        items.add(item("Insert date", "Structure", new Runnable() {
            public void run() {
                actions.insertDate();
            }
        }));
        items.add(item("Tidy document", "Structure - spacing, headings, lists", new Runnable() {
            public void run() {
                actions.tidy();
            }
        }));
        items.add(item("Strip trailing whitespace", "Structure", new Runnable() {
            public void run() {
                actions.stripWhitespace();
            }
        }));
        items.add(item("Text tools", "Sort, case, encode, JSON and more", new Runnable() {
            public void run() {
                showTextTools();
            }
        }));
        items.add(item("Sort lines A-Z", "Lines", new Runnable() {
            public void run() {
                actions.sortLines(false);
            }
        }));
        items.add(item("Sort lines Z-A", "Lines", new Runnable() {
            public void run() {
                actions.sortLines(true);
            }
        }));
        items.add(item("Duplicate line", "Lines", new Runnable() {
            public void run() {
                actions.duplicateLine();
            }
        }));
        items.add(item("Move line up", "Lines", new Runnable() {
            public void run() {
                actions.moveLine(-1);
            }
        }));
        items.add(item("Move line down", "Lines", new Runnable() {
            public void run() {
                actions.moveLine(1);
            }
        }));

        items.add(item("Find", "Navigate", new Runnable() {
            public void run() {
                showFind(true);
            }
        }));
        items.add(item("Go to line", "Navigate", new Runnable() {
            public void run() {
                Dialogs.goToLine(MainActivity.this, editor, colors);
            }
        }));
        items.add(item("Outline", "Navigate", new Runnable() {
            public void run() {
                Dialogs.outline(MainActivity.this, editor.getText().toString(), colors,
                        new Dialogs.OnJump() {
                            public void onJump(int line, int offset) {
                                jumpToOffset(offset >= 0 ? offset
                                        : SmartEdit.offsetForLine(editor.getText(), line));
                            }
                        });
            }
        }));
        items.add(item("Document checks", "Navigate", new Runnable() {
            public void run() {
                Dialogs.issues(MainActivity.this, editor.getText().toString(), colors,
                        new Dialogs.OnJump() {
                            public void onJump(int line, int offset) {
                                jumpToOffset(offset >= 0 ? offset
                                        : SmartEdit.offsetForLine(editor.getText(), line));
                            }
                        });
            }
        }));
        items.add(item("Statistics", "Navigate", new Runnable() {
            public void run() {
                Dialogs.stats(MainActivity.this, editor.getText().toString(), colors);
            }
        }));

        items.add(item("Read aloud", "Speak the document, or stop", new Runnable() {
            public void run() {
                toggleReadAloud(null);
            }
        }));
        items.add(item("Edit only", "View", new Runnable() {
            public void run() {
                setMode(MODE_EDIT, true);
            }
        }));
        items.add(item("Split view", "View", new Runnable() {
            public void run() {
                setMode(MODE_SPLIT, true);
            }
        }));
        items.add(item("Preview only", "View", new Runnable() {
            public void run() {
                setMode(MODE_PREVIEW, true);
            }
        }));
        items.add(item("Focus mode", "View", new Runnable() {
            public void run() {
                applyFocusMode(!focusMode, true);
            }
        }));
        items.add(item("Word wrap", "View", new Runnable() {
            public void run() {
                settings.set(Settings.KEY_SOFT_WRAP, !settings.softWrap());
                editor.setSoftWrapEnabled(settings.softWrap());
            }
        }));
        items.add(item("Light or dark", "View", new Runnable() {
            public void run() {
                settings.setThemeMode(colors.dark ? "light" : "dark");
                recreate();
            }
        }));
        items.add(item("MarkUltra+ syntax", "Render syntax beyond GitHub", new Runnable() {
            public void run() {
                boolean rich = !settings.richMarkdown();
                settings.setRichMarkdown(rich);
                toast(rich ? getString(R.string.rich_on) : getString(R.string.rich_off));
                scheduleRender(60);
            }
        }));
        items.add(item("Settings", "App", new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }));
        items.add(item("Cheat sheet", "App", new Runnable() {
            public void run() {
                Dialogs.cheatSheet(MainActivity.this, colors);
            }
        }));
        return items;
    }

    // ---------------------------------------------------------------- blocks

    private List<QuickPanel.Item> blockItems() {
        List<QuickPanel.Item> items = new ArrayList<QuickPanel.Item>();
        for (int level = 1; level <= 3; level++) {
            final int l = level;
            items.add(item("Heading " + level, "Section title, level " + level,
                    new Runnable() {
                        public void run() {
                            actions.headingLevel(l);
                        }
                    }));
        }
        items.add(item("Bulleted list", "- item", new Runnable() {
            public void run() {
                actions.bulletList();
            }
        }));
        items.add(item("Numbered list", "1. item", new Runnable() {
            public void run() {
                actions.orderedList();
            }
        }));
        items.add(item("Task list", "- [ ] item", new Runnable() {
            public void run() {
                actions.taskList();
            }
        }));
        items.add(item("Quote", "> quoted", new Runnable() {
            public void run() {
                actions.quote();
            }
        }));
        items.add(item("Alert", "> [!NOTE] block", new Runnable() {
            public void run() {
                insertBlock("> [!NOTE]\n> Something worth knowing.\n", 11);
            }
        }));
        items.add(item("Warning alert", "> [!WARNING] block", new Runnable() {
            public void run() {
                insertBlock("> [!WARNING]\n> Something that can go wrong.\n", 14);
            }
        }));
        items.add(item("Table", "3 columns, 3 rows", new Runnable() {
            public void run() {
                actions.table();
            }
        }));
        items.add(item("Code block", "Fenced, with a language", new Runnable() {
            public void run() {
                actions.codeBlock();
            }
        }));
        items.add(item("Mermaid diagram", "flowchart skeleton", new Runnable() {
            public void run() {
                insertBlock("```mermaid\nflowchart LR\n    A[Start] --> B{Check}\n"
                        + "    B -->|yes| C[Done]\n    B -->|no| A\n```\n", 0);
            }
        }));
        items.add(item("Maths equation", "Type TeX, see it typeset",
                new Runnable() {
                    public void run() {
                        showEquationSheet();
                    }
                }));
        items.add(item("Inline maths", "$E = mc^2$", new Runnable() {
            public void run() {
                MathPreview.show(MainActivity.this, colors, "", false,
                        new MathPreview.OnMath() {
                            @Override
                            public void onMath(String tex, boolean display) {
                                insertMath(tex, false);
                            }
                        });
            }
        }));
        items.add(item("Display maths", "$$ ... $$", new Runnable() {
            public void run() {
                MathPreview.show(MainActivity.this, colors, "", true,
                        new MathPreview.OnMath() {
                            @Override
                            public void onMath(String tex, boolean display) {
                                insertMath(tex, true);
                            }
                        });
            }
        }));
        items.add(item("Image from device", "Copy it next to the document",
                new Runnable() {
                    public void run() {
                        insertImageFromDevice();
                    }
                }));
        items.add(item("Details block", "<details><summary>", new Runnable() {
            public void run() {
                actions.insertDetails();
            }
        }));
        items.add(item("Footnote", "with the note at the bottom", new Runnable() {
            public void run() {
                actions.footnote();
            }
        }));
        items.add(item("Link", "[text](url)", new Runnable() {
            public void run() {
                actions.link();
            }
        }));
        items.add(item("Image", "![alt](path)", new Runnable() {
            public void run() {
                actions.image();
            }
        }));
        items.add(item("Horizontal rule", "---", new Runnable() {
            public void run() {
                actions.horizontalRule();
            }
        }));
        items.add(item("Table of contents", "Links to every heading", new Runnable() {
            public void run() {
                actions.insertToc();
            }
        }));
        items.add(item("Front matter", "YAML header", new Runnable() {
            public void run() {
                actions.insertFrontMatter();
            }
        }));
        items.add(item("Today's date", "insert the date", new Runnable() {
            public void run() {
                actions.insertDate();
            }
        }));
        items.add(item("Definition list", "MarkUltra+ only", new Runnable() {
            public void run() {
                insertBlock("Term\n: Definition of the term\n", 0);
            }
        }));
        items.add(item("Admonition", "MarkUltra+ only - !!! note", new Runnable() {
            public void run() {
                insertBlock("!!! note \"Title\"\n    Something worth knowing.\n", 0);
            }
        }));
        items.add(item("Wiki link", "MarkUltra+ only - [[Note]]", new Runnable() {
            public void run() {
                insertBlock("[[Another note]]", 2);
            }
        }));
        return items;
    }

    private void insertBlock(String markdown, int caretOffset) {
        SmartEdit.insertBlock(editor, markdown, caretOffset);
        if (mode == MODE_PREVIEW) setMode(MODE_SPLIT, true);
    }

    // ---------------------------------------------------------------- templates

    private void showTemplates() {
        List<Templates.Template> all = Templates.all();
        List<QuickPanel.Item> items = new ArrayList<QuickPanel.Item>();
        final CharSequence[] names = new CharSequence[all.size()];
        for (int i = 0; i < all.size(); i++) {
            final Templates.Template t = all.get(i);
            names[i] = t.name;
            items.add(item(t.name, t.hint, new Runnable() {
                public void run() {
                    applyTemplate(t);
                }
            }));
        }
        QuickPanel.show(this, getString(R.string.templates_title), items);
    }

    private void applyTemplate(final Templates.Template template) {
        String body = template.body;
        int caret = body.indexOf(Templates.CURSOR);
        if (caret < 0) caret = body.length();
        final String clean = body.replace(Templates.CURSOR, "");
        final int caretAt = Math.min(caret, clean.length());
        Runnable apply = new Runnable() {
            public void run() {
                Editable text = editor.getText();
                text.replace(0, text.length(), clean);
                editor.setSelection(caretAt);
                currentUri = null;
                currentName = templateName(template);
                dirty = true;
                updateTitleState();
                scheduleRender(60);
            }
        };
        if (editor.getText().length() > 0) {
            Dialogs.confirm(this, getString(R.string.templates_title),
                    getString(R.string.template_replace), getString(R.string.menu_new), apply);
        } else {
            apply.run();
        }
    }

    private static String templateName(Templates.Template template) {
        String name = template.name.toLowerCase(Locale.ROOT).replace(' ', '-');
        return "blank".equals(name) ? "Untitled.md" : name + ".md";
    }

    // ---------------------------------------------------------------- text tools

    private void showTextTools() {
        List<TextTools.Op> ops = TextTools.ops();
        List<QuickPanel.Item> items = new ArrayList<QuickPanel.Item>();
        for (int i = 0; i < ops.size(); i++) {
            final TextTools.Op op = ops.get(i);
            items.add(item(op.label, op.hint, new Runnable() {
                public void run() {
                    runTextOp(op.id);
                }
            }));
        }
        QuickPanel.show(this, getString(R.string.text_tools_title), items);
    }

    private void runTextOp(String id) {
        Editable text = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }
        if (end > start) {
            String piece = text.subSequence(start, end).toString();
            String result = TextTools.apply(id, piece);
            text.replace(start, end, result);
            editor.setSelection(start + result.length());
        } else {
            String all = text.toString();
            String result = TextTools.apply(id, all);
            int caret = Math.min(editor.getSelectionStart(), result.length());
            text.replace(0, text.length(), result);
            editor.setSelection(caret);
        }
    }

    // ---------------------------------------------------------------- paste

    private boolean handlePaste() {
        if (!settings.pasteRich()) return false;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return false;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return false;
            ClipData.Item item = clip.getItemAt(0);
            CharSequence html = item.getHtmlText();
            if (html == null) return false;
            String markdown = PasteConvert.htmlToMarkdown(html.toString());
            if (markdown == null) return false;
            SmartEdit.insertBlock(editor, markdown, -1);
            if (mode == MODE_PREVIEW) setMode(MODE_SPLIT, true);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "paste conversion failed", e);
            return false;
        }
    }

    private void pasteAsMarkdown() {
        String html = clipboardHtml();
        String markdown = html == null ? null : PasteConvert.htmlToMarkdown(html);
        if (markdown == null) {
            toast(getString(R.string.paste_nothing));
            return;
        }
        SmartEdit.insertBlock(editor, markdown, -1);
        if (mode == MODE_PREVIEW) setMode(MODE_SPLIT, true);
    }

    private void pasteAsTable() {
        String text = clipboardText();
        String table = text == null ? null : PasteConvert.tableToMarkdown(text);
        if (table == null) {
            toast(getString(R.string.paste_no_table));
            return;
        }
        SmartEdit.insertBlock(editor, table, -1);
        if (mode == MODE_PREVIEW) setMode(MODE_SPLIT, true);
    }

    private String clipboardHtml() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) return null;
        CharSequence html = cm.getPrimaryClip().getItemAt(0).getHtmlText();
        return html == null ? null : html.toString();
    }

    private String clipboardText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null
                || cm.getPrimaryClip().getItemCount() == 0) return null;
        CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        return text == null ? null : text.toString();
    }

    // ---------------------------------------------------------------- read aloud

    private void toggleReadAloud(MenuItem item) {
        try {
            if (tts != null && tts.isSpeaking()) {
                tts.stop();
                if (item != null) item.setTitle(R.string.menu_read_aloud);
                toast(getString(R.string.read_stopped));
                return;
            }
            if (textIsEmpty()) {
                toast(getString(R.string.read_nothing));
                return;
            }
            if (tts == null) {
                tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        speakNow();
                    }
                });
            } else {
                speakNow();
            }
            if (item != null) item.setTitle(R.string.menu_stop_reading);
        } catch (RuntimeException e) {
            Log.w(TAG, "read aloud", e);
        }
    }

    private boolean textIsEmpty() {
        return editor.getText().toString().trim().length() == 0;
    }

    private void speakNow() {
        if (tts == null) return;
        String plain = MdConvert.toPlainText(editor.getText().toString());
        String title = currentName == null ? "" : currentName;
        tts.speak(title + ". " + plain, TextToSpeech.QUEUE_FLUSH, null, "markultra");
    }

    // ---------------------------------------------------------------- PDF

    private void exportPdf() {
        if (preview == null) {
            toast(getString(R.string.pdf_wait));
            return;
        }
        try {
            if (mode == MODE_EDIT) setMode(MODE_SPLIT, true);
            PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (pm == null) {
                toast(getString(R.string.pdf_wait));
                return;
            }
            String job = currentName == null ? "MarkUltra" : currentName.replace(".md", "");
            PrintDocumentAdapter adapter = preview.createPrintDocumentAdapter(job);
            pm.print(job, adapter, new PrintAttributes.Builder().build());
        } catch (RuntimeException e) {
            Log.w(TAG, "print failed", e);
            toast(getString(R.string.pdf_wait));
        }
    }

    // =================================================================================
    // menus
    // =================================================================================

    private void showMainMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
        Menu menu = popup.getMenu();
        setChecked(menu, R.id.action_wrap, settings.softWrap());
        setChecked(menu, R.id.action_line_numbers, settings.lineNumbers());
        setChecked(menu, R.id.action_typewriter, settings.typewriter());
        setChecked(menu, R.id.action_syntax, settings.highlight());
        setChecked(menu, R.id.action_amoled, settings.isAmoled());
        setChecked(menu, R.id.action_scroll_sync, settings.scrollSync());
        setChecked(menu, R.id.action_keep_on, settings.keepScreenOn());
        setChecked(menu, R.id.action_focus, focusMode);
        setChecked(menu, R.id.action_rich, settings.richMarkdown());
        setChecked(menu, R.id.action_paste_rich, settings.pasteRich());
        MenuItem themeItem = menu.findItem(R.id.action_theme);
        if (themeItem != null) {
            themeItem.setTitle(colors.dark ? R.string.menu_theme_light : R.string.menu_theme_dark);
        }
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return handleMenuItem(item);
            }
        });
        popup.show();
    }

    private void showFileMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return handleMenuItem(item);
            }
        });
        popup.show();
    }

    private void showInsertMenu() {
        List<Dialogs.Action> items = new ArrayList<Dialogs.Action>();
        items.add(new Dialogs.Action(getString(R.string.fmt_heading), new Runnable() {
            @Override
            public void run() {
                actions.heading();
            }
        }));
        items.add(new Dialogs.Action("Highlight ==", new Runnable() {
            @Override
            public void run() {
                actions.highlight();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.math_insert), new Runnable() {
            @Override
            public void run() {
                showEquationSheet();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.math_peek), new Runnable() {
            @Override
            public void run() {
                peekMathAtCaret();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.image_pick), new Runnable() {
            @Override
            public void run() {
                insertImageFromDevice();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_insert_date), new Runnable() {
            @Override
            public void run() {
                actions.insertDate();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_insert_time), new Runnable() {
            @Override
            public void run() {
                actions.insertTime();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_insert_counter), new Runnable() {
            @Override
            public void run() {
                actions.insertWordCount();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_insert_toc), new Runnable() {
            @Override
            public void run() {
                actions.insertToc();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_insert_details), new Runnable() {
            @Override
            public void run() {
                actions.insertDetails();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_insert_front_matter), new Runnable() {
            @Override
            public void run() {
                actions.insertFrontMatter();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_duplicate), new Runnable() {
            @Override
            public void run() {
                actions.duplicateLine();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_delete_line), new Runnable() {
            @Override
            public void run() {
                actions.deleteLine();
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_move_up), new Runnable() {
            @Override
            public void run() {
                actions.moveLine(-1);
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_move_down), new Runnable() {
            @Override
            public void run() {
                actions.moveLine(1);
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_indent), new Runnable() {
            @Override
            public void run() {
                actions.indent(false);
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_outdent), new Runnable() {
            @Override
            public void run() {
                actions.indent(true);
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_sort_lines), new Runnable() {
            @Override
            public void run() {
                actions.sortLines(false);
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_sort_lines_desc), new Runnable() {
            @Override
            public void run() {
                actions.sortLines(true);
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_upper), new Runnable() {
            @Override
            public void run() {
                actions.transformCase(SmartEdit.CaseMode.UPPER);
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_lower), new Runnable() {
            @Override
            public void run() {
                actions.transformCase(SmartEdit.CaseMode.LOWER);
            }
        }));
        items.add(new Dialogs.Action(getString(R.string.menu_title_case), new Runnable() {
            @Override
            public void run() {
                actions.transformCase(SmartEdit.CaseMode.TITLE);
            }
        }));
        Dialogs.actions(this, getString(R.string.menu_insert), items);
    }

    private static void setChecked(Menu menu, int id, boolean checked) {
        MenuItem item = menu.findItem(id);
        if (item != null) item.setChecked(checked);
    }

    private boolean handleMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new) {
            newDocument();
        } else if (id == R.id.action_open) {
            requestOpenDocument();
        } else if (id == R.id.action_file_actions) {
            showFileActions();
        } else if (id == R.id.action_save) {
            saveDocument();
        } else if (id == R.id.action_recent) {
            Dialogs.recents(this, files, new Dialogs.OnText() {
                @Override
                public void onText(String value) {
                    if (value != null) openUri(Uri.parse(value), true);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    toast(getString(R.string.clear_recents));
                }
            });
        } else if (id == R.id.action_save_as) {
            saveAs();
        } else if (id == R.id.action_rename) {
            renameDocument();
        } else if (id == R.id.action_export_html) {
            export(FileController.MIME_HTML, ".html");
        } else if (id == R.id.action_export_txt) {
            export(FileController.MIME_TEXT, ".txt");
        } else if (id == R.id.action_export_rtf) {
            export(FileController.MIME_RTF, ".rtf");
        } else if (id == R.id.action_share) {
            UiUtil.shareText(this, currentName, editor.getText().toString());
        } else if (id == R.id.action_share_file) {
            shareFile();
        } else if (id == R.id.action_copy_all) {
            UiUtil.copy(this, editor.getText().toString(), currentName);
            toast(getString(R.string.copy_ok));
        } else if (id == R.id.action_find) {
            showFind(true);
        } else if (id == R.id.action_goto) {
            Dialogs.goToLine(this, editor, colors);
        } else if (id == R.id.action_outline) {
            Dialogs.outline(this, editor.getText().toString(), colors, new Dialogs.OnJump() {
                @Override
                public void onJump(int line, int offset) {
                    jumpToOffset(offset >= 0 ? offset
                            : SmartEdit.offsetForLine(editor.getText(), line));
                }
            });
        } else if (id == R.id.action_issues) {
            Dialogs.issues(this, editor.getText().toString(), colors, new Dialogs.OnJump() {
                @Override
                public void onJump(int line, int offset) {
                    jumpToOffset(offset >= 0 ? offset
                            : SmartEdit.offsetForLine(editor.getText(), line));
                }
            });
        } else if (id == R.id.action_stats) {
            Dialogs.stats(this, editor.getText().toString(), colors);
        } else if (id == R.id.action_toc) {
            actions.insertToc();
        } else if (id == R.id.action_tidy) {
            actions.tidy();
        } else if (id == R.id.action_strip_ws) {
            actions.stripWhitespace();
        } else if (id == R.id.action_table) {
            actions.table();
        } else if (id == R.id.action_insert_details) {
            actions.insertDetails();
        } else if (id == R.id.action_insert_front_matter) {
            actions.insertFrontMatter();
        } else if (id == R.id.action_insert_date) {
            actions.insertDate();
        } else if (id == R.id.action_focus) {
            applyFocusMode(!focusMode, true);
        } else if (id == R.id.action_wrap) {
            settings.set(Settings.KEY_SOFT_WRAP, !settings.softWrap());
            editor.setSoftWrapEnabled(settings.softWrap());
            toast(settings.softWrap() ? getString(R.string.wrap_on) : getString(R.string.wrap_off));
        } else if (id == R.id.action_line_numbers) {
            settings.set(Settings.KEY_LINE_NUMBERS, !settings.lineNumbers());
            editor.setLineNumbers(settings.lineNumbers());
        } else if (id == R.id.action_typewriter) {
            settings.set(Settings.KEY_TYPEWRITER, !settings.typewriter());
            editor.setTypewriter(settings.typewriter());
        } else if (id == R.id.action_syntax) {
            settings.set(Settings.KEY_HIGHLIGHT, !settings.highlight());
            highlighter.setEnabled(settings.highlight());
        } else if (id == R.id.action_amoled) {
            settings.setAmoled(!settings.isAmoled());
            recreate();
        } else if (id == R.id.action_theme) {
            settings.setThemeMode(colors.dark ? "light" : "dark");
            recreate();
        } else if (id == R.id.action_scroll_sync) {
            settings.set(Settings.KEY_SCROLL_SYNC, !settings.scrollSync());
        } else if (id == R.id.action_keep_on) {
            boolean keep = !settings.keepScreenOn();
            settings.set(Settings.KEY_KEEP_SCREEN_ON, keep);
            applyKeepScreenOn();
        } else if (id == R.id.action_palette) {
            showPalette();
        } else if (id == R.id.action_blocks) {
            QuickPanel.show(this, getString(R.string.blocks_title), blockItems());
        } else if (id == R.id.action_templates) {
            showTemplates();
        } else if (id == R.id.action_text_tools) {
            showTextTools();
        } else if (id == R.id.action_read_aloud) {
            toggleReadAloud(item);
        } else if (id == R.id.action_export_pdf) {
            exportPdf();
        } else if (id == R.id.action_rich) {
            boolean rich = !settings.richMarkdown();
            settings.setRichMarkdown(rich);
            toast(rich ? getString(R.string.rich_on) : getString(R.string.rich_off));
            scheduleRender(60);
        } else if (id == R.id.action_paste_rich) {
            boolean on = !settings.pasteRich();
            settings.setPasteRich(on);
            toast(on ? getString(R.string.paste_rich_on) : getString(R.string.paste_rich_off));
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.action_cheatsheet) {
            Dialogs.cheatSheet(this, colors);
        } else if (id == R.id.action_about) {
            Dialogs.about(this, colors, versionName());
        } else {
            return false;
        }
        return true;
    }

    private void newDocument() {
        runWithDiscardGuard(new Runnable() {
            @Override
            public void run() {
                showNewDocumentMenu();
            }
        });
    }

    private void showNewDocumentMenu() {
        final String[][] templates = {
                {"Blank document", ""},
                {"README", "# Project name\n\nOne line description of the project.\n\n"
                        + "## Install\n\n```bash\nnpm install\n```\n\n"
                        + "## Usage\n\n```js\nimport { thing } from 'thing';\n```\n\n"
                        + "## License\n\nMIT\n"},
                {"Blog post", "# Title\n\n*Draft - " + today() + "*\n\n"
                        + "Opening paragraph that sets the scene.\n\n## Section\n\nBody text.\n\n"
                        + "> A pull quote worth remembering.\n\n## Takeaways\n\n- Point one\n- Point two\n"},
                {"Meeting notes", "# Meeting - " + today() + "\n\n## Attendees\n\n- \n\n"
                        + "## Agenda\n\n1. \n2. \n\n## Decisions\n\n- \n\n## Actions\n\n"
                        + "- [ ] Owner - task\n- [ ] Owner - task\n"},
                {"Task list", "# Tasks\n\n## Now\n\n- [ ] First task\n- [ ] Second task\n\n"
                        + "## Later\n\n- [ ] Someday\n\n## Done\n\n- [x] Finished item\n"},
        };
        List<Dialogs.Action> items = new ArrayList<Dialogs.Action>();
        for (int i = 0; i < templates.length; i++) {
            final String name = templates[i][0];
            final String body = templates[i][1];
            items.add(new Dialogs.Action(name, new Runnable() {
                @Override
                public void run() {
                    createFromTemplate(name, body);
                }
            }));
        }
        Dialogs.actions(this, getString(R.string.menu_new), items);
    }

    private void createFromTemplate(String name, String body) {
        Runnable apply = new Runnable() {
            @Override
            public void run() {
                internalChange = true;
                editor.setText(body);
                editor.setSelection(0);
                internalChange = false;
                undoManager.reset();
                highlighter.refresh();
                currentUri = null;
                currentName = name.toLowerCase(Locale.ROOT).replace(' ', '-') + ".md";
                documentEol = TextFile.EOL_LF;
                documentCharset = TextFile.UTF8;
                documentBom = false;
                lastSaveTime = 0;
                diskModified = 0L;
                dirty = true;
                updateTitleState();
                updateStatus();
                updateUndoButtons();
                renderPreview();
                files.clearDraft();
            }
        };
        runWithDiscardGuard(apply);
    }

    private String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.7.2";
        }
    }

    /** Writes the document to the private cache and shares it through FileProvider. */
    /**
     * Shares the current text as a file. The bytes are written on the IO thread and in
     * the same shape as the open document: sharing a 4 MB note used to stall the UI
     * before the chooser had even appeared.
     */
    private void shareFile() {
        final String text = editor.getText().toString();
        final String charset = documentCharset;
        final String eol = documentEol;
        final boolean bom = documentBom;
        final String name = TextFile.withMarkdownExtension(
                TextFile.sanitiseName(currentName == null || currentName.trim().length() == 0
                        ? "document.md" : currentName));
        submit(new Runnable() {
            @Override
            public void run() {
                try {
                    java.io.File dir = new java.io.File(getCacheDir(), "share");
                    if (!dir.exists() && !dir.mkdirs()) throw new IOException("no cache");
                    // the folder only ever holds shares: drop yesterday's copies
                    java.io.File[] stale = dir.listFiles();
                    if (stale != null) {
                        for (int i = 0; i < stale.length; i++) stale[i].delete();
                    }
                    String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
                    java.io.File out = new java.io.File(dir, safe);
                    java.io.FileOutputStream stream = new java.io.FileOutputStream(out);
                    try {
                        stream.write(TextFile.encode(text, charset, eol, bom));
                        stream.flush();
                    } finally {
                        stream.close();
                    }
                    final Uri uri = androidx.core.content.FileProvider.getUriForFile(
                            MainActivity.this, getPackageName() + ".fileprovider", out);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(Intent.ACTION_SEND);
                            intent.setType(FileController.MIME_MARKDOWN);
                            intent.putExtra(Intent.EXTRA_STREAM, uri);
                            intent.putExtra(Intent.EXTRA_SUBJECT, name);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try {
                                startActivity(Intent.createChooser(intent,
                                        getString(R.string.menu_share_file)));
                            } catch (RuntimeException e) {
                                toast(getString(R.string.share_failed));
                            }
                        }
                    });
                } catch (final IOException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            toast(getString(R.string.share_failed));
                        }
                    });
                }
            }
        });
    }

    private void applyKeepScreenOn() {
        if (settings.keepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) focus = editor;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        editor.clearFocus();
    }
}
