// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.BooleanSupplier;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.topapp.TopAppTransition.Kind;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class TopAppHistoryActivityTest {
    private static final String APP = "com.example.app";
    private static final String AD = "com.example.ad";

    @Rule
    public final TemporaryFolder mFolder = new TemporaryFolder();

    private Application mApp;
    private TopAppHistory mHistory;
    private ActivityController<TopAppHistoryActivity> mController;
    private boolean mAuthenticatedBefore;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mAuthenticatedBefore = Ops.isAuthenticated();
        Ops.setAuthenticated(mApp, true);
        mHistory = new TopAppHistory(new File(mFolder.getRoot(), TopAppHistory.FILE_NAME), Runnable::run);
        TopAppHistory.setInstanceForTesting(mHistory);
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        Prefs.Privacy.setTopAppHistoryEnabled(false);
        TopAppHistory.setInstanceForTesting(null);
        Ops.setAuthenticated(mApp, mAuthenticatedBefore);
    }

    @NonNull
    private TopAppHistoryActivity open() {
        mController = Robolectric.buildActivity(TopAppHistoryActivity.class).setup();
        ShadowLooper.idleMainLooper();
        return mController.get();
    }

    private static void waitFor(@NonNull BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!condition.getAsBoolean()) {
            assertTrue("timed out", System.currentTimeMillis() < deadline);
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static void waitForRows(@NonNull TopAppHistoryActivity activity, int rows) {
        waitFor(() -> activity.getVisibleEntries().size() == rows);
    }

    @NonNull
    private String waitForCopy() {
        waitFor(() -> mApp.getString(R.string.copied_to_clipboard).equals(ShadowToast.getTextOfLatestToast()));
        ClipboardManager clipboard = (ClipboardManager) mApp.getSystemService(Context.CLIPBOARD_SERVICE);
        return clipboard.getPrimaryClip().getItemAt(0).getText().toString();
    }

    private void install(@NonNull String packageName, @NonNull String label) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = packageName;
        info.applicationInfo.nonLocalizedLabel = label;
        shadowOf(mApp.getPackageManager()).installPackage(info);
    }

    /** An app came up, then an ad was drawn over it. */
    private void recordAnAdOverAnApp() {
        mHistory.record(Collections.singletonList(new TopAppTransition(1_000L, Kind.APP, APP, null)));
        mHistory.record(Collections.singletonList(new TopAppTransition(2_000L, Kind.OVERLAY_SHOWN, AD, APP)));
    }

    @Test
    public void recordingStartsOffAndTheEmptyScreenTurnsItOn() {
        TopAppHistoryActivity activity = open();
        MaterialSwitch toggle = activity.findViewById(R.id.record_switch);
        assertFalse(toggle.isChecked());
        assertEquals(mApp.getString(R.string.top_app_history_empty_off), text(activity, R.id.empty_state_summary));
        assertEquals(mApp.getString(R.string.top_app_history_turn_on), text(activity, R.id.empty_state_action));

        activity.findViewById(R.id.empty_state_action).performClick();

        assertTrue(Prefs.Privacy.isTopAppHistoryEnabled());
        assertTrue(toggle.isChecked());
        // The bubble isn't running, so that's the next thing to do.
        assertEquals(mApp.getString(R.string.top_app_history_bubble_off), text(activity, R.id.empty_state_summary));
        assertEquals(mApp.getString(R.string.top_app_history_start_bubble),
                text(activity, R.id.empty_state_action));
    }

    @Test
    public void theStartButtonOpensWhatTheBubbleNeeds() {
        Prefs.Privacy.setTopAppHistoryEnabled(true);
        TopAppHistoryActivity activity = open();

        activity.findViewById(R.id.empty_state_action).performClick();

        assertNotNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void turningItOffKeepsWhatWasSaved() {
        Prefs.Privacy.setTopAppHistoryEnabled(true);
        recordAnAdOverAnApp();
        TopAppHistoryActivity activity = open();
        waitForRows(activity, 2);

        ((MaterialSwitch) activity.findViewById(R.id.record_switch)).setChecked(false);

        assertFalse(Prefs.Privacy.isTopAppHistoryEnabled());
        assertEquals(2, activity.getVisibleEntries().size());
    }

    @Test
    public void rowsSayWhatHappenedNewestFirst() {
        install(AD, "Ad Maker");
        recordAnAdOverAnApp();
        TopAppHistoryActivity activity = open();
        waitForRows(activity, 2);
        RecyclerView list = activity.findViewById(android.R.id.list);
        layOut(list);

        View first = list.getChildAt(0);
        assertEquals(mApp.getString(R.string.top_app_history_overlay_over, "Ad Maker", APP), text(first, R.id.text));
        assertEquals(AD + " / " + APP, text(first, R.id.package_name));
        assertEquals(mApp.getString(R.string.top_app_history_came_up, APP), text(list.getChildAt(1), R.id.text));
    }

    @Test
    public void aNewChangeShowsUpWhileTheScreenIsOpen() {
        TopAppHistoryActivity activity = open();

        mHistory.record(Collections.singletonList(new TopAppTransition(1_000L, Kind.APP, APP, null)));

        waitForRows(activity, 1);
    }

    @Test
    public void clearingIsImmediateAndOffersUndo() {
        recordAnAdOverAnApp();
        TopAppHistoryActivity activity = open();
        waitForRows(activity, 2);

        shadowOf(activity).clickMenuItem(R.id.action_clear);
        ShadowLooper.idleMainLooper();

        waitForRows(activity, 0);
        assertEquals(mApp.getString(R.string.top_app_history_cleared),
                text(activity, com.google.android.material.R.id.snackbar_text));

        activity.findViewById(com.google.android.material.R.id.snackbar_action).performClick();

        waitForRows(activity, 2);
        assertEquals(Arrays.asList(new TopAppTransition(2_000L, Kind.OVERLAY_SHOWN, AD, APP),
                new TopAppTransition(1_000L, Kind.APP, APP, null)), activity.getVisibleEntries());
    }

    @Test
    public void tappingARowCopiesIt() {
        recordAnAdOverAnApp();
        TopAppHistoryActivity activity = open();
        waitForRows(activity, 2);
        RecyclerView list = activity.findViewById(android.R.id.list);
        layOut(list);

        list.getChildAt(0).performClick();

        String copied = waitForCopy();
        assertTrue(copied, copied.contains(mApp.getString(R.string.top_app_history_overlay_over, AD, APP)));
        assertFalse(copied, copied.contains(mApp.getString(R.string.top_app_history_came_up, APP)));
    }

    @Test
    public void copyAllCopiesEveryRow() {
        recordAnAdOverAnApp();
        TopAppHistoryActivity activity = open();
        waitForRows(activity, 2);

        shadowOf(activity).clickMenuItem(R.id.action_copy_all);

        String copied = waitForCopy();
        assertEquals(copied, 2, copied.split("\n").length);
    }

    @Test
    public void copyingNothingSaysSo() {
        TopAppHistoryActivity activity = open();

        shadowOf(activity).clickMenuItem(R.id.action_copy_all);

        assertEquals(mApp.getString(R.string.top_app_history_nothing_to_copy), ShadowToast.getTextOfLatestToast());
    }

    private static void layOut(@NonNull RecyclerView list) {
        ShadowLooper.idleMainLooper();
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1920);
    }

    @NonNull
    private static String text(@NonNull View row, int id) {
        return ((TextView) row.findViewById(id)).getText().toString();
    }

    @NonNull
    private static String text(@NonNull TopAppHistoryActivity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
}
