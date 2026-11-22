// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import android.app.Application;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;

public class SettingsEditorViewModel extends AndroidViewModel {
    private static final String TAG = SettingsEditorViewModel.class.getSimpleName();

    @Nullable
    private static volatile SettingsBackend sBackendForTesting;
    @Nullable
    private static volatile File sAuditFileForTesting;

    @VisibleForTesting
    static void setForTesting(@Nullable SettingsBackend backend, @Nullable File auditFile) {
        sBackendForTesting = backend;
        sAuditFileForTesting = auditFile;
    }

    /** One table as read, with what can be done to it. */
    public static final class TableState {
        @NonNull
        public final SettingsTable table;
        @NonNull
        public final List<SettingEntry> entries;
        /** Why the table couldn't be read, or {@code null}. */
        @Nullable
        public final String error;
        public final boolean privileged;
        public final boolean writable;

        TableState(@NonNull SettingsTable table, @NonNull List<SettingEntry> entries, @Nullable String error,
                   boolean privileged, boolean writable) {
            this.table = table;
            this.entries = entries;
            this.error = error;
            this.privileged = privileged;
            this.writable = writable;
        }
    }

    /** How a change or an export went, shown once. */
    public static final class Outcome {
        public enum Kind {SAVED, REVERTED, UNCHANGED, FAILED, EXPORTED, EXPORT_FAILED}

        @NonNull
        public final Kind kind;
        /** The setting it was about, or empty for an export. */
        @NonNull
        public final String key;
        @Nullable
        public final SettingsChange change;
        @Nullable
        public final String error;

        Outcome(@NonNull Kind kind, @NonNull String key, @Nullable SettingsChange change, @Nullable String error) {
            this.kind = kind;
            this.key = key;
            this.change = change;
            this.error = error;
        }
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<TableState> mTableState = new MutableLiveData<>();
    private final MutableLiveData<List<SettingsChange>> mChanges = new MutableLiveData<>();
    private final MutableLiveData<Outcome> mOutcome = new MutableLiveData<>();
    @NonNull
    private SettingsTable mTable = SettingsTable.SYSTEM;
    private boolean mShowingChanges;

    public SettingsEditorViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        mExecutor.shutdown();
        super.onCleared();
    }

    @NonNull
    public LiveData<TableState> getTableState() {
        return mTableState;
    }

    @NonNull
    public LiveData<List<SettingsChange>> getChanges() {
        return mChanges;
    }

    @NonNull
    public LiveData<Outcome> getOutcome() {
        return mOutcome;
    }

    public void consumeOutcome() {
        mOutcome.setValue(null);
    }

    @NonNull
    public SettingsTable getTable() {
        return mTable;
    }

    public boolean isShowingChanges() {
        return mShowingChanges;
    }

    public void showTable(@NonNull SettingsTable table) {
        mTable = table;
        mShowingChanges = false;
        mExecutor.execute(() -> loadTable(table));
    }

    public void showChanges() {
        mShowingChanges = true;
        mExecutor.execute(this::loadChanges);
    }

    /** Sets a key, or deletes it when {@code value} is {@code null}. */
    public void set(@NonNull SettingsTable table, @NonNull String key, @Nullable String value) {
        mExecutor.execute(() -> {
            try {
                SettingsChange change = editor().set(table, key, value);
                mOutcome.postValue(new Outcome(change.isNoOp() ? Outcome.Kind.UNCHANGED : Outcome.Kind.SAVED, key,
                        change, null));
            } catch (IOException | IllegalArgumentException e) {
                Log.w(TAG, "Could not change %s", e, key);
                mOutcome.postValue(new Outcome(Outcome.Kind.FAILED, key, null, String.valueOf(e.getMessage())));
            }
            refresh(table);
        });
    }

    /** Puts a key back the way it was before {@code change}. Also the snackbar's Undo. */
    public void revert(@NonNull SettingsChange change) {
        mExecutor.execute(() -> {
            try {
                SettingsChange revert = editor().revert(change);
                mOutcome.postValue(new Outcome(revert.isNoOp() ? Outcome.Kind.UNCHANGED : Outcome.Kind.REVERTED,
                        change.key, revert, null));
            } catch (IOException | IllegalArgumentException e) {
                Log.w(TAG, "Could not put %s back", e, change.key);
                mOutcome.postValue(new Outcome(Outcome.Kind.FAILED, change.key, null,
                        String.valueOf(e.getMessage())));
            }
            refresh(change.table);
        });
    }

    /** Writes every readable key of the three tables to a file the user picked. */
    public void exportSnapshot(@NonNull Uri uri) {
        mExecutor.execute(() -> {
            SettingsBackend backend = backend();
            Map<SettingsTable, List<SettingEntry>> tables = new EnumMap<>(SettingsTable.class);
            Map<SettingsTable, String> unreadable = new EnumMap<>(SettingsTable.class);
            for (SettingsTable table : SettingsTable.values()) {
                try {
                    tables.put(table, backend.list(table));
                } catch (IOException e) {
                    unreadable.put(table, String.valueOf(e.getMessage()));
                }
            }
            try (OutputStream out = getApplication().getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) {
                    throw new IOException("Could not open " + uri);
                }
                String json = SettingsSnapshot.toJson(tables, unreadable, System.currentTimeMillis(),
                        Build.VERSION.SDK_INT, UserHandleHidden.myUserId()).toString(2);
                out.write(json.getBytes(StandardCharsets.UTF_8));
                mOutcome.postValue(new Outcome(Outcome.Kind.EXPORTED, "", null, null));
            } catch (IOException | JSONException | SecurityException | IllegalArgumentException e) {
                Log.w(TAG, "Could not save the settings snapshot.", e);
                mOutcome.postValue(new Outcome(Outcome.Kind.EXPORT_FAILED, "", null,
                        String.valueOf(e.getMessage())));
            }
        });
    }

    @WorkerThread
    private void refresh(@NonNull SettingsTable table) {
        loadTable(table);
        loadChanges();
    }

    @WorkerThread
    private void loadTable(@NonNull SettingsTable table) {
        SettingsBackend backend = backend();
        boolean writable = backend.canWrite(table);
        try {
            mTableState.postValue(new TableState(table, backend.list(table), null, backend.isPrivileged(),
                    writable));
        } catch (IOException e) {
            Log.w(TAG, "Could not list %s", e, table.shellName);
            mTableState.postValue(new TableState(table, Collections.emptyList(), String.valueOf(e.getMessage()),
                    backend.isPrivileged(), writable));
        }
    }

    @WorkerThread
    private void loadChanges() {
        mChanges.postValue(auditLog().readNewestFirst());
    }

    @WorkerThread
    @NonNull
    private SettingsEditor editor() {
        return new SettingsEditor(backend(), auditLog(), System::currentTimeMillis);
    }

    @NonNull
    private SettingsAuditLog auditLog() {
        File file = sAuditFileForTesting;
        return file != null ? new SettingsAuditLog(file) : SettingsAuditLog.getInstance(getApplication());
    }

    /** Asked each time, since the mode of operation can change while the screen is open. */
    @WorkerThread
    @NonNull
    private SettingsBackend backend() {
        SettingsBackend backend = sBackendForTesting;
        if (backend != null) {
            return backend;
        }
        ResolverSettingsBackend resolver = new ResolverSettingsBackend(getApplication());
        if (Ops.isDirectRoot() || LocalServices.alive()) {
            return new ShellSettingsBackend(ShellSettingsBackend.privilegedShell(), UserHandleHidden.myUserId(),
                    resolver);
        }
        return resolver;
    }
}
