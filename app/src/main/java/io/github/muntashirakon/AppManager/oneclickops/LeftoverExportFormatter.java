// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.oneclickops;

import androidx.annotation.NonNull;

import java.util.List;

import io.github.muntashirakon.AppManager.utils.ExportTextUtils;

public final class LeftoverExportFormatter {
    private LeftoverExportFormatter() {
    }

    @NonNull
    public static String toTsv(@NonNull List<OneClickOpsViewModel.LeftoverEntry> entries) {
        StringBuilder out = new StringBuilder();
        appendTsvLine(out, "package_name", "kind", "size_bytes", "path");
        for (OneClickOpsViewModel.LeftoverEntry entry : entries) {
            appendTsvLine(out,
                    entry.leftover.packageName,
                    entry.leftover.kindLabel(),
                    Long.toString(entry.size),
                    entry.leftover.path.getPath());
        }
        return out.toString();
    }

    private static void appendTsvLine(@NonNull StringBuilder builder, @NonNull String... values) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        for (int i = 0; i < values.length; ++i) {
            if (i > 0) {
                builder.append('\t');
            }
            builder.append(ExportTextUtils.escapeTsvField(values[i]));
        }
    }
}
