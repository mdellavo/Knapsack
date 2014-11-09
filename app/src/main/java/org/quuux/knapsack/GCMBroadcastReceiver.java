package org.quuux.knapsack;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

public class GCMBroadcastReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = Log.buildTag(GCMBroadcastReceiver.class);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d(TAG, "received intent: %s", intent);

        final ComponentName comp = new ComponentName(context.getPackageName(),
                ArchiveService.class.getName());
        intent.setAction(ArchiveService.ACTION_SYNC);
        startWakefulService(context, (intent.setComponent(comp)));
        setResultCode(Activity.RESULT_OK);
    }
}
