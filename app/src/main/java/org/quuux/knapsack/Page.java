package org.quuux.knapsack;

import java.io.Serializable;
import java.util.Date;

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

    public void update(final Page other) {
        if (other.uid != null)
            uid = other.uid;

        if (other.title != null)
            title = other.title;

        if (other.url != null)
            url = other.url;

        if (other.created != null)
            created = other.created;
    }
}

