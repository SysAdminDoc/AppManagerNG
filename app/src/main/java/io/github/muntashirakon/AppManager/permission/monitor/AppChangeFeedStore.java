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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DurableFile;

/**
 * Atomic JSON-on-disk store for the unified app-change audit feed.
 */
public final class AppChangeFeedStore {
    public static final String TAG = "AppChangeFeedStore";

    @VisibleForTesting
    static final String FILE_NAME = "app_change_feed.json";
    @VisibleForTesting
    static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES = 200;
    public static final long MAX_STORE_BYTES = 4L * 1024L * 1024L;

    @NonNull
    private final DurableFile mFile;

    public AppChangeFeedStore(@NonNull Context appContext) {
        this(new File(appContext.getFilesDir(), FILE_NAME));
    }

    @VisibleForTesting
    AppChangeFeedStore(@NonNull File file) {
        mFile = new DurableFile(file);
    }

    @WorkerThread
    public synchronized boolean append(@NonNull AppChangeFeedEntry entry) {
        List<AppChangeFeedEntry> entries = new ArrayList<>(readAll());
        entries.add(0, entry);
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        }
        return writeAll(entries);
    }

    /** Imports records without duplicating rows already present in the local feed. */
    @WorkerThread
    public synchronized int importEntries(@NonNull List<AppChangeFeedEntry> importedEntries) {
        if (importedEntries.isEmpty()) return 0;
        List<AppChangeFeedEntry> entries = new ArrayList<>(readAll());
        Set<String> keys = new HashSet<>();
        for (AppChangeFeedEntry entry : entries) keys.add(key(entry));
        int imported = 0;
        for (AppChangeFeedEntry entry : importedEntries) {
            if (entry == null || !keys.add(key(entry))) continue;
            entries.add(entry);
            imported++;
        }
        if (imported == 0) return 0;
        entries.sort(Comparator.comparingLong((AppChangeFeedEntry entry) -> entry.timestampMillis)
                .reversed());
        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        }
        return writeAll(entries) ? imported : 0;
    }

    @WorkerThread
    @NonNull
    public synchronized List<AppChangeFeedEntry> readAll() {
        String json = mFile.read(MAX_STORE_BYTES);
        return json != null ? parse(json) : Collections.emptyList();
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
                        obj.optLong("timestamp_millis", 0L), title, body,
                        obj.optLong("before_version_code", AppChangeFeedEntry.UNKNOWN_VERSION_CODE),
                        obj.optLong("after_version_code", AppChangeFeedEntry.UNKNOWN_VERSION_CODE)));
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
                if (entry.hasVersionContext()) {
                    obj.put("before_version_code", entry.beforeVersionCode);
                    obj.put("after_version_code", entry.afterVersionCode);
                }
                array.put(obj);
            }
            root.put("entries", array);
            return root.toString(2);
        } catch (JSONException e) {
            return "{\"schema_version\":" + SCHEMA_VERSION + ",\"entries\":[]}";
        }
    }

    @WorkerThread
    private synchronized boolean writeAll(@NonNull List<AppChangeFeedEntry> entries) {
        try {
            mFile.write(serialize(entries).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            // Fail closed: the previous feed is still intact and readable.
            Log.w(TAG, "Could not write the app-change feed; the previous one was kept.", e);
            return false;
        }
    }

    @NonNull
    private static String key(@NonNull AppChangeFeedEntry entry) {
        return entry.kind + '\u0000' + entry.packageName + '\u0000' + entry.timestampMillis
                + '\u0000' + entry.title + '\u0000' + entry.body + '\u0000'
                + entry.beforeVersionCode + '\u0000' + entry.afterVersionCode;
    }
}
