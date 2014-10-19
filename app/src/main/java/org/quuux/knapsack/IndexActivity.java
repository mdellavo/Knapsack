package org.quuux.knapsack;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.quuux.sack.Sack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IndexActivity extends ActionBarActivity implements AdapterView.OnItemClickListener {

    private static final String TAG = Log.buildTag(IndexActivity.class);
    private static final int ITEM_OPEN = 0;
    private static final int ITEM_REFRESH = 1;
    private static final int ITEM_DELETE = 2;
    private static final int EVENTS = (FileObserver.CREATE | FileObserver.DELETE | FileObserver.ATTRIB | FileObserver.MOVED_FROM | FileObserver.MOVED_TO);

    private final Handler mHandler = new Handler();

    private Runnable mReloadCallback = new Runnable() {
        @Override
        public void run() {
            loadArchives();
        }
    };

    private FileObserver mFileObserver = mFileObserver = new FileObserver(ArchivedPage.getArchivePath().getAbsolutePath(), EVENTS) {
        @Override
        public void onEvent(final int event, final String path) {
            Log.d(TAG, "event: %s / path: %s", event, path);

            mHandler.removeCallbacks(mReloadCallback);
            mHandler.postDelayed(mReloadCallback, 500);
        }
    };
    private ListView mListView;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_index);
        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFileObserver.startWatching();
        loadArchives();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mFileObserver.stopWatching();
    }

    private void loadArchives() {
        new ScanArchivesTask().execute();
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        final Intent intent = new Intent(this, ViewerActivity.class);
        intent.putExtra("page", (ArchivedPage)mListView.getAdapter().getItem(position));
        startActivity(intent);
    }

    class Adapter extends BaseAdapter {
        private final List<ArchivedPage> mPages;

        public Adapter(final List<ArchivedPage> pages) {
            mPages = pages;
        }

        @Override
        public int getCount() {
            return mPages.size();
        }

        @Override
        public ArchivedPage getItem(final int position) {
            return mPages.get(position);
        }

        @Override
        public long getItemId(final int position) {
            return position;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            final View rv = convertView != null ? convertView : newView(parent);
            bindView((Holder) rv.getTag(), getItem(position), position);
            return rv;
        }

        private View newView(final ViewGroup parent) {
            final View view = getLayoutInflater().inflate(R.layout.archived_page_row, parent, false);
            final Holder holder = new Holder();
            holder.title = (TextView)view.findViewById(R.id.title);
            holder.url = (TextView)view.findViewById(R.id.url);
            holder.screenshot = (ImageView)view.findViewById(R.id.screenshot);
            holder.favicon = (ImageView)view.findViewById(R.id.favicon);
            holder.more = view.findViewById(R.id.more);
            view.setTag(holder);
            return view;
        }

        private void bindView(final Holder holder, final ArchivedPage page, final int position) {
            holder.position = position;
            holder.title.setText(page.title);
            holder.url.setText(page.url);

            final File favicon = ArchivedPage.getArchivePath(page.url, "favicon.png");
            Picasso.with(IndexActivity.this).load(favicon).into(holder.favicon);

            final File screenshot = ArchivedPage.getArchivePath(page.url, "screenshot.png");
            Picasso.with(IndexActivity.this).load(screenshot).into(holder.screenshot);

            holder.more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    showPopup(v, page);
                }
            });

        }
    }

    private void showPopup(final View v, final ArchivedPage page) {
        final Resources res = v.getContext().getResources();

        final ListPopupWindow popup = new ListPopupWindow(v.getContext());
        popup.setAnchorView(v);
        popup.setWidth(res.getDimensionPixelSize(R.dimen.more_row));
        popup.setAdapter(
                new ArrayAdapter<String>(
                        v.getContext(),
                        android.R.layout.simple_list_item_1,
                        android.R.id.text1,
                        res.getStringArray(R.array.more)
                )
        );
        popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                switch (position) {
                    case ITEM_OPEN:
                        openPage(page);
                        break;
                    case ITEM_REFRESH:
                        refreshPage(page);
                        break;
                    case ITEM_DELETE:
                        deletePage(page);
                        break;
                }

                popup.dismiss();
            }
        });
        popup.show();
    }

    private void deletePage(final ArchivedPage page) {
        ArchivedPage.getArchivePath(page.url).delete();
    }

    private void refreshPage(final ArchivedPage page) {
        Intent i = new Intent(this, ArchiveService.class);
        i.setAction(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_SUBJECT, page.title);
        i.putExtra(Intent.EXTRA_TEXT, page.url);
        startService(i);
    }

    private void openPage(final ArchivedPage page) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(page.url));
        startActivity(i);
    }

    class Holder {
        int position;
        TextView url, title;
        ImageView screenshot, favicon;
        View more;
    }

    class ScanArchivesTask extends AsyncTask<Void, Void, List<ArchivedPage>> {

        @Override
        protected List<ArchivedPage> doInBackground(final Void... params) {

            final List<ArchivedPage> rv = new ArrayList<ArchivedPage>();

            final File[] files = ArchivedPage.getArchivePath().listFiles();
            if (files == null)
                return rv;

            for (final File file : files) {

                final File manifest = new File(file, "manifest.json");
                final File index = new File(file, "index.mht");
                if (file.isDirectory() && manifest.isFile() && index.isFile()) {
                    final Sack<ArchivedPage> store = Sack.open(ArchivedPage.class, manifest);
                    try {
                        final Pair<Sack.Status, ArchivedPage> sacked = store.doLoad();
                        if (sacked.first == Sack.Status.SUCCESS) {
                            Log.d(TAG, "adding %s", sacked.second);
                            rv.add(sacked.second);
                        } else {
                            Log.e(TAG, "error loading sack");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "error loading sack", e);
                    }
                }
            }

            return rv;
        }

        @Override
        protected void onPostExecute(final List<ArchivedPage> pages) {
            super.onPostExecute(pages);
            mListView.setAdapter(new Adapter(pages));
        }
    }

}
