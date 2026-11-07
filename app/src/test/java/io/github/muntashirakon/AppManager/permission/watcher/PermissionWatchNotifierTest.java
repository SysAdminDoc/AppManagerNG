// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import static io.github.muntashirakon.AppManager.permission.watcher.WatchTestDb.permission;

import android.app.Application;
import android.app.Notification;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

@RunWith(RobolectricTestRunner.class)
public class PermissionWatchNotifierTest {
    private static final String PKG = "com.example.app";
    private static final PermissionWatchNotifier.LabelResolver LABELS = packageName -> "Example";

    private Application mApp;

    @Before
    public void setUp() {
        mApp = RuntimeEnvironment.getApplication();
    }

    @NonNull
    private static PermissionWatcher.Drift drift(@NonNull String name, @WatchRule.State int state,
                                                 @PermissionWatcher.Outcome int outcome) {
        // A drift always has the opposite of what its rule asks for.
        return new PermissionWatcher.Drift(permission(PKG, 0, "android.permission." + name, state),
                state == WatchRule.STATE_DENY, outcome, outcome == PermissionWatcher.OUTCOME_FAILED ? "busy" : null);
    }

    @NonNull
    private static PermissionWatcher.Report report(PermissionWatcher.Drift... drifts) {
        return new PermissionWatcher.Report(drifts.length, 0, new ArrayList<>(Arrays.asList(drifts)));
    }

    @Test
    public void whenEverythingWasPutBackTheTitleSaysSo() {
        PermissionWatcher.Report report = report(
                drift("CAMERA", WatchRule.STATE_DENY, PermissionWatcher.OUTCOME_ENFORCED),
                drift("ACCESS_FINE_LOCATION", WatchRule.STATE_ALLOW, PermissionWatcher.OUTCOME_ENFORCED));

        assertEquals(mApp.getResources().getQuantityString(R.plurals.permission_watcher_notification_enforced, 2, 2),
                PermissionWatchNotifier.title(mApp, report));
        assertEquals(mApp.getString(R.string.permission_watcher_line_denied_again, "Example", "CAMERA") + "\n"
                        + mApp.getString(R.string.permission_watcher_line_allowed_again, "Example",
                        "ACCESS_FINE_LOCATION"),
                PermissionWatchNotifier.body(mApp, report, LABELS));
    }

    @Test
    public void withoutAPrivilegedModeTheAlertSaysWhyNothingChanged() {
        PermissionWatcher.Report report = report(
                drift("CAMERA", WatchRule.STATE_DENY, PermissionWatcher.OUTCOME_ENFORCED),
                drift("RECORD_AUDIO", WatchRule.STATE_DENY, PermissionWatcher.OUTCOME_NEEDS_PRIVILEGE));

        String body = PermissionWatchNotifier.body(mApp, report, LABELS);

        assertEquals(mApp.getResources().getQuantityString(R.plurals.permission_watcher_notification_title, 2, 2),
                PermissionWatchNotifier.title(mApp, report));
        assertTrue(body.contains(mApp.getString(R.string.permission_watcher_line_should_be_denied, "Example",
                "RECORD_AUDIO")));
        assertTrue(body.endsWith(mApp.getString(R.string.permission_watcher_reason_no_privilege)));
        assertFalse(body.contains(mApp.getString(R.string.permission_watcher_reason_enforce_off)));
    }

    @Test
    public void withEnforcementOffTheAlertSaysSo() {
        String body = PermissionWatchNotifier.body(mApp, report(
                drift("CAMERA", WatchRule.STATE_ALLOW, PermissionWatcher.OUTCOME_REPORTED)), LABELS);

        assertTrue(body.startsWith(mApp.getString(R.string.permission_watcher_line_should_be_allowed, "Example",
                "CAMERA")));
        assertTrue(body.endsWith(mApp.getString(R.string.permission_watcher_reason_enforce_off)));
        assertFalse(body.contains(mApp.getString(R.string.permission_watcher_reason_no_privilege)));
    }

    @Test
    public void aFailedChangeShowsItsReason() {
        String body = PermissionWatchNotifier.body(mApp, report(
                drift("CAMERA", WatchRule.STATE_DENY, PermissionWatcher.OUTCOME_FAILED)), LABELS);

        assertEquals(mApp.getString(R.string.permission_watcher_line_deny_failed, "Example", "CAMERA", "busy"), body);
    }

    @Test
    public void aLongReportIsCutWithACount() {
        List<PermissionWatcher.Drift> drifts = new ArrayList<>();
        for (int i = 0; i < PermissionWatchNotifier.MAX_LINES + 2; ++i) {
            drifts.add(drift("P" + i, WatchRule.STATE_DENY, PermissionWatcher.OUTCOME_ENFORCED));
        }

        String body = PermissionWatchNotifier.body(mApp, report(drifts.toArray(new PermissionWatcher.Drift[0])),
                LABELS);

        String[] lines = body.split("\n");
        assertEquals(PermissionWatchNotifier.MAX_LINES + 1, lines.length);
        assertEquals("and 2 more", lines[lines.length - 1]);
    }

    @Test
    public void tappingTheAlertOpensTheWatchedList() {
        Notification notification = PermissionWatchNotifier.build(mApp, new NotificationCompat.Builder(mApp, "test"),
                report(drift("CAMERA", WatchRule.STATE_DENY, PermissionWatcher.OUTCOME_ENFORCED)), LABELS);

        assertEquals(WatchedPermissionsActivity.class.getName(),
                shadowOf(notification.contentIntent).getSavedIntent().getComponent().getClassName());
    }
}
