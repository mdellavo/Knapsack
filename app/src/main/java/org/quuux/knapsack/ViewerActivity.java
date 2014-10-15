package org.quuux.knapsack;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;

import java.io.File;
import java.net.MalformedURLException;


public class ViewerActivity extends ActionBarActivity {
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    private static final String TAG = Log.buildTag(ViewerActivity.class);

    private WebView mContentView;
    private boolean mLeanback;
    private GestureDetector mGestureDetector;
    final private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setBackgroundDrawable(getResources().getDrawable(R.drawable.actionbar));

        setContentView(R.layout.activity_viewer);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mGestureDetector = new GestureDetector(this, mGestureListener);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);

        mContentView = (WebView) findViewById(R.id.fullscreen_content);
        mContentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View v, final MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });

        setupSystemUi();
        load();

        startLeanback();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(mLeanback)
            startLeanback();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (mLeanback)
                startLeanback();
            else
                exitLeanback();
        }
    }
    private void load() {
        final ArchivedPage page = (ArchivedPage) getIntent().getSerializableExtra("page");
        final File file = ArchivedPage.getArchivePath(page.url, "index.mht");
        try {
            mContentView.loadUrl(file.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            Log.e(TAG, "error loading page", e);
        }
    }


    @TargetApi(11)
    private void setupSystemUi() {
        final View v = getWindow().getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            v.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(final int visibility) {
                    final boolean isAwake = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;

                    Log.d(TAG, "system ui visibility change: isAwake=%s", isAwake);

                    if (isAwake) {
                        getSupportActionBar().show();
                    } else {
                        getSupportActionBar().hide();
                    }

                }
            });
        }
    }

    @TargetApi(11)
    private void hideSystemUi() {
        Log.d(TAG, "hide system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
            );
        }
    }

    @TargetApi(11)
    private void showSystemUi() {
        Log.d(TAG, "show system ui");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    @TargetApi(11)
    private void restoreSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    public void startLeanback() {
        Log.d(TAG, "start leanback");
        mLeanback = true;
        getSupportActionBar().hide();
        hideSystemUi();
    }

    public void endLeanback() {
        Log.d(TAG, "end leanback");
        mLeanback = false;
        mHandler.removeCallbacks(mLeanbackCallback);
        getSupportActionBar().show();
        showSystemUi();
    }

    public void exitLeanback() {
        Log.d(TAG, "exiting leanback");
        endLeanback();
        restoreSystemUi();
    }

    public boolean isLeanback() {
        return mLeanback;
    }

    public void toggleLeanback() {
        if (mLeanback)
            endLeanback();
        else
            startLeanback();
    }

    private final Runnable mLeanbackCallback = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "resuming leanback...");
            startLeanback();
        }
    };

    final GestureDetector.OnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onSingleTapUp(final MotionEvent e) {
            supportInvalidateOptionsMenu();
            toggleLeanback();

            if (mLeanback) {
                mHandler.removeCallbacks(mLeanbackCallback);
                mHandler.postDelayed(mLeanbackCallback, 2500);
            }

            return false;
        }

    };
}
