package org.quuux.knapsack;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import org.quuux.sack.Sack;

import java.io.File;
import java.net.MalformedURLException;


public class ViewerActivity extends ActionBarActivity {
    private static final String TAG = Log.buildTag(ViewerActivity.class);

    private ObservableWebView mContentView;
    private boolean mLeanback;
    private Handler mHandler;
    private ProgressBar mProgress;
    private int mSlop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHandler = new Handler();

        mSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        final GestureDetector gestureDetector = new GestureDetector(this, mGestureListener);

        getSupportActionBar().setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar));
        setContentView(R.layout.activity_viewer);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //final View controlsView = findViewById(R.id.fullscreen_content_controls);

        mProgress = (ProgressBar)findViewById(R.id.progress);

        mContentView = (ObservableWebView) findViewById(R.id.fullscreen_content);
        mContentView.setOnScrollChangedListener(mScrollListener);
        mContentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View v, final MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });
        initWebView(mContentView);

        setupSystemUi();
        load();
    }

    private boolean isNetworkAvailable() {
        final  ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView(final WebView view) {

        final WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);

        if (!isNetworkAvailable()) {
            settings.setBlockNetworkLoads(true);
            settings.setBlockNetworkImage(true);
            settings.setCacheMode(WebSettings.LOAD_CACHE_ONLY);
        }

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);

        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        settings.setUseWideViewPort(true);

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(final WebView view, final int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG, "progress: %s", newProgress);
            }

            @Override
            public void onReceivedIcon(final WebView view, final Bitmap icon) {
                super.onReceivedIcon(view, icon);
            }

            @Override
            public void onReceivedTitle(final WebView view, final String title) {
                super.onReceivedTitle(view, title);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
                super.onGeolocationPermissionsShowPrompt(origin, callback);
                callback.invoke(origin, false, false);
            }

        });

        view.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                Log.d(TAG, "override: %s", url);
                return !isNetworkAvailable();
            }

            @Override
            public void onReceivedSslError(final WebView view, final SslErrorHandler handler, final SslError error) {
                super.onReceivedSslError(view, handler, error);
                Log.d(TAG, "ssl error: %s", error);
                handler.proceed();
            }
            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                toggleProgress(false);
                mHandler.postDelayed(mStartLeanbackCallback, 500);
            }

            @Override
            public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                //toggleProgress(true);
            }

            @Override
            public void onLoadResource(final WebView view, final String url) {
                super.onLoadResource(view, url);
                Log.d(TAG, "loading: %s", url);
            }

            @Override
            public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.d(TAG, "error (%s) %s @ %s", errorCode, description, failingUrl);
            }
        });
    }

    private void toggleProgress(final boolean state) {
        mProgress.setVisibility(state ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void load() {
        final Page page = (Page) getIntent().getSerializableExtra("page");

        getSupportActionBar().setTitle(page.title);
        getSupportActionBar().setSubtitle(page.url);

        final File file = CacheManager.getArchivePath(page.url, "index.mht");
        try {
            mContentView.loadUrl(file.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            Log.e(TAG, "error loading page", e);
        }

        if (!page.read) {
            page.read = true;

            final File manifest = CacheManager.getArchivePath(page.url, "manifest.json");
            Sack<Page> sack = Sack.open(Page.class, manifest);
            sack.commit(page);
        }
    }


    @TargetApi(11)
    private void setupSystemUi() {
        final View v = getWindow().getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            v.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(final int visibility) {
                    final boolean isAwake = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;

                    Log.d(TAG, "system ui visibility change: isAwake=%s", isAwake);

                    if (isAwake) {
                        getSupportActionBar().show();
                    } else {
                        getSupportActionBar().hide();
                    }

                }
            });
        }
    }

    @TargetApi(11)
    private void hideSystemUi() {
        Log.d(TAG, "hide system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    @TargetApi(11)
    private void showSystemUi() {
        Log.d(TAG, "show system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    @TargetApi(11)
    private void restoreSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    public void startLeanback() {
        Log.d(TAG, "start leanback");
        mLeanback = true;
        mHandler.removeCallbacks(mEndLeanbackCallback);
        getSupportActionBar().hide();
        hideSystemUi();
    }

    public void endLeanback() {
        Log.d(TAG, "end leanback");
        mLeanback = false;
        mHandler.removeCallbacks(mStartLeanbackCallback);
        getSupportActionBar().show();
        showSystemUi();
    }

    public void exitLeanback() {
        Log.d(TAG, "exiting leanback");
        endLeanback();
        restoreSystemUi();
    }

    public boolean isLeanback() {
        return mLeanback;
    }

    public void toggleLeanback() {
        if (mLeanback)
            endLeanback();
        else
            startLeanback();
    }

    private final Runnable mStartLeanbackCallback = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "resuming leanback...");
            startLeanback();
        }
    };

    private final Runnable mEndLeanbackCallback = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "resuming leanback...");
            endLeanback();
        }
    };

    private ObservableWebView.OnScrollChangedListener mScrollListener = new ObservableWebView.OnScrollChangedListener() {

        int scrolled = 0;

        @Override
        public void onScroll(final int l, final int t, final int oldl, final int oldt) {
            final int deltaT = t - oldt;

            if ((deltaT > 0 && !mLeanback) || (deltaT < 0 && mLeanback))
                scrolled += deltaT;

            if (scrolled > mSlop && !mLeanback) {
                startLeanback();
                scrolled = 0;
            } else if (scrolled < -mSlop && mLeanback) {
                endLeanback();
                scrolled = 0;
            }
        }
    };

    private android.view.GestureDetector.OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener(){
        @Override
        public boolean onSingleTapConfirmed(final MotionEvent e) {
            toggleLeanback();
            return true;
        }
    };

}
