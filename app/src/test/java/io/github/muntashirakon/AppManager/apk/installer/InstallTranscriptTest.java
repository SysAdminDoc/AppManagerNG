// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InstallTranscriptTest {
    @Test
    public void redactSourceUriStripsLocalFilePath() {
        assertEquals("file://<redacted>",
                InstallTranscript.redactSourceUri("file:///storage/emulated/0/Download/foo.apk"));
        assertEquals("file://<redacted>",
                InstallTranscript.redactSourceUri("file:/data/local/tmp/install.apk"));
    }

    @Test
    public void redactSourceUriKeepsContentAuthorityAndDropsDocumentId() {
        String redacted = InstallTranscript.redactSourceUri(
                "content://com.android.providers.downloads.documents/document/raw:/storage/abc.apk");
        assertEquals("content://com.android.providers.downloads.documents/<redacted>", redacted);
    }

    @Test
    public void redactSourceUriKeepsHttpHostAndDropsPath() {
        assertEquals("https://example.com/<redacted>",
                InstallTranscript.redactSourceUri("https://example.com/downloads/private/foo.apk"));
        assertEquals("http://example.com:8080/<redacted>",
                InstallTranscript.redactSourceUri("http://example.com:8080/downloads/foo.apk"));
    }

    @Test
    public void redactSourceUriKeepsPackageSchemeVerbatim() {
        assertEquals("package:com.example.foo",
                InstallTranscript.redactSourceUri("package:com.example.foo"));
    }

    @Test
    public void redactSourceUriHandlesEmptyAndMalformed() {
        assertEquals("unknown", InstallTranscript.redactSourceUri(null));
        assertEquals("unknown", InstallTranscript.redactSourceUri(""));
        assertEquals("<redacted>", InstallTranscript.redactSourceUri("not-a-uri"));
        assertEquals("<redacted>", InstallTranscript.redactSourceUri(":scheme-empty"));
    }

    @Test
    public void redactSourceUriDoesNotRedactWhenNoPathPresent() {
        assertEquals("content://authority",
                InstallTranscript.redactSourceUri("content://authority"));
    }

    @Test
    public void redactSourceUriStripsUserInfoFromAuthority() {
        // Credentials in the URI userinfo (user[:password]@host) must never make it into the
        // shareable transcript, even though they sit inside the authority component the redactor
        // otherwise keeps verbatim.
        assertEquals("https://example.com/<redacted>",
                InstallTranscript.redactSourceUri("https://user:secret@example.com/path"));
        assertEquals("https://example.com",
                InstallTranscript.redactSourceUri("https://user@example.com"));
    }

    @Test
    public void redactSourceUriStripsQueryAndFragment() {
        // Download providers append signed tokens / document IDs to the query string; an APK
        // saved with a path-less URL like "https://host?token=…" would previously leak the token
        // verbatim into the issue transcript.
        assertEquals("https://example.com/<redacted>",
                InstallTranscript.redactSourceUri("https://example.com?token=abc123"));
        assertEquals("https://example.com/<redacted>",
                InstallTranscript.redactSourceUri("https://example.com#secret"));
        assertEquals("https://example.com/<redacted>",
                InstallTranscript.redactSourceUri("https://example.com/path?token=abc&doc=42#frag"));
    }

    @Test
    public void toShareableTextRendersStableLineOrderWithRedaction() {
        InstallTranscript transcript = new InstallTranscript(
                "2026-05-16T12:00:00Z",
                "0.4.2 (6)",
                "Google Pixel 7 (panther)",
                "14",
                34,
                "2026-05-01",
                "arm64-v8a",
                "shizuku",
                "com.example.foo",
                -7,
                "STATUS_FAILURE_INCOMPATIBLE_ROM",
                "INSTALL_FAILED_INVALID_APK: Failed parse",
                "content://com.android.providers.downloads.documents/document/12345",
                true);
        String text = transcript.toShareableText();

        // Header + horizontal rule
        assertTrue(text.startsWith("AppManagerNG install diagnostic\n================================\n"));
        // Every required field present, in the documented order
        assertTrue(text.contains("Timestamp: 2026-05-16T12:00:00Z"));
        assertTrue(text.contains("AppManagerNG version: 0.4.2 (6)"));
        assertTrue(text.contains("Device: Google Pixel 7 (panther)"));
        assertTrue(text.contains("Android: 14 (API 34, patch 2026-05-01)"));
        assertTrue(text.contains("ABI: arm64-v8a"));
        assertTrue(text.contains("Active mode: shizuku"));
        assertTrue(text.contains("Package: com.example.foo"));
        assertTrue(text.contains("Status: STATUS_FAILURE_INCOMPATIBLE_ROM (-7)"));
        assertTrue(text.contains("Status message: INSTALL_FAILED_INVALID_APK: Failed parse"));
        // Source is redacted when redactSource = true; document id must not leak
        assertTrue(text.contains("Source: content://com.android.providers.downloads.documents/<redacted>"));
        assertFalse(text.contains("12345"));
    }

    @Test
    public void toShareableTextOmitsStatusMessageLineWhenEmpty() {
        InstallTranscript transcript = new InstallTranscript(
                "2026-05-16T12:00:00Z", "0.4.2 (6)", "Generic", "14", 34, "2026-05-01",
                "arm64-v8a", "auto", "com.example.foo", 1, "STATUS_SUCCESS", null, null, true);
        String text = transcript.toShareableText();
        assertFalse(text.contains("Status message:"));
        assertTrue(text.contains("Source: unknown"));
    }

    @Test
    public void toShareableTextScrubsStatusMessage() {
        InstallTranscript transcript = new InstallTranscript(
                "2026-05-16T12:00:00Z", "0.4.2 (6)", "Generic", "14", 34, "2026-05-01",
                "arm64-v8a", "auto", "com.example.foo", -2, "STATUS_FAILURE_INVALID",
                "Failed com.example.secret /storage/emulated/0/Download/private.apk "
                        + "content://com.android.providers.downloads.documents/document/123 uid=10345 "
                        + "person@example.com",
                null,
                true);
        String text = transcript.toShareableText();

        assertTrue(text.contains("Status message: Failed <package> <path> content://<redacted> "
                + "uid=<redacted> <email>"));
        assertFalse(text.contains("com.example.secret"));
        assertFalse(text.contains("private.apk"));
        assertFalse(text.contains("document/123"));
        assertFalse(text.contains("uid=10345"));
        assertFalse(text.contains("person@example.com"));
    }

    @Test
    public void toShareableTextLeavesSourceVerbatimWhenOptedIn() {
        InstallTranscript transcript = new InstallTranscript(
                "2026-05-16T12:00:00Z", "0.4.2 (6)", "Generic", "14", 34, "2026-05-01",
                "arm64-v8a", "auto", "com.example.foo", -2, "STATUS_FAILURE_INVALID", null,
                "file:///storage/emulated/0/Download/foo.apk",
                false);
        String text = transcript.toShareableText();
        assertTrue(text.contains("Source: file:///storage/emulated/0/Download/foo.apk"));
    }

    @Test
    public void toShareableTextFallsBackToUnknownForEmptyFields() {
        InstallTranscript transcript = new InstallTranscript(
                "", "", "", "", 0, "", "", "", "", 0, "", null, null, true);
        String text = transcript.toShareableText();
        assertTrue(text.contains("Timestamp: unknown"));
        assertTrue(text.contains("AppManagerNG version: unknown"));
        assertTrue(text.contains("Device: unknown"));
        assertTrue(text.contains("Android: unknown (API 0, patch unknown)"));
        assertTrue(text.contains("Status: unknown (0)"));
    }

    @Test
    public void toShareableTextIncludesExplanationAndRecoveryHintWithRawStatus() {
        InstallTranscript transcript = new InstallTranscript(
                "2026-05-16T12:00:00Z", "0.4.2 (6)", "Generic", "14", 34, "2026-05-01",
                "arm64-v8a", "auto", "com.example.foo", -2, "STATUS_FAILURE_SECURITY",
                "Could not access the APK files.",
                "Grant access to the APK source and retry.",
                null,
                null,
                true);
        String text = transcript.toShareableText();

        assertTrue(text.contains("Status: STATUS_FAILURE_SECURITY (-2)"));
        assertTrue(text.contains("Status explanation: Could not access the APK files."));
        assertTrue(text.contains("Recovery hint: Grant access to the APK source and retry."));
    }
}
