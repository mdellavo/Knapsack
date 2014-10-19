package org.quuux.knapsack;

import android.app.ListActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.FileObserver;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.quuux.sack.Sack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IndexActivity extends ListActivity {

    private static final String TAG = Log.buildTag(IndexActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadArchives();
    }

    @Override
    protected void onListItemClick(final ListView l, final View v, final int position, final long id) {
        super.onListItemClick(l, v, position, id);
        final Intent intent = new Intent(this, ViewerActivity.class);
        intent.putExtra("page", (ArchivedPage)getListAdapter().getItem(position));
        startActivity(intent);
    }

    private void loadArchives() {
        new ScanArchivesTask().execute();
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
            bindView((Holder)rv.getTag(), getItem(position));
            return rv;
        }

        private View newView(final ViewGroup parent) {
            final View view = getLayoutInflater().inflate(R.layout.archived_page_row, parent, false);
            final Holder holder = new Holder();
            holder.title = (TextView)view.findViewById(R.id.title);
            holder.url = (TextView)view.findViewById(R.id.url);
            holder.screenshot = (ImageView)view.findViewById(R.id.screenshot);
            holder.favicon = (ImageView)view.findViewById(R.id.favicon);
            view.setTag(holder);
            return view;
        }

        private void bindView(final Holder holder, final ArchivedPage page) {
            holder.title.setText(page.title);
            holder.url.setText(page.url);

            final File favicon = ArchivedPage.getArchivePath(page.url, "favicon.png");
            Picasso.with(IndexActivity.this).load(favicon).into(holder.favicon);

            final File screenshot = ArchivedPage.getArchivePath(page.url, "screenshot.png");
            Picasso.with(IndexActivity.this).load(screenshot).into(holder.screenshot);

        }
    }

    class Holder {
        TextView url, title;
        ImageView screenshot, favicon;
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
            setListAdapter(new Adapter(pages));
        }
    }

}
