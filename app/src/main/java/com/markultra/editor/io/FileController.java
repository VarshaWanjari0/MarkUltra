package com.markultra.editor.io;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import com.markultra.editor.prefs.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Storage Access Framework plumbing: reading, writing, naming, recents and drafts.
 * No storage permissions are required on any API level because everything goes
 * through the SAF document picker.
 */
public final class FileController {

    public static final String MIME_MARKDOWN = "text/markdown";
    public static final String MIME_TEXT = "text/plain";
    public static final String MIME_HTML = "text/html";
    public static final String MIME_RTF = "application/rtf";
    private static final int MAX_RECENTS = 12;

    public static final class Document {
        public final Uri uri;
        public final String name;
        public final String content;
        public final boolean isNew;

        public Document(Uri uri, String name, String content, boolean isNew) {
            this.uri = uri;
            this.name = name;
            this.content = content;
            this.isNew = isNew;
        }
    }

    public static final class Recent {
        public final String uri;
        public final String name;
        public final long time;

        Recent(String uri, String name, long time) {
            this.uri = uri;
            this.name = name;
            this.time = time;
        }
    }

    private final Context context;
    private final Settings settings;

    public FileController(Context context) {
        this.context = context.getApplicationContext();
        this.settings = Settings.get(context);
    }

    // ---------------------------------------------------------------------------------
    // intents
    // ---------------------------------------------------------------------------------

