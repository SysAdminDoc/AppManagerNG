// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

/**
 * Waits for Android's answer to a committed install session. Android can hold a committed session
 * well past the usual minute: Play Protect keeps it open while its own dialog ("Harmful app
 * blocked", "Scan app?") waits for the user. Giving up at the minute reported a timeout and lost
 * the real answer, which came seconds later, so the wait goes on for as long as the session is
 * still there.
 */
final class InstallResultWait {
    interface Step {
        /** Waits up to {@code millis} for the result and says whether it came. */
        boolean await(long millis) throws InterruptedException;
    }

    interface SessionCheck {
        /** Whether Android still holds the session, so an answer is still coming. */
        boolean isAlive();
    }

    static final long STEP_MILLIS = TimeUnit.SECONDS.toMillis(5);
    /** The answer goes out as the session is destroyed, so it can trail the session a little. */
    static final long GRACE_MILLIS = TimeUnit.SECONDS.toMillis(5);
    static final long MAX_WAIT_MILLIS = TimeUnit.MINUTES.toMillis(15);

    private InstallResultWait() {
    }

    /**
     * @return {@code true} when the result came, {@code false} when the session went without one
     * or the wait reached {@link #MAX_WAIT_MILLIS}.
     */
    static boolean await(long firstWaitMillis, @NonNull Step step, @NonNull SessionCheck session)
            throws InterruptedException {
        if (step.await(firstWaitMillis)) {
            return true;
        }
        long waited = firstWaitMillis;
        while (waited < MAX_WAIT_MILLIS) {
            if (!session.isAlive()) {
                return step.await(GRACE_MILLIS);
            }
            if (step.await(STEP_MILLIS)) {
                return true;
            }
            waited += STEP_MILLIS;
        }
        return false;
    }
}
