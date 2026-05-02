// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.installer.ApkQueueItem;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerService;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.AppManager.profiles.ProfileQueueItem;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

public final class OperationJournalMetadata implements IJsonSerializer {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_TARGET_PREVIEW = 8;

    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final String KEY_MODE_LABEL = "mode_label";
    private static final String KEY_OPERATION_LABEL = "operation_label";
    private static final String KEY_TARGET_COUNT = "target_count";
    private static final String KEY_FAILED_COUNT = "failed_count";
    private static final String KEY_REQUIRES_RESTART = "requires_restart";
    private static final String KEY_REPLAYABLE = "replayable";
    private static final String KEY_REVERSIBLE = "reversible";
    private static final String KEY_RISK = "risk";
    private static final String KEY_ROLLBACK_HINT = "rollback_hint";
    private static final String KEY_TARGET_PREVIEW = "target_preview";
    private static final String KEY_FAILURE_MESSAGE = "failure_message";

    public static final int RISK_LOW = 0;
    public static final int RISK_MEDIUM = 1;
    public static final int RISK_HIGH = 2;

    @IntDef({RISK_LOW, RISK_MEDIUM, RISK_HIGH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Risk {
    }

    private static final String ROLLBACK_NONE = "none";
    private static final String ROLLBACK_RESTORE_BACKUP = "restore_backup";
    private static final String ROLLBACK_RUN_INVERSE = "run_inverse";
    private static final String ROLLBACK_REINSTALL = "reinstall";
    private static final String ROLLBACK_MANUAL_REAPPLY = "manual_reapply";
    private static final String ROLLBACK_DELETE_ARTIFACT = "delete_artifact";

    @NonNull
    private final JSONObject mJsonObject;

    private OperationJournalMetadata(@NonNull JSONObject jsonObject) {
        mJsonObject = jsonObject;
    }

    @Nullable
    public static OperationJournalMetadata fromJson(@Nullable String serializedExtra) {
        if (serializedExtra == null) {
            return null;
        }
        try {
            return new OperationJournalMetadata(new JSONObject(serializedExtra));
        } catch (JSONException ignore) {
            return null;
        }
    }

    @NonNull
    public static OperationJournalMetadata forBatchOperation(@NonNull Context context,
                                                             @NonNull BatchQueueItem item,
                                                             @NonNull BatchOpsManager.Result result) {
        int op = item.getOp();
        return builder(context)
                .setOperationLabel(BatchOpsService.getDesiredOpTitle(context, op))
                .setTargetCount(item.getPackages().size())
                .setFailedCount(result.getFailedPackages().size())
                .setRequiresRestart(result.requiresRestart())
                .setReplayable(true)
                .setReversible(isReversibleBatchOp(op))
                .setRisk(getRiskForBatchOp(op))
                .setRollbackHint(getRollbackHintForBatchOp(op))
                .setTargetPreview(item.getPackages())
                .build();
    }

    @NonNull
    public static OperationJournalMetadata forInstaller(@NonNull Context context,
                                                        @NonNull ApkQueueItem item,
                                                        @PackageInstallerCompat.Status int status,
                                                        @Nullable String blockingPackage,
                                                        @Nullable String statusMessage) {
        boolean success = status == PackageInstallerCompat.STATUS_SUCCESS;
        String label = item.getAppLabel();
        if (label == null) {
            label = item.getPackageName();
        }
        Builder builder = builder(context)
                .setOperationLabel(context.getString(R.string.package_installer))
                .setTargetCount(1)
                .setFailedCount(success ? 0 : 1)
                .setRequiresRestart(false)
                .setReplayable(true)
                .setReversible(false)
                .setRisk(item.isInstallExisting() ? RISK_LOW : RISK_MEDIUM)
                .setRollbackHint(ROLLBACK_REINSTALL)
                .setTargetPreview(label);
        if (!success) {
            String failureMessage = PackageInstallerService.getStringFromStatus(context, status, label, blockingPackage);
            if (statusMessage != null) {
                failureMessage += "\n" + statusMessage;
            }
            builder.setFailureMessage(failureMessage);
        }
        return builder.build();
    }

    @NonNull
    public static OperationJournalMetadata forProfile(@NonNull Context context,
                                                      @NonNull ProfileQueueItem item,
                                                      boolean success,
                                                      boolean requiresRestart,
                                                      @Nullable Throwable error) {
        Builder builder = builder(context)
                .setOperationLabel(context.getString(R.string.profiles))
                .setTargetCount(1)
                .setFailedCount(success ? 0 : 1)
                .setRequiresRestart(requiresRestart)
                .setReplayable(true)
                .setReversible(false)
                .setRisk(RISK_HIGH)
                .setRollbackHint(ROLLBACK_MANUAL_REAPPLY)
                .setTargetPreview(item.getProfileName());
        if (!success && error != null && error.getMessage() != null) {
            builder.setFailureMessage(error.getMessage());
        }
        return builder.build();
    }

    @NonNull
    private static Builder builder(@NonNull Context context) {
        return new Builder()
                .setModeLabel(Ops.getInferredMode(context).toString())
                .setSchemaVersion(SCHEMA_VERSION);
    }

    @Risk
    private static int getRiskForBatchOp(@BatchOpsManager.OpType int op) {
        switch (op) {
            case BatchOpsManager.OP_BACKUP:
            case BatchOpsManager.OP_BACKUP_APK:
            case BatchOpsManager.OP_CLEAR_CACHE:
            case BatchOpsManager.OP_EXPORT_RULES:
                return RISK_LOW;
            case BatchOpsManager.OP_CLEAR_DATA:
            case BatchOpsManager.OP_DELETE_BACKUP:
            case BatchOpsManager.OP_RESTORE_BACKUP:
            case BatchOpsManager.OP_UNINSTALL:
                return RISK_HIGH;
            default:
                return RISK_MEDIUM;
        }
    }

    private static boolean isReversibleBatchOp(@BatchOpsManager.OpType int op) {
        switch (op) {
            case BatchOpsManager.OP_BLOCK_TRACKERS:
            case BatchOpsManager.OP_FREEZE:
            case BatchOpsManager.OP_ADVANCED_FREEZE:
            case BatchOpsManager.OP_BLOCK_COMPONENTS:
            case BatchOpsManager.OP_UNBLOCK_TRACKERS:
            case BatchOpsManager.OP_UNFREEZE:
            case BatchOpsManager.OP_UNBLOCK_COMPONENTS:
                return true;
            default:
                return false;
        }
    }

    @NonNull
    private static String getRollbackHintForBatchOp(@BatchOpsManager.OpType int op) {
        switch (op) {
            case BatchOpsManager.OP_BACKUP:
            case BatchOpsManager.OP_BACKUP_APK:
            case BatchOpsManager.OP_EXPORT_RULES:
                return ROLLBACK_DELETE_ARTIFACT;
            case BatchOpsManager.OP_RESTORE_BACKUP:
                return ROLLBACK_RESTORE_BACKUP;
            case BatchOpsManager.OP_BLOCK_TRACKERS:
            case BatchOpsManager.OP_FREEZE:
            case BatchOpsManager.OP_ADVANCED_FREEZE:
            case BatchOpsManager.OP_BLOCK_COMPONENTS:
            case BatchOpsManager.OP_UNBLOCK_TRACKERS:
            case BatchOpsManager.OP_UNFREEZE:
            case BatchOpsManager.OP_UNBLOCK_COMPONENTS:
                return ROLLBACK_RUN_INVERSE;
            case BatchOpsManager.OP_SET_APP_OPS:
            case BatchOpsManager.OP_NET_POLICY:
            case BatchOpsManager.OP_DISABLE_BACKGROUND:
            case BatchOpsManager.OP_GRANT_PERMISSIONS:
            case BatchOpsManager.OP_REVOKE_PERMISSIONS:
                return ROLLBACK_MANUAL_REAPPLY;
            default:
                return ROLLBACK_NONE;
        }
    }

    @NonNull
    public String getModeLabel() {
        return mJsonObject.optString(KEY_MODE_LABEL, "");
    }

    @NonNull
    public String getSearchableText() {
        return mJsonObject.toString();
    }

    @NonNull
    public String getOperationLabel() {
        return mJsonObject.optString(KEY_OPERATION_LABEL, "");
    }

    public int getTargetCount() {
        return mJsonObject.optInt(KEY_TARGET_COUNT, 0);
    }

    public int getFailedCount() {
        return mJsonObject.optInt(KEY_FAILED_COUNT, 0);
    }

    public boolean requiresRestart() {
        return mJsonObject.optBoolean(KEY_REQUIRES_RESTART, false);
    }

    public boolean isReplayable() {
        return mJsonObject.optBoolean(KEY_REPLAYABLE, true);
    }

    public boolean isReversible() {
        return mJsonObject.optBoolean(KEY_REVERSIBLE, false);
    }

    @Risk
    public int getRisk() {
        int risk = mJsonObject.optInt(KEY_RISK, RISK_MEDIUM);
        if (risk == RISK_LOW || risk == RISK_HIGH) {
            return risk;
        }
        return RISK_MEDIUM;
    }

    @Nullable
    public String getFailureMessage() {
        return JSONUtils.optString(mJsonObject, KEY_FAILURE_MESSAGE);
    }

    @NonNull
    public List<String> getTargetPreview() {
        JSONArray targetPreview = mJsonObject.optJSONArray(KEY_TARGET_PREVIEW);
        List<String> targets = new ArrayList<>();
        if (targetPreview == null) {
            return targets;
        }
        for (int i = 0; i < targetPreview.length(); ++i) {
            String target = targetPreview.optString(i, null);
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    @NonNull
    public String getLocalizedRisk(@NonNull Context context) {
        switch (getRisk()) {
            case RISK_LOW:
                return context.getString(R.string.op_history_risk_low);
            case RISK_HIGH:
                return context.getString(R.string.op_history_risk_high);
            case RISK_MEDIUM:
            default:
                return context.getString(R.string.op_history_risk_medium);
        }
    }

    @NonNull
    public String getLocalizedRollbackHint(@NonNull Context context) {
        switch (mJsonObject.optString(KEY_ROLLBACK_HINT, ROLLBACK_NONE)) {
            case ROLLBACK_DELETE_ARTIFACT:
                return context.getString(R.string.op_history_rollback_delete_artifact);
            case ROLLBACK_RESTORE_BACKUP:
                return context.getString(R.string.op_history_rollback_restore_backup);
            case ROLLBACK_RUN_INVERSE:
                return context.getString(R.string.op_history_rollback_run_inverse);
            case ROLLBACK_REINSTALL:
                return context.getString(R.string.op_history_rollback_reinstall);
            case ROLLBACK_MANUAL_REAPPLY:
                return context.getString(R.string.op_history_rollback_manual_reapply);
            case ROLLBACK_NONE:
            default:
                return context.getString(R.string.op_history_rollback_none);
        }
    }

    @NonNull
    public String getSummary(@NonNull Context context) {
        String targetCount = context.getResources().getQuantityString(
                R.plurals.op_history_target_count, getTargetCount(), getTargetCount());
        return context.getString(R.string.op_history_item_metadata,
                getLocalizedRisk(context), targetCount, getModeLabel());
    }

    @NonNull
    @Override
    public JSONObject serializeToJson() throws JSONException {
        return new JSONObject(mJsonObject.toString());
    }

    public static final class Builder {
        @NonNull
        private final JSONObject mJsonObject = new JSONObject();

        @NonNull
        Builder setSchemaVersion(int schemaVersion) {
            put(KEY_SCHEMA_VERSION, schemaVersion);
            return this;
        }

        @NonNull
        Builder setModeLabel(@NonNull String modeLabel) {
            put(KEY_MODE_LABEL, modeLabel);
            return this;
        }

        @NonNull
        Builder setOperationLabel(@NonNull String operationLabel) {
            put(KEY_OPERATION_LABEL, operationLabel);
            return this;
        }

        @NonNull
        Builder setTargetCount(int targetCount) {
            put(KEY_TARGET_COUNT, targetCount);
            return this;
        }

        @NonNull
        Builder setFailedCount(int failedCount) {
            put(KEY_FAILED_COUNT, failedCount);
            return this;
        }

        @NonNull
        Builder setRequiresRestart(boolean requiresRestart) {
            put(KEY_REQUIRES_RESTART, requiresRestart);
            return this;
        }

        @NonNull
        Builder setReplayable(boolean replayable) {
            put(KEY_REPLAYABLE, replayable);
            return this;
        }

        @NonNull
        Builder setReversible(boolean reversible) {
            put(KEY_REVERSIBLE, reversible);
            return this;
        }

        @NonNull
        Builder setRisk(@Risk int risk) {
            put(KEY_RISK, risk);
            return this;
        }

        @NonNull
        Builder setRollbackHint(@NonNull String rollbackHint) {
            put(KEY_ROLLBACK_HINT, rollbackHint);
            return this;
        }

        @NonNull
        Builder setTargetPreview(@Nullable List<String> targets) {
            JSONArray targetPreview = new JSONArray();
            if (targets != null) {
                int count = Math.min(targets.size(), MAX_TARGET_PREVIEW);
                for (int i = 0; i < count; ++i) {
                    targetPreview.put(targets.get(i));
                }
            }
            put(KEY_TARGET_PREVIEW, targetPreview);
            return this;
        }

        @NonNull
        Builder setTargetPreview(@Nullable String target) {
            JSONArray targetPreview = new JSONArray();
            if (target != null) {
                targetPreview.put(target);
            }
            put(KEY_TARGET_PREVIEW, targetPreview);
            return this;
        }

        @NonNull
        Builder setFailureMessage(@NonNull String failureMessage) {
            put(KEY_FAILURE_MESSAGE, failureMessage);
            return this;
        }

        @NonNull
        OperationJournalMetadata build() {
            return new OperationJournalMetadata(mJsonObject);
        }

        private void put(@NonNull String key, @Nullable Object value) {
            try {
                mJsonObject.put(key, value);
            } catch (JSONException ignore) {
            }
        }
    }
}
