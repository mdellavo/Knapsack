package org.quuux.knapsack;


import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public class API {

    private static final String TAG = Log.buildTag(API.class);

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String API_ROOT = "http://knapsack.quuux.org";
    private static final String CHECKIN_URL = API_ROOT + "/device_tokens";
    private static final String PAGES_URL = API_ROOT + "/pages";

    private static final String SCOPE = "oauth2:https://www.googleapis.com/auth/userinfo.email";

    static class GetPagesResponse {
        String status;
        String message;
        List<Page> pages;
    }

    static class SetPageRequest {
        String auth_token;
        List<Page> pages;

        public SetPageRequest(final String auth_token, final List<Page> pages) {
            super();
            this.auth_token = auth_token;
            this.pages = pages;
        }
    }

    private static String execute(final Request request) throws IOException {
        final OkHttpClient client = new OkHttpClient();

        final long t1 = System.currentTimeMillis();
        final Response response = client.newCall(request).execute();
        final long t2 = System.currentTimeMillis();

        Log.d(TAG, "%s %s (%sms)", request.method(), request.urlString(), t2-t1);

        return response.body().string();
    }

    private static String get(final String url) throws IOException {
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

       return execute(request);
    }

    private static String post(final String url, final String json) throws IOException {
        final Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON, json))
                .build();
        return execute(request);
    }

    private static String put(final String url, final String json) throws IOException {
        final Request request = new Request.Builder()
                .url(url)
                .put(RequestBody.create(JSON, json))
                .build();
        return execute(request);
    }

    private static String delete(final String url, final String json) throws IOException {
        final Request request = new Request.Builder()
                .url(url)
                .method("DELETE",  RequestBody.create(JSON, json))
                .build();
        return execute(request);
    }

    public static boolean checkin(final String authToken, final String registrationId) {
        boolean rv = false;
        try {
            final JSONObject params = new JSONObject();
            params.put("device_token", registrationId);
            params.put("model", Build.MODEL);
            params.put("auth_token", authToken);

            final String body = post(CHECKIN_URL, params.toString());
            final JSONObject resp = new JSONObject(body);
            rv = "ok".equals(resp.optString("status"));
        } catch(IOException e) {
            Log.e(TAG, "error checking in", e);
        } catch (JSONException e) {
            Log.e(TAG, "error checking in", e);
        }

        return rv;
    }

    public static boolean deletePage(final String authToken, final Page page) {
        boolean rv = false;

        try {
            final JSONObject params = new JSONObject();
            params.put("auth_token", authToken);
            params.put("url", page.url);

            if (page.uid != null)
                params.put("uid", page.uid);

            final String json = delete(PAGES_URL, params.toString());
            final JSONObject resp = new JSONObject(json);
            rv = "ok".equals(resp.optString("status"));

        } catch (JSONException e) {
            Log.e(TAG, "error deleting page", e);
        } catch (IOException e) {
            Log.e(TAG, "error deleting page", e);
        }

        return rv;
    }

    public static boolean setPages(final String authToken, final List<Page> pages) {
        final SetPageRequest req = new SetPageRequest(authToken, pages);
        final Gson gson = getGson();
        final String json = gson.toJson(req);

        //Log.d(TAG, "pages = %s", json);

        boolean rv = false;
        try {
            final String respJson = put(PAGES_URL, json);
            final JSONObject resp = new JSONObject(respJson);
            rv = "ok".equals(resp.optString("status"));
        } catch (IOException e) {
            Log.e(TAG, "error setting pages", e);
        } catch (JSONException e) {
            Log.e(TAG, "error setting pages", e);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "error setting pages", e);
        }

        return rv;
    }

    public static List<Page> getPages(final String authToken) {

        List<Page> rv = null;

        final Gson gson = getGson();

        try {
            final String json = get(PAGES_URL + "?auth_token=" + authToken);

            //Log.d(TAG, "json: %s", json);

            final GetPagesResponse response = gson.fromJson(json, GetPagesResponse.class);

            if ("ok".equals(response.status)) {
                rv = response.pages;
            } else {
                Log.d(TAG, "error fetching pages: %s", response.message);
            }

        } catch (IOException e) {
            Log.e(TAG, "error fetching pages", e);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "error decoding pages", e);
        }

        return rv;
    }

    private static Gson getGson() {
        return new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
                .create();
    }

    public static String getToken(final Context context, final String account, final String scope, final Intent callback) {
        String token = null;

        try {
            final Bundle extras = new Bundle();
            token = GoogleAuthUtil.getTokenWithNotification(context, account, scope, extras, callback);
        } catch (UserRecoverableAuthException e) {
            Log.e(TAG, "error getting token", e);
        } catch (GoogleAuthException e) {
            Log.e(TAG, "error getting token", e);
        } catch (IOException e) {
            Log.e(TAG, "error getting token", e);
        }

        return token;
    }

    public static String getToken(final Context context, final String account, final Intent callback) {
        return getToken(context, account, SCOPE, callback);
    }
}
