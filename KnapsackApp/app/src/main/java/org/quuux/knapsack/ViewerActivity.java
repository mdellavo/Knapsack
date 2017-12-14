package org.quuux.knapsack;


import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.AppBarLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
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
import org.quuux.knapsack.data.API;
import org.quuux.knapsack.data.CacheManager;
import org.quuux.knapsack.data.Page;
import org.quuux.knapsack.data.PageCache;
import org.quuux.knapsack.util.ArchiveHelper;
import org.quuux.knapsack.view.NestedWebView;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Date;


public class ViewerActivity extends AppCompatActivity {
    private static final String TAG = Log.buildTag(ViewerActivity.class);

    public static final String EXTRA_PAGE = "page";
    private static final long DIM_TIMEOUT = 500;

    private final Handler mHandler = new Handler();

    private Toolbar mToolbar;
    private AppBarLayout mAppBarLayout;

    private NestedWebView mContentView;

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

        mAppBarLayout = (AppBarLayout) findViewById(R.id.app_bar);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getPageTitle());
        actionBar.setSubtitle(getPageSubTitle());

        mProgress = (ProgressBar)findViewById(R.id.progress);
        mProgress.setMax(100);

        mContentView = (NestedWebView) findViewById(R.id.fullscreen_content);
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

        if (mPage != null) {
            float progress = calculateProgress(mContentView);
            mPage.setProgress(progress);
            Log.d(TAG, "saving position @ %s", mPage.getProgress());
            ArchiveService.commit(this, mPage);
        }
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
        super.onSaveInstanceState(outState);
        final float position = calculateProgress(mContentView);
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

            case R.id.open_source:
                openSource(mPage);
                rv = true;
                break;

            case R.id.resave_page:
                savePage(mPage);
                rv = true;
                break;

            case R.id.delete_page:
                checkDeletePage(mPage);
                rv = true;
                break;

            default:
                rv = super.onOptionsItemSelected(item);
                break;
        }

        return rv;
    }

    private void openSource(final Page page) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(page.getUrl()));
        startActivity(browserIntent);
    }

    private void savePage(final Page page) {
        final Intent intent = new Intent(this, ArchiveActivity.class);
        intent.putExtra(ArchiveActivity.EXTRA_PAGE, page);
        startActivity(intent);
    }

    private void checkDeletePage(final Page page) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
                .setTitle(R.string.delete_page_title)
                .setMessage(R.string.delete_page_message)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        deletePage(page);
                    }
                }).create().show();
    }

    private void deletePage(final Page page) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... voids) {

                PageCache.getInstance().deletePage(page);

                final String account = Preferences.getSyncAccount(ViewerActivity.this);

                if (!account.isEmpty()) {
                    final String authToken = API.getInstance().getToken(ViewerActivity.this, account,
                            GCMService.getRegistrationIntent(ViewerActivity.this, account));
                    API.getInstance().deletePage(authToken, page);
                }

                return null;
            }

            @Override
            protected void onPostExecute(final Void aVoid) {
                super.onPostExecute(aVoid);
                mPage = null;
                finish();
            }
        }.execute();
    }

    private float calculateProgress(WebView content) {
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
        boolean isNetworkAvailable = isNetworkAvailable();

        view.setVerticalScrollBarEnabled(true);
        view.setNetworkAvailable(isNetworkAvailable);

        final WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBlockNetworkLoads(!isNetworkAvailable);
        settings.setBlockNetworkImage(!isNetworkAvailable);
        settings.setCacheMode(isNetworkAvailable ? WebSettings.LOAD_CACHE_ELSE_NETWORK : WebSettings.LOAD_CACHE_ONLY);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        settings.setUseWideViewPort(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        if  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

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

                if (mPage.isNew())
                    savePage();

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

        final float position = mSavedPosition;
        if (position > 0) {
            Log.d(TAG, "restoring position %s", mSavedPosition);

            float webviewsize = mContentView.getContentHeight() - mContentView.getTop();
            float positionInWV = webviewsize * mSavedPosition;
            int positionY = Math.round(mContentView.getTop() + positionInWV);
            mContentView.scrollTo(0, positionY);
            mSavedPosition = 0;
        }
    }

    private void loadNetwork() {
        Log.d(TAG, "loading network: %s", mPage);
        ArchiveHelper.loadPage(mPage, mContentView);
    }

    private void loadArchive() {
        Log.d(TAG, "loading archive: %s", mPage);

        final File file = CacheManager.getArchivePath(mPage.getUrl(), "index.mht");
        try {
            mContentView.loadUrl(file.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            Log.e(TAG, "error loading archived page", e);
        }
    }

    private void load() {
        if (mPage.isNew() && isNetworkAvailable())
            loadNetwork();
        else
            loadArchive();

        if (!mPage.isRead()) {
            mPage.markRead();
        }

        lastLoad = new Date();
    }

    private void savePage() {
        ArchiveHelper.savePage(mPage, mContentView, new Runnable() {
            @Override
            public void run() {
                onPageSaved();
            }
        });
    }

    private void onPageSaved() {
        mPage.setStatus(Page.STATUS_SUCCESS);
    }
}
