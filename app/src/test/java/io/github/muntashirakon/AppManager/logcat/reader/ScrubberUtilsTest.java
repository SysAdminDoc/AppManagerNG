// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScrubberUtilsTest {
    @Test
    public void scrubsIpAndPhoneEmbeddedInLongerLine() {
        String scrubbed = ScrubberUtils.scrubLine("Connected to 8.8.8.8:443 for +1 415 555 0132");
        assertFalse("IP address must be redacted", scrubbed.contains("8.8.8.8"));
        assertFalse("Phone number must be redacted", scrubbed.contains("415 555 0132"));
        assertTrue(scrubbed.contains("<IP address omitted>"));
        assertTrue(scrubbed.contains("<phone number omitted>"));
    }

    @Test
    public void scrubsWholeLineIp() {
        assertEquals("<IP address omitted>", ScrubberUtils.scrubLine("192.168.1.254"));
    }

    @Test
    public void scrubsEmbeddedEmailAndUrl() {
        String scrubbed = ScrubberUtils.scrubLine("send report to jane.doe@example.com via https://example.com/upload");
        assertFalse(scrubbed.contains("jane.doe@example.com"));
        assertFalse(scrubbed.contains("https://example.com/upload"));
    }

    @Test
    public void doesNotTouchDalvikCacheLines() {
        String line = "loading /data/dalvik-cache/arm64/system@framework@boot.art at 10.0.0.1";
        // Cache lines are deliberately ignored to avoid clobbering diagnostic paths.
        assertEquals(line, ScrubberUtils.scrubLine(line));
    }

    @Test
    public void leavesOrdinaryTextUntouched() {
        String line = "ActivityManager: Start proc for io.github.example";
        assertEquals(line, ScrubberUtils.scrubLine(line));
    }
}
