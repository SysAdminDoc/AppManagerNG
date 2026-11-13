// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.progress;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

public abstract class QueuedProgressHandler extends ProgressHandler {
    /**
     * Call when items are added to queue. This can be unrelated to progress, but useful in situations where queues
     * need to be handled.
     */
    @MainThread
    public abstract void onQueue(@Nullable Object message);

    /**
     * Call when an item passed to {@link #onQueue(Object)} leaves the queue (it started, or the work was dropped),
     * with the same message object, so anything shown for the waiting item can go away.
     */
    @MainThread
    public void onDequeue(@Nullable Object message) {
    }
}