    /**
     * The document picker.
     *
     * <p>Write access is requested as well as read: without it the picker hands back a
     * read-only grant, the editor opens the file happily, and every later save fails with
     * a permission error the user cannot do anything about. Asking up front is what makes
     * "open, edit, save" work on every provider.</p>
     */
    public static Intent openIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/markdown", "text/x-markdown", "text/plain", "text/*", "*/*"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    public static Intent createIntent(String suggestedName, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    public void takePersistablePermission(Uri uri, boolean write) {
        try {
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (write) flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            context.getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
            // provider may not support persistable grants; the session still works
        }
    }

    // ---------------------------------------------------------------------------------
    // read / write
    // ---------------------------------------------------------------------------------

    public String read(Uri uri) throws IOException {
        return readDocument(uri).text;
    }

    /** Reads a document with its charset, BOM and line ending style. */
    public TextFile.Decoded readDocument(Uri uri) throws IOException {
        byte[] data = readBytes(uri);
        if (TextFile.looksBinary(data)) throw new NotTextException();
        return TextFile.decode(data);
    }

    /** Reads the raw bytes, refusing anything larger than {@link TextFile#MAX_BYTES}. */
    public byte[] readBytes(Uri uri) throws IOException {
        if (uri == null) throw new IOException("No document");
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("The provider returned no data");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(16 * 1024);
            byte[] chunk = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > TextFile.MAX_BYTES) throw new TooLargeException();
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } catch (SecurityException e) {
            throw new PermissionLostException();
        } finally {
            closeQuietly(in);
        }
    }

    public void write(Uri uri, String text) throws IOException {
        writeDocument(uri, text, TextFile.UTF8, TextFile.EOL_LF, false);
    }

    /**
     * Writes a document back in the shape it was read.
     *
     * <p>Providers disagree about output modes: most accept {@code "wt"}, some only
     * {@code "w"}, a few reject both and only work with a plain stream. Trying them in
     * order is what stops "save failed" on cloud storage.</p>
     */
    public void writeDocument(Uri uri, String text, String charset, String eol, boolean bom)
            throws IOException {
        if (uri == null) throw new IOException("No document");
        byte[] data = TextFile.encode(text, charset, eol, bom);
        IOException failure = null;
        String[] modes = {"wt", "w", "rwt", null};
        for (int i = 0; i < modes.length; i++) {
            OutputStream out = null;
            try {
                ContentResolver resolver = context.getContentResolver();
                out = modes[i] == null
                        ? resolver.openOutputStream(uri)
                        : resolver.openOutputStream(uri, modes[i]);
                if (out == null) throw new IOException("The provider returned no stream");
                out.write(data);
                out.flush();
                return;
            } catch (IOException e) {
                failure = e;
            } catch (RuntimeException e) {
                // some providers throw for output modes they do not implement
                failure = new IOException(e.getMessage(), e);
            } finally {
                closeQuietly(out);
            }
        }
        if (failure != null) throw failure;
        throw new IOException("Could not write the document");
    }

    /**
     * Copies {@code source} next to {@code document} and returns the relative name.
     *
     * <p>This is what makes {@code ![](diagram.png)} work: the image has to live beside
     * the Markdown file, not in a picker URI that only this app can read and only until
     * the device reboots. Returns {@code null} when the document is not inside a tree
     * that accepts new files, which is the caller's signal to link the source instead.</p>
     */
    public String copyNextTo(Uri document, Uri source, String fileName) throws IOException {
        if (document == null || source == null) return null;
        if (!"content".equals(document.getScheme())) return null;
        String parent;
        try {
            String docId = DocumentsContract.getDocumentId(document);
            int slash = docId.lastIndexOf('/');
            int colon = docId.indexOf(':');
            if (slash <= colon) return null;      // the document is at the tree root
            parent = docId.substring(0, slash);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Uri parentUri;
        String parentDocumentId;
        try {
            String tree = DocumentsContract.getTreeDocumentId(document);
            parentDocumentId = tree + ":" + parent.substring(parent.indexOf(':') + 1);
            parentUri = DocumentsContract.buildDocumentUriUsingTree(document, parentDocumentId);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Uri created = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            created = DocumentsContract.createDocument(context.getContentResolver(), parentUri,
                    mimeOf(fileName), fileName);
            if (created == null) return null;
            in = context.getContentResolver().openInputStream(source);
            if (in == null) throw new IOException("The image could not be read");
            out = context.getContentResolver().openOutputStream(created, "w");
            if (out == null) throw new IOException("The image could not be written");
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > TextFile.MAX_BYTES) throw new TooLargeException();
                out.write(buffer, 0, read);
            }
            out.flush();
            return fileName;
        } catch (SecurityException e) {
            throw new PermissionLostException();
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    /** The MIME type for a file name, or a generic binary type. */
    private static String mimeOf(String fileName) {
        String ext = TextFile.extensionOf(fileName);
        if ("png".equals(ext)) return "image/png";
        if ("jpg".equals(ext) || "jpeg".equals(ext)) return "image/jpeg";
        if ("gif".equals(ext)) return "image/gif";
        if ("webp".equals(ext)) return "image/webp";
        if ("svg".equals(ext)) return "image/svg+xml";
        if ("bmp".equals(ext)) return "image/bmp";
        return "application/octet-stream";
    }

    /** True when the document can still be read (a permission may have expired). */
    public boolean exists(Uri uri) {
        if (uri == null) return false;
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            return in != null;
        } catch (Exception e) {
            return false;
        } finally {
            closeQuietly(in);
        }
    }

    /** Size reported by the provider, or -1 when it does not say. */
    public long sizeOf(Uri uri) {
        if (uri == null) return -1L;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
            // providers without a size column are fine, the dialog shows "unknown"
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1L;
    }

    /** Larger than the editor will accept. */
    public static final class TooLargeException extends IOException {
        public TooLargeException() {
            super("Larger than " + TextFile.humanSize(TextFile.MAX_BYTES));
        }
    }

    /** Bytes that are clearly not text. */
    public static final class NotTextException extends IOException {
        public NotTextException() {
            super("Not a text file");
        }
    }

    /** The grant has expired or was revoked. */
    public static final class PermissionLostException extends IOException {
        public PermissionLostException() {
            super("No permission");
        }
    }

    public String displayName(Uri uri) {
        if (uri == null) return "Untitled.md";
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && name.length() > 0) return name;
                }
            }
        } catch (Exception ignored) {
            // fall through to the path based name
        } finally {
            if (cursor != null) cursor.close();
        }
        String path = uri.getLastPathSegment();
        if (path == null) return "Untitled.md";
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    // ---------------------------------------------------------------------------------
    // change detection
    // ---------------------------------------------------------------------------------

    /**
     * Last-modified timestamp reported by the provider, or -1 when it does not expose
     * one. Used to notice that another app edited the file before an autosave silently
     * overwrites it.
     */
    public long lastModified(Uri uri) {
        if (uri == null) return -1L;
        android.database.Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
            // Providers that do not implement the column simply report "unknown".
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1L;
    }

    /**
     * True when the file changed on disk after {@code known} was recorded. Providers
     * that report no timestamp are treated as unchanged so autosave keeps working.
     */
    public boolean changedSince(Uri uri, long known) {
        if (known <= 0L) return false;
        long now = lastModified(uri);
        return now > 0L && now > known + 1000L;
    }

    // ---------------------------------------------------------------------------------
    // recents
    // ---------------------------------------------------------------------------------

    public List<Recent> recents() {
        List<Recent> list = new ArrayList<Recent>();
        String raw = settings.getString(Settings.KEY_RECENTS, "");
        if (raw == null || raw.length() == 0) return list;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length() && i < MAX_RECENTS; i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String uri = o.optString("uri");
                if (uri.length() == 0) continue;
                list.add(new Recent(uri, o.optString("name", "Document"), o.optLong("time", 0)));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public void addRecent(Uri uri, String name) {
        if (uri == null) return;
        List<Recent> existing = recents();
        List<Recent> next = new ArrayList<Recent>(MAX_RECENTS);
        next.add(new Recent(uri.toString(), name, System.currentTimeMillis()));
        for (int i = 0; i < existing.size() && next.size() < MAX_RECENTS; i++) {
            if (!existing.get(i).uri.equals(uri.toString())) next.add(existing.get(i));
        }
        save(next);
    }

    public void removeRecent(String uri) {
        List<Recent> existing = recents();
        List<Recent> next = new ArrayList<Recent>(existing.size());
        for (int i = 0; i < existing.size(); i++) {
            if (!existing.get(i).uri.equals(uri)) next.add(existing.get(i));
        }
        save(next);
    }

    public void clearRecents() {
        settings.setString(Settings.KEY_RECENTS, "");
    }

    private void save(List<Recent> list) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < list.size(); i++) {
            try {
                JSONObject o = new JSONObject();
                o.put("uri", list.get(i).uri);
                o.put("name", list.get(i).name);
                o.put("time", list.get(i).time);
                array.put(o);
            } catch (JSONException ignored) {
            }
        }
        settings.setString(Settings.KEY_RECENTS, array.toString());
    }

    // ---------------------------------------------------------------------------------
    // drafts
    // ---------------------------------------------------------------------------------

    public void saveDraft(String text, Uri uri, String name) {
        saveDraft(text, uri, name, true);
    }

    /**
     * Stores the recovery draft in one commit.
     *
     * <p>The four values used to be four separate {@code apply()} calls, which meant four
     * preference writes per pause; one editor does the same work once.</p>
     */
    public void saveDraft(String text, Uri uri, String name, boolean dirty) {
        settings.raw().edit()
                .putString(Settings.KEY_DRAFT, text == null ? "" : text)
                .putString(Settings.KEY_LAST_URI, uri == null ? "" : uri.toString())
                .putString(Settings.KEY_LAST_NAME, name == null ? "" : name)
                .putBoolean(Settings.KEY_DRAFT_DIRTY, dirty)
                .apply();
    }

    public boolean draftDirty() {
        return settings.is(Settings.KEY_DRAFT_DIRTY, true);
    }

    public String draftText() {
        return settings.getString(Settings.KEY_DRAFT, "");
    }

    public Uri lastUri() {
        String raw = settings.getString(Settings.KEY_LAST_URI, "");
        return raw.length() == 0 ? null : Uri.parse(raw);
    }

    public String lastName() {
        return settings.getString(Settings.KEY_LAST_NAME, "");
    }

    public void clearDraft() {
        settings.raw().edit()
                .putString(Settings.KEY_DRAFT, "")
                .putString(Settings.KEY_LAST_URI, "")
                .putString(Settings.KEY_LAST_NAME, "")
                .putBoolean(Settings.KEY_DRAFT_DIRTY, false)
                .apply();
    }

    private static void closeQuietly(java.io.Closeable stream) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }
}
