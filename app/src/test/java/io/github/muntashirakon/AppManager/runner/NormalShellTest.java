// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.util.reflector.Reflector.reflector;

import com.topjohnwu.superuser.Shell;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;
import org.robolectric.util.reflector.Static;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Upstream App Manager #2048: libsu's default shell asks for root, so a no-root shell that went
 * through {@link Shell#getShell()} raised a superuser prompt in no-root mode.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {NormalShellTest.RecordingShell.class, NormalShellTest.RecordingBuilder.class})
public class NormalShellTest {
    static final List<String> sCalls = new ArrayList<>();
    static Shell sCachedShell;
    /**
     * The application's static initializer hands libsu a real builder before any test runs, and
     * libsu requires its own implementation, so the shadows only take over inside a test.
     */
    static boolean sRecording;

    @Before
    public void setUp() {
        sCalls.clear();
        sRecording = true;
    }

    @After
    public void tearDown() {
        sRecording = false;
        sCalls.clear();
        sCachedShell = null;
    }

    @Test
    public void nonRootShellNeverBuildsTheDefaultRootShell() {
        NormalShell shell = new NormalShell(false);

        assertFalse(shell.isRoot());
        assertEquals(Arrays.asList("getCachedShell", "build flags=" + Shell.FLAG_NON_ROOT_SHELL), sCalls);
    }

    @Test
    public void nonRootShellReusesACachedNonRootMainShell() {
        sCachedShell = new FakeShell(Shell.NON_ROOT_SHELL);

        NormalShell shell = new NormalShell(false);

        assertFalse(shell.isRoot());
        assertEquals(Collections.singletonList("getCachedShell"), sCalls);
    }

    @Test
    public void nonRootShellNeverReusesACachedRootShell() {
        sCachedShell = new FakeShell(Shell.ROOT_SHELL);

        NormalShell shell = new NormalShell(false);

        assertFalse(shell.isRoot());
        assertEquals(Arrays.asList("getCachedShell", "build flags=" + Shell.FLAG_NON_ROOT_SHELL), sCalls);
    }

    @Test
    public void rootShellStillUsesTheMainShell() {
        // Positive control: the recorder does see a getShell() call when one happens.
        sCachedShell = new FakeShell(Shell.ROOT_SHELL);

        NormalShell shell = new NormalShell(true);

        assertTrue(shell.isRoot());
        assertEquals(Collections.singletonList("getShell"), sCalls);
    }

    @Implements(Shell.class)
    public static class RecordingShell {
        @Implementation
        protected static Shell getShell() {
            if (!sRecording) {
                return reflector(ShellReflector.class).getShell();
            }
            sCalls.add("getShell");
            return sCachedShell != null ? sCachedShell : new FakeShell(Shell.NON_ROOT_SHELL);
        }

        @Implementation
        protected static Shell getCachedShell() {
            if (!sRecording) {
                return reflector(ShellReflector.class).getCachedShell();
            }
            sCalls.add("getCachedShell");
            return sCachedShell;
        }
    }

    @Implements(Shell.Builder.class)
    public static class RecordingBuilder {
        @Implementation
        protected static Shell.Builder create() {
            if (!sRecording) {
                return reflector(BuilderReflector.class).create();
            }
            return new FakeBuilder();
        }
    }

    @ForType(Shell.class)
    interface ShellReflector {
        @Static
        @Direct
        Shell getShell();

        @Static
        @Direct
        Shell getCachedShell();
    }

    @ForType(Shell.Builder.class)
    interface BuilderReflector {
        @Static
        @Direct
        Shell.Builder create();
    }

    static final class FakeBuilder extends Shell.Builder {
        private int mFlags;

        @Override
        public Shell.Builder setFlags(int flags) {
            mFlags = flags;
            return this;
        }

        @Override
        public Shell.Builder setTimeout(long timeout) {
            return this;
        }

        @Override
        public Shell.Builder setCommands(String... commands) {
            return this;
        }

        @Override
        public Shell build() {
            sCalls.add("build flags=" + mFlags);
            return new FakeShell((mFlags & Shell.FLAG_NON_ROOT_SHELL) != 0 ? Shell.NON_ROOT_SHELL : Shell.ROOT_SHELL);
        }

        @Override
        public Shell build(Process process) {
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeShell extends Shell {
        private final int mStatus;

        FakeShell(int status) {
            mStatus = status;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public void execTask(Task task) {
        }

        @Override
        public void submitTask(Task task) {
        }

        @Override
        public Job newJob() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getStatus() {
            return mStatus;
        }

        @Override
        public boolean waitAndClose(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
