// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.file.Files;

import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class LogViewerActivityTest {
    private java.nio.file.Path tempDir;
    private Path root;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("appmanagerng-log-share");
        root = Paths.get(tempDir.toFile());
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.delete();
        }
    }

    @Test
    public void buildShareIntentNormalizesAttachmentType() throws IOException {
        Path file = root.createNewFile("events.log", null);

        Intent intent = LogViewerActivity.buildShareIntent("subject", " Text/Plain ; charset=utf-8 ", file);

        assertEquals("text/plain", intent.getType());
        assertEquals("subject", intent.getStringExtra(Intent.EXTRA_SUBJECT));
    }

    @Test
    public void buildShareIntentFallsBackToAttachmentTypeForMalformedInput() throws IOException {
        Path file = root.createNewFile("events.log", null);
        Uri uri = FmProvider.getContentUri(file);

        Intent intent = LogViewerActivity.buildShareIntent(null, "not-a-mime", file);

        assertEquals(file.getType(), intent.getType());
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertNotNull(intent.getClipData());
        assertEquals(uri, intent.getClipData().getItemAt(0).getUri());
    }

    @Test
    public void formatAttachmentNameFlattensControlsDefusesFormulaAndFallsBack() {
        assertEquals("' =report csv",
                LogViewerActivity.formatAttachmentName("\t=report\ncsv", "fallback.log"));
        assertEquals("' @fallback log",
                LogViewerActivity.formatAttachmentName(null, "\t@fallback\nlog"));
        assertEquals(SaveLogHelper.LOG_FILENAME,
                LogViewerActivity.formatAttachmentName("\n\t", "\r\n"));
    }
}
