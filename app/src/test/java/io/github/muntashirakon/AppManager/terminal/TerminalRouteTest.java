// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.settings.Ops;

@RunWith(RobolectricTestRunner.class)
public class TerminalRouteTest {
    @Test
    public void resolveUsesLocalWhenNoPrivilegedProviderIsExpected() {
        TerminalRoute route = TerminalRoute.resolve(false, false, false, false, android.os.Process.myUid());

        assertEquals(TerminalRoute.Type.LOCAL, route.getType());
        assertFalse(route.isLocalFallback());
        assertTrue(route.usesLocalProcess());
        assertNull(route.getUnavailablePrivilegedType());
    }

    @Test
    public void resolveUsesAdbWhenRemoteShellIsAvailable() {
        TerminalRoute route = TerminalRoute.resolve(true, false, false, true, Ops.SHELL_UID);

        assertEquals(TerminalRoute.Type.ADB, route.getType());
        assertFalse(route.isLocalFallback());
        assertFalse(route.usesLocalProcess());
    }

    @Test
    public void resolveUsesShizukuBeforeRootBackedUid() {
        TerminalRoute route = TerminalRoute.resolve(true, false, true, false, Ops.ROOT_UID);

        assertEquals(TerminalRoute.Type.SHIZUKU, route.getType());
        assertFalse(route.isLocalFallback());
    }

    @Test
    public void resolveMarksExplicitFallbackWhenExpectedRemoteIsUnavailable() {
        TerminalRoute route = TerminalRoute.resolve(false, false, false, true, Ops.SHELL_UID);

        assertEquals(TerminalRoute.Type.LOCAL, route.getType());
        assertTrue(route.isLocalFallback());
        assertTrue(route.usesLocalProcess());
        assertEquals(TerminalRoute.Type.ADB, route.getUnavailablePrivilegedType());
    }

    @Test
    public void processEndedMessageNamesRouteAndExitCode() {
        Context context = ApplicationProvider.getApplicationContext();
        TerminalRoute route = TerminalRoute.resolve(true, false, false, true, Ops.SHELL_UID);

        assertEquals("Terminal process ended via ADB (exit 137).", route.getProcessEndedText(context, 137));
    }
}
