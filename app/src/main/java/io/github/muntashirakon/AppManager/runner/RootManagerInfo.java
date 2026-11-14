// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.core.content.pm.PackageInfoCompat;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * Detects which Android root manager is in use and whether the ZygiskNext or
 * Sui layers are loaded on top of it. Surfaced in the onboarding capability
 * sheet and the privilege health-check screen so users can see <em>which</em>
 * root provider has actually granted AppManagerNG superuser — important when
 * more than one coexists (Magisk + ZygiskNext, KernelSU + Sui, etc.).
 *
 * <p>Detection strategies, in order:
 * <ol>
 *   <li><b>Marker directory probe</b> via the privileged shell — reads
 *       {@code /data/adb/<manager>/} which is mode-700 root-owned and only
 *       visible to a privileged shell. Most authoritative; available only
 *       once root has been granted.</li>
 *   <li><b>Installed-package fallback</b> via {@link PackageManager} — works
 *       without root but only tells us a manager <em>app</em> is on the
 *       device, not whether its kernel/userspace half is actually live.</li>
 * </ol>
 *
 * <p>Probe matrix, checked against the releases current on 2026-11-14. The marker probe
 * takes the first directory that exists, in this order:
 * <table>
 *   <tr><th>Manager</th><th>Marker</th><th>App package</th><th>Checked</th></tr>
 *   <tr><td>Magisk</td><td>{@code /data/adb/magisk}</td><td>{@value #MAGISK_PACKAGE}</td>
 *       <td>v31.1 (versionCode 31100). From v31.1 su never asks: an app gets root only after
 *       it's added in Magisk &gt; Superuser &gt; +, so a refused switch says so
 *       ({@link #waitsForMagiskGrant}) and picking Root again asks su again
 *       ({@link RunnerUtils#retryRootGrant}). From v30.7 (30700) su keeps Linux capabilities
 *       unless started with {@code --drop-cap} ({@link RootCapabilityDiagnostics}).</td></tr>
 *   <tr><td>KernelSU</td><td>{@code /data/adb/ksu}</td><td>{@code me.weishu.kernelsu}</td>
 *       <td>The original.</td></tr>
 *   <tr><td>KernelSU-Next</td><td>{@code /data/adb/ksu}</td><td>{@code com.rifsxd.ksunext}</td>
 *       <td>v3.4.2</td></tr>
 *   <tr><td>SukiSU Ultra</td><td>{@code /data/adb/ksu}</td><td>{@code com.sukisu.ultra}</td>
 *       <td>v4.3.0, a KernelSU fork.</td></tr>
 *   <tr><td>APatch</td><td>{@code /data/adb/ap}</td><td>{@code me.bmax.apatch}</td>
 *       <td>11282. Kernel patch through KernelPatch, ARM64 only.</td></tr>
 * </table>
 * The KernelSU forks share one marker, so they all report {@link Manager#KERNELSU}. Layers
 * probed on top: ZygiskNext at {@code /data/adb/modules/zygisksu}, and Sui (the Magisk-module
 * form of Shizuku, with no {@code moe.shizuku.privileged.api} app) at
 * {@code /data/adb/modules/sui}.
 */
public final class RootManagerInfo {
    /** Magisk's app. Its versionCode is major * 1000 + minor * 100, so v30.7 is 30700. */
    public static final String MAGISK_PACKAGE = "com.topjohnwu.magisk";
    /** The original KernelSU, KernelSU-Next and SukiSU Ultra, which all live in /data/adb/ksu. */
    private static final String[] KERNELSU_PACKAGES = {
            "me.weishu.kernelsu",
            "com.rifsxd.ksunext",
            "com.sukisu.ultra",
    };
    private static final String APATCH_PACKAGE = "me.bmax.apatch";
    /** Magisk v31.1, the first whose su never asks for root. */
    private static final long MAGISK_GRANTS_AHEAD_OF_TIME = 31100;

    /** Which root manager owns {@code /data/adb} on this device. */
    public enum Manager {
        /** No root manager detected (or root not granted). */
        NONE,
        MAGISK,
        KERNELSU,
        APATCH,
        /** Detection inconclusive — usually because the privileged shell is unavailable. */
        UNKNOWN,
    }

    /** How the manager was identified — useful for diagnostics. */
    public enum Source {
        /** Confirmed by reading {@code /data/adb/...} via the privileged shell. */
        MARKER,
        /** Inferred from a manager package being installed; not yet confirmed live. */
        PACKAGE,
        /** Neither shell nor package signal turned up anything. */
        NONE,
    }

    @NonNull
    public final Manager manager;
    @NonNull
    public final Source source;
    public final boolean zygiskNextPresent;
    public final boolean suiPresent;

    private RootManagerInfo(@NonNull Manager manager, @NonNull Source source,
                            boolean zygiskNextPresent, boolean suiPresent) {
        this.manager = manager;
        this.source = source;
        this.zygiskNextPresent = zygiskNextPresent;
        this.suiPresent = suiPresent;
    }

    /**
     * Probe for the active root manager. Performs at most two shell commands
     * (~50–150ms when root is live) and falls back to a synchronous package
     * lookup. Always call from a worker thread.
     */
    @WorkerThread
    @NonNull
    public static RootManagerInfo detect(@NonNull Context ctx) {
        if (privilegedShellAvailable()) {
            Manager fromShell = detectViaShell();
            if (fromShell != Manager.UNKNOWN) {
                boolean zn = fromShell != Manager.NONE && checkZygiskNextViaShell();
                boolean sui = fromShell != Manager.NONE && checkSuiViaShell();
                return new RootManagerInfo(fromShell, Source.MARKER, zn, sui);
            }
        }
        Manager fromPackages = detectViaPackages(ctx);
        return new RootManagerInfo(fromPackages, fromPackages == Manager.NONE ? Source.NONE : Source.PACKAGE, false, false);
    }

    @AnyThread
    public boolean hasManager() {
        return manager != Manager.NONE && manager != Manager.UNKNOWN;
    }

    @AnyThread
    @Nullable
    public String displayName() {
        switch (manager) {
            case MAGISK:   return "Magisk";
            case KERNELSU: return "KernelSU";
            case APATCH:   return "APatch";
            case NONE:
            case UNKNOWN:
            default:       return null;
        }
    }

    private static boolean privilegedShellAvailable() {
        return Ops.isDirectRoot() || LocalServices.alive();
    }

    @WorkerThread
    @NonNull
    private static Manager detectViaShell() {
        // Single-round-trip cascade; first matching marker wins.
        Runner.Result r = Runner.runCommand(
                "if [ -d /data/adb/magisk ]; then echo MAGISK; "
                + "elif [ -d /data/adb/ksu ]; then echo KERNELSU; "
                + "elif [ -d /data/adb/ap ]; then echo APATCH; "
                + "else echo NONE; fi");
        if (!r.isSuccessful()) {
            return Manager.UNKNOWN;
        }
        switch (r.getOutput().trim()) {
            case "MAGISK":   return Manager.MAGISK;
            case "KERNELSU": return Manager.KERNELSU;
            case "APATCH":   return Manager.APATCH;
            case "NONE":     return Manager.NONE;
            default:         return Manager.UNKNOWN;
        }
    }

    @WorkerThread
    private static boolean checkZygiskNextViaShell() {
        Runner.Result r = Runner.runCommand(
                "if [ -d /data/adb/modules/zygisksu ]; then echo YES; else echo NO; fi");
        return r.isSuccessful() && "YES".equals(r.getOutput().trim());
    }

    /**
     * Sui (Magisk-module form of Shizuku) ships under a stable module ID.
     * Skips the {@code moe.shizuku.privileged.api} package install so its presence
     * is invisible to {@link PackageManager}; the only authoritative signal is the
     * Magisk module marker. Effectively superset-equivalent to a Shizuku binding
     * but with zero-UI permission grants for already-trusted apps.
     */
    @WorkerThread
    private static boolean checkSuiViaShell() {
        Runner.Result r = Runner.runCommand(
                "if [ -d /data/adb/modules/sui ]; then echo YES; else echo NO; fi");
        return r.isSuccessful() && "YES".equals(r.getOutput().trim());
    }

    @AnyThread
    @NonNull
    private static Manager detectViaPackages(@NonNull Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        if (getVersionCode(pm, MAGISK_PACKAGE) != null) {
            return Manager.MAGISK;
        }
        for (String packageName : KERNELSU_PACKAGES) {
            if (getVersionCode(pm, packageName) != null) {
                return Manager.KERNELSU;
            }
        }
        if (getVersionCode(pm, APATCH_PACKAGE) != null) {
            return Manager.APATCH;
        }
        return Manager.NONE;
    }

    /**
     * Whether Magisk v31.1 or later is installed. Its su never asks, so root has to be granted in
     * Magisk before it's picked here. When the Magisk app isn't under its own package (Hide the
     * Magisk app reinstalls it under a random one), Magisk's su says which version it is.
     */
    @WorkerThread
    public static boolean magiskGrantsAheadOfTime(@NonNull Context ctx) {
        Long versionCode = getVersionCode(ctx.getPackageManager(), MAGISK_PACKAGE);
        if (versionCode == null) {
            versionCode = RunnerUtils.getMagiskSuVersionCode();
        }
        return versionCode != null && versionCode >= MAGISK_GRANTS_AHEAD_OF_TIME;
    }

    /**
     * Whether su is there but refused AppManagerNG while Magisk v31.1 or later is installed. That
     * Magisk never asks, so the app has to be added in Magisk first. With no su at all (Magisk only
     * patching a boot image) this is false, and so is it for KernelSU and APatch, which hide su from
     * apps they haven't granted. Call it after a root probe so it reuses that probe's shell.
     */
    @WorkerThread
    public static boolean waitsForMagiskGrant(@NonNull Context ctx) {
        return RunnerUtils.isAppGrantedRoot() == null && magiskGrantsAheadOfTime(ctx);
    }

    /**
     * What to tell the user when Root was picked and it failed. When su refused on Magisk v31.1 or
     * later, that's where to grant it, since no prompt ever came up.
     */
    @WorkerThread
    @StringRes
    public static int getRootRefusedMessage(@NonNull Context ctx) {
        return waitsForMagiskGrant(ctx) ? R.string.root_refused_magisk_superuser
                : R.string.failed_to_use_the_current_mode_of_operation;
    }

    /** Magisk's app to open, or null when it isn't installed or has no launcher. */
    @AnyThread
    @Nullable
    public static Intent getMagiskLaunchIntent(@NonNull Context ctx) {
        return getLaunchIntent(ctx.getPackageManager(), MAGISK_PACKAGE);
    }

    /** The installed root manager app to open: Magisk, a KernelSU manager or APatch, in that order. */
    @AnyThread
    @Nullable
    public static Intent getManagerLaunchIntent(@NonNull Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent intent = getMagiskLaunchIntent(ctx);
        if (intent == null) {
            intent = getKernelSuManagerLaunchIntent(ctx);
        }
        return intent != null ? intent : getLaunchIntent(pm, APATCH_PACKAGE);
    }

    /** The installed KernelSU manager app to open: the original, KernelSU-Next or SukiSU Ultra. */
    @AnyThread
    @Nullable
    public static Intent getKernelSuManagerLaunchIntent(@NonNull Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (String packageName : KERNELSU_PACKAGES) {
            Intent intent = getLaunchIntent(pm, packageName);
            if (intent != null) {
                return intent;
            }
        }
        return null;
    }

    @AnyThread
    @Nullable
    private static Intent getLaunchIntent(@NonNull PackageManager pm, @NonNull String packageName) {
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        return intent != null ? intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) : null;
    }

    /** The app's versionCode, or null when it isn't installed. */
    @AnyThread
    @Nullable
    private static Long getVersionCode(@NonNull PackageManager pm, @NonNull String packageName) {
        try {
            return PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(packageName, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
