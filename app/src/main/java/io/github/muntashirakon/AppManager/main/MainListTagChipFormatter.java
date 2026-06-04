// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import androidx.annotation.NonNull;

import java.util.Collection;

public final class MainListTagChipFormatter {
    private MainListTagChipFormatter() {
    }

    @NonNull
    public static String labelFor(@NonNull Collection<String> tags) {
        if (tags.isEmpty()) {
            return "";
        }
        String first = tags.iterator().next();
        int remaining = tags.size() - 1;
        if (remaining <= 0) {
            return first;
        }
        return first + " +" + remaining;
    }

    @NonNull
    public static String summaryFor(@NonNull Collection<String> tags) {
        StringBuilder sb = new StringBuilder();
        for (String tag : tags) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(tag);
        }
        return sb.toString();
    }
}
