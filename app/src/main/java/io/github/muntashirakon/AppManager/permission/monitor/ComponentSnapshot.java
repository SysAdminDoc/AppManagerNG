// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Per-package component and tracker-component snapshot.
 *
 * <p>Schema 2 keeps a {@link ComponentRecord} per component rather than a bare name, so the
 * auditor can see update-time privilege expansion — a component becoming exported, losing its
 * guard permission, or being enabled into reachability — and not just components appearing and
 * disappearing.
 */
public final class ComponentSnapshot {
    public final long versionCode;
    /** Component class name to its recorded manifest facts. Sorted for deterministic output. */
    @NonNull
    public final Map<String, ComponentRecord> records;
    @NonNull
    public final Set<String> components;
    @NonNull
    public final Set<String> trackerComponents;

    public ComponentSnapshot(long versionCode, @NonNull Map<String, ComponentRecord> records,
                             @NonNull Set<String> trackerComponents) {
        this.versionCode = versionCode;
        this.records = Collections.unmodifiableMap(new TreeMap<>(records));
        this.components = Collections.unmodifiableSet(new TreeSet<>(this.records.keySet()));
        this.trackerComponents = Collections.unmodifiableSet(new TreeSet<>(trackerComponents));
    }

    @VisibleForTesting
    @NonNull
    public static ComponentSnapshot of(long versionCode, @NonNull String[] components,
                                       @NonNull String[] trackerComponents) {
        Map<String, ComponentRecord> records = new TreeMap<>();
        for (String c : components) {
            if (c != null && !c.isEmpty()) records.put(c, ComponentRecord.unknown());
        }
        Set<String> trackerSet = new LinkedHashSet<>();
        for (String c : trackerComponents) if (c != null && !c.isEmpty()) trackerSet.add(c);
        return new ComponentSnapshot(versionCode, records, trackerSet);
    }
}
