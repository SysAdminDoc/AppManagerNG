// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Contract test: every destructive operation entry point must gate through
 * {@link ActionAuthGate#authenticate} so the biometric/device-credential
 * challenge fires when the user has it enabled.
 *
 * This test reads source files and asserts the gate call is present near each
 * destructive trigger. If it fails, someone added or moved a destructive flow
 * without wiring the auth gate.
 */
public class DestructiveConfirmationContractTest {

    @Test
    public void mainActivityBatchOpsGatedByAuthGate() throws Exception {
        String source = readSource("app/src/main/java/io/github/muntashirakon/AppManager/main/MainActivity.java");
        assertTrue("Batch ops in MainActivity must call ActionAuthGate.authenticate",
                source.contains("ActionAuthGate.authenticate"));
    }

    @Test
    public void mainRecyclerAdapterQuickUninstallGatedByAuthGate() throws Exception {
        String source = readSource("app/src/main/java/io/github/muntashirakon/AppManager/main/MainRecyclerAdapter.java");
        assertTrue("Quick uninstall in MainRecyclerAdapter must call ActionAuthGate.authenticate",
                source.contains("ActionAuthGate.authenticate"));
    }

    @Test
    public void appInfoFragmentUninstallAndClearDataGatedByAuthGate() throws Exception {
        String source = readSource("app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java");
        assertTrue("Uninstall and clear-data in AppInfoFragment must call ActionAuthGate.authenticate",
                source.contains("ActionAuthGate.authenticate"));
    }

    @Test
    public void packageInstallerActivityGatedByAuthGate() throws Exception {
        String source = readSource("app/src/main/java/io/github/muntashirakon/AppManager/apk/installer/PackageInstallerActivity.java");
        assertTrue("Install trigger in PackageInstallerActivity must call ActionAuthGate.authenticate",
                source.contains("ActionAuthGate.authenticate"));
    }

    @Test
    public void oneClickOpsDestructiveActionsGatedByAuthGate() throws Exception {
        String source = readSource("app/src/main/java/io/github/muntashirakon/AppManager/oneclickops/OneClickOpsActivity.java");
        assertTrue("Destructive one-click ops must call ActionAuthGate.authenticate",
                source.contains("ActionAuthGate.authenticate"));
    }

    @Test
    public void terminalLaunchUsesAlwaysAuthGate() throws Exception {
        String source = readSource("app/src/main/java/io/github/muntashirakon/AppManager/misc/LabsActivity.java");
        assertTrue("Terminal launch in LabsActivity must use authenticateAlways (privileged route hardening)",
                source.contains("ActionAuthGate.authenticateAlways"));
    }

    @Test
    public void backupDeletionGatedByAuthGate() throws Exception {
        String singleRestore = readSource(
                "app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/RestoreSingleFragment.java");
        assertTrue("Backup deletion in RestoreSingleFragment must call ActionAuthGate.authenticate",
                singleRestore.contains("ActionAuthGate.authenticate"));

        String dialogFragment = readSource(
                "app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogFragment.java");
        assertTrue("Base backup deletion in BackupRestoreDialogFragment must call ActionAuthGate.authenticate",
                dialogFragment.contains("ActionAuthGate.authenticate"));
    }

    @Test
    public void destructiveDialogsUseSharedSafetyModel() throws Exception {
        String mainActivity = readSource("app/src/main/java/io/github/muntashirakon/AppManager/main/MainActivity.java");
        assertTrue("Batch destructive dialogs must use DestructiveActionConfirmation",
                mainActivity.contains("DestructiveActionConfirmation.forBatchOp"));

        String runningApps = readSource(
                "app/src/main/java/io/github/muntashirakon/AppManager/runningapps/RunningAppsActivity.java");
        assertTrue("Running-app destructive dialogs must use DestructiveActionConfirmation",
                runningApps.contains("DestructiveActionConfirmation.forKill")
                        && runningApps.contains("DestructiveActionConfirmation.forBatchOp"));

        String appInfo = readSource(
                "app/src/main/java/io/github/muntashirakon/AppManager/details/info/AppInfoFragment.java");
        assertTrue("App Details clear-data dialogs must use DestructiveActionConfirmation",
                appInfo.contains("DestructiveActionConfirmation")
                        && appInfo.contains(".forClearData(")
                        && appInfo.contains(".forDataOnlyPackageClear("));
        assertTrue("App Details uninstall dialogs must use DestructiveActionConfirmation",
                appInfo.contains(".forUninstall("));

        String singleRestore = readSource(
                "app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/RestoreSingleFragment.java");
        assertTrue("Selected-backup deletion must use DestructiveActionConfirmation",
                singleRestore.contains("DestructiveActionConfirmation")
                        && singleRestore.contains(".forBackupDelete("));

        String dialogFragment = readSource(
                "app/src/main/java/io/github/muntashirakon/AppManager/backup/dialog/BackupRestoreDialogFragment.java");
        assertTrue("Base-backup deletion must use DestructiveActionConfirmation",
                dialogFragment.contains("DestructiveActionConfirmation")
                        && dialogFragment.contains(".forBaseBackupDelete("));

        String profiles = readSource(
                "app/src/main/java/io/github/muntashirakon/AppManager/profiles/ProfilesActivity.java");
        assertTrue("Profile deletion must use DestructiveActionConfirmation",
                profiles.contains("DestructiveActionConfirmation")
                        && profiles.contains(".forProfileDelete("));

        String profileApplier = readSource(
                "app/src/main/java/io/github/muntashirakon/AppManager/profiles/ProfileApplierActivity.java");
        assertTrue("Profile apply confirmations must use the shared safety note model",
                profileApplier.contains("DestructiveActionConfirmation.withSafetyNote")
                        && profileApplier.contains("requiresProfileApplyConfirmation"));
    }

    @Test
    public void sharedSafetyModelDistinguishesReversibleAndIrreversibleActions() throws Exception {
        String source = readSource(
                "app/src/main/java/io/github/muntashirakon/AppManager/history/ops/DestructiveActionConfirmation.java");
        assertTrue("Shared destructive confirmation must include the reversible-note branch",
                source.contains("destructive_confirm_reversible_note"));
        assertTrue("Shared destructive confirmation must include the irreversible-warning branch",
                source.contains("destructive_confirm_irreversible_warning"));
        assertTrue("Shared destructive confirmation must key irreversible warnings to high risk",
                source.contains("OperationJournalMetadata.RISK_HIGH"));
        assertTrue("Shared destructive confirmation must include profile-delete coverage",
                source.contains("forProfileDelete"));
    }

    @Test
    public void fileManagerDuplicateDeleteGatedByAuthGate() throws Exception {
        String source = readSource("app/src/main/java/io/github/muntashirakon/AppManager/fm/FmFragment.java");
        assertTrue("Duplicate APK deletion in FmFragment must call ActionAuthGate.authenticate",
                source.contains("ActionAuthGate.authenticate"));
    }

    @Test
    public void authGateChecksPreferenceBeforePrompting() throws Exception {
        String source = readSource("app/src/main/java/io/github/muntashirakon/AppManager/crypto/auth/ActionAuthGate.java");
        assertTrue("ActionAuthGate must check Prefs.Privacy before prompting",
                source.contains("Prefs.Privacy.isActionAuthGateEnabled()"));
        assertTrue("ActionAuthGate must run the callback immediately when disabled",
                source.contains("onAuthenticated.run()"));
    }

    @Test
    public void authenticateAlwaysDoesNotCheckPreference() throws Exception {
        String source = readSource("app/src/main/java/io/github/muntashirakon/AppManager/crypto/auth/ActionAuthGate.java");
        int alwaysMethodStart = source.indexOf("public static void authenticateAlways(");
        assertTrue("authenticateAlways method must exist", alwaysMethodStart >= 0);
        int nextMethodStart = source.indexOf("private static void doAuthenticate(", alwaysMethodStart);
        assertTrue("doAuthenticate method must exist after authenticateAlways", nextMethodStart > alwaysMethodStart);
        String alwaysMethodBody = source.substring(alwaysMethodStart, nextMethodStart);
        assertFalse("authenticateAlways must NOT check isActionAuthGateEnabled",
                alwaysMethodBody.contains("isActionAuthGateEnabled"));
    }

    private String readSource(String relativePath) throws IOException {
        Path path = findProjectRoot().resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findProjectRoot() throws IOException {
        Path cursor = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (Files.exists(cursor.resolve("settings.gradle"))
                    && Files.exists(cursor.resolve("app/src/main/AndroidManifest.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("Could not locate AppManagerNG project root");
    }
}
