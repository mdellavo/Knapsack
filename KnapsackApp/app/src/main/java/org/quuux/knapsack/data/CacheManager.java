package org.quuux.knapsack.data;

import android.net.Uri;

import org.quuux.knapsack.data.Page;

import java.io.File;

// FIXME make singleton
public class CacheManager {

    private static final String MANIFEST = "manifest.json";

    private static File root;

    public static void setRoot(final File path) {
        root = path;
    }

    public static File getArchivePath() {
        return root;
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
        return getArchivePath(page.getUrl());
    }

    public static File getArchivePath(final Page page, final String filename) {
        return getArchivePath(page.getUrl(), filename);
    }

    public static File getManifest(final Page page) {
        return getArchivePath(page.getUrl(), MANIFEST);
    }

    public static File getManifest(final String url) {
        return getArchivePath(url, MANIFEST);
    }

    public static void delete(final Page page) {
        final File dir = getArchivePath(page);

        for (final File file : dir.listFiles())
            file.delete();

        dir.delete();

    }
}
