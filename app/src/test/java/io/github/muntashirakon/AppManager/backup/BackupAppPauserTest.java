// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;

@RunWith(RobolectricTestRunner.class)
public class BackupAppPauserTest {
    private static final String PACKAGE_NAME = "com.example.app";
    private static final int USER_ID = 10;

    @Test
    public void pauseForBackupUsesPackageSuspendAndUnsuspendsOnClose() throws BackupException {
        List<String> commands = new ArrayList<>();
        BackupAppPauser pauser = BackupAppPauser.pauseForBackup(
                PACKAGE_NAME, USER_ID, dataBackupFlags(), true, true, successRunner(commands), "self.pkg");

        assertEquals(1, commands.size());
        assertEquals(packageCommand("suspend"), commands.get(0));

        pauser.close();

        assertEquals(2, commands.size());
        assertEquals(packageCommand("unsuspend"), commands.get(1));
    }

    @Test
    public void pauseForBackupFallsBackToSignalStopWhenSuspendFails() throws BackupException {
        List<String> commands = new ArrayList<>();
        BackupAppPauser.CommandRunner runner = command -> {
            commands.add(command);
            return new Runner.Result(commands.size() == 1 ? 1 : 0);
        };

        BackupAppPauser pauser = BackupAppPauser.pauseForBackup(
                PACKAGE_NAME, USER_ID, dataBackupFlags(), true, true, runner, "self.pkg");

        assertEquals(packageCommand("suspend"), commands.get(0));
        assertTrue(commands.get(1).contains("pidof " + PACKAGE_NAME));
        assertTrue(commands.get(1).contains("kill -STOP $pids"));

        pauser.close();

        assertTrue(commands.get(2).contains("kill -CONT $pids"));
    }

    @Test
    public void pauseForBackupSkipsWhenDisabledOrNotDataBackup() throws BackupException {
        List<String> disabledCommands = new ArrayList<>();
        BackupAppPauser.pauseForBackup(
                PACKAGE_NAME, USER_ID, dataBackupFlags(), false, true, successRunner(disabledCommands), "self.pkg")
                .close();
        assertTrue(disabledCommands.isEmpty());

        List<String> apkOnlyCommands = new ArrayList<>();
        BackupAppPauser.pauseForBackup(
                PACKAGE_NAME, USER_ID, new BackupFlags(BackupFlags.BACKUP_APK_FILES), true, true,
                successRunner(apkOnlyCommands), "self.pkg").close();
        assertTrue(apkOnlyCommands.isEmpty());
    }

    @Test
    public void pauseForBackupThrowsWhenAllPauseAttemptsFail() {
        List<String> commands = new ArrayList<>();
        BackupAppPauser.CommandRunner runner = command -> {
            commands.add(command);
            return new Runner.Result(1);
        };

        assertThrows(BackupException.class, () -> BackupAppPauser.pauseForBackup(
                PACKAGE_NAME, USER_ID, dataBackupFlags(), true, true, runner, "self.pkg"));

        assertEquals(2, commands.size());
        assertEquals(packageCommand("suspend"), commands.get(0));
        assertTrue(commands.get(1).contains("kill -STOP $pids"));
    }

    private static BackupFlags dataBackupFlags() {
        return new BackupFlags(BackupFlags.BACKUP_INT_DATA);
    }

    private static BackupAppPauser.CommandRunner successRunner(List<String> commands) {
        return command -> {
            commands.add(command);
            return new Runner.Result(0);
        };
    }

    private static String packageCommand(String action) {
        return RunnerUtils.CMD_PM + " " + action + " --user " + USER_ID + " " + PACKAGE_NAME;
    }
}
