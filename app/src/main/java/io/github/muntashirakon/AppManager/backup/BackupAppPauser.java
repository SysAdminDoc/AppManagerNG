// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.Closeable;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

class BackupAppPauser implements Closeable {
    private static final String TAG = BackupAppPauser.class.getSimpleName();
    private static final BackupAppPauser NO_OP = new BackupAppPauser(null, null);

    @VisibleForTesting
    interface CommandRunner {
        @NonNull
        Runner.Result run(@NonNull String command);
    }

    @NonNull
    private static final CommandRunner DEFAULT_COMMAND_RUNNER = Runner::runCommand;

    private final CommandRunner mCommandRunner;
    private final String mResumeCommand;

    private BackupAppPauser(CommandRunner commandRunner, String resumeCommand) {
        mCommandRunner = commandRunner;
        mResumeCommand = resumeCommand;
    }

    @NonNull
    static BackupAppPauser pauseForBackup(@NonNull String packageName, int userId, @NonNull BackupFlags backupFlags)
            throws BackupException {
        return pauseForBackup(packageName, userId, backupFlags,
                Prefs.BackupRestore.pauseAppsDuringBackup(), canPauseApps(), DEFAULT_COMMAND_RUNNER,
                ContextUtils.getContext().getPackageName());
    }

    @VisibleForTesting
    @NonNull
    static BackupAppPauser pauseForBackup(@NonNull String packageName, int userId, @NonNull BackupFlags backupFlags,
                                          boolean enabled, boolean canPauseApps,
                                          @NonNull CommandRunner commandRunner,
                                          @NonNull String selfPackageName)
            throws BackupException {
        if (!shouldPause(packageName, backupFlags, enabled, canPauseApps, selfPackageName)) {
            return NO_OP;
        }
        Runner.Result suspendResult = commandRunner.run(buildSuspendCommand(packageName, userId));
        if (suspendResult.isSuccessful()) {
            return new BackupAppPauser(commandRunner, buildUnsuspendCommand(packageName, userId));
        }
        Runner.Result stopResult = commandRunner.run(buildSignalCommand(packageName, "STOP"));
        if (stopResult.isSuccessful()) {
            return new BackupAppPauser(commandRunner, buildSignalCommand(packageName, "CONT"));
        }
        throw new BackupException("Could not pause " + packageName + " before backup.");
    }

    private static boolean shouldPause(@NonNull String packageName, @NonNull BackupFlags backupFlags,
                                       boolean enabled, boolean canPauseApps,
                                       @NonNull String selfPackageName) {
        return enabled
                && canPauseApps
                && backupFlags.backupData()
                && !SystemDataBackup.isSystemDataPackage(packageName)
                && !selfPackageName.equals(packageName);
    }

    private static boolean canPauseApps() {
        if (SelfPermissions.isSystemOrRootOrShell()) {
            return true;
        }
        return SelfPermissions.canFreezeUnfreezePackages() || SelfPermissions.canKillUid();
    }

    @NonNull
    private static String buildSuspendCommand(@NonNull String packageName, int userId) {
        return RunnerUtils.CMD_PM + " suspend --user " + userId + " " + RunnerUtils.escape(packageName);
    }

    @NonNull
    private static String buildUnsuspendCommand(@NonNull String packageName, int userId) {
        return RunnerUtils.CMD_PM + " unsuspend --user " + userId + " " + RunnerUtils.escape(packageName);
    }

    @NonNull
    private static String buildSignalCommand(@NonNull String packageName, @NonNull String signal) {
        String escapedPackageName = RunnerUtils.escape(packageName);
        return "pids=$(pidof " + escapedPackageName + " 2>/dev/null || true); "
                + "if [ -z \"$pids\" ]; then exit 0; fi; "
                + "kill -" + signal + " $pids";
    }

    @Override
    public void close() {
        if (mCommandRunner == null || mResumeCommand == null) {
            return;
        }
        Runner.Result result = mCommandRunner.run(mResumeCommand);
        if (!result.isSuccessful()) {
            Log.w(TAG, "Could not resume app after backup. Command: %s", mResumeCommand);
        }
    }
}
