// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Coalesces the rapid {@code onQueryTextChange} callbacks a search view emits while the user is
 * still typing, so that only the query they paused on reaches the (potentially expensive) filter.
 *
 * <p>Every screen that filters a large list needs the same three behaviours, and getting any of
 * them wrong is what makes hand-rolled debouncing leak or drop input:
 *
 * <ul>
 *     <li>a newer keystroke replaces the pending pass rather than queueing behind it;</li>
 *     <li>an explicit submit runs immediately and cancels whatever was pending, so the user never
 *     sees their own submit overwritten by a stale in-flight query;</li>
 *     <li>the pending pass is dropped on {@link #cancel()}, so it cannot fire into a destroyed
 *     activity or against a detached view.</li>
 * </ul>
 *
 * <p>Callbacks are posted to the main looper. Cancellation is explicit — hosts must call
 * {@link #cancel()} from their teardown callback; posting through a view would not do this for
 * them, because a runnable already queued on a view's handler still runs after detach.
 */
public class SearchViewDebouncer {
    /**
     * Debounce window in milliseconds. Long enough to swallow a fast typist's inter-key gaps, short
     * enough that the list still feels live.
     */
    public static final long DEFAULT_DEBOUNCE_MS = 300L;

    /**
     * Receives the query once it has settled.
     */
    public interface OnQueryConsumer {
        void onQuery(@Nullable String query, int type);
    }

    @NonNull
    private final Handler mHandler;
    @NonNull
    private final OnQueryConsumer mConsumer;
    private final long mDebounceMs;
    @Nullable
    private Runnable mPending;

    public SearchViewDebouncer(@NonNull OnQueryConsumer consumer) {
        this(consumer, DEFAULT_DEBOUNCE_MS);
    }

    public SearchViewDebouncer(@NonNull OnQueryConsumer consumer, long debounceMs) {
        mHandler = new Handler(Looper.getMainLooper());
        mConsumer = consumer;
        mDebounceMs = debounceMs;
    }

    /**
     * Schedule {@code query} to be delivered once the user stops typing, replacing any pass that
     * has not run yet.
     */
    @MainThread
    public void onQueryTextChange(@Nullable String query, int type) {
        cancel();
        mPending = () -> {
            mPending = null;
            mConsumer.onQuery(query, type);
        };
        mHandler.postDelayed(mPending, mDebounceMs);
    }

    /**
     * Deliver {@code query} immediately, discarding any pending pass.
     */
    @MainThread
    public void onQueryTextSubmit(@Nullable String query, int type) {
        cancel();
        mConsumer.onQuery(query, type);
    }

    /**
     * Drop the pending pass. Call from {@code onDestroy()} / {@code onDestroyView()} so a queued
     * filter cannot run against a torn-down host.
     */
    @MainThread
    public void cancel() {
        if (mPending != null) {
            mHandler.removeCallbacks(mPending);
            mPending = null;
        }
    }

    @VisibleForTesting
    boolean hasPending() {
        return mPending != null;
    }
}
