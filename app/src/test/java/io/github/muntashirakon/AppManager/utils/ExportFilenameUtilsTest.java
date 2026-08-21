// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
public class ExportFilenameUtilsTest {
    private static final long FIXED_TIMESTAMP = 1_786_446_240_000L;

    @Test
    public void buildTimestampedFileNameHandlesSlashDotAnd24HourPatterns() {
        Context context = ApplicationProvider.getApplicationContext();
        ContentResolver resolver = context.getContentResolver();
        String previousDateFormat = Settings.System.getString(resolver, Settings.System.DATE_FORMAT);
        String previousTimeFormat = Settings.System.getString(resolver, Settings.System.TIME_12_24);
        try {
            Settings.System.putString(resolver, Settings.System.DATE_FORMAT, "M/d/yy");
            Settings.System.putString(resolver, Settings.System.TIME_12_24, "12");
            String slashPattern = ExportFilenameUtils.buildTimestampedFileName(
                    context, "rules-", ".am.tsv", FIXED_TIMESTAMP);

            Settings.System.putString(resolver, Settings.System.DATE_FORMAT, "dd.MM.yy");
            Settings.System.putString(resolver, Settings.System.TIME_12_24, "24");
            String dotAnd24HourPattern = ExportFilenameUtils.buildTimestampedFileName(
                    context, "rules-", ".am.tsv", FIXED_TIMESTAMP);

            assertSafe(slashPattern);
            assertSafe(dotAnd24HourPattern);
            assertTrue(slashPattern.endsWith(".am.tsv"));
            assertEquals(dotAnd24HourPattern, ExportFilenameUtils.buildTimestampedFileName(
                    context, "rules-", ".am.tsv", FIXED_TIMESTAMP));
        } finally {
            Settings.System.putString(resolver, Settings.System.DATE_FORMAT, previousDateFormat);
            Settings.System.putString(resolver, Settings.System.TIME_12_24, previousTimeFormat);
        }
    }

    @Test
    public void buildFileNameSanitizesLocaleSpecificDateAndTimePatterns() {
        assertEquals("rules-8-11-26-3-04-PM.am.tsv", ExportFilenameUtils.buildFileName(
                "rules-8/11/26 3:04 PM", ".am.tsv", "rules"));
        assertEquals("rules-11.08.26-15-04.am.tsv", ExportFilenameUtils.buildFileName(
                "rules-11.08.26 15:04", ".am.tsv", "rules"));
        assertEquals("rules-2026-08-11-15-04.am.tsv", ExportFilenameUtils.buildFileName(
                "rules-2026\u5e7408\u670811\u65e5 15\u664204\u5206", ".am.tsv", "rules"));
    }

    @Test
    public void buildFileNameSanitizesAppLabelAndPreservesExtension() {
        String filename = ExportFilenameUtils.buildFileName(
                "My/App\\Name:\n\t*?\"<>|_icon", ".png", "app_icon");

        assertEquals("My-App-Name-_icon.png", filename);
        assertSafe(filename);
    }

    @Test
    public void buildFileNameUsesFallbackWhenStemHasNoUsableCharacters() {
        assertEquals("app_icon.png", ExportFilenameUtils.buildFileName(
                "///:::***", ".png", "app_icon"));
    }

    @Test
    public void buildFileNameIsStableAndCappedAt240Utf8Bytes() {
        String longStem = new String(new char[300]).replace('\0', '\u00e9');

        String first = ExportFilenameUtils.buildFileName(longStem, ".csv", "usage");
        String second = ExportFilenameUtils.buildFileName(longStem, ".csv", "usage");

        assertEquals(first, second);
        assertEquals(ExportFilenameUtils.MAX_FILENAME_BYTES,
                first.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(first.endsWith(".csv"));
        assertSafe(first);
    }

    private static void assertSafe(String filename) {
        assertTrue(filename.matches("[A-Za-z0-9._-]+"));
    }
}
