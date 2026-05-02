// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;

public final class OperationPreflight {
    @NonNull
    private final String mCurrentModeLabel;
    @NonNull
    private final String mSavedModeLabel;
    private final boolean mReplayable;
    private final boolean mReversible;
    private final boolean mRequiresRestart;
    private final boolean mModeChanged;
    @OperationJournalMetadata.Risk
    private final int mRisk;

    private OperationPreflight(@NonNull String currentModeLabel,
                               @NonNull String savedModeLabel,
                               boolean replayable,
                               boolean reversible,
                               boolean requiresRestart,
                               @OperationJournalMetadata.Risk int risk) {
        mCurrentModeLabel = currentModeLabel;
        mSavedModeLabel = savedModeLabel;
        mReplayable = replayable;
        mReversible = reversible;
        mRequiresRestart = requiresRestart;
        mRisk = risk;
        mModeChanged = !TextUtils.isEmpty(savedModeLabel)
                && !currentModeLabel.equalsIgnoreCase(savedModeLabel);
    }

    @NonNull
    public static OperationPreflight fromHistory(@NonNull Context context, @NonNull OpHistoryItem history) {
        return new OperationPreflight(Ops.getInferredMode(context).toString(),
                history.getModeLabel(),
                history.isReplayable(),
                history.isReversible(),
                history.requiresRestart(),
                history.getRisk());
    }

    @NonNull
    public String getConfirmationMessage(@NonNull Context context, @NonNull OpHistoryItem history) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.op_preflight_rerun_intro));
        appendLine(context, builder, R.string.op_preflight_current_mode, mCurrentModeLabel);
        appendLine(context, builder, R.string.op_preflight_saved_mode, nonEmptyOrUnknown(context, mSavedModeLabel));
        appendLine(context, builder, R.string.op_history_detail_risk, getLocalizedRisk(context));
        appendLine(context, builder, R.string.op_history_detail_reversible,
                context.getString(mReversible ? R.string.yes : R.string.no));
        appendLine(context, builder, R.string.op_history_detail_restart,
                context.getString(mRequiresRestart ? R.string.yes : R.string.no));
        appendLine(context, builder, R.string.op_history_detail_rollback, history.getLocalizedRollbackHint(context));
        if (mModeChanged) {
            builder.append("\n\n").append(context.getString(R.string.op_preflight_mode_changed_warning));
        }
        if (!mReplayable) {
            builder.append("\n\n").append(context.getString(R.string.op_preflight_not_replayable_warning));
        }
        builder.append("\n\n").append(history.getDetailMessage(context));
        return builder.toString();
    }

    @NonNull
    private String getLocalizedRisk(@NonNull Context context) {
        switch (mRisk) {
            case OperationJournalMetadata.RISK_LOW:
                return context.getString(R.string.op_history_risk_low);
            case OperationJournalMetadata.RISK_HIGH:
                return context.getString(R.string.op_history_risk_high);
            case OperationJournalMetadata.RISK_MEDIUM:
            default:
                return context.getString(R.string.op_history_risk_medium);
        }
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
}
