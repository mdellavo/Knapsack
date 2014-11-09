package org.quuux.knapsack;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;

public class GCMService extends IntentService {

    private static final String TAG = Log.buildTag(GCMService.class);

    private static final String ACTION_REGISTER = "org.quuux.knapsack.action.REGISTER";

    private static final String SENDER_ID = "843379878054";
    private static final String EXTRA_ACCOUNT = "account";

    public GCMService() {
        super(GCMService.class.getName());
    }

    public static void register(final Context context, final String account) {
        context.startService(getRegistrationIntent(context, account));
    }

    public static Intent getRegistrationIntent(final Context context, final String account) {
        final Intent intent = new Intent(context, GCMService.class);
        intent.setAction(ACTION_REGISTER);
        intent.putExtra(EXTRA_ACCOUNT, account);
        return intent;
    }

    @Override
    protected void onHandleIntent(final Intent intent) {

        Log.d(TAG, "got intent: %s", intent);

        if (intent != null) {
            final String action = intent.getAction();
            final String account = intent.getStringExtra(EXTRA_ACCOUNT);

            if (ACTION_REGISTER.equals(action)) {

                final int currentVersion = getAppVersion();
                String registrationId = Preferences.getRegistrationId(this, currentVersion);

                if (registrationId.isEmpty()) {
                    Log.d(TAG, "registering...");
                    registrationId = doRegistration();
                }

                final Intent callback = GCMService.getRegistrationIntent(this, account);
                final String authToken = API.getToken(this, account, callback);

                if (!isEmpty(registrationId) & !isEmpty(authToken)) {
                    Log.d(TAG, "registered, checking in...");

                    if (API.checkin(authToken, registrationId)) {
                        Log.d(TAG, "registration complete!");
                        Preferences.setRegistrationId(this, registrationId, currentVersion);
                    }
                } else {
                    Log.d(TAG, "registration failed!");
                }
            }
        }

        GCMBroadcastReceiver.completeWakefulIntent(intent);
    }

    private boolean isEmpty(final String s) {
        return s == null || s.isEmpty();
    }


    private String doRegistration() {
        String registrationId = null;
        final GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        try {
            registrationId = gcm.register(SENDER_ID);
        } catch (IOException e) {
            Log.e(TAG, "error registering", e);
        }

        return registrationId;
    }


    private int getAppVersion() {
        try {
            final PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

}
