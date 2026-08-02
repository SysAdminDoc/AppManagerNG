// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_DAEMON;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_START_SERVICE;
import static io.github.muntashirakon.AppManager.server.common.ServerUtils.CMDLINE_STOP_SERVICE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The root-service trampoline receives its component name, client uid and action as argv, and
 * {@code killOldServer} decides what to kill from a {@code /proc} entry. Each of those elements
 * selects something privileged, so none of them may be trusted on shape alone.
 */
@RunWith(RobolectricTestRunner.class)
public class ServerUtilsArgsTest {
    private static final String COMPONENT = "io.github.sysadmindoc.AppManagerNG/.ipc.RootService";

    @Test
    public void wellFormedArgsParse() {
        ServerUtils.LaunchArgs args = ServerUtils.parseLaunchArgs(
                new String[]{COMPONENT, "10123", CMDLINE_START_SERVICE});
        assertEquals("io.github.sysadmindoc.AppManagerNG", args.component.getPackageName());
        assertEquals(10123, args.uid);
        assertFalse(args.isDaemon);
        assertFalse(args.stop);
    }

    @Test
    public void daemonAndStopActionsParse() {
        ServerUtils.LaunchArgs daemon = ServerUtils.parseLaunchArgs(
                new String[]{COMPONENT, "0", CMDLINE_START_DAEMON});
        assertTrue(daemon.isDaemon);
        assertFalse(daemon.stop);

        ServerUtils.LaunchArgs stop = ServerUtils.parseLaunchArgs(
                new String[]{COMPONENT, "0", CMDLINE_STOP_SERVICE});
        assertTrue(stop.isDaemon);
        assertTrue(stop.stop);
    }

    @Test
    public void missingArgumentsAreNamed() {
        assertArgsRejected(null);
        assertArgsRejected(new String[0]);
        assertArgsRejected(new String[]{COMPONENT});
        assertArgsRejected(new String[]{COMPONENT, "0"});
    }

    @Test
    public void unexpectedComponentIsRejected() {
        // No package/class separator at all.
        assertArgsRejected(new String[]{"io.github.sysadmindoc.AppManagerNG", "0", CMDLINE_START_SERVICE});
        assertArgsRejected(new String[]{"", "0", CMDLINE_START_SERVICE});
        assertArgsRejected(new String[]{"/.ipc.RootService", "0", CMDLINE_START_SERVICE});
        assertArgsRejected(new String[]{"io.github.sysadmindoc.AppManagerNG/", "0", CMDLINE_START_SERVICE});
    }

    @Test
    public void malformedUidIsRejected() {
        assertArgsRejected(new String[]{COMPONENT, "not-a-number", CMDLINE_START_SERVICE});
        assertArgsRejected(new String[]{COMPONENT, "", CMDLINE_START_SERVICE});
        assertArgsRejected(new String[]{COMPONENT, "-1", CMDLINE_START_SERVICE});
    }

    @Test
    public void unknownActionIsRejected() {
        assertArgsRejected(new String[]{COMPONENT, "0", "launch"});
        assertArgsRejected(new String[]{COMPONENT, "0", ""});
    }

    @Test
    public void ourOwnServerIsRecognised() {
        assertTrue(ServerUtils.isOldServer(Constants.SERVER_NAME, 0, 0));
        assertTrue(ServerUtils.isOldServer(Constants.SERVER_NAME, 2000, 2000));
    }

    @Test
    public void sameNamedImpostorUnderAnotherUidIsNotKilled() {
        // A process name is chosen by whoever starts the process, so it is not an identity.
        assertFalse(ServerUtils.isOldServer(Constants.SERVER_NAME, 10123, 0));
        assertFalse(ServerUtils.isOldServer(Constants.SERVER_NAME, 10123, 2000));
        assertFalse(ServerUtils.isOldServer(Constants.SERVER_NAME, 0, 2000));
    }

    @Test
    public void unreadableProcEntryIsNotKilled() {
        // -1 means the /proc entry could not be stat'ed; an unknown owner is never a match.
        assertFalse(ServerUtils.isOldServer(Constants.SERVER_NAME, -1, 0));
        assertFalse(ServerUtils.isOldServer(Constants.SERVER_NAME, -1, -1));
    }

    @Test
    public void differentProcessNameIsNotKilled() {
        assertFalse(ServerUtils.isOldServer("com.example.other", 0, 0));
        assertFalse(ServerUtils.isOldServer("", 0, 0));
        assertFalse(ServerUtils.isOldServer(null, 0, 0));
    }

    private static void assertArgsRejected(String[] args) {
        try {
            ServerUtils.parseLaunchArgs(args);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Named failure; the trampoline logs it and exits.
        }
    }
}
