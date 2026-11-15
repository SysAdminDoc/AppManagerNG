// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;

/**
 * The shortcut trampoline is kept out of Recents, and the system marks its intent that way. The target it starts
 * has to get none of that, and the trampoline has to close itself whatever happens.
 */
@RunWith(RobolectricTestRunner.class)
public class ActivityLauncherShortcutActivityTest {
    private static final String PKG = "com.example.app";
    private static final String CLS = "com.example.app.SettingsActivity";
    private static final int START_CLASS_NOT_FOUND = -2;
    private static final int START_ABORTED = 102;

    private final List<Intent> mStarted = new ArrayList<>();
    private final List<Integer> mUsers = new ArrayList<>();
    private Application mApp;
    private boolean mAuthenticatedBefore;
    private ActivityLauncherShortcutActivity.TargetStarter mStarterBefore;
    private int mNightModeBefore;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mAuthenticatedBefore = Ops.isAuthenticated();
        Ops.setAuthenticated(mApp, true);
        mStarterBefore = ActivityLauncherShortcutActivity.sTargetStarter;
        ActivityLauncherShortcutActivity.sTargetStarter = (intent, userId) -> {
            mStarted.add(intent);
            mUsers.add(userId);
            return 0;
        };
        // As in a process that has already shown a screen. The first screen of a fresh process is
        // recreated when its post-create callback applies the night mode. Android skips that for a
        // screen that finished in onCreate, as the trampoline does, but Robolectric recreates it.
        mNightModeBefore = AppCompatDelegate.getDefaultNightMode();
        AppCompatDelegate.setDefaultNightMode(Prefs.Appearance.getNightMode());
    }

    @After
    public void tearDown() {
        AppCompatDelegate.setDefaultNightMode(mNightModeBefore);
        ActivityLauncherShortcutActivity.sTargetStarter = mStarterBefore;
        Ops.setAuthenticated(mApp, mAuthenticatedBefore);
    }

    @Test
    public void theTargetGetsNoneOfTheTrampolinesFlagsOrExtras() {
        Intent target = ActivityLauncherShortcutActivity.getTargetIntent(shortcut()
                .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_HISTORY));

        assertNotNull(target);
        assertEquals(new ComponentName(PKG, CLS), target.getComponent());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP, target.getFlags());
        assertNull(target.getAction());
        assertNull(target.getExtras());
        assertNull(ActivityLauncherShortcutActivity.getTargetIntent(new Intent(Intent.ACTION_CREATE_SHORTCUT)));
        assertNull(ActivityLauncherShortcutActivity.getTargetIntent(shortcut().setAction(Intent.ACTION_MAIN)));
    }

    @Test
    public void aShortcutStartsItsTargetAsItsUserAndCloses() {
        ActivityController<ActivityLauncherShortcutActivity> controller = Robolectric.buildActivity(
                ActivityLauncherShortcutActivity.class,
                shortcut().addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)).setup();

        assertEquals(1, mStarted.size());
        assertEquals(new ComponentName(PKG, CLS), mStarted.get(0).getComponent());
        assertEquals(0, mStarted.get(0).getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        assertEquals(10, (int) mUsers.get(0));
        assertTrue(controller.get().isFinishing());

        // A later shortcut tap reaches the open trampoline and starts its target too
        controller.newIntent(shortcut());
        assertEquals(2, mStarted.size());

        // A later intent that isn't a shortcut closes it again instead of crashing
        controller.newIntent(new Intent());
        assertEquals(2, mStarted.size());
        assertTrue(controller.get().isFinishing());
    }

    @Test
    public void aShortcutTapWhileTheLockIsUpWaitsForTheUnlock() throws Exception {
        ActivityController<ActivityLauncherShortcutActivity> controller = Robolectric.buildActivity(
                ActivityLauncherShortcutActivity.class, shortcut()).setup();
        assertEquals(1, mStarted.size());
        Field pending = BaseActivity.class.getDeclaredField("mAuthenticationPending");
        pending.setAccessible(true);
        pending.setBoolean(controller.get(), true);

        Intent later = shortcut(11);
        controller.newIntent(later);
        assertEquals(1, mStarted.size());
        assertSame(later, controller.get().getIntent());

        pending.setBoolean(controller.get(), false);
        controller.get().onAuthenticated(null);
        assertEquals(2, mStarted.size());
        assertEquals(11, (int) mUsers.get(1));
    }

    @Test
    public void startResultsCountAsStartedOnlyWhenTheTargetIsUpOrQueued() {
        for (int result = 0; result <= 3; ++result) {
            assertTrue(ActivityLauncherShortcutActivity.targetStarted(result, Build.VERSION_CODES.O));
            assertTrue(ActivityLauncherShortcutActivity.targetStarted(result, Build.VERSION_CODES.N_MR1));
        }
        // A canceled app switch is started later by the system
        assertTrue(ActivityLauncherShortcutActivity.targetStarted(100, Build.VERSION_CODES.O));
        assertTrue(ActivityLauncherShortcutActivity.targetStarted(4, Build.VERSION_CODES.N_MR1));
        // Lock task violation and aborted start
        assertFalse(ActivityLauncherShortcutActivity.targetStarted(101, Build.VERSION_CODES.O));
        assertFalse(ActivityLauncherShortcutActivity.targetStarted(102, Build.VERSION_CODES.O));
        assertFalse(ActivityLauncherShortcutActivity.targetStarted(5, Build.VERSION_CODES.N_MR1));
        assertFalse(ActivityLauncherShortcutActivity.targetStarted(START_CLASS_NOT_FOUND, Build.VERSION_CODES.O));
    }

    @Test
    public void anAbortedStartSaysSoAndCloses() {
        ActivityLauncherShortcutActivity.sTargetStarter = (intent, userId) -> START_ABORTED;

        ActivityLauncherShortcutActivity activity = Robolectric.buildActivity(
                ActivityLauncherShortcutActivity.class, shortcut()).setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(activity.isFinishing());
        assertEquals(mApp.getString(R.string.shortcut_target_unavailable), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void aTargetTheSystemRefusesSaysSoAndCloses() {
        ActivityLauncherShortcutActivity.sTargetStarter = (intent, userId) -> START_CLASS_NOT_FOUND;

        ActivityLauncherShortcutActivity activity = Robolectric.buildActivity(
                ActivityLauncherShortcutActivity.class, shortcut()).setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(activity.isFinishing());
        assertEquals(mApp.getString(R.string.shortcut_target_unavailable), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void aTargetThatThrowsSaysSoAndCloses() {
        ActivityLauncherShortcutActivity.sTargetStarter = (intent, userId) -> {
            throw new SecurityException("not exported");
        };

        ActivityLauncherShortcutActivity activity = Robolectric.buildActivity(
                ActivityLauncherShortcutActivity.class, shortcut()).setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(activity.isFinishing());
        assertEquals(mApp.getString(R.string.shortcut_target_unavailable), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void anIntentThatIsntAShortcutClosesWithoutStartingAnything() {
        ActivityLauncherShortcutActivity activity = Robolectric.buildActivity(ActivityLauncherShortcutActivity.class,
                new Intent(mApp, ActivityLauncherShortcutActivity.class)).setup().get();

        assertTrue(activity.isFinishing());
        assertTrue(mStarted.isEmpty());
    }

    @NonNull
    private Intent shortcut() {
        return shortcut(10);
    }

    @NonNull
    private Intent shortcut(int userId) {
        return ActivityLauncherShortcutActivity.getShortcutIntent(mApp, PKG, CLS, userId, false)
                .setAction(Intent.ACTION_CREATE_SHORTCUT);
    }
}
