package org.quuux.knapsack.util;


import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.quuux.feller.Log;
import org.quuux.knapsack.data.AdBlocker;
import org.quuux.knapsack.data.CacheManager;
import org.quuux.knapsack.data.Page;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class ArchiveHelper {

    private static final String TAG = Log.buildTag(ArchiveHelper.class);

    @SuppressLint("SetJavaScriptEnabled")
    public static void configureWebView(final WebView view) {
        final WebSettings settings = view.getSettings();
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw();
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);

        if  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        CookieManager.getInstance().setAcceptCookie(true);

            settings.setJavaScriptEnabled(true);

            view.setBackgroundColor(view.getResources().getColor(android.R.color.white));
    }

    public static void loadPage(final Page page, final WebView view) {
        final Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "http://www.google.com/");  // paywall smashie
        view.loadUrl(page.getUrl());
    }

    public static class ArchiveChromeClient extends WebChromeClient {

        private final Page mPage;

        public ArchiveChromeClient(final Page page) {
            mPage = page;
        }

        @Override
        public void onProgressChanged(final WebView view, final int newProgress) {
            super.onProgressChanged(view, newProgress);
            //Log.d(TAG, "progress: %s", newProgress);
        }

        @Override
        public void onReceivedIcon(final WebView view, final Bitmap icon) {
            super.onReceivedIcon(view, icon);
            if (icon != null) {
                ArchiveHelper.saveFavicon(mPage, icon, null);
            }
        }

        @Override
        public void onReceivedTitle(final WebView view, final String title) {
            super.onReceivedTitle(view, title);
            //Log.d(TAG, "got title: %s", title);
            mPage.setTitle(title);
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
            super.onGeolocationPermissionsShowPrompt(origin, callback);
            callback.invoke(origin, false, false);
        }

    }

    public static boolean savePage(final Page page, final WebView view, final Runnable onComplete) {

        final long t1 = System.currentTimeMillis();
        final String path = CacheManager.getArchivePath(page.getUrl(), "index.mht").getPath();
        boolean rv;
        try {
            view.saveWebArchive(path, false, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(final String value) {
                    final long t2 = System.currentTimeMillis();
                    Log.d(TAG, "archive %s -> %s in %sms", path, value, t2 - t1);
                    saveScreenshot(page, view, onComplete);
                }
            });
            rv = true;
        } catch(Exception e) {
            Log.e(TAG, "Error saving page", e);
            rv = false;
        }

        return rv;
    }

    public static class ArchiveClient extends WebViewClient {

        private final Page mPage;
        private int mLoadClient = 0;

        public ArchiveClient(final Page page) {
            mPage = page;
        }

        @Override
        public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
            //Log.d(TAG, "override: %s", url);
            mLoadClient--;
            return false;
        }

        @Override
        public void onReceivedSslError(final WebView view, @NonNull final SslErrorHandler handler, final SslError error) {
            super.onReceivedSslError(view, handler, error);
            Log.d(TAG, "ssl error: %s", error);
            handler.proceed();
        }

        @Override
        public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
            super.onPageStarted(view, url, favicon);

            mLoadClient++;

            Log.d(TAG, "page started:%s: %s", mLoadClient, url);

            if (favicon != null) {
                ArchiveHelper.saveFavicon(mPage, favicon, null);
            }
        }

        @Override
        public void onPageFinished(final WebView view, final String url) {
            super.onPageFinished(view, url);

            mLoadClient--;

            Log.d(TAG, "page finished:%s: %s", mLoadClient, url);
            if (!isLoading()) {
                mPage.setStatus(Page.STATUS_SUCCESS);
            }
        }

        @Override
        public void onLoadResource(final WebView view, final String url) {
            super.onLoadResource(view, url);
            Log.d(TAG, "loading: %s", url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(final WebView view, final WebResourceRequest request) {
            return shouldInterceptRequest(view, request.getUrl().toString());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(final WebView view, final String url) {

            if (isAdblockEnabled()) {
                final AdBlocker adBlocker = AdBlocker.getInstance();

                Log.d(TAG, "intercept? %s", url);
                if (adBlocker.match(url)) {
                    Log.d(TAG, "blocking: %s", url);
                    final InputStream in = new ByteArrayInputStream("".getBytes());
                    return new WebResourceResponse("text/plain", "utf-8", in);
                }
            }

            return super.shouldInterceptRequest(view, url);
        }

        private boolean isAdblockEnabled() {
            return false;
        }

        @Override
        public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            Log.d(TAG, "error (%s) %s @ %s", errorCode, description, failingUrl);
            mPage.setStatus(Page.STATUS_ERROR);
        }

        public boolean isLoading() {
            return mLoadClient > 0;
        }
    }

    private static AsyncTask<Void, Void, Void> saveBitmap(final Page page, final Bitmap bitmap, final File path, final int width, final int height, final Runnable onComplete) {
        return new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void[] params) {

                final long t1 = System.currentTimeMillis();

                final Bitmap resized;
                if (width > 0 || height > 0)
                    resized = Bitmap.createScaledBitmap(bitmap, width, height, true);
                else
                    resized = bitmap;

                try {
                    OutputStream out = new BufferedOutputStream(new FileOutputStream(path));
                    resized.compress(Bitmap.CompressFormat.PNG, 90, out);
                    out.flush();
                    out.close();
                } catch (java.io.IOException e) {
                    Log.e(TAG, "error saving bitmap", e);
                }

                final long t2 = System.currentTimeMillis();
                Log.d(TAG, "lastStatusChange bitmap %s in %sms", path, t2-t1);

                return null;
            }

            @Override
            protected void onPostExecute(final Void aVoid) {
                super.onPostExecute(aVoid);
                if (onComplete != null)
                    onComplete.run();
            }
        }.execute();
    }

    private static void saveScreenshot(final Page page, final WebView view, final Runnable onComplete) {
        final int width = view.getWidth();
        final int height = view.getHeight();
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);

        Log.d(TAG, "taking snapshot");
        view.draw(canvas);

        saveBitmap(page, bitmap, CacheManager.getArchivePath(page.getUrl(), "screenshot.png"), width / 4, height / 4, onComplete);
    }

    public static void saveFavicon(final Page page, final Bitmap icon, final Runnable onComplete) {
        saveBitmap(page, icon, CacheManager.getArchivePath(page.getUrl(), "favicon.png"), 0, 0, onComplete);
    }

}
