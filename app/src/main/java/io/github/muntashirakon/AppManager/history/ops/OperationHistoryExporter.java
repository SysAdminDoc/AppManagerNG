// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.DateUtils;

public final class OperationHistoryExporter {
    private OperationHistoryExporter() {
    }

    @NonNull
    public static String toJson(@NonNull Context context, @NonNull List<OpHistoryItem> histories)
            throws JSONException {
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
        return export.toString(2);
    }

    @NonNull
    public static String toCsv(@NonNull Context context, @NonNull List<OpHistoryItem> histories) {
        StringBuilder csv = new StringBuilder();
        appendCsvLine(csv, "id", "time", "type", "label", "status", "operation", "mode", "risk",
                "targets", "failures", "replayable", "reversible", "restart_required", "target_preview");
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
                    context.getString(history.isReplayable() ? R.string.yes : R.string.no),
                    context.getString(history.isReversible() ? R.string.yes : R.string.no),
                    context.getString(history.requiresRestart() ? R.string.yes : R.string.no),
                    android.text.TextUtils.join("; ", history.getTargetPreview()));
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
            text.append("\n\n")
                    .append(history.getLabel(context))
                    .append('\n')
                    .append(history.getDetailMessage(context));
        }
        return text.toString();
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
            builder.append(values[i].replace("\"", "\"\""));
            builder.append('"');
        }
    }
}
