// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Atomic JSON-on-disk store for the unified app-change audit feed.
 */
public final class AppChangeFeedStore {
    public static final String TAG = "AppChangeFeedStore";

    @VisibleForTesting
    static final String FILE_NAME = "app_change_feed.json";
    @VisibleForTesting
    static final int SCHEMA_VERSION = 1;
    @VisibleForTesting
    static final int MAX_ENTRIES = 200;
    @VisibleForTesting
    static final long MAX_STORE_BYTES = 4L * 1024L * 1024L;

    @NonNull
    private final File mFile;

    public AppChangeFeedStore(@NonNull Context appContext) {
        this(new File(appContext.getFilesDir(), FILE_NAME));
    }

    @VisibleForTesting
    AppChangeFeedStore(@NonNull File file) {
        mFile = file;
    }

    @WorkerThread
    public synchronized void append(@NonNull AppChangeFeedEntry entry) {
        List<AppChangeFeedEntry> entries = new ArrayList<>(readAll());
        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        }
        writeAll(entries);
    }

    @WorkerThread
    @NonNull
    public synchronized List<AppChangeFeedEntry> readAll() {
        if (!mFile.isFile()) return Collections.emptyList();
        long len = mFile.length();
        if (len <= 0L || len > MAX_STORE_BYTES) {
            if (len > MAX_STORE_BYTES) {
                Log.w(TAG, "Feed store is implausibly large (" + len + " bytes); treating as empty.");
            }
            return Collections.emptyList();
        }
        try (FileInputStream fis = new FileInputStream(mFile)) {
            byte[] buf = new byte[(int) len];
            int read = 0;
            while (read < buf.length) {
                int n = fis.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            return parse(new String(buf, 0, read, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not read app-change feed; treating as empty.", e);
            return Collections.emptyList();
        }
    }

    @VisibleForTesting
    @NonNull
    static List<AppChangeFeedEntry> parse(@NonNull String json) {
        if (json.trim().isEmpty()) return Collections.emptyList();
        List<AppChangeFeedEntry> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray entries = root.optJSONArray("entries");
            if (entries == null) return out;
            for (int i = 0; i < entries.length() && out.size() < MAX_ENTRIES; ++i) {
                JSONObject obj = entries.optJSONObject(i);
                if (obj == null) continue;
                String kind = obj.optString("kind", "");
                String packageName = obj.optString("package_name", "");
                String title = obj.optString("title", "");
                String body = obj.optString("body", "");
                if (kind.isEmpty() || packageName.isEmpty() || title.isEmpty()) continue;
                out.add(new AppChangeFeedEntry(kind, packageName,
                        obj.optLong("timestamp_millis", 0L), title, body));
            }
        } catch (JSONException ignore) {
            return Collections.emptyList();
        }
        return out;
    }

    @VisibleForTesting
    @NonNull
    static String serialize(@NonNull List<AppChangeFeedEntry> entries) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema_version", SCHEMA_VERSION);
            JSONArray array = new JSONArray();
            int count = Math.min(entries.size(), MAX_ENTRIES);
            for (int i = 0; i < count; ++i) {
                AppChangeFeedEntry entry = entries.get(i);
                JSONObject obj = new JSONObject();
                obj.put("kind", entry.kind);
                obj.put("package_name", entry.packageName);
                obj.put("timestamp_millis", entry.timestampMillis);
                obj.put("title", entry.title);
                obj.put("body", entry.body);
                array.put(obj);
            }
            root.put("entries", array);
            return root.toString(2);
        } catch (JSONException e) {
            return "{\"schema_version\":" + SCHEMA_VERSION + ",\"entries\":[]}";
        }
    }

    @WorkerThread
    private synchronized void writeAll(@NonNull List<AppChangeFeedEntry> entries) {
        String body = serialize(entries);
        File tmp = new File(mFile.getParentFile(), FILE_NAME + ".tmp");
        try {
            File parent = tmp.getParentFile();
            if (parent != null) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(body.getBytes(StandardCharsets.UTF_8));
                fos.getFD().sync();
            }
            if (!tmp.renameTo(mFile)) {
                Log.w(TAG, "Atomic rename failed for app-change feed; falling back to delete+rename.");
                //noinspection ResultOfMethodCallIgnored
                mFile.delete();
                if (!tmp.renameTo(mFile)) {
                    Log.w(TAG, "App-change feed rename failed twice; leaving stale tmp at " + tmp);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not write app-change feed.", e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
