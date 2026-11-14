// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.R;

/**
 * A recreated Settings has to reopen the page that was on screen. Android recreates it after a
 * language change, after the process is killed in the background (as it is once the first
 * privileged mode switch gets its log access), and when a tablet or foldable changes width.
 */
@RunWith(RobolectricTestRunner.class)
public class SettingsActivityRecreateTest {
    private static final String DUAL_PANE = "w900dp-h600dp-land";
    private static final String SINGLE_PANE = "w400dp-h800dp-port";

    private ActivityController<SettingsActivity> mController;

    @Before
    public void setUp() {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
        mController = Robolectric.buildActivity(SettingsActivity.class);
    }

    @After
    public void tearDown() {
        mController.pause().stop().destroy();
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
    }

    @Test
    public void theModeOfOperationPageComesBack() {
        assertReopensAfterRecreate("mode_of_operations", ModeOfOpsPreference.class);
    }

    @Test
    public void theLanguageListComesBack() {
        assertReopensAfterRecreate("custom_locale", ChangeLanguageFragment.class);
    }

    @Test
    public void aPreferencePageStillComesBack() {
        assertReopensAfterRecreate("privacy_prefs", PrivacyPreferences.class);
    }

    @Test
    public void aDialogOnTheWayDoesNotLoseTheNestedPage() {
        mController.setup();
        idle();
        open(R.id.main_layout, "appearance_prefs", AppearancePreferences.class);
        DialogFragment dialog = new DialogFragment();
        dialog.show(mController.get().getSupportFragmentManager(), "test");
        idle();
        dialog.dismiss();
        idle();
        open(R.id.main_layout, "language_files", LanguagePacksPreferences.class);

        mController.recreate();
        idle();

        assertShows(R.id.main_layout, LanguagePacksPreferences.class);
    }

    @Test
    public void twoRecreatesInARowKeepTheNestedPage() {
        // Choosing a language recreates Settings twice
        mController.setup();
        idle();
        open(R.id.main_layout, "appearance_prefs", AppearancePreferences.class);
        open(R.id.main_layout, "language_files", LanguagePacksPreferences.class);

        mController.recreate();
        idle();
        mController.recreate();
        idle();

        assertShows(R.id.main_layout, LanguagePacksPreferences.class);
    }

    @Test
    public void aLinkedPageClosedWithBackStaysClosedAfterARecreate() {
        mController = Robolectric.buildActivity(SettingsActivity.class, SettingsActivity.getSettingsIntent(
                ApplicationProvider.getApplicationContext(), "appearance_prefs", "language_files"));
        mController.setup();
        idle();
        assertShows(R.id.main_layout, LanguagePacksPreferences.class);
        mController.get().getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertShows(R.id.main_layout, AppearancePreferences.class);

        mController.recreate();
        idle();

        assertShows(R.id.main_layout, AppearancePreferences.class);
    }

    @Test
    public void aLinkRotatedBehindTheLockScreenStillOpens() {
        Intent link = SettingsActivity.getSettingsIntent(
                ApplicationProvider.getApplicationContext(), "appearance_prefs", "language_files");
        Bundle state = recreateLocked(link, null, null);

        unlockWith(link, state);

        assertShows(R.id.main_layout, LanguagePacksPreferences.class);
    }

    @Test
    public void aPageLeftWithBackStaysClosedWhenRotatedBehindTheLockScreen() {
        // The process stopped in the background after Back, so the lock screen comes back first
        Intent link = SettingsActivity.getSettingsIntent(
                ApplicationProvider.getApplicationContext(), "appearance_prefs", "language_files");
        ActivityController<SettingsActivity> unlocked = Robolectric.buildActivity(SettingsActivity.class, link).setup();
        Bundle state = new Bundle();
        try {
            idle();
            unlocked.get().getOnBackPressedDispatcher().onBackPressed();
            idle();
            assertTrue(pageIn(unlocked, R.id.main_layout) instanceof AppearancePreferences);
            unlocked.pause().saveInstanceState(state);
        } finally {
            unlocked.stop().destroy();
        }

        state = recreateLocked(link, state, null);
        unlockWith(link, state);

        assertShows(R.id.main_layout, AppearancePreferences.class);
    }

    @Test
    public void aLinkSentWhileLockedOpensAfterARotation() {
        Intent plain = new Intent(ApplicationProvider.getApplicationContext(), SettingsActivity.class);
        Bundle state = recreateLocked(plain, null, SettingsActivity.getSettingsIntent(
                ApplicationProvider.getApplicationContext(), "privacy_prefs"));

        unlockWith(plain, state);

        assertShows(R.id.main_layout, PrivacyPreferences.class);
    }

    @Test
    @Config(qualifiers = DUAL_PANE)
    public void dualPaneTappingAParentAgainShowsTheParent() {
        mController.setup();
        idle();
        open(R.id.main_layout, "appearance_prefs", AppearancePreferences.class, R.id.secondary_layout);
        open(R.id.secondary_layout, "language_files", LanguagePacksPreferences.class, R.id.secondary_layout);

        open(R.id.main_layout, "appearance_prefs", AppearancePreferences.class, R.id.secondary_layout);
        mController.recreate();
        idle();

        assertShows(R.id.secondary_layout, AppearancePreferences.class);
    }

