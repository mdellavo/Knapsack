package org.quuux.knapsack;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.quuux.sack.Sack;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class ArchiveService extends Service {

    private static final String TAG = Log.buildTag(ArchiveService.class);

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {

        if (intent == null) {
            return 0;
        }

        Log.d(TAG, "intent = %s", intent);

        final Bundle bundle = intent.getExtras();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            Log.d(TAG, "%s %s (%s)", key,
                    value.toString(), value.getClass().getName());
        }

        final String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            final String url = intent.getStringExtra(Intent.EXTRA_TEXT);
            final String title = intent.getStringExtra(Intent.EXTRA_SUBJECT);

            final File parent = ArchivedPage.getArchivePath(url);

            if (!parent.exists())
                parent.mkdirs();

            saveBitmap(intent, "share_favicon", ArchivedPage.getArchivePath(url, "favicon.png"));
            saveBitmap(intent, "share_screenshot", ArchivedPage.getArchivePath(url, "screenshot.png"));
            archive(url, ArchivedPage.getArchivePath(url, "index.mht").getAbsolutePath());

            final ArchivedPage page = new ArchivedPage(url, title);
            Sack<ArchivedPage> store = Sack.open(ArchivedPage.class, ArchivedPage.getArchivePath(url, "manifest.json"));
            try {
                store.commit(page).get();
            } catch (Exception e) {
                Log.e(TAG, "error sacking page", e);
            }
        }

        return 0;
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
    private void archive(final String url, final String path) {
        Log.d(TAG, "archiving: %s", url);

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


        view.getSettings().setJavaScriptEnabled(true);
        view.setWebChromeClient(new WebChromeClient() {});
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (favicon != null)
                    saveBitmap(favicon, ArchivedPage.getArchivePath(url, "favicon.png"));
            }

            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "page finished: %s", url);
                view.pauseTimers();
                view.onPause();
                view.saveWebArchive(path);

                final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                view.draw(canvas);

                final Bitmap resized = Bitmap.createScaledBitmap(bitmap, width/4, height/4, true);

                saveBitmap(resized, ArchivedPage.getArchivePath(url, "screenshot.png"));

                view.destroy();
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
        view.loadUrl(url);
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }
}
