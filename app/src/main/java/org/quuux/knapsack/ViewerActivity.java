package org.quuux.knapsack;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
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
import android.widget.Toast;

import java.io.File;
import java.net.MalformedURLException;


public class ViewerActivity extends ActionBarActivity {
    private static final String TAG = Log.buildTag(ViewerActivity.class);

    public static final String EXTRA_PAGE = "page";

    private final Handler mHandler = new Handler();

    private ObservableWebView mContentView;
    private ProgressBar mProgress;
    private ProgressDrawable mProgressDrawable;

    private float mSavedPosition;
    private Page mPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPage = PageCache.getInstance().getPage((Page) getIntent().getSerializableExtra(EXTRA_PAGE));

        setContentView(R.layout.activity_viewer);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mProgress = (ProgressBar)findViewById(R.id.progress);
        mContentView = (ObservableWebView) findViewById(R.id.fullscreen_content);
        mContentView.setOnScrollChangedListener(mScrollListener);

        initWebView(mContentView);

        mProgress.setMax(100);

        mProgressDrawable = new ProgressDrawable();

        if (savedInstanceState != null)
            mSavedPosition = savedInstanceState.getFloat("position", 0);

        mSavedPosition = Math.max(mSavedPosition, mPage.progress);
        load();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        final float position = calculateProgression(mContentView);
        Log.d(TAG, "saving position %s", position);
        outState.putFloat("position", position);
    }

    private float calculateProgression(WebView content) {
        float positionTopView = content.getTop();
        float contentHeight = content.getContentHeight();
        float currentScrollPosition = content.getScrollY();
        return (currentScrollPosition - positionTopView) / contentHeight;
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
        settings.setLoadsImagesAutomatically(true);

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        settings.setAllowFileAccess(true);

        if  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        settings.setUseWideViewPort(true);

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(final WebView view, final int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG, "progress: %s", newProgress);
                mProgress.setProgress(newProgress);
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

                if (isNetworkAvailable()) {
                    final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    try {
                        startActivity(intent);
                    } catch(ActivityNotFoundException e) {

                    }
                } else {
                    Toast.makeText(ViewerActivity.this, R.string.no_network, Toast.LENGTH_LONG).show();
                }

                return true;
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

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        restorePosition();
                    }
                }, 250);
            }

            @Override
            public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                toggleProgress(true);
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
        mContentView.onResume();
        mContentView.resumeTimers();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mContentView.pauseTimers();
        mContentView.onPause();

        mPage.progress = calculateProgression(mContentView);
        Log.d(TAG, "saving position @ %s", mPage.progress);
        PageCache.getInstance().commitPage(mPage);
    }

    private void restorePosition() {
        final float position = mSavedPosition;
        if (position > 0) {
            Log.d(TAG, "restoring position %s", mSavedPosition);
            float webviewsize = mContentView.getContentHeight() - mContentView.getTop();
            float positionInWV = webviewsize * mSavedPosition;
            int positionY = Math.round(mContentView.getTop() + positionInWV);
            mContentView.scrollTo(0, positionY);
            mSavedPosition = 0 ;
        }
    }

    private void load() {
        Log.d(TAG, "loading: %s", mPage);

        final File file = CacheManager.getArchivePath(mPage.url, "index.mht");
        try {
            mContentView.loadUrl(file.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            Log.e(TAG, "error loading page", e);
        }

        if (!mPage.read) {
            mPage.read = true;
            PageCache.getInstance().commitAsync(mPage);
        }
    }

    private ObservableWebView.OnScrollChangedListener mScrollListener = new ObservableWebView.OnScrollChangedListener() {

        int scrolled = 0;

        @Override
        public void onScroll(final int l, final int t, final int oldl, final int oldt) {
            mProgressDrawable.setProgress(calculateProgression(mContentView));
        }
    };

    class ProgressDrawable extends Drawable {

        final Paint mPaint;
        float progress;

        ProgressDrawable() {
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setAlpha(255);
            mPaint.setColor(getResources().getColor(android.R.color.black));
            mPaint.setTextAlign(Paint.Align.CENTER);
            mPaint.setTextSize(80);
            mPaint.setSubpixelText(true);
        }

        public void setProgress(final float progress) {
            this.progress = progress;
            Log.d(TAG, "progress=%s", progress);
            invalidateSelf();
        }

        @Override
        public void draw(final Canvas canvas) {
            final float cx = canvas.getWidth() / 2f;
            final float cy = canvas.getHeight() / 2f;
            final String s = String.format("%s%%", progress * 100);
            canvas.drawText(s, cx, cy, mPaint);
        }

        @Override
        public void setAlpha(final int alpha) {
            mPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(final ColorFilter cf) {
            mPaint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return 1 - mPaint.getAlpha();
        }
    }

}
