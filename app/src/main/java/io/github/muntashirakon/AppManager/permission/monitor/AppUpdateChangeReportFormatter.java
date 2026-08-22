// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.entity.AppUpdateChangeReport;

/** Formats persisted report rows for the existing local change-feed surface. */
public final class AppUpdateChangeReportFormatter {
    private static final int MAX_DISPLAY_ITEMS = 4;

    private AppUpdateChangeReportFormatter() {
    }

    public static boolean hasChanges(@NonNull AppUpdateChangeReport report) {
        return !decode(report.addedPermissions).isEmpty()
                || !decode(report.removedPermissions).isEmpty()
                || !decode(report.addedTrackers).isEmpty()
                || !decode(report.removedTrackers).isEmpty()
                || !decode(report.addedComponents).isEmpty()
                || !decode(report.removedComponents).isEmpty();
    }

    @NonNull
    public static String formatBody(@NonNull Context context,
                                    @NonNull AppUpdateChangeReport report) {
        return context.getString(R.string.app_update_change_report_body,
                report.beforeVersionCode, report.afterVersionCode,
                display(context, report.addedPermissions), display(context, report.removedPermissions),
                display(context, report.addedTrackers), display(context, report.removedTrackers),
                display(context, report.addedComponents), display(context, report.removedComponents));
    }

    @NonNull
    static List<String> decode(@NonNull String json) {
        if (json.trim().isEmpty()) return Collections.emptyList();
        try {
            JSONArray array = new JSONArray(json);
            List<String> values = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); ++i) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) values.add(value);
            }
            return values;
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }

    @NonNull
    private static String display(@NonNull Context context, @NonNull String json) {
        List<String> values = decode(json);
        if (values.isEmpty()) return context.getString(R.string.none);
        StringBuilder out = new StringBuilder();
        int count = Math.min(values.size(), MAX_DISPLAY_ITEMS);
        for (int i = 0; i < count; ++i) {
            String value = values.get(i);
            int dot = value.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < value.length()) value = value.substring(dot + 1);
            if (out.length() > 0) out.append(", ");
            out.append(value);
        }
        int hidden = values.size() - count;
        if (hidden > 0) out.append(" +").append(hidden);
        return out.toString();
    }
}
