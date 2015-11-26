package org.quuux.knapsack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.quuux.feller.Log;

public class StatusBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = Log.buildTag(StatusBroadcastReceiver.class);

    public void onReceive(final Context context, final Intent intent) {
        Log.d(TAG, "Status  update: %s", intent);
    }
}
