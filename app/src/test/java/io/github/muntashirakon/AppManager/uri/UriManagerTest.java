// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.uri;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;
import android.os.UserHandleHidden;

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
    public void uriGrantRoundTripsUserNullHandleFromModernAndroid() {
        // On current Android a captured grant has userHandle == USER_NULL because grants are
        // keyed by explicit source/target user IDs. Such a grant must survive a backup
        // round-trip instead of being silently dropped on restore.
        UriManager.UriGrant grant = new UriManager.UriGrant(0, 0, UserHandleHidden.USER_NULL,
                "com.source", "com.target",
                Uri.parse("content://example/items"),
                false, Intent.FLAG_GRANT_READ_URI_PERMISSION, 1234L);

        UriManager.UriGrant parsed = UriManager.UriGrant.unflattenFromString(grant.flattenToString());
        assertEquals(UserHandleHidden.USER_NULL, parsed.userHandle);
        assertEquals(0, parsed.sourceUserId);
        assertEquals("com.target", parsed.targetPkg);

        // Only USER_NULL is allowed as a negative; other negatives remain invalid.
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "0,0,-2,com.source,com.target,false,1,1234,content://example/items"));
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "0,0,bad,com.source,com.target,false,1,1234,content://example/items"));
    }

    @Test
    public void uriGrantRejectsTruncatedRows() {
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString("0,10,10,com.source"));
    }

    @Test
    public void uriGrantRejectsMalformedPackageFields() {
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "0,10,10,com.source,shifted,true,1,1234,content://example/items"));
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "0,10,10,com.source,not a package,true,1,1234,content://example/items"));
    }

    @Test
    public void uriGrantRejectsMalformedScalarFields() {
        UriManager.UriGrant parsed = UriManager.UriGrant.unflattenFromString(
                " 0 ,10,10,com.source,com.target, TRUE ,1, 1234 ,content://example/items");
        assertEquals(0, parsed.sourceUserId);
        assertTrue(parsed.prefix);
        assertEquals(1234L, parsed.createdTime);

        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "bad,10,10,com.source,com.target,true,1,1234,content://example/items"));
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "-1,10,10,com.source,com.target,true,1,1234,content://example/items"));
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "0,10,10,com.source,com.target,maybe,1,1234,content://example/items"));
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "0,10,10,com.source,com.target,true,-1,1234,content://example/items"));
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "0,10,10,com.source,com.target,true,1,-1,content://example/items"));
        assertThrows(IllegalArgumentException.class,
                () -> UriManager.UriGrant.unflattenFromString(
                        "0,10,10,com.source,com.target,true,1,bad,content://example/items"));
    }
}
