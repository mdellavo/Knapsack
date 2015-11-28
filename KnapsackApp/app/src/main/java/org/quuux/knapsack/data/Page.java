package org.quuux.knapsack.data;

import android.content.Intent;
import android.text.TextUtils;
import android.util.Patterns;

import java.io.Serializable;
import java.util.Date;
import java.util.regex.Matcher;

public class Page implements Serializable {

    public static final int STATUS_NEW = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_ERROR = -1;

    private int status;
    private Date lastStatusChange;
    private String uid;
    private String url;
    private String title;
    private Date created;
    private boolean read;
    private float progress = 0;

    public Page(final String url, final String title, final String uid) {
        this.url = url;
        this.title = title;
        this.uid = uid;
        this.created = new Date();
        this.lastStatusChange = new Date();
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof Page && ((Page) o).url.equals(url);
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public Date getCreated() {
        return created;
    }

    public boolean isRead() {
        return read;
    }

    public float getProgress() {
        return progress;
    }

    public boolean isKnown() {
        return uid != null;
    }

    public void setStatus(final int status) {
        this.status = status;
        this.lastStatusChange = new Date();
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public String getUid() {
        return uid;
    }

    public boolean isSuccess() {
        return status == Page.STATUS_SUCCESS;
    }

    public boolean isError() {
        return status == Page.STATUS_ERROR;
    }

    public void setProgress(final float progress) {
        this.progress = progress;
    }

    public String getDisplayTitle() {
        return !TextUtils.isEmpty(getTitle()) ? getTitle() : getUrl();
    }

    public void markRead() {
        this.read = true;
    }

    public boolean isNew() {
        return status == Page.STATUS_NEW;
    }

    public Date getlastStatusChange() {
        return lastStatusChange;
    }

    @Override
    public String toString() {
        return String.format("Page(url: %s / uid: %s)", url, uid);
    }

    public void update(final Page other) {
        uid = other.uid;
        title = other.title;
        url = other.url;
        created = other.created;
        status = other.status;
        lastStatusChange = other.lastStatusChange;
        progress = other.progress;
        read = other.read;
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