    @Test
    @Config(qualifiers = DUAL_PANE)
    public void dualPaneModeOfOperationComesBack() {
        mController.setup();
        idle();
        assertShows(R.id.secondary_layout, ChangeLanguageFragment.class);
        open(R.id.main_layout, "mode_of_operations", ModeOfOpsPreference.class, R.id.secondary_layout);

        mController.recreate();
        idle();

        assertShows(R.id.secondary_layout, ModeOfOpsPreference.class);
    }

    @Test
    @Config(qualifiers = DUAL_PANE)
    public void dualPaneNestedPageComesBack() {
        mController.setup();
        idle();
        open(R.id.main_layout, "appearance_prefs", AppearancePreferences.class, R.id.secondary_layout);
        open(R.id.secondary_layout, "language_files", LanguagePacksPreferences.class, R.id.secondary_layout);

        mController.recreate();
        idle();

        assertShows(R.id.secondary_layout, LanguagePacksPreferences.class);
    }

    @Test
    @Config(qualifiers = DUAL_PANE)
    public void dualPaneComingBackFromHomeKeepsThePagePicked() {
        mController.setup();
        idle();
        open(R.id.main_layout, "privacy_prefs", PrivacyPreferences.class, R.id.secondary_layout);

        mController.pause().stop();
        idle();
        mController.restart().start().resume();
        idle();

        assertShows(R.id.secondary_layout, PrivacyPreferences.class);
    }

    @Test
    @Config(qualifiers = DUAL_PANE)
    public void narrowingKeepsThePagePicked() {
        mController.setup();
        idle();
        open(R.id.main_layout, "privacy_prefs", PrivacyPreferences.class, R.id.secondary_layout);

        RuntimeEnvironment.setQualifiers(SINGLE_PANE);
        mController.configurationChange();
        idle();

        assertShows(R.id.main_layout, PrivacyPreferences.class);
    }

    @Test
    @Config(qualifiers = DUAL_PANE)
    public void narrowingBeforeAnyPickShowsTheList() {
        mController.setup();
        idle();
        assertShows(R.id.secondary_layout, ChangeLanguageFragment.class);

        RuntimeEnvironment.setQualifiers(SINGLE_PANE);
        mController.configurationChange();
        idle();

        assertShows(R.id.main_layout, MainPreferences.class);
    }

    /**
     * Creates Settings behind the lock screen (from {@code state} when it's a second recreate),
     * hands it {@code newIntent} if there is one, and recreates it before the lock screen is passed.
     */
    private Bundle recreateLocked(Intent intent, @Nullable Bundle state, @Nullable Intent newIntent) {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), false);
        ActivityController<SettingsActivity> locked = Robolectric.buildActivity(SettingsActivity.class, intent);
        try {
            if (state == null) {
                locked.create().start();
            } else {
                locked.create(state).start().restoreInstanceState(state);
            }
            if (newIntent != null) {
                locked.newIntent(newIntent);
            }
            // Its old pages come back with the fragment manager even now, but not its layout
            assertNull("the lock screen was passed", locked.get().findViewById(R.id.main_layout));
            Bundle saved = new Bundle();
            locked.saveInstanceState(saved);
            return saved;
        } finally {
            locked.stop().destroy();
        }
    }

    private void unlockWith(Intent intent, Bundle state) {
        Ops.setAuthenticated(ApplicationProvider.getApplicationContext(), true);
        mController = Robolectric.buildActivity(SettingsActivity.class, intent).setup(state);
        idle();
    }

    private void assertReopensAfterRecreate(String key, Class<? extends Fragment> page) {
        mController.setup();
        idle();
        open(R.id.main_layout, key, page);

        mController.recreate();
        idle();

        assertShows(R.id.main_layout, page);
    }

    private void open(@IdRes int from, String key, Class<? extends Fragment> page) {
        open(from, key, page, R.id.main_layout);
    }

    private void open(@IdRes int from, String key, Class<? extends Fragment> page, @IdRes int to) {
        Fragment caller = pageIn(from);
        assertTrue("no preference list in the pane: " + caller, caller instanceof PreferenceFragmentCompat);
        Preference pref = ((PreferenceFragmentCompat) caller).findPreference(key);
        assertNotNull(key, pref);
        pref.performClick();
        idle();
        assertShows(to, page);
    }

    private void assertShows(@IdRes int pane, Class<? extends Fragment> page) {
        Fragment shown = pageIn(pane);
        assertTrue("showed " + shown + " instead of " + page.getSimpleName(), page.isInstance(shown));
    }

    private Fragment pageIn(@IdRes int pane) {
        return pageIn(mController, pane);
    }

    private static Fragment pageIn(ActivityController<SettingsActivity> controller, @IdRes int pane) {
        return controller.get().getSupportFragmentManager().findFragmentById(pane);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }
}
