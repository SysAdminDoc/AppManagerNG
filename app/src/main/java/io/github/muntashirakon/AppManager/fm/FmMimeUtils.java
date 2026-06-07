// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

final class FmMimeUtils {
    private FmMimeUtils() {
    }

    @NonNull
    static String normalizeMimeTypeOrDefault(@Nullable String mimeType) {
        String normalized = normalizeMimeType(mimeType);
        return normalized != null ? normalized : ContentType2.OTHER.getMimeType();
    }

    @Nullable
    static String normalizeMimeType(@Nullable String mimeType) {
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
    static String getMimeMajorType(@NonNull String mimeType) {
        return mimeType.substring(0, mimeType.indexOf('/'));
    }
}
