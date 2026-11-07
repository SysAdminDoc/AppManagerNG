// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TopAppTrackerTest {
    private static final String SELF = "io.example.self";
    private static final String LAUNCHER = "com.example.launcher";
    private static final String APP = "com.example.app";
    private static final String OTHER_APP = "com.example.other";
    private static final String AD = "com.example.ad";
    private static final String EDGE_HANDLE = "com.example.edge";

    private final TopAppTracker mTracker = new TopAppTracker(Arrays.asList(SELF, LAUNCHER));

    @NonNull
    private static TopAppTracker.Window app(@NonNull String packageName) {
        return new TopAppTracker.Window(packageName, true);
    }

    @NonNull
    private static TopAppTracker.Window overlay(@NonNull String packageName) {
        return new TopAppTracker.Window(packageName, false);
    }

    @NonNull
    private static List<TopAppTracker.Window> walk(@NonNull TopAppTracker.Window... windowsTopFirst) {
        return Arrays.asList(windowsTopFirst);
    }

    @Test
    public void ignoredPackagesNeverReplaceTheCurrentApp() {
        assertTrue(mTracker.onTopApp(AD));

        assertFalse(mTracker.onTopApp(SELF));
        assertFalse(mTracker.onTopApp(LAUNCHER));
        assertFalse(mTracker.onTopApp(null));
        assertFalse(mTracker.onTopApp(""));

        assertEquals(AD, mTracker.getCurrent());
        assertEquals(Arrays.asList(AD), mTracker.getRecents());
    }

    @Test
    public void nothingIsNamedBeforeAnAppIsSeen() {
        assertFalse(mTracker.onWindows(walk(app(LAUNCHER))));

        assertNull(mTracker.getCurrent());
        assertTrue(mTracker.getRecents().isEmpty());
    }

    @Test
    public void recentsAreNewestFirstWithoutRepeats() {
        mTracker.onTopApp(APP);
        mTracker.onTopApp(AD);
        mTracker.onTopApp(APP);

        assertEquals(Arrays.asList(APP, AD), mTracker.getRecents());
    }

    @Test
    public void recentsDropTheOldestPastTheLimit() {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < TopAppTracker.MAX_RECENTS + 3; ++i) {
            String packageName = "com.example.app" + i;
            mTracker.onTopApp(packageName);
            expected.add(0, packageName);
        }

        assertEquals(TopAppTracker.MAX_RECENTS, mTracker.getRecents().size());
        assertEquals(expected.subList(0, TopAppTracker.MAX_RECENTS), mTracker.getRecents());
    }

    @Test
    public void repeatingTheCurrentAppIsNotAChange() {
        assertTrue(mTracker.onTopApp(APP));

        assertFalse(mTracker.onTopApp(APP));
    }

    @Test
    public void aPermanentOverlayNeverHoldsTheBubble() {
        mTracker.onWindows(walk(overlay(EDGE_HANDLE), app(APP)));
        assertEquals(APP, mTracker.getCurrent());

        assertTrue(mTracker.onWindows(walk(overlay(EDGE_HANDLE), app(OTHER_APP))));
        assertEquals(OTHER_APP, mTracker.getCurrent());

        assertFalse(mTracker.onWindows(walk(overlay(EDGE_HANDLE), app(OTHER_APP))));
        assertEquals(OTHER_APP, mTracker.getCurrent());
    }

    @Test
    public void aPermanentOverlayThatLeavesAndReturnsIsStillNotNamed() {
        mTracker.onWindows(walk(overlay(EDGE_HANDLE), app(APP)));
        mTracker.onWindows(walk(app(APP)));

        assertFalse(mTracker.onWindows(walk(overlay(EDGE_HANDLE), app(APP))));

        assertEquals(APP, mTracker.getCurrent());
    }

    @Test
    public void anOverlayAlreadyUpStaysWithinReach() {
        mTracker.onWindows(walk(overlay(AD), app(APP)));

        assertEquals(APP, mTracker.getCurrent());
        assertEquals(Arrays.asList(APP, AD), mTracker.getRecents());
    }

    @Test
    public void anOverlayAlreadyUpIsNamedOverTheHomeScreenUntilAnAppOpens() {
        mTracker.onWindows(walk(overlay(AD), app(LAUNCHER)));
        assertEquals(AD, mTracker.getCurrent());

        mTracker.onWindows(walk(overlay(AD), app(APP)));
        assertEquals(APP, mTracker.getCurrent());
    }

    @Test
    public void aNewOverlayIsNamed() {
        mTracker.onWindows(walk(app(APP)));

        assertTrue(mTracker.onWindows(walk(overlay(AD), app(APP))));

        assertEquals(AD, mTracker.getCurrent());
        assertEquals(Arrays.asList(AD, APP), mTracker.getRecents());
    }

    @Test
    public void anOverlayThatFlashedStaysNamedAfterItGoes() {
        mTracker.onWindows(walk(app(APP)));
        mTracker.onWindows(walk(overlay(AD), app(APP)));

        assertFalse(mTracker.onWindows(walk(app(APP))));

        assertEquals(AD, mTracker.getCurrent());
    }

    @Test
    public void anOverlayThatComesBackIsNamedAgain() {
        mTracker.onWindows(walk(app(APP)));
        mTracker.onWindows(walk(overlay(AD), app(APP)));
        mTracker.onWindows(walk(app(OTHER_APP)));
        assertEquals(OTHER_APP, mTracker.getCurrent());

        assertTrue(mTracker.onWindows(walk(overlay(AD), app(OTHER_APP))));

        assertEquals(AD, mTracker.getCurrent());
    }

    @Test
    public void anOverlayArrivingWithANewAppKeepsBoth() {
        mTracker.onWindows(walk(app(APP)));

        assertTrue(mTracker.onWindows(walk(overlay(AD), app(OTHER_APP))));

        assertEquals(Arrays.asList(AD, OTHER_APP, APP), mTracker.getRecents());
    }

    @Test
    public void thisAppsOwnBubbleIsNeverNamed() {
        mTracker.onWindows(walk(app(APP)));

        assertFalse(mTracker.onWindows(walk(overlay(SELF), app(APP))));

        assertEquals(APP, mTracker.getCurrent());
    }

    @Test
    public void aFlashedAdSurvivesClosingToTheHomeScreen() {
        mTracker.onTopApp(AD);

        assertFalse(mTracker.onTopApp(LAUNCHER));
        assertFalse(mTracker.onWindows(walk(app(LAUNCHER))));

        assertEquals(AD, mTracker.getCurrent());
    }

    @Test
    public void aWalkWithNoAppWindowLeavesTheCurrentAppAlone() {
        mTracker.onTopApp(APP);

        assertFalse(mTracker.onWindows(new ArrayList<>()));

        assertEquals(APP, mTracker.getCurrent());
    }
}
