// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.ApkQueueItem;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierService;
import io.github.muntashirakon.AppManager.profiles.ProfileQueueItem;

public final class OpHistoryManager {
    public static final String TAG = OpHistoryManager.class.getSimpleName();

    public static final String HISTORY_TYPE_BATCH_OPS = "batch_ops";
    public static final String HISTORY_TYPE_INSTALLER = "installer";
    public static final String HISTORY_TYPE_PROFILE = "profile";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({HISTORY_TYPE_BATCH_OPS, HISTORY_TYPE_INSTALLER, HISTORY_TYPE_PROFILE})
    public @interface HistoryType {
    }

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "failure";
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({STATUS_SUCCESS, STATUS_FAILURE})
    public @interface Status {
    }

    private static final MutableLiveData<OpHistory> sHistoryAddedLiveData = new MutableLiveData<>();

    public static LiveData<OpHistory> getHistoryAddedLiveData() {
        return sHistoryAddedLiveData;
    }

    @NonNull
    public static Intent getHistoryActivityIntent(@NonNull Context context) {
        return new Intent(context, OpHistoryActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @WorkerThread
    public static long addHistoryItem(@HistoryType String historyType,
                                      @NonNull IJsonSerializer item,
                                      boolean success) {
        return addHistoryItem(historyType, item, success, null);
    }

    @WorkerThread
    public static long addHistoryItem(@HistoryType String historyType,
                                      @NonNull IJsonSerializer item,
                                      boolean success,
                                      @Nullable OperationJournalMetadata metadata) {
        try {
            OpHistory opHistory = new OpHistory();
            opHistory.type = historyType;
            opHistory.execTime = System.currentTimeMillis();
            opHistory.serializedData = item.serializeToJson().toString();
            opHistory.status = success ? STATUS_SUCCESS : STATUS_FAILURE;
            opHistory.serializedExtra = metadata != null ? metadata.serializeToJson().toString() : null;
            long id = AppsDb.getInstance().opHistoryDao().insert(opHistory);
            opHistory.id = id;
            sHistoryAddedLiveData.postValue(opHistory);
            return id;
        } catch (JSONException e) {
            Log.e(TAG, "Could not serialize " + item.getClass(), e);
            return -1;
        }
    }

    @WorkerThread
    public static List<OpHistory> getAllHistoryItems() {
        return AppsDb.getInstance().opHistoryDao().getAll();
    }

    @WorkerThread
    public static void clearAllHistory() {
        AppsDb.getInstance().opHistoryDao().deleteAll();
    }

    @WorkerThread
    public static void deleteHistoryItem(long id) {
        AppsDb.getInstance().opHistoryDao().delete(id);
    }

    @WorkerThread
    public static int deleteHistoryItemsByStatus(@Status String status) {
        return AppsDb.getInstance().opHistoryDao().deleteByStatus(status);
    }

    @WorkerThread
    public static int pruneHistoryOlderThan(int days) {
        if (days <= 0) {
            return 0;
        }
        return AppsDb.getInstance().opHistoryDao().deleteOlderThan(System.currentTimeMillis() - days * DAY_MILLIS);
    }

    @WorkerThread
    public static int addDebugHistoryFixtures(@NonNull Context context) {
        if (!BuildConfig.DEBUG) {
            return 0;
        }
        long now = System.currentTimeMillis();
        try {
            addRawHistoryItem(HISTORY_TYPE_INSTALLER,
                    now - 15 * 60 * 1000,
                    STATUS_SUCCESS,
                    new JSONObject()
                            .put("package_name", "android")
                            .put("app_label", "Android System")
                            .put("install_existing", true),
                    createFixtureMetadata(context.getString(R.string.package_installer),
                            context.getString(R.string.adb), 1, 0, OperationJournalMetadata.RISK_MEDIUM,
                            false, false, "android", null));
            addRawHistoryItem(HISTORY_TYPE_INSTALLER,
                    now - 2 * 60 * 60 * 1000,
                    STATUS_FAILURE,
                    new JSONObject()
                            .put("package_name", "com.example.missing")
                            .put("app_label", "Missing APK")
                            .put("install_existing", false),
                    createFixtureMetadata(context.getString(R.string.package_installer),
                            context.getString(R.string.no_root), 1, 1, OperationJournalMetadata.RISK_MEDIUM,
                            false, false, "com.example.missing",
                            "INSTALL_FAILED_VERSION_DOWNGRADE"));
            addRawHistoryItem(HISTORY_TYPE_PROFILE,
                    now - 3 * DAY_MILLIS,
                    STATUS_SUCCESS,
                    new JSONObject()
                            .put("profile_id", "debug-history-sample")
                            .put("profile_type", 0)
                            .put("profile_name", "Debug cleanup profile")
                            .put("state", (String) null),
                    createFixtureMetadata(context.getString(R.string.profiles),
                            context.getString(R.string.root), 1, 0, OperationJournalMetadata.RISK_HIGH,
                            true, false, "Debug cleanup profile", null));
            return 3;
        } catch (JSONException e) {
            Log.e(TAG, "Could not create debug operation history fixtures.", e);
            return 0;
        }
    }

    @WorkerThread
    private static void addRawHistoryItem(@HistoryType String historyType,
                                          long execTime,
                                          @Status String status,
                                          @NonNull JSONObject serializedData,
                                          @NonNull JSONObject serializedExtra) {
        OpHistory opHistory = new OpHistory();
        opHistory.type = historyType;
        opHistory.execTime = execTime;
        opHistory.serializedData = serializedData.toString();
        opHistory.status = status;
        opHistory.serializedExtra = serializedExtra.toString();
        long id = AppsDb.getInstance().opHistoryDao().insert(opHistory);
        opHistory.id = id;
        sHistoryAddedLiveData.postValue(opHistory);
    }

    @NonNull
    private static JSONObject createFixtureMetadata(@NonNull String operationLabel,
                                                    @NonNull String modeLabel,
                                                    int targetCount,
                                                    int failedCount,
                                                    @OperationJournalMetadata.Risk int risk,
                                                    boolean requiresRestart,
                                                    boolean reversible,
                                                    @NonNull String targetPreview,
                                                    @Nullable String failureMessage)
            throws JSONException {
        JSONObject metadata = new JSONObject()
                .put("schema_version", 1)
                .put("mode_label", modeLabel)
                .put("operation_label", operationLabel)
                .put("target_count", targetCount)
                .put("failed_count", failedCount)
                .put("requires_restart", requiresRestart)
                .put("replayable", false)
                .put("reversible", reversible)
                .put("risk", risk)
                .put("rollback_hint", "manual_reapply")
                .put("target_preview", new JSONArray().put(targetPreview));
        if (failureMessage != null) {
            metadata.put("failure_message", failureMessage);
        }
        return metadata;
    }

    @NonNull
    public static Intent getExecutableIntent(@NonNull Context context, @NonNull OpHistoryItem item)
            throws JSONException {
        switch (item.getType()) {
            case HISTORY_TYPE_BATCH_OPS: {
                BatchQueueItem batchQueueItem = BatchQueueItem.DESERIALIZER.deserialize(item.jsonData);
                return BatchOpsService.getServiceIntent(context, batchQueueItem);
            }
            case HISTORY_TYPE_INSTALLER: {
                ApkQueueItem apkQueueItem = ApkQueueItem.DESERIALIZER.deserialize(item.jsonData);
                Intent intent = new Intent(context, PackageInstallerService.class);
                IntentCompat.putWrappedParcelableExtra(intent, PackageInstallerService.EXTRA_QUEUE_ITEM, apkQueueItem);
                return intent;
            }
            case HISTORY_TYPE_PROFILE: {
                ProfileQueueItem profileQueueItem = ProfileQueueItem.DESERIALIZER.deserialize(item.jsonData);
                return ProfileApplierService.getIntent(context, profileQueueItem, true);
            }
        }
        throw new IllegalStateException("Invalid type: " + item.getType());
    }
}
