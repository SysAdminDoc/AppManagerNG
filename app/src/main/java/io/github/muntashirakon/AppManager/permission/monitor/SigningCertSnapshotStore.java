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
 * Atomic JSON-on-disk store of per-package signing-certificate SHA-256
 * snapshots, mirroring {@link PermissionSnapshotStore} but in a separate file
 * so a corrupted snapshot in one feature can't disable the other.
 *
 * <p>Schema:
 * <pre>
 * {
 *   "schema_version": 1,
 *   "snapshots": {
 *     "com.foo.bar": {
 *       "version_code": 42,
 *       "cert_shas256": ["AA:BB:...", "11:22:..."]
 *     }
 *   }
 * }
 * </pre>
 */
public final class SigningCertSnapshotStore {
    public static final String TAG = "SigningCertSnapshotStore";

    @VisibleForTesting
    static final String FILE_NAME = "signing_cert_snapshots.json";
    @VisibleForTesting
    static final int SCHEMA_VERSION = 1;

    @NonNull
    private final File mFile;

    public SigningCertSnapshotStore(@NonNull Context appContext) {
        this(new File(appContext.getFilesDir(), FILE_NAME));
    }

    @VisibleForTesting
    SigningCertSnapshotStore(@NonNull File file) {
        mFile = file;
    }

    @WorkerThread
    public synchronized void put(@NonNull String packageName, @NonNull SigningCertSnapshot snapshot) {
        Map<String, SigningCertSnapshot> all = readAll();
        all.put(packageName, snapshot);
        writeAll(all);
    }

    @WorkerThread
    @Nullable
    public synchronized SigningCertSnapshot get(@NonNull String packageName) {
        return readAll().get(packageName);
    }

    @WorkerThread
    public synchronized void remove(@NonNull String packageName) {
        Map<String, SigningCertSnapshot> all = readAll();
        if (all.remove(packageName) != null) {
            writeAll(all);
        }
    }

    @WorkerThread
    public synchronized void clear() {
        //noinspection ResultOfMethodCallIgnored
        mFile.delete();
    }

    @VisibleForTesting
    @NonNull
    synchronized Map<String, SigningCertSnapshot> readAll() {
        if (!mFile.isFile()) return new HashMap<>();
        try (FileInputStream fis = new FileInputStream(mFile)) {
            byte[] buf = new byte[(int) mFile.length()];
            int read = 0;
            while (read < buf.length) {
                int n = fis.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            return parse(new String(buf, 0, read, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not read signing-cert snapshot store; resetting in-memory copy.", e);
            return new HashMap<>();
        }
    }

    @VisibleForTesting
    @NonNull
    static Map<String, SigningCertSnapshot> parse(@NonNull String json) {
        Map<String, SigningCertSnapshot> out = new HashMap<>();
        if (json.trim().isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject snapshots = root.optJSONObject("snapshots");
            if (snapshots == null) return out;
            for (Iterator<String> it = snapshots.keys(); it.hasNext(); ) {
                String packageName = it.next();
                JSONObject entry = snapshots.optJSONObject(packageName);
                if (entry == null) continue;
                long versionCode = entry.optLong("version_code", -1);
                JSONArray shas = entry.optJSONArray("cert_shas256");
                Set<String> shaSet = new HashSet<>();
                if (shas != null) {
                    for (int i = 0; i < shas.length(); ++i) {
                        String s = shas.optString(i, null);
                        if (s != null && !s.isEmpty()) shaSet.add(s);
                    }
                }
                out.put(packageName, new SigningCertSnapshot(versionCode, shaSet));
            }
        } catch (JSONException ignore) {
            // Empty store treatment, intentionally.
        }
        return out;
    }

    @WorkerThread
    private synchronized void writeAll(@NonNull Map<String, SigningCertSnapshot> all) {
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
                Log.w(TAG, "Atomic rename failed for signing-cert store; falling back to delete+rename.");
                //noinspection ResultOfMethodCallIgnored
                mFile.delete();
                if (!tmp.renameTo(mFile)) {
                    Log.w(TAG, "Signing-cert store rename failed twice; leaving stale tmp at " + tmp);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not write signing-cert snapshot store.", e);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    @VisibleForTesting
    @NonNull
    static String serialize(@NonNull Map<String, SigningCertSnapshot> all) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema_version", SCHEMA_VERSION);
            JSONObject snapshots = new JSONObject();
            for (Map.Entry<String, SigningCertSnapshot> e : all.entrySet()) {
                JSONObject entry = new JSONObject();
                entry.put("version_code", e.getValue().versionCode);
                JSONArray shas = new JSONArray();
                for (String s : e.getValue().certShas256) shas.put(s);
                entry.put("cert_shas256", shas);
                snapshots.put(e.getKey(), entry);
            }
            root.put("snapshots", snapshots);
            return root.toString(2);
        } catch (JSONException e) {
            return "{\"schema_version\":" + SCHEMA_VERSION + ",\"snapshots\":{}}";
        }
    }
}
