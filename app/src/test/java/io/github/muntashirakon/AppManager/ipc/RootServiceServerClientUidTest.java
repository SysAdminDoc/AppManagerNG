// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.UserHandle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * In ADB mode the privileged service runs as the shell (2000), and it sends its binder to the app
 * at startup from inside its own process. It used to keep the UID it was launched for only when
 * that "caller" was root, so an app in a work profile (user 10) never got the binder and its
 * startup check waited out its timeout.
 */
@RunWith(RobolectricTestRunner.class)
public class RootServiceServerClientUidTest {
    private static final int SHELL = 2000;
    private static final int PROFILE_APP = 10 * 100_000 + 10209;
    private static final int OWNER_APP = 10209;

    @Test
    public void anAdbModeServerDeliversToTheProfileItWasStartedFor() {
        // Its startup broadcast: no binder call, so the calling UID is its own
        int uid = RootServiceServer.resolveClientUid(SHELL, SHELL, PROFILE_APP);

        assertEquals(PROFILE_APP, uid);
        // sendBroadcastAsUser goes to user 10, not the owner
        assertEquals(UserHandle.getUserHandleForUid(10 * 100_000), UserHandle.getUserHandleForUid(uid));
        assertNotEquals(UserHandle.getUserHandleForUid(OWNER_APP), UserHandle.getUserHandleForUid(uid));
    }

    @Test
    public void theShellCannotRedirectARootServer() {
        // Only root or the server itself may name the client. Anything the shell starts that calls
        // into a root daemon is an ordinary caller there.
        assertEquals(SHELL, RootServiceServer.resolveClientUid(SHELL, 0, PROFILE_APP));
    }

    @Test
    public void rootModeKeepsTheUidItWasLaunchedFor() {
        assertEquals(PROFILE_APP, RootServiceServer.resolveClientUid(0, 0, PROFILE_APP));
        assertEquals(PROFILE_APP, RootServiceServer.resolveClientUid(0, SHELL, PROFILE_APP));
    }

    @Test
    public void anAppCanOnlyAskForItself() {
        // The owner's instance can't redirect the binder to the profile's, or to anyone else
        assertEquals(OWNER_APP, RootServiceServer.resolveClientUid(OWNER_APP, SHELL, PROFILE_APP));
        assertEquals(OWNER_APP, RootServiceServer.resolveClientUid(OWNER_APP, 0, PROFILE_APP));
    }
}
