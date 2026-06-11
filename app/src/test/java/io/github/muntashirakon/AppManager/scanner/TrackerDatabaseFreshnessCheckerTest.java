// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TrackerDatabaseFreshnessCheckerTest {
    @Test
    public void extractVersionReadsTrackerDatabaseResourceString() {
        String xml = "<resources>"
                + "<string name=\"tracker_database_version\" translatable=\"false\">2026-06-11</string>"
                + "</resources>";

        assertEquals("2026-06-11", TrackerDatabaseFreshnessChecker.extractVersion(xml));
    }

    @Test
    public void extractVersionReturnsNullWhenMetadataIsMissing() {
        assertNull(TrackerDatabaseFreshnessChecker.extractVersion("<resources />"));
    }
}
