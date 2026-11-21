// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;

import androidx.test.core.app.ApplicationProvider;

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

    @Test
    public void getArchiveEntryDisplayNameUsesFormattedEntryName() {
        assertEquals("' =payload name.txt",
                FmUtils.getArchiveEntryDisplayName("\t=payload\nname.txt"));
    }

    @Test
    public void getFailureToastMessageFormatsThrowableMessage() {
        assertEquals("Failed: ' =payload action",
                FmUtils.getFailureToastMessage(new SecurityException("\t=payload\raction")));
    }

    @Test
    public void getFailureToastMessageFallsBackForBlankMessage() {
        assertEquals("Failed", FmUtils.getFailureToastMessage(new SecurityException()));
    }

    @Test
    public void rowTitleShowsHiddenCharactersAsBadges() {
        // #53: zero-width and direction characters in a file name are shown, not hidden
        Context context = ApplicationProvider.getApplicationContext();
        String zwsp = String.valueOf((char) 0x200B);
        String rlo = String.valueOf((char) 0x202E);

        CharSequence title = FmUtils.getRowTitle(context, "a" + zwsp + "b.txt", "/sdcard/x");

        assertEquals("a[ZWSP]b.txt", title.toString());
        assertTrue(title instanceof Spanned);
        assertEquals(1, ((Spanned) title).getSpans(0, title.length(), BackgroundColorSpan.class).length);
        assertEquals("photo[RLO]gpj.exe",
                FmUtils.getRowTitle(context, "photo" + rlo + "gpj.exe", "/sdcard/x").toString());
    }

    @Test
    public void rowTitlesOfBlankNamesStayApart() {
        // Two files named with one and three spaces both showed the folder path before
        Context context = ApplicationProvider.getApplicationContext();
        String nbsp = String.valueOf((char) 0x00A0);

        assertEquals("[SP][SP][SP]", FmUtils.getRowTitle(context, "   ", "/sdcard/   ").toString());
        assertEquals("[SP]", FmUtils.getRowTitle(context, " ", "/sdcard/ ").toString());
        assertEquals("[NBSP]", FmUtils.getRowTitle(context, nbsp, "/sdcard/" + nbsp).toString());
        assertEquals("[TAB]", FmUtils.getRowTitle(context, "\t", "/sdcard/\t").toString());
    }

    @Test
    public void rowTitlesOfOrdinaryNamesAreUnchanged() {
        Context context = ApplicationProvider.getApplicationContext();

        CharSequence title = FmUtils.getRowTitle(context, "notes.txt", "/sdcard/notes.txt");
        assertEquals("notes.txt", title.toString());
        assertFalse(title instanceof Spanned);
        assertEquals("' =payload name.txt",
                FmUtils.getRowTitle(context, "\t=payload\nname.txt", "/sdcard/x").toString());
        assertEquals("/sdcard/root", FmUtils.getRowTitle(context, null, "/sdcard/root").toString());
        assertEquals("/", FmUtils.getRowTitle(context, "", "/").toString());
        assertEquals("notes.txt", FmUtils.getRowTitle(context,
                Paths.get(Uri.parse("file:///sdcard/notes.txt"))).toString());
    }
}
