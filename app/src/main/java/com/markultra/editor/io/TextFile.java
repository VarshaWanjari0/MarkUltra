package com.markultra.editor.io;

import java.io.UnsupportedEncodingException;

/**
 * Byte level text handling for documents: byte order marks, line endings, charset
 * detection and the human strings the file dialogs show.
 *
 * <p>Deliberately free of Android so the whole class is covered by the JVM self test. A
 * document opened in MarkUltra comes back out the way it went in: a Windows file keeps
 * its CRLF pairs, a file that started with a BOM still starts with one, and a UTF-16 file
 * is not silently rewritten as UTF-8.</p>
 */
public final class TextFile {

    public static final String EOL_LF = "\n";
    public static final String EOL_CRLF = "\r\n";

    public static final String UTF8 = "UTF-8";
    public static final String UTF16LE = "UTF-16LE";
    public static final String UTF16BE = "UTF-16BE";

    /** Hard ceiling for a document, so a stray binary cannot exhaust the heap. */
    public static final long MAX_BYTES = 16L * 1024L * 1024L;

    private TextFile() {
    }

    /** A decoded document plus everything needed to write it back unchanged. */
    public static final class Decoded {
        public final String text;
        public final String charset;
        public final boolean bom;
        public final String eol;

        public Decoded(String text, String charset, boolean bom, String eol) {
            this.text = text;
            this.charset = charset;
            this.bom = bom;
            this.eol = eol;
        }
    }

    private static final Decoded EMPTY = new Decoded("", UTF8, false, EOL_LF);

    /**
     * Decodes a file. Line endings are normalised to {@code \n} for editing and the
     * original style is remembered in {@link Decoded#eol}.
     */
    public static Decoded decode(byte[] data) {
        if (data == null || data.length == 0) return EMPTY;
        String charset = UTF8;
        boolean bom = false;
        int offset = 0;
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            offset = 3;
            bom = true;
        } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            charset = UTF16LE;
            offset = 2;
            bom = true;
        } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            charset = UTF16BE;
            offset = 2;
            bom = true;
        }
        String raw = newString(data, offset, data.length - offset, charset);
        String eol = detectEol(raw);
        return new Decoded(normalise(raw), charset, bom, eol);
    }

    /** True for bytes that clearly are not text, judged by a NUL byte in the first 4 KB. */
    public static boolean looksBinary(byte[] data) {
        if (data == null || data.length == 0) return false;
        if (data.length >= 2 && ((data[0] & 0xFF) == 0xFF || (data[0] & 0xFF) == 0xFE)) {
            // a UTF-16 byte order mark: NULs are expected, not a binary file
            return false;
        }
        int limit = Math.min(data.length, 4096);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) return true;
        }
        return false;
    }

    /** Encodes with the charset, line endings and BOM the document was read with. */
    public static byte[] encode(String text, String charset, String eol, boolean bom) {
        String body = text == null ? "" : text;
        if (EOL_CRLF.equals(eol)) {
            body = body.replace(EOL_CRLF, EOL_LF).replace(EOL_LF, EOL_CRLF);
        }
        String cs = charset == null ? UTF8 : charset;
        byte[] bodyBytes = bytes(body, cs);
        if (!bom) return bodyBytes;
        byte[] head;
        if (UTF16LE.equals(cs)) {
            head = new byte[]{(byte) 0xFF, (byte) 0xFE};
        } else if (UTF16BE.equals(cs)) {
            head = new byte[]{(byte) 0xFE, (byte) 0xFF};
        } else {
            head = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        }
        byte[] out = new byte[head.length + bodyBytes.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(bodyBytes, 0, out, head.length, bodyBytes.length);
        return out;
    }

    /** The dominant line ending of a block of text, defaulting to LF. */
    public static String detectEol(String text) {
        if (text == null || text.length() == 0) return EOL_LF;
        int crlf = 0;
        int lf = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (i > 0 && text.charAt(i - 1) == '\r') crlf++;
                else lf++;
            }
        }
        return crlf > lf ? EOL_CRLF : EOL_LF;
    }

    /** Turns CRLF and lone CR into LF, so the editor never sees a carriage return. */
    public static String normalise(String text) {
        if (text == null) return "";
        if (text.indexOf('\r') < 0) return text;
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    // ------------------------------------------------------------------ display helpers

    /** "512 B", "12.4 KB", "3.1 MB" - what a file manager would show. */
    public static String humanSize(long bytes) {
        if (bytes < 0) return "unknown size";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return round1(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return round1(mb) + " MB";
        return round1(mb / 1024.0) + " GB";
    }

    private static String round1(double value) {
        long scaled = Math.round(value * 10.0);
        return (scaled / 10) + "." + Math.abs(scaled % 10);
    }

    /** "README.md" -> "md"; "" when there is no extension. */
    public static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Strips path separators and control characters from a name typed by the user. */
    public static String sanitiseName(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c < 0x20) continue;
            sb.append(c);
        }
        String out = sb.toString().trim();
        while (out.startsWith(".")) out = out.substring(1);
        return out.length() == 0 ? "Untitled.md" : out;
    }

    /** Adds a .md extension when the name has none. */
    public static String withMarkdownExtension(String name) {
        String clean = sanitiseName(name);
        return extensionOf(clean).length() == 0 ? clean + ".md" : clean;
    }

    /** "notes.md" -> "notes (copy).md" */
    public static String copyName(String name) {
        String clean = sanitiseName(name);
        int dot = clean.lastIndexOf('.');
        String base = dot > 0 ? clean.substring(0, dot) : clean;
        String ext = dot > 0 ? clean.substring(dot) : "";
        return base + " (copy)" + ext;
    }

    // ------------------------------------------------------------------ internals

    private static String newString(byte[] data, int offset, int length, String charset) {
        try {
            return new String(data, offset, length, charset);
        } catch (UnsupportedEncodingException e) {
            return new String(data, offset, length);
        }
    }

    private static byte[] bytes(String text, String charset) {
        try {
            return text.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            return text.getBytes();
        }
    }
}
