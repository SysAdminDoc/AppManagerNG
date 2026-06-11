// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;

import org.junit.Test;

public class PithusTest {
    @Test
    public void validatesSha256BeforeBuildingReportUrl() {
        assertTrue(Pithus.isValidSha256("ae05bbd31820c566543addbb0ddc7b19b05be3c098d0f7aa658ab83d6f6cd5c8"));
        assertTrue(Pithus.isValidSha256("AE05BBD31820C566543ADDBB0DDC7B19B05BE3C098D0F7AA658AB83D6F6CD5C8"));

        assertFalse(Pithus.isValidSha256(null));
        assertFalse(Pithus.isValidSha256(""));
        assertFalse(Pithus.isValidSha256("ae05bbd31820c566543addbb0ddc7b19b05be3c098d0f7aa658ab83d6f6cd5c"));
        assertFalse(Pithus.isValidSha256("ge05bbd31820c566543addbb0ddc7b19b05be3c098d0f7aa658ab83d6f6cd5c8"));
    }

    @Test
    public void onlyHttpOkMeansReportAvailable() {
        assertTrue(Pithus.isReportAvailableResponse(HttpURLConnection.HTTP_OK));

        assertFalse(Pithus.isReportAvailableResponse(HttpURLConnection.HTTP_MOVED_TEMP));
        assertFalse(Pithus.isReportAvailableResponse(HttpURLConnection.HTTP_NOT_FOUND));
        assertFalse(Pithus.isReportAvailableResponse(HttpURLConnection.HTTP_INTERNAL_ERROR));
    }
}
