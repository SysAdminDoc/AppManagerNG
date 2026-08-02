// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * The privileged server's parameters arrive on its command line. Parsing them used to index
 * {@code param[1]} straight after {@code split(":")}, so an element without a separator threw.
 */
public class ConfigParamsTest {
    @Test
    public void launcherShapedArgumentsParse() {
        // The launcher script concatenates fragments that each start with a comma, so the
        // serialized form legitimately begins with "path:" and carries an empty leading entry
        // in some orderings.
        ConfigParams params = ConfigParams.parse(
                "path:1234,app:io.github.sysadmindoc.AppManagerNG,bgrun:1,token:abc123");
        assertEquals("1234", params.getPath());
        assertEquals("io.github.sysadmindoc.AppManagerNG", params.getAppName());
        assertTrue(params.isRunInBackground());
        assertEquals("abc123", params.getToken());
        assertFalse(params.isIsDebug());
    }

    @Test
    public void emptyEntriesAreSkipped() {
        ConfigParams params = ConfigParams.parse(",app:pkg,,bgrun:1,");
        assertEquals("pkg", params.getAppName());
        assertTrue(params.isRunInBackground());
    }

    @Test
    public void emptyInputYieldsEmptyParams() {
        ConfigParams params = ConfigParams.parse("");
        assertNull(params.getAppName());
        assertNull(params.getToken());
    }

    @Test
    public void valueMayContainSeparators() {
        // split(":") used to truncate everything after the second colon.
        ConfigParams params = ConfigParams.parse("path:/data/local/tmp:9,token:a:b:c");
        assertEquals("/data/local/tmp:9", params.getPath());
        assertEquals("a:b:c", params.getToken());
    }

    @Test
    public void elementWithoutASeparatorIsRejected() {
        assertRejected("app:pkg,bgrun");
        assertRejected("nonsense");
    }

    @Test
    public void elementWithAnEmptyKeyIsRejected() {
        assertRejected(":value");
        assertRejected("app:pkg,:value");
    }

    @Test
    public void theRejectionMessageDoesNotEchoTheArgument() {
        // The serialized parameters carry the privileged-channel token; a parse failure must
        // not put an unattributable fragment of it into the log.
        try {
            ConfigParams.parse("supersecrettokenvalue");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertFalse(e.getMessage(), e.getMessage().contains("supersecrettokenvalue"));
        }
    }

    @Test
    public void redactHidesTheToken() {
        String redacted = ConfigParams.redact("path:1234,app:pkg,token:supersecret");
        assertFalse(redacted, redacted.contains("supersecret"));
        assertTrue(redacted, redacted.contains("path:1234"));
    }

    private static void assertRejected(String serializedParams) {
        try {
            ConfigParams.parse(serializedParams);
            fail("Expected IllegalArgumentException for " + serializedParams);
        } catch (IllegalArgumentException expected) {
            // The launcher logs this and exits instead of crashing mid-parse.
        }
    }
}
