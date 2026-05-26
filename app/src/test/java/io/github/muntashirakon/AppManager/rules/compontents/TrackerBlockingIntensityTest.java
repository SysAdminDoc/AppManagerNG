// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import static io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity.DETECT_ONLY;
import static io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity.STANDARD;
import static io.github.muntashirakon.AppManager.rules.compontents.TrackerBlockingIntensity.STRICT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure-JVM coverage for the NF-07 tracker-blocking intensity policy. */
public class TrackerBlockingIntensityTest {

    @Test
    public void detectOnlyBlocksNothing() {
        for (TrackerCategory cat : TrackerCategory.values()) {
            assertFalse("DETECT_ONLY must never block " + cat,
                    DETECT_ONLY.shouldBlock(cat));
        }
    }

    @Test
    public void strictBlocksEverything() {
        for (TrackerCategory cat : TrackerCategory.values()) {
            assertTrue("STRICT must always block " + cat,
                    STRICT.shouldBlock(cat));
        }
    }

    @Test
    public void standardBlocksOnlyHostileCategories() {
        // Hostile categories — user-visible only as ads, identification, or
        // analytics calls home about the user.
        assertTrue(STANDARD.shouldBlock(TrackerCategory.AD));
        assertTrue(STANDARD.shouldBlock(TrackerCategory.ANALYTICS));
        assertTrue(STANDARD.shouldBlock(TrackerCategory.IDENTIFICATION));

        // Useful or load-bearing categories — STANDARD leaves them alone so
        // apps don't lose crash reports, push notifications, location features,
        // or social-login flows.
        assertFalse(STANDARD.shouldBlock(TrackerCategory.CRASH));
        assertFalse(STANDARD.shouldBlock(TrackerCategory.PUSH));
        assertFalse(STANDARD.shouldBlock(TrackerCategory.LOCATION));
        assertFalse(STANDARD.shouldBlock(TrackerCategory.SOCIAL));
        assertFalse(STANDARD.shouldBlock(TrackerCategory.OTHER));
    }

    @Test
    public void fromPrefValueDefaultsToStrictForNullOrUnknown() {
        assertEquals(STRICT, TrackerBlockingIntensity.fromPrefValue(null));
        assertEquals(STRICT, TrackerBlockingIntensity.fromPrefValue(""));
        assertEquals(STRICT, TrackerBlockingIntensity.fromPrefValue("NOPE"));
    }

    @Test
    public void fromPrefValueRoundTripsAllValues() {
        for (TrackerBlockingIntensity intensity : TrackerBlockingIntensity.values()) {
            assertEquals(intensity, TrackerBlockingIntensity.fromPrefValue(intensity.name()));
        }
    }
}
