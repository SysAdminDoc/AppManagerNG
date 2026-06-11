// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;

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
}
