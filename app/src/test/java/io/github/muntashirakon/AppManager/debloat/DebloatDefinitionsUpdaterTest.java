// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class DebloatDefinitionsUpdaterTest {
    @Test
    public void sha256UsesLowercaseHex() {
        String digest = DebloatDefinitionsUpdater.sha256("debloat".getBytes(StandardCharsets.UTF_8));

        assertEquals("86dead8896a8dce1f6033a2226b19f86a039fafc4d6faccb16486ac0309b8a95", digest);
    }

    @Test
    public void approvedUrlRequiresPinnedGithubRawRepo() {
        assertTrue(DebloatDefinitionsUpdater.isApprovedRawGithubUrl(
                "https://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/app/src/main/assets/debloat.json"));

        assertFalse(DebloatDefinitionsUpdater.isApprovedRawGithubUrl(
                "http://raw.githubusercontent.com/SysAdminDoc/AppManagerNG/main/app/src/main/assets/debloat.json"));
        assertFalse(DebloatDefinitionsUpdater.isApprovedRawGithubUrl(
                "https://example.com/SysAdminDoc/AppManagerNG/main/app/src/main/assets/debloat.json"));
        assertFalse(DebloatDefinitionsUpdater.isApprovedRawGithubUrl(
                "https://raw.githubusercontent.com/Other/AppManagerNG/main/app/src/main/assets/debloat.json"));
    }

    @Test
    public void datasetValidationRejectsMissingRequiredFields() {
        Gson gson = new Gson();
        String suggestionsJson = "[{\"_id\":\"browser\",\"id\":\"org.mozilla.fennec_fdroid\",\"label\":\"Fennec\"}]";

        assertTrue(DebloatDefinitionsUpdater.isValidDatasetPair(gson,
                "[{\"id\":\"com.example.bloat\",\"type\":\"oem\",\"description\":\"Test\",\"removal\":\"safe\"}]",
                suggestionsJson));
        assertFalse(DebloatDefinitionsUpdater.isValidDatasetPair(gson,
                "[{\"type\":\"oem\",\"description\":\"Test\",\"removal\":\"safe\"}]",
                suggestionsJson));
    }
}
