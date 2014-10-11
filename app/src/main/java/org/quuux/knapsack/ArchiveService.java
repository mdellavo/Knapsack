package org.quuux.knapsack;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
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

            final File parent = getArchivePath(url);

            if (!parent.exists())
                parent.mkdirs();

            saveBitmap(intent, "share_favicon", getArchivePath(url, "favicon.png"));
            saveBitmap(intent, "share_screenshot", getArchivePath(url, "screenshot.png"));
            archive(url, getArchivePath(url, "index.mht").getPath());

            final ArchivedPage page = new ArchivedPage(url, title);
            Sack<ArchivedPage> store = Sack.open(ArchivedPage.class, getArchivePath(url, "manifest.json"));
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
        try {
            FileOutputStream out = new FileOutputStream(path);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "error saving bitmap", e);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void archive(final String url, final String path) {
        Log.d(TAG, "archiving: %s", url);

        final WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 0;
        params.width = 0;
        params.height = 0;

        final WebView view = new WebView(this);

        windowManager.addView(view, params);

        view.getSettings().setJavaScriptEnabled(true);
        view.setWebChromeClient(new WebChromeClient() {});
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "page finished: %s", url);
                view.pauseTimers();
                view.onPause();
                view.saveWebArchive(path);
                view.destroy();
            }

        });

        view.onResume();
        view.loadUrl(url);
    }

    private File getArchivePath() {
        final File base = Environment.getExternalStorageDirectory();
        return new File(base, "WebArchive");
    }

    private File getArchivePath(final String url) {
        final Uri uri = Uri.parse(url);

        final StringBuilder sb = new StringBuilder();
        sb.append(uri.getHost());

        for (final String part : uri.getPathSegments()) {
            sb.append("-");
            sb.append(part);
        }

        return new File(getArchivePath(), sb.toString());
    }

    private File getArchivePath(final String url, final String filename) {
        return new File(getArchivePath(url), filename);
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }
}
