// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

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
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public final class OpHistoryManager {
    public static final String TAG = OpHistoryManager.class.getSimpleName();

    public static final String HISTORY_TYPE_BATCH_OPS = "batch_ops";
    public static final String HISTORY_TYPE_INSTALLER = "installer";
    public static final String HISTORY_TYPE_PROFILE = "profile";
    public static final String HISTORY_TYPE_CLEANUP = "cleanup";
    public static final String HISTORY_TYPE_SINGLE_APP_ACTION = "single_app_action";
    public static final String HISTORY_TYPE_UNKNOWN = "unknown";

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({HISTORY_TYPE_BATCH_OPS, HISTORY_TYPE_INSTALLER, HISTORY_TYPE_PROFILE, HISTORY_TYPE_CLEANUP,
            HISTORY_TYPE_SINGLE_APP_ACTION})
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

    @NonNull
    public static String normalizeHistoryType(@Nullable String type) {
        String normalizedType = normalizeToken(type);
        if (HISTORY_TYPE_BATCH_OPS.equals(normalizedType)
                || HISTORY_TYPE_INSTALLER.equals(normalizedType)
                || HISTORY_TYPE_PROFILE.equals(normalizedType)
                || HISTORY_TYPE_CLEANUP.equals(normalizedType)
                || HISTORY_TYPE_SINGLE_APP_ACTION.equals(normalizedType)) {
            return normalizedType;
        }
        return HISTORY_TYPE_UNKNOWN;
    }

    @Status
    @NonNull
    public static String normalizeStatus(@Nullable String status) {
        return STATUS_SUCCESS.equals(normalizeToken(status))
                ? STATUS_SUCCESS
                : STATUS_FAILURE;
    }

    @Nullable
    public static String normalizePackageName(@Nullable String packageName) {
        if (packageName == null) {
            return null;
        }
        String normalizedPackageName = packageName.trim();
        return PackageUtils.validateName(normalizedPackageName) ? normalizedPackageName : null;
    }

    @Nullable
    public static Integer normalizeUserId(@Nullable Object userId) {
        if (!(userId instanceof Number)) {
            return null;
        }
        Number number = (Number) userId;
        long longValue = number.longValue();
        if (longValue < 0 || longValue > Integer.MAX_VALUE || number.doubleValue() != longValue) {
            return null;
        }
        return (int) longValue;
    }

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
        if (STATUS_SUCCESS.equals(status)) {
            return AppsDb.getInstance().opHistoryDao().deleteByStatus(STATUS_SUCCESS);
        }
        return AppsDb.getInstance().opHistoryDao().deleteByStatusOtherThan(STATUS_SUCCESS);
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

    public static boolean canReplayHistoryItem(@NonNull OpHistoryItem item) {
        try {
            switch (item.getType()) {
                case HISTORY_TYPE_BATCH_OPS:
                    getBatchQueueItem(item);
                    return true;
                case HISTORY_TYPE_INSTALLER:
                    getApkQueueItem(item);
                    return true;
                case HISTORY_TYPE_PROFILE:
                    getProfileQueueItem(item);
                    return true;
            }
        } catch (JSONException | RuntimeException e) {
            return false;
        }
        return false;
    }

    @NonNull
    public static Intent getExecutableIntent(@NonNull Context context, @NonNull OpHistoryItem item)
            throws JSONException {
        switch (item.getType()) {
            case HISTORY_TYPE_BATCH_OPS: {
                return BatchOpsService.getServiceIntent(context, getBatchQueueItem(item));
            }
            case HISTORY_TYPE_INSTALLER: {
                Intent intent = new Intent(context, PackageInstallerService.class);
                IntentCompat.putWrappedParcelableExtra(intent, PackageInstallerService.EXTRA_QUEUE_ITEM,
                        getApkQueueItem(item));
                return intent;
            }
            case HISTORY_TYPE_PROFILE: {
                return ProfileApplierService.getIntent(context, getProfileQueueItem(item), true);
            }
        }
        throw new IllegalStateException("Invalid type: " + item.getType());
    }

    @NonNull
    private static BatchQueueItem getBatchQueueItem(@NonNull OpHistoryItem item) throws JSONException {
        BatchQueueItem batchQueueItem = BatchQueueItem.DESERIALIZER.deserialize(item.jsonData);
        if (batchQueueItem.getPackages().isEmpty()) {
            throw new JSONException("Missing batch queue targets.");
        }
        return batchQueueItem;
    }

    @NonNull
    private static ApkQueueItem getApkQueueItem(@NonNull OpHistoryItem item) throws JSONException {
        ApkQueueItem apkQueueItem = ApkQueueItem.DESERIALIZER.deserialize(item.jsonData);
        if (apkQueueItem.isInstallExisting()) {
            String packageName = normalizePackageName(apkQueueItem.getPackageName());
            if (packageName == null) {
                throw new JSONException("Missing install-existing package name.");
            }
            apkQueueItem.setPackageName(packageName);
        } else if (apkQueueItem.getApkSource() == null) {
            throw new JSONException("Missing APK source.");
        }
        return apkQueueItem;
    }

    @NonNull
    private static ProfileQueueItem getProfileQueueItem(@NonNull OpHistoryItem item) throws JSONException {
        ProfileQueueItem profileQueueItem = ProfileQueueItem.DESERIALIZER.deserialize(item.jsonData);
        if (TextUtils.isEmpty(profileQueueItem.getProfileId()) || TextUtils.isEmpty(profileQueueItem.getProfileName())) {
            throw new JSONException("Missing profile identity.");
        }
        return profileQueueItem;
    }

    @Nullable
    private static String normalizeToken(@Nullable String token) {
        if (token == null) {
            return null;
        }
        String normalizedToken = token.trim();
        return normalizedToken.isEmpty() ? null : normalizedToken;
    }
}
