// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * The encryption each backup folder had when this app made it or last listed it, kept in private
 * storage. A backup's info file says which mode it uses, and so which checksums count, but that
 * file is never encrypted and anyone who can write the backup folder can change it. The backups
 * table can't stand in for this record, since every full scan rebuilds it from those same files.
 */
public final class BackupCryptoRecord {
    public static final String TAG = BackupCryptoRecord.class.getSimpleName();

    private static final String PREFS_NAME = "backup_crypto_modes";

    private BackupCryptoRecord() {
    }

    /**
     * The mode recorded for the backup in {@code relativeDir}, or {@code null} when this app never
     * made or listed it.
     */
    @Nullable
    public static String getRecordedMode(@NonNull String relativeDir) {
        return getPrefs().getString(relativeDir, null);
    }

    /**
     * Records the mode of a backup this app has just put in place, replacing whatever the folder
     * held before.
     */
    @WorkerThread
    static void record(@NonNull String relativeDir, @NonNull String mode) {
        if (!getPrefs().edit().putString(relativeDir, mode).commit()) {
            Log.w(TAG, "Could not save the encryption mode of %s", relativeDir);
        }
    }

    /**
     * Drops the record of a backup this app has deleted.
     */
    @WorkerThread
    static void forget(@NonNull String relativeDir) {
        if (!getPrefs().edit().remove(relativeDir).commit()) {
            Log.w(TAG, "Could not drop the encryption mode of %s", relativeDir);
        }
    }

    /**
     * Keeps every encrypted mode in {@code rows}, the backups table as it stands before it's
     * cleared and filled again from the info files. A folder already in the record keeps what it
     * has, since this app wrote or saw that first.
     */
    @WorkerThread
    public static void keepEncryptedModes(@NonNull List<Backup> rows) {
        SharedPreferences prefs = getPrefs();
        SharedPreferences.Editor editor = null;
        for (Backup row : rows) {
            if (!isEncrypted(row.crypto)) {
                continue;
            }
            String relativeDir = relativeDirOf(row);
            if (relativeDir == null || prefs.contains(relativeDir)) {
                continue;
            }
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.putString(relativeDir, row.crypto);
        }
        if (editor != null && !editor.commit()) {
            Log.w(TAG, "Could not save the encryption modes of the listed backups");
        }
    }

    /**
     * Stops a restore when the backup in {@code relativeDir} was recorded as encrypted and its info
     * file now names another mode, no encryption included.
     *
     * @param modeNow The mode the backup's info file names now
     */
    static void requireRecordedMode(@NonNull String relativeDir, @Nullable String modeNow)
            throws BackupException {
        String recordedMode = getRecordedMode(relativeDir);
        if (!isEncrypted(recordedMode) || recordedMode.equals(modeNow)) {
            return;
        }
        throw new BackupException("This backup was made with " + describe(recordedMode)
                + ", but its info file now says " + describe(modeNow)
                + ". Another app may have changed it, so nothing was restored.");
    }

    @NonNull
    static String describe(@Nullable String mode) {
        if (!isEncrypted(mode)) {
            return "no encryption";
        }
        return mode.toUpperCase(Locale.ROOT) + " encryption";
    }

    private static boolean isEncrypted(@Nullable String mode) {
        return !TextUtils.isEmpty(mode) && !CryptoUtils.MODE_NO_ENCRYPTION.equals(mode);
    }

    @Nullable
    private static String relativeDirOf(@NonNull Backup row) {
        if (!TextUtils.isEmpty(row.relativeDir)) {
            return row.relativeDir;
        }
        if (row.version >= 5) {
            // Rows from v5 on always name their folder, see Backup#getItem()
            return null;
        }
        return BackupUtils.getV4RelativeDir(row.userId, row.backupName, row.packageName);
    }

    @NonNull
    private static SharedPreferences getPrefs() {
        return ContextUtils.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
