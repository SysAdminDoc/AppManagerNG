// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class PermissionWatchRelayClientTest {
    private static final String CAMERA = "android.permission.CAMERA";
    private static final String LOCATION = "android.permission.ACCESS_FINE_LOCATION";

    private static Integer uidOf(String packageName, int userId) {
        switch (packageName) {
            case "com.a":
                return userId * 100_000 + 10050;
            case "com.b":
                return userId * 100_000 + 10060;
            default:
                return null;
        }
    }

    @Test
    public void oneEntryPerAppAndUserWithARuleThatActs() {
        PermissionWatchRelayClient.Targets targets = PermissionWatchRelayClient.targetsFor(Arrays.asList(
                WatchTestDb.permission("com.a", 0, CAMERA, WatchRule.STATE_DENY),
                WatchTestDb.permission("com.a", 0, LOCATION, WatchRule.STATE_ALLOW),
                WatchTestDb.permission("com.a", 10, CAMERA, WatchRule.STATE_DENY),
                WatchTestDb.appOp("com.b", 0, 24, "SYSTEM_ALERT_WINDOW", WatchRule.STATE_DENY)),
                PermissionWatchRelayClientTest::uidOf);

        assertArrayEquals(new int[]{10050, 1010050, 10060}, targets.uids);
        assertArrayEquals(new String[]{"com.a", "com.a", "com.b"}, targets.packageNames);
    }

    @Test
    public void dontAlertRulesAndUninstalledAppsAreLeftOut() {
        PermissionWatchRelayClient.Targets targets = PermissionWatchRelayClient.targetsFor(Arrays.asList(
                WatchTestDb.permission("com.a", 0, CAMERA, WatchRule.STATE_IGNORE),
                WatchTestDb.permission("com.gone", 0, CAMERA, WatchRule.STATE_DENY),
                WatchTestDb.permission("com.b", 0, CAMERA, WatchRule.STATE_DENY)),
                PermissionWatchRelayClientTest::uidOf);

        assertArrayEquals(new int[]{10060}, targets.uids);
        assertArrayEquals(new String[]{"com.b"}, targets.packageNames);
    }
}
