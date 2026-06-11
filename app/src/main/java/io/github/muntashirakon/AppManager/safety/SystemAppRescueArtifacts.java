// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.safety;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.snapshot.SnapshotBundle;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.io.Path;

public final class SystemAppRescueArtifacts {
    private static final String RESCUE_DIRECTORY = "rescue";
    private static final String TIMESTAMP_PATTERN = "yyyyMMdd-HHmmss-SSS";

    private SystemAppRescueArtifacts() {}

    @WorkerThread
    @NonNull
    public static Result writePreOperationArtifacts(@NonNull Context context,
                                                    @NonNull List<UserPackagePair> systemTargets)
            throws IOException {
        Path rescueDir = Prefs.Storage.getAppManagerDirectory().findOrCreateDirectory(RESCUE_DIRECTORY);
        String timestamp = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).format(new Date());

        Path snapshotPath = rescueDir.createNewFile("pre-op-" + timestamp + ".am-snapshot.zip", null);
        try (OutputStream out = snapshotPath.openOutputStream()) {
            SnapshotBundle.writeTo(context, out);
        }

        Path scriptPath = rescueDir.createNewFile("pre-op-" + timestamp + "-install-existing.txt", null);
        try (OutputStream out = scriptPath.openOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(buildInstallExistingScript(systemTargets));
        }

        return new Result(snapshotPath, scriptPath);
    }

    @NonNull
    public static List<UserPackagePair> findSystemAppTargets(@NonNull List<UserPackagePair> targets) {
        ArrayList<UserPackagePair> systemTargets = new ArrayList<>();
        for (UserPackagePair target : targets) {
            if (isSystemAppTarget(target)) {
                systemTargets.add(target);
            }
        }
        return systemTargets;
    }

    private static boolean isSystemAppTarget(@NonNull UserPackagePair target) {
        try {
            ApplicationInfo info = PackageManagerCompat.getApplicationInfo(target.getPackageName(),
                    PackageManagerCompat.MATCH_DISABLED_COMPONENTS
                            | PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
                            | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                    target.getUserId());
            return (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (Throwable ignore) {
            return false;
        }
    }

    @VisibleForTesting
    @NonNull
    static String buildInstallExistingScript(@NonNull List<UserPackagePair> targets) {
        StringBuilder script = new StringBuilder();
        script.append("# AppManagerNG system-app rescue commands\n")
                .append("# Generated before a system-app batch operation.\n")
                .append("# Connect the affected device over ADB, then run these commands from your computer.\n\n");
        for (UserPackagePair target : targets) {
            String packageName = target.getPackageName();
            if (!isSafePackageName(packageName)) {
                script.append("# Skipped unsafe package name for user ")
                        .append(target.getUserId())
                        .append(": ")
                        .append(packageName)
                        .append('\n');
                continue;
            }
            script.append("adb shell cmd package install-existing --user ")
                    .append(target.getUserId())
                    .append(' ')
                    .append(packageName)
                    .append('\n');
        }
        return script.toString();
    }

    private static boolean isSafePackageName(@NonNull String packageName) {
        if (packageName.isEmpty()) {
            return false;
        }
        for (int i = 0; i < packageName.length(); ++i) {
            char c = packageName.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.') {
                continue;
            }
            return false;
        }
        return true;
    }

    public static final class Result {
        @NonNull
        private final Path mSnapshotPath;
        @NonNull
        private final Path mScriptPath;

        private Result(@NonNull Path snapshotPath, @NonNull Path scriptPath) {
            mSnapshotPath = snapshotPath;
            mScriptPath = scriptPath;
        }

        @NonNull
        public Path getSnapshotPath() {
            return mSnapshotPath;
        }

        @NonNull
        public Path getScriptPath() {
            return mScriptPath;
        }
    }
}
