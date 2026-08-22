// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class AppUpdateChangeReportTest {
    @Test
    public void buildCombinesPermissionAndComponentDeltasWithVersionContext() {
        PermissionChangeDiff.Result permission = new PermissionChangeDiff.Result(
                "com.example.app", 10L, 11L,
                set("android.permission.CAMERA"), set("android.permission.READ_CONTACTS"),
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet());
        ComponentChangeDiff.Result components = new ComponentChangeDiff.Result(
                "com.example.app", 10L, 11L,
                set("com.example.NewActivity"), set("com.example.OldActivity"),
                set("com.example.Tracker"), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet());

        io.github.muntashirakon.AppManager.db.entity.AppUpdateChangeReport report =
                AppUpdateChangeReportRecorder.build(permission, components);

        assertEquals("com.example.app", report.packageName);
        assertEquals(10L, report.beforeVersionCode);
        assertEquals(11L, report.afterVersionCode);
        assertEquals(Collections.singletonList("android.permission.CAMERA"),
                AppUpdateChangeReportFormatter.decode(report.addedPermissions));
        assertEquals(Collections.singletonList("com.example.Tracker"),
                AppUpdateChangeReportFormatter.decode(report.addedTrackers));
        assertEquals(Collections.singletonList("com.example.NewActivity"),
                AppUpdateChangeReportFormatter.decode(report.addedComponents));
    }

    @Test
    public void buildReturnsNullForEmptyDiffs() {
        PermissionChangeDiff.Result permission = new PermissionChangeDiff.Result(
                "com.example.app", 1L, 1L, Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet());
        ComponentChangeDiff.Result components = new ComponentChangeDiff.Result(
                "com.example.app", 1L, 1L, Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet());

        assertNull(AppUpdateChangeReportRecorder.build(permission, components));
    }

    private static Set<String> set(String value) {
        Set<String> values = new LinkedHashSet<>();
        values.add(value);
        return values;
    }
}
