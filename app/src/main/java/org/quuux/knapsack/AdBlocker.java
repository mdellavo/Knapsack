package org.quuux.knapsack;

import android.content.Context;
import android.net.Uri;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdBlocker {
    private static final String TAG = Log.buildTag(AdBlocker.class);

    private static final String[] BLOCKLIST_URLS = {
            "https://easylist-downloads.adblockplus.org/easylist.txt",
            "https://easylist-downloads.adblockplus.org/easyprivacy.txt"
    };
    private static final int BUF_SIZE = 4096;

    private static AdBlocker instance;

    public enum RuleType {
        PART,
        DOMAIN,
        EXACT
    }

    public static class Rule implements Serializable {

        final String line;
        final boolean isException;
        final RuleType type;
        final String selector;
        final String options;
        final String rule;
        final Pattern pattern;

        Rule(final String line, final boolean isException, final RuleType type, final String rule, final String selector, final String options) {
            this.line = line;
            this.isException = isException;
            this.type = type;
            this.rule = rule;
            this.selector = selector;
            this.options = options;
            this.pattern = buildPattern();
        }

        public static Rule parse(final String line) {
            String rule = line;
            final boolean isException = line.startsWith("@@");
            if (isException)
                rule = rule.substring(2);

            RuleType type;
            if (rule.startsWith("||")) {
                type = RuleType.DOMAIN;
            } else if (rule.startsWith("|")) {
                type = RuleType.EXACT;
            } else {
                type = RuleType.PART;
            }

            final int selectorPosition = rule.indexOf("#");
            final boolean selectorFound = selectorPosition != -1;
            String selector = selectorFound ? rule.substring(selectorPosition + 1) : null;
            rule = selectorFound ? rule.substring(0, selectorPosition) : rule;

            final int optionsPosition = rule.indexOf("$");
            String options = optionsPosition != -1 ? rule.substring(optionsPosition + 1) : null;

            rule = optionsPosition != -1 ? rule.substring(0, optionsPosition) : rule;

            return new Rule(line, isException, type, rule, selector, options);
        }

        @Override
        public boolean equals(final Object o) {
            return o instanceof Rule && ((Rule)o).line.equals(line);
        }

        @Override
        public int hashCode() {
            return this.line.hashCode();
        }

        private String replace(final String s, final Pattern pattern, final String replacement) {
            return pattern.matcher(s).replaceAll(replacement);
        }

        private static final Pattern MULTIPLE_WILDCARDS = Pattern.compile("\\*+");
        private static final Pattern ANCHORS_FOLLOWING_SEPARATOR = Pattern.compile("\\^\\|$");
        private static final Pattern SPECIAL_SYMBOLS = Pattern.compile("\\W");
        private static final Pattern REPLACE_WILDCARDS = Pattern.compile("\\\\*");
        private static final Pattern SEPARATOR_PLACEHOLDERS = Pattern.compile("\\\\^");
        private static final Pattern EXTENDED_ANCHOR = Pattern.compile("^\\\\|\\\\|");
        private static final Pattern ANCHOR_START = Pattern.compile("^\\\\|");
        private static final Pattern ANCHOR_END = Pattern.compile("\\\\|$");
        private static final Pattern LEADING_WILDCARDS = Pattern.compile("^(\\.\\*)");
        private static final Pattern TRAILING_WILDCARDS = Pattern.compile("(\\.\\*)$");

        private String transformRule() {
            String rv = rule;
            rv = replace(rv, MULTIPLE_WILDCARDS, "*");      // remove multiple wildcards
            rv = replace(rv, ANCHORS_FOLLOWING_SEPARATOR, "^");   // remove anchors following separator placeholder
            rv = replace(rv, SPECIAL_SYMBOLS, "\\$&");    // escape special symbols
            rv = replace(rv, REPLACE_WILDCARDS, ".*");  // replace wildcards by .*
            // process separator placeholders (all ANSI characters but alphanumeric characters and _%.-)
            rv = replace(rv, SEPARATOR_PLACEHOLDERS, "(?:[\\x00-\\x24\\x26-\\x2C\\x2F\\x3A-\\x40\\x5B-\\x5E\\x60\\x7B-\\x7F]|$)");
            rv = replace(rv, EXTENDED_ANCHOR, "^[\\w\\-]+:\\/+(?!\\/)(?:[^\\/]+\\.)?"); // process extended anchor at expression start
            rv = replace(rv, ANCHOR_START, "^");  // process anchor at expression start
            rv = replace(rv, ANCHOR_END, "$");  // process anchor at expression end
            rv = replace(rv, LEADING_WILDCARDS, "");  // remove leading wildcards
            rv = replace(rv, TRAILING_WILDCARDS, "");  // remove trailing wildcards
            return rv;
        }

        private Pattern buildPattern() {
            return Pattern.compile(transformRule());
        }

        public boolean hasSelector() {
            return selector != null;
        }

        public boolean match(final String url) {
            if (pattern == null)
                return false;

            final Matcher match = pattern.matcher(url);
            return match.matches();
        }
    }

    private List<Rule> mRules = new ArrayList<>();

    protected AdBlocker() {
    }

    private InputStream execute(final Request request) throws IOException {
        final OkHttpClient client = new OkHttpClient();

        final Response response = client.newCall(request).execute();

        return response.body().byteStream();
    }

    private InputStream get(final String url) throws IOException {
        final Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        return execute(request);
    }

    private long slurp(final OutputStream output, final InputStream input) throws IOException {
        final byte[] data = new byte[BUF_SIZE];

        long total = 0;
        int count;
        while ((count = input.read(data)) != -1) {
            total += count;
            output.write(data, 0, count);
        }
        return total;
    }

    private long fetch(final String url, final File path) throws IOException {
        final InputStream is = get(url);
        final InputStream input = new BufferedInputStream(is);
        final OutputStream output = new BufferedOutputStream(new FileOutputStream(path));

        final long rv = slurp(output, input);

        output.flush();
        output.close();
        input.close();

        return rv;
    }

    private void cacheRules(final File path) throws IOException {
        final ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
        out.writeObject((ArrayList<Rule>)mRules);
        out.flush();
        out.close();
    }

    private void loadRulesCache(final File path) throws Exception {
        final ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(path)));
        mRules = (ArrayList<Rule>) in.readObject();
    }

    public void load(final Context context) {

        if (mRules.size() > 0)
            return;

        boolean rebuild = false;


        for (final String blocklistUrl : BLOCKLIST_URLS) {
            final Uri uri = Uri.parse(blocklistUrl);
            final String filename = uri.getLastPathSegment();
            final File path = getPath(context, filename);

            final boolean invalid = !path.exists() || isExpired(path);

            if (invalid) {
                rebuild = true;
                try {
                    final long t1 = System.currentTimeMillis();
                    final long total = fetch(blocklistUrl, path);
                    final long t2 = System.currentTimeMillis();
                    Log.d(TAG, "fetched %s to %s (%dKB in %sms)", blocklistUrl, path, total / 1024, t2 - t1);
                } catch (IOException e) {
                    Log.e(TAG, "error fetching %s: %s", blocklistUrl, e);
                }
            }
        }

        final String cacheFilename = "rules.cache";
        final File cachePath = getPath(context, cacheFilename);

        if (cachePath.exists() && !rebuild) {
            try {
                Log.d(TAG, "loading from cache");
                final long t1 = System.currentTimeMillis();
                loadRulesCache(cachePath);
                final long t2 = System.currentTimeMillis();
                Log.d(TAG, "loaded %s rules from cache %s in %sms", mRules.size(), cacheFilename, t2 - t1);
            } catch (Exception e) {
                Log.e(TAG, "error loading rules from cache %s: %s", cachePath, e);
            }
        }

        if (mRules.size() == 0) {

            for (final String blocklistUrl : BLOCKLIST_URLS) {
                final Uri uri = Uri.parse(blocklistUrl);
                final String filename = uri.getLastPathSegment();
                final File path = getPath(context, filename);

                final long t1 = System.currentTimeMillis();
                int numRules = parse(path);
                final long t2 = System.currentTimeMillis();
                Log.d(TAG, "loaded %s rules from %s in %sms", numRules, filename, t2 - t1);
            }

            try {
                final long t3 = System.currentTimeMillis();
                cacheRules(cachePath);
                final long t4 = System.currentTimeMillis();
                Log.d(TAG, "cached %s rules from %s in %sms", mRules.size(), cacheFilename, t4 - t3);
            } catch (IOException e) {
                Log.e(TAG, "error caching rules to %s: %s", cachePath, e);
            }

        }
    }


    private int parse(final File path) {
        int count = 0;

        try {
            final BufferedReader reader = new BufferedReader(new FileReader(path));
            final String version = reader.readLine();
            final Map<String, String> head = readHead(reader);

            String line;
            while((line = reader.readLine()) != null) {

                if (line.startsWith("!"))
                    continue;

                //Log.d(TAG, "Compiling line: %s", line);

                final Rule rule = parseLine(line);
                if (rule != null) {
                    mRules.add(rule);
                    count++;
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "error parsing file", e);
        }

        return count;
    }

    private Rule parseLine(final String line) {
        return Rule.parse(line);
    }

    private File getPath(final Context context, final String filename) {
        final File parent = new File(context.getExternalCacheDir(), "blocklists");
        if (!parent.exists())
            parent.mkdirs();

        return new File(parent, filename);
    }

    private Map<String, String> readHead(final BufferedReader reader) throws IOException {
        final Map<String, String> rv = new HashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("!"))
                break;

            final Pattern patten = Pattern.compile("^!\\s+([^:]+):\\s+(.*)$");
            final Matcher matcher = patten.matcher(line);
            if (matcher.matches()) {
                rv.put(matcher.group(1), matcher.group(2));
            }
        }

        return rv;
    }

    private Date parseLastModified(final Map<String, String> head)  {
        final String lastModified = head.get("Last modified");
        if (lastModified == null)
            return new Date(0);

        final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy HH:mm zzz", Locale.US);

        Date rv;
        try {
            rv = fmt.parse(lastModified);
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing last modified", e);
            rv = new Date(0);
        }

        return rv;
    }

    private long parseExpires(final Map<String, String> head) {
        final String expires = head.get("Expires");

        if (expires == null)
            return 0;

        final Pattern pattern = Pattern.compile("(\\d+) (hours|days)\\s+.*");
        final Matcher matcher = pattern.matcher(expires);
        if (!matcher.matches())
            return 0;

        final int value = Integer.valueOf(matcher.group(1));
        final String scale = matcher.group(2);
        int multiplier = 1;
        if ("hours".equals(scale))
            multiplier = 60 * 60;
        else if ("days".equals(scale))
            multiplier = 60 * 60 * 24;
        return value * multiplier * 1000;
    }

    private boolean isExpired(final File path) {
        boolean rv;
        try {
            final BufferedReader reader = new BufferedReader(new FileReader(path));
            final String version = reader.readLine();
            final Map<String, String> head = readHead(reader);
            final Date lastModified = parseLastModified(head);
            final long expires = parseExpires(head);
            final Date expiresAt = new Date(lastModified.getTime() + expires);

            Log.d(TAG, "version: %s", version);
            Log.d(TAG, "head: %s", head);
            Log.d(TAG, "last modified: %s",  lastModified);
            Log.d(TAG, "expires: %s", expires);
            Log.d(TAG, "expires at: %s", expiresAt);

            rv = expiresAt.before(new Date());
        } catch (IOException e) {
            Log.e(TAG, "error reading head of blocklist", e);
            rv = true;
        }
        return rv;
    }

    public boolean match(final String url) {

        boolean rv = false;

        final long t1 = System.currentTimeMillis();

        int count = 0;
        for (Rule rule : mRules) {
            if (rule.hasSelector())
                continue;

            if (rule.match(url)) {
                rv = !rule.isException;
                break;
            }
            count++;
        }

        final long t2 = System.currentTimeMillis();

        Log.d(TAG, "match %s in %sms", url, t2-t1);

        return rv;
    }

    public static AdBlocker getInstance() {
        if (instance == null) {
            instance = new AdBlocker();
        }
        return instance;
    }

}
