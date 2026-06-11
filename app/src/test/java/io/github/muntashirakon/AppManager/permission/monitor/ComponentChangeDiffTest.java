// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ComponentChangeDiffTest {
    @Test
    public void unchangedSnapshotIsNotInteresting() {
        ComponentSnapshot before = ComponentSnapshot.of(1,
                new String[]{"com.foo.MainActivity"},
                new String[]{"com.foo.AnalyticsService"});
        ComponentSnapshot after = ComponentSnapshot.of(2,
                new String[]{"com.foo.MainActivity"},
                new String[]{"com.foo.AnalyticsService"});

        ComponentChangeDiff.Result result = ComponentChangeDiff.compute("com.foo", before, after);

        assertFalse(result.isInteresting());
        assertEquals(1, result.beforeVersionCode);
        assertEquals(2, result.afterVersionCode);
    }

    @Test
    public void componentAndTrackerDeltasAreReportedSeparately() {
        ComponentSnapshot before = ComponentSnapshot.of(1,
                new String[]{"com.foo.MainActivity", "com.foo.LegacyReceiver", "com.foo.OldTracker"},
                new String[]{"com.foo.OldTracker"});
        ComponentSnapshot after = ComponentSnapshot.of(2,
                new String[]{"com.foo.MainActivity", "com.foo.NewReceiver", "com.foo.NewTracker"},
                new String[]{"com.foo.NewTracker"});

        ComponentChangeDiff.Result result = ComponentChangeDiff.compute("com.foo", before, after);

        assertTrue(result.isInteresting());
        assertTrue(result.addedComponents.contains("com.foo.NewReceiver"));
        assertTrue(result.addedComponents.contains("com.foo.NewTracker"));
        assertTrue(result.removedComponents.contains("com.foo.LegacyReceiver"));
        assertTrue(result.removedComponents.contains("com.foo.OldTracker"));
        assertTrue(result.addedTrackers.contains("com.foo.NewTracker"));
        assertTrue(result.removedTrackers.contains("com.foo.OldTracker"));
    }
}
