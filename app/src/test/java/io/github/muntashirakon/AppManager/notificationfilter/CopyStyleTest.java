// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Notification;

import androidx.annotation.NonNull;

import org.junit.Test;

import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Action;
import io.github.muntashirakon.AppManager.notificationfilter.NotificationFilterRule.Priority;

/** #32: what each rule priority does to a copy. */
@SuppressWarnings("deprecation")
public class CopyStyleTest {
    private static void assertStyle(@NonNull CopyStyle style, @NonNull String channel, int legacyPriority,
                                    @NonNull String sortKey, boolean whenIsNow) {
        assertEquals(channel, style.channel);
        assertEquals(legacyPriority, style.legacyPriority);
        assertEquals(sortKey, style.sortKey);
        assertEquals(whenIsNow, style.whenIsNow);
    }

    @Test
    public void silentCopiesMoveBetweenThreeImportanceTiers() {
        for (Action action : new Action[]{Action.SILENT, Action.PIN}) {
            assertStyle(CopyStyle.of(action, Priority.HIGH), NotificationReposter.CHANNEL_ICON,
                    Notification.PRIORITY_HIGH, "0", true);
            assertStyle(CopyStyle.of(action, Priority.NORMAL), NotificationReposter.CHANNEL_SILENT,
                    Notification.PRIORITY_LOW, "1", false);
            assertStyle(CopyStyle.of(action, Priority.LOW), NotificationReposter.CHANNEL_SILENT_LOW,
                    Notification.PRIORITY_MIN, "2", false);
        }
    }

    @Test
    public void keepIconStaysWhereTheStatusBarShowsIt() {
        assertStyle(CopyStyle.of(Action.KEEP_ICON, Priority.HIGH), NotificationReposter.CHANNEL_ICON,
                Notification.PRIORITY_HIGH, "0", true);
        assertStyle(CopyStyle.of(Action.KEEP_ICON, Priority.NORMAL), NotificationReposter.CHANNEL_ICON,
                Notification.PRIORITY_DEFAULT, "1", false);
        // Below Android 8 the minimum priority hides the icon, so low stops at low.
        assertStyle(CopyStyle.of(Action.KEEP_ICON, Priority.LOW), NotificationReposter.CHANNEL_ICON,
                Notification.PRIORITY_LOW, "2", false);
    }

    @Test
    public void vibrateKeepsItsChannelAndNeverPopsUp() {
        // A high priority that vibrates would pop up before Android 8, so it stops at default.
        assertStyle(CopyStyle.of(Action.VIBRATE, Priority.HIGH), NotificationReposter.CHANNEL_VIBRATE,
                Notification.PRIORITY_DEFAULT, "0", true);
        assertStyle(CopyStyle.of(Action.VIBRATE, Priority.NORMAL), NotificationReposter.CHANNEL_VIBRATE,
                Notification.PRIORITY_DEFAULT, "1", false);
        assertStyle(CopyStyle.of(Action.VIBRATE, Priority.LOW), NotificationReposter.CHANNEL_VIBRATE,
                Notification.PRIORITY_LOW, "2", false);
    }

    @Test
    public void higherPrioritySortsFirst() {
        for (Action action : new Action[]{Action.SILENT, Action.VIBRATE, Action.KEEP_ICON, Action.PIN}) {
            String high = CopyStyle.of(action, Priority.HIGH).sortKey;
            String normal = CopyStyle.of(action, Priority.NORMAL).sortKey;
            String low = CopyStyle.of(action, Priority.LOW).sortKey;
            assertTrue(action.name(), high.compareTo(normal) < 0);
            assertTrue(action.name(), normal.compareTo(low) < 0);
            assertTrue(CopyStyle.of(action, Priority.HIGH).whenIsNow);
            assertFalse(CopyStyle.of(action, Priority.NORMAL).whenIsNow);
        }
    }
}
