// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Which component rows are expanded, by component name. Expand all and Collapse all act on the names passed in,
 * the rows the tab shows right now, so a row a search hides keeps its state.
 */
public final class ComponentExpansionState {
    private final Set<String> mExpanded = new HashSet<>();

    public boolean isExpanded(@NonNull String name) {
        return mExpanded.contains(name);
    }

    public void toggle(@NonNull String name) {
        if (!mExpanded.remove(name)) {
            mExpanded.add(name);
        }
    }

    public void expandAll(@NonNull Collection<String> names) {
        mExpanded.addAll(names);
    }

    public void collapseAll(@NonNull Collection<String> names) {
        mExpanded.removeAll(names);
    }

    /**
     * Whether every one of these rows is expanded, which turns the menu item into Collapse all. An empty tab has
     * nothing to collapse.
     */
    public boolean areAllExpanded(@NonNull Collection<String> names) {
        return !names.isEmpty() && mExpanded.containsAll(names);
    }
}
