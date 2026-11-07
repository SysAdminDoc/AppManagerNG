// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.pm.ApplicationInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.main.MainListOptions;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;

/**
 * Search and sort for a profile's app list. Based on the approach in ifritraen's pull request #24.
 */
public final class ProfileAppsListFilter {
    private ProfileAppsListFilter() {
    }

    /**
     * Returns a new list with the items matching {@code query} (label or package name), sorted by
     * {@code sortBy}. The input list is left untouched.
     */
    @NonNull
    public static List<AppsFragment.AppsFragmentItem> apply(@NonNull List<AppsFragment.AppsFragmentItem> items,
                                                            @Nullable String query,
                                                            @AdvancedSearchView.SearchType int searchType,
                                                            int sortBy, boolean reverse) {
        List<AppsFragment.AppsFragmentItem> result;
        if (TextUtils.isEmpty(query)) {
            result = new ArrayList<>(items);
        } else {
            boolean regex = searchType == AdvancedSearchView.SEARCH_TYPE_REGEX;
            // Plain searches ignore case. A regex keeps its own case rules, like the other lists.
            String q = regex ? query : query.toLowerCase(Locale.ROOT);
            result = AdvancedSearchView.matches(q, items, (AdvancedSearchView.ChoicesGenerator<AppsFragment.AppsFragmentItem>) item -> {
                List<String> choices = new ArrayList<>(2);
                choices.add(regex ? item.packageName : item.packageName.toLowerCase(Locale.ROOT));
                if (item.label != null) {
                    String label = item.label.toString();
                    choices.add(regex ? label : label.toLowerCase(Locale.ROOT));
                }
                return choices;
            }, searchType);
        }
        sort(result, sortBy, reverse);
        return result;
    }

    static void sort(@NonNull List<AppsFragment.AppsFragmentItem> items, int sortBy, boolean reverse) {
        if (items.size() <= 1) return;
        Collator collator = Collator.getInstance();
        int mode = reverse ? -1 : 1;
        Comparator<AppsFragment.AppsFragmentItem> byLabel = (o1, o2) ->
                collator.compare(displayName(o1), displayName(o2));
        // Label first, then the chosen key with a stable sort, so ties stay alphabetical
        Collections.sort(items, sortBy == MainListOptions.SORT_BY_APP_LABEL
                ? (o1, o2) -> mode * byLabel.compare(o1, o2) : byLabel);
        switch (sortBy) {
            case MainListOptions.SORT_BY_PACKAGE_NAME:
                Collections.sort(items, (o1, o2) -> mode * o1.packageName.compareTo(o2.packageName));
                break;
            case MainListOptions.SORT_BY_DOMAIN:
                Collections.sort(items, (o1, o2) -> mode * Boolean.compare(isSystemApp(o1), isSystemApp(o2)));
                break;
            case MainListOptions.SORT_BY_INSTALLATION_DATE:
                // Newest first, as in the main list
                Collections.sort(items, (o1, o2) -> -mode * Long.compare(firstInstallTime(o1), firstInstallTime(o2)));
                break;
            case MainListOptions.SORT_BY_LAST_UPDATE:
                Collections.sort(items, (o1, o2) -> -mode * Long.compare(lastUpdateTime(o1), lastUpdateTime(o2)));
                break;
            case MainListOptions.SORT_BY_APP_LABEL:
            default:
                break;
        }
    }

    @NonNull
    private static String displayName(@NonNull AppsFragment.AppsFragmentItem item) {
        return item.label != null ? item.label.toString() : item.packageName;
    }

    private static boolean isSystemApp(@NonNull AppsFragment.AppsFragmentItem item) {
        if (item.applicationInfo != null) {
            return (item.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        }
        return item.filterableAppInfo != null && item.filterableAppInfo.isSystemApp();
    }

    private static long firstInstallTime(@NonNull AppsFragment.AppsFragmentItem item) {
        if (item.firstInstallTime > 0) return item.firstInstallTime;
        return item.filterableAppInfo != null ? item.filterableAppInfo.getFirstInstallTime() : 0;
    }

    private static long lastUpdateTime(@NonNull AppsFragment.AppsFragmentItem item) {
        if (item.lastUpdateTime > 0) return item.lastUpdateTime;
        return item.filterableAppInfo != null ? item.filterableAppInfo.getLastUpdateTime() : 0;
    }
}
