package org.quuux.knapsack.data;


import android.os.AsyncTask;
import android.util.Pair;

import org.quuux.feller.Log;
import org.quuux.knapsack.event.EventBus;
import org.quuux.knapsack.event.PageUpdated;
import org.quuux.knapsack.event.PagesUpdated;
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
    private boolean scanning;

    protected PageCache() {}

    private Page loadPage(final File manifest) {
        final long t1 = System.currentTimeMillis();

        Page rv = null;
        if (manifest.exists()) {
            Sack<Page> store = Sack.open(Page.class, manifest);
            final Pair<Sack.Status, Page> result = store.doLoad();
            if (result.first == Sack.Status.SUCCESS) {
                rv = result.second;
            }
        }

        final long t2 = System.currentTimeMillis();
        Log.d(TAG, "loaded %s in %s ms", manifest.getPath(), t2-t1);

        return rv;
    }

    private Page loadPage(final String url) {
        return loadPage(CacheManager.getManifest(url));
    }

    private Page loadPage(Page page) {
        return loadPage(page.getUrl());
    }

    public Page commitPage(Page page) {

        final long t1 = System.currentTimeMillis();

        final File parent = CacheManager.getArchivePath(page);

        if (!parent.exists())
            if (!parent.mkdirs())
                Log.e(TAG, "error creating directory: %s", parent);

        page = addPage(page);

        Page rv = null;
        final File manifest = CacheManager.getManifest(page);
        final Sack<Page> store = Sack.open(Page.class, manifest);
        final Pair<Sack.Status, Page> result = store.doCommit(page);
        if (result.first == Sack.Status.SUCCESS) {
            rv = result.second;
        }

        final long t2 = System.currentTimeMillis();
        Log.d(TAG, "committed %s in %s ms", manifest.getPath(), t2 - t1);

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
        scanning = true;
        new ScanArchivesTask().execute(CacheManager.getArchivePath());
    }

    private void scanningComplete() {
        scanning = false;
        EventBus.getInstance().post(new PagesUpdated());
    }

    public  boolean isLoading() {
        return scanning;
    }

    public Page getPage(final String url) {
        return mPages.get(url);
    }

    public Page getPage(final Page page) {
        return getPage(page.getUrl());
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

                if (lhs.getCreated() == null && rhs.getCreated() == null)
                    return 0;

                if (lhs.getCreated() == null)
                    return 1;

                if (rhs.getCreated() == null)
                    return -1;

                return -lhs.getCreated().compareTo(rhs.getCreated());
            }
        });

        return rv;
    }

    private Page addPage(Page page) {

        final Page existing = getPage(page);
        if (existing != null) {
            existing.update(page);
            page = existing;
        } else {
            mPages.put(page.getUrl(), page);
        }

        page.setStatus(page.getStatus());

        return page;
    }

    private List<Page> addPages(List<Page> pages) {
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
        //EventBus.getInstance().post(new PagesUpdated());
    }

    public static PageCache getInstance() {

        if (instance == null)
            instance = new PageCache();

        return instance;
    }

    class ScanArchivesTask extends AsyncTask<File, Void, List<Page>> {

        @Override
        protected List<Page> doInBackground(final File... params) {
            final long t1 = System.currentTimeMillis();

            final List<Page> rv = new ArrayList<>();

            final File[] files = params[0].listFiles();
            if (files == null)
                return rv;

            for (final File file : files) {
                final File manifest = new File(file, "manifest.json");
                if (file.isDirectory() && manifest.isFile()) {
                    final Page page = loadPage(manifest);
                    if (page != null) {
                        rv.add(page);
                    }
                }
            }

            final long t2 = System.currentTimeMillis();
            Log.d(TAG, "scanned %s in %s ms", rv.size(), t2 - t1);
            return rv;
        }

        @Override
        protected void onPostExecute(final List<Page> result) {
            super.onPostExecute(result);
            addPages(result);
            scanningComplete();
        }
    }

}
