// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Atomic JSON-on-disk store of per-package component snapshots.
 */
public final class ComponentSnapshotStore {
    public static final String TAG = "ComponentSnapshotStore";

    @VisibleForTesting
    static final String FILE_NAME = "component_snapshots.json";
    @VisibleForTesting
    static final int SCHEMA_VERSION = 1;
    @VisibleForTesting
    static final long MAX_STORE_BYTES = 16L * 1024L * 1024L;

    @NonNull
    private final File mFile;

    public ComponentSnapshotStore(@NonNull Context appContext) {
        this(new File(appContext.getFilesDir(), FILE_NAME));
    }

    @VisibleForTesting
    ComponentSnapshotStore(@NonNull File file) {
        mFile = file;
    }

    @WorkerThread
    public synchronized void put(@NonNull String packageName, @NonNull ComponentSnapshot snapshot) {
        Map<String, ComponentSnapshot> all = readAll();
        all.put(packageName, snapshot);
        writeAll(all);
    }

    @WorkerThread
    @Nullable
    public synchronized ComponentSnapshot get(@NonNull String packageName) {
        return readAll().get(packageName);
    }

    @WorkerThread
    public synchronized void remove(@NonNull String packageName) {
        Map<String, ComponentSnapshot> all = readAll();
        if (all.remove(packageName) != null) {
            writeAll(all);
        }
    }

    @VisibleForTesting
    @NonNull
    synchronized Map<String, ComponentSnapshot> readAll() {
        if (!mFile.isFile()) return new HashMap<>();
        long len = mFile.length();
        if (len <= 0L || len > MAX_STORE_BYTES) {
            if (len > MAX_STORE_BYTES) {
                Log.w(TAG, "Component snapshot store is implausibly large (" + len + " bytes); treating as empty.");
            }
            return new HashMap<>();
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
            Log.w(TAG, "Could not read component snapshot store; treating as empty.", e);
            return new HashMap<>();
        }
    }

    @VisibleForTesting
    @NonNull
    static Map<String, ComponentSnapshot> parse(@NonNull String json) {
        Map<String, ComponentSnapshot> out = new HashMap<>();
        if (json.trim().isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject snapshots = root.optJSONObject("snapshots");
            if (snapshots == null) return out;
            for (Iterator<String> it = snapshots.keys(); it.hasNext(); ) {
                String packageName = it.next();
                JSONObject entry = snapshots.optJSONObject(packageName);
                if (entry == null) continue;
                out.put(packageName, new ComponentSnapshot(entry.optLong("version_code", -1),
                        readStringSet(entry.optJSONArray("components")),
                        readStringSet(entry.optJSONArray("tracker_components"))));
            }
        } catch (JSONException ignore) {
            return new HashMap<>();
        }
        return out;
    }

    @VisibleForTesting
    @NonNull
    static String serialize(@NonNull Map<String, ComponentSnapshot> all) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema_version", SCHEMA_VERSION);
            JSONObject snapshots = new JSONObject();
            for (Map.Entry<String, ComponentSnapshot> e : all.entrySet()) {
                JSONObject entry = new JSONObject();
                entry.put("version_code", e.getValue().versionCode);
                entry.put("components", toJsonArray(e.getValue().components));
                entry.put("tracker_components", toJsonArray(e.getValue().trackerComponents));
                snapshots.put(e.getKey(), entry);
            }
            root.put("snapshots", snapshots);
            return root.toString(2);
        } catch (JSONException e) {
            return "{\"schema_version\":" + SCHEMA_VERSION + ",\"snapshots\":{}}";
        }
    }

    @NonNull
    private static Set<String> readStringSet(@Nullable JSONArray array) {
        Set<String> out = new HashSet<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); ++i) {
            String value = array.optString(i, null);
            if (value != null && !value.isEmpty()) out.add(value);
        }
        return out;
    }

    @NonNull
    private static JSONArray toJsonArray(@NonNull Set<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        return array;
    }

    @WorkerThread
    private synchronized void writeAll(@NonNull Map<String, ComponentSnapshot> all) {
        String body = serialize(all);
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
                Log.w(TAG, "Atomic rename failed for component snapshot store; falling back to delete+rename.");
                //noinspection ResultOfMethodCallIgnored
                mFile.delete();
                if (!tmp.renameTo(mFile)) {
                    Log.w(TAG, "Component snapshot store rename failed twice; leaving stale tmp at " + tmp);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not write component snapshot store.", e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
