// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.runner.RootManagerInfo;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.server.common.Shell;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.DateUtils;

public final class PrivilegeModeDoctor {
    private PrivilegeModeDoctor() {
    }

    @NonNull
    public static String run(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        List<Probe> probes = new ArrayList<>();
        String configuredMode = Ops.getMode();
        String inferredMode = String.valueOf(Ops.getInferredMode(appContext));
        int workingUid = Users.getSelfOrRemoteUid();
        probes.add(Probe.pass("Mode selection",
                "configured=" + configuredMode + ", inferred=" + inferredMode + ", uid=" + workingUid,
                "Change Settings > Operating mode if the inferred mode is not what you intended."));
        probes.add(probeRootBinary());
        RootManagerInfo rootManagerInfo = RootManagerInfo.detect(appContext);
        probes.add(probeRootManager(rootManagerInfo));
        probes.add(probeSui(rootManagerInfo));
        probes.add(probeShizuku(appContext));
        probes.add(probeAdb(appContext));
        probes.add(probeLocalServer());
        probes.add(probeSelinux());
        probes.add(probeAbi());
        return buildReport(appContext, configuredMode, inferredMode, workingUid, probes);
    }

    @NonNull
    private static Probe probeRootBinary() {
        try {
            Boolean rootGrant = RunnerUtils.isAppGrantedRoot();
            if (Boolean.TRUE.equals(rootGrant)) {
                Runner.Result id = Runner.runCommand("id -u");
                if (id.isSuccessful() && "0".equals(id.getOutput().trim())) {
                    return Probe.pass("Root grant", "su granted; id -u=0", "No action needed.");
                }
                return Probe.warn("Root grant", "su granted but id -u returned " + summarize(id),
                        "Re-open the root manager and verify AppManagerNG still has root access.");
            }
            if (rootGrant == null) {
                return Probe.warn("Root binary", "su exists but AppManagerNG is not granted root",
                        "Open your root manager and grant AppManagerNG, or use Shizuku/ADB mode.");
            }
            return Probe.skip("Root binary", "no executable su binary detected",
                    "Use Shizuku or ADB mode on non-rooted devices.");
        } catch (Throwable th) {
            return Probe.fail("Root grant", summarize(th),
                    "Check the root manager prompt/logs, then rerun the doctor.");
        }
    }

    @NonNull
    private static Probe probeRootManager(@NonNull RootManagerInfo info) {
        String name = info.displayName();
        if (name == null) {
            return Probe.skip("Root manager", "none detected by shell marker or package fallback",
                    "Install/configure Magisk, KernelSU, or APatch if root mode is expected.");
        }
        return Probe.pass("Root manager", name + " via " + info.source,
                info.source == RootManagerInfo.Source.PACKAGE
                        ? "Package fallback only; grant root and rerun to confirm /data/adb markers."
                        : "No action needed.");
    }

    @NonNull
    private static Probe probeSui(@NonNull RootManagerInfo info) {
        if (info.suiPresent) {
            return Probe.pass("Sui marker", "Magisk-module Sui marker detected",
                    "Use Shizuku mode if you want Sui-backed binder operations.");
        }
        return Probe.skip("Sui marker", "not detected", "No action needed unless this device should be using Sui.");
    }

    @NonNull
    private static Probe probeShizuku(@NonNull Context context) {
        String versionName = ShizukuBridge.getInstalledVersionName(context);
        boolean binderAlive = ShizukuBridge.isBinderAlive();
        boolean supportsUserService = ShizukuBridge.supportsUserService();
        boolean hasPermission = ShizukuBridge.hasPermission();
        String details = "installed=" + (versionName != null ? versionName : "unknown")
                + ", api=" + ShizukuBridge.getVersionOrZero()
                + ", uid=" + ShizukuBridge.getUidOrSelf()
                + ", binder=" + binderAlive
                + ", userService=" + supportsUserService
                + ", permission=" + hasPermission;
        ShizukuBridge.OemCompatibilityWarning oemWarning = ShizukuBridge.getOemCompatibilityWarning(context);
        if (oemWarning != null) {
            return Probe.warn("Shizuku OEM compatibility", details + ", oemRisk=" + oemWarning.reasonCode,
                    context.getString(oemWarning.summaryTextRes, oemWarning.fallbackVersion)
                            + " Archive: " + oemWarning.archiveUrl);
        }
        if (binderAlive && supportsUserService && hasPermission) {
            return Probe.pass("Shizuku binder", details, "No action needed.");
        }
        if (!binderAlive) {
            return Probe.warn("Shizuku binder", details, "Start Shizuku/Sui, then rerun the doctor.");
        }
        if (!supportsUserService) {
            return Probe.fail("Shizuku UserService", details, "Update Shizuku to a version with UserService support.");
        }
        return Probe.warn("Shizuku permission", details, "Authorize AppManagerNG in Shizuku, then rerun the doctor.");
    }

    @NonNull
    private static Probe probeAdb(@NonNull Context context) {
        boolean usbDebugging = isGlobalSettingEnabled(context, "adb_enabled");
        boolean wirelessDebugging = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && isGlobalSettingEnabled(context, "adb_wifi_enabled");
        boolean paired = ServerConfig.hasPairedAdbDevice();
        String details = "usb=" + usbDebugging + ", wireless=" + wirelessDebugging
                + ", paired=" + paired + ", lastPairingPort=" + ServerConfig.getLastAdbPairingPort();
        if (usbDebugging && (wirelessDebugging || paired)) {
            return Probe.pass("ADB reachability", details, "No action needed unless pairing has expired.");
        }
        if (usbDebugging || wirelessDebugging || paired) {
            return Probe.warn("ADB reachability", details,
                    "Enable both USB debugging and Wireless debugging, or reconnect ADB over TCP.");
        }
        return Probe.skip("ADB reachability", details,
                "Enable Developer options > USB debugging / Wireless debugging for ADB modes.");
    }

