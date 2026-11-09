package com.markultra.editor.io;

import java.util.Locale;

/**
 * Resolves the relative image paths a README uses ({@code docs/banner.png},
 * {@code ../logo.svg}) against the document that is open, the way GitHub resolves them
 * against the repository.
 *
 * <p>The preview page is loaded from {@code file:///android_asset/}, so a relative
 * {@code src} arrives as {@code file:///android_asset/docs/banner.png}; everything that
 * is not one of the app's own assets is treated as a sibling of the open document.</p>
 *
 * <p>Only string handling lives here, so the rules can be tested without Android.</p>
 */
public final class RelativeImage {

    private RelativeImage() {
    }

    /** Every file the app ships in {@code assets/}, in the order the page loads them. */
    private static final String[] BUNDLED = {
            "katex.min.js", "katex.css", "mhchem.min.js", "mermaid.min.js",
            "viz-global.js", "vega.min.js", "vega-lite.min.js", "plantuml.js", "diagrams.js"
    };

    /**
     * True when the requested asset path is one of the app's own bundled files: a script,
     * a stylesheet, or one of the KaTeX font faces.
     *
     * <p>This has to list every asset, not just the two scripts 1.7 shipped. Anything it
     * does not claim is treated as a file next to the open document and is looked up in
     * the document's folder instead - so a mismatched list silently hands the page the
     * wrong bytes (or an empty stream from a cloud provider) for its own scripts, and the
     * diagrams never appear.</p>
     */
    public static boolean isBundledAsset(String path) {
        if (path == null || path.length() == 0) return false;
        String name = path.startsWith("/") ? path.substring(1) : path;
        if (name.startsWith("fonts/")) return true;
        for (int i = 0; i < BUNDLED.length; i++) {
            if (BUNDLED[i].equals(name)) return true;
        }
        if (name.startsWith("../") || name.indexOf("\\") >= 0) return false;
        return false;
    }

    /**
     * Normalises {@code base} + {@code relative} into a slash separated path without
     * {@code .} or {@code ..} segments. Returns null when the path escapes the root,
     * which is how a README referencing {@code ../../../etc/passwd} is stopped.
     */
    public static String join(String base, String relative) {
        if (relative == null) return null;
        String path = relative.replace('\\', '/');
        if (path.length() == 0) return null;
        if (path.startsWith("/")) {
            // absolute paths stay inside the document root
            path = path.substring(1);
            base = "";
        }
        StringBuilder combined = new StringBuilder();
        if (base != null && base.length() > 0) {
            combined.append(base.replace('\\', '/'));
            if (combined.length() > 0 && combined.charAt(combined.length() - 1) != '/') {
                combined.append('/');
            }
        }
        combined.append(path);
        String[] parts = combined.toString().split("/");
        java.util.ArrayList<String> stack = new java.util.ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() == 0 || part.equals(".")) continue;
            if (part.equals("..")) {
                if (stack.isEmpty()) return null;
                stack.remove(stack.size() - 1);
                continue;
            }
            stack.add(part);
        }
        if (stack.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) out.append('/');
            out.append(stack.get(i));
        }
        return out.toString();
    }

    /** Directory part of a path, with a trailing slash, or "" when it has none. */
    public static String directoryOf(String path) {
        if (path == null) return "";
        String normal = path.replace('\\', '/');
        int slash = normal.lastIndexOf('/');
        return slash < 0 ? "" : normal.substring(0, slash + 1);
    }

    /** File name part of a path. */
    public static String nameOf(String path) {
        if (path == null) return "";
        String normal = path.replace('\\', '/');
        int slash = normal.lastIndexOf('/');
        return slash < 0 ? normal : normal.substring(slash + 1);
    }

    /** MIME type for the extensions a README normally points at. */
    public static String mimeOf(String path) {
        String name = nameOf(path).toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".bmp")) return "image/bmp";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".json")) return "application/json";
        // The preview loads scripts, stylesheets and font files of its own: serving any of
        // them as application/octet-stream makes the WebView refuse to execute or apply it.
        if (name.endsWith(".js") || name.endsWith(".mjs")) return "application/javascript";
        if (name.endsWith(".wasm")) return "application/wasm";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".woff")) return "font/woff";
        if (name.endsWith(".ttf")) return "font/ttf";
        if (name.endsWith(".otf")) return "font/otf";
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html";
        if (name.endsWith(".txt") || name.endsWith(".md")) return "text/plain";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        return "application/octet-stream";
    }

    /** True for the image types the preview can draw. */
    public static boolean isImage(String path) {
        return mimeOf(path).startsWith("image/");
    }
}
