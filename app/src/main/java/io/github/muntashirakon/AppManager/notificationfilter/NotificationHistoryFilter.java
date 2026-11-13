// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.misc.TextQuery;

/** Narrows the history the way the screen asks: by app, by what was done, and by a search. */
public final class NotificationHistoryFilter {
    /** One app in the app filter, with how many entries it has. */
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

    private NotificationHistoryFilter() {
    }

    /** @return Every app in the entries once, by name, with the newest label each was saved under. */
    @NonNull
    public static List<AppChoice> apps(@NonNull List<NotificationHistoryEntry> entries) {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (NotificationHistoryEntry entry : entries) {
            if (!labels.containsKey(entry.packageName)) {
                labels.put(entry.packageName, entry.appLabel);
            }
            Integer count = counts.get(entry.packageName);
            counts.put(entry.packageName, count == null ? 1 : count + 1);
        }
        List<AppChoice> apps = new ArrayList<>(labels.size());
        for (Map.Entry<String, String> app : labels.entrySet()) {
            Integer count = counts.get(app.getKey());
            apps.add(new AppChoice(app.getKey(), app.getValue(), count == null ? 0 : count));
        }
        Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> {
            int byLabel = collator.compare(a.label, b.label);
            return byLabel != 0 ? byLabel : a.packageName.compareTo(b.packageName);
        });
        return apps;
    }

    /** @return The app filter, or {@code null} once that app has nothing left, so the list doesn't go blank. */
    @Nullable
    public static String keepIfPresent(@Nullable String packageName, @NonNull List<AppChoice> apps) {
        if (packageName == null) {
            return null;
        }
        for (AppChoice app : apps) {
            if (app.packageName.equals(packageName)) {
                return packageName;
            }
        }
        return null;
    }

    /**
     * @param packageName Only this app's entries, or every app's if {@code null}.
     * @param action      Only entries where this was done, or all of them if {@code null}.
     * @param query       Matched against the title, text, sender, app name and package.
     * @return The entries that pass, in the order they came.
     */
    @NonNull
    public static List<NotificationHistoryEntry> filter(@NonNull List<NotificationHistoryEntry> entries,
                                                        @Nullable String packageName, @Nullable String action,
                                                        @NonNull TextQuery query) {
        if (packageName == null && action == null && query.isValid() && query.isEmpty()) {
            return entries;
        }
        List<NotificationHistoryEntry> kept = new ArrayList<>();
        for (NotificationHistoryEntry entry : entries) {
            if (packageName != null && !packageName.equals(entry.packageName)) {
                continue;
            }
            if (action != null && !action.equals(entry.action)) {
                continue;
            }
            if (query.matches(entry.title, entry.text, entry.sender, entry.appLabel, entry.packageName)) {
                kept.add(entry);
            }
        }
        return kept;
    }
}
