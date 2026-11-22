// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Makes a change through a backend and adds it to the audit trail with the value it replaced, so it can be put back
 * later, by the snackbar's Undo or from the Changes list.
 */
@WorkerThread
public final class SettingsEditor {
    private static final String TAG = SettingsEditor.class.getSimpleName();

    public interface Clock {
        long now();
    }

    @NonNull
    private final SettingsBackend mBackend;
    @NonNull
    private final SettingsAuditLog mAuditLog;
    @NonNull
    private final Clock mClock;

    public SettingsEditor(@NonNull SettingsBackend backend, @NonNull SettingsAuditLog auditLog, @NonNull Clock clock) {
        mBackend = backend;
        mAuditLog = auditLog;
        mClock = clock;
    }

    /**
     * Sets a key, or deletes it when {@code value} is {@code null}. Nothing is written or recorded when the key already
     * holds that value; the returned change then has the same old and new value.
     */
    @NonNull
    public SettingsChange set(@NonNull SettingsTable table, @NonNull String key, @Nullable String value)
            throws IOException {
        String current = mBackend.get(table, key);
        SettingsChange change = new SettingsChange(mClock.now(), table, key, current, value);
        apply(change);
        return change;
    }

    /** Puts a key back the way it was before {@code change}, from whatever it holds now. */
    @NonNull
    public SettingsChange revert(@NonNull SettingsChange change) throws IOException {
        String current = mBackend.get(change.table, change.key);
        SettingsChange revert = change.revert(current, mClock.now());
        apply(revert);
        return revert;
    }

    private void apply(@NonNull SettingsChange change) throws IOException {
        if (change.isNoOp()) {
            return;
        }
        if (change.newValue == null) {
            mBackend.delete(change.table, change.key);
        } else {
            mBackend.put(change.table, change.key, change.newValue);
        }
        try {
            mAuditLog.record(change);
        } catch (IOException e) {
            // The change itself went through, and the snackbar can still undo it.
            Log.w(TAG, "Could not add a settings change to the trail.", e);
        }
    }
}
