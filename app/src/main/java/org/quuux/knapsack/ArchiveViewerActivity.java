package org.quuux.knapsack;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;

import java.io.File;
import java.net.MalformedURLException;

public class ArchiveViewerActivity extends Activity {
    private static final String TAG = Log.buildTag(ArchiveViewerActivity.class);
    private WebView mViewer;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.viewer);
        mViewer = (WebView)findViewById(R.id.viewer);
        load();
    }

    private void load() {
        final ArchivedPage page = (ArchivedPage) getIntent().getSerializableExtra("page");
        final File file = ArchivedPage.getArchivePath(page.url, "index.mht");
        try {
            mViewer.loadUrl(file.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            Log.e(TAG, "error loading page", e);
        }
    }
}
