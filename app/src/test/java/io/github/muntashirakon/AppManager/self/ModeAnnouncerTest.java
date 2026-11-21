// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;

/**
 * #56: "Working through Shizuku shell mode" kept popping up over other apps, because every mode
 * check toasted, background ones included.
 */
@RunWith(RobolectricTestRunner.class)
public class ModeAnnouncerTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        ModeAnnouncer.reset();
        ShadowToast.reset();
        AppPref.set(AppPref.PrefKey.PREF_MODE_TOAST_BOOL, true);
    }

    @After
    public void tearDown() {
        ModeAnnouncer.reset();
        AppPref.set(AppPref.PrefKey.PREF_MODE_TOAST_BOOL, true);
    }

    private static void announceShizuku() {
        ModeAnnouncer.announce(R.string.working_on_shizuku_mode, false);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void theToastIsOnByDefault() {
        assertTrue(AppPref.getBoolean(AppPref.PrefKey.PREF_MODE_TOAST_BOOL));
    }

    @Test
    public void nothingIsShownWithNoScreenOfTheAppStarted() {
        announceShizuku();

        assertEquals(0, ShadowToast.shownToastCount());
    }

    @Test
    public void aStartedScreenGetsTheModeOnce() {
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        try {
            announceShizuku();
            announceShizuku();
            announceShizuku();

            assertEquals(1, ShadowToast.shownToastCount());
            assertEquals(mContext.getString(R.string.working_on_shizuku_mode), ShadowToast.getTextOfLatestToast());
        } finally {
            screen.pause().stop().destroy();
        }
    }

    @Test
    public void backgroundChecksAfterTheScreenClosesStaySilent() {
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        screen.pause().stop();
        assertEquals(0, ModeAnnouncer.startedActivities());

        ModeAnnouncer.announce(R.string.working_on_shizuku_root_mode, false);
        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, ShadowToast.shownToastCount());
        screen.destroy();
    }

    @Test
    public void aNewModeIsAnnouncedAgain() {
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        try {
            announceShizuku();
            ModeAnnouncer.announce(R.string.working_on_adb_mode, false);
            shadowOf(Looper.getMainLooper()).idle();

            assertEquals(2, ShadowToast.shownToastCount());
            assertEquals(mContext.getString(R.string.working_on_adb_mode), ShadowToast.getTextOfLatestToast());
        } finally {
            screen.pause().stop().destroy();
        }
    }

    @Test
    public void changingTheModeSettingLetsTheSameModeBeAnnouncedAgain() {
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        try {
            announceShizuku();
            ModeAnnouncer.forget();
            announceShizuku();

            assertEquals(2, ShadowToast.shownToastCount());
        } finally {
            screen.pause().stop().destroy();
        }
    }

    @Test
    public void theSwitchTurnsItOff() {
        AppPref.set(AppPref.PrefKey.PREF_MODE_TOAST_BOOL, false);
        ActivityController<Activity> screen = Robolectric.buildActivity(Activity.class).setup();
        try {
            announceShizuku();

            assertEquals(0, ShadowToast.shownToastCount());
            assertNull(ShadowToast.getLatestToast());
            assertFalse(ModeAnnouncer.shouldAnnounce(R.string.working_on_shizuku_mode));
        } finally {
            screen.pause().stop().destroy();
        }
    }
}
