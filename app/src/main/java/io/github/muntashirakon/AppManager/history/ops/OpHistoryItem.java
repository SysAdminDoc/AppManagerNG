// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;
import io.github.muntashirakon.io.Path;

public class OpHistoryItem {
    private final OpHistory opHistory;
    public final JSONObject jsonData;
    @Nullable
    private final OperationJournalMetadata metadata;

    public OpHistoryItem(@NonNull OpHistory opHistory) throws JSONException {
        this.opHistory = opHistory;
        jsonData = new JSONObject(opHistory.serializedData);
        metadata = OperationJournalMetadata.fromJson(opHistory.serializedExtra);
    }

    @OpHistoryManager.HistoryType
    public String getType() {
        return opHistory.type;
    }

    public long getId() {
        return opHistory.id;
    }

    @NonNull
    public String getLocalizedType(@NonNull Context context) {
        switch (opHistory.type) {
            case OpHistoryManager.HISTORY_TYPE_BATCH_OPS:
                try {
                    return context.getString(jsonData.getInt("title_res"));
                } catch (Resources.NotFoundException | JSONException e) {
                    return context.getString(R.string.batch_ops);
                }
            case OpHistoryManager.HISTORY_TYPE_INSTALLER:
                return context.getString(R.string.installer);
            case OpHistoryManager.HISTORY_TYPE_PROFILE:
                return context.getString(R.string.profiles);
        }
        throw new IllegalStateException("Invalid type: " + opHistory.type);
    }

    @NonNull
    public String getLabel(@NonNull Context context) {
        switch (opHistory.type) {
            case OpHistoryManager.HISTORY_TYPE_BATCH_OPS:
                try {
                    int op = jsonData.getInt("op");
                    return BatchOpsService.getDesiredOpTitle(context, op);
                } catch (JSONException e) {
                    return context.getString(R.string.unknown_op);
                }
            case OpHistoryManager.HISTORY_TYPE_INSTALLER: {
                String label = JSONUtils.optString(jsonData, "app_label");
                if (label != null) {
                    return label;
                }
                return context.getString(R.string.state_unknown);
            }
            case OpHistoryManager.HISTORY_TYPE_PROFILE: {
                String label = JSONUtils.optString(jsonData, "profile_name");
                if (label != null) {
                    return label;
                }
                return context.getString(R.string.state_unknown);
            }
        }
        throw new IllegalStateException("Invalid type: " + opHistory.type);
    }

    public long getTimestamp() {
        return opHistory.execTime;
    }

    @NonNull
    public String getStatusName() {
        return opHistory.status;
    }

    public boolean getStatus() {
        return opHistory.status.equals(OpHistoryManager.STATUS_SUCCESS);
    }

    @NonNull
    public String getLocalizedStatus(@NonNull Context context) {
        return context.getString(getStatus()
                ? R.string.op_history_status_success
                : R.string.op_history_status_failure);
    }

    public boolean isReplayable() {
        return metadata == null || metadata.isReplayable();
    }

    public boolean isReversible() {
        return metadata != null && metadata.isReversible();
    }

    @OperationJournalMetadata.Risk
    public int getRisk() {
        return metadata != null ? metadata.getRisk() : OperationJournalMetadata.RISK_MEDIUM;
    }

    @NonNull
    public String getLocalizedRisk(@NonNull Context context) {
        return metadata != null
                ? metadata.getLocalizedRisk(context)
                : context.getString(R.string.op_history_risk_medium);
    }

    @NonNull
    public String getModeLabel() {
        return metadata != null ? metadata.getModeLabel() : "";
    }

    @NonNull
    public String getOperationLabel() {
        return metadata != null ? metadata.getOperationLabel() : "";
    }

    public int getTargetCount() {
        return metadata != null ? metadata.getTargetCount() : 0;
    }

    public int getFailedCount() {
        return metadata != null ? metadata.getFailedCount() : 0;
    }

