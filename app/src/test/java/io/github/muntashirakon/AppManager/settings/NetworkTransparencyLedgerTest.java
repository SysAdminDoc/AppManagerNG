// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class NetworkTransparencyLedgerTest {

    @Test
    public void buildEntriesReturnsAllFourFeatures() {
        List<NetworkTransparencyLedger.Entry> entries = NetworkTransparencyLedger.buildEntries();

        assertEquals(4, entries.size());
        assertEquals("VirusTotal", entries.get(0).name);
        assertEquals("Pithus", entries.get(1).name);
        assertEquals("Debloat definitions", entries.get(2).name);
        assertEquals("Tracker database freshness", entries.get(3).name);
    }

    @Test
    public void entriesHaveEndpointAndPayloadMetadata() {
        List<NetworkTransparencyLedger.Entry> entries = NetworkTransparencyLedger.buildEntries();

        for (NetworkTransparencyLedger.Entry entry : entries) {
            assertFalse("Entry " + entry.name + " must have endpoint",
                    entry.endpointClass.isEmpty());
            assertFalse("Entry " + entry.name + " must have payload category",
                    entry.payloadCategory.isEmpty());
        }
    }

    @Test
    public void flossCompileAvailabilityMatchesBuildConfig() {
        List<NetworkTransparencyLedger.Entry> entries = NetworkTransparencyLedger.buildEntries();

        boolean expected = FeatureController.areOptionalNetworkFeaturesAvailable();
        for (NetworkTransparencyLedger.Entry entry : entries) {
            assertEquals("compileAvailable for " + entry.name + " should match build config",
                    expected, entry.compileAvailable);
        }
    }

    @Test
    public void formatForDisplayIncludesAllEntryFields() {
        android.content.Context context =
                androidx.test.core.app.ApplicationProvider.getApplicationContext();
        List<NetworkTransparencyLedger.Entry> entries = NetworkTransparencyLedger.buildEntries();

        String text = NetworkTransparencyLedger.formatForDisplay(context, entries);

        assertTrue(text.contains("VirusTotal"));
        assertTrue(text.contains("Pithus"));
        assertTrue(text.contains("Debloat definitions"));
        assertTrue(text.contains("Tracker database freshness"));
    }
}
