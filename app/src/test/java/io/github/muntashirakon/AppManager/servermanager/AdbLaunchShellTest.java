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
import org.robolectric.shadows.ShadowLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test(timeout = 30_000)
    public void theLaunchShellsEchoIsNeitherLoggedNorTakenForAnAnswer() throws Exception {
        String launch = COMMAND + " || echo \"Error! Could not run the server launcher.\"";
        // As the S22 answered on 2026-09-30, with this test's token: the terminal's echo, mksh's
        // first draw ending three characters into the token, and scrolled redraws, one of them
        // starting at the fallback's "Error!"
        String transcript = "id\n"
                + launch + "\n"
                + "r0q:/ $ id\n"
                + "uid=2000(shell) gid=2000(shell)\n"
                + "r0q:/ $ sh run_server.sh 62001 012\n"
                + "3456789abcdef || echo \"Error! Could not        <\b\b\b\b\b\b\b\b\b run the server launcher.\n"
                + "Error! Could not run the server launcher.       <\b\b\b\b\b\b\b\b\"\n"
                + "\n"
                + "Starting amng_server as 2000:2000...\n"
                + "Local server has started.\n"
                + "r0q:/ $ Arguments: [path:62001,token:" + TOKEN + "]\n"
                + "Success! Server has started.\n";
        ShadowLog.clear();

        LocalServerManager.launchInShell(openShell(transcript), new ByteArrayOutputStream(), launch, TOKEN,
                SystemClock.elapsedRealtime(), 5, TimeUnit.SECONDS);

        List<String> logged = new ArrayList<>();
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("LocalServerManager")) {
            if (item.msg.startsWith("RESPONSE: ")) {
                logged.add(item.msg.substring("RESPONSE: ".length()));
            }
        }
        String echo = LocalServerManager.ECHO_NOT_LOGGED;
        assertEquals(Arrays.asList("id", echo, "r0q:/ $ id", "uid=2000(shell) gid=2000(shell)", echo, echo, echo, "",
                "Starting amng_server as 2000:2000...", "Local server has started.",
                "r0q:/ $ Arguments: [path:62001,token:<redacted>]", "Success! Server has started."), logged);
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
        assertTrue(e instanceof LocalServerManager.LaunchRefusedException);
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
        assertFalse(LocalServerManager.isWorthRetrying(2, refused));
        // useAdbStartServer already opened a second shell for this, and a start retried on top of
        // that made up to four
        assertFalse(LocalServerManager.isWorthRetrying(1, new LocalServerManager.ShellClosedEarlyException(null)));
        // A shell that refused the launch refuses it again
        assertFalse(LocalServerManager.isWorthRetrying(1, new LocalServerManager.LaunchRefusedException("Server wasn't started.")));
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
        assertFalse(late instanceof LocalServerManager.LaunchRefusedException);
        IOException refused = assertThrows(IOException.class, () ->
                AdbLaunchFiles.checkLocked("chmod: Operation not permitted\n", 20));
        assertFalse(refused instanceof LocalServerManager.ShellClosedEarlyException);
        assertTrue(refused instanceof LocalServerManager.LaunchRefusedException);
        assertTrue(refused.getMessage(), refused.getMessage().endsWith("chmod: Operation not permitted"));
    }

    @Test(timeout = 30_000)
    public void theStagingShellsAnswerIsKeptWhenLibadbEndsItWithStreamClosed() throws IOException {
        // What the S22 did over Wireless debugging: every ADB start failed with "Stream closed."
        // because the read after the output threw instead of returning -1
        String locked = AdbLaunchFiles.readShellAnswer(new LibadbShell("AMNG_STAGING_LOCKED\n", false),
                () -> {}, AdbLaunchFiles.LOCKED_MARKER, 60_000);
        AdbLaunchFiles.checkLocked(locked, 20);

        String refused = AdbLaunchFiles.readShellAnswer(new LibadbShell("chmod: Operation not permitted\n", false),
                () -> {}, AdbLaunchFiles.LOCKED_MARKER, 60_000);
        assertEquals("chmod: Operation not permitted\n", refused);

        String nothing = AdbLaunchFiles.readShellAnswer(new LibadbShell("", false), () -> {},
                AdbLaunchFiles.LOCKED_MARKER, 60_000);
        assertThrows(LocalServerManager.ShellClosedEarlyException.class, () -> AdbLaunchFiles.checkLocked(nothing, 20));
        // A stream that does end with -1 reads the same
        assertEquals("AMNG_STAGING_LOCKED\n", AdbLaunchFiles.readShellAnswer(new ByteArrayInputStream(
                "AMNG_STAGING_LOCKED\n".getBytes(StandardCharsets.UTF_8)), () -> {}, AdbLaunchFiles.LOCKED_MARKER, 60_000));
    }

    @Test(timeout = 30_000)
    public void aStagingShellWhoseCloseCameBeforeItsOutputWasReadDoesNotHang() throws IOException {
        // libadb leaves such a stream waiting after the output. The marker is enough to stop at.
        long start = System.nanoTime();
        LibadbShell locked = new LibadbShell("AMNG_STAGING_LOCKED\n", true);
        assertEquals("AMNG_STAGING_LOCKED\n", AdbLaunchFiles.readShellAnswer(locked, locked,
                AdbLaunchFiles.LOCKED_MARKER, 60_000));
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10);

        // Without the marker, the deadline closes the stream, which frees the read
        LibadbShell refused = new LibadbShell("chmod: Operation not permitted\n", true);
        start = System.nanoTime();
        assertEquals("chmod: Operation not permitted\n", AdbLaunchFiles.readShellAnswer(refused, refused,
                AdbLaunchFiles.LOCKED_MARKER, 300));
        long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(String.valueOf(tookMillis), tookMillis >= 250 && tookMillis < 10_000);
        assertTrue(refused.mClosed);
    }

    @Test(timeout = 30_000)
    public void aCancelledStartEndsTheReadInsteadOfPassingForAClosedShell() throws InterruptedException {
        LibadbShell waiting = new LibadbShell("", true);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean stillInterrupted = new AtomicBoolean();
        AtomicBoolean retried = new AtomicBoolean(true);
        Thread start = new Thread(() -> {
            try {
                AdbLaunchFiles.readShellAnswer(waiting, waiting, AdbLaunchFiles.LOCKED_MARKER, 60_000);
            } catch (Throwable t) {
                thrown.set(t);
                retried.set(LocalServerManager.isWorthRetrying(1, t));
            }
            stillInterrupted.set(Thread.currentThread().isInterrupted());
        });
        start.start();
        long deadline = System.currentTimeMillis() + 5_000;
        while (start.getState() != Thread.State.WAITING && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        start.interrupt();
        start.join(10_000);

        assertFalse(start.isAlive());
        assertTrue(String.valueOf(thrown.get()), thrown.get() instanceof InterruptedIOException);
        assertTrue(stillInterrupted.get());
        assertFalse(retried.get());
        // Positive control: a failure that says nothing of a cancel still gets its retry
        assertTrue(LocalServerManager.isWorthRetrying(1, new IOException("Stream closed.")));
    }

    @Test
    public void aCancelWhoseWaitClearedTheInterruptIsStillACancel() throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedAfterWait = new AtomicBoolean(true);
        AtomicBoolean retried = new AtomicBoolean(true);
        AtomicReference<Throwable> ended = new AtomicReference<>();
        AtomicBoolean interruptedAtEnd = new AtomicBoolean();
        Thread start = new Thread(() -> {
            // The launch shell's answer is awaited this way, and the wait clears the interrupt
            Thread.currentThread().interrupt();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                thrown.set(e);
            }
            interruptedAfterWait.set(Thread.currentThread().isInterrupted());
            retried.set(LocalServerManager.isWorthRetrying(1, thrown.get()));
            try {
                LocalServerManager.throwIfCancelled((Exception) thrown.get());
            } catch (IOException e) {
                ended.set(e);
            }
            interruptedAtEnd.set(Thread.currentThread().isInterrupted());
        });
        start.start();
        start.join(10_000);

        assertFalse(interruptedAfterWait.get());
        // It used to be retried: a second full start, holding the lock the new mode waits for
        assertFalse(retried.get());
        // It ends as a cancel, not as a server that won't start, with the interrupt back for
        // the callers that look for it
        assertTrue(String.valueOf(ended.get()), ended.get() instanceof InterruptedIOException);
        assertTrue(interruptedAtEnd.get());
    }

    @Test
    public void everyShapeACancelComesInIsTakenForOne() throws IOException {
        assertTrue(LocalServerManager.isCancellation(new InterruptedException()));
        // libadb's read, which clears the interrupt
        assertTrue(LocalServerManager.isCancellation(new IOException().initCause(new InterruptedException())));
        assertTrue(LocalServerManager.isCancellation(new InterruptedIOException()));
        assertTrue(LocalServerManager.isCancellation(new ClosedByInterruptException()));
        assertTrue(LocalServerManager.isCancellation(new LocalServerManager.AdbUnreachableException(
                new IOException(new InterruptedException()))));

        // A socket timeout is an InterruptedIOException too, but nobody cancelled anything
        SocketTimeoutException timeout = new SocketTimeoutException("connect timed out");
        assertFalse(LocalServerManager.isCancellation(timeout));
        assertTrue(LocalServerManager.isWorthRetrying(1, timeout));
        LocalServerManager.throwIfCancelled(timeout);
        assertFalse(Thread.currentThread().isInterrupted());
        assertFalse(LocalServerManager.isCancellation(new IOException("Connection refused")));
    }

    @Test
    public void theEarlyCloseClockStartsOnceAdbdHasTheShellOpen() throws IOException {
        // Started before openStream, a slow open used up the window and a shell libadb closed at
        // once read as a real failure
        assertClockAfterOpen(read("LocalServerManager.java"), "manager.openStream(\"shell:\")");
        assertClockAfterOpen(read("AdbLaunchFiles.java"), "manager.openStream(\"shell:\" + lockDirCommand(STAGING_DIR))");
    }

    private static void assertClockAfterOpen(String source, String open) {
        int opened = source.indexOf(open);
        int clock = source.indexOf("openedAt = SystemClock.elapsedRealtime()");
        assertTrue(open, opened != -1 && clock > opened);
        assertEquals(clock, source.lastIndexOf("openedAt = SystemClock.elapsedRealtime()"));
    }

    private static String read(String name) throws IOException {
        java.nio.file.Path cursor = java.nio.file.Paths.get("").toAbsolutePath();
        while (cursor != null && !java.nio.file.Files.isDirectory(cursor.resolve("app/src/main/java"))) {
            cursor = cursor.getParent();
        }
        return new String(java.nio.file.Files.readAllBytes(cursor.resolve(
                "app/src/main/java/io/github/muntashirakon/AppManager/servermanager/" + name)), StandardCharsets.UTF_8);
    }

    /**
     * A finished shell as libadb-android 3.1.1's AdbStream hands it over. It never returns -1.
     * When adbd's close came after the output was read, the next read throws "Stream closed.".
     * When it came before, the close is only pending and the next read waits until the stream is
     * closed from this side.
     */
    private static final class LibadbShell extends InputStream {
        private byte[] mOutput;
        private final boolean mCloseCameFirst;
        volatile boolean mClosed;

        LibadbShell(String output, boolean closeCameFirst) {
            mOutput = output.getBytes(StandardCharsets.UTF_8);
            mCloseCameFirst = closeCameFirst;
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) throws IOException {
            if (mOutput.length > 0) {
                int n = Math.min(len, mOutput.length);
                System.arraycopy(mOutput, 0, b, off, n);
                mOutput = Arrays.copyOfRange(mOutput, n, mOutput.length);
                return n;
            }
            while (mCloseCameFirst && !mClosed) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // As libadb's AdbStream.read does it: a plain IOException, the flag cleared
                    throw (IOException) new IOException().initCause(e);
                }
            }
            throw new IOException("Stream closed.");
        }

        @Override
        public synchronized void close() {
            mClosed = true;
            notifyAll();
        }
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
