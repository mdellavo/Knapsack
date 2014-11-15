package org.quuux.knapsack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.quuux.sack.Sack;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class IndexActivity extends ActionBarActivity implements AdapterView.OnItemClickListener, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = Log.buildTag(IndexActivity.class);

    private static final int ITEM_OPEN = 0;
    private static final int ITEM_REFRESH = 1;
    private static final int ITEM_DELETE = 2;

    private static final long MILLIS_PER_DAY = 86400000;

    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private ListView mListView;
    private SwipeRefreshLayout mSwipeLayout;
    private Adapter mAdapter;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_index);

        final String syncAccount = Preferences.getSyncAccount(this);
        final boolean hasSync = !syncAccount.isEmpty();

        mSwipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        mSwipeLayout.setOnRefreshListener(this);
        mSwipeLayout.setEnabled(hasSync);

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);

        if (checkPlayServices()) {
            if (hasSync) {
                GCMService.register(this, syncAccount);
            }

        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }

        mAdapter = new Adapter();
        mListView.setAdapter(mAdapter);

        loadArchives();
        sync();
    }

    @Override
    protected void onResume() {
        super.onResume();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ArchiveService.ACTION_ARCHIVE_UPDATE);
        filter.addAction(ArchiveService.ACTION_SYNC_COMPLETE);
        registerReceiver(mReceiver, filter);

        checkPlayServices();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        final boolean hasSync = Preferences.hasSyncAccount(this);
        menu.findItem(R.id.setup_sync).setVisible(!hasSync);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        boolean rv;
        switch (item.getItemId()) {
            case R.id.setup_sync:
                startActivityForResult(Identity.pickUserAccount(), Identity.REQUEST_CODE_PICK_ACCOUNT);
                rv = true;
                break;

            case R.id.settings:
                startActivity(new Intent(this, Preferences.class));
                rv = true;
                break;

            default:
                rv = super.onOptionsItemSelected(item);
                break;
        }

        return rv;
    }

    private void sync() {
        mSwipeLayout.setRefreshing(true);
        ArchiveService.sync(this);
    }

    private void loadArchives() {
        new ScanArchivesTask().execute();
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        final Intent intent = new Intent(this, ViewerActivity.class);
        intent.putExtra("page", (Page) mListView.getAdapter().getItem(position));
        startActivity(intent);
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == Identity.REQUEST_CODE_PICK_ACCOUNT) {
            if (resultCode == RESULT_OK) {
                Identity.handleResponse(this, data);
                invalidateOptionsMenu();
            }
        }
    }

    @Override
    public void onRefresh() {
        sync();
    }

    class Adapter extends BaseAdapter {
        private final List<Page> mPages;

        public Adapter() {
            mPages = new ArrayList<Page>();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getCount() {
            return mPages.size();
        }

        @Override
        public Page getItem(final int position) {
            return mPages.get(position);
        }

        @Override
        public long getItemId(final int position) {
            return mPages.get(position).url.hashCode();
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
            holder.created = (TextView) view.findViewById(R.id.created);
            holder.read = (TextView) view.findViewById(R.id.read);
            view.setTag(holder);
            return view;
        }

        private void bindView(final Holder holder, final Page page, final int position) {
            holder.position = position;
            holder.title.setText(page.title);
            holder.url.setText(Uri.parse(page.url).getHost());

            final File favicon = CacheManager.getArchivePath(page.url, "favicon.png");
            Picasso.with(IndexActivity.this).load(favicon).into(holder.favicon);

            final File screenshot = CacheManager.getArchivePath(page.url, "screenshot.png");
            Picasso.with(IndexActivity.this).load(screenshot).into(holder.screenshot, new Callback() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onError() {
                    Picasso.with(IndexActivity.this).load(favicon).into(holder.screenshot);
                }
            });

            holder.more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    showPopup(v, page);
                }
            });

            holder.created.setText(page.created != null ? formatCreated(page.created) : null);
            holder.created.setVisibility(page.created != null ? View.VISIBLE : View.GONE);

            holder.read.setText(page.read ? R.string.read : R.string.unread);
            holder.read.setVisibility(page.read ? View.GONE : View.VISIBLE);

        }

        private CharSequence formatCreated(final Date created) {
            final Date now = new Date();
            final long delta = (now.getTime() - created.getTime()) / MILLIS_PER_DAY;

            final CharSequence rv;

            if (delta == 0) {
                rv = "Today";
            } else if (delta == 1) {
                rv = "Yesterday";
            } else if (delta < 7) {
                rv = new SimpleDateFormat("E").format(created);
            } else {
                rv = new SimpleDateFormat("M/d").format(created);
            }

            return rv;
        }

        public void update(final List<Page> pages) {
            mPages.clear();
            mPages.addAll(pages);
            notifyDataSetChanged();
        }

        public void remove(final Page page) {
            mPages.remove(page);
            notifyDataSetChanged();
        }
    }

    private void showPopup(final View v, final Page page) {
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

    private void deletePage(final Page page) {

        mAdapter.remove(page);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... voids) {

                CacheManager.delete(page);

                final String account = Preferences.getSyncAccount(IndexActivity.this);

                if (!account.isEmpty()) {
                    final String authToken = API.getToken(IndexActivity.this, account,
                            GCMService.getRegistrationIntent(IndexActivity.this, account));
                    API.deletePage(authToken, page);
                }


                return null;
            }
        }.execute();
    }

    private void refreshPage(final Page page) {
        Intent i = new Intent(this, ArchiveService.class);
        i.setAction(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_SUBJECT, page.title);
        i.putExtra(Intent.EXTRA_TEXT, page.url);
        startService(i);
    }

    private void openPage(final Page page) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(page.url));
        startActivity(i);
    }

    class Holder {
        int position;
        TextView url, title, created, read;
        ImageView screenshot, favicon;
        View more;
    }

    class ScanArchivesTask extends AsyncTask<Void, Void, List<Page>> {

        @Override
        protected List<Page> doInBackground(final Void... params) {

            final List<Page> rv = new ArrayList<Page>();

            final File[] files = CacheManager.getArchivePath().listFiles();
            if (files == null)
                return rv;

            for (final File file : files) {

                final File manifest = new File(file, "manifest.json");
                final File index = new File(file, "index.mht");
                if (file.isDirectory() && manifest.isFile()) {
                    final Sack<Page> store = Sack.open(Page.class, manifest);
                    try {
                        final Pair<Sack.Status, Page> sacked = store.doLoad();
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

        @Override
        protected void onPostExecute(final List<Page> pages) {
            super.onPostExecute(pages);
            mAdapter.update(pages);
        }
    }

    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (ArchiveService.ACTION_ARCHIVE_UPDATE.equals(action))
                loadArchives();
            else if(ArchiveService.ACTION_SYNC_COMPLETE.equals(action))
                onSyncComplete();
        }
    };

    private void onSyncComplete() {
        mSwipeLayout.setRefreshing(false);
    }

}
