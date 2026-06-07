// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.nio.file.Files;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmOpenWithDefaultsTest {
    private Context mContext;
    private java.nio.file.Path tempDir;
    private Path root;

    @Before
    public void setUp() throws IOException {
        mContext = RuntimeEnvironment.getApplication();
        mContext.getSharedPreferences(FmOpenWithDefaults.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        tempDir = Files.createTempDirectory("appmanagerng-open-with");
        root = Paths.get(tempDir.toFile());
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.delete();
        }
    }

    @Test
    public void extensionDefaultMatchesCaseInsensitively() {
        FmOpenWithDefaults.OpenWithHandler handler =
                new FmOpenWithDefaults.OpenWithHandler("com.example.viewer", "com.example.viewer.TextActivity");

        FmOpenWithDefaults.setDefault(mContext, Uri.parse("file:///sdcard/Download/a.TXT"), "TXT", handler, false);

        FmOpenWithDefaults.OpenWithHandler restored =
                FmOpenWithDefaults.getDefault(mContext, Uri.parse("file:///sdcard/Download/b.txt"), ".txt");
        assertEquals("com.example.viewer", restored.packageName);
        assertEquals("com.example.viewer.TextActivity", restored.activityName);
    }

    @Test
    public void fileDefaultOverridesExtensionDefault() {
        Uri uri = Uri.parse("file:///sdcard/Download/a.txt");
        FmOpenWithDefaults.setDefault(mContext, uri, "txt",
                new FmOpenWithDefaults.OpenWithHandler("com.example.viewer", "com.example.viewer.TextActivity"),
                false);
        FmOpenWithDefaults.setDefault(mContext, uri, "txt",
                new FmOpenWithDefaults.OpenWithHandler("com.example.editor", "com.example.editor.EditActivity"),
                true);

        FmOpenWithDefaults.OpenWithHandler restored = FmOpenWithDefaults.getDefault(mContext, uri, "txt");
        assertEquals("com.example.editor", restored.packageName);
        assertEquals("com.example.editor.EditActivity", restored.activityName);
    }

    @Test
    public void clearDefaultsRemovesFileAndExtensionDefaults() {
        Uri uri = Uri.parse("file:///sdcard/Download/a.txt");
        FmOpenWithDefaults.setDefault(mContext, uri, "txt",
                new FmOpenWithDefaults.OpenWithHandler("com.example.viewer", "com.example.viewer.TextActivity"),
                false);
        FmOpenWithDefaults.setDefault(mContext, uri, "txt",
                new FmOpenWithDefaults.OpenWithHandler("com.example.editor", "com.example.editor.EditActivity"),
                true);

        FmOpenWithDefaults.clearDefaults(mContext, uri, "txt");

        assertNull(FmOpenWithDefaults.getDefault(mContext, uri, "txt"));
    }

    @Test
    public void buildViewIntentNormalizesCustomMimeType() throws IOException {
        Path file = root.createNewFile("note.txt", null);

        Intent intent = FmOpenWithDefaults.buildViewIntent(file, " Text/Plain ; charset=utf-8 ");

        assertEquals("text/plain", intent.getType());
    }

    @Test
    public void buildViewIntentFallsBackForMalformedCustomMimeType() throws IOException {
        Path file = root.createNewFile("note.txt", null);

        Intent intent = FmOpenWithDefaults.buildViewIntent(file, "not-a-mime");

        assertEquals(file.getType(), intent.getType());
    }
}
