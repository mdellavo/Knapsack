package org.quuux.knapsack.data;


import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONException;
import org.json.JSONObject;
import org.quuux.feller.Log;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class API {

    private static final String TAG = Log.buildTag(API.class);

    private static final String TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final boolean DEV = false;

    private static final String API_ROOT = DEV ? "http://dev:6543" : "https://knapsack-api.quuux.org";
    private static final String CHECKIN_URL = API_ROOT + "/device_tokens";
    private static final String PAGES_URL = API_ROOT + "/pages";

    private static final String SCOPE = "oauth2:https://www.googleapis.com/auth/userinfo.email";
    private static final String HEADER_AUTH_TOKEN = "Auth";

    static class GetPagesResponse {
        String status;
        String message;
        Date before;
        List<Page> pages;
    }

    static class SetPageRequest {
        List<Page> pages;

        public SetPageRequest(final List<Page> pages) {
            super();
            this.pages = pages;
        }
    }

    static class AddPageRequest {
        Page page;

        public AddPageRequest(final Page page) {
            super();
            this.page = page;
        }
    }

    static API instance;

    private CookieJar cookieJar = new CookieJar() {

        Map<String, List<Cookie>> cookies = new HashMap<>();

        @Override
        public void saveFromResponse(final HttpUrl url, final List<Cookie> cookies) {
            this.cookies.put(url.toString(), cookies);
        }

        @Override
        public List<Cookie> loadForRequest(final HttpUrl url) {
            List<Cookie> rv = cookies.get(url.toString());
            if (rv == null)
                rv = new ArrayList<>();
            return rv;
        }
    };

    public static API getInstance() {
        if (instance == null)
            instance = new API();
        return instance;
    }

    private OkHttpClient getClient() {
        return new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build();
    }


    private Gson getGson() {
        return new GsonBuilder()
                .setDateFormat(TIME_FORMAT)
                .create();
    }

    private Request.Builder getRequest(final String url, final String authToken) {
        return new Request.Builder()
                .url(url)
                .addHeader(HEADER_AUTH_TOKEN, authToken);
    }

    private Response execute(final Request request) throws IOException {
        final long t1 = System.currentTimeMillis();
        final Response response = getClient().newCall(request).execute();
        final long t2 = System.currentTimeMillis();
        Log.d(TAG, "%s %s (%sms) -> %s", request.method(), request.url(), t2-t1, response.code());
        return response;
    }


    private Response get(final String url, final String authToken) throws IOException {
        final Request request = getRequest(url, authToken).get().build();
       return execute(request);
    }

    private Response post(final String url, final String json, final String authToken) throws IOException {
        final Request request = getRequest(url, authToken)
                .post(RequestBody.create(JSON, json))
                .build();
        return execute(request);
    }

    private Response put(final String url, final String json, final String authToken) throws IOException {
        final Request request = getRequest(url, authToken)
                .put(RequestBody.create(JSON, json))
                .build();
        return execute(request);
    }

    private Response delete(final String url, final String json, final String authToken) throws IOException {
        final Request request = getRequest(url, authToken)
                .method("DELETE",  RequestBody.create(JSON, json))
                .build();
        return execute(request);
    }

    private boolean isOk(Response resp) throws IOException, JSONException {
        return resp.isSuccessful() && "ok".equals(new JSONObject(resp.body().string()).optString("status"));
    }

    public boolean checkin(final String authToken, final String registrationId) {
        boolean rv = false;
        try {
            final JSONObject params = new JSONObject();
            params.put("device_token", registrationId);
            params.put("model", Build.MODEL);

            final Response resp = post(CHECKIN_URL, params.toString(), authToken);
            rv = isOk(resp);
        } catch(IOException | JSONException e) {
            Log.e(TAG, "error checking in", e);
        }

        return rv;
    }

    public boolean deletePage(final String authToken, final Page page) {
        boolean rv = false;

        try {
            final JSONObject params = new JSONObject();
            params.put("url", page.getUrl());

            if (page.getUid() != null)
                params.put("uid", page.getUid());

            final Response resp = delete(PAGES_URL, params.toString(), authToken);
            rv = isOk(resp);
        } catch (JSONException | IOException e) {
            Log.e(TAG, "error deleting page", e);
        }

        return rv;
    }

    public boolean setPages(final String authToken, final List<Page> pages) {
        final SetPageRequest req = new SetPageRequest(pages);
        final Gson gson = getGson();
        final String json = gson.toJson(req);

        boolean rv = false;
        try {
            final Response resp = put(PAGES_URL, json, authToken);
            rv = isOk(resp);
        } catch (IOException | JSONException | JsonSyntaxException e) {
            Log.e(TAG, "error setting pages", e);
        }

        return rv;
    }

    public boolean addPage(final String authToken, final Page page) {
        final AddPageRequest req = new AddPageRequest(page);
        final Gson gson = getGson();
        final String json = gson.toJson(req);

        boolean rv = false;
        try {
            final Response resp = post(PAGES_URL, json, authToken);
            rv = isOk(resp);
        } catch (IOException | JSONException | JsonSyntaxException e) {
            Log.e(TAG, "error setting pages", e);
        }

        return rv;
    }

    public GetPagesResponse getPages(final String authToken, final Date before) {
        final Gson gson = getGson();

        try {
            final HttpUrl.Builder builder = HttpUrl.parse(PAGES_URL).newBuilder();
            if (before != null) {
                final SimpleDateFormat df = new SimpleDateFormat(TIME_FORMAT, Locale.US);
                builder.addQueryParameter("before", df.format(before));
            }
            final String url = builder.build().toString();
            Log.d(TAG, "url: %s", url);
            final Response resp = get(url, authToken);

            //Log.d(TAG, "json: %s", json);

            if (resp.isSuccessful()) {
                final GetPagesResponse response = gson.fromJson(resp.body().charStream(), GetPagesResponse.class);

                if ("ok".equals(response.status)) {
                   return response;
                } else {
                    Log.d(TAG, "error fetching pages: %s", response.message);
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "error fetching pages", e);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "error decoding pages", e);
        }

        return null;
    }

    public List<Page> getAllPages(final String authToken) {
        List<Page> pages = new ArrayList<>();

        Date before = null;

        GetPagesResponse response;
        do {
            response = getPages(authToken, before);
            before = response.before;
            pages.addAll(response.pages);
        } while(before != null);

        return pages;
    }

    public String getToken(final Context context, final String account, final String scope, final Intent callback) {
        String token = null;

        try {
            final Bundle extras = new Bundle();
            token = GoogleAuthUtil.getTokenWithNotification(context, account, scope, extras, callback);
        } catch (GoogleAuthException | IOException e) {
            Log.e(TAG, "error getting token", e);
        }

        return token;
    }

    public String getToken(final Context context, final String account, final Intent callback) {
        return getToken(context, account, SCOPE, callback);
    }

}
