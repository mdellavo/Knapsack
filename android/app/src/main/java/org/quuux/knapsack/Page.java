package org.quuux.knapsack;

import android.content.Intent;
import android.util.Patterns;

import java.io.Serializable;
import java.util.Date;
import java.util.regex.Matcher;

public class Page implements Serializable {

    public static final int STATUS_NEW = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_ERROR = -1;

    int status;
    String uid;
    String url;
    String title;
    Date created;
    boolean read;
    float progress = 0;

    public Page(final String url, final String title, final String uid) {
        this.url = url;
        this.title = title;
        this.uid = uid;
        this.created = new Date();
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof Page && ((Page)o).url.equals(url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    public boolean isKnown() {
        return uid != null;
    }

    @Override
    public String toString() {
        return String.format("Page(url: %s / uid: %s)", url, uid);
    }

    private boolean shouldUpdate(final Object self, final Object other) {
        return self == null && other != null;
    }

    public void update(final Page other) {
        if (shouldUpdate(this.uid, other.uid))
            uid = other.uid;

        if (shouldUpdate(this.title, other.title))
            title = other.title;

        if (shouldUpdate(this.url, other.url))
            url = other.url;

        if (shouldUpdate(this.created, other.created))
            created = other.created;
    }

    public static Page extractPage(final String text, final String title) {
        String url = null;
        final Matcher matcher = Patterns.WEB_URL.matcher(text);
        while (matcher.find()) {
            final String nextUrl = matcher.group();
            if (url == null || nextUrl.length() > url.length())
                url = nextUrl;
        }

        if (url == null)
            return null;

        return new Page(url, title, null);
    }

    public static Page extractPage(final Intent intent) {
        return extractPage(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_SUBJECT)
        );
    }
}

