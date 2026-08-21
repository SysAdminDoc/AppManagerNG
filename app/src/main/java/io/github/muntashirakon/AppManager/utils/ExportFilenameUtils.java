// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ExportFilenameUtils {
    static final int MAX_FILENAME_BYTES = 240;

    private static final Pattern SAFE_EXTENSION = Pattern.compile("(?:\\.[A-Za-z0-9_-]+)+");
    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final Pattern EDGE_SEPARATORS = Pattern.compile("^[._-]+|[._-]+$");
    private static final Pattern WINDOWS_RESERVED_NAME = Pattern.compile(
            "(?i)^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])$");

    private ExportFilenameUtils() {
    }

    @NonNull
    public static String buildTimestampedFileName(@NonNull Context context, @NonNull String prefix,
                                                   @NonNull String extension, long timestamp) {
        return buildFileName(prefix + DateUtils.formatDateTime(context, timestamp), extension, prefix);
    }

    @NonNull
    public static String buildFileName(@Nullable String stem, @NonNull String extension,
                                       @NonNull String fallbackStem) {
        return sanitizeBaseName(stem, extension, fallbackStem) + extension;
    }

    @NonNull
    public static String sanitizeBaseName(@Nullable String stem, @NonNull String reservedExtension,
                                          @NonNull String fallbackStem) {
        requireSafeExtension(reservedExtension);
        int maxStemLength = MAX_FILENAME_BYTES - reservedExtension.length();
        if (maxStemLength < 1) {
            throw new IllegalArgumentException("Extension leaves no room for a filename");
        }

        String safeStem = sanitizeComponent(stem);
        if (safeStem.isEmpty()) {
            safeStem = sanitizeComponent(fallbackStem);
        }
        if (safeStem.isEmpty()) {
            safeStem = "export";
        }
        if (WINDOWS_RESERVED_NAME.matcher(safeStem).matches()) {
            safeStem += "_file";
        }
        if (safeStem.length() > maxStemLength) {
            safeStem = safeStem.substring(0, maxStemLength);
        }
        safeStem = EDGE_SEPARATORS.matcher(safeStem).replaceAll("");
        return safeStem.isEmpty() ? "export" : safeStem;
    }

    private static void requireSafeExtension(@NonNull String extension) {
        if (!SAFE_EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Unsafe filename extension: %s", extension));
        }
    }

    @NonNull
    private static String sanitizeComponent(@Nullable String component) {
        if (component == null) {
            return "";
        }
        String normalized = Normalizer.normalize(component, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");
        return EDGE_SEPARATORS.matcher(
                UNSAFE_CHARACTERS.matcher(normalized).replaceAll("-")).replaceAll("");
    }
}
