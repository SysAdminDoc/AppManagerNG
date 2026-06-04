// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.io.Paths;

public final class LocalCrashSink {
    private static final int MAX_CRASH_FILES = 10;
    private static final int SUPPORT_SUMMARY_LIMIT = 5;
    private static final int STACK_TRACE_LIMIT = 80;

    private LocalCrashSink() {
    }

    @WorkerThread
    @Nullable
    public static Uri writeCrash(@NonNull Context context,
                                 @NonNull Thread thread,
                                 @NonNull Throwable throwable,
                                 @NonNull String report) {
        File file = writeCrashFile(context, thread.getName(), throwable, report, new Date());
        return file != null ? FmProvider.getContentUri(Paths.get(file)) : null;
    }

    @WorkerThread
    @NonNull
    public static String buildSupportSummary(@NonNull Context context) {
        File[] files = listCrashFiles(context);
        if (files.length == 0) {
            return "No local crash files.\n";
        }
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        StringBuilder summary = new StringBuilder();
        int count = Math.min(SUPPORT_SUMMARY_LIMIT, files.length);
        for (int i = 0; i < count; ++i) {
            JSONObject object = readJson(files[i]);
            if (object == null) {
                summary.append("- ").append(files[i].getName()).append(": unreadable\n");
                continue;
            }
            summary.append("- ")
                    .append(object.optString("captured_at_utc", "unknown"))
                    .append(" · ")
                    .append(object.optString("throwable_class", "unknown"));
            String message = object.optString("message", "");
            if (!message.isEmpty()) {
                summary.append(" · ").append(message);
            }
            summary.append('\n');
        }
        if (files.length > count) {
            summary.append("- ").append(files.length - count).append(" older local crash file(s) omitted\n");
        }
        return summary.toString();
    }

    @VisibleForTesting
    @WorkerThread
    @Nullable
    static File writeCrashFile(@NonNull Context context,
                               @NonNull String threadName,
                               @NonNull Throwable throwable,
                               @NonNull String report,
                               @NonNull Date now) {
        try {
            File crashDir = getCrashDir(context);
            if (!crashDir.exists() && !crashDir.mkdirs()) {
                return null;
            }
            pruneOldCrashes(crashDir);
            File crashFile = new File(crashDir, "crash_" + formatFileTimestamp(now) + ".json");
            JSONObject object = buildCrashJson(threadName, throwable, report, now);
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(crashFile), StandardCharsets.UTF_8)) {
                writer.write(object.toString(2));
                writer.write('\n');
            }
            return crashFile;
        } catch (IOException | JSONException ignored) {
            return null;
        }
    }

    @VisibleForTesting
    @NonNull
    static JSONObject buildCrashJson(@NonNull String threadName,
                                     @NonNull Throwable throwable,
                                     @NonNull String report,
                                     @NonNull Date now) throws JSONException {
        JSONObject object = new JSONObject()
                .put("schema", 1)
                .put("captured_at_utc", formatUtc(now))
                .put("app_version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
                .put("build_type", BuildConfig.BUILD_TYPE)
                .put("android_sdk", Build.VERSION.SDK_INT)
                .put("thread", SupportInfoBundle.scrubForPublicIssue(threadName))
                .put("throwable_class", throwable.getClass().getName())
                .put("message", SupportInfoBundle.scrubForPublicIssue(throwable.getMessage()))
                .put("stack", buildStackJson(throwable))
                .put("causes", buildCausesJson(throwable))
                .put("scrubbed_report", SupportInfoBundle.scrubForPublicIssue(report));
        return object;
    }

    @VisibleForTesting
    @NonNull
    static File getCrashDir(@NonNull Context context) {
        return new File(context.getFilesDir(), AMExceptionHandler.CRASHES_DIR);
    }

    @NonNull
    private static File[] listCrashFiles(@NonNull Context context) {
        File crashDir = getCrashDir(context);
        File[] files = crashDir.listFiles((dir, name) -> name.endsWith(".json"));
        return files != null ? files : new File[0];
    }

    @Nullable
    private static JSONObject readJson(@NonNull File file) {
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return new JSONObject(builder.toString());
        } catch (Throwable ignored) {
            return null;
        }
    }

    @NonNull
    private static JSONArray buildStackJson(@NonNull Throwable throwable) {
        JSONArray array = new JSONArray();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        int count = Math.min(STACK_TRACE_LIMIT, stackTrace.length);
        for (int i = 0; i < count; ++i) {
            array.put(SupportInfoBundle.scrubForPublicIssue(stackTrace[i].toString()));
        }
        if (stackTrace.length > count) {
            array.put("... " + (stackTrace.length - count) + " frame(s) omitted");
        }
        return array;
    }

    @NonNull
    private static JSONArray buildCausesJson(@NonNull Throwable throwable) throws JSONException {
        JSONArray causes = new JSONArray();
        Throwable cause = throwable.getCause();
        while (cause != null) {
            causes.put(new JSONObject()
                    .put("throwable_class", cause.getClass().getName())
                    .put("message", SupportInfoBundle.scrubForPublicIssue(cause.getMessage()))
                    .put("stack", buildStackJson(cause)));
            cause = cause.getCause();
        }
        return causes;
    }

    private static void pruneOldCrashes(@NonNull File crashDir) {
        File[] files = crashDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length < MAX_CRASH_FILES) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i <= files.length - MAX_CRASH_FILES; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    @NonNull
    private static String formatUtc(@NonNull Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }

    @NonNull
    private static String formatFileTimestamp(@NonNull Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }
}
