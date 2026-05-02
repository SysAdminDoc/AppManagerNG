// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.runner;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.settings.Ops;

/**
 * Detects which Android root manager is in use and whether the ZygiskNext layer
 * is loaded on top of it. Surfaced in the onboarding capability sheet and the
 * privilege health-check screen so users can see <em>which</em> root provider
 * has actually granted AppManagerNG superuser — important when more than one
 * coexists (Magisk + ZygiskNext, KernelSU + ZygiskNext, APatch + ZygiskNext).
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
 * <p>References:
 * <ul>
 *   <li>Magisk v30.x — {@code /data/adb/magisk}</li>
 *   <li>KernelSU v3.2.x — {@code /data/adb/ksu} / {@code apd} daemon</li>
 *   <li>APatch — {@code /data/adb/ap} (kernel patch via KernelPatch, ARM64)</li>
 *   <li>ZygiskNext v1.3.x — {@code /data/adb/modules/zygisksu}</li>
 * </ul>
 */
public final class RootManagerInfo {
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

    private RootManagerInfo(@NonNull Manager manager, @NonNull Source source, boolean zygiskNextPresent) {
        this.manager = manager;
        this.source = source;
        this.zygiskNextPresent = zygiskNextPresent;
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
                return new RootManagerInfo(fromShell, Source.MARKER, zn);
            }
        }
        Manager fromPackages = detectViaPackages(ctx);
        return new RootManagerInfo(fromPackages, fromPackages == Manager.NONE ? Source.NONE : Source.PACKAGE, false);
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

    @AnyThread
    @NonNull
    private static Manager detectViaPackages(@NonNull Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        // Magisk package id has been stable since the topjohnwu rewrite.
        if (isInstalled(pm, "com.topjohnwu.magisk")) {
            return Manager.MAGISK;
        }
        // KernelSU ships under two well-known applicationIds — original (weishu)
        // and KernelSU-Next fork (rifsxd) — either implies a KernelSU userland.
        if (isInstalled(pm, "me.weishu.kernelsu") || isInstalled(pm, "com.rifsxd.ksunext")) {
            return Manager.KERNELSU;
        }
        if (isInstalled(pm, "me.bmax.apatch")) {
            return Manager.APATCH;
        }
        return Manager.NONE;
    }

    @AnyThread
    private static boolean isInstalled(@NonNull PackageManager pm, @NonNull String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
