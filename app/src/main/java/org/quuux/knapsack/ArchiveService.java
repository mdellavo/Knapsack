package org.quuux.knapsack;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.regex.Matcher;

public class ArchiveService extends IntentService {

    private static final String TAG = Log.buildTag(ArchiveService.class);

    public static final String ACTION_ARCHIVE_UPDATE = "org.quuux.knapsack.action.ARCHIVE_UPDATE";
    public static final String ACTION_SYNC = "org.quuux.knapsack.action.SYNC";
    public static final String ACTION_ARCHIVE = "org.quuux.knapsack.action.ARCHIVE";
    private static final String ACTION_UPLOAD = "org.quuux.knapsack.action.UPLOAD";

    public static final String ACTION_SYNC_COMPLETE = "org.quuux.knapsack.action.SYNC_COMPLETE";

    private static final String EXTRA_PAGE = "page";

    private final Handler mHandler = new Handler();
    private final BlockingQueue<Page> mQueue = new ArrayBlockingQueue<Page>(1);

    public ArchiveService() {
        super(ArchiveService.class.getName());
    }

    public static void sync(final Context context) {
        final Intent intent = new Intent(context, ArchiveService.class);
        intent.setAction(ACTION_SYNC);
        context.startService(intent);
    }

    public static void upload(final Context context) {
        final Intent intent = new Intent(context, ArchiveService.class);
        intent.setAction(ACTION_UPLOAD);
        context.startService(intent);
    }

    public static void archive(final Context context, final Page page) {
        final Intent intent = new Intent(context, ArchiveService.class);
        intent.setAction(ACTION_ARCHIVE);
        intent.putExtra(EXTRA_PAGE, page);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {

        if (intent == null) {
            return;
        }

        Log.d(TAG, "intent = %s", intent);

        if (intent.getExtras() != null)
            dumpExtras(intent);

        final String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) || ACTION_ARCHIVE.equals(action)) {

            final Page page;
            if (intent.hasExtra(EXTRA_PAGE))
                page = (Page) intent.getSerializableExtra(EXTRA_PAGE);
            else
                page = extractPage(
                        intent.getStringExtra(Intent.EXTRA_TEXT),
                        intent.getStringExtra(Intent.EXTRA_SUBJECT)
                );

            if (page != null) {
                final File parent = CacheManager.getArchivePath(page);

                if (!parent.exists())
                    parent.mkdirs();

                archivePage(page);
            }

        } else if (ArchiveService.ACTION_SYNC.equals(action)) {
            sync();
        } else if (ArchiveService.ACTION_UPLOAD.equals(action)) {
            upload();
        }

