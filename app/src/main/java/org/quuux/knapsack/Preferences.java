package org.quuux.knapsack;


import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Preferences extends PreferenceActivity {

    private static final String TAG = Log.buildTag(Preferences.class);

    private static final String PROPERTY_SYNC_ACCOUNT = "sync-account";
    private static final String PROPERTY_REG_ID = "registration-id";
    private static final String PROPERTY_APP_VERSION = "app-version";
    private static final String PROPERTY_PURCHASES = "purchases";
    private static final String PROPERTY_FIRST_RUN = "first-run";
    private static final String PROPERTY_WIFI_ONLY = "wifi-only";

    private static final String PREF_SYNC_ACCOUNT = "pref_sync_account";
    private static final String PREF_MORE = "pref_more_by_author";

    private static final String PUBLISHER_NAME = "Quuux Software";

    private Preference mPrefSync;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        mPrefSync = getPreferenceScreen().findPreference(PREF_SYNC_ACCOUNT);
        mPrefSync.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                setupSync();
                return true;
            }
        });

        getPreferenceScreen().findPreference(PREF_MORE).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pub:" + PUBLISHER_NAME)));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/search?q=pub:" + PUBLISHER_NAME)));
                }
                return true;
            }
        });
    }

    private void setupSync() {
        startActivityForResult(Identity.pickUserAccount(), Identity.REQUEST_CODE_PICK_ACCOUNT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSyncAccount();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Identity.REQUEST_CODE_PICK_ACCOUNT) {
            if (resultCode == RESULT_OK) {
                Identity.handleResponse(this, data);
                updateSyncAccount();
            }
        }
    }

    private void updateSyncAccount() {
        final String syncAccount = getSyncAccount(this);
        final boolean setup = !syncAccount.isEmpty();
        mPrefSync.setTitle(setup ? getString(R.string.signed_in) : getString(R.string.sign_in));
        mPrefSync.setSummary(setup ? syncAccount : getString(R.string.not_set));
    }

    public static SharedPreferences get(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static SharedPreferences.Editor edit(final Context context) {
        return get(context).edit();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void commit(final SharedPreferences.Editor edit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            edit.apply();
        } else {
            edit.commit();
        }
    }

    public static String getSyncAccount(final Context context) {
        final String rv = get(context).getString(PROPERTY_SYNC_ACCOUNT, "");
        if (rv.isEmpty()) {
            Log.i(TAG, "Sync account not found.");
        }
        return rv;
    }

    public static void setSyncAccount(final Context context, final String account) {
        final SharedPreferences.Editor edit = get(context).edit();
        edit.putString(PROPERTY_SYNC_ACCOUNT, account);
        commit(edit);
    }

    public static String getRegistrationId(final Context context, final int currentVersion) {
        final SharedPreferences prefs = get(context);
        final String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }

        final int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    public static void setRegistrationId(final Context context, final String regId, final int appVersion) {
        final SharedPreferences prefs = get(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        commit(editor);
    }

    public static boolean hasSyncAccount(final Context context) {
        return !getSyncAccount(context).isEmpty();
    }

    public static void setPurchases(final Context context, final Set<String> purchases) {
        final SharedPreferences.Editor edit = edit(context);
        edit.putStringSet(PROPERTY_PURCHASES, purchases);
        commit(edit);
    }

    public static Set<String> getPurchases(final Context context) {
        return get(context).getStringSet(PROPERTY_PURCHASES, Collections.<String>emptySet());
    }

    public static boolean isFirstRun(final Context context) {
        return get(context).getBoolean(PROPERTY_FIRST_RUN, true);
    }

    public static boolean wifiOnly(final Context context) {
        return get(context).getBoolean(PROPERTY_WIFI_ONLY, true);
    }

    public static void markFirstRun(final Context context) {
        final SharedPreferences.Editor edit = edit(context);
        edit.putBoolean(PROPERTY_FIRST_RUN, false);
        commit(edit);
    }
}