    @NonNull
    private static Probe probeLocalServer() {
        long started = android.os.SystemClock.elapsedRealtime();
        try {
            LocalServer server = LocalServer.getInstance();
            Shell.Result result = server.runCommand("id -u");
            long elapsed = android.os.SystemClock.elapsedRealtime() - started;
            String output = result.getMessage() != null ? result.getMessage().trim() : "";
            if (result.getStatusCode() == 0) {
                return Probe.pass("LocalServer command", "id -u=" + output + ", elapsed=" + elapsed + "ms",
                        "No action needed.");
            }
            return Probe.fail("LocalServer command", "exit=" + result.getStatusCode()
                            + ", output=" + output,
                    "Rerun the LocalServer bootstrap smoke test and share the support info bundle.");
        } catch (Throwable th) {
            return Probe.fail("LocalServer command", summarize(th),
                    "Rerun the LocalServer bootstrap smoke test and share the support info bundle.");
        }
    }

    @NonNull
    private static Probe probeSelinux() {
        try {
            Runner.Result result = Runner.runCommand("id -Z 2>/dev/null || cat /proc/self/attr/current 2>/dev/null");
            if (result.isSuccessful() && !result.getOutput().trim().isEmpty()) {
                return Probe.pass("SELinux domain", result.getOutput().trim(), "No action needed.");
            }
            return Probe.warn("SELinux domain", summarize(result),
                    "Expected on some ROMs; include support info if privileged calls fail.");
        } catch (Throwable th) {
            return Probe.warn("SELinux domain", summarize(th),
                    "Expected on some ROMs; include support info if privileged calls fail.");
        }
    }

    @NonNull
    private static Probe probeAbi() {
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        if (supportedAbis != null && supportedAbis.length > 0) {
            return Probe.pass("ABI", Arrays.toString(supportedAbis), "No action needed.");
        }
        return Probe.fail("ABI", "Build.SUPPORTED_ABIS is empty", "This ROM is not reporting supported ABIs correctly.");
    }

    @NonNull
    static String buildReport(@NonNull Context context,
                              @NonNull String configuredMode,
                              @NonNull String inferredMode,
                              int workingUid,
                              @NonNull List<Probe> probes) {
        StringBuilder report = new StringBuilder("AppManagerNG mode doctor\n");
        report.append("Generated: ").append(DateUtils.formatLongDateTime(context, System.currentTimeMillis())).append('\n');
        report.append("Configured mode: ").append(configuredMode).append('\n');
        report.append("Inferred mode: ").append(inferredMode).append('\n');
        report.append("Working UID: ").append(workingUid).append('\n');
        report.append("Android SDK: ").append(Build.VERSION.SDK_INT).append('\n');
        report.append("ABIs: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        for (Probe probe : probes) {
            report.append("\n")
                    .append(probe.status)
                    .append(" - ")
                    .append(probe.name)
                    .append(": ")
                    .append(probe.details);
            if (probe.fix != null && !probe.fix.isEmpty()) {
                report.append("\nFix: ").append(probe.fix);
            }
        }
        return report.toString();
    }

    @NonNull
    private static String summarize(@NonNull Runner.Result result) {
        String output = result.getOutput();
        if (TextUtils.isEmpty(output)) {
            output = TextUtils.join(" ", result.getStderr());
        }
        return "exit=" + result.getExitCode() + (TextUtils.isEmpty(output) ? "" : ", output=" + singleLine(output));
    }

    @NonNull
    private static String summarize(@NonNull Throwable th) {
        String message = th.getMessage();
        return th.getClass().getSimpleName() + (message != null ? ": " + singleLine(message) : "");
    }

    @NonNull
    private static String singleLine(@NonNull String value) {
        String compact = value.trim().replaceAll("\\s+", " ");
        if (compact.length() > 180) {
            return compact.substring(0, 177) + "...";
        }
        return compact;
    }

    private static boolean isGlobalSettingEnabled(@NonNull Context context, @NonNull String key) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    static final class Probe {
        @NonNull
        final String status;
        @NonNull
        final String name;
        @NonNull
        final String details;
        @Nullable
        final String fix;

        private Probe(@NonNull String status, @NonNull String name,
                      @NonNull String details, @Nullable String fix) {
            this.status = status;
            this.name = name;
            this.details = details;
            this.fix = fix;
        }

        @NonNull
        static Probe pass(@NonNull String name, @NonNull String details, @Nullable String fix) {
            return new Probe("PASS", name, details, fix);
        }

        @NonNull
        static Probe warn(@NonNull String name, @NonNull String details, @Nullable String fix) {
            return new Probe("WARN", name, details, fix);
        }

        @NonNull
        static Probe fail(@NonNull String name, @NonNull String details, @Nullable String fix) {
            return new Probe("FAIL", name, details, fix);
        }

        @NonNull
        static Probe skip(@NonNull String name, @NonNull String details, @Nullable String fix) {
            return new Probe("SKIP", name, details, fix);
        }
    }
}
