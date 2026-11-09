package com.markultra.editor.ui;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.markultra.editor.md.GfmHtml;
import com.markultra.editor.io.RelativeImage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Answers the preview page's requests for the app's own files.
 *
 * <p>Every page built by {@link GfmHtml#page} asks a made-up host
 * ({@link GfmHtml#ASSET_HOST}) for its scripts, stylesheet and fonts, and this client
 * serves them straight out of the APK with the MIME type the WebView needs in order to
 * execute or apply them.</p>
 *
 * <p>The address matters: a {@code file://} sub-resource of a page built with
 * {@code loadDataWithBaseURL} can be refused outright by a WebView - silently, with the
 * script never running and no console message - and that is what a real device did with
 * KaTeX, Mermaid, Graphviz, PlantUML and Vega at once. An intercepted {@code https}
 * request has no such rule.</p>
 */
public class AssetClient extends WebViewClient {

    private final Context context;

    public AssetClient(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri url = request == null ? null : request.getUrl();
        if (url == null || !GfmHtml.ASSET_HOST.equals(url.getHost())) return null;
        String path = url.getPath();
        if (path == null || !path.startsWith("/assets/")) return null;
        String name = path.substring("/assets/".length());
        if (name.length() == 0) return null;
        try {
            InputStream in = context.getAssets().open(name);
            String mime = RelativeImage.mimeOf(name);
            // a script decoded as Latin-1 does not parse: state the encoding
            String enc = mime.startsWith("text/") || mime.endsWith("javascript")
                    || mime.endsWith("json") ? "UTF-8" : null;
            return new WebResourceResponse(mime, enc, in);
        } catch (IOException e) {
            return null;
        }
    }
}
