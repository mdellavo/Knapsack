package org.quuux.knapsack;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Patterns;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

public class ArchiveService extends IntentService {

    private static final String TAG = Log.buildTag(ArchiveService.class);

    public static final String ACTION_ARCHIVE_UPDATE = "org.quuux.knapsack.action.ARCHIVE_UPDATE";
    public static final String ACTION_SYNC = "org.quuux.knapsack.action.SYNC";
    public static final String ACTION_ARCHIVE = "org.quuux.knapsack.action.ARCHIVE";
    public static final String ACTION_SYNC_COMPLETE = "org.quuux.knapsack.action.SYNC_COMPLETE";

    private static final String EXTRA_PAGE = "page";

    private static final Set<Page> mArchiving = Collections.newSetFromMap(new ConcurrentHashMap<Page, Boolean>());
    private static final int NOTIFICATION = 10;

    private final Handler mHandler = new Handler();
    private final BlockingQueue<Page> mQueue = new ArrayBlockingQueue<>(1);
    private ConnectivityManager mConnectivityManager;

    public ArchiveService() {
        super(ArchiveService.class.getName());
    }


    public static void sync(final Context context) {
        final Intent intent = new Intent(context, ArchiveService.class);
        intent.setAction(ACTION_SYNC);
        context.startService(intent);
    }

    public static void archive(final Context context, final Page page) {
        if (isArchiving(page))
            return;

        mArchiving.add(page);

        final Intent intent = new Intent(context, ArchiveService.class);
        intent.setAction(ACTION_ARCHIVE);
        intent.putExtra(EXTRA_PAGE, page);
        context.startService(intent);
    }

