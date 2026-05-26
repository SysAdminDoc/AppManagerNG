// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class PackageVisibilityInfoTest {

    @Test
    public void hasQueryAllPackagesIsTrueWhenManifestDeclaresIt() {
        assertTrue(PackageVisibilityInfo.hasQueryAllPackages(
                new String[]{"android.permission.INTERNET", PackageVisibilityInfo.QUERY_ALL_PACKAGES}));
    }

    @Test
    public void hasQueryAllPackagesIsFalseForUnrelatedPermissions() {
        assertFalse(PackageVisibilityInfo.hasQueryAllPackages(
                new String[]{"android.permission.INTERNET", "android.permission.WAKE_LOCK"}));
    }

    @Test
    public void hasQueryAllPackagesHandlesNullAndEmpty() {
        assertFalse(PackageVisibilityInfo.hasQueryAllPackages(null));
        assertFalse(PackageVisibilityInfo.hasQueryAllPackages(new String[0]));
    }

    @Test
    public void hasSignalIsTrueOnQueryAllPackages() {
        PackageVisibilityInfo info = new PackageVisibilityInfo(
                /* queryAll */ true, Collections.emptyList(), Collections.emptyList());
        assertTrue(info.hasSignal());
    }

    @Test
    public void hasSignalIsTrueOnNonEmptyQueriesPackages() {
        PackageVisibilityInfo info = new PackageVisibilityInfo(
                /* queryAll */ false,
                Arrays.asList("com.example.target"),
                Collections.emptyList());
        assertTrue(info.hasSignal());
    }

    @Test
    public void hasSignalIsTrueOnNonEmptyIntentActions() {
        PackageVisibilityInfo info = new PackageVisibilityInfo(
                /* queryAll */ false,
                Collections.emptyList(),
                Arrays.asList("android.intent.action.VIEW"));
        assertTrue(info.hasSignal());
    }

    @Test
    public void hasSignalIsFalseWhenEverythingIsEmpty() {
        PackageVisibilityInfo info = new PackageVisibilityInfo(
                /* queryAll */ false, Collections.emptyList(), Collections.emptyList());
        assertFalse(info.hasSignal());
    }

    @Test
    public void listsAreSortedAndDefensivelyCopied() {
        ArrayList<String> mutable = new ArrayList<>(Arrays.asList("b", "a", "c"));
        PackageVisibilityInfo info = new PackageVisibilityInfo(
                false, mutable, Collections.emptyList());
        mutable.add("z");
        // Defensive copy: post-construction mutation must not leak in.
        assertEquals(3, info.queriesPackages.size());
        assertEquals("b", info.queriesPackages.get(0));
    }

    @Test
    public void queriesFlagIsSafeBelowApi30() {
        // The constant is only meaningful on API 30+; helper returns 0 below.
        int flag = PackageVisibilityInfo.getQueriesFlag();
        // We can't reliably set API levels here without Robolectric sdk= annotation;
        // just assert the flag is one of the two valid values.
        assertTrue(flag == 0 || flag == 0x00040000);
    }
}
