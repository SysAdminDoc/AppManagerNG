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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import io.github.muntashirakon.AppManager.backup.schedule.AutoBackupWorker;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.AppPref;
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
    private static final String KEY_UNFINISHED_IMPORTS = "unfinished_imports";
    private static final int MODES = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    /** How long an import that didn't finish keeps its folder's access, so it can be retried after a restart. */
    @VisibleForTesting
    static final long RETRY_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(7);

    /** The persisted grants as Android keeps them. */
    @VisibleForTesting
    interface GrantStore {
        void take(@NonNull Uri uri, int modes);

        void release(@NonNull Uri uri, int modes);

        @NonNull
        List<UriPermission> persisted();
    }

    private static final Object LOCK = new Object();
    /**
     * Grants taken by this process, which the flow that took them lets go, each with the take it came from, so a
     * release can tell the folder was picked again meanwhile. The startup sweep leaves them alone. Guarded by LOCK.
     */
    private static final Map<String, Long> sTakenThisProcess = new HashMap<>();
    private static long sTakes;
    /**
     * Imports handed to the batch service from each folder and not yet ended, by tree key, so one ending keeps the
     * access the next needs. Guarded by LOCK.
     */
    private static final Map<String, Integer> sQueuedImports = new HashMap<>();
    @Nullable
    private static GrantStore sStoreForTesting;
    @Nullable
    private static Predicate<Uri> sBackupProbeForTesting;
    @Nullable
    private static BooleanSupplier sBackupRunningForTesting;
    @Nullable
    private static LongSupplier sClockForTesting;

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
            sTakenThisProcess.put(key, ++sTakes);
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
            return useDestination(context, destination);
        }
    }

    /**
     * Makes a folder the backup folder.
     *
     * @return the backup folder it replaced, to hand to {@link #releaseIfUnused(Context, Uri)} off the main thread
     */
    @AnyThread
    @NonNull
    public static Uri useDestination(@NonNull Context context, @NonNull Uri destination) {
        synchronized (LOCK) {
            Uri previous = Prefs.Storage.getVolumePath();
            if (!previous.equals(destination)) {
                Prefs.Storage.setVolumePath(destination.toString());
            }
            // Access taken for an import now belongs to a backup folder, kept while it holds backups
            String treeKey = treeKey(destination);
            if (treeKey != null) {
                Map<String, Integer> owned = readOwned(context);
                boolean changed = false;
                for (Map.Entry<String, Integer> entry : owned.entrySet()) {
                    if (entry.getValue() == PURPOSE_IMPORT && treeKey.equals(treeKey(Uri.parse(entry.getKey())))) {
                        entry.setValue(PURPOSE_DESTINATION);
                        changed = true;
                    }
                }
                if (changed) {
                    writeOwned(context, owned);
                }
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
        long listedAt;
        synchronized (LOCK) {
            listedAt = sTakes;
            for (String key : readOwned(context).keySet()) {
                if (treeKey.equals(treeKey(Uri.parse(key)))) {
                    candidates.add(key);
                }
            }
        }
        releaseStale(context, candidates, listedAt);
    }

    /**
     * Notes an import from this folder was handed to the batch service, so the folder keeps its access until the
     * import ends, and an earlier import from it that ends first keeps the access this one needs. Called before the
     * service is started, since a folder change in between would otherwise let the access go. Each is matched by
     * {@link #releaseAfterImport}, {@link #importFailed} or {@link #importDropped(Uri)}.
     */
    @AnyThread
    public static void importQueued(@NonNull Uri importDirectory) {
        String treeKey = treeKey(importDirectory);
        if (treeKey == null) {
            return;
        }
        synchronized (LOCK) {
            Integer queued = sQueuedImports.get(treeKey);
            sQueuedImports.put(treeKey, queued == null ? 1 : queued + 1);
        }
    }

    /**
     * A queued import from this folder will never run, as one dropped when the batch service was stopped, or one the
     * service couldn't be started for. The access stays until the next start of the app checks it.
     */
    @AnyThread
    public static void importDropped(@NonNull Uri importDirectory) {
        String treeKey = treeKey(importDirectory);
        if (treeKey == null) {
            return;
        }
        synchronized (LOCK) {
            importEnded(treeKey);
        }
    }

    /**
     * An import from this folder began. It's recorded on disk until it ends cleanly, so one cut short when the app
     * was killed, or one that failed, keeps the folder's access for a retry after a restart, for
     * {@link #RETRY_WINDOW_MILLIS}.
     */
    @WorkerThread
    public static void importStarted(@NonNull Context context, @NonNull Uri importDirectory, int importType) {
        String treeKey = treeKey(importDirectory);
        if (treeKey == null) {
            return;
        }
        synchronized (LOCK) {
            setUnfinished(context, treeKey, importType, true);
        }
    }

    /**
     * An import from this folder failed. Its access is kept for a retry of the same import, for
     * {@link #RETRY_WINDOW_MILLIS} from now.
     */
    @WorkerThread
    public static void importFailed(@NonNull Context context, @NonNull Uri importDirectory, int importType) {
        String treeKey = treeKey(importDirectory);
        if (treeKey == null) {
            return;
        }
        synchronized (LOCK) {
            importEnded(treeKey);
            setUnfinished(context, treeKey, importType, true);
        }
    }

    /**
     * An import from this folder ended cleanly, which also settles an earlier failed one of the same type. Lets go of
     * the access taken for it, unless another import from the folder is queued, another that didn't finish may be
     * retried, or the folder is also a backup folder.
     */
    @WorkerThread
    public static void releaseAfterImport(@NonNull Context context, @NonNull Uri importDirectory, int importType) {
        String treeKey = treeKey(importDirectory);
        if (treeKey == null) {
            return;
        }
        synchronized (LOCK) {
            boolean ended = importEnded(treeKey);
            setUnfinished(context, treeKey, importType, false);
            if (!ended || importNeeds(context, treeKey)) {
                return;
            }
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
        long listedAt;
        synchronized (LOCK) {
            listedAt = sTakes;
            Map<String, Integer> owned = readOwned(context);
            if (!owned.isEmpty()) {
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
                    } else if (!sTakenThisProcess.containsKey(key)) {
                        candidates.add(key);
                    }
                }
                if (changed) {
                    writeOwned(context, owned);
                }
            }
            pruneUnfinished(context, owned);
        }
        releaseStale(context, candidates, listedAt);
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

    /**
     * @param listedAt The take count when the candidates were listed. A candidate picked again after that is kept.
     */
    private static void releaseStale(@NonNull Context context, @NonNull List<String> candidates, long listedAt) {
        if (candidates.isEmpty() || isBackupRunning()) {
            return;
        }
        // A new backup folder is only queued for the disk, and a restart before it lands goes back to the old one
        if (!AppPref.awaitWrites(AppPref.PrefKey.PREF_BACKUP_VOLUME_STR)) {
            Log.w(TAG, "The backup folder wasn't saved, so no folder access is let go");
            return;
        }
        for (String key : candidates) {
            Uri uri = Uri.parse(key);
            String treeKey = treeKey(uri);
            Integer purpose;
            synchronized (LOCK) {
                purpose = takenSince(key, listedAt) || importNeeds(context, treeKey) ? null
                        : readOwned(context).get(key);
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
                if (!Objects.equals(owned.get(key), purpose) || takenSince(key, listedAt)
                        || importNeeds(context, treeKey) || isReferenced(context, treeKey) || isBackupRunning()) {
                    continue;
                }
                release(context, owned, uri);
            }
        }
    }

    /** Whether the folder was picked again after the candidates were listed. Call with LOCK held. */
    private static boolean takenSince(@NonNull String key, long listedAt) {
        Long taken = sTakenThisProcess.get(key);
        return taken != null && taken > listedAt;
    }

    /**
     * Whether an import from the folder is queued, or began and didn't end cleanly within the retry window, as one
     * that failed or was cut short. A queued one counts from the moment it's handed to the batch service, before the
     * service runs, so a running backup doesn't cover it. Call with LOCK held.
     */
    private static boolean importNeeds(@NonNull Context context, @Nullable String treeKey) {
        if (treeKey == null) {
            return false;
        }
        if (sQueuedImports.containsKey(treeKey)) {
            return true;
        }
        JSONObject types = readUnfinished(context).optJSONObject(treeKey);
        if (types == null) {
            return false;
        }
        long now = now();
        for (Iterator<String> it = types.keys(); it.hasNext(); ) {
            if (withinRetryWindow(types.optLong(it.next(), 0), now)) {
                return true;
            }
        }
        return false;
    }

    /** A clock moved back counts too, as long as it moved less than the window. */
    private static boolean withinRetryWindow(long recordedAt, long now) {
        return recordedAt > 0 && Math.abs(now - recordedAt) < RETRY_WINDOW_MILLIS;
    }

    /**
     * Records an import of this type from the folder as not finished, as of now, or forgets it. Call with LOCK held.
     */
    private static void setUnfinished(@NonNull Context context, @NonNull String treeKey, int importType,
                                      boolean unfinished) {
        JSONObject all = readUnfinished(context);
        JSONObject types = all.optJSONObject(treeKey);
        String type = String.valueOf(importType);
        try {
            if (unfinished) {
                if (types == null) {
                    types = new JSONObject();
                    all.put(treeKey, types);
                }
                types.put(type, now());
            } else {
                if (types == null || !types.has(type)) {
                    return;
                }
                types.remove(type);
                if (types.length() == 0) {
                    all.remove(treeKey);
                }
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        writeUnfinished(context, all);
    }

    /**
     * Forgets imports past the retry window, and ones from folders no access is recorded for here, since there's
     * nothing of theirs to keep. Call with LOCK held.
     */
    private static void pruneUnfinished(@NonNull Context context, @NonNull Map<String, Integer> owned) {
        JSONObject all = readUnfinished(context);
        if (all.length() == 0) {
            return;
        }
        Set<String> ownedTrees = new HashSet<>();
        for (String key : owned.keySet()) {
            String treeKey = treeKey(Uri.parse(key));
            if (treeKey != null) {
                ownedTrees.add(treeKey);
            }
        }
        long now = now();
        boolean changed = false;
        for (Iterator<String> trees = all.keys(); trees.hasNext(); ) {
            String treeKey = trees.next();
            JSONObject types = all.optJSONObject(treeKey);
            if (types != null && ownedTrees.contains(treeKey)) {
                for (Iterator<String> it = types.keys(); it.hasNext(); ) {
                    if (!withinRetryWindow(types.optLong(it.next(), 0), now)) {
                        it.remove();
                        changed = true;
                    }
                }
            }
            if (types == null || types.length() == 0 || !ownedTrees.contains(treeKey)) {
                trees.remove();
                changed = true;
            }
        }
        if (changed) {
            writeUnfinished(context, all);
        }
    }

    @NonNull
    private static JSONObject readUnfinished(@NonNull Context context) {
        String raw = prefs(context).getString(KEY_UNFINISHED_IMPORTS, null);
        if (raw == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            Log.w(TAG, "The record of unfinished imports couldn't be read", e);
            return new JSONObject();
        }
    }

    private static void writeUnfinished(@NonNull Context context, @NonNull JSONObject all) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (all.length() == 0) {
            editor.remove(KEY_UNFINISHED_IMPORTS);
        } else {
            editor.putString(KEY_UNFINISHED_IMPORTS, all.toString());
        }
        if (!editor.commit()) {
            Log.w(TAG, "The record of unfinished imports couldn't be saved");
        }
    }

    private static long now() {
        LongSupplier clock = sClockForTesting;
        return clock != null ? clock.getAsLong() : System.currentTimeMillis();
    }

    /** @return whether no other import from the folder is queued */
    private static boolean importEnded(@NonNull String treeKey) {
        Integer queued = sQueuedImports.get(treeKey);
        if (queued == null || queued <= 1) {
            sQueuedImports.remove(treeKey);
            return true;
        }
        sQueuedImports.put(treeKey, queued - 1);
        return false;
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

    @VisibleForTesting
    static void setClockForTesting(@Nullable LongSupplier clock) {
        sClockForTesting = clock;
    }

    /** What a new process sees: nothing taken or queued by it yet. What's on disk stays. */
    @VisibleForTesting
    static void startNewProcessForTesting() {
        synchronized (LOCK) {
            sTakenThisProcess.clear();
            sQueuedImports.clear();
        }
    }

    @VisibleForTesting
    @NonNull
    static Map<String, Integer> ownedForTesting(@NonNull Context context) {
        synchronized (LOCK) {
            return readOwned(context);
        }
    }
}
