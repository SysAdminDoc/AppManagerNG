// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ExportTextUtils {
    private ExportTextUtils() {
    }

    @NonNull
    public static String escapeCsvField(@NonNull String value) {
        return defuseCsvFormula(value.replace("\"", "\"\""));
    }

    @NonNull
    public static String escapeTsvField(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ');
        return defuseCsvFormula(escaped);
    }

    @NonNull
    public static String defuseCsvFormula(@NonNull String value) {
        if (startsWithSpreadsheetFormula(value)) {
            return "'" + value;
        }
        return value;
    }

    @NonNull
    public static String toMarkdownText(@Nullable String value) {
        String text = String.valueOf(value);
        StringBuilder safeText = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t') {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace && safeText.length() > 0 && safeText.charAt(safeText.length() - 1) != ' ') {
                safeText.append(' ');
            }
            pendingSpace = false;
            switch (c) {
                case '&':
                    safeText.append("&amp;");
                    break;
                case '<':
                    safeText.append("&lt;");
                    break;
                case '>':
                    safeText.append("&gt;");
                    break;
                case '\\':
                case '`':
                case '*':
                case '_':
                case '{':
                case '}':
                case '[':
                case ']':
                case '(':
                case ')':
                case '#':
                case '+':
                case '-':
                case '!':
                case '|':
                    safeText.append('\\').append(c);
                    break;
                default:
                    safeText.append(c);
                    break;
            }
        }
        return safeText.toString();
    }

    @NonNull
    public static String toPlainTextReport(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\t', ' ')
                .replace('\r', ' ');
        StringBuilder safeText = new StringBuilder(normalized.length());
        int lineStart = 0;
        for (int i = 0; i <= normalized.length(); ++i) {
            if (i == normalized.length() || normalized.charAt(i) == '\n') {
                if (i > lineStart) {
                    safeText.append(defuseCsvFormula(normalized.substring(lineStart, i)));
                }
                if (i < normalized.length()) {
                    safeText.append('\n');
                    lineStart = i + 1;
                }
            }
        }
        return safeText.toString();
    }

    private static boolean startsWithSpreadsheetFormula(@NonNull String value) {
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (c == '=' || c == '+' || c == '-' || c == '@'
                    || c == '\t' || c == '\r' || c == '\n') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return false;
    }
}
