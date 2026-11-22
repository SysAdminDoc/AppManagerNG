// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Names an Android API level the way people know it, like "12L (API 32)".
 */
public final class AndroidReleases {
    // Version names from API 21 (Android 5.0) on, in order
    private static final String[] NAMES = {
            "5.0", "5.1", "6", "7.0", "7.1", "8.0", "8.1", "9", "10", "11", "12", "12L", "13", "14", "15", "16",
            "17"
    };
    private static final int FIRST = 21;

    private AndroidReleases() {
    }

    @NonNull
    public static String describe(int sdk) {
        int index = sdk - FIRST;
        if (index < 0 || index >= NAMES.length) {
            return String.format(Locale.ROOT, "API %d", sdk);
        }
        return String.format(Locale.ROOT, "%s (API %d)", NAMES[index], sdk);
    }
}
