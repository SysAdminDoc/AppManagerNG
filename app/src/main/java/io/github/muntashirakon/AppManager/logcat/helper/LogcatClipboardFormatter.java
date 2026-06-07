// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import androidx.annotation.NonNull;

import java.util.Collection;

import io.github.muntashirakon.AppManager.utils.ExportTextUtils;

public final class LogcatClipboardFormatter {
    private LogcatClipboardFormatter() {
    }

    @NonNull
    public static String formatLine(@NonNull String line) {
        return ExportTextUtils.toPlainTextReport(line);
    }

    @NonNull
    public static String formatLines(@NonNull Collection<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(line);
        }
        return ExportTextUtils.toPlainTextReport(builder.toString());
    }
}
