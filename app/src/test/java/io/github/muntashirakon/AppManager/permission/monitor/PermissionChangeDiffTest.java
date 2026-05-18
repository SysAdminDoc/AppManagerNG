// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PermissionChangeDiffTest {

    @Test
    public void noChangeIsNotInteresting() {
        PermissionSnapshot before = PermissionSnapshot.of(10,
                "android.permission.CAMERA", "android.permission.RECORD_AUDIO");
        PermissionSnapshot after = PermissionSnapshot.of(11,
                "android.permission.RECORD_AUDIO", "android.permission.CAMERA");  // reordered
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute("com.foo", before, after);
        assertFalse(diff.isInteresting());
        assertTrue(diff.added.isEmpty());
        assertTrue(diff.removed.isEmpty());
    }

    @Test
    public void addedDangerousIsInteresting() {
        PermissionSnapshot before = PermissionSnapshot.of(10,
                "android.permission.CAMERA");
        PermissionSnapshot after = PermissionSnapshot.of(11,
                "android.permission.CAMERA", "android.permission.RECORD_AUDIO");
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute("com.foo", before, after);
        assertTrue(diff.isInteresting());
        assertEquals(1, diff.added.size());
        assertTrue(diff.added.contains("android.permission.RECORD_AUDIO"));
        assertTrue(diff.removed.isEmpty());
    }

    @Test
    public void removedOnlyIsNotInteresting() {
        // The monitor's purpose is to flag escalation. Losing a permission
        // doesn't alarm; it's reported in the result but the .isInteresting()
        // signal stays false so the notification doesn't fire.
        PermissionSnapshot before = PermissionSnapshot.of(10,
                "android.permission.CAMERA", "android.permission.RECORD_AUDIO");
        PermissionSnapshot after = PermissionSnapshot.of(11,
                "android.permission.CAMERA");
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute("com.foo", before, after);
        assertFalse(diff.isInteresting());
        assertEquals(1, diff.removed.size());
        assertTrue(diff.removed.contains("android.permission.RECORD_AUDIO"));
        assertTrue(diff.added.isEmpty());
    }

    @Test
    public void mixedAddAndRemoveStillInterestingWhenAddedNonEmpty() {
        PermissionSnapshot before = PermissionSnapshot.of(10,
                "android.permission.CAMERA");
        PermissionSnapshot after = PermissionSnapshot.of(11,
                "android.permission.LOCATION");
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute("com.foo", before, after);
        assertTrue(diff.isInteresting());
        assertEquals(1, diff.added.size());
        assertEquals(1, diff.removed.size());
    }

    @Test
    public void diffPreservesPackageAndVersions() {
        PermissionSnapshot before = PermissionSnapshot.of(10);
        PermissionSnapshot after = PermissionSnapshot.of(11, "android.permission.CAMERA");
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute("com.foo.bar", before, after);
        assertEquals("com.foo.bar", diff.packageName);
        assertEquals(10, diff.beforeVersionCode);
        assertEquals(11, diff.afterVersionCode);
    }

    @Test
    public void emptyOnBothSidesIsNotInteresting() {
        PermissionSnapshot before = PermissionSnapshot.of(10);
        PermissionSnapshot after = PermissionSnapshot.of(11);
        PermissionChangeDiff.Result diff = PermissionChangeDiff.compute("com.foo", before, after);
        assertFalse(diff.isInteresting());
        assertTrue(diff.added.isEmpty());
        assertTrue(diff.removed.isEmpty());
    }
}
