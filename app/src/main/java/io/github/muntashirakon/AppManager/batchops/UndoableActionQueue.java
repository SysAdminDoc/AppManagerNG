// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds privileged destructive actions in a deferred-commit state so a
 * SnackBar undo path can intercept them before they hit the system (T21-F).
 *
 * <p>The queue is intentionally minimal: it has no awareness of Android,
 * SnackBars, or any specific privileged surface. Callers wire one of these
 * per surface and ask:
 * <ul>
 *   <li>{@link #defer(String, Runnable, long, long)} to register a pending
 *       commit and a deadline.</li>
 *   <li>{@link #cancel(int)} when the user taps Undo.</li>
 *   <li>{@link #pollExpired(long)} on a heartbeat / lifecycle-pause to drain
 *       and return the commits whose deadlines have elapsed.</li>
 * </ul>
 *
 * <p>All public methods are thread-safe via an instance monitor. The queue
 * deliberately exposes the commit list back to the caller rather than
 * invoking the commits itself, so callers can route the actual privileged
 * work back through their own executor without the queue becoming a tiny
 * scheduler. That separation also keeps the class JVM-unit-testable.
 */
public final class UndoableActionQueue {

    /** A single deferred action awaiting either timeout or cancellation. */
    public static final class Entry {
        public final int handle;
        @NonNull
        public final String label;
        @NonNull
        public final Runnable commit;
        public final long expiresAtMillis;

        Entry(int handle, @NonNull String label, @NonNull Runnable commit, long expiresAtMillis) {
            this.handle = handle;
            this.label = label;
            this.commit = commit;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    /** Strategy interface for clock injection. Tests pass a fixed clock. */
    public interface Clock {
        long currentTimeMillis();
    }

    private static final Clock SYSTEM_CLOCK = new Clock() {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    };

    private final AtomicInteger mHandleGen = new AtomicInteger();
    private final Map<Integer, Entry> mPending = new LinkedHashMap<>();
    @NonNull
    private final Clock mClock;

    public UndoableActionQueue() {
        this(SYSTEM_CLOCK);
    }

    /** Visible for testing. */
    public UndoableActionQueue(@NonNull Clock clock) {
        mClock = clock;
    }

    /**
     * Register a deferred action. The action will be returned by
     * {@link #pollExpired(long)} once {@code delayMillis} has elapsed from
     * {@code nowMillis}, unless {@link #cancel(int)} arrives first.
     *
     * @param label   short user-readable description for the SnackBar.
     * @param commit  the actual privileged work to run on undo timeout.
     * @param delayMillis how long the undo window stays open.
     * @return opaque handle the caller hands to {@link #cancel(int)}.
     */
    @AnyThread
    public synchronized int defer(@NonNull String label, @NonNull Runnable commit,
                                  long delayMillis) {
        return defer(label, commit, mClock.currentTimeMillis(), delayMillis);
    }

    /** Same as {@link #defer(String, Runnable, long)} but with an explicit "now". */
    @AnyThread
    public synchronized int defer(@NonNull String label, @NonNull Runnable commit,
                                  long nowMillis, long delayMillis) {
        if (delayMillis < 0) delayMillis = 0;
        int h = mHandleGen.incrementAndGet();
        long expires = nowMillis + delayMillis;
        mPending.put(h, new Entry(h, label, commit, expires));
        return h;
    }

    /**
     * Convenience overload that consults {@link SnackbarDurationPolicy} to
     * derive the undo window from a destructiveness severity and the
     * current system animation scale, then defers via the existing
     * {@link #defer(String, Runnable, long)}. The caller still gets the
     * computed window via {@link Entry#expiresAtMillis} so the SnackBar
     * UI can match its visible duration to the queue deadline.
     */
    @AnyThread
    public synchronized int deferWithPolicy(@NonNull String label, @NonNull Runnable commit,
                                            @NonNull SnackbarDurationPolicy.Severity severity,
                                            float systemAnimScale) {
        long window = SnackbarDurationPolicy.windowFor(severity, systemAnimScale);
        return defer(label, commit, window);
    }

    /**
     * Cancel a pending action. Returns {@code true} if a matching entry was
     * still pending (the privileged commit will NOT run). Returns
     * {@code false} if the handle is unknown or the entry already expired
     * and was drained by a previous {@link #pollExpired(long)}.
     */
    @AnyThread
    public synchronized boolean cancel(int handle) {
        return mPending.remove(handle) != null;
    }

    /**
     * Drain every entry whose {@code expiresAtMillis &lt;= nowMillis}.
     * Returned entries are removed from the queue, so calling
     * {@link #cancel(int)} on them after this is a no-op.
     *
     * <p>The returned list iterates in insertion order so a heartbeat that
     * picks up several expired entries commits them deterministically.
     */
    @AnyThread
    @NonNull
    public synchronized List<Entry> pollExpired(long nowMillis) {
        if (mPending.isEmpty()) return new ArrayList<>(0);
        List<Entry> drained = new ArrayList<>();
        for (Map.Entry<Integer, Entry> e : new ArrayList<>(mPending.entrySet())) {
            if (e.getValue().expiresAtMillis <= nowMillis) {
                drained.add(e.getValue());
                mPending.remove(e.getKey());
            }
        }
        return drained;
    }

    /**
     * Drain every entry regardless of deadline. Use this on
     * {@code Lifecycle.Event#ON_STOP} or activity destroy to make sure no
     * deferred action survives past the surface that requested it.
     *
     * <p>Note: the returned commits still need to be invoked by the caller -
     * the queue never runs them itself.
     */
    @AnyThread
    @NonNull
    public synchronized List<Entry> drainAll() {
        if (mPending.isEmpty()) return new ArrayList<>(0);
        List<Entry> drained = new ArrayList<>(mPending.values());
        mPending.clear();
        return drained;
    }

    @AnyThread
    public synchronized int size() {
        return mPending.size();
    }

    @AnyThread
    @Nullable
    public synchronized Entry peek(int handle) {
        return mPending.get(handle);
    }
}
