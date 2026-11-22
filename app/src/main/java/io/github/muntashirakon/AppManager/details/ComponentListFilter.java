// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.details.struct.AppDetailsActivityItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;

/**
 * The Activities tab's "Launchable only" filter.
 */
public final class ComponentListFilter {
    private ComponentListFilter() {
    }

    /**
     * The rows that show a Launch button, by the same test the row uses ({@link AppDetailsActivityItem#isLaunchable()}).
     * The list itself is left alone, so turning the filter off brings every row back.
     *
     * @param enabled Whether the filter is on. When off, the same list comes back.
     */
    @NonNull
    public static List<AppDetailsItem<ComponentInfo>> launchableOnly(@NonNull List<AppDetailsItem<ComponentInfo>> items,
                                                                     boolean enabled) {
        if (!enabled) {
            return items;
        }
        List<AppDetailsItem<ComponentInfo>> launchable = new ArrayList<>();
        for (AppDetailsItem<ComponentInfo> item : items) {
            if (item instanceof AppDetailsActivityItem && ((AppDetailsActivityItem) item).isLaunchable()) {
                launchable.add(item);
            }
        }
        return launchable;
    }
}
