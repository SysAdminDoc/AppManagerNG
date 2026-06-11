// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Per-package component and tracker-component snapshot.
 */
public final class ComponentSnapshot {
    public final long versionCode;
    @NonNull
    public final Set<String> components;
    @NonNull
    public final Set<String> trackerComponents;

    public ComponentSnapshot(long versionCode, @NonNull Set<String> components,
                             @NonNull Set<String> trackerComponents) {
        this.versionCode = versionCode;
        this.components = Collections.unmodifiableSet(new TreeSet<>(components));
        this.trackerComponents = Collections.unmodifiableSet(new TreeSet<>(trackerComponents));
    }

    @VisibleForTesting
    public static ComponentSnapshot of(long versionCode, @NonNull String[] components,
                                       @NonNull String[] trackerComponents) {
        Set<String> componentSet = new LinkedHashSet<>();
        for (String c : components) if (c != null && !c.isEmpty()) componentSet.add(c);
        Set<String> trackerSet = new LinkedHashSet<>();
        for (String c : trackerComponents) if (c != null && !c.isEmpty()) trackerSet.add(c);
        return new ComponentSnapshot(versionCode, componentSet, trackerSet);
    }
}
