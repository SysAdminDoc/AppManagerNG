// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.fm.FmProvider;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.runner.RootManagerInfo;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.self.SelfBatteryOptimization;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public final class SupportInfoBundle {
    private static final int LOGCAT_TAIL_LINES = 120;
    private static final String UNKNOWN = "unknown";
    private static final String REDACTED = "<redacted>";

    private SupportInfoBundle() {
    }

    @WorkerThread
    @NonNull
    public static Path writeTextBundle(@NonNull Context context) throws IOException {
        return writeTextBundle(context, null);
    }

    /**
     * Variant that lets a caller (e.g. Mode Doctor) inline a probe report or
     * other free-form section above the standard support bundle. The
     * {@code preamble}, if non-null, is written verbatim followed by a blank
     * line before the regular bundle body so an analyst opening the file sees
     * the contextual report first and the environment dump below.
     */
    @WorkerThread
    @NonNull
    public static Path writeTextBundle(@NonNull Context context, @Nullable CharSequence preamble) throws IOException {
        Date now = new Date();
        String text = buildText(context.getApplicationContext(), formatUtc(now), readScrubbedLogcatTail());
        File dir = FileCache.getGlobalFileCache().createCachedDir("support-info");
        File file = new File(dir, buildFileName(Build.DEVICE, now));
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            if (preamble != null && preamble.length() > 0) {
                writer.write(preamble.toString());
                writer.write("\n\n");
            }
            writer.write(text);
        }
        return Paths.get(file);
    }

    @NonNull
    public static Intent buildShareIntent(@NonNull Context context, @NonNull Path bundlePath) {
        Uri uri = FmProvider.getContentUri(bundlePath);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.support_info_bundle_subject))
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("", uri));
        return intent;
    }

    @WorkerThread
    @NonNull
    private static String buildText(@NonNull Context context,
                                    @NonNull String timestampUtc,
                                    @NonNull String scrubbedLogcatTail) {
        StringBuilder sb = new StringBuilder();
        sb.append("AppManagerNG support info").append('\n');
        sb.append("=========================").append('\n');
        appendLine(sb, "Generated UTC", timestampUtc);
        appendLine(sb, "App version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        appendLine(sb, "Build flavor", emptyToUnknown(BuildConfig.FLAVOR));
        appendLine(sb, "Build type", BuildConfig.BUILD_TYPE);
        sb.append('\n');

        sb.append("Device").append('\n');
        sb.append("------").append('\n');
        appendLine(sb, "Manufacturer", Build.MANUFACTURER);
        appendLine(sb, "Brand", Build.BRAND);
        appendLine(sb, "Model", Build.MODEL);
        appendLine(sb, "Product/device", Build.PRODUCT + "/" + Build.DEVICE);
        appendLine(sb, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        appendLine(sb, "Security patch", getSecurityPatch());
        appendLine(sb, "Build ID", Build.ID);
        appendLine(sb, "Incremental", Build.VERSION.INCREMENTAL);
        appendLine(sb, "ABIs", Arrays.toString(Build.SUPPORTED_ABIS));
        appendLine(sb, "LineageOS", SystemProperties.get("ro.lineage.version", UNKNOWN));
        appendLine(sb, "One UI", SystemProperties.get("ro.build.version.oneui", UNKNOWN));
        appendLine(sb, "MIUI/HyperOS", firstKnown(
                SystemProperties.get("ro.mi.os.version.name", ""),
                SystemProperties.get("ro.miui.ui.version.name", "")));
        sb.append('\n');

        sb.append("Privilege state").append('\n');
        sb.append("---------------").append('\n');
        appendLine(sb, "Configured mode", Ops.getMode());
        appendLine(sb, "Inferred mode", Ops.getInferredMode(context).toString());
        appendLine(sb, "Working UID", REDACTED);
        appendLine(sb, "App UID", REDACTED);
        appendLine(sb, "Remote server alive", String.valueOf(LocalServer.alive(context)));
        appendLine(sb, "Remote services alive", String.valueOf(LocalServices.alive()));
        appendRootManager(context, sb);
        appendLine(sb, "Shizuku manager", emptyToUnknown(ShizukuBridge.getInstalledVersionName(context)));
        appendLine(sb, "Shizuku binder alive", String.valueOf(ShizukuBridge.isBinderAlive()));
        appendLine(sb, "Shizuku API", String.valueOf(ShizukuBridge.getVersionOrZero()));
        appendLine(sb, "Shizuku permission", String.valueOf(ShizukuBridge.hasPermission()));
        appendLine(sb, "Shizuku service UID", REDACTED);
        appendLine(sb, "Battery optimization exempt",
                SelfBatteryOptimization.isSupported() ? String.valueOf(SelfBatteryOptimization.isExempt(context)) : "unsupported");
        appendLine(sb, "Last LocalServer bootstrap signature",
                scrubForPublicIssue(emptyToUnknown(LocalServer.getLastBootstrapSignature())));
        sb.append('\n');

        sb.append("Feature flags").append('\n');
        sb.append("-------------").append('\n');
        appendLine(sb, "Raw flags", "0x" + Integer.toHexString(AppPref.getInt(AppPref.PrefKey.PREF_ENABLED_FEATURES_INT)));
        appendLine(sb, "Optional network features available",
                String.valueOf(FeatureController.areOptionalNetworkFeaturesAvailable()));
        appendLine(sb, "Internet", enabled(FeatureController.isInternetEnabled()));
        appendLine(sb, "VirusTotal", enabled(FeatureController.isVirusTotalEnabled()));
        appendLine(sb, "Installer", enabled(FeatureController.isInstallerEnabled()));
        appendLine(sb, "Scanner", enabled(FeatureController.isScannerEnabled()));
        appendLine(sb, "Log viewer", enabled(FeatureController.isLogViewerEnabled()));
        appendLine(sb, "Usage access", enabled(FeatureController.isUsageAccessEnabled()));
        appendLine(sb, "Terminal", enabled(FeatureController.isTerminalEnabled()));
        sb.append('\n');

        sb.append("Scrubbed logcat tail").append('\n');
        sb.append("--------------------").append('\n');
        sb.append(scrubbedLogcatTail);
        if (!scrubbedLogcatTail.endsWith("\n")) {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void appendRootManager(@NonNull Context context, @NonNull StringBuilder sb) {
        RootManagerInfo rootManagerInfo = RootManagerInfo.detect(context);
        appendLine(sb, "Root manager", emptyToUnknown(rootManagerInfo.displayName()));
        appendLine(sb, "Root manager source", rootManagerInfo.source.name());
        appendLine(sb, "ZygiskNext marker", String.valueOf(rootManagerInfo.zygiskNextPresent));
        appendLine(sb, "Sui marker", String.valueOf(rootManagerInfo.suiPresent));
    }

    @WorkerThread
    @NonNull
    private static String readScrubbedLogcatTail() {
        try {
            Runner.Result result = Runner.runCommand(new String[]{
                    "logcat", "-d", "-t", String.valueOf(LOGCAT_TAIL_LINES),
                    "-v", "threadtime", "-v", "uid", "-b", "default"
            });
            if (result.isSuccessful()) {
                return scrubForPublicIssue(result.getOutput());
            }
            String stderr = result.getStderr().isEmpty() ? "" : "\n" + TextUtils.join("\n", result.getStderr());
            return "logcat unavailable: exit " + result.getExitCode() + scrubForPublicIssue(stderr);
        } catch (Throwable th) {
            return "logcat unavailable: " + th.getClass().getSimpleName();
        }
    }

    @VisibleForTesting
    @NonNull
    static String scrubForPublicIssue(@Nullable String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String scrubbed = input;
        scrubbed = scrubbed.replaceAll("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", "<email>");
        scrubbed = scrubbed.replaceAll("(?i)\\b(content|file|https?)://\\S+", "$1://<redacted>");
        scrubbed = scrubbed.replaceAll("(?i)\\bpackage:[^\\s]+", "package:<redacted>");
        scrubbed = scrubbed.replaceAll("(?i)(?:/storage/emulated/\\d+|/sdcard|/data/user/\\d+|/data/data|/data/media/\\d+|/mnt/media_rw)/\\S+", "<path>");
        scrubbed = scrubbed.replaceAll("(?i)\\b(appUid|uid|userId|user)\\s*[:=]\\s*\\d+\\b", "$1=<redacted>");
        scrubbed = scrubbed.replaceAll("\\bu\\d+_a\\d+\\b", "u<redacted>");
        scrubbed = scrubbed.replaceAll("\\b[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*){1,}\\b", "<package>");
        scrubbed = scrubbed.replaceAll("\\b\\d{5,7}\\b", "<id>");
        return scrubbed;
    }

    @VisibleForTesting
    @NonNull
    static String buildFileName(@Nullable String device, @NonNull Date date) {
        String safeDevice = device == null ? "" : device.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safeDevice.isEmpty()) {
            safeDevice = "device";
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "support-info-" + safeDevice + "-" + formatter.format(date) + ".txt";
    }

    @NonNull
    private static String formatUtc(@NonNull Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }

    @NonNull
    private static String getSecurityPatch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return emptyToUnknown(Build.VERSION.SECURITY_PATCH);
        }
        return UNKNOWN;
    }

    @NonNull
    private static String enabled(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    @NonNull
    private static String firstKnown(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        return UNKNOWN;
    }

    @NonNull
    private static String emptyToUnknown(@Nullable String value) {
        return value == null || value.isEmpty() ? UNKNOWN : value;
    }

    private static void appendLine(@NonNull StringBuilder sb, @NonNull String key, @NonNull String value) {
        sb.append(key).append(": ").append(value).append('\n');
    }
}
