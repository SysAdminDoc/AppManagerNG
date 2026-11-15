// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import io.github.muntashirakon.AppManager.backup.schedule.AutoBackupWorker;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.StorageUtils;

/**
 * The folder access AppManagerNG keeps for backups. Android keeps a limited number of persisted folder grants for each
 * app and drops the oldest once there are more, which can be the folder backups go to. Each grant taken here for a
 * backup folder or an import is recorded, so it can be let go once nothing uses it. Grants taken anywhere else, as by
 * the file manager, and grants taken before this record existed are never let go here.
 */
public final class BackupDestinationGrants {
    private static final String TAG = "BackupDestinationGrants";

    @IntDef({PURPOSE_DESTINATION, PURPOSE_IMPORT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Purpose {
    }

    /** A folder backups can go to. It's kept while it holds backups. */
    public static final int PURPOSE_DESTINATION = 1;
    /** A folder backups are imported from. It's let go once the import ends. */
    public static final int PURPOSE_IMPORT = 2;

    @VisibleForTesting
    static final String PREFS_NAME = "backup_destination_grants";
    private static final String KEY_OWNED = "owned";
    private static final int MODES = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    /** The persisted grants as Android keeps them. */
    @VisibleForTesting
    interface GrantStore {
        void take(@NonNull Uri uri, int modes);

        void release(@NonNull Uri uri, int modes);

        @NonNull
        List<UriPermission> persisted();
    }

    private static final Object LOCK = new Object();
    /** Grants taken by this process, which the flow that took them lets go. The startup sweep leaves them alone. */
    private static final Set<String> sTakenThisProcess = Collections.synchronizedSet(new HashSet<>());
    @Nullable
    private static GrantStore sStoreForTesting;
    @Nullable
    private static Predicate<Uri> sBackupProbeForTesting;
    @Nullable
    private static BooleanSupplier sBackupRunningForTesting;

    private BackupDestinationGrants() {
    }

    /**
     * Takes lasting access to a folder the user picked and checks Android kept it. A grant that already existed and
     * wasn't taken here, as one the file manager holds, stays out of the record.
     *
     * @throws SecurityException if the access couldn't be taken or wasn't kept
     */
    @AnyThread
    public static void acquire(@NonNull Context context, @NonNull Uri treeUri, int modes, @Purpose int purpose) {
        modes &= MODES;
        synchronized (LOCK) {
            GrantStore store = store(context);
            Map<String, Integer> owned = readOwned(context);
            String key = treeUri.toString();
            boolean existed = findGrant(store, treeUri) != null;
            boolean recordedNow = false;
            if (!existed || owned.containsKey(key)) {
                Integer before = owned.get(key);
                // A destination is kept while it holds backups, so it wins over an import
                int newPurpose = before != null && before == PURPOSE_DESTINATION ? PURPOSE_DESTINATION : purpose;
                if (before == null || before != newPurpose) {
                    owned.put(key, newPurpose);
                    // Recorded before it's taken, so a process that dies in between leaves something to clean up
                    recordedNow = writeOwned(context, owned) && before == null;
                }
            }
            try {
                store.take(treeUri, modes);
                UriPermission grant = findGrant(store, treeUri);
                if (grant == null || ((modes & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0 && !grant.isReadPermission())
                        || ((modes & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0 && !grant.isWritePermission())) {
                    throw new SecurityException("Android didn't keep the access to " + treeUri);
                }
            } catch (RuntimeException e) {
                if (!existed) {
                    // Access kept without the modes asked for can't be used, so it doesn't take up a slot
                    dropUnusable(store, treeUri);
                }
                if (recordedNow) {
                    owned.remove(key);
                    writeOwned(context, owned);
                }
                throw e instanceof SecurityException ? (SecurityException) e : new SecurityException(e.getMessage(), e);
            }
            sTakenThisProcess.add(key);
        }
    }

    /**
     * Takes access to a folder and makes it the backup folder. On failure the last backup folder stays.
     *
     * @return the backup folder it replaced, to hand to {@link #releaseIfUnused(Context, Uri)} off the main thread
     * @throws SecurityException if the access couldn't be taken or wasn't kept
     * @throws IllegalArgumentException if the URI isn't a folder
     */
    @AnyThread
    @NonNull
    public static Uri selectDestination(@NonNull Context context, @NonNull Uri treeUri, int modes) {
        Uri destination = StorageUtils.getFixedTreeUri(treeUri);
        // Held across both steps, so a release running meanwhile sees the new folder as in use
        synchronized (LOCK) {
            acquire(context, treeUri, modes, PURPOSE_DESTINATION);
            return useDestination(destination);
        }
    }

    /**
     * Makes a folder the backup folder.
     *
     * @return the backup folder it replaced, to hand to {@link #releaseIfUnused(Context, Uri)} off the main thread
     */
    @AnyThread
    @NonNull
    public static Uri useDestination(@NonNull Uri destination) {
        synchronized (LOCK) {
            Uri previous = Prefs.Storage.getVolumePath();
            if (!previous.equals(destination)) {
                Prefs.Storage.setVolumePath(destination.toString());
            }
            return previous;
        }
    }

    /**
     * Lets go of the access taken here for a folder that's no longer the backup folder, once nothing uses it: no
     * backup tag policy points there, no backup or restore is running, and it holds no backups. A running backup
     * leaves it for the next start of the app.
     */
    @WorkerThread
    public static void releaseIfUnused(@NonNull Context context, @NonNull Uri previous) {
        String treeKey = treeKey(previous);
        if (treeKey == null) {
            return;
        }
        List<String> candidates = new ArrayList<>();
        synchronized (LOCK) {
            for (String key : readOwned(context).keySet()) {
                if (treeKey.equals(treeKey(Uri.parse(key)))) {
                    candidates.add(key);
                }
            }
        }
        releaseStale(context, candidates);
    }

    /**
     * Lets go of the access taken for an import once it ends, unless the folder is also a backup folder.
     */
    @WorkerThread
    public static void releaseAfterImport(@NonNull Context context, @NonNull Uri importDirectory) {
        String treeKey = treeKey(importDirectory);
        if (treeKey == null) {
            return;
        }
        synchronized (LOCK) {
            Map<String, Integer> owned = readOwned(context);
            for (Map.Entry<String, Integer> entry : owned.entrySet()) {
                Uri uri = Uri.parse(entry.getKey());
                if (entry.getValue() == PURPOSE_IMPORT && treeKey.equals(treeKey(uri))
                        && !isReferenced(context, treeKey)) {
                    release(context, owned, uri);
                    return;
                }
            }
        }
    }

    /**
     * Run once when the app starts. Forgets grants Android no longer holds, as when the folder's app was removed, and
     * lets go of the ones taken here that nothing uses any more, as a folder picked just before the app was closed.
     */
    @WorkerThread
    public static void pruneStale(@NonNull Context context) {
        List<String> candidates = new ArrayList<>();
        synchronized (LOCK) {
            Map<String, Integer> owned = readOwned(context);
            if (owned.isEmpty()) {
                return;
            }
            Set<String> persisted = new HashSet<>();
            for (UriPermission permission : store(context).persisted()) {
                persisted.add(permission.getUri().toString());
            }
            boolean changed = false;
            for (Iterator<String> it = owned.keySet().iterator(); it.hasNext(); ) {
                String key = it.next();
                if (!persisted.contains(key)) {
                    it.remove();
                    changed = true;
                } else if (!sTakenThisProcess.contains(key)) {
                    candidates.add(key);
                }
            }
            if (changed) {
                writeOwned(context, owned);
            }
        }
        releaseStale(context, candidates);
    }

    /**
     * The folder's access now belongs to someone else, as the file manager, and is left alone here.
     */
    @AnyThread
    public static void disown(@NonNull Context context, @NonNull Uri treeUri) {
        String treeKey = treeKey(treeUri);
        if (treeKey == null) {
            return;
        }
        synchronized (LOCK) {
            Map<String, Integer> owned = readOwned(context);
            boolean changed = false;
            for (Iterator<String> it = owned.keySet().iterator(); it.hasNext(); ) {
                if (treeKey.equals(treeKey(Uri.parse(it.next())))) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) {
                writeOwned(context, owned);
            }
        }
    }

    private static void releaseStale(@NonNull Context context, @NonNull List<String> candidates) {
        if (candidates.isEmpty() || isBackupRunning()) {
            return;
        }
        for (String key : candidates) {
            Uri uri = Uri.parse(key);
            String treeKey = treeKey(uri);
            Integer purpose;
            synchronized (LOCK) {
                purpose = readOwned(context).get(key);
            }
            if (purpose == null || treeKey == null || isReferenced(context, treeKey)) {
                continue;
            }
            // Read outside the lock, since a network folder can take a while to answer
            if (purpose == PURPOSE_DESTINATION && holdsBackups(StorageUtils.getFixedTreeUri(uri))) {
                continue;
            }
            synchronized (LOCK) {
                Map<String, Integer> owned = readOwned(context);
                // Picked again or put to use meanwhile
                if (!Objects.equals(owned.get(key), purpose) || isReferenced(context, treeKey) || isBackupRunning()) {
                    continue;
                }
                release(context, owned, uri);
            }
        }
    }

    private static void dropUnusable(@NonNull GrantStore store, @NonNull Uri uri) {
        try {
            if (findGrant(store, uri) != null) {
                store.release(uri, MODES);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Couldn't let go of the unusable access to %s", e, uri);
        }
    }

    private static void release(@NonNull Context context, @NonNull Map<String, Integer> owned, @NonNull Uri uri) {
        try {
            store(context).release(uri, MODES);
        } catch (SecurityException e) {
            // Already gone
            Log.i(TAG, "Access to %s was already gone", uri);
        }
        owned.remove(uri.toString());
        writeOwned(context, owned);
        sTakenThisProcess.remove(uri.toString());
    }

    /** The backup folder, or a folder a backup tag policy uses or used, so its backups can still be found. */
    private static boolean isReferenced(@NonNull Context context, @NonNull String treeKey) {
        if (treeKey.equals(treeKey(Prefs.Storage.getVolumePath()))) {
            return true;
        }
        for (Uri destination : new BackupTagPolicyStore(context).getKnownDestinations()) {
            if (treeKey.equals(treeKey(destination))) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsBackups(@NonNull Uri destination) {
        Predicate<Uri> probe = sBackupProbeForTesting;
        return probe != null ? probe.test(destination) : BackupItems.holdsBackups(destination);
    }

    private static boolean isBackupRunning() {
        BooleanSupplier running = sBackupRunningForTesting;
        if (running != null) {
            return running.getAsBoolean();
        }
        return BatchOpsService.isServiceWorking() || AutoBackupWorker.isRunning();
    }

    /** The provider and the folder a tree URI opens, the same for the grant and the folder saved from it. */
    @Nullable
    private static String treeKey(@NonNull Uri uri) {
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            return null;
        }
        try {
            return uri.getAuthority() + "/" + DocumentsContract.getTreeDocumentId(uri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static UriPermission findGrant(@NonNull GrantStore store, @NonNull Uri uri) {
        for (UriPermission permission : store.persisted()) {
            if (permission.getUri().equals(uri)) {
                return permission;
            }
        }
        return null;
    }

    @NonNull
    private static Map<String, Integer> readOwned(@NonNull Context context) {
        Map<String, Integer> owned = new LinkedHashMap<>();
        String raw = prefs(context).getString(KEY_OWNED, null);
        if (raw == null) {
            return owned;
        }
        try {
            JSONObject object = new JSONObject(raw);
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                String key = it.next();
                int purpose = object.optInt(key, 0);
                if (purpose == PURPOSE_DESTINATION || purpose == PURPOSE_IMPORT) {
                    owned.put(key, purpose);
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "The record of backup folder grants couldn't be read", e);
        }
        return owned;
    }

    private static boolean writeOwned(@NonNull Context context, @NonNull Map<String, Integer> owned) {
        JSONObject object = new JSONObject();
        try {
            for (Map.Entry<String, Integer> entry : owned.entrySet()) {
                object.put(entry.getKey(), (int) entry.getValue());
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        SharedPreferences prefs = prefs(context);
        String before = prefs.getString(KEY_OWNED, null);
        if (prefs.edit().putString(KEY_OWNED, object.toString()).commit()) {
            return true;
        }
        // Android keeps a failed write in memory, so the old record is put back there
        Log.w(TAG, "The record of backup folder grants couldn't be saved");
        prefs.edit().putString(KEY_OWNED, before).apply();
        return false;
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static GrantStore store(@NonNull Context context) {
        GrantStore store = sStoreForTesting;
        if (store != null) {
            return store;
        }
        ContentResolver resolver = context.getContentResolver();
        return new GrantStore() {
            @Override
            public void take(@NonNull Uri uri, int modes) {
                resolver.takePersistableUriPermission(uri, modes);
            }

            @Override
            public void release(@NonNull Uri uri, int modes) {
                resolver.releasePersistableUriPermission(uri, modes);
            }

            @NonNull
            @Override
            public List<UriPermission> persisted() {
                return resolver.getPersistedUriPermissions();
            }
        };
    }

    @VisibleForTesting
    static void setStoreForTesting(@Nullable GrantStore store) {
        sStoreForTesting = store;
    }

    @VisibleForTesting
    static void setBackupProbeForTesting(@Nullable Predicate<Uri> probe) {
        sBackupProbeForTesting = probe;
    }

    @VisibleForTesting
    static void setBackupRunningForTesting(@Nullable BooleanSupplier running) {
        sBackupRunningForTesting = running;
    }

    /** What a new process sees: nothing taken by it yet. */
    @VisibleForTesting
    static void startNewProcessForTesting() {
        sTakenThisProcess.clear();
    }

    @VisibleForTesting
    @NonNull
    static Map<String, Integer> ownedForTesting(@NonNull Context context) {
        synchronized (LOCK) {
            return readOwned(context);
        }
    }
}
