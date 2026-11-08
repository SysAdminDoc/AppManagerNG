// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Receivers in this process holding their broadcast open with {@code goAsync()}.
 * <p>
 * Since Android 14 a process gets its broadcasts one at a time, so every later broadcast to this
 * process waits until a held one finishes. A privileged service hands its binder over by broadcast,
 * so whatever is about to wait for that handoff calls {@link #releaseAll()} first. A released
 * holder answers its caller early and carries on with the work.
 */
public final class HeldBroadcasts {
    private static final Set<Runnable> sReleasers = new CopyOnWriteArraySet<>();

    private HeldBroadcasts() {
    }

    /**
     * @param release finishes the held broadcast. It may run on any thread and more than once.
     */
    @AnyThread
    public static void hold(@NonNull Runnable release) {
        sReleasers.add(release);
    }

    @AnyThread
    public static void done(@NonNull Runnable release) {
        sReleasers.remove(release);
    }

    @AnyThread
    public static void releaseAll() {
        for (Runnable release : sReleasers) {
            if (sReleasers.remove(release)) {
                release.run();
            }
        }
    }
}
