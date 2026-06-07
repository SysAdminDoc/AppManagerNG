// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import io.github.muntashirakon.AppManager.logcat.struct.LogLine;

public final class LogcatStructuredExporter {
    public enum Format {
        JSON("application/json", "json"),
        CSV("text/csv", "csv");

        @NonNull
        public final String mimeType;
        @NonNull
        public final String extension;

        Format(@NonNull String mimeType, @NonNull String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }

    private LogcatStructuredExporter() {
    }

    @NonNull
    public static String toJson(@NonNull List<LogLine> logLines) throws JSONException {
        JSONObject export = new JSONObject();
        export.put("schema_version", 1);
        export.put("generated_at", System.currentTimeMillis());
        export.put("entry_count", logLines.size());
        JSONArray entries = new JSONArray();
        for (int i = 0; i < logLines.size(); ++i) {
            entries.put(toJsonObject(i, logLines.get(i)));
        }
        export.put("entries", entries);
        return export.toString(2);
    }

    @NonNull
    public static String toCsv(@NonNull List<LogLine> logLines) {
        StringBuilder csv = new StringBuilder();
        appendCsvLine(csv, "index", "timestamp", "uid_owner", "uid", "pid", "tid", "level",
                "tag", "package", "message", "original_line");
        for (int i = 0; i < logLines.size(); ++i) {
            LogLine logLine = logLines.get(i);
            appendCsvLine(csv,
                    Integer.toString(i),
                    emptyIfNull(logLine.getTimestamp()),
                    emptyIfNull(logLine.getUidOwner()),
                    emptyIfNegative(logLine.getUid()),
                    emptyIfNegative(logLine.getPid()),
                    emptyIfNegative(logLine.getTid()),
                    logLine.getLogLevel() != -1 ? Character.toString(LogLine.convertLogLevelToChar(logLine.getLogLevel())) : "",
                    emptyIfNull(logLine.getTagName()),
                    emptyIfNull(logLine.getPackageName()),
                    emptyIfNull(logLine.getLogOutput()),
                    logLine.getOriginalLine());
        }
        return csv.toString();
    }

    @NonNull
    public static String createExportFilename(@NonNull Format format) {
        String filename = SaveLogHelper.createLogFilename();
        if (filename.endsWith(".am.log")) {
            filename = filename.substring(0, filename.length() - ".am.log".length());
        }
        return filename + ".logcat." + format.extension;
    }

    @NonNull
    private static JSONObject toJsonObject(int index, @NonNull LogLine logLine) throws JSONException {
        JSONObject entry = new JSONObject();
        entry.put("index", index);
        putNullable(entry, "timestamp", logLine.getTimestamp());
        putNullable(entry, "uid_owner", logLine.getUidOwner());
        putNullableInt(entry, "uid", logLine.getUid());
        putNullableInt(entry, "pid", logLine.getPid());
        putNullableInt(entry, "tid", logLine.getTid());
        if (logLine.getLogLevel() != -1) {
            entry.put("level", Character.toString(LogLine.convertLogLevelToChar(logLine.getLogLevel())));
        } else {
            entry.put("level", JSONObject.NULL);
        }
        putNullable(entry, "tag", logLine.getTagName());
        putNullable(entry, "package", logLine.getPackageName());
        putNullable(entry, "message", logLine.getLogOutput());
        entry.put("original_line", logLine.getOriginalLine());
        return entry;
    }

    private static void putNullable(@NonNull JSONObject object, @NonNull String key, @Nullable String value)
            throws JSONException {
        object.put(key, value != null ? value : JSONObject.NULL);
    }

    private static void putNullableInt(@NonNull JSONObject object, @NonNull String key, int value)
            throws JSONException {
        object.put(key, value >= 0 ? value : JSONObject.NULL);
    }

    private static void appendCsvLine(@NonNull StringBuilder builder, @NonNull String... values) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        for (int i = 0; i < values.length; ++i) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escapeCsvField(values[i])).append('"');
        }
    }

    @NonNull
    private static String emptyIfNull(@Nullable String value) {
        return value != null ? value : "";
    }

    @NonNull
    private static String emptyIfNegative(int value) {
        return value >= 0 ? Integer.toString(value) : "";
    }

    @NonNull
    private static String escapeCsvField(@NonNull String value) {
        String escaped = value.replace("\"", "\"\"");
        if (startsWithSpreadsheetFormula(escaped)) {
            return "'" + escaped;
        }
        return escaped;
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
