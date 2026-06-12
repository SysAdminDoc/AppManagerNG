// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class ScannerViewModelTest {
    @Test
    public void sanitizeReportFilePartKeepsPortableCharacters() {
        assertEquals("com.example_app_1.apk",
                ScannerViewModel.sanitizeReportFilePart(" com.example/app:1.apk "));
        assertEquals("", ScannerViewModel.sanitizeReportFilePart(null));
    }

    @Test
    public void signatureMatchesToJsonExportsReadableMatchRows() throws Exception {
        SignatureInfo signatureInfo = new SignatureInfo("com.example.analytics.", "Example Analytics");
        signatureInfo.setCount(3);
        signatureInfo.addClass("com.example.analytics.Sdk");

        JSONArray items = ScannerViewModel.signatureMatchesToJson(Collections.singletonList(signatureInfo));
        JSONObject item = items.getJSONObject(0);

        assertEquals("Example Analytics", item.getString("label"));
        assertEquals("com.example.analytics.", item.getString("signature"));
        assertEquals("Tracker", item.getString("type"));
        assertEquals(3, item.getInt("match_count"));
        assertEquals("com.example.analytics.Sdk", item.getJSONArray("classes").getString(0));
    }

    @Test
    public void buildTrackerDatabaseJsonExportsFreshnessMetadata() throws Exception {
        JSONObject database = ScannerViewModel.buildTrackerDatabaseJson(
                "2026-04-30", 1985, "2026-06-11", 42L);

        assertEquals("2026-04-30", database.getString("bundled_version"));
        assertEquals(1985, database.getInt("signature_count"));
        assertEquals("2026-06-11", database.getString("latest_checked_version"));
        assertEquals(42L, database.getLong("last_check_time"));
    }

    @Test
    public void buildTrackerDatabaseJsonUsesNullsWhenNeverChecked() throws Exception {
        JSONObject database = ScannerViewModel.buildTrackerDatabaseJson(
                "2026-04-30", 1985, "", 0L);

        assertEquals(JSONObject.NULL, database.get("latest_checked_version"));
        assertEquals(JSONObject.NULL, database.get("last_check_time"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void onlineReportFetchPolicyRequiresNetworkAndDigests() {
        Pair<String, String>[] digests = new Pair[]{
                new Pair<>("MD5", "md5"),
                new Pair<>("SHA-1", "sha1"),
                new Pair<>("SHA-256", "sha256")
        };

        assertFalse(ScannerViewModel.shouldFetchPithusReport(digests, false));
        assertFalse(ScannerViewModel.shouldFetchPithusReport(null, true));
        assertTrue(ScannerViewModel.shouldFetchPithusReport(digests, true));
        assertFalse(ScannerViewModel.shouldFetchVirusTotalReport(null, digests, true, true));
        assertFalse(ScannerViewModel.shouldFetchVirusTotalReport(null, digests, false, true));
    }
}
