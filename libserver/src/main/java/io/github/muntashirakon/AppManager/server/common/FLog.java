// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
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
    private static FileOutputStream fos;
    private static final AtomicInteger sBufferSize = new AtomicInteger();
    private static final AtomicInteger sErrorCount = new AtomicInteger();

    private static void openFile() {
        try {
            if (writeLog && fos == null && sErrorCount.get() < 5) {
                File file = new File("/data/local/tmp/am.txt");
                fos = new FileOutputStream(file);

                fos.write("\n\n\n--------------------".getBytes());
                fos.write(new Date().toString().getBytes());
                fos.write("\n\n".getBytes());
                chown(file.getAbsolutePath(), 2000, 2000);
                chmod(file.getAbsolutePath(), 0755);
            }
        } catch (IOException | RuntimeException e) {
            handleInternalFailure("open", e);
            fos = null;
        }
    }

    private static void chown(String path, int uid, int gid) {
        try {
            Os.chown(path, uid, gid);
        } catch (ErrnoException e) {
            handleInternalFailure("chown", e);
        }
    }

    private static void chmod(String path, int mode) {
        try {
            Os.chmod(path, mode);
        } catch (ErrnoException e) {
            handleInternalFailure("chmod", e);
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

    private static String sanitize(String value) {
        String sanitized = SECRET_ASSIGNMENT_PATTERN.matcher(value)
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
