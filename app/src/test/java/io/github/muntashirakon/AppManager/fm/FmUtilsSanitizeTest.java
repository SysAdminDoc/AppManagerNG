// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** sanitizeContentInput takes URIs from other apps' intents, so it must not throw on odd ones. */
@RunWith(RobolectricTestRunner.class)
public class FmUtilsSanitizeTest {
    @Test
    public void anOpaqueFileUriIsRefusedInsteadOfThrowing() {
        // file:x.apk has no path; normalizing it used to throw a NullPointerException.
        assertNull(FmUtils.sanitizeContentInput(Uri.parse("file:x.apk")));
    }

    @Test
    public void anOrdinaryFileUriIsKept() {
        assertEquals(Uri.parse("file:///sdcard/Download/app.apk"),
                FmUtils.sanitizeContentInput(Uri.parse("file:///sdcard/Download/app.apk")));
    }

    @Test
    public void aUriWithoutASchemeIsRefused() {
        assertNull(FmUtils.sanitizeContentInput(Uri.parse("/sdcard/Download/app.apk")));
    }
}
