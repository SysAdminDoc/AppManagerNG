// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

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
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class CodeEditorFragmentTest {
    private java.nio.file.Path tempDir;
    private Path root;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("appmanagerng-editor-share");
        root = Paths.get(tempDir.toFile());
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.delete();
        }
    }

    @Test
    public void buildShareIntentUsesNormalizedFileTypeAndStreamGrant() throws IOException {
        Path file = root.createNewFile("snippet.txt", null);
        Uri uri = FmProvider.getContentUri(file);

        Intent intent = CodeEditorFragment.buildShareIntent(file);

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/plain", intent.getType());
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertNotNull(intent.getClipData());
        assertEquals(uri, intent.getClipData().getItemAt(0).getUri());
    }
}
