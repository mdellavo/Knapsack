package org.quuux.knapsack;

import android.app.Application;
import android.net.http.HttpResponseCache;

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
    }
}
