// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** The import preview names the staged snapshot by the start of its SHA-256. */
@RunWith(RobolectricTestRunner.class)
public class SnapshotFingerprintTest {
    @Test
    public void theFingerprintIsTheFirstSixteenHexDigitsInGroupsOfFour() {
        assertEquals("3f5f 597c 7474 d90f", PrivacyPreferences.formatFingerprint(
                "3f5f597c7474d90fe27060477f243b804bec705dc60feca4de27ca326386448a"));
    }

    @Test
    public void aShortDigestIsShownWhole() {
        assertEquals("abcd ef", PrivacyPreferences.formatFingerprint("abcdef"));
    }
}
