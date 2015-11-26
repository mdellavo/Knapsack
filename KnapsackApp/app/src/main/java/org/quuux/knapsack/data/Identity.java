package org.quuux.knapsack.data;


import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.common.AccountPicker;

import org.quuux.knapsack.GCMService;
import org.quuux.knapsack.Preferences;

public class Identity {

    public static final int REQUEST_CODE_PICK_ACCOUNT = 1000;

    public static Intent pickUserAccount() {
        final String[] accountTypes = new String[]{"com.google"};
        return AccountPicker.newChooseAccountIntent(null, null, accountTypes, false, null, null, null, null);
    }

    public static void handleResponse(final Context context, final Intent data) {
        final String account = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        Preferences.setSyncAccount(context, account);
        GCMService.register(context, account);
    }
}
