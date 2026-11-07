// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists the apps the saved toasts came from, for the screen's app filter. */
public final class ToastHistoryFilter {
    private ToastHistoryFilter() {
    }

    public static final class AppChoice {
        @NonNull
        public final String packageName;
        @NonNull
        public final String label;
        public final int count;

        AppChoice(@NonNull String packageName, @NonNull String label, int count) {
            this.packageName = packageName;
            this.label = label;
            this.count = count;
        }
    }

    /**
     * @param entries Newest first, so that the newest label names an app whose label has changed.
     * @return Each app once, by name.
     */
    @NonNull
    public static List<AppChoice> apps(@NonNull List<ToastEntry> entries) {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ToastEntry entry : entries) {
            if (!labels.containsKey(entry.packageName)) {
                labels.put(entry.packageName, entry.appLabel);
                counts.put(entry.packageName, 0);
            }
            counts.put(entry.packageName, counts.get(entry.packageName) + 1);
        }
        List<AppChoice> apps = new ArrayList<>(labels.size());
        for (Map.Entry<String, String> app : labels.entrySet()) {
            apps.add(new AppChoice(app.getKey(), app.getValue(), counts.get(app.getKey())));
        }
        Collections.sort(apps, (a, b) -> {
            int byLabel = a.label.compareToIgnoreCase(b.label);
            return byLabel != 0 ? byLabel : a.packageName.compareTo(b.packageName);
        });
        return apps;
    }

    /** @return The filter to go on using: an app with nothing left to show would just be an empty list with no way out. */
    @Nullable
    public static String keepIfPresent(@Nullable String packageName, @NonNull List<AppChoice> apps) {
        for (AppChoice app : apps) {
            if (app.packageName.equals(packageName)) {
                return packageName;
            }
        }
        return null;
    }
}
