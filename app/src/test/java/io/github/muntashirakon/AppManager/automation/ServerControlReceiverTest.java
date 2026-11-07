// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BadParcelableException;
import android.os.Build;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.topjohnwu.superuser.Shell;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbUtils;
import io.github.muntashirakon.AppManager.crypto.auth.AuthManager;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.shizuku.ShizukuBridge;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;

/**
 * Issue 19: an automation app starting and stopping the privileged server. The broadcasts here go
 * through the manifest receiver by action and package, as a caller's would. Most run the real Ops
 * paths over a shadowed server.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {
        ServerControlReceiverTest.NoRootRunnerUtils.class,
        ServerControlReceiverTest.NoShell.class,
        ServerControlReceiverTest.NoShizuku.class,
        ServerControlReceiverTest.FakeLocalServer.class,
        ServerControlReceiverTest.FakeLocalServices.class,
        ServerControlReceiverTest.SelfUsers.class,
        ServerControlReceiverTest.NoAdb.class,
})
public class ServerControlReceiverTest {
    private static final String KEY = "correct-horse-battery-st";
    private static final String WRONG_KEY = "not-the-key-of-this-app0";
    private static final int NOT_ANSWERED = 12345;

    static boolean sListening;
    static boolean sHeld;
    static boolean sReachable;
    static boolean sSurvivesClose;
    static boolean sServicesAlive;

    private final Application mContext = ApplicationProvider.getApplicationContext();
    private String mSavedMode;
    private String mSavedKey;

    @Before
    public void setUp() throws Exception {
        mSavedMode = Ops.getMode();
        mSavedKey = AuthManager.getKey();
        AuthManager.setKey(KEY);
        Prefs.AppActions.setServerControlBroadcastsEnabled(true);
        ServerControlReceiver.resetRejectionLimitForTest();
        ShadowToast.reset();
        runOnBackground(() -> {
            AppsDb.getInstance().opHistoryDao().deleteAll();
            return null;
        });
    }

    @After
    public void tearDown() throws Exception {
        ServerControlReceiver.setOperationsForTest(null);
        ServerControlReceiver.resetRejectionLimitForTest();
        Prefs.AppActions.setServerControlBroadcastsEnabled(false);
        AuthManager.setKey(mSavedKey);
        Ops.setMode(mSavedMode);
        sListening = sHeld = sReachable = sSurvivesClose = sServicesAlive = false;
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 36);
        runOnBackground(() -> {
            AppsDb.getInstance().opHistoryDao().deleteAll();
            return null;
        });
    }

    // Off by default, off means off

    @Test
    public void theSettingIsOffOnAFreshInstallAndABundleCannotTurnItOn() {
        Prefs.AppActions.setServerControlBroadcastsEnabled(false);
        AppPref pref = AppPref.getInstance();
        String key = AppPref.PrefKey.getKey(AppPref.PrefKey.PREF_SERVER_CONTROL_BROADCASTS_BOOL);

        assertEquals(Boolean.FALSE, pref.getDefaultValue(AppPref.PrefKey.PREF_SERVER_CONTROL_BROADCASTS_BOOL));
        assertFalse(AppPref.isPortablePreference(key, true));
        assertTrue(AppPref.SENSITIVE_PREF_KEYS.contains(key));
    }

    @Test
    public void withTheSettingOffEvenTheRightKeyDoesNothingAtAll() throws Exception {
        Prefs.AppActions.setServerControlBroadcastsEnabled(false);
        Ops.setMode(Ops.MODE_ROOT);
        FakeOperations operations = new FakeOperations();
        ServerControlReceiver.setOperationsForTest(operations);

        int result = sendAndWait(authorized("SERVER_STOP"));

        assertEquals(2, result);
        assertEquals(0, operations.calls.get());
        assertEquals(Ops.MODE_ROOT, Ops.getMode());
        assertNull(ShadowToast.getTextOfLatestToast());
        // No work was started, so there is nothing to wait for before looking
        assertEquals(0, rows().size());
        assertEquals(0, resultBroadcasts().size());
    }

    // The key

    @Test
    public void aWrongKeyChangesNothingAndLeavesARowWithoutTheKey() throws Exception {
        Ops.setMode(Ops.MODE_ROOT);

        int result = sendAndWait(command("SERVER_SET_MODE")
                .putExtra(BaseActivity.EXTRA_AUTH, WRONG_KEY)
                .putExtra("mode", Ops.MODE_NO_ROOT)
                .putExtra("result_package", "com.example.caller"));

        assertEquals(3, result);
        assertEquals(Ops.MODE_ROOT, Ops.getMode());
        OpHistory row = awaitRows(1).get(0);
        assertEquals(OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION, row.type);
        assertEquals(OpHistoryManager.STATUS_FAILURE, row.status);
        assertTrue(row.serializedData, row.serializedData.contains("result=3"));
        assertNoSecrets(row);
        pumpUntil("the toast", () -> ShadowToast.getTextOfLatestToast() != null);
        assertEquals(mContext.getString(R.string.server_control_bad_key), ShadowToast.getTextOfLatestToast());
        // Telling a caller more than the code is for callers who proved who they are
        assertEquals(0, resultBroadcasts().size());
    }

    @Test
    public void aMissingOrMistypedKeyIsAWrongKey() throws Exception {
        assertEquals(3, sendAndWait(command("SERVER_STOP")));
        assertEquals(3, sendAndWait(command("SERVER_STOP").putExtra(BaseActivity.EXTRA_AUTH, 7)));
        awaitRows(1);
    }

    @Test
    public void aBlankStoredKeyRefusesABlankOne() throws Exception {
        AuthManager.setKey("");

        assertEquals(3, sendAndWait(command("SERVER_STOP").putExtra(BaseActivity.EXTRA_AUTH, "")));
        awaitRows(1);
    }

    @Test
    public void aFloodOfBadKeysCannotFillTheHistory() throws Exception {
        for (int i = 0; i < 5; ++i) {
            assertEquals(3, sendAndWait(command("SERVER_STOP").putExtra(BaseActivity.EXTRA_AUTH, WRONG_KEY)));
        }
        awaitRows(1);
        settleBackgroundWork();
        assertEquals(1, rows().size());

        // Positive control: the next one past the interval is recorded
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertEquals(3, sendAndWait(command("SERVER_STOP").putExtra(BaseActivity.EXTRA_AUTH, WRONG_KEY)));
        awaitRows(2);
    }

    @Test
    public void extrasThatCannotBeUnparceledAreAnsweredNotACrash() throws Exception {
        Intent poisoned = new Intent(BuildConfig.APPLICATION_ID + ".action.SERVER_STOP") {
            @Override
            public String getStringExtra(String name) {
                throw new BadParcelableException("ClassNotFoundException when unmarshalling");
            }
        };

        new ServerControlReceiver().onReceive(mContext, poisoned);

        OpHistory row = awaitRows(1).get(0);
        assertTrue(row.serializedData, row.serializedData.contains("result=4"));
    }

    // Schema

    @Test
    public void aBadRequestRunsNothingAndStillTellsAProvenCaller() throws Exception {
        FakeOperations operations = new FakeOperations();
        ServerControlReceiver.setOperationsForTest(operations);

        int result = sendAndWait(authorized("SERVER_SET_MODE")
                .putExtra("mode", "root; reboot")
                .putExtra("request_id", "t1")
                .putExtra("result_package", "com.example.caller"));

        assertEquals(4, result);
        assertEquals(0, operations.calls.get());
        Intent answer = awaitResultBroadcast();
        assertEquals("com.example.caller", answer.getPackage());
        assertEquals(4, answer.getIntExtra("result_code", 0));
        assertEquals("t1", answer.getStringExtra("request_id"));
        OpHistory row = awaitRows(1).get(0);
        assertFalse(row.serializedData, row.serializedData.contains("reboot"));
    }

    @Test
    public void aBadResultPackageGetsNoBroadcast() throws Exception {
        assertEquals(4, sendAndWait(authorized("SERVER_STOP").putExtra("result_package", "a.b/c")));
        awaitRows(1);
        assertEquals(0, resultBroadcasts().size());
    }

    @Test
    public void wirelessDebuggingIsRefusedWhereTheScreenDoesNotOfferIt() throws Exception {
        FakeOperations operations = new FakeOperations();
        ServerControlReceiver.setOperationsForTest(operations);
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", Build.VERSION_CODES.Q);

        assertEquals(4, sendAndWait(authorized("SERVER_SET_MODE").putExtra("mode", Ops.MODE_ADB_WIFI)));

        assertEquals(0, operations.calls.get());
        awaitRows(1);
    }

    // Choosing the mode, through the real Ops path

    @Test
    public void theRightKeySwitchesAndSavesTheMode() throws Exception {
        Ops.setMode(Ops.MODE_ROOT);

        int result = sendAndWait(authorized("SERVER_SET_MODE").putExtra("mode", Ops.MODE_NO_ROOT));

        assertEquals(-1, result);
        assertEquals(Ops.MODE_NO_ROOT, Ops.getMode());
        OpHistory row = awaitRows(1).get(0);
        assertEquals(OpHistoryManager.STATUS_SUCCESS, row.status);
        assertTrue(row.serializedData, row.serializedData.contains("server_control"));
        assertTrue(row.serializedData, row.serializedData.contains("no-root"));
        assertNoSecrets(row);
        pumpUntil("the toast", () -> ShadowToast.getTextOfLatestToast() != null);
        assertEquals(mContext.getString(R.string.server_control_mode_set, Ops.MODE_NO_ROOT),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void aFailedSwitchKeepsTheSavedModeAndSaysWhy() throws Exception {
        Ops.setMode(Ops.MODE_NO_ROOT);

        // These shadows deny root
        int result = sendAndWait(authorized("SERVER_SET_MODE")
                .putExtra("mode", Ops.MODE_ROOT)
                .putExtra("result_package", "com.example.caller"));

        assertEquals(100 + Ops.STATUS_FAILURE, result);
        assertEquals(Ops.MODE_NO_ROOT, Ops.getMode());
        assertEquals(OpHistoryManager.STATUS_FAILURE, awaitRows(1).get(0).status);
        Intent answer = awaitResultBroadcast();
        assertEquals(100 + Ops.STATUS_FAILURE, answer.getIntExtra("result_code", 0));
        assertEquals(Ops.STATUS_FAILURE, answer.getIntExtra("ops_status", -1));
        assertEquals(Ops.MODE_NO_ROOT, answer.getStringExtra("mode"));
    }

    // Stopping, through the real Ops path

    @Test
    public void stopKeepsTheSavedModeAndClosesAServerThisProcessNeverConnectedTo() throws Exception {
        Ops.setMode(Ops.MODE_ROOT);
        // A process woken for this broadcast: a server is listening and nothing here holds it
        sListening = true;
        sReachable = true;

        int result = sendAndWait(authorized("SERVER_STOP"));

        assertEquals(-1, result);
        assertFalse("The server must really be gone", sListening);
        assertEquals(Ops.MODE_ROOT, Ops.getMode());
        assertFalse(Ops.isAdb());
        assertFalse(Ops.isDirectRoot());
        assertEquals(OpHistoryManager.STATUS_SUCCESS, awaitRows(1).get(0).status);
    }

    @Test
    public void stopWithNothingRunningSucceedsWithoutConnecting() throws Exception {
        sReachable = true;

        assertEquals(-1, sendAndWait(authorized("SERVER_STOP")));

        assertFalse(sHeld);
        awaitRows(1);
    }

    @Test
    public void stopTellsTheCallerWhenTheServerSurvives() throws Exception {
        sListening = true;
        sReachable = true;
        sSurvivesClose = true;

        assertEquals(100 + Ops.STATUS_FAILURE, sendAndWait(authorized("SERVER_STOP")));

        assertEquals(OpHistoryManager.STATUS_FAILURE, awaitRows(1).get(0).status);
    }

    @Test
    public void stopTellsTheCallerWhenItCannotReachTheServer() throws Exception {
        sListening = true;
        sReachable = false;

        assertEquals(100 + Ops.STATUS_FAILURE, sendAndWait(authorized("SERVER_STOP")));

        assertEquals(OpHistoryManager.STATUS_FAILURE, awaitRows(1).get(0).status);
    }

    // Starting

    @Test
    public void startInASavedModeWithNoServerSaysSoInsteadOfDone() throws Exception {
        Ops.setMode(Ops.MODE_NO_ROOT);

        assertEquals(6, sendAndWait(authorized("SERVER_START")));

        assertEquals(Ops.MODE_NO_ROOT, Ops.getMode());
        assertEquals(OpHistoryManager.STATUS_FAILURE, awaitRows(1).get(0).status);
    }

    @Test
    public void startThatLeavesAServerRunningIsDone() throws Exception {
        FakeOperations operations = new FakeOperations();
        operations.serverRunning = true;
        ServerControlReceiver.setOperationsForTest(operations);

        assertEquals(-1, sendAndWait(authorized("SERVER_START")));

        assertEquals(1, operations.starts.get());
        awaitRows(1);
    }

    @Test
    public void aModeThatNeedsAPersonReportsItsStatusAndTellsTheUser() throws Exception {
        FakeOperations operations = new FakeOperations();
        operations.status = Ops.STATUS_SHIZUKU_PERMISSION_REQUIRED;
        ServerControlReceiver.setOperationsForTest(operations);

        assertEquals(100 + Ops.STATUS_SHIZUKU_PERMISSION_REQUIRED,
                sendAndWait(authorized("SERVER_SET_MODE").putExtra("mode", Ops.MODE_SHIZUKU)));

        awaitRows(1);
        pumpUntil("the toast", () -> ShadowToast.getTextOfLatestToast() != null);
        assertEquals(mContext.getString(R.string.server_control_needs_you), ShadowToast.getTextOfLatestToast());
    }

    // One at a time, and never over the settings screen

    @Test
    public void aSecondCommandWhileOneRunsIsAnsweredBusy() throws Exception {
        FakeOperations operations = new FakeOperations();
        operations.block();
        ServerControlReceiver.setOperationsForTest(operations);
        Sent first = sendOrdered(authorized("SERVER_STOP"));
        operations.awaitEntered();
        try {
            assertEquals(5, sendAndWait(authorized("SERVER_START")));
            assertEquals(0, operations.starts.get());
        } finally {
            operations.release();
        }
        pumpUntil("the first command", () -> first.done);
        assertEquals(-1, first.code);
        awaitRows(2);
    }

    @Test
    public void aCommandIsRefusedWhileTheScreenIsApplyingAMode() throws Exception {
        FakeOperations operations = new FakeOperations();
        operations.applying = true;
        ServerControlReceiver.setOperationsForTest(operations);

        assertEquals(5, sendAndWait(authorized("SERVER_SET_MODE")
                .putExtra("mode", Ops.MODE_ROOT)
                .putExtra("result_package", "com.example.caller")));

        assertEquals(0, operations.calls.get());
        assertEquals(5, awaitResultBroadcast().getIntExtra("result_code", 0));
        awaitRows(1);
    }

    // Slow commands

    @Test
    public void aCommandOutlivingTheBroadcastAnswersInTimeThenReportsTheRealResult() throws Exception {
        FakeOperations operations = new FakeOperations();
        operations.block();
        ServerControlReceiver.setOperationsForTest(operations);

        Sent sent = sendOrdered(authorized("SERVER_SET_MODE")
                .putExtra("mode", Ops.MODE_ROOT)
                .putExtra("request_id", "slow-1")
                .putExtra("result_package", "com.example.caller"));
        operations.awaitEntered();
        try {
            // Inside Android's 60 seconds for a background broadcast, with room to spare
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(29));
            assertFalse("Too early", sent.done);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            pumpUntil("the deadline", () -> sent.done);
            assertEquals(1, sent.code);
            assertEquals(0, resultBroadcasts().size());
        } finally {
            operations.release();
        }

        Intent answer = awaitResultBroadcast();
        assertEquals(-1, answer.getIntExtra("result_code", 0));
        assertEquals("slow-1", answer.getStringExtra("request_id"));
        assertEquals(OpHistoryManager.STATUS_SUCCESS, awaitRows(1).get(0).status);
    }

    @Test
    public void aForegroundBroadcastGetsItsShorterDeadline() throws Exception {
        FakeOperations operations = new FakeOperations();
        operations.block();
        ServerControlReceiver.setOperationsForTest(operations);

        Sent sent = sendOrdered(authorized("SERVER_STOP").addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
        operations.awaitEntered();
        try {
            // Android allows 10 seconds here
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));
            pumpUntil("the deadline", () -> sent.done);
            assertEquals(1, sent.code);
        } finally {
            operations.release();
        }
        awaitRows(1);
    }

    @Test
    public void aCommandThatThrowsIsAFailureAndTheNextOneStillRuns() throws Exception {
        FakeOperations operations = new FakeOperations();
        operations.fail = true;
        ServerControlReceiver.setOperationsForTest(operations);

        assertEquals(100 + Ops.STATUS_FAILURE, sendAndWait(authorized("SERVER_STOP")));
        awaitRows(1);

        operations.fail = false;
        assertEquals(-1, sendAndWait(authorized("SERVER_STOP")));
        awaitRows(2);
    }

    // A caller that can't read an ordered result

    @Test
    public void anUnorderedBroadcastRunsAndAnswersByResultBroadcast() throws Exception {
        Ops.setMode(Ops.MODE_ROOT);

        mContext.sendBroadcast(authorized("SERVER_SET_MODE")
                .putExtra("mode", Ops.MODE_NO_ROOT)
                .putExtra("request_id", "t2")
                .putExtra("result_package", "com.example.caller"));

        Intent answer = awaitResultBroadcast();
        assertEquals("com.example.caller", answer.getPackage());
        assertEquals(BuildConfig.APPLICATION_ID + ".action.SERVER_CONTROL_RESULT", answer.getAction());
        assertEquals(-1, answer.getIntExtra("result_code", 0));
        assertEquals(Ops.STATUS_SUCCESS, answer.getIntExtra("ops_status", -1));
        assertEquals("SERVER_SET_MODE", answer.getStringExtra("command"));
        assertEquals(Ops.MODE_NO_ROOT, answer.getStringExtra("mode"));
        assertEquals("t2", answer.getStringExtra("request_id"));
        assertEquals(Ops.getWorkingUid(), answer.getIntExtra("uid", -1));
        assertFalse(answer.hasExtra(BaseActivity.EXTRA_AUTH));
        assertEquals(Ops.MODE_NO_ROOT, Ops.getMode());
        awaitRows(1);
    }

    // Helpers

    private static void assertNoSecrets(@NonNull OpHistory row) {
        String all = row.serializedData + " " + row.serializedExtra;
        assertFalse(all, all.contains(KEY));
        assertFalse(all, all.contains(WRONG_KEY));
    }

    @NonNull
    private static Intent command(@NonNull String command) {
        return new Intent(BuildConfig.APPLICATION_ID + ".action." + command).setPackage(BuildConfig.APPLICATION_ID);
    }

    @NonNull
    private static Intent authorized(@NonNull String command) {
        return command(command).putExtra(BaseActivity.EXTRA_AUTH, KEY);
    }

    private static final class Sent {
        volatile boolean done;
        volatile int code;
    }

    @NonNull
    private Sent sendOrdered(@NonNull Intent intent) {
        Sent sent = new Sent();
        mContext.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent received) {
                sent.code = getResultCode();
                sent.done = true;
            }
        }, null, NOT_ANSWERED, null, null);
        return sent;
    }

    private int sendAndWait(@NonNull Intent intent) throws Exception {
        Sent sent = sendOrdered(intent);
        pumpUntil("the broadcast to finish", () -> sent.done);
        return sent.code;
    }

    private interface Condition {
        boolean holds() throws Exception;
    }

    private static void pumpUntil(@NonNull String what, @NonNull Condition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.holds()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for " + what);
            }
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(5);
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Long enough for a row that shouldn't exist to show up if it were coming. */
    private static void settleBackgroundWork() throws Exception {
        Thread.sleep(300);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @NonNull
    private static List<OpHistory> rows() throws Exception {
        return runOnBackground(() -> AppsDb.getInstance().opHistoryDao().getAll());
    }

    @NonNull
    private static List<OpHistory> awaitRows(int count) throws Exception {
        pumpUntil(count + " history rows", () -> rows().size() >= count);
        return rows();
    }

    @NonNull
    private List<Intent> resultBroadcasts() {
        List<Intent> answers = new ArrayList<>();
        for (Intent intent : new ArrayList<>(shadowOf(mContext).getBroadcastIntents())) {
            if ((BuildConfig.APPLICATION_ID + ".action.SERVER_CONTROL_RESULT").equals(intent.getAction())) {
                answers.add(intent);
            }
        }
        return answers;
    }

    @NonNull
    private Intent awaitResultBroadcast() throws Exception {
        pumpUntil("the result broadcast", () -> !resultBroadcasts().isEmpty());
        return resultBroadcasts().get(0);
    }

    private static <T> T runOnBackground(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        new Thread(task).start();
        return task.get(20, TimeUnit.SECONDS);
    }

    private static final class FakeOperations implements ServerControlReceiver.Operations {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger starts = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);
        volatile boolean blocking;
        volatile boolean applying;
        volatile boolean serverRunning;
        volatile boolean fail;
        volatile int status = Ops.STATUS_SUCCESS;

        void block() {
            blocking = true;
        }

        void release() {
            released.countDown();
        }

        void awaitEntered() throws Exception {
            // The broadcast itself is delivered on the main looper
            pumpUntil("the command to run", () -> entered.getCount() == 0);
        }

        private int run() {
            calls.incrementAndGet();
            entered.countDown();
            if (blocking) {
                try {
                    released.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (fail) {
                throw new IllegalStateException("Thrown by the test");
            }
            return status;
        }

        @Override
        public int start(@NonNull Context context) {
            starts.incrementAndGet();
            return run();
        }

        @Override
        public int switchMode(@NonNull Context context, @NonNull String mode) {
            return run();
        }

        @Override
        public int stop(@NonNull Context context) {
            return run();
        }

        @Override
        public boolean isModeApplyInProgress() {
            return applying;
        }

        @Override
        public boolean isServerRunning() {
            return serverRunning;
        }
    }

    @Implements(RunnerUtils.class)
    public static class NoRootRunnerUtils {
        @Implementation
        protected static Boolean isAppGrantedRoot() {
            return false;
        }
    }

    @Implements(Shell.class)
    public static class NoShell {
        @Implementation
        @Nullable
        protected static Shell getShell() {
            return null;
        }
    }

    @Implements(ShizukuBridge.class)
    public static class NoShizuku {
        @Implementation
        protected static boolean isBinderAlive() {
            return false;
        }

        @Implementation
        protected static boolean isUsable() {
            return false;
        }

        @Implementation
        protected static boolean supportsUserService() {
            return false;
        }

        @Implementation
        protected static boolean hasPermission() {
            return false;
        }
    }

    /**
     * Behaves as the real one does for a process holding no connection: closeIfConnected only acts
     * after getInstance has made one.
     */
    @Implements(LocalServer.class)
    public static class FakeLocalServer {
        @Implementation
        @Nullable
        protected static LocalServer getInstance() throws IOException {
            if (!sReachable) {
                throw new IOException("Could not create session");
            }
            sHeld = true;
            return null;
        }

        @Implementation
        protected static boolean alive(Context context) {
            return sListening;
        }

        @Implementation
        protected static boolean alive(Context context, int port) {
            return sListening;
        }

        @Implementation
        protected static void closeIfConnected() {
            if (sHeld) {
                sHeld = false;
                if (!sSurvivesClose) {
                    sListening = false;
                }
            }
        }
    }

    @Implements(LocalServices.class)
    public static class FakeLocalServices {
        @Implementation
        protected static boolean alive() {
            return sServicesAlive;
        }

        @Implementation
        protected static void stopServices() {
            sServicesAlive = false;
        }

        @Implementation
        protected static void bindServices() {
        }

        @Implementation
        protected static void bindServicesIfNotAlready() {
        }
    }

    @Implements(Users.class)
    public static class SelfUsers {
        @Implementation
        protected static int getSelfOrRemoteUid() {
            return android.os.Process.myUid();
        }
    }

    @Implements(AdbUtils.class)
    public static class NoAdb {
        @Implementation
        protected static boolean isAdbdRunning() {
            return false;
        }

        @Implementation
        protected static boolean enableWirelessDebugging(Context context) {
            return false;
        }

        @Implementation
        protected static boolean startAdb(int port) {
            return false;
        }
    }
}
