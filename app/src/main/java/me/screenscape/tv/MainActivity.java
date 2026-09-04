package me.screenscape.tv;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

/**
 * ScreenScape — a full-screen browser shell for https://screenscape.me/ on Google TV.
 *
 * Because the site is a normal web app rather than a leanback app, plain D-pad focus
 * navigation is not enough. A virtual pointer is drawn over the WebView: the D-pad
 * moves it and OK synthesises a real tap. Press MENU (or long-press OK) to switch
 * between pointer mode and native D-pad focus mode.
 */
public class MainActivity extends AppCompatActivity {

    private static final String HOME_URL = "https://screenscape.me/";

    private WebView web;
    private CursorView cursor;
    private View splash;
    private View errorView;

    private boolean pointerMode = true;
    private boolean pageLoaded = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Video keeps playing without the screen dimming.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        goFullscreen();

        web = findViewById(R.id.web);
        cursor = findViewById(R.id.cursor);
        splash = findViewById(R.id.splash);
        errorView = findViewById(R.id.error_view);

        configureWebView();
        setupBackHandling();

        Button retry = findViewById(R.id.btn_retry);
        retry.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            splash.setVisibility(View.VISIBLE);
            web.loadUrl(HOME_URL);
        });

        cursor.attachTo(web);
        cursor.setVisibility(pointerMode ? View.VISIBLE : View.GONE);

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(HOME_URL);
        }
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // let video autoplay
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Present as a desktop-class browser; most sites then serve the full layout,
        // which suits a 10-foot screen far better than the mobile layout.
        s.setUserAgentString(
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/122.0.0.0 Safari/537.36 ScreenScapeTV/1.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setBackgroundColor(0xFF0A0A0C);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                // Keep every navigation inside this app.
                return false;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageLoaded = true;
                splash.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (req != null && req.isForMainFrame()) {
                    splash.setVisibility(View.GONE);
                    TextView msg = findViewById(R.id.error_message);
                    msg.setText(getString(R.string.error_body));
                    errorView.setVisibility(View.VISIBLE);
                    findViewById(R.id.btn_retry).requestFocus();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // Grant DRM/media permissions the page asks for (needed by some players).
                request.grant(request.getResources());
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        }
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (errorView.getVisibility() == View.VISIBLE) {
                    finish();
                } else if (web.canGoBack()) {
                    web.goBack();
                } else {
                    confirmExit();
                }
            }
        });
    }

    private void confirmExit() {
        new AlertDialog.Builder(this, R.style.ScreenScapeDialog)
                .setTitle(R.string.exit_title)
                .setMessage(R.string.exit_body)
                .setNegativeButton(R.string.exit_stay, null)
                .setPositiveButton(R.string.exit_go, (d, w) -> finish())
                .show();
    }

    // ------------------------------------------------------------------ remote input

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();

        // MENU toggles between pointer mode and native focus navigation.
        if (code == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
            togglePointerMode();
            return true;
        }

        // Media keys pass straight through to the page.
        if (!pointerMode || !pageLoaded || errorView.getVisibility() == View.VISIBLE) {
            return super.dispatchKeyEvent(event);
        }

        if (cursor.handleKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void togglePointerMode() {
        pointerMode = !pointerMode;
        cursor.setVisibility(pointerMode ? View.VISIBLE : View.GONE);
        if (!pointerMode) {
            web.requestFocus();
        }
        Toast.makeText(this,
                pointerMode ? R.string.mode_pointer : R.string.mode_focus,
                Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ lifecycle

    private void goFullscreen() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goFullscreen();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
        }
        super.onDestroy();
    }

    /** Synthesise a tap on the WebView at the given screen coordinates. */
    static void tap(WebView target, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(down);
        target.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }
}
