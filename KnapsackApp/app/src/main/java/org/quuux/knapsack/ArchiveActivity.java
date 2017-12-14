package org.quuux.knapsack;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;


import org.quuux.feller.Log;
import org.quuux.knapsack.data.Page;
import org.quuux.knapsack.data.PageCache;
import org.quuux.knapsack.util.ArchiveHelper;

public class ArchiveActivity extends Activity {
    private static final String TAG = Log.buildTag(ArchiveActivity.class);
    public static final String EXTRA_PAGE = "extra";
    private static final int THRESH = 90;

    private WebView mWebView;
    private Page mPage;
    private ProgressBar mProgress;
    private Button mButton;
    private boolean mLoaded;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        if (isUnattended(intent)) {
            startArchiveService(intent);
            finish();
            return;
        }

        setContentView(R.layout.archive_activity);

        mProgress = (ProgressBar) findViewById(R.id.progress);
        mProgress.setMax(100);

        mButton = (Button) findViewById(R.id.archive_button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                savePage();
            }
        });
        mButton.setEnabled(false);

        mWebView = (WebView) findViewById(R.id.webview);
        ArchiveHelper.configureWebView(mWebView);
        loadPage(intent);
    }

    private void loadPage(final Intent intent) {

        if (intent.hasExtra(EXTRA_PAGE))
            mPage = (Page) intent.getSerializableExtra(EXTRA_PAGE);
        else
            mPage = Page.extractPage(intent);

        mWebView.setWebChromeClient(new ArchiveHelper.ArchiveChromeClient(mPage) {
            @Override
            public void onProgressChanged(final WebView view, final int newProgress) {
                super.onProgressChanged(view, newProgress);
                Log.d(TAG, "page progress = %s%%", newProgress);

                mProgress.setProgress(newProgress);
            }
        });

        mWebView.setWebViewClient(new ArchiveHelper.ArchiveClient(mPage) {
            @Override
            public void onPageFinished(final WebView view, final String url) {
                super.onPageFinished(view, url);

                if (isLoading())
                    return;

                Log.d(TAG, "page finished");
                Toast.makeText(view.getContext(), R.string.page_loaded, Toast.LENGTH_LONG).show();
                mButton.setEnabled(true);
                mProgress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.d(TAG, "error (%s) %s @ %s", errorCode, description, failingUrl);
                Toast.makeText(view.getContext(), R.string.page_error, Toast.LENGTH_LONG).show();
            }
        });

        mWebView.onResume();
        mWebView.resumeTimers();
        ArchiveHelper.loadPage(mPage, mWebView);
        mLoaded = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mLoaded) {
            mWebView.pauseTimers();
            mWebView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mLoaded) {
            mWebView.resumeTimers();
            mWebView.onResume();
        }
    }

    private void savePage() {
        mButton.setEnabled(false);
        ArchiveHelper.savePage(mPage, mWebView, new Runnable() {
            @Override
            public void run() {
                onPageSaved();
            }
        });
    }

    public void onPageSaved() {
        mPage.setStatus(Page.STATUS_SUCCESS);

        ArchiveService.commit(this, mPage);

        Toast.makeText(this, R.string.page_saved, Toast.LENGTH_LONG).show();
        mButton.setEnabled(true);
    }

    private boolean isUnattended(final Intent intent) {
        return false; //!intent.hasExtra("manual");
    }

    private void startArchiveService(final Intent src) {
        final Intent intent = new Intent(this, ArchiveService.class);
        intent.fillIn(src, 0);
        startService(intent);
    }
}
