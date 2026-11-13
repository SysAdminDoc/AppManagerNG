// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;

/**
 * Search for the Local sync app list: the app name, the package name and the version name or code,
 * of the other device's copy and of the one installed here.
 */
public final class LanSyncRowFilter {
    private LanSyncRowFilter() {
    }

    /**
     * The rows matching {@code query}, in the same order, or every row when there's no query. A
     * regular expression is compiled once, and one that doesn't compile matches nothing.
     */
    @NonNull
    public static List<LanSyncViewModel.AppRow> matching(@Nullable List<LanSyncViewModel.AppRow> rows,
                                                         @Nullable String query,
                                                         @AdvancedSearchView.SearchType int searchType) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        if (query == null || query.isEmpty()) {
            return new ArrayList<>(rows);
        }
        boolean regex = searchType == AdvancedSearchView.SEARCH_TYPE_REGEX;
        // Plain searches ignore case. A regex keeps its own case rules, like the other lists.
        String q = regex ? query : query.toLowerCase(Locale.ROOT);
        List<LanSyncViewModel.AppRow> result = AdvancedSearchView.matches(q, rows,
                (AdvancedSearchView.ChoicesGenerator<LanSyncViewModel.AppRow>) row -> choices(row, regex),
                searchType);
        return result != null ? result : Collections.<LanSyncViewModel.AppRow>emptyList();
    }

    @NonNull
    private static List<String> choices(@NonNull LanSyncViewModel.AppRow row, boolean regex) {
        List<String> choices = new ArrayList<>(7);
        add(choices, row.remote.label, regex);
        add(choices, row.remote.packageName, regex);
        add(choices, row.remote.versionName, regex);
        choices.add(Long.toString(row.remote.versionCode));
        if (row.local != null) {
            add(choices, row.local.label, regex);
            add(choices, row.local.versionName, regex);
            choices.add(Long.toString(row.local.versionCode));
        }
        return choices;
    }

    private static void add(@NonNull List<String> choices, @Nullable String text, boolean regex) {
        if (text != null && !text.isEmpty()) {
            choices.add(regex ? text : text.toLowerCase(Locale.ROOT));
        }
    }
}
