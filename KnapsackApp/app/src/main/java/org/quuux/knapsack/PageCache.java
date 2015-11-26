package org.quuux.knapsack;


import android.os.AsyncTask;
import android.util.Pair;

import org.quuux.feller.Log;
import org.quuux.sack.Sack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageCache {

    private static final String TAG = Log.buildTag(PageCache.class);
    private static PageCache instance;

    private Map<String, Page> mPages = new HashMap<>();
    private boolean mScanned = false;

    protected PageCache() {}

    public Page loadPage(final File manifest) {
        final long t1 = System.currentTimeMillis();

        Page rv = null;
        if (manifest.exists()) {
            Sack<Page> store = Sack.open(Page.class, manifest);
            final Pair<Sack.Status, Page> result = store.doLoad();
            if (result.first == Sack.Status.SUCCESS) {
                rv = result.second;
                addPage(rv);
            }
        }

        final long t2 = System.currentTimeMillis();
        Log.d(TAG, "loaded %s in %s ms", manifest.getPath(), t2-t1);

        return rv;
    }

    public Page loadPage(final String url) {
        return loadPage(CacheManager.getManifest(url));
    }

    public Page loadPage(Page page) {
        return loadPage(page.url);
    }

    public Page commitPage(final Page page) {

        final long t1 = System.currentTimeMillis();

        final File parent = CacheManager.getArchivePath(page);

        if (!parent.exists())
            if (!parent.mkdirs())
                Log.e(TAG, "error creating directory: %s", parent);

        addPage(page);

        Page rv = null;
        final File manifest = CacheManager.getManifest(page);
        final Sack<Page> store = Sack.open(Page.class, manifest);
        final Pair<Sack.Status, Page> result = store.doCommit(page);
        if (result.first == Sack.Status.SUCCESS) {
            rv = result.second;
        }

        final long t2 = System.currentTimeMillis();
        Log.d(TAG, "committed %s in %s ms", manifest.getPath(), t2-t1);
        return rv;
    }

    public void commitAsync(final Page page) {
        new AsyncTask<Page, Void, Void>() {
            @Override
            protected Void doInBackground(final Page... params) {
                commitPage(params[0]);
                return null;
            }
        }.execute(page);
    }

    public void scanPages() {
        if (mScanned)
            return;

        mScanned = true;

        final long t1 = System.currentTimeMillis();

        final List<Page> rv = new ArrayList<Page>();

        final File[] files = CacheManager.getArchivePath().listFiles();
        if (files == null)
            return;

        int count = 0;
        for (final File file : files) {
            final File manifest = new File(file, "manifest.json");
            if (file.isDirectory() && manifest.isFile()) {
                loadPage(manifest);
                count++;
            }
        }

        final long t2 = System.currentTimeMillis();
        Log.d(TAG, "scanned %s in %s ms", count, t2 - t1);
    }

    public Page getPage(final String url) {
        return mPages.get(url);
    }

    public Page getPage(final Page page) {
        return getPage(page.url);
    }

    public Page ensurePage(final Page page) {
        Page rv = getPage(page);

        if (rv == null) {
            rv = loadPage(page);
            if (rv == null) {
                rv = commitPage(page);
            }
        }

        return rv;
    }

    public List<Page> getPages() {
        return new ArrayList<Page>(mPages.values());
    }

    public List<Page> getPagesSorted() {
        final List<Page> rv = getPages();
        Collections.sort(rv, new Comparator<Page>() {
            @Override
            public int compare(final Page lhs, final Page rhs) {

                if (lhs.created == null && rhs.created == null)
                    return 0;

                if (lhs.created == null)
                    return 1;

                if (rhs.created == null)
                    return -1;

                return -lhs.created.compareTo(rhs.created);
            }
        });

        return rv;
    }

    public Page addPage(Page page) {

        final Page existing = getPage(page);
        if (existing != null) {
            existing.update(page);
            page = existing;
        } else {
            mPages.put(page.url, page);
        }

        page.setStatus(page.status);

        return page;
    }

    public List<Page> addPages(List<Page> pages) {
        final List<Page> rv = new ArrayList<>(pages.size());

        for (Page page : pages)
            rv.add(addPage(page));

        return rv;
    }

    public void deletePage(final Page page) {
        final long t1 = System.currentTimeMillis();
        mPages.remove(page);
        CacheManager.delete(page);
        final long t2 = System.currentTimeMillis();
        Log.d(TAG, "deleted %s in %s", CacheManager.getArchivePath(page).getPath(), t2-t1);
    }

    public static PageCache getInstance() {

        if (instance == null)
            instance = new PageCache();

        return instance;
    }

}