    public static boolean isArchiving(final Page page) {
        return mArchiving.contains(page);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mConnectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {

        if (intent == null) {
            return;
        }

        Log.d(TAG, "intent = %s", intent);

        if (intent.getExtras() != null)
            dumpExtras(intent);

        // FIXME move to application
//        AdBlocker adblocker = AdBlocker.getInstance();
//        adblocker.load(this);

        final String action = intent.getAction();
        switch (action) {
            case Intent.ACTION_SEND:
            case ACTION_ARCHIVE:
                doArchive(intent);
                break;
            case ArchiveService.ACTION_SYNC:
                sync();
                break;
        }

        GCMBroadcastReceiver.completeWakefulIntent(intent);
    }

    private Page getPage(final Page page) {
        final PageCache cache = PageCache.getInstance();

        Page rv = cache.getPage(page);

        if (rv == null) {
            rv = cache.loadPage(page);
            if (rv == null) {
                rv = cache.commitPage(page);
            }
        }

        return rv;
    }

    private Page getPageFromIntent(final Intent intent) {
        final Page page;
        if (intent.hasExtra(EXTRA_PAGE))
            page = (Page) intent.getSerializableExtra(EXTRA_PAGE);
        else
            page = extractPage(
                    intent.getStringExtra(Intent.EXTRA_TEXT),
                    intent.getStringExtra(Intent.EXTRA_SUBJECT)
            );

        if (page == null)
            return null;

        return getPage(page);
    }

    private void doArchive(final Intent intent) {
        final Page page = getPageFromIntent(intent);
        if (page != null) {
            mArchiving.add(page);

            if (isConnected()) {
                if (!Preferences.wifiOnly(this) || isWifi()) {
                    final Page result = archivePage(page);
                    Log.d(TAG, "result: (%s) %s", result.status, result.url);
                }

                if (page.uid == null) {
                    final String authToken = getAuthToken();
                    if (authToken != null)
                        API.addPage(authToken, page);
                }
            }

            mArchiving.remove(page);
        }
    }

    private boolean isConnected() {
        final NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private boolean isWifi() {
        final NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private void upload(final String authToken, final List<Page> excludingPages)
    {
        final PageCache cache =  PageCache.getInstance();
        final List<Page> localPages = new ArrayList<>();

        cache.scanPages();
        for (Page p : cache.getPages()) {
            if (!p.isKnown())
                localPages.add(p);
        }

        if (localPages.size() > 0) {
            API.setPages(authToken, localPages);
        }
    }

    private void dumpExtras(final Intent intent) {
        final Bundle bundle = intent.getExtras();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            Log.d(TAG, "%s %s", key, value);
        }
    }

    private void sync() {
        if (!isConnected())
            return;

        Log.d(TAG, "fetching pages...");

        final String authToken = getAuthToken();

        if (authToken != null) {
             final List<Page> pages = API.getPages(authToken);

            if (pages != null) {
                for (final Page p : pages) {
                    final Page page = getPage(p);
                    if (page.status == Page.STATUS_NEW)
                        archive(this, page);
                }

                upload(authToken, pages);
            } else {
                Log.e(TAG, "error syncing pages");
            }
        }

        syncComplete();

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

        return new Page(url, title, null);
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
            PageCache.getInstance().commitPage(page);
        } catch (InterruptedException e) {
            Log.e(TAG, "error getting result", e);
        }

        return result;
    }

    private void broadcastUpdate(final Page page) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Intent intent = new Intent(ACTION_ARCHIVE_UPDATE);
                intent.putExtra(EXTRA_PAGE, page);
                sendBroadcast(intent);
            }
        });
    }

    private AsyncTask<Void, Void, Void> saveBitmap(final Page page, final Bitmap bitmap, final File path, final int width, final int height) {
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

            @Override
            protected void onPostExecute(final Void aVoid) {
                super.onPostExecute(aVoid);
                broadcastUpdate(page);
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
        view.setBackgroundColor(getResources().getColor(android.R.color.white));

        final WebSettings settings = view.getSettings();
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);

        if  (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        CookieManager.getInstance().setAcceptCookie(true);

        settings.setJavaScriptEnabled(true);

        return view;
    }

    private void archive(final Page page) {
        Log.d(TAG, "archiving: %s", page.url);

        final Intent contentIntent = new Intent(this, IndexActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, page.hashCode(), contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder
                .setContentTitle(getString(R.string.archiving))
                .setContentText(page.title)
                .setSmallIcon(R.drawable.ic_stat_archving)
                .setContentIntent(pendingIntent)
                .setNumber(mArchiving.size())
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
                    saveBitmap(page, icon, CacheManager.getArchivePath(page.url, "favicon.png"), 0, 0);
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
            public void onReceivedSslError(final WebView view, @NonNull final SslErrorHandler handler, final SslError error) {
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
                    saveBitmap(page, favicon, CacheManager.getArchivePath(url, "favicon.png"), 0, 0);
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
                builder.setNumber(mArchiving.size());
                updateNotification(builder.build(), page);

                mHandler.removeCallbacks(timeout);
                mHandler.postDelayed(success, 3 * 1000); // give it a little breathing room
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

                Log.d(TAG, "intercept? %s", url);

                final AdBlocker adBlocker = AdBlocker.getInstance();

                if (adBlocker.match(url)) {
                    Log.d(TAG, "blocking: %s", url);
                    final InputStream in = new ByteArrayInputStream("".getBytes());
                    return new WebResourceResponse("text/plain", "utf-8", in);
                }

                return super.shouldInterceptRequest(view, url);
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
        view.resumeTimers();
        view.loadUrl(page.url);

        mHandler.postDelayed(timeout, 600 * 1000);
    }

    private void terminate(final WebView view, final NotificationCompat.Builder builder, final Page page) {

        if (!savePage(page, view)) {
            page.status = Page.STATUS_ERROR;
        }
        destoryWebView(view);

        if (page.status == Page.STATUS_SUCCESS)
            notifySuccess(builder, page);
        else if (page.status == Page.STATUS_ERROR)
            notifyError(builder, page);

        mQueue.offer(page);
    }

    private void updateNotification(final Notification notification, final Page page) {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION, notification);
    }

    private void notifySuccess(final NotificationCompat.Builder builder, final Page page) {
        builder.setProgress(0, 0, false);
        builder.setContentTitle(getString(R.string.archived));
        builder.setContentText(page.title);
        builder.setNumber(mArchiving.size());
        updateNotification(builder.build(), page);
    }

    private void notifyError(final NotificationCompat.Builder builder, final Page page) {
        builder.setProgress(0, 0, false);
        builder.setContentTitle(getString(R.string.archive_error));
        builder.setContentText(page.title);
        builder.setNumber(mArchiving.size());
        updateNotification(builder.build(), page);
    }

    private void destoryWebView(final WebView view) {
        view.stopLoading();
        view.pauseTimers();
        view.onPause();
        view.destroy();
    }

    private boolean savePage(final Page page, final WebView view) {
        Log.d(TAG, "saving!");

        final int width = view.getWidth();
        final int height = view.getHeight();

        boolean rv;
        try {
            view.saveWebArchive(CacheManager.getArchivePath(page.url, "index.mht").getPath(), false, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(final String value) {
                    Log.d(TAG, "archive saved: %s", value);
                }
            });
            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);

            saveBitmap(page, bitmap, CacheManager.getArchivePath(page.url, "screenshot.png"), width / 4, height / 4);
            rv = true;
        } catch(Exception e) {
            Log.e(TAG, "Error saving page", e);
            rv = false;
        }

        return rv;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

}
