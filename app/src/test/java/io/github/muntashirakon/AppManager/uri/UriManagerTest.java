// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.uri;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UriManagerTest {
    @Test
    public void uriGrantRoundTripsUriContainingCommas() {
        UriManager.UriGrant grant = new UriManager.UriGrant(0, 10, 10,
                "com.source", "com.target",
                Uri.parse("content://example/items/a,b?value=1,2"),
                true, Intent.FLAG_GRANT_READ_URI_PERMISSION, 1234L);

        UriManager.UriGrant parsed = UriManager.UriGrant.unflattenFromString(grant.flattenToString());

        assertEquals(0, parsed.sourceUserId);
        assertEquals(10, parsed.targetUserId);
        assertEquals(10, parsed.userHandle);
        assertEquals("com.source", parsed.sourcePkg);
        assertEquals("com.target", parsed.targetPkg);
        assertTrue(parsed.prefix);
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, parsed.modeFlags);
        assertEquals(1234L, parsed.createdTime);
        assertEquals("content://example/items/a,b?value=1,2", parsed.uri.toString());
    }

    @Test
    public void uriGrantRejectsTruncatedRows() {
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString("0,10,10,com.source"));
    }
}
