// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class InstallResultWaitTest {
    private static final long FIRST = TimeUnit.MINUTES.toMillis(1);

    /** Answers on the given call (counting from 1), or never with 0, and records what was asked. */
    private static final class FakeStep implements InstallResultWait.Step {
        final List<Long> asked = new ArrayList<>();
        private final int mAnswerOnCall;

        FakeStep(int answerOnCall) {
            mAnswerOnCall = answerOnCall;
        }

        @Override
        public boolean await(long millis) {
            asked.add(millis);
            return asked.size() == mAnswerOnCall;
        }

        long total() {
            long sum = 0;
            for (long millis : asked) {
                sum += millis;
            }
            return sum;
        }
    }

    @Test
    public void anAnswerInTheFirstMinuteNeedsNoSessionCheck() throws InterruptedException {
        FakeStep step = new FakeStep(1);

        assertTrue(InstallResultWait.await(FIRST, step, () -> {
            throw new AssertionError("no session check needed");
        }));
        assertEquals(1, step.asked.size());
    }

    @Test
    public void anAnswerAfterTheFirstMinuteStillCountsWhileTheSessionIsOpen() throws InterruptedException {
        // Play Protect's dialog held the session for a minute and a half before the user answered
        FakeStep step = new FakeStep(1 + 6);

        assertTrue(InstallResultWait.await(FIRST, step, () -> true));
        assertEquals(FIRST + 6 * InstallResultWait.STEP_MILLIS, step.total());
    }

    @Test
    public void anAnswerTrailingTheClosedSessionIsCaught() throws InterruptedException {
        FakeStep step = new FakeStep(2);

        assertTrue(InstallResultWait.await(FIRST, step, () -> false));
        assertEquals(InstallResultWait.GRACE_MILLIS, (long) step.asked.get(1));
    }

    @Test
    public void aSessionGoneWithoutAnAnswerStopsAfterTheGrace() throws InterruptedException {
        FakeStep step = new FakeStep(0);

        assertFalse(InstallResultWait.await(FIRST, step, () -> false));
        assertEquals(FIRST + InstallResultWait.GRACE_MILLIS, step.total());
    }

    @Test
    public void aSessionThatNeverAnswersStopsAtTheCap() throws InterruptedException {
        FakeStep step = new FakeStep(0);

        assertFalse(InstallResultWait.await(FIRST, step, () -> true));
        assertEquals(InstallResultWait.MAX_WAIT_MILLIS, step.total());
    }

    @Test
    public void theSessionIsCheckedBeforeEveryStep() throws InterruptedException {
        FakeStep step = new FakeStep(0);
        int[] checks = {0};

        assertFalse(InstallResultWait.await(FIRST, step, () -> ++checks[0] < 3));
        // Two open checks, each followed by a step, then the closed one and the grace
        assertEquals(3, checks[0]);
        assertEquals(FIRST + 2 * InstallResultWait.STEP_MILLIS + InstallResultWait.GRACE_MILLIS, step.total());
    }
}
