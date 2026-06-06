// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.struct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LogLineTest {
    @Test
    public void selectPackageNameForUidReturnsNullForNoPackages() {
        assertNull(LogLine.selectPackageNameForUid(null));
        assertNull(LogLine.selectPackageNameForUid(new String[0]));
        assertNull(LogLine.selectPackageNameForUid(new String[]{"", null}));
    }

    @Test
    public void selectPackageNameForUidPrefersShortestPackageName() {
        assertEquals("com.primary", LogLine.selectPackageNameForUid(new String[]{
                "com.example.longer",
                "com.primary",
                "com.example.longest"
        }));
    }

    @Test
    public void selectPackageNameForUidBreaksLengthTiesByName() {
        assertEquals("com.alpha", LogLine.selectPackageNameForUid(new String[]{
                "com.gamma",
                "com.alpha",
                "com.delta"
        }));
    }
}
