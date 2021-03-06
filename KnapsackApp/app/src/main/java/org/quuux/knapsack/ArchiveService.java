package org.quuux.knapsack;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;

import org.quuux.feller.Log;
import org.quuux.knapsack.data.API;
import org.quuux.knapsack.data.Page;
import org.quuux.knapsack.data.PageCache;
import org.quuux.knapsack.event.EventBus;
import org.quuux.knapsack.event.PageUpdated;
import org.quuux.knapsack.event.PagesUpdated;
import org.quuux.knapsack.util.ArchiveHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ArchiveService extends IntentService {

    private static final String TAG = Log.buildTag(ArchiveService.class);

    public static final String ACTION_SYNC = "org.quuux.knapsack.action.SYNC";
    public static final String ACTION_ARCHIVE = "org.quuux.knapsack.action.ARCHIVE";
    public static final String ACTION_COMMIT = "org.quuux.knapsack.action.COMMIT";

    private static final String EXTRA_PAGE = "page";

    private static final int NOTIFICATION = 10;

    private final Handler mHandler = new Handler();
    private final BlockingQueue<Page> mQueue = new ArrayBlockingQueue<>(1);
    private ConnectivityManager mConnectivityManager;
    private static boolean syncing;

    public ArchiveService() {
        super(ArchiveService.class.getName());
    }

    public static void sync(final Context context) {
        final Intent intent = new Intent(context, ArchiveService.class);
        intent.setAction(ACTION_SYNC);
        context.startService(intent);
    }

    public static void commit(final Context context, final Page page) {
        final Intent intent = new Intent(context, ArchiveService.class);
        intent.setAction(ACTION_COMMIT);
        intent.putExtra(EXTRA_PAGE, page);
        context.startService(intent);
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

        final String action = intent.getAction();
        switch (action) {
            case Intent.ACTION_SEND:
            case ACTION_ARCHIVE:
                doArchive(intent);
                break;
            case ArchiveService.ACTION_SYNC:
                sync();
                break;
            case ArchiveService.ACTION_COMMIT:
                doCommit(getPageFromIntent(intent));
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

    private void doCommit(Page page) {
        PageCache.getInstance().commitPage(page);
        if (isConnected()) {
            final String authToken = getAuthToken();
            if (authToken != null)
                API.getInstance().updatePage(authToken, page);
        }
    }

    private void doArchive(final Intent intent) {
        final Page page = getPageFromIntent(intent);
        if (page == null)
            return;

        if (!isConnected())
            return;

        if (!Preferences.wifiOnly(this) || isWifi()) {
            final Page result = archivePage(page);
            Log.d(TAG, "result: (%s) %s", result.getStatus(), result.getUrl());
        }

        doCommit(page);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                EventBus.getInstance().post(new PageUpdated());
            }
        });
    }

    private boolean isConnected() {
        final NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private boolean isWifi() {
        final NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private void dumpExtras(final Intent intent) {
        final Bundle bundle = intent.getExtras();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            Log.d(TAG, "Extra: %s -> %s", key, value);
        }
    }

    private void broadcastSync() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                EventBus.getInstance().post(new PagesUpdated());
            }
        });
    }




    public List<Page> syncPages(final String authToken) {
        List<Page> pages = new ArrayList<>();

        Date before = null;
        PageCache cache = PageCache.getInstance();

        API.GetPagesResponse response;
        do {
            response = API.getInstance().getPages(authToken, before);
            before = response.before;
            for (Page page : response.pages) {
                boolean containsPage = cache.getPage(page) != null;
                if (containsPage)
                    return pages;

                pages.add(page);
            }
        } while(before != null);

        return pages;
    }

    private void sync() {
        if (!isConnected())
            return;

        syncing = true;
        broadcastSync();

        Log.d(TAG, "fetching pages...");

        final String authToken = getAuthToken();

        if (authToken != null) {
             final List<Page> pages = syncPages(authToken);

            if (pages != null) {

                final PageCache cache = PageCache.getInstance();
                for (final Page p : pages) {
                    cache.ensurePage(p);
                }

                Log.d(TAG, "synced %d pages", pages.size());

            } else {
                Log.e(TAG, "error syncing pages");
            }
        }

        syncing = false;
        broadcastSync();
    }

    private String getAuthToken() {
        final String account = Preferences.getSyncAccount(this);

        if (account.isEmpty()) {
            return null;
        }

        return API.getInstance().getToken(this, account, GCMService.getRegistrationIntent(this, account));
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
        final long t1 = System.currentTimeMillis();

        Log.d(TAG, "archiving: %s", page.getUrl());

        final Intent contentIntent = new Intent(this, IndexActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, page.hashCode(), contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder
                .setContentTitle(getString(R.string.archiving))
                .setContentText(getContextText(page))
                .setSmallIcon(R.drawable.ic_stat_archving)
                .setContentIntent(pendingIntent)
                .setProgress(100, 0, true);

        updateNotification(builder.build(), page);

        final WebView view = newWebView();
        view.setWebChromeClient(new ArchiveHelper.ArchiveChromeClient(page) {
            @Override
            public void onProgressChanged(final WebView view, final int newProgress) {
                super.onProgressChanged(view, newProgress);
                //Log.d(TAG, "progress: %s", newProgress);
                builder.setProgress(100, newProgress, false).setContentText(getContextText(page));
                updateNotification(builder.build(), page);
            }
        });

        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "timeout!");
                page.setStatus(Page.STATUS_ERROR);
                terminate(view, builder, page, t1);
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
                updateNotification(builder.build(), page);

                mHandler.removeCallbacks(timeout);

                page.setStatus(Page.STATUS_SUCCESS);

                terminate(view, builder, page, t1);
            }


            @Override
            public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.d(TAG, "error (%s) %s @ %s", errorCode, description, failingUrl);
                page.setStatus(Page.STATUS_ERROR);
                mHandler.removeCallbacks(timeout);
                terminate(view, builder, page, t1);
            }
        });

        view.onResume();
        view.resumeTimers();

        ArchiveHelper.loadPage(page, view);

        mHandler.postDelayed(timeout, 60 * 1000);
    }

    private void terminate(final WebView view, final NotificationCompat.Builder builder, final Page page, final long t1) {

        final Runnable onComplete = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "archive lastStatusChange!");

                destoryWebView(view);

                if (page.isSuccess())
                    notifySuccess(builder, page);
                else if (page.isError())
                    notifyError(builder, page);

                mQueue.add(page);

                final long t2 = System.currentTimeMillis();
                Log.d(TAG, "archived %s -> %s in %sms", page.getUrl(), page.getStatus(), t2-t1);
            }
        };

        if (!ArchiveHelper.savePage(page, view, onComplete)) {
            page.setStatus(Page.STATUS_ERROR);
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
        updateNotification(builder.build(), page);
    }

    private void notifyError(final NotificationCompat.Builder builder, final Page page) {
        builder.setProgress(0, 0, false);
        builder.setContentTitle(getString(R.string.archive_error));
        builder.setContentText(getContextText(page));
        updateNotification(builder.build(), page);
    }

    private void destoryWebView(final WebView view) {
        view.stopLoading();
        view.pauseTimers();
        view.onPause();
        view.destroy();
    }

    private String getContextText(final Page page) {
        return page.getDisplayTitle();
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    public static boolean isSyncing() {
        return syncing;
    }
}
