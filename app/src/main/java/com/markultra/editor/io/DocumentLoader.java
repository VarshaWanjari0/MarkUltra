package com.markultra.editor.io;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.ViewModel;

import java.io.IOException;

/**
 * Opens documents in a way that survives the activity being recreated.
 *
 * <p>This class exists because of a bug that was reported as "the Open button does
 * nothing". The old code started the read on the activity's own executor and posted the
 * result back to the activity's handler. The document picker is a heavy activity, so
 * Android routinely destroys the activity underneath it while the user browses; on return
 * a fresh activity is built, the read finishes in the old, dead one, and its UI update is
 * delivered to a window nobody is looking at. The file had been read, and nothing changed
 * on screen - exactly the "only sometimes it opens" symptom, because it depended on
 * whether the system happened to kill us.</p>
 *
 * <p>A {@link ViewModel} lives across that recreation. It owns the executor, the read and
 * the result; whoever is on screen at the time of delivery gets it. A result that arrives
 * while no listener is attached waits for the next one instead of being dropped.</p>
 */
public final class DocumentLoader extends ViewModel {

    private static final String TAG = "DocumentLoader";

    public static final int IDLE = 0;
    public static final int LOADING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;

    /** What a completed read produced. */
    public static final class Loaded {
        public final Uri uri;
        public final String name;
        public final TextFile.Decoded document;
        public final long modified;

        Loaded(Uri uri, String name, TextFile.Decoded document, long modified) {
            this.uri = uri;
            this.name = name;
            this.document = document;
            this.modified = modified;
        }
    }

    /** Receives the outcome on the main thread, in whichever activity is alive. */
    public interface Listener {
        void onDocumentOpened(Loaded loaded);

        void onDocumentFailed(Uri uri, IOException error, boolean fromRecents);

        void onOpenStarted();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private java.util.concurrent.ExecutorService io;
    private Listener listener;

    private int state = IDLE;
    private int generation;
    private Loaded result;
    private Uri failedUri;
    private IOException failure;
    private boolean failedFromRecents;
    private boolean delivered = true;
    private Uri loadingUri;

    /** True while a read is running, whoever started it. */
    public boolean isLoading() {
        return state == LOADING;
    }

    public int state() {
        return state;
    }

    public Uri loadingUri() {
        return loadingUri;
    }

    /** The generation of the newest request; used to ignore stale work. */
    public int generation() {
        return generation;
    }

    /**
     * Attaches the visible activity. If a read completed while nothing was attached, it
     * is delivered immediately.
     */
    public void setListener(Listener target) {
        listener = target;
        if (!delivered) deliver();
    }

    public void clearListener(Listener target) {
        if (listener == target) listener = null;
    }

    /**
     * Starts reading {@code uri}. Any read still running is superseded: its result is
     * thrown away rather than applied over the newer one.
     */
    public void load(final Context context, final Uri uri, final boolean fromRecents) {
        if (uri == null) return;
        final int mine = ++generation;
        final Context app = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        loadingUri = uri;
        state = LOADING;
        delivered = true;
        result = null;
        failure = null;
        if (listener != null) listener.onOpenStarted();
        executor().execute(new Runnable() {
            @Override
            public void run() {
                final FileController files = new FileController(app);
                Loaded read = null;
                IOException problem = null;
                try {
                    String name = files.displayName(uri);
                    TextFile.Decoded document = files.readDocument(uri);
                    long modified = files.lastModified(uri);
                    files.addRecent(uri, name);
                    read = new Loaded(uri, name, document, modified);
                } catch (IOException e) {
                    problem = e;
                } catch (RuntimeException e) {
                    // a provider that throws runtime exceptions still failed to open
                    problem = new IOException(
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
                }
                final Loaded ok = read;
                final IOException bad = problem;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mine != generation) {
                            Log.i(TAG, "dropping superseded open of " + uri);
                            return;
                        }
                        loadingUri = null;
                        if (ok != null) {
                            state = DONE;
                            result = ok;
                            failedUri = null;
                            failure = null;
                        } else {
                            state = FAILED;
                            failedUri = uri;
                            failure = bad == null ? new IOException("Could not open the document")
                                    : bad;
                            failedFromRecents = fromRecents;
                        }
                        delivered = false;
                        deliver();
                    }
                });
            }
        });
    }

    /** Hands a finished result to the listener, if there is one. */
    private void deliver() {
        if (delivered || listener == null) return;
        if (state == DONE && result != null) {
            Loaded loaded = result;
            delivered = true;
            result = null;
            listener.onDocumentOpened(loaded);
        } else if (state == FAILED) {
            Uri uri = failedUri;
            IOException error = failure;
            boolean recents = failedFromRecents;
            delivered = true;
            failedUri = null;
            failure = null;
            listener.onDocumentFailed(uri, error, recents);
        }
    }

    private synchronized java.util.concurrent.ExecutorService executor() {
        if (io == null || io.isShutdown()) {
            io = java.util.concurrent.Executors.newSingleThreadExecutor();
        }
        return io;
    }

    @Override
    protected void onCleared() {
        if (io != null) io.shutdownNow();
        listener = null;
        super.onCleared();
    }
}
