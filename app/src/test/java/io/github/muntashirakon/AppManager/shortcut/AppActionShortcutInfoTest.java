// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class AppActionShortcutInfoTest {
    @Test
    public void constructorAcceptsValidTarget() {
        AppActionShortcutInfo shortcutInfo = new AppActionShortcutInfo(
                "com.example.app", 10, AppActionShortcutInfo.ACTION_FORCE_STOP);

        assertEquals("com.example.app", shortcutInfo.packageName);
        assertEquals(10, shortcutInfo.userId);
        assertEquals(AppActionShortcutInfo.ACTION_FORCE_STOP, shortcutInfo.action);
        assertEquals("app-action:force_stop:u=10,p=com.example.app", shortcutInfo.getId());
    }

    @Test
    public void constructorRejectsMalformedTarget() {
        assertThrows(IllegalArgumentException.class, () -> new AppActionShortcutInfo(
                "not a package", 10, AppActionShortcutInfo.ACTION_FORCE_STOP));
        assertThrows(IllegalArgumentException.class, () -> new AppActionShortcutInfo(
                "com.example.app", -1, AppActionShortcutInfo.ACTION_FORCE_STOP));
        assertThrows(IllegalArgumentException.class, () -> new AppActionShortcutInfo(
                "com.example.app", 10, "reboot"));
    }
}
