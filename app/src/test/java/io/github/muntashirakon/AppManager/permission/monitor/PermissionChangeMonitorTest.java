// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.permission.watcher.WatchRule;
import io.github.muntashirakon.AppManager.permission.watcher.WatchRuleActionReceiver;

@RunWith(RobolectricTestRunner.class)
public class PermissionChangeMonitorTest {
    private static final String PKG = "com.example.app";
    private static final String CAMERA = "android.permission.CAMERA";
    private static final String LOCATION = "android.permission.ACCESS_FINE_LOCATION";

    @NonNull
    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    public void anIgnoredPermissionDoesNotAlert() {
        assertEquals(new TreeSet<>(Collections.singletonList(LOCATION)),
                PermissionChangeMonitor.withoutIgnored(set(CAMERA, LOCATION), set(CAMERA, "android.permission.X")));
        assertEquals(set(CAMERA, LOCATION), PermissionChangeMonitor.withoutIgnored(set(CAMERA, LOCATION), set()));
        assertEquals(set(), PermissionChangeMonitor.withoutIgnored(set(CAMERA), set(CAMERA)));
    }

    @Test
    public void theAlertOffersAlwaysDenyAndAlwaysAllow() {
        Application app = RuntimeEnvironment.getApplication();
        Set<String> permissions = new TreeSet<>(Arrays.asList(CAMERA, LOCATION));

        Notification notification = PermissionChangeMonitor.buildNotification(app,
                new NotificationCompat.Builder(app, "test"), PKG, 10, "Title", "Body", permissions);

        assertEquals(2, notification.actions.length);
        assertEquals(app.getString(R.string.permission_watcher_always_deny), notification.actions[0].title.toString());
        assertEquals(app.getString(R.string.permission_watcher_always_allow),
                notification.actions[1].title.toString());
        checkAction(notification.actions[0], WatchRule.STATE_DENY);
        checkAction(notification.actions[1], WatchRule.STATE_ALLOW);
    }

    private static void checkAction(@NonNull Notification.Action action, int state) {
        Intent intent = shadowOf(action.actionIntent).getSavedIntent();
        assertEquals(WatchRuleActionReceiver.class.getName(), intent.getComponent().getClassName());
        assertEquals(PKG, intent.getStringExtra(WatchRuleActionReceiver.EXTRA_PACKAGE));
        assertEquals(10, intent.getIntExtra(WatchRuleActionReceiver.EXTRA_USER, -1));
        assertEquals(state, intent.getIntExtra(WatchRuleActionReceiver.EXTRA_STATE, -1));
        // Sorted, because the alert's set is a TreeSet.
        assertArrayEquals(new String[]{LOCATION, CAMERA},
                intent.getStringArrayExtra(WatchRuleActionReceiver.EXTRA_PERMISSIONS));
        assertEquals(PermissionChangeMonitor.notificationTag(PKG),
                intent.getStringExtra(WatchRuleActionReceiver.EXTRA_NOTIFICATION_TAG));
    }
}
