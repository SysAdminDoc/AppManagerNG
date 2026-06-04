// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class FmOpenWithDefaultsTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        mContext.getSharedPreferences(FmOpenWithDefaults.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
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
}
