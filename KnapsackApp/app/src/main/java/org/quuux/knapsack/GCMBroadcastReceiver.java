package org.quuux.knapsack;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import org.quuux.feller.Log;
import org.quuux.knapsack.data.Page;
import org.quuux.knapsack.data.PageCache;

public class GCMBroadcastReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = Log.buildTag(GCMBroadcastReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String url = intent.getStringExtra("url");
        Log.d(TAG, "received intent: %s -> %s", intent, url);

        Page page = PageCache.getInstance().getPage(url);

        if (page == null) {
            PageCache.getInstance().addPage(url);


            final ComponentName comp = new ComponentName(context.getPackageName(),
                    ArchiveService.class.getName());
            intent.setAction(ArchiveService.ACTION_SYNC);
            startWakefulService(context, (intent.setComponent(comp)));
        }
    }
}
