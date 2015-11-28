package org.quuux.knapsack;


import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.quuux.feller.Log;
import org.quuux.knapsack.data.CacheManager;
import org.quuux.knapsack.data.Page;
import org.quuux.knapsack.data.PageCache;
import org.quuux.knapsack.view.ObservableWebview;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Date;


public class ViewerActivity extends AppCompatActivity {
    private static final String TAG = Log.buildTag(ViewerActivity.class);

    public static final String EXTRA_PAGE = "page";
    private static final long DIM_TIMEOUT = 500;

    private final Handler mHandler = new Handler();

    private Toolbar mToolbar;

    private ObservableWebview mContentView;

    private ProgressBar mProgress;

    private Page mPage;
    private Date lastLoad = null;
    private float mSavedPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPage = PageCache.getInstance().getPage((Page) getIntent().getSerializableExtra(EXTRA_PAGE));

        setContentView(R.layout.activity_viewer);

        final View decorView = getWindow().getDecorView();
        final Runnable dimCallback = new Runnable() {
            @Override
            public void run() {
                dimSystemBars(decorView);
            }
        };

        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                decorView.removeCallbacks(dimCallback);
                decorView.postDelayed(dimCallback, DIM_TIMEOUT);
            }
        });

        dimSystemBars(decorView);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getPageTitle());
        actionBar.setSubtitle(getPageSubTitle());

        mProgress = (ProgressBar)findViewById(R.id.progress);
        mProgress.setMax(100);

        mContentView = (ObservableWebview) findViewById(R.id.fullscreen_content);
        initWebView(mContentView);

        if (savedInstanceState != null)
            mSavedPosition = savedInstanceState.getFloat("position", 0);

        mSavedPosition = Math.max(mSavedPosition, mPage.getProgress());
    }

    @Override
    protected void onResume() {
        super.onResume();

        mContentView.onResume();
        mContentView.resumeTimers();

        Log.d(TAG, "lastload=%s / last status change=%s", lastLoad, mPage.getlastStatusChange());

        if (lastLoad == null || lastLoad.before(mPage.getlastStatusChange())) {
            load();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mContentView.pauseTimers();
        mContentView.onPause();

        mPage.setProgress(calculateProgression(mContentView));
        Log.d(TAG, "saving position @ %s", mPage.getProgress());
        PageCache.getInstance().commitPage(mPage);
    }

    private String getPageTitle() {
        return mPage.getDisplayTitle();
    }

    private String getPageSubTitle() {
        return !TextUtils.isEmpty(mPage.getTitle()) ? mPage.getUrl() : null;
    }

    private void dimSystemBars(final View decorView) {
        int uiOptions = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        decorView.setSystemUiVisibility(uiOptions);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        final float position = calculateProgression(mContentView);
        Log.d(TAG, "saving position %s", position);
        outState.putFloat("position", position);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.viewer_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        final boolean rv;
        switch (item.getItemId()) {

            case R.id.resave_page:
                savePage(mPage);
                rv = true;
                break;

            default:
                rv = super.onOptionsItemSelected(item);
                break;
        }

        return rv;
    }

    private void savePage(final Page page) {
        final Intent intent = new Intent(this, ArchiveActivity.class);
        intent.putExtra(ArchiveActivity.EXTRA_PAGE, page);
        startActivity(intent);
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

        view.setVerticalScrollBarEnabled(true);
        view.setNetworkAvailable(false);

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

    private void restorePosition() {

        mContentView.setOnScrollChangedListener(null);

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

        final File file = CacheManager.getArchivePath(mPage.getUrl(), "index.mht");
        try {
            mContentView.loadUrl(file.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            Log.e(TAG, "error loading page", e);
        }

        if (!mPage.isRead()) {
            mPage.markRead();
            PageCache.getInstance().commitAsync(mPage);
        }

        lastLoad = new Date();
    }
}
