// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;

public class SupportInfoBundleTest {
    @Test
    public void scrubForPublicIssue_masksPackagePathUriEmailAndUidData() {
        String scrubbed = SupportInfoBundle.scrubForPublicIssue(
                "uid=10345 appUid=2000 userId=10 u0_a123 com.example.secret/.Main "
                        + "content://com.android.providers.downloads.documents/document/123 "
                        + "file:///sdcard/Download/private.apk /storage/emulated/0/DCIM/private.jpg "
                        + "person@example.com 123456");

        assertTrue(scrubbed.contains("uid=<redacted>"));
        assertTrue(scrubbed.contains("appUid=<redacted>"));
        assertTrue(scrubbed.contains("userId=<redacted>"));
        assertTrue(scrubbed.contains("u<redacted>"));
        assertTrue(scrubbed.contains("<package>"));
        assertTrue(scrubbed.contains("<email>"));
        assertTrue(scrubbed.contains("<id>"));
        assertFalse(scrubbed.contains("10345"));
        assertFalse(scrubbed.contains("com.example.secret"));
        assertFalse(scrubbed.contains("private.apk"));
        assertFalse(scrubbed.contains("private.jpg"));
        assertFalse(scrubbed.contains("person@example.com"));
    }

    @Test
    public void buildFileName_sanitizesDeviceNameAndUsesUtcTimestamp() {
        String fileName = SupportInfoBundle.buildFileName("Pixel 9/Pro", new Date(0));

        assertEquals("support-info-Pixel_9_Pro-19700101-000000.txt", fileName);
    }
}
