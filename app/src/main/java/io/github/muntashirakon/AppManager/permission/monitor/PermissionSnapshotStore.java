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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DurableFile;

/**
 * Atomic JSON-on-disk store of per-package dangerous-permission snapshots, used
 * by the Permission Change Monitor to diff across {@code PACKAGE_REPLACED}
 * broadcasts.
 *
 * <p>Schema:
 * <pre>
 * {
 *   "schema_version": 1,
 *   "snapshots": {
 *     "com.foo.bar": {
 *       "version_code": 42,
 *       "dangerous_perms": ["android.permission.CAMERA", "android.permission.RECORD_AUDIO"]
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>Writes go through {@link DurableFile}, so a crash — or a failing fsync or
 * rename — leaves the previous snapshot intact and readable rather than
 * destroying it. Reads are tolerant of an empty / missing / malformed file
 * (treated as "no snapshots known yet") so the receiver never fails on
 * first-run or corrupted state — at worst it can't report on the very next
 * update.
 */
public final class PermissionSnapshotStore {
    public static final String TAG = "PermissionSnapshotStore";

    @VisibleForTesting
    static final String FILE_NAME = "permission_snapshots.json";
    @VisibleForTesting
    static final int SCHEMA_VERSION = 2;
    /**
     * Hard ceiling on the on-disk store. A well-formed store for every
     * installed package is far smaller than this; a file larger than the cap is
     * treated as corrupt. The cap also avoids the {@code (int) file.length()}
     * truncation below wrapping negative (NegativeArraySizeException) or
     * allocating a multi-gigabyte buffer (OOM) on a corrupted/grown file.
     */
    @VisibleForTesting
    static final long MAX_STORE_BYTES = 16L * 1024L * 1024L;

    @NonNull
    private final DurableFile mFile;

    public PermissionSnapshotStore(@NonNull Context appContext) {
        this(new File(appContext.getFilesDir(), FILE_NAME));
    }

    @VisibleForTesting
    PermissionSnapshotStore(@NonNull File file) {
        mFile = new DurableFile(file);
    }

    /**
     * @return whether the snapshot was durably persisted.
     */
    @WorkerThread
    public synchronized boolean put(@NonNull String packageName, @NonNull PermissionSnapshot snapshot) {
        Map<String, PermissionSnapshot> all = readAll();
        all.put(packageName, snapshot);
        return writeAll(all);
    }

    @WorkerThread
    @Nullable
    public synchronized PermissionSnapshot get(@NonNull String packageName) {
        return readAll().get(packageName);
    }

    @WorkerThread
    public synchronized boolean remove(@NonNull String packageName) {
        Map<String, PermissionSnapshot> all = readAll();
        if (all.remove(packageName) == null) {
            return true;
        }
        return writeAll(all);
    }

    @WorkerThread
    public synchronized void clear() {
        mFile.delete();
    }

    @VisibleForTesting
    @NonNull
    synchronized Map<String, PermissionSnapshot> readAll() {
        // Tolerant: a corrupted store reverts to "no snapshots known" so the
        // monitor stops claiming a regression on every future update; the
        // next per-package put() rebuilds the cache for that package.
        String json = mFile.read(MAX_STORE_BYTES);
        return json != null ? parse(json) : new HashMap<>();
    }

    @VisibleForTesting
    @NonNull
    static Map<String, PermissionSnapshot> parse(@NonNull String json) {
        Map<String, PermissionSnapshot> out = new HashMap<>();
        if (json.trim().isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("schema_version", 0) != SCHEMA_VERSION) {
                // An older schema recorded only the dangerous set. Discard it so the next prime
                // rebuilds a full baseline instead of alerting on facts that were never recorded.
                return out;
            }
            JSONObject snapshots = root.optJSONObject("snapshots");
            if (snapshots == null) return out;
            for (Iterator<String> it = snapshots.keys(); it.hasNext(); ) {
                String packageName = it.next();
                JSONObject entry = snapshots.optJSONObject(packageName);
                if (entry == null) continue;
                long versionCode = entry.optLong("version_code", -1);
                Set<String> dangerous = readStringSet(entry.optJSONArray("dangerous_perms"));
                Set<String> requested = readStringSet(entry.optJSONArray("requested_perms"));
                if (requested.isEmpty()) requested = dangerous;
                out.put(packageName, new PermissionSnapshot(versionCode, dangerous, requested,
                        readDeclared(entry.optJSONObject("declared_perms"))));
            }
        } catch (JSONException ignore) {
            // Treated as empty store, intentionally.
        }
        return out;
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

    @NonNull
    private static Map<String, DeclaredPermission> readDeclared(@Nullable JSONObject object) {
        Map<String, DeclaredPermission> out = new TreeMap<>();
        if (object == null) return out;
        for (Iterator<String> it = object.keys(); it.hasNext(); ) {
            String name = it.next();
            JSONObject entry = object.optJSONObject(name);
            if (entry == null || name.isEmpty()) continue;
            out.put(name, new DeclaredPermission(name, entry.optInt("protection", -1),
                    entry.isNull("signer") ? null : entry.optString("signer", null)));
        }
        return out;
    }

    @NonNull
    private static JSONObject toDeclaredObject(@NonNull Map<String, DeclaredPermission> declared)
            throws JSONException {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, DeclaredPermission> e : declared.entrySet()) {
            JSONObject entry = new JSONObject();
            entry.put("protection", e.getValue().protectionLevel);
            if (e.getValue().ownerSigner != null) entry.put("signer", e.getValue().ownerSigner);
            object.put(e.getKey(), entry);
        }
        return object;
    }

    @WorkerThread
    private synchronized boolean writeAll(@NonNull Map<String, PermissionSnapshot> all) {
        try {
            mFile.write(serialize(all).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            // Fail closed: the previous store is still intact and readable.
            Log.w(TAG, "Could not write snapshot store; the previous one was kept.", e);
            return false;
        }
    }

    @VisibleForTesting
    @NonNull
    static String serialize(@NonNull Map<String, PermissionSnapshot> all) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema_version", SCHEMA_VERSION);
            JSONObject snapshots = new JSONObject();
            for (Map.Entry<String, PermissionSnapshot> e : all.entrySet()) {
                JSONObject entry = new JSONObject();
                entry.put("version_code", e.getValue().versionCode);
                entry.put("dangerous_perms", toJsonArray(e.getValue().dangerousPermissions));
                entry.put("requested_perms", toJsonArray(e.getValue().requestedPermissions));
                entry.put("declared_perms", toDeclaredObject(e.getValue().declaredPermissions));
                snapshots.put(e.getKey(), entry);
            }
            root.put("snapshots", snapshots);
            return root.toString(2);
        } catch (JSONException e) {
            return "{\"schema_version\":" + SCHEMA_VERSION + ",\"snapshots\":{}}";
        }
    }
}