    public boolean requiresRestart() {
        return metadata != null && metadata.requiresRestart();
    }

    @NonNull
    public String getLocalizedRollbackHint(@NonNull Context context) {
        return metadata != null
                ? metadata.getLocalizedRollbackHint(context)
                : context.getString(R.string.op_history_rollback_none);
    }

    public boolean matchesQuery(@NonNull Context context, @NonNull String query) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return containsQuery(getLocalizedType(context), normalizedQuery)
                || containsQuery(getLabel(context), normalizedQuery)
                || containsQuery(getMetadataSummary(context), normalizedQuery)
                || containsQuery(jsonData.toString(), normalizedQuery)
                || metadata != null && containsQuery(metadata.getSearchableText(), normalizedQuery);
    }

    @NonNull
    public String getMetadataSummary(@NonNull Context context) {
        if (metadata == null) {
            return context.getString(R.string.op_history_legacy_metadata_summary);
        }
        return metadata.getSummary(context);
    }

    @NonNull
    public List<String> getTargetPreview() {
        return metadata != null ? metadata.getTargetPreview() : java.util.Collections.emptyList();
    }

    @Nullable
    public String getFailureMessage() {
        return metadata != null ? metadata.getFailureMessage() : null;
    }

    @Nullable
    public Intent getPrimaryTargetIntent(@NonNull Context context) {
        if (OpHistoryManager.HISTORY_TYPE_PROFILE.equals(getType())) {
            String profileId = JSONUtils.optString(jsonData, "profile_id");
            if (profileId != null) {
                Path profilePath = ProfileManager.findProfilePathById(profileId);
                if (profilePath != null && profilePath.exists()) {
                    return ProfileManager.getProfileIntent(context, jsonData.optInt("profile_type", 0), profileId);
                }
            }
            return null;
        }
        String packageName = getPrimaryPackageName();
        if (packageName == null) {
            return null;
        }
        return AppDetailsActivity.getIntent(context, packageName, getPrimaryUserId());
    }

    @NonNull
    public JSONObject getExportJson(@NonNull Context context) throws JSONException {
        JSONObject entry = new JSONObject();
        entry.put("id", getId());
        entry.put("type", getType());
        entry.put("type_label", getLocalizedType(context));
        entry.put("label", getLabel(context));
        entry.put("status", getStatusName());
        entry.put("status_label", getLocalizedStatus(context));
        entry.put("timestamp", getTimestamp());
        entry.put("time", DateUtils.formatLongDateTime(context, getTimestamp()));
        entry.put("operation", getOperationLabel());
        entry.put("mode", getModeLabel());
        entry.put("risk", getRisk());
        entry.put("risk_label", getLocalizedRisk(context));
        entry.put("target_count", getTargetCount());
        entry.put("failed_count", getFailedCount());
        entry.put("replayable", isReplayable());
        entry.put("reversible", isReversible());
        entry.put("requires_restart", requiresRestart());
        entry.put("rollback_guidance", getLocalizedRollbackHint(context));
        JSONArray targetPreview = new JSONArray();
        for (String target : getTargetPreview()) {
            targetPreview.put(target);
        }
        entry.put("target_preview", targetPreview);
        String failureMessage = getFailureMessage();
        if (failureMessage != null) {
            entry.put("failure_message", failureMessage);
        }
        return entry;
    }

    @NonNull
    public String getDetailMessage(@NonNull Context context) {
        StringBuilder detail = new StringBuilder();
        appendSection(context, detail, R.string.op_history_detail_section_summary);
        appendLine(context, detail, R.string.type, getLocalizedType(context));
        appendLine(context, detail, R.string.op_history_detail_status, getLocalizedStatus(context));
        if (metadata == null) {
            appendLine(context, detail, R.string.op_history_detail_metadata,
                    context.getString(R.string.op_history_legacy_metadata_detail));
            return detail.toString();
        }
        appendLine(context, detail, R.string.op_history_detail_operation,
                nonEmptyOrUnknown(context, metadata.getOperationLabel()));
        appendSection(context, detail, R.string.op_history_detail_section_execution);
        appendLine(context, detail, R.string.op_history_detail_time,
                DateUtils.formatLongDateTime(context, getTimestamp()));
        appendLine(context, detail, R.string.op_history_detail_mode,
                nonEmptyOrUnknown(context, metadata.getModeLabel()));
        appendSection(context, detail, R.string.op_history_detail_section_targets);
        appendLine(context, detail, R.string.op_history_detail_targets,
                context.getResources().getQuantityString(
                        R.plurals.op_history_target_count,
                        metadata.getTargetCount(),
                        metadata.getTargetCount()));
        appendLine(context, detail, R.string.op_history_detail_failed,
                context.getResources().getQuantityString(
                        R.plurals.op_history_failed_count,
                        metadata.getFailedCount(),
                        metadata.getFailedCount()));
        List<String> targetPreview = metadata.getTargetPreview();
        if (!targetPreview.isEmpty()) {
            appendLine(context, detail, R.string.op_history_detail_target_preview,
                    TextUtils.join(", ", targetPreview));
        }
        appendSection(context, detail, R.string.op_history_detail_section_safety);
        appendLine(context, detail, R.string.op_history_detail_risk, getLocalizedRisk(context));
        appendLine(context, detail, R.string.op_history_detail_replayable,
                context.getString(metadata.isReplayable() ? R.string.yes : R.string.no));
        appendLine(context, detail, R.string.op_history_detail_reversible,
                context.getString(metadata.isReversible() ? R.string.yes : R.string.no));
        appendLine(context, detail, R.string.op_history_detail_restart,
                context.getString(requiresRestart() ? R.string.yes : R.string.no));
        appendSection(context, detail, R.string.op_history_detail_section_recovery);
        appendLine(context, detail, R.string.op_history_detail_rollback, getLocalizedRollbackHint(context));
        String failureMessage = metadata.getFailureMessage();
        if (failureMessage != null) {
            appendLine(context, detail, R.string.op_history_detail_failure_message, failureMessage);
        }
        return detail.toString();
    }

    @NonNull
    public String getExecutionConfirmationMessage(@NonNull Context context) {
        return OperationPreflight.fromHistory(context, this)
                .getConfirmationMessage(context, this);
    }

    @Nullable
    private String getPrimaryPackageName() {
        switch (opHistory.type) {
            case OpHistoryManager.HISTORY_TYPE_BATCH_OPS: {
                JSONArray packages = jsonData.optJSONArray("packages");
                if (packages != null && packages.length() == 1) {
                    return packages.optString(0, null);
                }
                return null;
            }
            case OpHistoryManager.HISTORY_TYPE_INSTALLER:
                return JSONUtils.optString(jsonData, "package_name");
            default:
                return null;
        }
    }

    private int getPrimaryUserId() {
        if (OpHistoryManager.HISTORY_TYPE_BATCH_OPS.equals(opHistory.type)) {
            JSONArray users = jsonData.optJSONArray("users");
            if (users != null && users.length() > 0) {
                return users.optInt(0, UserHandleHidden.myUserId());
            }
        }
        return UserHandleHidden.myUserId();
    }

    private static void appendSection(@NonNull Context context,
                                      @NonNull StringBuilder builder,
                                      int titleRes) {
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(context.getString(titleRes));
    }

    private static void appendLine(@NonNull Context context,
                                   @NonNull StringBuilder builder,
                                   int labelRes,
                                   @NonNull CharSequence value) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(context.getString(R.string.op_history_detail_line,
                context.getString(labelRes), value));
    }

    @NonNull
    private static String nonEmptyOrUnknown(@NonNull Context context, @Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return context.getString(R.string.state_unknown);
        }
        return value;
    }

    private static boolean containsQuery(@Nullable CharSequence value, @NonNull String normalizedQuery) {
        return value != null && value.toString().toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }
}
