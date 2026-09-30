// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocalServerManagerTokenRedactionTest {
    private static final String TOKEN = "9b3e7d41c2a85f06e1d4b7a39c58f20e6ad13b7c48e5f92a07c6d3e8b1f45a92";
    private static final String LAUNCH = "sh /data/local/tmp/io.github.sysadmindoc.AppManagerNG.debug/run_server.sh 62001 "
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
                "Starting amng_server as 2000:2000",
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

        assertTrue(line, line.contains("/run_server.sh 62001 <redacted> || echo"));
    }

    @Test
    public void everyEchoedLineThatHoldsTwoTokenCharactersIsKeptOutOfTheLogAtAnyWidth() {
        String prompt = "r0q:/ $ ";
        int tokenStart = LAUNCH.indexOf(TOKEN);
        int tokenEnd = tokenStart + TOKEN.length();
        for (int width = 20; width <= 200; ++width) {
            // Before mksh has the line: the terminal's own echo, whole
            assertTrue(LocalServerManager.isEcho(LAUNCH, LAUNCH, TOKEN));
            // mksh's first draw: the prompt and as much of the command as fits
            int firstEnd = Math.min(LAUNCH.length(), Math.max(0, width - prompt.length() - 1));
            assertEchoHandled(prompt + LAUNCH.substring(0, firstEnd), 0, firstEnd, tokenStart, tokenEnd, width);
            // Then a scrolled window from anywhere in the command, padded, marked and backed up to
            // the cursor, with what was typed next
            int span = Math.max(1, width / 2);
            for (int from = 1; from < LAUNCH.length(); ++from) {
                int to = Math.min(LAUNCH.length(), from + span);
                int typed = Math.min(LAUNCH.length(), to + 10);
                String line = LAUNCH.substring(from, to) + repeat(' ', width / 3) + '<'
                        + repeat('\b', width / 3 + 1) + LAUNCH.substring(to, typed);
                assertEchoHandled(line, from, typed, tokenStart, tokenEnd, width);
            }
        }
    }

    @Test
    public void whatTheLauncherAndServerPrintIsNotTakenForEcho() {
        String[] lines = {
                "uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),1007(log)",
                "Starting amng_server as 2000:2000...",
                "Jar path: /data/local/tmp/io.github.sysadmindoc.AppManagerNG.debug/am.jar",
                "Local server has started.",
                "r0q:/ $ Arguments: [path:62001,app:io.github.sysadmindoc.AppManagerNG.debug,bgrun:1,debug:1,token:" + TOKEN + "]",
                "Success! Server has started.",
                "Error! Could not run the server launcher.",
                "Command:  export PATH=:$PATH",
                "",
        };
        for (String line : lines) {
            assertFalse(line, LocalServerManager.isEcho(line, LAUNCH, TOKEN));
        }
    }

    private static void assertEchoHandled(String line, int from, int to, int tokenStart, int tokenEnd, int width) {
        int overlap = Math.min(to, tokenEnd) - Math.max(from, tokenStart);
        if (overlap >= 2) {
            assertTrue("width " + width + ": " + line.replace('\b', '~'), LocalServerManager.isEcho(line, LAUNCH, TOKEN));
        }
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }

    private static void assertNoTokenPiece(String line) {
        for (int i = 0; i + 4 <= TOKEN.length(); ++i) {
            assertFalse(line, line.contains(TOKEN.substring(i, i + 4)));
        }
    }
}
