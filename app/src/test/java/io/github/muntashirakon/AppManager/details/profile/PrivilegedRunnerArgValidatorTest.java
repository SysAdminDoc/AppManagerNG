// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class PrivilegedRunnerArgValidatorTest {

    @Test
    public void simpleArgvIsAccepted() {
        String[] argv = {"simpleperf", "record", "--app", "com.example", "-o", "/data/local/tmp/perf.data"};
        PrivilegedRunnerArgValidator.validateArgv(argv);
    }

    @Test
    public void rejectsArgvLongerThanCeiling() {
        String[] argv = new String[PrivilegedRunnerArgValidator.MAX_ARGV_LENGTH + 1];
        for (int i = 0; i < argv.length; ++i) argv[i] = "x";
        try {
            PrivilegedRunnerArgValidator.validateArgv(argv);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("ARGV_TOO_LONG"));
        }
    }

    @Test
    public void rejectsControlCharacterInArgument() {
        try {
            PrivilegedRunnerArgValidator.validateArgument("hello\tworld");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("CONTROL_CHARACTER"));
        }
    }

    @Test
    public void rejectsShellMetacharacters() {
        String[] meta = {"`", "$", "\"", "'", ";", "&", "|", "<", ">", "*", "?", "!", "\\", "\n", "\r"};
        for (String m : meta) {
            String arg = "safe" + m + "tail";
            assertEquals("metachar " + m + " missed",
                    PrivilegedRunnerArgValidator.Rejection.SHELL_METACHARACTER,
                    PrivilegedRunnerArgValidator.classifyArgument(arg));
        }
    }

    @Test
    public void rejectsNullArgument() {
        assertEquals(PrivilegedRunnerArgValidator.Rejection.NULL_ARGUMENT,
                PrivilegedRunnerArgValidator.classifyArgument(null));
    }

    @Test
    public void rejectsEmptyArgument() {
        assertEquals(PrivilegedRunnerArgValidator.Rejection.EMPTY_ARGUMENT,
                PrivilegedRunnerArgValidator.classifyArgument(""));
    }

    @Test
    public void rejectsArgumentOverLengthCeiling() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PrivilegedRunnerArgValidator.MAX_ARG_LENGTH + 1; ++i) sb.append('x');
        assertEquals(PrivilegedRunnerArgValidator.Rejection.TOO_LONG,
                PrivilegedRunnerArgValidator.classifyArgument(sb.toString()));
    }

    @Test
    public void pathTraversalIsRejectedForPathArguments() {
        assertEquals(PrivilegedRunnerArgValidator.Rejection.PATH_TRAVERSAL,
                PrivilegedRunnerArgValidator.classifyPath("/data/local/tmp/../private"));
        assertEquals(PrivilegedRunnerArgValidator.Rejection.PATH_TRAVERSAL,
                PrivilegedRunnerArgValidator.classifyPath("../../etc/passwd"));
        assertEquals(PrivilegedRunnerArgValidator.Rejection.PATH_TRAVERSAL,
                PrivilegedRunnerArgValidator.classifyPath(".."));
    }

    @Test
    public void pathTraversalDoesNotMistakeDotDotInsideFilenameSegments() {
        // A filename containing two dots somewhere in the middle is fine;
        // only a complete ".." segment counts as traversal.
        assertEquals(PrivilegedRunnerArgValidator.Rejection.OK,
                PrivilegedRunnerArgValidator.classifyPath("/data/local/tmp/perf..data"));
        assertEquals(PrivilegedRunnerArgValidator.Rejection.OK,
                PrivilegedRunnerArgValidator.classifyPath("/data/.config/foo"));
        assertEquals(PrivilegedRunnerArgValidator.Rejection.OK,
                PrivilegedRunnerArgValidator.classifyPath("trace.perfetto-trace"));
    }

    @Test
    public void pathTraversalRejectsLeadingDotDotSegmentAtRootOrSubpath() {
        assertEquals(PrivilegedRunnerArgValidator.Rejection.PATH_TRAVERSAL,
                PrivilegedRunnerArgValidator.classifyPath("/data/local/tmp/.."));
        assertEquals(PrivilegedRunnerArgValidator.Rejection.PATH_TRAVERSAL,
                PrivilegedRunnerArgValidator.classifyPath("a/../b"));
    }

    @Test
    public void validatePackageNameAcceptsCanonicalIds() {
        assertTrue(PrivilegedRunnerArgValidator.isValidPackageName("com.android.chrome"));
        assertTrue(PrivilegedRunnerArgValidator.isValidPackageName("io.github.sysadmindoc.AppManagerNG"));
        assertTrue(PrivilegedRunnerArgValidator.isValidPackageName("a.b"));
    }

    @Test
    public void validatePackageNameRejectsMalformedInputs() {
        assertFalse(PrivilegedRunnerArgValidator.isValidPackageName(""));
        assertFalse(PrivilegedRunnerArgValidator.isValidPackageName("noDots"));
        assertFalse(PrivilegedRunnerArgValidator.isValidPackageName(".leading.dot"));
        assertFalse(PrivilegedRunnerArgValidator.isValidPackageName("trailing.dot."));
        assertFalse(PrivilegedRunnerArgValidator.isValidPackageName("dash-in.name"));
        assertFalse(PrivilegedRunnerArgValidator.isValidPackageName("space in.name"));
        assertFalse(PrivilegedRunnerArgValidator.isValidPackageName("9.starts.with.digit"));
        assertFalse(PrivilegedRunnerArgValidator.isValidPackageName("com..double.dot"));
    }

    @Test
    public void argvWithBackgroundChainIsRejected() {
        String[] argv = {"perfetto", "-c", "cfg.txt", "--txt", "-o", "trace; rm -rf /"};
        try {
            PrivilegedRunnerArgValidator.validateArgv(argv);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("SHELL_METACHARACTER"));
            assertTrue("index of unsafe element must surface",
                    expected.getMessage().contains("index=5"));
        }
    }

    @Test
    public void rejectionMessageTruncatesLongDescribes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; ++i) sb.append('x');
        sb.append('|'); // make the arg unsafe so we exit via the metachar branch
        try {
            PrivilegedRunnerArgValidator.validateArgument(sb.toString());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            String msg = expected.getMessage();
            assertNotNull(msg);
            // describe() truncates after 60 chars and appends "... (len=N)"
            assertTrue("message should be truncated",
                    msg.contains("...") && msg.contains("len=201"));
        }
    }

    @Test
    public void isSafePathAndIsSafeArgumentMatchClassifierOk() {
        assertTrue(PrivilegedRunnerArgValidator.isSafeArgument("--duration"));
        assertTrue(PrivilegedRunnerArgValidator.isSafeArgument("10"));
        assertTrue(PrivilegedRunnerArgValidator.isSafePath("/data/local/tmp/trace.perfetto"));
        assertFalse(PrivilegedRunnerArgValidator.isSafePath("/data/$HOME/private"));
        assertFalse(PrivilegedRunnerArgValidator.isSafeArgument("rm -rf /"));
    }
}
