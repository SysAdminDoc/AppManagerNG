// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class FmProviderTest {
    private final String cpFile = "content://" + FmProvider.AUTHORITY + "/storage/emulated/0/AppManager";
    private final String noFile = "file:///storage/emulated/0/AppManager";

    private final String cpContent = "content://" + FmProvider.AUTHORITY + "/!com.authority/primary%3Adocument/AppManager";
    private final String noContent = "content://com.authority/primary%3Adocument/AppManager";

    @Test
    public void getContentUriForFile() {
        Uri uri = FmProvider.getContentUri(Uri.parse(noFile));
        assertEquals(cpFile, uri.toString());
    }

    @Test
    public void getContentUriForContent() {
        Uri uri = FmProvider.getContentUri(Uri.parse(noContent));
        assertEquals(cpContent, uri.toString());
    }

    @Test
    public void getContentUriForVfs() {
        Uri uri = FmProvider.getContentUri(Uri.parse("vfs://12/lib/arm64-v8a/libfoo.so"));
        assertEquals("content://" + FmProvider.AUTHORITY + "/!!12/lib/arm64-v8a/libfoo.so", uri.toString());
    }

    @Test
    public void getContentUriLeavesItsOwnUrisAlone() {
        // Upstream 9345675c1: these used to gain another "!<authority>" layer on every pass
        assertEquals(cpFile, FmProvider.getContentUri(Uri.parse(cpFile)).toString());
        assertEquals(cpContent, FmProvider.getContentUri(Uri.parse(cpContent)).toString());
    }

    @Test
    public void getContentUriIsIdempotent() {
        for (String source : new String[]{noFile, noContent, "vfs://12/lib/arm64-v8a/libfoo.so"}) {
            Uri once = FmProvider.getContentUri(Uri.parse(source));
            assertEquals(source, once, FmProvider.getContentUri(once));
        }
    }

    @Test
    public void getFileProviderPathForFile() {
        Uri uri = FmProvider.getFileProviderPathInternal(Uri.parse(cpFile));
        assertEquals(noFile, uri.toString());
    }

    @Test
    public void getFileProviderPathForContent() {
        Uri uri = FmProvider.getFileProviderPathInternal(Uri.parse(cpContent));
        assertEquals(noContent, uri.toString());
    }
}