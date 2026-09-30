// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ModeOfOpsStatusContractTest {
    private static final String BASE_ACTIVITY = "app/src/main/java/io/github/muntashirakon/AppManager/BaseActivity.java";
    private static final String SPLASH_ACTIVITY = "app/src/main/java/io/github/muntashirakon/AppManager/main/SplashActivity.java";
    private static final String MODE_OF_OPS = "app/src/main/java/io/github/muntashirakon/AppManager/settings/ModeOfOpsPreference.java";

    @Test
    public void incompleteUsbDebuggingFinishesWhenItsDialogCloses() throws IOException {
        String ops = read("app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java");
        String dialog = body(ops, "public static void displayIncompleteUsbDebuggingMessage(");
        assertTrue(dialog, dialog.contains(".setOnDismissListener("));
        assertTrue(dialog, dialog.contains("onDismiss.run()"));

        // It used to fall through into the success branch while its dialog was still showing
        assertCompletesOnDismiss(read(BASE_ACTIVITY), "private void handleAuthenticationStatus(",
                "displayIncompleteUsbDebuggingMessage(this, () -> completeAuthentication(savedInstanceState))");
        assertCompletesOnDismiss(read(SPLASH_ACTIVITY), "private void handleAuthenticationStatus(",
                "displayIncompleteUsbDebuggingMessage(this, this::completeAuthentication)");
        assertCompletesOnDismiss(read(MODE_OF_OPS), "private void handleModeStatus(",
                "finishModeApply(false, true)");
    }

    @Test
    public void everyStatusDialogIsRecordedForARecreatedScreen() throws IOException {
        assertDialogsRecorded(read(BASE_ACTIVITY), "private void handleAuthenticationStatus(", "mViewModel.onStatusDialogShown();");
        assertDialogsRecorded(read(SPLASH_ACTIVITY), "private void handleAuthenticationStatus(", "mViewModel.onStatusDialogShown();");
        assertDialogsRecorded(read(MODE_OF_OPS), "private void handleModeStatus(", "mModel.onModeStatusDialogShown();");

        assertTrue(read(BASE_ACTIVITY).contains("mViewModel.getLostDialogStatus()"));
        assertTrue(read(SPLASH_ACTIVITY).contains("mViewModel.getLostDialogStatus()"));
        assertTrue(read(MODE_OF_OPS).contains("mModel.getLostDialogStatus()"));
    }

    @Test
    public void modeScreenKeepsASwitchAcrossConfigurationChanges() throws IOException {
        String screen = read(MODE_OF_OPS);
        assertTrue(screen, body(screen, "public void onCreate(").contains("mModel.getModeApplyState()"));
        assertFalse(screen, screen.contains("new ModeOfOpsApplyState()"));

        String destroy = body(screen, "public void onDestroyView()");
        int changing = destroy.indexOf("isChangingConfigurations()");
        assertTrue(destroy, changing != -1 && destroy.indexOf("dismissPendingModeApply()") > changing);

        String begin = body(screen, "private void beginModeApply(");
        assertTrue(begin, begin.indexOf("isModeOperationPending()") < begin.indexOf("mModeApplyState.begin("));
    }

    @Test
    public void restartSurvivesAServerThatIsAlreadyGone() throws IOException {
        String server = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServer.java");
        String restart = body(server, "public static void restart()");

        assertTrue(restart, restart.contains("synchronized (sPortRebindLock)"));
        int close = restart.indexOf("manager.closeBgServer();");
        int caught = restart.indexOf("catch (Exception e)", close);
        int stop = restart.indexOf("manager.stop();", caught);
        int start = restart.indexOf("manager.start();", stop);
        assertTrue(restart, close != -1 && caught > close && restart.indexOf("finally", caught) < stop && start > stop);
        // A failed start leaves no half-open session behind
        assertTrue(restart, restart.indexOf("manager.stop();", start) > start);
    }

    private static void assertCompletesOnDismiss(String source, String signature, String completion) {
        String handler = body(source, signature);
        int needMorePerms = handler.indexOf("case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:");
        int success = handler.indexOf("case Ops.STATUS_SUCCESS:");
        assertTrue(handler, needMorePerms != -1 && success > needMorePerms);
        String branch = handler.substring(needMorePerms, success);
        assertTrue(branch, branch.contains("displayIncompleteUsbDebuggingMessage("));
        assertTrue(branch, branch.contains(completion));
        assertTrue(branch, branch.trim().endsWith("return;"));
    }

    private static void assertDialogsRecorded(String source, String signature, String record) {
        String handler = body(source, signature);
        for (String dialog : new String[]{"Ops.connectWirelessDebugging(", "Ops.connectAdbInput(",
                "Ops.requestShizukuPermission(", "Ops.displayLocalNetworkPermissionMessage(",
                "Ops.pairAdbInput(", "Ops.displayIncompleteUsbDebuggingMessage("}) {
            int shown = handler.indexOf(dialog);
            assertTrue(dialog, shown != -1);
            int next = handler.indexOf("return;", shown);
            assertTrue(dialog, handler.substring(shown, next).contains(record));
        }
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature, start != -1);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); ++i) {
            char c = source.charAt(i);
            if (c == '{') ++depth;
            else if (c == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        throw new AssertionError("Unbalanced braces after " + signature);
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(findRepoRoot().resolve(path)), StandardCharsets.UTF_8);
    }

    private static Path findRepoRoot() {
        Path cursor = Paths.get("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve("app/src/main/java"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
