package org.quuux.knapsack;

import android.net.Uri;
import android.os.Environment;

import java.io.File;
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

    public Page(final String url, final String title) {
        this.url = url;
        this.title = title;
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
}

