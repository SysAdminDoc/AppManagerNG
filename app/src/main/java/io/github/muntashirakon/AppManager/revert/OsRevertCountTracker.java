// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.revert;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thread-safe per-package OS-revert counter used by the T21-G attention
 * badge source. Each entry has a TTL so a single revert event does not
 * keep the badge lit forever - the goal is "recent" reverts, not
 * "ever-reverted".
 *
 * <p>{@link OsRevertMonitor} fires a {@code LiveData<RevertEvent>} when
 * the OS reverts a privileged change. That stream is point-in-time; the
 * tracker keeps an aggregate so {@link
 * io.github.muntashirakon.AppManager.main.AttentionBadgeSource#forApp}
 * can answer "how many recent reverts for this package?" without
 * walking the LiveData history.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #recordRevert(String, long)} increments the counter for
 *       a package, stamping the current millis.</li>
 *   <li>{@link #countRecent(String, long, long)} returns the count of
 *       reverts within the trailing TTL window.</li>
 *   <li>{@link #evictExpired(long, long)} drops stale entries.
 *       Callers should run this on a heartbeat (e.g. main-list refresh)
 *       so the map does not grow unbounded.</li>
 * </ul>
 *
 * <p>The tracker is JVM-clean and uses an injectable clock for tests.
 * All methods take an explicit {@code nowMillis} so the caller decides
 * what "now" means - useful for tests and for batch processing.
 */
public final class OsRevertCountTracker {

    /** Default TTL: 7 days. Long enough to surface multi-day OEM cleanup. */
    public static final long DEFAULT_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    /** Hard cap on per-package event log to prevent runaway memory in a regression. */
    public static final int MAX_EVENTS_PER_PACKAGE = 256;

    /** Hard cap on the total tracked-package set; oldest-touched entries evict first. */
    public static final int MAX_TRACKED_PACKAGES = 8192;

    private final Map<String, List<Long>> mPerPackageEventTimes = new HashMap<>();
    private final Map<String, Long> mLastTouchMillis = new HashMap<>();

    /** Record a single revert event for {@code packageName} at {@code nowMillis}. */
    public synchronized void recordRevert(@NonNull String packageName, long nowMillis) {
        if (packageName.isEmpty()) return;
        List<Long> events = mPerPackageEventTimes.get(packageName);
        if (events == null) {
            // Capacity-evict before inserting so the map cannot grow past the cap.
            if (mPerPackageEventTimes.size() >= MAX_TRACKED_PACKAGES) {
                evictOldestTrackedPackage();
            }
            events = new ArrayList<>(4);
            mPerPackageEventTimes.put(packageName, events);
        }
        events.add(nowMillis);
        if (events.size() > MAX_EVENTS_PER_PACKAGE) {
            // Drop the oldest event(s) so the per-package list never blows up.
            events.remove(0);
        }
        mLastTouchMillis.put(packageName, nowMillis);
    }

    /**
     * Count of revert events for {@code packageName} whose timestamp is
     * &gt;= {@code nowMillis - ttlMillis}. Negative TTLs are treated as
     * zero (no recent events).
     */
    public synchronized int countRecent(@NonNull String packageName,
                                        long nowMillis,
                                        long ttlMillis) {
        if (ttlMillis <= 0L) return 0;
        List<Long> events = mPerPackageEventTimes.get(packageName);
        if (events == null || events.isEmpty()) return 0;
        long cutoff = nowMillis - ttlMillis;
        int hit = 0;
        for (Long ts : events) {
            if (ts != null && ts >= cutoff) ++hit;
        }
        return hit;
    }

    /**
     * Drop every event older than {@code nowMillis - ttlMillis} and every
     * package whose entire log fell off the window. Returns the number of
     * entries removed (useful for diagnostics).
     */
    public synchronized int evictExpired(long nowMillis, long ttlMillis) {
        if (ttlMillis <= 0L) return 0;
        long cutoff = nowMillis - ttlMillis;
        int removed = 0;
        List<String> emptyKeys = null;
        for (Map.Entry<String, List<Long>> e : mPerPackageEventTimes.entrySet()) {
            List<Long> events = e.getValue();
            int before = events.size();
            events.removeIf(ts -> ts == null || ts < cutoff);
            removed += before - events.size();
            if (events.isEmpty()) {
                if (emptyKeys == null) emptyKeys = new ArrayList<>();
                emptyKeys.add(e.getKey());
            }
        }
        if (emptyKeys != null) {
            for (String key : emptyKeys) {
                mPerPackageEventTimes.remove(key);
                mLastTouchMillis.remove(key);
            }
        }
        return removed;
    }

    /** Diagnostic accessor used by tests. */
    @VisibleForTesting
    public synchronized int trackedPackageCount() {
        return mPerPackageEventTimes.size();
    }

    /** Diagnostic accessor: total tracked events across every package. */
    @VisibleForTesting
    public synchronized int totalEventCount() {
        int total = 0;
        for (List<Long> events : mPerPackageEventTimes.values()) {
            total += events.size();
        }
        return total;
    }

    /** Reset for tests. */
    @VisibleForTesting
    public synchronized void clear() {
        mPerPackageEventTimes.clear();
        mLastTouchMillis.clear();
    }

    /** Returns an immutable snapshot of tracked packages. Used for sweeps. */
    @NonNull
    public synchronized List<String> trackedPackages() {
        return Collections.unmodifiableList(new ArrayList<>(mPerPackageEventTimes.keySet()));
    }

    private void evictOldestTrackedPackage() {
        String oldestKey = null;
        long oldestTouch = Long.MAX_VALUE;
        for (Map.Entry<String, Long> e : mLastTouchMillis.entrySet()) {
            Long touch = e.getValue();
            if (touch == null) continue;
            if (touch < oldestTouch) {
                oldestTouch = touch;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            mPerPackageEventTimes.remove(oldestKey);
            mLastTouchMillis.remove(oldestKey);
        }
    }
}
