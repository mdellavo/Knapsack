package org.quuux.knapsack;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

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

    private Page getPageFromIntent(final Intent intent) {
        final Page page;
        if (intent.hasExtra(EXTRA_PAGE))
            page = (Page) intent.getSerializableExtra(EXTRA_PAGE);
        else
            page = Page.extractPage(intent);

        if (page == null)
            return null;

        return PageCache.getInstance().ensurePage(page);
    }

    private void doArchive(final Intent intent) {
        final Page page = getPageFromIntent(intent);
        if (page == null)
            return;

        if (!isConnected())
            return;

        mArchiving.add(page);

        if (!Preferences.wifiOnly(this) || isWifi()) {
            final Page result = archivePage(page);
            Log.d(TAG, "result: (%s) %s", result.status, result.url);
        }

        if (page.uid == null) {
            final String authToken = getAuthToken();
            if (authToken != null)
                API.addPage(authToken, page);
        }

        mArchiving.remove(page);

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

                final PageCache cache = PageCache.getInstance();

                for (final Page p : pages) {
                    final Page page = cache.ensurePage(p);
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
            broadcastUpdate(page);
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

    private WebView newWebView() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();

        final int width = display.getWidth();
        final int height = display.getHeight();

        final WebView view = new WebView(this);
        view.measure(View.MeasureSpec.getSize(width), View.MeasureSpec.getSize(height));
        view.layout(0, 0, width, height);

        ArchiveHelper.configureWebView(view);

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
                .setContentText(getContextText(page))
                .setSmallIcon(R.drawable.ic_stat_archving)
                .setContentIntent(pendingIntent)
                .setNumber(mArchiving.size())
                .setProgress(100, 0, true);

        updateNotification(builder.build(), page);

        final WebView view = newWebView();
        view.setWebChromeClient(new ArchiveHelper.ArchiveChromeClient(page) {
            @Override
            public void onProgressChanged(final WebView view, final int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG, "progress: %s", newProgress);
                builder.setProgress(100, newProgress, false);
                updateNotification(builder.build(), page);
            }
        });

        final Runnable success = new Runnable() {
            @Override
            public void run() {
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

        view.setWebViewClient(new ArchiveHelper.ArchiveClient(page) {
            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);
                if (isLoading())
                    return;

                builder.setProgress(0, 0, true);
                builder.setContentTitle(getString(R.string.archiving));
                builder.setNumber(mArchiving.size());
                updateNotification(builder.build(), page);

                mHandler.removeCallbacks(timeout);
                mHandler.postDelayed(success, 1 * 1000); // give it a little breathing room
            }


            @Override
            public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.d(TAG, "error (%s) %s @ %s", errorCode, description, failingUrl);
                terminate(view, builder, page);
            }
        });

        view.onResume();
        view.resumeTimers();
        view.loadUrl(page.url);

        mHandler.postDelayed(timeout, 60 * 1000);
    }

    private void terminate(final WebView view, final NotificationCompat.Builder builder, final Page page) {

        final Runnable onComplete = new Runnable() {
            @Override
            public void run() {
                destoryWebView(view);

                if (page.status == Page.STATUS_SUCCESS)
                    notifySuccess(builder, page);
                else if (page.status == Page.STATUS_ERROR)
                    notifyError(builder, page);

                mQueue.offer(page);
            }
        };

        if (!ArchiveHelper.savePage(page, view, onComplete)) {
            page.status = Page.STATUS_ERROR;
            onComplete.run();
        }
    }



    private void updateNotification(final Notification notification, final Page page) {
        final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION, notification);
    }

    private void notifySuccess(final NotificationCompat.Builder builder, final Page page) {
        builder.setProgress(0, 0, false);
        builder.setContentTitle(getString(R.string.archived));
        builder.setContentText(getContextText(page));
        builder.setNumber(mArchiving.size());
        updateNotification(builder.build(), page);
    }

    private void notifyError(final NotificationCompat.Builder builder, final Page page) {
        builder.setProgress(0, 0, false);
        builder.setContentTitle(getString(R.string.archive_error));
        builder.setContentText(getContextText(page));
        builder.setNumber(mArchiving.size());
        updateNotification(builder.build(), page);
    }

    private void destoryWebView(final WebView view) {
        view.stopLoading();
        view.pauseTimers();
        view.onPause();
        view.destroy();
    }

    private String getContextText(final Page page) {
        return !TextUtils.isEmpty(page.title) ? page.title : page.url;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

}
