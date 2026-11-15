// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The app only notices a newer Android version than it was tested on; it never refuses one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class PlatformSupportTest {
    private static final int TESTED = PlatformSupport.TESTED_SDK;

    @Test
    public void theTestedCeilingIsTheCompileSdk() throws IOException {
        File versions = new File(findRepoRoot(), "versions.gradle");
        String text = new String(Files.readAllBytes(versions.toPath()), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?m)^\\s*compile_sdk\\s*=\\s*(\\d+)\\s*$").matcher(text);

        assertTrue("versions.gradle has no numeric compile_sdk", matcher.find());
        assertEquals(Integer.parseInt(matcher.group(1)), TESTED);
    }

    @Test
    public void onlyANewerVersionIsUntested() {
        assertFalse(PlatformSupport.isUntested(TESTED - 1, "REL"));
        assertFalse(PlatformSupport.isUntested(TESTED, "REL"));
        assertTrue(PlatformSupport.isUntested(TESTED + 1, "REL"));
    }

    @Test
    public void aPreviewCountsAsTheVersionItLeadsTo() {
        // A preview of the next version reports the tested level with its codename
        assertTrue(PlatformSupport.isUntested(TESTED, "Ultra"));
        assertEquals(TESTED + 1, PlatformSupport.getRunningLevel(TESTED, "Ultra"));
        // A preview of the tested version itself is covered
        assertFalse(PlatformSupport.isUntested(TESTED - 1, "Ultra"));
    }

    @Test
    public void aDismissedNoticeStaysHiddenOnlyOnThatVersion() {
        assertTrue(PlatformSupport.shouldShowUntestedNotice(TESTED + 1, "REL", 0));
        assertFalse(PlatformSupport.shouldShowUntestedNotice(TESTED + 1, "REL", TESTED + 1));
        assertTrue(PlatformSupport.shouldShowUntestedNotice(TESTED + 2, "REL", TESTED + 1));
        // Dismissed on a preview, it stays hidden once that version is released
        assertFalse(PlatformSupport.shouldShowUntestedNotice(TESTED, "Ultra", TESTED + 1));
        assertFalse(PlatformSupport.shouldShowUntestedNotice(TESTED + 1, "REL", TESTED + 1));
        assertFalse(PlatformSupport.shouldShowUntestedNotice(TESTED, "REL", 0));
    }

    @Test
    public void aTestedDeviceShowsNoNoticeAndNoUntestedLine() {
        assertFalse(PlatformSupport.isUntested());
        assertFalse(PlatformSupport.shouldShowUntestedNotice());
        assertNull(PlatformSupport.describeUntested(36, "REL"));
    }

    @Test
    public void theSupportLineNamesBothVersions() {
        String line = PlatformSupport.describeUntested(TESTED + 1, "REL");
        assertNotNull(line);
        assertEquals("this build was tested through API " + TESTED + " and the device runs API " + (TESTED + 1), line);

        String preview = PlatformSupport.describeUntested(TESTED, "Ultra");
        assertEquals("this build was tested through API " + TESTED + " and the device runs a preview of API "
                + (TESTED + 1) + " (Ultra)", preview);
    }

    private static File findRepoRoot() {
        File cursor = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (cursor != null) {
            if (new File(cursor, "settings.gradle").isFile() && new File(cursor, "versions.gradle").isFile()) {
                return cursor;
            }
            cursor = cursor.getParentFile();
        }
        throw new IllegalStateException("Could not locate repository root from user.dir");
    }
}
