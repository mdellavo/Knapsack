package org.quuux.knapsack;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Pair;
import android.util.Patterns;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.quuux.sack.Sack;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.regex.Matcher;

public class ArchiveService extends IntentService {

    private static final String TAG = Log.buildTag(ArchiveService.class);

    public static final String ACTION_ARCHIVE_COMPLETE = "org.quuux.knapsack.intent.actions.ARCHIVE_COMPLETE";

    final Handler mHandler = new Handler();

    public ArchiveService() {
        super(ArchiveService.class.getName());
    }

    @Override
    protected void onHandleIntent(final Intent intent) {

        if (intent == null) {
            return;
        }

        Log.d(TAG, "intent = %s", intent);

        final Bundle bundle = intent.getExtras();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            Log.d(TAG, "%s %s", key, value);
        }

        final String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            final String text = intent.getStringExtra(Intent.EXTRA_TEXT);

            if (text == null)
                return;

            final String title = intent.getStringExtra(Intent.EXTRA_SUBJECT);

            String url = null;
            final Matcher matcher = Patterns.WEB_URL.matcher(text);
            while (matcher.find()) {
                final String nextUrl = matcher.group();
                if (url == null || nextUrl.length() > url.length())
                    url = nextUrl;
            }

            if (url == null)
                return;

            final File parent = ArchivedPage.getArchivePath(url);

            if (!parent.exists())
                parent.mkdirs();

            final ArchivedPage page = getPage(title, url);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    archive(page);
                }
            });
        }
    }

    private ArchivedPage getPage(final String title, final String url) {
        final File manifest = ArchivedPage.getArchivePath(url, "manifest.json");
        ArchivedPage page = null;
        if (manifest.exists()) {
            Sack<ArchivedPage> store = Sack.open(ArchivedPage.class, manifest);
            try {
                final Pair<Sack.Status, ArchivedPage> result = store.load().get();
                if (result.first == Sack.Status.SUCCESS)
                    page = result.second;
            } catch (Exception e) {
                Log.e(TAG, "error loading sacked page", e);
            }
        }

        if (page == null) {
            page = new ArchivedPage(url, title);
        }

        return page;
    }

    private void onArchiveComplete(final ArchivedPage page) {
        final File manifest = ArchivedPage.getArchivePath(page.url, "manifest.json");
        Sack<ArchivedPage> store = Sack.open(ArchivedPage.class, manifest);
        store.commit(page, new Sack.Listener<ArchivedPage>() {
            @Override
            public void onResult(final Sack.Status status, final ArchivedPage obj) {
                final Intent intent = new Intent(ACTION_ARCHIVE_COMPLETE);
                sendBroadcast(intent);
            }
        });
    }

    private void saveBitmap(final Intent intent, final String key, final File path) {
        if (intent.hasExtra(key)) {
            saveBitmap((Bitmap) intent.getParcelableExtra(key), path);
        }
    }

    private void saveBitmap(final Bitmap bitmap, final File path) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void[] params) {

                try {
                    FileOutputStream out = new FileOutputStream(path);
                    bitmap.compress(Bitmap.CompressFormat.PNG, 80, out);
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "error saving bitmap", e);
                }

                return null;
            }
        }.execute();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void archive(final ArchivedPage page) {
        Log.d(TAG, "archiving: %s", page.url);

        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();

        final int width = display.getWidth();
        final int height = display.getHeight();

        final WebView view = new WebView(this);
        view.measure(View.MeasureSpec.getSize(width), View.MeasureSpec.getSize(height));
        view.layout(0, 0, width, height);

        final WebSettings settings = view.getSettings();
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // FIXME set to in memory only?
        CookieManager.getInstance().setAcceptCookie(true);

        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAppCacheEnabled(false);
        view.clearHistory();
        view.clearCache(true);

        view.clearFormData();
        settings.setSavePassword(false);
        settings.setSaveFormData(false);

        view.getSettings().setJavaScriptEnabled(true);
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(final WebView view, final int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG, "progress: %s", newProgress);
            }

            @Override
            public void onReceivedIcon(final WebView view, final Bitmap icon) {
                super.onReceivedIcon(view, icon);
                if (icon != null)
                    saveBitmap(icon, ArchivedPage.getArchivePath(page.url, "favicon.png"));
            }

            @Override
            public void onReceivedTitle(final WebView view, final String title) {
                super.onReceivedTitle(view, title);
                Log.d(TAG, "got title: %s", title);
                page.title = title;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
                super.onGeolocationPermissionsShowPrompt(origin, callback);
                callback.invoke(origin, false, false);
            }
        });

        final Runnable save = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "saving!");

                view.saveWebArchive(ArchivedPage.getArchivePath(page.url, "index.mht").getPath());
                final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                view.draw(canvas);

                final Bitmap resized = Bitmap.createScaledBitmap(bitmap, width/4, height/4, true);

                saveBitmap(resized, ArchivedPage.getArchivePath(page.url, "screenshot.png"));

                view.pauseTimers();
                view.onPause();
                view.destroy();

                onArchiveComplete(page);
            }
        };

        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "timeout!");
                view.stopLoading();
            }
        };


        view.setWebViewClient(new WebViewClient() {

            private int loadCount = 0;

            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                Log.d(TAG, "override: %s", url);
                loadCount--;
                return false;
            }

            @Override
            public void onReceivedSslError(final WebView view, final SslErrorHandler handler, final SslError error) {
                super.onReceivedSslError(view, handler, error);
                Log.d(TAG, "ssl error: %s", error);
                handler.proceed();
            }

            @Override
            public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                loadCount++;

                Log.d(TAG, "page started:%s: %s", loadCount, url);

                if (favicon != null)
                    saveBitmap(favicon, ArchivedPage.getArchivePath(url, "favicon.png"));
            }

            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);

                loadCount--;

                Log.d(TAG, "page finished:%s: %s", loadCount, url);

                if (loadCount > 0)
                    return;

                mHandler.removeCallbacks(timeout);
                mHandler.postDelayed(save, 10 * 1000); // give it a little breathing room for ajax loads
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

        view.onResume();
        view.loadUrl(page.url);

        mHandler.postDelayed(timeout, 30 * 1000);
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

}