        GCMBroadcastReceiver.completeWakefulIntent(intent);
    }

    private void upload() {
        final List<Page> localPages = new ArrayList<Page>();
        final File dir = CacheManager.getArchivePath();
        if (!dir.exists())
            return;

        final File[] files =  dir.listFiles();
        if (files == null)
            return;

        for (final File f : files) {
            if (f.isDirectory()) {
                final File manifest = new File(f, "manifest.json");
                Sack<Page> store = Sack.open(Page.class, manifest);
                try {
                    final Pair<Sack.Status, Page> result = store.load().get();
                    if (result.first == Sack.Status.SUCCESS) {
                        localPages.add(result.second);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "error loading sack", e);
                } catch (ExecutionException e) {
                    Log.e(TAG, "error loading sack", e);
                }
            }
        }

        if (localPages.size() == 0)
            return;

        final String authToken = getAuthToken();
        if (authToken == null)
            return;

        API.setPages(authToken, localPages);
    }

    private void dumpExtras(final Intent intent) {
        final Bundle bundle = intent.getExtras();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            Log.d(TAG, "%s %s", key, value);
        }
    }

    private void sync() {
        Log.d(TAG, "fetching pages...");


        final String authToken = getAuthToken();

        if (authToken != null) {
            final List<Page> pages = API.getPages(authToken);

            if (pages != null) {

                for (final Page page : pages) {
                    final File manifest = CacheManager.getManifest(page);
                    if (!manifest.exists()) {
                        commitPage(page);

                        Log.d(TAG, "synced: %s / %s / %s", page.created, page.uid, page.url);
                        archive(this, page);
                    }
                }
            } else {
                Log.e(TAG, "error syncing pages");
            }
        }

        syncComplete();

        upload(this);
    }

    private String getAuthToken() {
        final String account = Preferences.getSyncAccount(this);

        if (account.isEmpty()) {
            return null;
        }

        return API.getToken(this, account, GCMService.getRegistrationIntent(this, account));
    }

    private void syncComplete() {
        final Intent intent = new Intent(ACTION_SYNC_COMPLETE);
        sendBroadcast(intent);
    }

    private Page extractPage(final String text, final String title) {
        String url = null;
        final Matcher matcher = Patterns.WEB_URL.matcher(text);
        while (matcher.find()) {
            final String nextUrl = matcher.group();
            if (url == null || nextUrl.length() > url.length())
                url = nextUrl;
        }

        if (url == null)
            return null;

        return getPage(title, url);
    }

    private Page archivePage(final Page page) {

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                archive(page);
            }
        });

        Page result = null;
        try {
            result = mQueue.take();
        } catch (InterruptedException e) {
            Log.e(TAG, "error getting result", e);
        }

        return result;
    }

    private Page getPage(final String title, final String url) {
        final File manifest = CacheManager.getArchivePath(url, "manifest.json");
        Page page = null;

        final boolean manifestExists = manifest.exists();

        if (manifestExists) {
            Sack<Page> store = Sack.open(Page.class, manifest);
            try {
                final Pair<Sack.Status, Page> result = store.load().get();
                if (result.first == Sack.Status.SUCCESS)
                    page = result.second;
            } catch (Exception e) {
                Log.e(TAG, "error loading sacked page", e);
            }
        }

        if (page == null) {
            page = new Page(url, title);
        }

        if (!manifestExists) {
            commitPage(page);
        }

        return page;
    }

    private void commitPage(final Page page) {

        final File manifest = CacheManager.getArchivePath(page.url, "manifest.json");
        final Sack<Page> store = Sack.open(Page.class, manifest);
        store.commit(page, new Sack.Listener<Page>() {
            @Override
            public void onResult(final Sack.Status status, final Page obj) {
                broadcastUpdate();
                try {
                    mQueue.put(obj);
                } catch (InterruptedException e) {
                    Log.e(TAG, "error putting result", e);
                }
            }
        });
    }

    private void broadcastUpdate() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Intent intent = new Intent(ACTION_ARCHIVE_UPDATE);
                sendBroadcast(intent);
            }
        });
    }

    private AsyncTask<Void, Void, Void> saveBitmap(final Intent intent, final String key, final File path) {
        AsyncTask<Void, Void, Void> rv = null;
        if (intent.hasExtra(key)) {
            rv = saveBitmap((Bitmap) intent.getParcelableExtra(key), path, 0, 0);
        }
        return rv;
    }

    private AsyncTask<Void, Void, Void> saveBitmap(final Bitmap bitmap, final File path, final int width, final int height) {
        return new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void[] params) {

                final Bitmap resized;
                if (width > 0 || height > 0)
                    resized = Bitmap.createScaledBitmap(bitmap, width, height, true);
                else
                    resized = bitmap;

                try {
                    FileOutputStream out = new FileOutputStream(path);
                    resized.compress(Bitmap.CompressFormat.PNG, 90, out);
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "error saving bitmap", e);
                }

                return null;
            }
        }.execute();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView newWebView() {
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

        return view;
    }

    private void archive(final Page page) {
        Log.d(TAG, "archiving: %s", page.url);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder
                .setContentTitle(getString(R.string.archiving))
                .setContentText(page.title)
                .setSmallIcon(R.drawable.ic_stat_archving)
                .setProgress(100, 0, true);

        updateNotification(builder.build(), page);

        final WebView view = newWebView();
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(final WebView view, final int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG, "progress: %s", newProgress);
                builder.setProgress(100, newProgress, false);
                updateNotification(builder.build(), page);
            }

            @Override
            public void onReceivedIcon(final WebView view, final Bitmap icon) {
                super.onReceivedIcon(view, icon);
                if (icon != null)
                    saveBitmap(icon, CacheManager.getArchivePath(page.url, "favicon.png"), 0, 0);
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

        final Runnable success = new Runnable() {
            @Override
            public void run() {
                page.status = Page.STATUS_SUCCESS;
                terminate(view, builder, page);
            }
        };

        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "timeout!");
                page.status = Page.STATUS_ERROR;
                terminate(view, builder, page);
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
                    saveBitmap(favicon, CacheManager.getArchivePath(url, "favicon.png"), 0, 0);
            }

            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);

                loadCount--;

                Log.d(TAG, "page finished:%s: %s", loadCount, url);

                if (loadCount > 0)
                    return;

                builder.setProgress(0, 0, true);
                builder.setContentTitle(getString(R.string.archiving));
                builder.setContentText(page.title);

                mHandler.removeCallbacks(timeout);
                mHandler.postDelayed(success, 10 * 1000); // give it a little breathing room for ajax loads
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
                page.status = Page.STATUS_ERROR;
                terminate(view, builder, page);
            }
        });

        view.onResume();
        view.loadUrl(page.url);

        mHandler.postDelayed(timeout, 30 * 1000);
    }

    private void terminate(final WebView view, final NotificationCompat.Builder builder, final Page page) {

        if (page.status == Page.STATUS_SUCCESS)
            notifySuccess(builder, page);
        else if (page.status == Page.STATUS_ERROR)
            notifyError(builder, page);

        savePage(page, view);
        commitPage(page);
    }

    private void updateNotification(final Notification notification, final Page page) {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(page.url.hashCode(), notification);
    }

    private void notifySuccess(final NotificationCompat.Builder builder, final Page page) {
        builder.setProgress(0, 0, false);
        builder.setContentTitle(getString(R.string.archived));
        builder.setContentText(page.title);
        updateNotification(builder.build(), page);
    }

    private void notifyError(final NotificationCompat.Builder builder, final Page page) {
        builder.setProgress(0, 0, false);
        builder.setContentTitle(getString(R.string.archive_error));
        builder.setContentText(page.title);
        updateNotification(builder.build(), page);
    }

    private void destoryWebView(final WebView view) {
        view.stopLoading();
        view.pauseTimers();
        view.onPause();
        view.destroy();
    }

    private void savePage(final Page page, final WebView view) {
        Log.d(TAG, "saving!");

        final int width = view.getWidth();
        final int height = view.getHeight();

        view.saveWebArchive(CacheManager.getArchivePath(page.url, "index.mht").getPath());
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        saveBitmap(bitmap, CacheManager.getArchivePath(page.url, "screenshot.png"), width/4, height/4);
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

}
