// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.logcat.helper.LogcatHelper;
import io.github.muntashirakon.io.Paths;

public class DiagnosticUtils {
    private static final int MAX_LOGCAT_LINES = 2000;
    private static final String DIAG_DIR = "diagnostics";

    /**
     * Collects device info, recent crash logs, and the last {@value #MAX_LOGCAT_LINES} logcat
     * lines into a ZIP file and returns a shareable content URI.
     *
     * @return content URI pointing to the diagnostic ZIP, or {@code null} on failure
     */
    @WorkerThread
    @Nullable
    public static Uri buildDiagnosticReport(@NonNull Context context) {
        try {
            File diagDir = new File(context.getFilesDir(), DIAG_DIR);
            if (!diagDir.exists() && !diagDir.mkdirs()) {
                return null;
            }
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                    .format(new Date());
            File zipFile = new File(diagDir, "diagnostic_" + timestamp + ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                writeDeviceInfo(context, zos);
                writeCrashLogs(context, zos);
                writeLogcat(zos);
            }
            return FmProvider.getContentUri(Paths.get(zipFile));
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeDeviceInfo(@NonNull Context context, @NonNull ZipOutputStream zos)
            throws IOException {
        zos.putNextEntry(new ZipEntry("device_info.txt"));
        PrintWriter writer = newZipEntryWriter(zos);
        writer.println(formatDeviceInfoForReport(new DeviceInfo(context).toString()));
        writer.flush();
        zos.closeEntry();
    }

    private static void writeCrashLogs(@NonNull Context context, @NonNull ZipOutputStream zos)
            throws IOException {
        File crashDir = new File(context.getFilesDir(), AMExceptionHandler.CRASHES_DIR);
        if (!crashDir.isDirectory()) return;
        File[] crashFiles = crashDir.listFiles();
        if (crashFiles == null) return;
        for (File crashFile : crashFiles) {
            if (!crashFile.isFile()) continue;
            zos.putNextEntry(new ZipEntry("crashes/" + crashFile.getName()));
            // Scrub before sharing: this ZIP is fired out via ACTION_SEND (email /
            // issue tracker). A raw crash dump embeds package names, file paths and
            // uid/userId tokens — exactly the identifiers the SupportInfoBundle
            // path already strips. Route both surfaces through the same scrubber so
            // the diagnostic export honours the same privacy guarantee.
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(crashFile)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            PrintWriter writer = newZipEntryWriter(zos);
            writer.print(SupportInfoBundle.scrubForPublicIssue(sb.toString()));
            writer.flush();
            zos.closeEntry();
        }
    }

    private static void writeLogcat(@NonNull ZipOutputStream zos) throws IOException {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(
                    LogcatHelper.getLogcatArgs(LogcatHelper.LOG_ID_DEFAULT, true));
            zos.putNextEntry(new ZipEntry("logcat.txt"));
            // Ring-buffer: keep last MAX_LOGCAT_LINES lines
            String[] ringBuf = new String[MAX_LOGCAT_LINES];
            int head = 0;
            int count = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ringBuf[head] = line;
                    head = (head + 1) % MAX_LOGCAT_LINES;
                    if (count < MAX_LOGCAT_LINES) count++;
                }
            }
            PrintWriter writer = newZipEntryWriter(zos);
            int start = (count == MAX_LOGCAT_LINES) ? head : 0;
            for (int i = 0; i < count; i++) {
                // Scrub each line: see writeCrashLogs — the report is shared externally.
                writer.println(SupportInfoBundle.scrubForPublicIssue(ringBuf[(start + i) % MAX_LOGCAT_LINES]));
            }
            writer.flush();
            zos.closeEntry();
        } finally {
            if (process != null) process.destroy();
        }
    }

    @VisibleForTesting
    @NonNull
    static String formatDeviceInfoForReport(@NonNull String deviceInfo) {
        return SupportInfoBundle.scrubForPublicIssue(deviceInfo);
    }

    @NonNull
    private static PrintWriter newZipEntryWriter(@NonNull ZipOutputStream zos) {
        return new PrintWriter(new OutputStreamWriter(zos, StandardCharsets.UTF_8));
    }
}
