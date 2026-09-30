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
        String screen = read(MODE_OF_OPS);
        assertCompletesOnDismiss(screen, "private void handleModeStatus(",
                "displayIncompleteUsbDebuggingMessage(activity, this::onIncompleteUsbDebuggingDismissed)");
        assertTrue(body(screen, "private void onIncompleteUsbDebuggingDismissed()").contains("finishModeApply(false, true)"));
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

    @Test
    public void serverFailuresAreClassifiedWhereTheyHappen() throws IOException {
        String manager = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java");
        String session = body(manager, "private ClientSession createSession(int port)");
        assertTrue(session, session.indexOf("Reason.UNRESPONSIVE") > session.indexOf("catch (SocketTimeoutException e)"));
        assertTrue(session, session.indexOf("Reason.NOT_ACKNOWLEDGED")
                > session.indexOf("catch (DataTransmission.HandshakeRejectedException e)"));
        String getSession = body(manager, "private ClientSession getSession()");
        int start = getSession.indexOf("startServer(configuredPort)");
        // An unreachable adbd is a port or pairing problem, so the chooser still gets its chance
        int passThrough = getSession.indexOf("catch (AdbPairingRequiredException | AdbUnreachableException e)");
        assertTrue(getSession, passThrough > start && getSession.indexOf("throw e;", passThrough) > passThrough);
        int squatter = getSession.indexOf("Reason.NOT_ACKNOWLEDGED");
        assertTrue(getSession, squatter > passThrough && getSession.indexOf("Reason.SERVER_START") > squatter);
        String adbConnect = body(manager, "private void connectToAdbd(@NonNull AdbConnectionManager manager)");
        assertTrue(adbConnect, adbConnect.contains("throw new AdbUnreachableException(e);"));
        assertTrue(adbConnect, adbConnect.contains("throw new AdbUnreachableException(null);"));
        assertFalse(adbConnect, adbConnect.contains("new IOException(\"Could not connect to ADB.\")"));
        assertFalse(body(manager, "private void useAdbStartServer(int localServerPort)").contains("manager.connect("));

        String ops = read("app/src/main/java/io/github/muntashirakon/AppManager/settings/Ops.java");
        assertTrue(body(ops, "private static int connectAdbLocked(int port,")
                .contains("return reportServerFailure(e, returnCodeOnFailure);"));
        assertTrue(body(ops, "private static int autoConnectWirelessDebuggingLocked(@NonNull Context context)")
                .contains("return reportServerFailure(e, STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED);"));
        // Exactly one message: the specific one, or the generic one
        String init = body(ops, "private static int initLocked(@NonNull Context context, boolean force, @NonNull @Mode String mode,");
        String fallback = init.substring(init.indexOf("catch (Throwable e)"));
        assertTrue(fallback, fallback.contains("int status = reportServerFailure(e, STATUS_FAILURE);"));
        assertTrue(fallback, fallback.indexOf("failed_to_use_the_current_mode_of_operation")
                > fallback.indexOf("if (status == STATUS_FAILURE)"));
    }

    @Test
    public void screensFinishOnAServerFailureWithoutASecondMessage() throws IOException {
        assertFinishesQuietly(read(BASE_ACTIVITY), "private void handleAuthenticationStatus(",
                "completeAuthentication(savedInstanceState);");
        assertFinishesQuietly(read(SPLASH_ACTIVITY), "private void handleAuthenticationStatus(",
                "completeAuthentication();");
        assertFinishesQuietly(read(MODE_OF_OPS), "private void handleModeStatus(",
                "finishModeApply(false, false);");
    }

    @Test
    public void everyAdbStartGetsAFreshShellAndOneMoreTryWhenItClosesAtOnce() throws IOException {
        String manager = read("app/src/main/java/io/github/muntashirakon/AppManager/servermanager/LocalServerManager.java");
        String start = body(manager, "private void useAdbStartServer(int localServerPort)");
        int retry = start.indexOf("catch (ShellClosedEarlyException e)");
        assertTrue(start, retry > start.indexOf("stageAndLaunch(manager, localServerPort);"));
        assertTrue(start, start.indexOf("stageAndLaunch(manager, localServerPort);", retry) > retry);

        String launch = body(manager, "private void stageAndLaunch(@NonNull AdbConnectionManager manager, int localServerPort)");
        // Upstream 03298fafa: a shell kept from an earlier start was reused while it looked open
        assertTrue(launch, launch.indexOf("IoUtils.closeQuietly(mAdbStream);") < launch.indexOf("manager.openStream(\"shell:\")"));
        assertFalse(launch, launch.contains("isClosed()"));

        // Upstream 0152f468f: a start that failed quickly, or a server gone right after it started
        String session = body(manager, "private ClientSession getSession()");
        assertTrue(session, session.contains("for (int attempt = 1; mSession == null; ++attempt)"));
        assertTrue(session, session.indexOf("isWorthRetrying(attempt, e)") < session.indexOf("Reason.SERVER_START"));
    }

    @Test
    public void screensThatDependOnTheModeFollowAChange() throws IOException {
        String base = "app/src/main/java/io/github/muntashirakon/AppManager/";
        String uid = "LiveDataChanges.observe(Ops.getWorkingUidLiveData(), ";
        assertTrue(read(base + "fm/dialogs/FilePropertiesDialogFragment.java").contains(uid + "getViewLifecycleOwner(), "));
        assertTrue(read(base + "main/MainListOptions.java").contains(uid + "getViewLifecycleOwner(), this::reloadUi);"));
        String running = read(base + "runningapps/RunningAppsActivity.java");
        String refresh = running.substring(running.indexOf("LiveDataChanges.observe(LocalServices.state(), this, "));
        assertTrue(refresh, refresh.indexOf("invalidateOptionsMenu();") < refresh.indexOf("});"));

        String interceptor = read(base + "intercept/ActivityInterceptor.java");
        // setupVariables() runs again on every Reset, so an observer there would pile up
        assertFalse(body(interceptor, "private void setupVariables()").contains("observe("));
        assertTrue(body(interceptor, "public void onAuthenticated(").contains(uid + "this, this::onWorkingUidChanged);"));
        assertTrue(body(interceptor, "private void onWorkingUidChanged()").contains("setChecked(false)"));
    }

    private static void assertFinishesQuietly(String source, String signature, String completion) {
        String handler = body(source, signature);
        int start = handler.indexOf("case Ops.STATUS_FAILURE_SERVER_START:");
        assertTrue(handler, start != -1);
        String branch = handler.substring(start, handler.indexOf("return;", start));
        assertTrue(branch, branch.contains("case Ops.STATUS_FAILURE_SERVER_UNRESPONSIVE:"));
        assertTrue(branch, branch.contains("case Ops.STATUS_FAILURE_SERVER_NOT_ACKNOWLEDGED:"));
        assertTrue(branch, branch.contains(completion));
        assertFalse(branch, branch.contains("Toast"));
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
