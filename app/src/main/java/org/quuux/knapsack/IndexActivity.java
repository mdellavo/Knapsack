package org.quuux.knapsack;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.TextView;

import com.android.vending.billing.IInAppBillingService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IndexActivity extends ActionBarActivity implements AdapterView.OnItemClickListener, SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = Log.buildTag(IndexActivity.class);

    private static final int ITEM_OPEN = 0;
    private static final int ITEM_REFRESH = 1;
    private static final int ITEM_DELETE = 2;

    private static final long MILLIS_PER_DAY = 86400000;

    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private static final String ACTION_PURCHASE = "org.quuux.knapsack.action.PURCHASE";
    private static final String SKU_PREMIUM = "premium";

    private ListView mListView;
    private SwipeRefreshLayout mSwipeLayout;
    private Adapter mAdapter;

    private static boolean sNagShown;
    private Set<String> mPurchases = Collections.<String>emptySet();
    private IInAppBillingService mService;

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

        final Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);

        mPurchases = Preferences.getPurchases(this);
        onPurchasesUpdated();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ArchiveService.ACTION_ARCHIVE_UPDATE);
        filter.addAction(ArchiveService.ACTION_SYNC_COMPLETE);
        filter.addAction(ACTION_PURCHASE);
        registerReceiver(mReceiver, filter);

        checkPlayServices();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mServiceConn);
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

    @Override
    public void onBackPressed() {
        sNagShown = false;
        super.onBackPressed();
    }

    private void loadArchives() {
        new ScanArchivesTask().execute();
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
        final Page page = (Page) mListView.getAdapter().getItem(position);
        if (page.status == Page.STATUS_SUCCESS) {
            final Intent intent = new Intent(this, ViewerActivity.class);
            intent.putExtra("page", page);
            startActivity(intent);
        } else {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder
                    .setTitle(R.string.archive_page_title)
                    .setMessage(R.string.archive_page_message)
                    .setCancelable(true)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.archive, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            refreshPage(page);
                        }
                    });
            builder.create().show();
        }
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

    public void sendEvent(final String category, final String action, final String label) {
        KnapsackTracker.get(this).sendEvent(category, action, label);
    }

    public void sendEvent(final String category, final String action) {
        sendEvent(category, action, null);
    }

    private void showNag(final boolean forced) {
        if (!sNagShown || forced) {
            final FragmentManager fm = getSupportFragmentManager();
            if (fm.findFragmentByTag("nag") == null) {
                final NagDialog dialog = NagDialog.newInstance();
                dialog.show(getSupportFragmentManager(), "nag");
            }

            sendEvent("ui", "show nag");

            sNagShown = true;
        }
    }

    private void showNag() {
        showNag(false);
    }

    private void startPurchase() {

        if (mService == null)
            return;

        final Bundle response;
        try {
            response = mService.getBuyIntent(3, getPackageName(), SKU_PREMIUM, "inapp", null);
        } catch (final RemoteException e) {
            Log.e(TAG, "error starting purchase", e);
            return;
        }

        final int responseCode = response.getInt("RESPONSE_CODE");
        if (responseCode != 0) {
            Log.d(TAG, "error starting purchase: %s", response);
            return;
        }

        final PendingIntent pendingIntent = response.getParcelable("BUY_INTENT");
        try {
            startIntentSenderForResult(pendingIntent.getIntentSender(),
                    1001, new Intent(), Integer.valueOf(0), Integer.valueOf(0),
                    Integer.valueOf(0));
        } catch (final IntentSender.SendIntentException e) {
            Log.e(TAG, "error starting purchase: %s", e);
        }

        sendEvent("ui", "start purchase");
    }


    private void initBilling() {
        new QueryPurchasesTask().execute();
    }

    private void onPurchasesUpdated() {
        final boolean unlocked = mPurchases.contains(SKU_PREMIUM);

        if (unlocked) {
            hideNag();
        } else {
            showNag();
        }

        supportInvalidateOptionsMenu();
    }


    private void onPurchaseResult(final Set<String> purchases) {
        if (purchases == null)
            return;

        mPurchases = purchases;
        Preferences.setPurchases(this, purchases);
        onPurchasesUpdated();

    }

    private void onPurchaseComplete() {
        mPurchases.add(SKU_PREMIUM);
        Preferences.setPurchases(this, mPurchases);
        onPurchasesUpdated();
        sendEvent("ui", "purchase complete");
    }

    private void hideNag() {
        final FragmentManager fm = getSupportFragmentManager();
        final NagDialog nag = (NagDialog) fm.findFragmentByTag("nag");
        if (nag != null) {
            nag.dismiss();
        }

    }

    @Override
    public void onRefresh() {
        sync();
    }

    private void onSyncComplete() {
        mSwipeLayout.setRefreshing(false);
        loadArchives();
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
                        checkDeletePage(page);
                        break;
                }

                popup.dismiss();
            }
        });
        popup.show();
    }

    private void checkDeletePage(final Page page) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
                .setTitle(R.string.delete_page_title)
                .setMessage(R.string.delete_page_message)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        deletePage(page);
                    }
                }).create().show();
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

    private void sync() {
        mSwipeLayout.setRefreshing(true);
        ArchiveService.sync(this);
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

            final Callback fallbackIcon = new Callback.EmptyCallback() {
                @Override
                public void onError() {
                    Picasso.with(IndexActivity.this).load(R.drawable.ic_cloud).fit().centerInside().into(holder.favicon);
                }
            };

            final Callback loadFavicon = new Callback.EmptyCallback() {
                @Override
                public void onError() {
                    holder.screenshot.setImageDrawable(getDrawable(R.drawable.blank));
                    Picasso.with(IndexActivity.this).load(favicon).fit().centerInside().into(holder.favicon, fallbackIcon);
                }
            };

            final File screenshot = CacheManager.getArchivePath(page.url, "screenshot.png");
            Picasso.with(IndexActivity.this).load(screenshot).into(holder.screenshot, loadFavicon);

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
                            //Log.d(TAG, "adding %s", sacked.second);
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
            else if (ACTION_PURCHASE.equals(action))
                startPurchase();
        }
    };

    public static class NagDialog extends DialogFragment {

        public NagDialog() {
            super();
        }

        public static NagDialog newInstance() {
            final NagDialog rv = new NagDialog();
            return rv;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Context context = getActivity();
            if (context == null)
                return null;

            final View v = getActivity().getLayoutInflater().inflate(R.layout.nag_dialog, null);
            if (v == null)
                return null;

            final TextView nagText = (TextView) v.findViewById(R.id.nag_text);
            nagText.setText(Html.fromHtml(getString(R.string.nag_text)));

            final Button button = (Button) v.findViewById(R.id.purchase_button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    context.sendBroadcast(new Intent(ACTION_PURCHASE));
                }
            });

            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            final Dialog dialog = builder.setTitle(R.string.nag_title)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, null)
                    .setView(v)
                    .create();

            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            return dialog;
        }
    }

    final ServiceConnection mServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = IInAppBillingService.Stub.asInterface(service);
            initBilling();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    class QueryPurchasesTask extends AsyncTask<String, Void, Set<String>> {

        @Override
        protected Set<String> doInBackground(final String... params) {
            final Bundle purchases;
            try {
                purchases = mService.getPurchases(3, getPackageName(), "inapp", null);
            } catch (RemoteException e) {
                e.printStackTrace();
                return null;
            }

            final int response = purchases.getInt("RESPONSE_CODE");
            if (response != 0) {
                Log.e(TAG, "Error querying purchases: %s", purchases);
                return null;
            }

            final List<String> purchasedSkus = purchases.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
            final Set<String> rv = new HashSet<String>();
            rv.addAll(purchasedSkus);
            return rv;
        }

        @Override
        protected void onPostExecute(final Set<String> purchases) {
            onPurchaseResult(purchases);
        }
    }
}
