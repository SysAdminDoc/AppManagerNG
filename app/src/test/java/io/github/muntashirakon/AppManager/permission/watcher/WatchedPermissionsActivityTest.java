// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static io.github.muntashirakon.AppManager.permission.watcher.WatchTestDb.appOp;
import static io.github.muntashirakon.AppManager.permission.watcher.WatchTestDb.permission;

import android.app.Application;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;

@RunWith(RobolectricTestRunner.class)
public class WatchedPermissionsActivityTest {
    private static final String PKG = "com.example.app";
    private static final String CAMERA = "android.permission.CAMERA";

    private Application mApp;
    private PermissionWatchDb mDb;
    private RecordingWorkSink mSink;
    private ActivityController<WatchedPermissionsActivity> mController;
    private boolean mAuthenticatedBefore;
    private boolean mEnforceBefore;

    @Before
    public void setUp() {
        mApp = ApplicationProvider.getApplicationContext();
        mAuthenticatedBefore = Ops.isAuthenticated();
        mEnforceBefore = Prefs.Privacy.isPermissionWatcherEnforceEnabled();
        Ops.setAuthenticated(mApp, true);
        mDb = WatchTestDb.install(mApp);
        mSink = RecordingWorkSink.install();
    }

    @After
    public void tearDown() {
        if (mController != null) {
            mController.pause().stop().destroy();
        }
        RecordingWorkSink.uninstall();
        WatchTestDb.uninstall(mDb);
        Prefs.Privacy.setPermissionWatcherEnforceEnabled(mEnforceBefore);
        Ops.setAuthenticated(mApp, mAuthenticatedBefore);
    }

    @NonNull
    private WatchedPermissionsActivity open() {
        mController = Robolectric.buildActivity(WatchedPermissionsActivity.class).setup();
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

    private static void waitForRows(@NonNull WatchedPermissionsActivity activity, int rows) {
        waitFor(() -> activity.getRules().size() == rows);
    }

    @Test
    public void theRulesDefaultToEnforcing() {
        assertTrue(Prefs.Privacy.isPermissionWatcherEnforceEnabled());
    }

    @Test
    public void withNoRulesTheScreenSaysHowToAddOne() {
        WatchedPermissionsActivity activity = open();

        assertEquals(mApp.getString(R.string.permission_watcher_empty_title), text(activity, R.id.empty_state_title));
        assertEquals(mApp.getString(R.string.permission_watcher_empty_summary),
                text(activity, R.id.empty_state_summary));
        assertEquals(View.GONE, activity.findViewById(R.id.banner).getVisibility());
    }

    @Test
    public void eachRowShowsWhatIsWatchedAndHow() {
        mDb.ruleDao().upsertAll(Arrays.asList(
                permission(PKG, 0, CAMERA, WatchRule.STATE_DENY),
                appOp(PKG, 0, 26, "CAMERA", WatchRule.STATE_IGNORE)));
        WatchedPermissionsActivity activity = open();
        waitForRows(activity, 2);
        RecyclerView list = activity.findViewById(android.R.id.list);
        layOut(list);

        View permissionRow = list.getChildAt(0);
        assertEquals("CAMERA", text(permissionRow, R.id.name));
        assertEquals(mApp.getString(R.string.permission_watcher_state_deny), text(permissionRow, R.id.state));
        assertEquals(PKG, text(permissionRow, R.id.package_name));
        assertEquals("an app that isn't installed shows its package name", PKG, text(permissionRow, R.id.label));
        View opRow = list.getChildAt(1);
        assertEquals(mApp.getString(R.string.permission_watcher_app_op, "CAMERA"), text(opRow, R.id.name));
        assertEquals(mApp.getString(R.string.permission_watcher_state_ignore), text(opRow, R.id.state));
    }

    @Test
    public void removingIsImmediateAndUndoPutsItBack() {
        WatchRule rule = permission(PKG, 0, CAMERA, WatchRule.STATE_DENY);
        mDb.ruleDao().upsert(rule);
        WatchedPermissionsActivity activity = open();
        waitForRows(activity, 1);
        RecyclerView list = activity.findViewById(android.R.id.list);
        layOut(list);

        list.getChildAt(0).findViewById(R.id.remove).performClick();

        waitForRows(activity, 0);
        assertEquals(0, mDb.ruleDao().count());
        assertEquals(mApp.getString(R.string.permission_watcher_rule_removed, "CAMERA"),
                text(activity, com.google.android.material.R.id.snackbar_text));

        activity.findViewById(com.google.android.material.R.id.snackbar_action).performClick();

        waitForRows(activity, 1);
        assertEquals(rule, mDb.ruleDao().find(PKG, 0, WatchRule.TYPE_PERMISSION, CAMERA));
    }

    @Test
    public void changingARulesStateSavesItAndChecksThatApp() {
        WatchRule rule = permission(PKG, 10, CAMERA, WatchRule.STATE_DENY);
        mDb.ruleDao().upsert(rule);
        WatchedPermissionsActivity activity = open();
        waitForRows(activity, 1);

        new ViewModelProvider(activity).get(WatchedPermissionsViewModel.class).setState(rule, WatchRule.STATE_ALLOW);

        waitFor(() -> activity.getRules().get(0).state == WatchRule.STATE_ALLOW);
        assertEquals(WatchRule.STATE_ALLOW, mDb.ruleDao().find(PKG, 10, WatchRule.TYPE_PERMISSION, CAMERA).state);
        waitFor(() -> !mSink.enqueuedNames.isEmpty());
        assertEquals(PermissionWatchScheduler.uniqueCheckName(PKG, 10), mSink.enqueuedNames.get(0));
    }

    @Test
    public void withEnforcementOffTheBannerSaysChangesAreOnlyReported() {
        Prefs.Privacy.setPermissionWatcherEnforceEnabled(false);
        mDb.ruleDao().upsert(permission(PKG, 0, CAMERA, WatchRule.STATE_DENY));
        WatchedPermissionsActivity activity = open();
        waitForRows(activity, 1);

        TextView banner = activity.findViewById(R.id.banner);
        assertEquals(View.VISIBLE, banner.getVisibility());
        assertEquals(mApp.getString(R.string.permission_watcher_enforce_off_banner), banner.getText().toString());
    }

    @Test
    public void theBannerExplainsWhyChangesWouldOnlyBeReported() {
        assertEquals(0, WatchedPermissionsActivity.bannerFor(true, true, false));
        assertEquals(0, WatchedPermissionsActivity.bannerFor(false, false, false));
        assertEquals(0, WatchedPermissionsActivity.bannerFor(true, true, true));
        assertEquals(R.string.permission_watcher_needs_privilege,
                WatchedPermissionsActivity.bannerFor(true, false, true));
        assertEquals(R.string.permission_watcher_enforce_off_banner,
                WatchedPermissionsActivity.bannerFor(false, false, true));
        assertEquals(R.string.permission_watcher_enforce_off_banner,
                WatchedPermissionsActivity.bannerFor(false, true, true));
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
    private static String text(@NonNull WatchedPermissionsActivity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
}
