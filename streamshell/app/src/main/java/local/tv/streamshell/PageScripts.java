package local.tv.streamshell;

import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Registers the scripts that run at the start of every HBO Max document, before the page's own
 * code, in the page's own JavaScript world and in every frame. This is the same position a
 * browser extension's content script gets, which is what the ad-blocking rules rely on.
 */
final class PageScripts {
    private static final String TAG = "StreamShell";

    /** Built once; reused if the WebView has to be recreated. */
    private static String adRules;
    private static String widevineFallback;

    private PageScripts() {}

    /** @return false if this WebView cannot inject at document start, in which case nothing is blocked. */
    static boolean install(WebView webView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.e(TAG, "This WebView cannot inject scripts at document start; ads will not be blocked");
            return false;
        }
        Context context = webView.getContext();
        try {
            if (widevineFallback == null) {
                widevineFallback = read(context, "scripts/widevine-fallback.js");
            }
            if (adRules == null) {
                String version = read(context, "rules/VERSION").trim();
                // uBlock Origin Lite's files are designed to run back to back in this order.
                adRules = "console.info('[streamshell] ad rules " + version + " on ' + location.hostname);\n"
                        + read(context, "rules/scriptlet-begin.js") + "\n"
                        + read(context, "rules/ublock-filters.js") + "\n"
                        + read(context, "rules/scriptlet-end.js") + "\n";
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not load page scripts; ads will not be blocked", e);
            return false;
        }
        WebViewCompat.addDocumentStartJavaScript(webView, widevineFallback, Sites.HBO_ORIGIN_RULES);
        WebViewCompat.addDocumentStartJavaScript(webView, adRules, Sites.HBO_ORIGIN_RULES);
        return true;
    }

    private static String read(Context context, String asset) throws IOException {
        try (InputStream in = context.getAssets().open(asset)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(in.available(), 1024));
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
