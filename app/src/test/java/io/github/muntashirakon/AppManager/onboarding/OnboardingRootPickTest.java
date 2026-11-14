// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.onboarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.test.core.app.ApplicationProvider;

import com.topjohnwu.superuser.NoShellException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

/**
 * Picking Root in onboarding after granting it in Magisk kept showing "grant it first", because the
 * refused root shell was cached for the whole process. The shadow stands in for su: its answer
 * changes only once the refused shell has been dropped.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {OnboardingRootPickTest.SuRunnerUtils.class, OnboardingRootPickTest.QueuedThreadUtils.class})
public class OnboardingRootPickTest {
    /** What su says before the refused shell is dropped: null when it's there and refused. */
    static Boolean sGrantBeforeRetry;
    /** What a fresh su says after the drop. */
    static Boolean sGrantAfterRetry;
    static boolean sRetried;
    /** libsu couldn't start any shell. */
    static boolean sNoShell;
    /** Background work the sheet posted, which runs only when a test runs it. */
    static final List<FutureTask<?>> sQueued = new ArrayList<>();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private ActivityController<AppCompatActivity> mController;
    private OnboardingFragment mSheet;
    /** The sheet's own dialog, kept since the fragment lets go of it once dismissed. */
    private Dialog mSheetDialog;

    @Before
    public void setUp() {
        sGrantBeforeRetry = null;
        sGrantAfterRetry = null;
        sRetried = false;
        sNoShell = false;
        sQueued.clear();
        Ops.setMode(Ops.MODE_AUTO);
        AppPref.set(AppPref.PrefKey.PREF_ONBOARDING_SHOWN_BOOL, false);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        sQueued.clear();
        Ops.setMode(Ops.MODE_AUTO);
    }

    @Test
    public void rootGrantedInMagiskSincePickingIsUsed() {
        installMagisk(31100);
        sGrantAfterRetry = true;

        assertEquals(0, OnboardingFragment.checkRootPick(mContext));
    }

    @Test
    public void stillRefusedOnMagisk31_1SaysWhereToGrantIt() {
        installMagisk(31100);

        assertEquals(R.string.onboarding_mode_root_pick_magisk_grant_first,
                OnboardingFragment.checkRootPick(mContext));
    }

    @Test
    public void noSuKeepsTheGenericWarning() {
        sGrantBeforeRetry = false;
        sGrantAfterRetry = false;

        assertEquals(R.string.onboarding_mode_root_pick_without_detection,
                OnboardingFragment.checkRootPick(mContext));
    }

    @Test
    public void noShellAtAllKeepsTheGenericWarning() {
        installMagisk(31100);
        sNoShell = true;

        assertEquals(R.string.onboarding_mode_root_pick_without_detection,
                OnboardingFragment.checkRootPick(mContext));
    }

    @Test
    public void aRootCheckStandsWhenNothingElseWasPicked() {
        OnboardingFragment.RootPicks picks = new OnboardingFragment.RootPicks();

        assertTrue(picks.onPick(true));
        assertTrue(picks.onChecked());
    }

    @Test
    public void aPickAfterRootWinsOverItsSlowCheck() {
        OnboardingFragment.RootPicks picks = new OnboardingFragment.RootPicks();

        assertTrue(picks.onPick(true));
        assertFalse(picks.onPick(false));
        assertFalse(picks.onChecked());
    }

    @Test
    public void rootTappedAgainWhileCheckingSharesTheCheck() {
        OnboardingFragment.RootPicks picks = new OnboardingFragment.RootPicks();

        assertTrue(picks.onPick(true));
        assertFalse(picks.onPick(false));
        assertFalse(picks.onPick(true));
        assertTrue(picks.onChecked());
    }

    @Test
    public void aFinishedCheckLetsTheNextRootTapCheckAgain() {
        OnboardingFragment.RootPicks picks = new OnboardingFragment.RootPicks();
        assertTrue(picks.onPick(true));
        assertFalse(picks.onPick(false));
        assertFalse(picks.onChecked());

        assertTrue(picks.onPick(true));
        assertTrue(picks.onChecked());
    }

    @Test
    public void aRootTapWithNothingElsePickedCommitsRoot() {
        sGrantAfterRetry = true;
        showSheet();

        answer(tapRoot());

        assertEquals(Ops.MODE_ROOT, savedMode());
        assertFalse(mSheetDialog.isShowing());
        assertFalse(mSheet.isAdded());
    }

    @Test
    public void wirelessAdbPickedDuringTheRootCheckWins() {
        sGrantAfterRetry = true;
        showSheet();
        FutureTask<?> check = tapRoot();

        // Not paired, so the sheet stays up behind the setup prompt
        tap(R.id.card_mode_adb_wifi);
        Dialog prompt = ShadowDialog.getLatestDialog();
        assertNotSame(mSheetDialog, prompt);
        assertTrue(prompt.isShowing());
        answer(check);

        assertEquals(Ops.MODE_AUTO, savedMode());
        assertTrue(mSheetDialog.isShowing());
    }

    @Test
    public void wirelessSetupOpenedDuringTheRootCheckWins() {
        sGrantAfterRetry = true;
        showSheet();
        FutureTask<?> check = tapRoot();

        // USB debugging is off, so setup asks about it first and the sheet stays up
        tap(R.id.action_setup_adb_wifi);
        Dialog usbDebugging = ShadowDialog.getLatestDialog();
        assertNotSame(mSheetDialog, usbDebugging);
        assertTrue(usbDebugging.isShowing());
        answer(check);

        assertEquals(Ops.MODE_AUTO, savedMode());
        assertTrue(mSheetDialog.isShowing());
        assertTrue(usbDebugging.isShowing());
    }

    @Test
    public void anExplainerOpenWhenRootCommitsClosesWithTheSheet() {
        sGrantAfterRetry = true;
        showSheet();
        FutureTask<?> check = tapRoot();

        // Opening an explainer is not a pick, so the Root answer still stands
        assertTrue(mSheet.requireView().findViewById(R.id.card_mode_shizuku).performLongClick());
        ShadowLooper.idleMainLooper();
        Dialog explainer = ShadowDialog.getLatestDialog();
        assertNotSame(mSheetDialog, explainer);
        assertTrue(explainer.isShowing());
        answer(check);

        assertEquals(Ops.MODE_ROOT, savedMode());
        assertFalse(mSheetDialog.isShowing());
        assertFalse(mSheet.isAdded());
        assertFalse(explainer.isShowing());
    }

    @Test
    public void aRootAnswerArrivingAsTheSheetIsCancelledIsDropped() {
        sGrantAfterRetry = true;
        showSheet();
        FutureTask<?> check = tapRoot();
        check.run();

        // The answer is queued on the main thread, and the sheet is cancelled before it runs
        mSheetDialog.cancel();
        ShadowLooper.idleMainLooper();

        assertEquals(Ops.MODE_AUTO, savedMode());
        assertFalse(mSheetDialog.isShowing());
    }

    private void showSheet() {
        shadowOf((Application) mContext).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        mController = Robolectric.buildActivity(AppCompatActivity.class);
        mController.get().setTheme(R.style.AppTheme_V2);
        mController.setup();
        mSheet = new OnboardingFragment();
        mSheet.showNow(mController.get().getSupportFragmentManager(), OnboardingFragment.TAG);
        ShadowLooper.idleMainLooper();
        mSheetDialog = mSheet.requireDialog();
        assertTrue(mSheetDialog.isShowing());
    }

    private void tap(@IdRes int id) {
        assertTrue(mSheet.requireView().findViewById(id).performClick());
        ShadowLooper.idleMainLooper();
    }

    /** Taps Root and hands back its su check, which has not run yet. */
    @NonNull
    private FutureTask<?> tapRoot() {
        int before = sQueued.size();
        tap(R.id.card_mode_root);
        assertEquals(before + 1, sQueued.size());
        return sQueued.get(before);
    }

    /** Runs the su check, then lets the sheet handle its answer. */
    private static void answer(@NonNull FutureTask<?> check) {
        check.run();
        ShadowLooper.idleMainLooper();
    }

    @NonNull
    private static String savedMode() {
        return AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
    }

    private void installMagisk(long versionCode) {
        PackageInfo info = new PackageInfo();
        info.packageName = "com.topjohnwu.magisk";
        info.setLongVersionCode(versionCode);
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = info.packageName;
        shadowOf(mContext.getPackageManager()).installPackage(info);
    }

    @Implements(RunnerUtils.class)
    public static class SuRunnerUtils {
        @Implementation
        protected static Boolean isAppGrantedRoot() {
            if (sNoShell) {
                throw new NoShellException("Unable to create a shell!");
            }
            return sRetried ? sGrantAfterRetry : sGrantBeforeRetry;
        }

        @Implementation
        protected static void retryRootGrant() {
            sRetried = true;
        }

        @Implementation
        protected static Long getMagiskSuVersionCode() {
            return null;
        }
    }

    @Implements(ThreadUtils.class)
    public static class QueuedThreadUtils {
        @Implementation
        protected static Future<?> postOnBackgroundThread(Runnable runnable) {
            FutureTask<?> task = new FutureTask<>(runnable, null);
            sQueued.add(task);
            return task;
        }

        @Implementation
        protected static <T> Future<T> postOnBackgroundThread(Callable<T> callable) {
            FutureTask<T> task = new FutureTask<>(callable);
            sQueued.add(task);
            return task;
        }
    }
}
