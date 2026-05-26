// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.components;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.database.MatrixCursor;
import android.net.Uri;
import android.text.Editable;
import android.text.SpannableStringBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ProviderQueryUtilsTest {
    @Test
    public void parseAuthoritiesDeduplicatesAndTrims() {
        assertEquals(Arrays.asList("settings", "com.example.provider"),
                ProviderQueryUtils.parseAuthorities(" settings ; com.example.provider ; settings "));
    }

    @Test
    public void buildContentUriUsesSafeSegmentsAndQueryParameters() {
        Uri uri = ProviderQueryUtils.buildContentUri("settings", "/system/display",
                Arrays.asList(new ProviderQueryUtils.QueryParameter("name", "screen brightness")));

        assertEquals("content://settings/system/display?name=screen%20brightness", uri.toString());
    }

    @Test
    public void buildContentUriRejectsAuthorityWithSchemeOrPath() {
        assertThrows(IllegalArgumentException.class, () ->
                ProviderQueryUtils.buildContentUri("content://settings/system", null, java.util.Collections.emptyList()));
    }

    @Test
    public void parseProjectionAcceptsCommaAndLineSeparatedColumns() {
        Editable editable = new SpannableStringBuilder("_id, name\nvalue\nname");

        assertArrayEquals(new String[]{"_id", "name", "value"}, ProviderQueryUtils.parseProjection(editable));
    }

    @Test
    public void selectionValidationRequiresArgumentParity() {
        ProviderQueryUtils.validateSelection("name = ? AND value != '?'", new String[]{"enabled"});

        assertThrows(IllegalArgumentException.class, () ->
                ProviderQueryUtils.validateSelection("name = ? AND value = ?", new String[]{"enabled"}));
        assertThrows(IllegalArgumentException.class, () ->
                ProviderQueryUtils.validateSelection("name = 'enabled'; DELETE", null));
    }

    @Test
    public void snapshotCursorCapsRowsAndFormatsValues() {
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "name", "payload", "missing"});
        cursor.addRow(new Object[]{1, "alpha\nbeta", new byte[]{1, 2, 3}, null});
        cursor.addRow(new Object[]{2, "gamma", new byte[]{4}, null});

        ProviderQueryUtils.QueryResult result = ProviderQueryUtils.snapshotCursor(Uri.parse("content://settings/system"),
                cursor, 1);

        assertTrue(result.truncated);
        assertEquals(1, result.rows.size());
        assertEquals("1", result.rows.get(0).get(0));
        assertEquals("alpha\nbeta", result.rows.get(0).get(1));
        assertEquals("<blob 3 B>", result.rows.get(0).get(2));
        assertEquals("NULL", result.rows.get(0).get(3));
        assertEquals("_id\tname\tpayload\tmissing\n1\talpha beta\t<blob 3 B>\tNULL\n",
                ProviderQueryUtils.toTsv(result));
    }

    @Test
    public void unprivilegedQueryRequiresReadableCurrentProfileProvider() {
        assertTrue(ProviderQueryUtils.canUseUnprivilegedQuery(true, null, false, "com.example",
                "io.github.muntashirakon.AppManager", 0, 0));
        assertTrue(ProviderQueryUtils.canUseUnprivilegedQuery(true, "com.example.READ", true, "com.example",
                "io.github.muntashirakon.AppManager", 0, 0));
        assertTrue(ProviderQueryUtils.canUseUnprivilegedQuery(false, "com.example.READ", false,
                "io.github.muntashirakon.AppManager", "io.github.muntashirakon.AppManager", 0, 0));
        assertFalse(ProviderQueryUtils.canUseUnprivilegedQuery(true, "com.example.READ", false, "com.example",
                "io.github.muntashirakon.AppManager", 0, 0));
        assertFalse(ProviderQueryUtils.canUseUnprivilegedQuery(true, null, false, "com.example",
                "io.github.muntashirakon.AppManager", 10, 0));
    }

    @Test
    public void parseQueryParametersAcceptsEmptyValues() {
        List<ProviderQueryUtils.QueryParameter> parameters = ProviderQueryUtils.parseQueryParameters(
                new SpannableStringBuilder("limit=50\nnotify"));

        assertEquals("limit", parameters.get(0).name);
        assertEquals("50", parameters.get(0).value);
        assertEquals("notify", parameters.get(1).name);
        assertEquals("", parameters.get(1).value);
    }
}
