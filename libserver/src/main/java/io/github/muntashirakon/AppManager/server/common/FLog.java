// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

// Copyright 2017 Zheng Li
public class FLog {

    public static boolean writeLog = false;
    static final int MAX_LOG_CHARS = 16 * 1024;
    static final int MAX_THROWABLE_FRAMES = 32;
    static final int MAX_THROWABLE_CAUSES = 4;
    static final String DIAGNOSTIC_TRUNCATED_MARKER = "\n[diagnostic truncated]\n";
    private static final String REDACTION_MARKER = "<redacted>";
    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)(\\b(?:auth|token|secret|password|passwd)\\b\\s*[:=]\\s*)([^,\\s\\]]+)");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b",
            Pattern.CASE_INSENSITIVE);
    /**
     * Shortest string accepted as a secret. Redacting something shorter would blank out ordinary
     * words all over the log without protecting anything worth protecting.
     */
    private static final int MIN_SECRET_LENGTH = 8;
    /** Bound on how many secrets are held, so a loop cannot grow this without limit. */
    private static final int MAX_SECRETS = 8;
    /**
     * Literal values known to be secret. The patterns above can only redact secrets that appear
     * in a shape they recognise — an {@code auth=}/{@code token=} assignment or a UUID — and a
     * value in any other shape reached the log intact. Anything registered here is removed
     * wherever it appears, in whatever shape.
     */
    private static final CopyOnWriteArraySet<String> sSecrets = new CopyOnWriteArraySet<>();
    private static FileOutputStream fos;
    @Nullable
    private static FileDescriptor sLogFd;
    private static final AtomicInteger sBufferSize = new AtomicInteger();
    private static final AtomicInteger sErrorCount = new AtomicInteger();

    /**
     * Declare a value that must never reach the log, whatever surrounds it. Called wherever a
     * secret first becomes known, so the redaction does not depend on guessing its shape.
     *
     * @param secret The literal value; ignored when null or too short to redact safely
     */
    public static void registerSecret(@Nullable String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            return;
        }
        if (sSecrets.size() >= MAX_SECRETS) {
            return;
        }
        sSecrets.add(secret);
    }

    /** Visible for testing: forget every registered secret. */
    static void clearSecrets() {
        sSecrets.clear();
    }

    private static void openFile() {
        try {
            if (writeLog && fos == null && sErrorCount.get() < 5) {
                fos = openPrivateLog(Constants.SERVER_LOG);
                if (fos == null) {
                    return;
                }
                fos.write("\n\n\n--------------------".getBytes());
                fos.write(new Date().toString().getBytes());
                fos.write("\n\n".getBytes());
            }
        } catch (IOException | RuntimeException e) {
            handleInternalFailure("open", e);
            fos = null;
        }
    }

    /**
     * Opens the log empty, owned by the shell user and readable by it alone. Nothing in the app
     * reads it back; it is pulled over adb.
     * <p>
     * The log lives in /data/local/tmp, which the shell user owns, so whatever sits at that path
     * may be a link the shell user planted. Opening, chowning and chmodding by name followed such
     * a link, which let the shell user (or any app it lends Shizuku to) have root empty any file
     * on the device and hand it to the shell user. So the open never follows a link, a file with
     * another name or that isn't a plain file is left alone, and every later change goes through
     * the open file rather than its name.
     *
     * @return The open log, or {@code null} when it isn't safe to write
     */
    @Nullable
    private static FileOutputStream openPrivateLog(String path) {
        FileDescriptor fd;
        try {
            // O_NONBLOCK keeps a FIFO planted at the path from holding the server up
            fd = Os.open(path, OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_NOFOLLOW
                    | OsConstants.O_NONBLOCK, 0600);
        } catch (ErrnoException e) {
            handleInternalFailure("open", e);
            return null;
        }
        try {
            if (!isPrivateLogFile(Os.fstat(fd))) {
                handleInternalFailure("open", new IOException("Not a plain file with one name: " + path));
                closeQuietly(fd);
                return null;
            }
            Os.ftruncate(fd, 0);
        } catch (ErrnoException e) {
            handleInternalFailure("open", e);
            closeQuietly(fd);
            return null;
        }
        try {
            Os.fchown(fd, 2000, 2000);
        } catch (ErrnoException e) {
            handleInternalFailure("chown", e);
        }
        try {
            Os.fchmod(fd, 0600);
        } catch (ErrnoException e) {
            handleInternalFailure("chmod", e);
        }
        // A stream made from a descriptor doesn't close it, so close() does
        sLogFd = fd;
        return new FileOutputStream(fd);
    }

    /**
     * Whether an open log may be written: a plain file, not a device, FIFO or socket, and with no
     * other name, since a hard link would make the log another file's contents.
     */
    static boolean isPrivateLogFile(StructStat stat) {
        return OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1;
    }

    private static void closeQuietly(FileDescriptor fd) {
        try {
            Os.close(fd);
        } catch (ErrnoException e) {
            handleInternalFailure("close", e);
        }
    }

    public static void log(String log) {
        String safeLog = sanitizeAndLimit(log);
        if (writeLog) {
            System.out.println(safeLog);
        } else {
            Log.e("am", "Flog --> " + safeLog);
        }

        try {
            if (writeLog) {
                openFile();
                if (fos != null) {
                    fos.write(safeLog.getBytes(StandardCharsets.UTF_8));
                    fos.write("\n".getBytes(StandardCharsets.UTF_8));

                    if (sBufferSize.incrementAndGet() > 10) {
                        fos.getFD().sync();
                        fos.flush();
                        sBufferSize.set(0);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            handleInternalFailure("write", e);
        }
    }

    public static void log(Throwable e) {
        log(formatThrowable(e));
    }

    public static void close() {
        try {
            if (writeLog && fos != null) {
                fos.getFD().sync();
                fos.close();
            }
        } catch (IOException | RuntimeException e) {
            handleInternalFailure("close", e);
        }
        if (sLogFd != null) {
            closeQuietly(sLogFd);
            sLogFd = null;
        }
    }

    static String formatThrowable(Throwable throwable) {
        if (throwable == null) {
            return "null";
        }
        BoundedText text = new BoundedText(MAX_LOG_CHARS);
        appendThrowable(text, throwable, 0);
        return text.toString();
    }

    static String sanitizeAndLimit(String value) {
        String sanitized = sanitize(String.valueOf(value));
        if (sanitized.length() <= MAX_LOG_CHARS) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_LOG_CHARS) + DIAGNOSTIC_TRUNCATED_MARKER;
    }

    static String sanitize(String value) {
        // Known values first: a literal match does not depend on the secret appearing in a shape
        // the patterns below happen to recognise.
        String sanitized = value;
        for (String secret : sSecrets) {
            sanitized = sanitized.replace(secret, REDACTION_MARKER);
        }
        sanitized = SECRET_ASSIGNMENT_PATTERN.matcher(sanitized)
                .replaceAll("$1" + REDACTION_MARKER);
        return UUID_PATTERN.matcher(sanitized).replaceAll(REDACTION_MARKER);
    }

    private static void appendThrowable(BoundedText text, Throwable throwable, int depth) {
        if (depth > 0) {
            text.append("Caused by: ");
        }
        text.append(throwable.getClass().getName());
        String message = throwable.getMessage();
        if (message != null && !message.isEmpty()) {
            text.append(": ");
            text.append(sanitize(message));
        }
        text.append("\n");

        StackTraceElement[] stackTrace = throwable.getStackTrace();
        int frameCount = Math.min(stackTrace.length, MAX_THROWABLE_FRAMES);
        for (int i = 0; i < frameCount; ++i) {
            text.append("\tat ");
            text.append(stackTrace[i].toString());
            text.append("\n");
        }
        if (stackTrace.length > frameCount) {
            text.append("\t... ");
            text.append(String.valueOf(stackTrace.length - frameCount));
            text.append(" more frames\n");
        }
        Throwable[] suppressed = throwable.getSuppressed();
        if (suppressed.length > 0) {
            text.append("\t... ");
            text.append(String.valueOf(suppressed.length));
            text.append(" suppressed exception(s)\n");
        }
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            if (depth + 1 >= MAX_THROWABLE_CAUSES) {
                text.append("Caused by: [cause chain truncated]\n");
            } else {
                appendThrowable(text, cause, depth + 1);
            }
        }
    }

    private static void handleInternalFailure(String action, Throwable throwable) {
        sErrorCount.incrementAndGet();
        try {
            Log.e("am", "FLog " + action + " failed: " + formatThrowable(throwable));
        } catch (Throwable ignored) {
            // Logging itself failed; avoid recursive logging from the logger.
        }
    }

    private static final class BoundedText {
        private final StringBuilder mText = new StringBuilder();
        private final int mMaxChars;
        private boolean mTruncated;

        private BoundedText(int maxChars) {
            mMaxChars = Math.max(0, maxChars);
        }

        private void append(String value) {
            if (value == null || value.length() == 0 || mTruncated) {
                return;
            }
            int remaining = mMaxChars - mText.length();
            if (remaining > 0) {
                mText.append(value, 0, Math.min(remaining, value.length()));
            }
            if (value.length() > remaining) {
                mText.append(DIAGNOSTIC_TRUNCATED_MARKER);
                mTruncated = true;
            }
        }

        @Override
        public String toString() {
            return mText.toString();
        }
    }
}
