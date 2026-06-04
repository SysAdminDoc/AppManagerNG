// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ForceStopTileControllerTest {
    @Test
    public void encodeAndParseTargetRoundTripsPackageAndUser() {
        String encoded = ForceStopTileController.encodeTarget("com.example.app", 10);

        ForceStopTileController.Target target = ForceStopTileController.parseTarget(encoded);

        assertEquals("com.example.app:10", encoded);
        assertEquals("com.example.app", target.packageName);
        assertEquals(10, target.userId);
    }

    @Test
    public void parseTargetRejectsMalformedValues() {
        assertNull(ForceStopTileController.parseTarget(null));
        assertNull(ForceStopTileController.parseTarget(""));
        assertNull(ForceStopTileController.parseTarget("com.example.app"));
        assertNull(ForceStopTileController.parseTarget("com.example.app:"));
        assertNull(ForceStopTileController.parseTarget(":10"));
        assertNull(ForceStopTileController.parseTarget("not a package:10"));
        assertNull(ForceStopTileController.parseTarget("com.example.app:-1"));
        assertNull(ForceStopTileController.parseTarget("com.example.app:user"));
    }
}
