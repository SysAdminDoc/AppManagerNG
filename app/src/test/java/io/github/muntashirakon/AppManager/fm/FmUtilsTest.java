// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

import io.github.muntashirakon.io.Paths;

@RunWith(RobolectricTestRunner.class)
public class FmUtilsTest {
    @Test
    public void getClipboardPathFlattensControlCharacters() {
        Uri uri = Uri.parse("file:///sdcard/%09=payload%0Aname");

        assertEquals("/sdcard/ =payload name", FmUtils.getClipboardPath(uri));
    }

    @Test
    public void getDisplayNameFlattensControlsDefusesFormulaAndFallsBack() {
        assertEquals("' =payload name.txt",
                FmUtils.getDisplayName("\t=payload\nname.txt", "fallback.txt"));
        assertEquals("/sdcard/root",
                FmUtils.getDisplayName("\r\n\t", "/sdcard/root"));
        assertEquals("", FmUtils.getDisplayName(null, "\r\n\t"));
    }

    @Test
    public void getPathDisplayNameUsesFormattedName() {
        Uri uri = Uri.parse("file:///sdcard/%09=payload%0Aname.txt");

        assertEquals("' =payload name.txt", FmUtils.getPathDisplayName(Paths.get(uri)));
    }

    @Test
    public void getFileDisplayNameUsesFormattedName() {
        File file = new File("/sdcard/\t=payload\nname.apk");

        assertEquals("' =payload name.apk", FmUtils.getFileDisplayName(file));
    }
}
