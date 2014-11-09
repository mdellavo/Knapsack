package org.quuux.knapsack;

import android.net.Uri;
import android.os.Environment;

import java.io.File;

public class CacheManager {
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

    public static File getArchivePath(final Page page) {
        return getArchivePath(page.url);
    }

    public static File getArchivePath(final Page page, final String filename) {
        return getArchivePath(page.url, filename);
    }

    public static File getManifest(final Page page) {
        return getArchivePath(page, "manifest.json");
    }

    public static void delete(final Page page) {
        final File dir = getArchivePath(page);

        for (final File file : dir.listFiles())
            file.delete();

        dir.delete();

    }
}
