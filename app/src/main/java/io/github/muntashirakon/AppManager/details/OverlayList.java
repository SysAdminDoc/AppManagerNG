// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import java.util.ArrayList;
import java.util.List;

/**
 * Every overlay the last read found, and what the Overlays tab shows of them. Search and sort
 * always start from the full list, never from what they showed before. Reads are numbered when
 * they start, and a read that finishes after one that started later is dropped, so the tab never
 * goes back to an older state.
 */
final class OverlayList<T> {
    interface FilterAndSort<T> {
        @NonNull
        List<T> apply(@NonNull List<T> all);
    }

    @NonNull
    private final FilterAndSort<T> mFilterAndSort;
    @NonNull
    private final Consumer<List<T>> mShow;
    private final List<T> mAll = new ArrayList<>();
    private long mReadsStarted;
    private long mReadShown;

    /**
     * @param show called under this list's lock, so what it's handed is in the order of the reads
     */
    OverlayList(@NonNull FilterAndSort<T> filterAndSort, @NonNull Consumer<List<T>> show) {
        mFilterAndSort = filterAndSort;
        mShow = show;
    }

    /**
     * @return the read's number, to hand to {@link #finishRead(long, List)}
     */
    @AnyThread
    synchronized long startRead() {
        return ++mReadsStarted;
    }

    /**
     * @return {@code false} if a read that started later already finished, in which case nothing changes
     */
    @AnyThread
    synchronized boolean finishRead(long read, @NonNull List<T> items) {
        if (read < mReadShown) {
            return false;
        }
        mReadShown = read;
        mAll.clear();
        mAll.addAll(items);
        show();
        return true;
    }

    /**
     * Shows the last read again with the current search and sort.
     */
    @AnyThread
    synchronized void refilter() {
        show();
    }

    private void show() {
        mShow.accept(mFilterAndSort.apply(new ArrayList<>(mAll)));
    }
}
