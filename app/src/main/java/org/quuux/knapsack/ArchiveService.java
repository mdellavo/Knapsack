package org.quuux.knapsack;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;

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
            archive(intent.getStringExtra(Intent.EXTRA_TEXT));
        }
        return 0;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void archive(final String url) {
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
                save(view, url);
                view.destroy();
            }

        });
        
        view.onResume();
        view.loadUrl(url);
    }

    private void save(final WebView view, final String url) {

        final File base = Environment.getExternalStorageDirectory();
        final File dir = new File(base, "WebArchive");

        if (!dir.exists())
            dir.mkdirs();

        final Uri uri = Uri.parse(url);

        final StringBuilder sb = new StringBuilder();
        sb.append(uri.getHost());

        for (final String part : uri.getPathSegments()) {
            sb.append("-");
            sb.append(part);
        }

        final File path = new File(dir, sb.toString() + ".mht");

        Log.d(TAG, "Path: %s", path);

        view.saveWebArchive(path.getPath());
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }
}
