// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * The log's redaction used to be a denylist of shapes — an {@code auth|token|secret|password}
 * assignment, or a UUID — so a secret that appeared in any other shape reached the file intact.
 * A value known to be secret is now removed literally, wherever and however it appears.
 */
public class FLogSecretRedactionTest {
    private static final String REDACTED = "<redacted>";
    /** Not an assignment, not a UUID: exactly what the shape patterns could not catch. */
    private static final String ODD_SHAPED_TOKEN = "Zm9vYmFyLXRva2VuLTQyMDk4";

    @After
    public void tearDown() {
        FLog.clearSecrets();
    }

    @Test
    public void aRegisteredTokenDoesNotSurviveInAnUnusualShape() {
        FLog.registerSecret(ODD_SHAPED_TOKEN);

        String sanitized = FLog.sanitize("Starting server with " + ODD_SHAPED_TOKEN + " on port 8080");
        assertFalse(sanitized, sanitized.contains(ODD_SHAPED_TOKEN));
        assertTrue(sanitized, sanitized.contains(REDACTED));
        assertTrue(sanitized, sanitized.contains("on port 8080"));
    }

    @Test
    public void everyOccurrenceIsRemoved() {
        FLog.registerSecret(ODD_SHAPED_TOKEN);

        String sanitized = FLog.sanitize(ODD_SHAPED_TOKEN + "/" + ODD_SHAPED_TOKEN
                + " again: " + ODD_SHAPED_TOKEN);
        assertFalse(sanitized, sanitized.contains(ODD_SHAPED_TOKEN));
    }

    @Test
    public void aRegisteredTokenIsRemovedFromAThrowableToo() {
        FLog.registerSecret(ODD_SHAPED_TOKEN);

        String diagnostic = FLog.formatThrowable(
                new IllegalStateException("handshake failed for " + ODD_SHAPED_TOKEN));
        assertFalse(diagnostic, diagnostic.contains(ODD_SHAPED_TOKEN));
        assertTrue(diagnostic, diagnostic.contains(REDACTED));
    }

    @Test
    public void parsingTheLaunchParametersRegistersTheToken() {
        // The one place the token becomes known on either side of the channel.
        ConfigParams.parse("path:8080,app:io.github.sysadmindoc.AppManagerNG,token:" + ODD_SHAPED_TOKEN);

        String sanitized = FLog.sanitize("server started, peer presented " + ODD_SHAPED_TOKEN);
        assertFalse(sanitized, sanitized.contains(ODD_SHAPED_TOKEN));
    }

    @Test
    public void shortValuesAreNotRegistered() {
        // Redacting a short string would blank out ordinary words without protecting anything.
        FLog.registerSecret("abc");
        FLog.registerSecret("");
        FLog.registerSecret(null);

        assertEquals("abc is fine here", FLog.sanitize("abc is fine here"));
    }

    @Test
    public void theShapePatternsStillApply() {
        // The literal list supplements the patterns; it does not replace them.
        String sanitized = FLog.sanitize("auth=hunter2 id=123e4567-e89b-12d3-a456-426614174000");
        assertFalse(sanitized, sanitized.contains("hunter2"));
        assertFalse(sanitized, sanitized.contains("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    public void theRegisteredSetIsBounded() {
        // Same length, differing in one position, so none is a substring of another.
        for (int i = 0; i < 26; ++i) {
            FLog.registerSecret("bounded-value-" + (char) ('A' + i) + "-here");
        }
        // Beyond the bound, later values are simply not held; the earlier ones still redact.
        assertFalse(FLog.sanitize("bounded-value-A-here").contains("bounded-value-A-here"));
        assertTrue(FLog.sanitize("bounded-value-Z-here").contains("bounded-value-Z-here"));
    }
}
