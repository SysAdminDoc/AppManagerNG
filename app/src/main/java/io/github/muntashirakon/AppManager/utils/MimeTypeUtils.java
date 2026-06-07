// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class MimeTypeUtils {
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private MimeTypeUtils() {
    }

    @NonNull
    public static String normalizeMimeTypeOrDefault(@Nullable String mimeType) {
        String normalized = normalizeMimeType(mimeType);
        return normalized != null ? normalized : DEFAULT_MIME_TYPE;
    }

    @Nullable
    public static String normalizeMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String normalized = mimeType.trim();
        int parameterStart = normalized.indexOf(';');
        if (parameterStart >= 0) {
            normalized = normalized.substring(0, parameterStart).trim();
        }
        int slash = normalized.indexOf('/');
        if (slash <= 0 || slash != normalized.lastIndexOf('/') || slash == normalized.length() - 1) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    @NonNull
    public static String getMimeMajorType(@NonNull String mimeType) {
        return mimeType.substring(0, mimeType.indexOf('/'));
    }
}
