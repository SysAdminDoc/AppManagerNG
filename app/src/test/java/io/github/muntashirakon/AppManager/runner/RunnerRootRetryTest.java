// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

/**
 * The root shell lives for the whole process, so after su refused once, granting root in the root
 * manager did nothing until the app restarted. Magisk 31.1 and later never ask, so that's the only
 * way to grant it there.
 */
@RunWith(RobolectricTestRunner.class)
public class RunnerRootRetryTest {
    @After
    public void tearDown() throws ReflectiveOperationException {
        setRootShell(null);
    }

    @Test
    public void aRefusedRootShellIsClosedSoTheNextCheckAsksAgain() throws ReflectiveOperationException {
        FakeRunner refused = new FakeRunner(false);
        setRootShell(refused);

        RunnerUtils.retryRootGrant();

        assertTrue(refused.mClosed);
        assertNull(getRootShell());
    }

    @Test
    public void aGrantedRootShellIsKept() throws ReflectiveOperationException {
        FakeRunner granted = new FakeRunner(true);
        setRootShell(granted);

        RunnerUtils.retryRootGrant();

        assertFalse(granted.mClosed);
        assertSame(granted, getRootShell());
    }

    private static Field rootShellField() throws NoSuchFieldException {
        Field field = Runner.class.getDeclaredField("sRootShell");
        field.setAccessible(true);
        return field;
    }

    private static Object getRootShell() throws ReflectiveOperationException {
        return rootShellField().get(null);
    }

    private static void setRootShell(Runner runner) throws ReflectiveOperationException {
        rootShellField().set(null, runner);
    }

    private static final class FakeRunner extends Runner {
        private final boolean mRoot;
        boolean mClosed;

        FakeRunner(boolean root) {
            mRoot = root;
        }

        @Override
        public boolean isRoot() {
            return mRoot;
        }

        @Override
        void close() {
            mClosed = true;
        }

        @NonNull
        @Override
        protected Result runCommand() {
            throw new UnsupportedOperationException();
        }
    }
}
