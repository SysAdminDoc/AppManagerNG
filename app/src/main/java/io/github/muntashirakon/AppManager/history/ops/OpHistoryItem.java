// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.JSONUtils;

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

    public boolean getStatus() {
        return opHistory.status.equals(OpHistoryManager.STATUS_SUCCESS);
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
    public String getDetailMessage(@NonNull Context context) {
        StringBuilder detail = new StringBuilder();
        appendLine(context, detail, R.string.type, getLocalizedType(context));
        appendLine(context, detail, R.string.op_history_detail_status,
                context.getString(getStatus()
                        ? R.string.op_history_status_success
                        : R.string.op_history_status_failure));
        appendLine(context, detail, R.string.op_history_detail_time,
                DateUtils.formatLongDateTime(context, getTimestamp()));
        if (metadata == null) {
            appendLine(context, detail, R.string.op_history_detail_metadata,
                    context.getString(R.string.op_history_legacy_metadata_detail));
            return detail.toString();
        }
        appendLine(context, detail, R.string.op_history_detail_operation,
                nonEmptyOrUnknown(context, metadata.getOperationLabel()));
        appendLine(context, detail, R.string.op_history_detail_mode,
                nonEmptyOrUnknown(context, metadata.getModeLabel()));
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
        appendLine(context, detail, R.string.op_history_detail_risk, getLocalizedRisk(context));
        appendLine(context, detail, R.string.op_history_detail_replayable,
                context.getString(metadata.isReplayable() ? R.string.yes : R.string.no));
        appendLine(context, detail, R.string.op_history_detail_reversible,
                context.getString(metadata.isReversible() ? R.string.yes : R.string.no));
        appendLine(context, detail, R.string.op_history_detail_restart,
                context.getString(requiresRestart() ? R.string.yes : R.string.no));
        appendLine(context, detail, R.string.op_history_detail_rollback, getLocalizedRollbackHint(context));
        List<String> targetPreview = metadata.getTargetPreview();
        if (!targetPreview.isEmpty()) {
            appendLine(context, detail, R.string.op_history_detail_target_preview,
                    TextUtils.join(", ", targetPreview));
        }
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
