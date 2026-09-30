// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocalServerManagerTokenRedactionTest {
    private static final String TOKEN = "9b3e7d41c2a85f06e1d4b7a39c58f20e6ad13b7c48e5f92a07c6d3e8b1f45a92";
    private static final String LAUNCH = "sh /data/local/tmp/io.github.sysadmindoc.AppManagerNG.debug/run_server.sh 60001 "
            + TOKEN + " || echo \"Error! Could not run the server launcher.\"";

    @Test
    public void wholeTokenIsMasked() {
        String line = LocalServerManager.redactToken(LAUNCH, TOKEN);

        assertEquals(LAUNCH.replace(TOKEN, "<redacted>"), line);
    }

    @Test
    public void tokenSplitByTheTerminalLeavesNothingUsableInEitherLine() {
        // The shell's terminal wraps or scrolls a long command, so the echo can split the token anywhere
        int start = LAUNCH.indexOf(TOKEN);
        for (int split = start + 1; split < start + TOKEN.length(); ++split) {
            String first = LocalServerManager.redactToken(LAUNCH.substring(0, split), TOKEN);
            String second = LocalServerManager.redactToken(LAUNCH.substring(split), TOKEN);

            assertNoTokenPiece(first);
            assertNoTokenPiece(second);
        }
    }

    @Test
    public void serverOutputIsLeftAlone() {
        String[] lines = {
                "Starting am_local_server as 2000:2000",
                "Jar path: /data/local/tmp/io.github.sysadmindoc.AppManagerNG.debug/am.jar",
                "uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),1007(log)",
                "Success! Server has started.",
                "Error! Could not run the server launcher.",
        };
        for (String line : lines) {
            assertEquals(line, LocalServerManager.redactToken(line, TOKEN));
        }
    }

    @Test
    public void launchCommandKeepsItsNonSecretParts() {
        String line = LocalServerManager.redactToken(LAUNCH, TOKEN);

        assertTrue(line, line.contains("/run_server.sh 60001 <redacted> || echo"));
    }

    private static void assertNoTokenPiece(String line) {
        for (int i = 0; i + 4 <= TOKEN.length(); ++i) {
            assertFalse(line, line.contains(TOKEN.substring(i, i + 4)));
        }
    }
}
