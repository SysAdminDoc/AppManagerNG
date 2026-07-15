// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.FileNotFoundException;

@RunWith(RobolectricTestRunner.class)
public class BackupPolicyLocatorTest {
    @Test
    public void explicitDestinationRoundTripsThroughPortableLocator() throws Exception {
        Uri destination = Uri.parse("content://provider/tree/primary%3ABackups");

        String locator = BackupItems.encodeRelativeDir(destination, "backups/1234");

        assertEquals(destination, BackupItems.decodeDestination(locator));
        assertEquals(BackupItems.getDestinationKey(locator),
                BackupItems.getDestinationKey(BackupItems.encodeRelativeDir(destination, "backups/other")));
    }

    @Test
    public void configuredDestinationKeepsLegacyRelativePath() throws Exception {
        String locator = BackupItems.encodeRelativeDir(null, "backups/1234");
        assertEquals("backups/1234", locator);
        assertEquals("", BackupItems.getDestinationKey(locator));
        assertNull(BackupItems.decodeDestination(locator));
    }

    @Test
    public void malformedOrTraversingLocatorsFailClosed() {
        assertThrows(FileNotFoundException.class,
                () -> BackupItems.decodeDestination("policy_volume/not-base64/backups/1234"));
        Uri destination = Uri.parse("file:///storage/emulated/0");
        String prefix = BackupItems.encodeRelativeDir(destination, "backups/1234")
                .replace("backups/1234", "../outside");
        assertThrows(FileNotFoundException.class, () -> BackupItems.decodeDestination(prefix));
        String finalSegmentTraversal = BackupItems.encodeRelativeDir(destination, "backups/1234")
                .replace("backups/1234", "backups/..");
        assertThrows(FileNotFoundException.class,
                () -> BackupItems.decodeDestination(finalSegmentTraversal));
    }
}
