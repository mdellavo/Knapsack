package org.quuux.knapsack;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

public class ObservableWebView extends WebView {
    private OnScrollChangedListener mOnScrollChangedListener;
    private OnTouchEventListener mOnTouchEventListener;

    public ObservableWebView(final Context context) {
        super(context);
    }

    public ObservableWebView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public ObservableWebView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onScrollChanged(final int l, final int t, final int oldl, final int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mOnScrollChangedListener != null) mOnScrollChangedListener.onScroll(l, t, oldl, oldt);
    }

    public void setOnScrollChangedListener(final OnScrollChangedListener listener) {
        mOnScrollChangedListener = listener;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        return (mOnTouchEventListener != null ? mOnTouchEventListener.onTouchEvent(event) : false) || super.onTouchEvent(event);
    }

    public void setOnTouchEventListener(final OnTouchEventListener listener) {
        mOnTouchEventListener = listener;
    }

    public interface OnTouchEventListener {
        boolean onTouchEvent(MotionEvent event);
    }


    public interface OnScrollChangedListener {
         void onScroll(int l, int t, int oldl, int oldt);
    }
}