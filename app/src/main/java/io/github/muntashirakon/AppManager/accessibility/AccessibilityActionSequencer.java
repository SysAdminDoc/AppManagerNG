// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility;

import androidx.annotation.NonNull;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class AccessibilityActionSequencer implements AutoCloseable {
    interface ReadyAction {
        boolean runIfReady();
    }

    interface Dispatcher {
        void dispatch(@NonNull Runnable action);
    }

    interface Scheduler {
        void schedule(@NonNull Runnable action, long delayMillis);

        void shutdownNow();
    }

    @NonNull
    static AccessibilityActionSequencer create(@NonNull Dispatcher uiDispatcher) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "accessibility-action-sequencer");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return new AccessibilityActionSequencer(uiDispatcher, new Scheduler() {
            @Override
            public void schedule(@NonNull Runnable action, long delayMillis) {
                executor.schedule(action, delayMillis, TimeUnit.MILLISECONDS);
            }

            @Override
            public void shutdownNow() {
                executor.shutdownNow();
            }
        });
    }

    private final Dispatcher mUiDispatcher;
    private final Scheduler mScheduler;
    private final AtomicInteger mGeneration = new AtomicInteger();
    private volatile boolean mClosed;

    AccessibilityActionSequencer(@NonNull Dispatcher uiDispatcher, @NonNull Scheduler scheduler) {
        mUiDispatcher = uiDispatcher;
        mScheduler = scheduler;
    }

    void runAfter(long delayMillis, @NonNull Runnable action) {
        int generation = mGeneration.get();
        mScheduler.schedule(() -> dispatchIfActive(generation, action), delayMillis);
    }

    void runWhenReady(@NonNull ReadyAction action, int maxChecks, long retryDelayMillis,
                      @NonNull Runnable onTimeout) {
        if (maxChecks <= 0) {
            throw new IllegalArgumentException("maxChecks must be positive");
        }
        int generation = mGeneration.get();
        dispatchAttempt(generation, action, maxChecks, retryDelayMillis, onTimeout);
    }

    void cancelPending() {
        mGeneration.incrementAndGet();
    }

    @Override
    public void close() {
        mClosed = true;
        cancelPending();
        mScheduler.shutdownNow();
    }

    private void dispatchAttempt(int generation, @NonNull ReadyAction action, int checksRemaining,
                                 long retryDelayMillis, @NonNull Runnable onTimeout) {
        dispatchIfActive(generation, () -> {
            if (action.runIfReady()) {
                return;
            }
            if (checksRemaining == 1) {
                onTimeout.run();
                return;
            }
            mScheduler.schedule(() -> dispatchAttempt(generation, action, checksRemaining - 1,
                    retryDelayMillis, onTimeout), retryDelayMillis);
        });
    }

    private void dispatchIfActive(int generation, @NonNull Runnable action) {
        if (mClosed || generation != mGeneration.get()) {
            return;
        }
        mUiDispatcher.dispatch(() -> {
            if (!mClosed && generation == mGeneration.get()) {
                action.run();
            }
        });
    }
}
