// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProviderQueryUtils {
    public static final int DEFAULT_ROW_LIMIT = 500;
    public static final int ROW_LIMIT_STEP = 500;

    private static final int MAX_CELL_CHARS = 4096;
    private static final String NULL_CELL = "NULL";

    private ProviderQueryUtils() {
    }

    @NonNull
    public static List<String> parseAuthorities(@Nullable String authorities) {
        if (TextUtils.isEmpty(authorities)) {
            return Collections.emptyList();
        }
        Set<String> authoritySet = new LinkedHashSet<>();
        for (String authority : authorities.split(";")) {
            String trimmed = authority.trim();
            if (!trimmed.isEmpty()) {
                authoritySet.add(trimmed);
            }
        }
        return new ArrayList<>(authoritySet);
    }

    @Nullable
    public static String[] parseProjection(@Nullable Editable editable) {
        String text = editable == null ? null : editable.toString();
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        Set<String> projection = new LinkedHashSet<>();
        for (String part : text.split("[,\\r\\n]+")) {
            String column = part.trim();
            if (!column.isEmpty()) {
                projection.add(column);
            }
        }
        return projection.isEmpty() ? null : projection.toArray(new String[0]);
    }

    @Nullable
    public static String[] parseSelectionArgs(@Nullable Editable editable) {
        String text = editable == null ? null : editable.toString();
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        List<String> args = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String arg = line.trim();
            if (!arg.isEmpty()) {
                args.add(arg);
            }
        }
        return args.isEmpty() ? null : args.toArray(new String[0]);
    }

    @NonNull
    public static List<QueryParameter> parseQueryParameters(@Nullable Editable editable) {
        String text = editable == null ? null : editable.toString();
        if (TextUtils.isEmpty(text)) {
            return Collections.emptyList();
        }
        List<QueryParameter> parameters = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equalsAt = trimmed.indexOf('=');
            String key = equalsAt == -1 ? trimmed : trimmed.substring(0, equalsAt).trim();
            String value = equalsAt == -1 ? "" : trimmed.substring(equalsAt + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Query parameter names cannot be empty.");
            }
            parameters.add(new QueryParameter(key, value));
        }
        return parameters;
    }

    @NonNull
    public static Uri buildContentUri(@NonNull String authority, @Nullable String path,
                                      @NonNull List<QueryParameter> queryParameters) {
        String trimmedAuthority = authority.trim();
        if (trimmedAuthority.isEmpty()) {
            throw new IllegalArgumentException("Authority is required.");
        }
        if (trimmedAuthority.contains("://") || trimmedAuthority.contains("/")) {
            throw new IllegalArgumentException("Authority must not include a scheme or path.");
        }
        Uri.Builder builder = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(trimmedAuthority);
        if (!TextUtils.isEmpty(path)) {
            String normalizedPath = path.trim();
            while (normalizedPath.startsWith("/")) {
                normalizedPath = normalizedPath.substring(1);
            }
            for (String segment : normalizedPath.split("/")) {
                if (!segment.isEmpty()) {
                    builder.appendPath(segment);
                }
            }
        }
        for (QueryParameter parameter : queryParameters) {
            builder.appendQueryParameter(parameter.name, parameter.value);
        }
        return builder.build();
    }

    public static void validateSelection(@Nullable String selection, @Nullable String[] selectionArgs) {
        String normalizedSelection = selection == null ? "" : selection.trim();
        int argCount = selectionArgs == null ? 0 : selectionArgs.length;
        if (normalizedSelection.indexOf('\0') != -1 || normalizedSelection.contains(";")) {
            throw new IllegalArgumentException("Selection cannot contain NUL bytes or semicolons.");
        }
        int placeholders = countSelectionPlaceholders(normalizedSelection);
        if (placeholders != argCount) {
            throw new IllegalArgumentException("Selection placeholder count must match selection arguments.");
        }
    }

    @NonNull
    public static QueryResult executeQuery(@NonNull Context context, @NonNull QueryRequest request, int rowLimit) {
        try (Cursor cursor = context.getContentResolver().query(request.uri, request.projection, request.selection,
                request.selectionArgs, request.sortOrder)) {
            if (cursor == null) {
                throw new IllegalStateException("Provider returned a null cursor.");
            }
            return snapshotCursor(request.uri, cursor, rowLimit);
        }
    }

    @VisibleForTesting
    @NonNull
    static QueryResult snapshotCursor(@NonNull Uri uri, @NonNull Cursor cursor, int rowLimit) {
        int safeLimit = Math.max(1, rowLimit);
        String[] columns = cursor.getColumnNames();
        List<List<String>> rows = new ArrayList<>();
        boolean truncated = false;
        while (cursor.moveToNext()) {
            if (rows.size() >= safeLimit) {
                truncated = true;
                break;
            }
            List<String> row = new ArrayList<>(columns.length);
            for (int i = 0; i < columns.length; ++i) {
                row.add(readCell(cursor, i));
            }
            rows.add(row);
        }
        return new QueryResult(uri, columns, rows, truncated, safeLimit);
    }

    @NonNull
    public static String toTsv(@NonNull QueryResult result) {
        StringBuilder builder = new StringBuilder();
        appendTsvRow(builder, result.columns);
        for (List<String> row : result.rows) {
            appendTsvRow(builder, row);
        }
        return builder.toString();
    }

    public static boolean canUseUnprivilegedQuery(boolean exported, @Nullable String readPermission,
                                                  boolean hasReadPermission, @NonNull String providerPackageName,
                                                  @NonNull String currentPackageName, int targetUserId,
                                                  int currentUserId) {
        if (targetUserId != currentUserId) {
            return false;
        }
        if (providerPackageName.equals(currentPackageName)) {
            return true;
        }
        return exported && (readPermission == null || hasReadPermission);
    }

    @NonNull
    private static String readCell(@NonNull Cursor cursor, int columnIndex) {
        try {
            switch (cursor.getType(columnIndex)) {
                case Cursor.FIELD_TYPE_NULL:
                    return NULL_CELL;
                case Cursor.FIELD_TYPE_INTEGER:
                    return Long.toString(cursor.getLong(columnIndex));
                case Cursor.FIELD_TYPE_FLOAT:
                    return Double.toString(cursor.getDouble(columnIndex));
                case Cursor.FIELD_TYPE_BLOB:
                    byte[] blob = cursor.getBlob(columnIndex);
                    return blob == null ? NULL_CELL : "<blob " + blob.length + " B>";
                case Cursor.FIELD_TYPE_STRING:
                default:
                    return limitCell(cursor.getString(columnIndex));
            }
        } catch (Throwable throwable) {
            return "<unreadable: " + throwable.getClass().getSimpleName() + ">";
        }
    }

    @NonNull
    private static String limitCell(@Nullable String value) {
        if (value == null) {
            return NULL_CELL;
        }
        if (value.length() <= MAX_CELL_CHARS) {
            return value;
        }
        return value.substring(0, MAX_CELL_CHARS) + " ... [truncated]";
    }

    private static int countSelectionPlaceholders(@NonNull String selection) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int count = 0;
        for (int i = 0; i < selection.length(); ++i) {
            char c = selection.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '?' && !inSingleQuote && !inDoubleQuote) {
                ++count;
            }
        }
        return count;
    }

    private static void appendTsvRow(@NonNull StringBuilder builder, @NonNull String[] values) {
        for (int i = 0; i < values.length; ++i) {
            if (i > 0) {
                builder.append('\t');
            }
            builder.append(escapeTsvCell(values[i]));
        }
        builder.append('\n');
    }

    private static void appendTsvRow(@NonNull StringBuilder builder, @NonNull List<String> values) {
        for (int i = 0; i < values.size(); ++i) {
            if (i > 0) {
                builder.append('\t');
            }
            builder.append(escapeTsvCell(values.get(i)));
        }
        builder.append('\n');
    }

    @NonNull
    private static String escapeTsvCell(@Nullable String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    public static final class QueryParameter {
        @NonNull
        public final String name;
        @NonNull
        public final String value;

        public QueryParameter(@NonNull String name, @NonNull String value) {
            this.name = name;
            this.value = value;
        }
    }

    public static final class QueryRequest {
        @NonNull
        public final Uri uri;
        @Nullable
        public final String[] projection;
        @Nullable
        public final String selection;
        @Nullable
        public final String[] selectionArgs;
        @Nullable
        public final String sortOrder;

        public QueryRequest(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
            this.uri = uri;
            this.projection = projection;
            this.selection = TextUtils.isEmpty(selection) ? null : selection;
            this.selectionArgs = selectionArgs == null || selectionArgs.length == 0 ? null : selectionArgs;
            this.sortOrder = TextUtils.isEmpty(sortOrder) ? null : sortOrder;
        }
    }

    public static final class QueryResult {
        @NonNull
        public final Uri uri;
        @NonNull
        public final String[] columns;
        @NonNull
        public final List<List<String>> rows;
        public final boolean truncated;
        public final int rowLimit;

        QueryResult(@NonNull Uri uri, @NonNull String[] columns, @NonNull List<List<String>> rows,
                    boolean truncated, int rowLimit) {
            this.uri = uri;
            this.columns = columns;
            this.rows = rows;
            this.truncated = truncated;
            this.rowLimit = rowLimit;
        }
    }
}
