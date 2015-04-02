package org.quuux.knapsack;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class ArchiveActivity extends Activity {
    private static final String TAG = Log.buildTag(ArchiveActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = new Intent(this, ArchiveService.class);
        intent.fillIn(getIntent(), 0);
        startService(intent);
        finish();
    }
}
