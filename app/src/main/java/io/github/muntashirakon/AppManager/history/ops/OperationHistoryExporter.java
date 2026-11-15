// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.ExportTextUtils;
import io.github.muntashirakon.AppManager.utils.DateUtils;

public final class OperationHistoryExporter {
    private OperationHistoryExporter() {
    }

    @NonNull
    public static String toJson(@NonNull Context context, @NonNull List<OpHistoryItem> histories)
            throws JSONException {
        return toJson(context, histories, null);
    }

    /** {@link #toJson(Context, List)} with mutation images attached, each saying whether it verified. */
    @NonNull
    public static String toJson(@NonNull Context context, @NonNull List<OpHistoryItem> histories,
                                @Nullable List<MutationJournal.Entry> images) throws JSONException {
        JSONObject export = new JSONObject();
        export.put("schema_version", 1);
        export.put("generated_at", System.currentTimeMillis());
        export.put("generated_at_label", DateUtils.formatLongDateTime(context, System.currentTimeMillis()));
        export.put("entry_count", histories.size());
        JSONArray entries = new JSONArray();
        for (OpHistoryItem history : histories) {
            entries.put(history.getExportJson(context));
        }
        export.put("entries", entries);
        if (images != null) {
            export.put("mutation_image_count", images.size());
            export.put("mutation_images", MutationJournal.toJson(images));
        }
        return export.toString(2);
    }

    /**
     * The images that go with the exported rows: all of them when the whole history is exported, otherwise only
     * those written by the executions of those rows.
     */
    @NonNull
    public static List<MutationJournal.Entry> imagesFor(@NonNull List<OpHistoryItem> histories,
                                                        @NonNull List<MutationJournal.Entry> images,
                                                        boolean wholeHistory) {
        if (wholeHistory) {
            return images;
        }
        Set<String> executionIds = new HashSet<>();
        for (OpHistoryItem history : histories) {
            String executionId = history.getExecutionId();
            if (executionId != null) {
                executionIds.add(executionId);
            }
        }
        List<MutationJournal.Entry> result = new ArrayList<>();
        for (MutationJournal.Entry entry : images) {
            if (executionIds.contains(entry.image.executionId)) {
                result.add(entry);
            }
        }
        return result;
    }

    @NonNull
    public static String toCsv(@NonNull Context context, @NonNull List<OpHistoryItem> histories) {
        StringBuilder csv = new StringBuilder();
        appendCsvLine(csv, "id", "time", "type", "label", "status", "operation", "mode", "risk",
                "targets", "failures", "exit_code", "replayable", "reversible", "restart_required",
                "target_preview", "bootstrap_signature");
        for (OpHistoryItem history : histories) {
            appendCsvLine(csv,
                    Long.toString(history.getId()),
                    DateUtils.formatLongDateTime(context, history.getTimestamp()),
                    history.getLocalizedType(context),
                    history.getLabel(context),
                    history.getLocalizedStatus(context),
                    history.getOperationLabel(),
                    history.getModeLabel(),
                    history.getLocalizedRisk(context),
                    Integer.toString(history.getTargetCount()),
                    Integer.toString(history.getFailedCount()),
                    emptyIfNull(history.getExitCode()),
                    context.getString(history.isReplayable() ? R.string.yes : R.string.no),
                    context.getString(history.isReversible() ? R.string.yes : R.string.no),
                    context.getString(history.requiresRestart() ? R.string.yes : R.string.no),
                    android.text.TextUtils.join("; ", history.getTargetPreview()),
                    emptyIfNull(history.getBootstrapSignature()));
        }
        return csv.toString();
    }

    @NonNull
    public static String toText(@NonNull Context context, @NonNull List<OpHistoryItem> histories) {
        StringBuilder text = new StringBuilder();
        text.append(context.getString(R.string.op_history)).append('\n');
        text.append(DateUtils.formatLongDateTime(context, System.currentTimeMillis())).append('\n');
        text.append(context.getResources().getQuantityString(
                R.plurals.op_history_operation_count,
                histories.size(),
                histories.size()));
        for (OpHistoryItem history : histories) {
            text.append("\n\n").append(toTextEntry(context, history));
        }
        return text.toString();
    }

    @NonNull
    static String toTextEntry(@NonNull Context context, @NonNull OpHistoryItem history) {
        return toTextEntry(context, history, history.getDetailMessage(context));
    }

    @NonNull
    static String toTextEntry(@NonNull Context context,
                              @NonNull OpHistoryItem history,
                              @NonNull String detailMessage) {
        return ExportTextUtils.toPlainTextReport(history.getLabel(context) + '\n' + detailMessage);
    }

    @NonNull
    static String toTextLabel(@NonNull Context context, @NonNull OpHistoryItem history) {
        String label = ExportTextUtils.escapeTsvField(history.getLabel(context)).trim();
        return label.isEmpty() ? context.getString(R.string.op_history) : label;
    }

    private static void appendCsvLine(@NonNull StringBuilder builder, @NonNull String... values) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        for (int i = 0; i < values.length; ++i) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"');
            builder.append(ExportTextUtils.escapeCsvField(values[i]));
            builder.append('"');
        }
    }

    @NonNull
    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }

    @NonNull
    private static String emptyIfNull(Integer value) {
        return value != null ? Integer.toString(value) : "";
    }

}
