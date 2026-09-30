// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Launching the privileged server over an ADB shell: a shell libadb closed at once gets one more
 * try (libadb-android #34), and a closed shell fails at once instead of after a minute.
 */
@RunWith(RobolectricTestRunner.class)
public class AdbLaunchShellTest {
    private static final String TOKEN = "0123456789abcdef";
    private static final String COMMAND = "sh run_server.sh 62001 " + TOKEN;

    private PipedOutputStream mShellOutput;

    @After
    public void tearDown() throws IOException {
        if (mShellOutput != null) {
            mShellOutput.close();
        }
    }

    @Test
    public void aLauncherThatSaysSuccessStartsTheServer() throws Exception {
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        InputStream shell = openShell("uid=2000(shell)\nLocal server has started.\nSuccess! Server has started.\n");

        LocalServerManager.launchInShell(shell, sent, COMMAND, TOKEN, SystemClock.elapsedRealtime(), 5, TimeUnit.SECONDS);

        assertEquals("id\n" + COMMAND + "\n", sent.toString(StandardCharsets.UTF_8.name()));
        // The shell stays open: the server writes to it for as long as it runs
        mShellOutput.write("Process: amng_server, PID: 20596\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void aShellClosedAsSoonAsItOpenedIsWorthAnotherTry() {
        long start = System.nanoTime();

        assertThrows(LocalServerManager.ShellClosedEarlyException.class, () -> LocalServerManager.launchInShell(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), COMMAND, TOKEN,
                SystemClock.elapsedRealtime(), 1, TimeUnit.MINUTES));
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10);
    }

    @Test
    public void aShellThatCannotBeWrittenToRightAwayIsWorthAnotherTry() {
        OutputStream closed = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Stream closed");
            }
        };

        IOException e = assertThrows(LocalServerManager.ShellClosedEarlyException.class, () ->
                LocalServerManager.launchInShell(openShell(""), closed, COMMAND, TOKEN,
                        SystemClock.elapsedRealtime(), 1, TimeUnit.MINUTES));
        assertEquals("Stream closed", e.getCause().getMessage());
    }

    @Test
    public void aShellThatClosesLaterFailsAtOnce() {
        long start = System.nanoTime();

        IOException e = assertThrows(IOException.class, () -> LocalServerManager.launchInShell(
                new ByteArrayInputStream("uid=2000(shell)\n".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(), COMMAND, TOKEN, SystemClock.elapsedRealtime() - 5_000,
                1, TimeUnit.MINUTES));

        assertFalse(e instanceof LocalServerManager.ShellClosedEarlyException);
        assertEquals("The ADB shell closed before the server started.", e.getMessage());
        // It used to wait out the minute
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10);
    }

    @Test
    public void anErrorFromTheLauncherIsNotAnEarlyClose() {
        // Even when the shell closes right after saying so
        IOException e = assertThrows(IOException.class, () -> LocalServerManager.launchInShell(
                new ByteArrayInputStream("Error! Could not run the server launcher.\n".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(), COMMAND, TOKEN, SystemClock.elapsedRealtime(), 1, TimeUnit.MINUTES));

        assertFalse(e instanceof LocalServerManager.ShellClosedEarlyException);
        assertEquals("Server wasn't started.", e.getMessage());
    }

    @Test
    public void aLauncherThatNeverAnswersTimesOut() {
        assertThrows(TimeoutException.class, () -> LocalServerManager.launchInShell(openShell("uid=2000(shell)\n"),
                new ByteArrayOutputStream(), COMMAND, TOKEN, SystemClock.elapsedRealtime(), 200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void onlyAQuickUnexplainedFailureIsRetried() {
        IOException refused = new IOException("Connection refused");

        assertTrue(LocalServerManager.isWorthRetrying(1, refused));
        assertTrue(LocalServerManager.isWorthRetrying(1, new LocalServerManager.ShellClosedEarlyException(null)));
        assertFalse(LocalServerManager.isWorthRetrying(2, refused));
        // A minute's wait isn't doubled
        assertFalse(LocalServerManager.isWorthRetrying(1, new TimeoutException()));
        // Another server on the port, or one that doesn't answer, won't change in 150 ms
        assertFalse(LocalServerManager.isWorthRetrying(1, new IOException(new ServerConnectionFailure(
                ServerConnectionFailure.Reason.NOT_ACKNOWLEDGED, "squatter", null))));
        assertFalse(LocalServerManager.isWorthRetrying(1, new ServerConnectionFailure(
                ServerConnectionFailure.Reason.UNRESPONSIVE, "silent", null)));
    }

    @Test
    public void theStagingShellClosingAtOnceIsToldApartFromAFailure() throws IOException {
        AdbLaunchFiles.checkLocked("AMNG_STAGING_LOCKED\n", 20);

        assertThrows(LocalServerManager.ShellClosedEarlyException.class, () -> AdbLaunchFiles.checkLocked("", 20));
        IOException late = assertThrows(IOException.class, () -> AdbLaunchFiles.checkLocked("", 5_000));
        assertFalse(late instanceof LocalServerManager.ShellClosedEarlyException);
        IOException refused = assertThrows(IOException.class, () ->
                AdbLaunchFiles.checkLocked("chmod: Operation not permitted\n", 20));
        assertFalse(refused instanceof LocalServerManager.ShellClosedEarlyException);
        assertTrue(refused.getMessage(), refused.getMessage().endsWith("chmod: Operation not permitted"));
    }

    /**
     * A shell that has printed {@code output} and stays open.
     */
    private InputStream openShell(String output) throws IOException {
        PipedInputStream in = new PipedInputStream(4096);
        mShellOutput = new PipedOutputStream(in);
        mShellOutput.write(output.getBytes(StandardCharsets.UTF_8));
        mShellOutput.flush();
        return in;
    }
}
