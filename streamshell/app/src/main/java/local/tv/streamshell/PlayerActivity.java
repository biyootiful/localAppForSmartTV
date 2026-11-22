package local.tv.streamshell;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * The whole app: one full-screen WebView showing HBO Max's web player, with the ad-blocking
 * page scripts installed and a pointer driven by the TV remote.
 */
public class PlayerActivity extends Activity {
    private static final String TAG = "StreamShell";
    private static final String STATE_URL = "url";

    private FrameLayout root;
    private WebView webView;
    private VirtualMouse mouse;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private String lastUrl = Sites.HOME_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Lets desktop Chrome on the Mac inspect the page (chrome://inspect) while developing.
        WebView.setWebContentsDebuggingEnabled(true);

        root = new FrameLayout(this);
        mouse = new VirtualMouse(this);
        setContentView(root);
        if (savedInstanceState != null) {
            lastUrl = savedInstanceState.getString(STATE_URL, Sites.HOME_URL);
        }
        createWebView();
    }

    /** Builds a fresh WebView; also used to recover when Android kills the page process. */
    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF000000);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        DesktopIdentity.apply(webView);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        PageScripts.install(webView);
        webView.setWebViewClient(new PageClient());
        webView.setWebChromeClient(new ChromeClient());

        root.removeAllViews();
        root.addView(webView, matchParent());
        root.addView(mouse, matchParent());
        mouse.setTarget(webView);
        webView.requestFocus();
        webView.loadUrl(lastUrl);
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    // ---- Remote control -------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) mouse.nudge(0, -1, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) mouse.nudge(0, 1, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) mouse.nudge(-1, 0, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) mouse.nudge(1, 0, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // Enter from a real keyboard belongs to the page (submitting a form).
                if (fromFullKeyboard(event)) break;
                // fall through: on a remote, Enter is the OK button
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (down && event.getRepeatCount() == 0) mouse.press();
                else if (!down) mouse.release();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) goBack();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (down && event.getRepeatCount() == 0) video("v.paused ? v.play() : v.pause()");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (down && event.getRepeatCount() == 0) video("v.play()");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down && event.getRepeatCount() == 0) video("v.pause()");
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (down) video("v.currentTime += 10");
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) video("v.currentTime -= 10");
                return true;
            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean fromFullKeyboard(KeyEvent event) {
        InputDevice device = event.getDevice();
        return device != null && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                && device.getId() != KeyCharacterMap.VIRTUAL_KEYBOARD;
    }

    /** Runs a statement against the page's video element, if there is one. */
    private void video(String statement) {
        webView.evaluateJavascript(
                "(()=>{const v=document.querySelector('video');if(v){" + statement + "}})()", null);
    }

    private void goBack() {
        if (fullscreenView != null) {
            // Tell WebView the fullscreen view is going away; it then calls onHideCustomView.
            if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
            else exitFullscreenViews();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    // ---- Page events ----------------------------------------------------------------------

    private final class PageClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            String scheme = url.getScheme();
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                // intent://, market:// and the like would leave the app.
                Log.i(TAG, "Blocked non-web link: " + scheme);
                return true;
            }
            if (request.isForMainFrame() && !Sites.isHbo(url)) {
                Log.i(TAG, "Leaving HBO Max for " + url.getHost());
            }
            return false;
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            lastUrl = url;
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // The TV kills page processes when memory is tight. Rebuild instead of crashing.
            Log.w(TAG, "Page process gone (crashed=" + detail.didCrash() + "); reloading " + lastUrl);
            exitFullscreenViews();
            root.removeView(view);
            view.destroy();
            createWebView();
            return true;
        }
    }

    private final class ChromeClient extends WebChromeClient {
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            // Protected-media (Widevine) access, and only for HBO Max's own pages.
            boolean hbo = Sites.isHbo(request.getOrigin());
            for (String resource : request.getResources()) {
                if (hbo && PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(resource)) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                    return;
                }
            }
            request.deny();
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreenView = view;
            fullscreenCallback = callback;
            root.addView(view, root.indexOfChild(mouse), matchParent());
            webView.setVisibility(View.INVISIBLE);
            mouse.setTarget(view);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreenViews();
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage message) {
            // Page console goes to logcat (tag StreamShellJS): our own markers always, the rest when it is an error.
            String text = message.message();
            if (text != null && (text.startsWith("[streamshell]")
                    || message.messageLevel() == ConsoleMessage.MessageLevel.ERROR)) {
                Log.i("StreamShellJS", text.length() > 400 ? text.substring(0, 400) : text);
            }
            return true;
        }
    }

    private void exitFullscreenViews() {
        if (fullscreenView == null) return;
        root.removeView(fullscreenView);
        fullscreenView = null;
        fullscreenCallback = null;
        webView.setVisibility(View.VISIBLE);
        mouse.setTarget(webView);
        webView.requestFocus();
    }

    // ---- Lifecycle ------------------------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_URL, lastUrl);
    }

    @Override
    protected void onDestroy() {
        root.removeAllViews();
        webView.destroy();
        super.onDestroy();
    }
}
