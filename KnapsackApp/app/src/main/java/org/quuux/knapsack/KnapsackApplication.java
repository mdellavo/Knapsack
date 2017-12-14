package org.quuux.knapsack;

import android.app.Application;
import android.net.http.HttpResponseCache;
import android.os.Build;
import android.webkit.WebView;

import org.quuux.feller.Log;
import org.quuux.knapsack.data.CacheManager;
import org.quuux.knapsack.data.PageCache;

import java.io.File;
import java.io.IOException;

public class KnapsackApplication extends Application {
    private static final String TAG = Log.buildTag(KnapsackApplication.class);

    @Override
    public void onCreate() {
        super.onCreate();

        CacheManager.setRoot(getExternalCacheDir());

        try {
            final File httpCacheDir = new File(getExternalCacheDir(), "http");
            final long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
            HttpResponseCache.install(httpCacheDir, httpCacheSize);
        } catch (IOException e) {
            Log.e(TAG, "error setting http cache", e);
        }

        PageCache.getInstance().scanPages();
        ArchiveService.sync(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WebView.enableSlowWholeDocumentDraw();
        }

    }
}
