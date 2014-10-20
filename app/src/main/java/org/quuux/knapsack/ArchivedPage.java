package org.quuux.knapsack;

import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

public class ArchivedPage implements Serializable {
    String url;
    String title;
    Date created;
    boolean read;

    public ArchivedPage(final String url, final String title) {
        this.url = url;
        this.title = title;
        this.created = new Date();
    }

    public static File getArchivePath() {
        final File base = Environment.getExternalStorageDirectory();
        return new File(base, "WebArchive");
    }

    public static File getArchivePath(final String url) {
        final Uri uri = Uri.parse(url);

        final StringBuilder sb = new StringBuilder();
        sb.append(uri.getHost());

        for (final String part : uri.getPathSegments()) {
            sb.append("-");
            sb.append(part);
        }

        return new File(getArchivePath(), sb.toString());
    }

    public static File getArchivePath(final String url, final String filename) {
        return new File(getArchivePath(url), filename);
    }

}
