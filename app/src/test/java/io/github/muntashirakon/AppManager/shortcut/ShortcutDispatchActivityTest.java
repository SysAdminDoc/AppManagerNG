// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import io.github.muntashirakon.AppManager.settings.SettingsActivity;

/**
 * The trampoline is exported and takes no permission, so any installed app can launch it. The scheduled
 * backup action must only open the review screen; queueing takes the user's tap there.
 */
@RunWith(RobolectricTestRunner.class)
public class ShortcutDispatchActivityTest {
    @Test
    public void scheduledBackupShortcutOpensTheReviewScreenAndQueuesNothing() {
        Intent started = launch(new Intent(ShortcutDispatchActivity.ACTION_RUN_SCHEDULED_BACKUP));

        assertReviewScreen(started);
    }

    @Test
    public void extrasFromAnotherAppAreNotForwarded() {
        Intent hostile = new Intent(ShortcutDispatchActivity.ACTION_RUN_SCHEDULED_BACKUP)
                .putExtra("manual", true)
                .putExtra("origin", "settings")
                .putExtra("auth", "guess");

        Intent started = launch(hostile);

        assertReviewScreen(started);
        assertTrue(started.getExtras() == null || started.getExtras().isEmpty());
    }

    @Test
    public void unknownActionsStartNothing() {
        ShortcutDispatchActivity activity = Robolectric.buildActivity(ShortcutDispatchActivity.class,
                new Intent("io.github.muntashirakon.AppManager.shortcut.action.RUN_BACKUP_NOW")).create().get();

        assertNull(shadowOf(activity).getNextStartedActivity());
        assertTrue(activity.isFinishing());
    }

    private static Intent launch(Intent intent) {
        ShortcutDispatchActivity activity = Robolectric.buildActivity(ShortcutDispatchActivity.class, intent)
                .create().get();
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull("the shortcut should open a screen", started);
        assertNull("the shortcut should open exactly one screen", shadowOf(activity).getNextStartedActivity());
        assertTrue(activity.isFinishing());
        return started;
    }

    private static void assertReviewScreen(Intent started) {
        assertEquals(SettingsActivity.class.getName(), started.getComponent().getClassName());
        assertFalse(AutoBackupShortcutActivity.class.getName().equals(started.getComponent().getClassName()));
        assertNotNull(started.getData());
        assertEquals(Arrays.asList("backup_restore_prefs", "backup_schedule_run_now"),
                started.getData().getPathSegments());
        assertNull(started.getAction());
    }
}
