// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_ABORTED;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.utils.MiuiUtils;
import io.github.muntashirakon.io.Path;

/**
 * A MIUI or HyperOS retry runs inside the install call that needed it, and that call returns what
 * the last attempt did. The retry used to start from the listener after install() had been told
 * the first attempt failed, so a restore gave up while its retry was still installing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {PackageInstallerRetryTest.ScriptedSessions.class, PackageInstallerRetryTest.AffectedRom.class})
public class PackageInstallerRetryTest {
    private static final String PACKAGE = "com.example.restored";
    private static final String MIUI_FAILURE = "INSTALL_FAILED_ABORTED: Permission denied";

    @After
    public void forgetTheScript() {
        ScriptedSessions.sInstallerNames.clear();
        ScriptedSessions.sResults.clear();
        ScriptedSessions.sLateReportOfTheFirstSession = false;
        AffectedRom.sMiui = false;
        AffectedRom.sHyperOs = false;
    }

    @Test
    public void aMiuiRetryIsWhatTheInstallReturnsAndAllTheListenerHears() throws Throwable {
        AffectedRom.sMiui = true;
        ScriptedSessions.sResults.add(new Object[]{STATUS_FAILURE_ABORTED, MIUI_FAILURE});
        ScriptedSessions.sResults.add(new Object[]{STATUS_SUCCESS, null});
        List<Integer> heard = new CopyOnWriteArrayList<>();

        boolean installed = installOnAWorker(heard, options("com.google.android.packageinstaller"));

        assertTrue(installed);
        assertEquals(Collections.singletonList(STATUS_SUCCESS), heard);
        assertEquals(2, ScriptedSessions.sInstallerNames.size());
    }

    @Test
    public void aHyperOsRetryAsksForTheShellInstallerAndLeavesTheCallersOptionsAlone() throws Throwable {
        // The HyperOS retry is for privileged installs
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.INSTALL_PACKAGES);
        AffectedRom.sHyperOs = true;
        ScriptedSessions.sResults.add(new Object[]{STATUS_FAILURE_ABORTED,
                "INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION: system package"});
        ScriptedSessions.sResults.add(new Object[]{STATUS_SUCCESS, null});
        List<Integer> heard = new CopyOnWriteArrayList<>();
        InstallerOptions options = options("com.android.vending");

        boolean installed = installOnAWorker(heard, options);

        assertTrue(installed);
        assertEquals(Collections.singletonList(STATUS_SUCCESS), heard);
        assertEquals(Arrays.asList("com.android.vending", "com.android.shell"), ScriptedSessions.sInstallerNames);
        assertEquals("com.android.vending", options.getInstallerName());
    }

    @Test
    public void theFirstSessionReportingLateDoesNotStandForTheRetry() throws Throwable {
        // Dismissing the first attempt's confirmation reports its session as aborted, here
        // while the retry is running
        AffectedRom.sMiui = true;
        ScriptedSessions.sLateReportOfTheFirstSession = true;
        ScriptedSessions.sResults.add(new Object[]{STATUS_FAILURE_ABORTED, MIUI_FAILURE});
        ScriptedSessions.sResults.add(new Object[]{STATUS_SUCCESS, null});
        List<Integer> heard = new CopyOnWriteArrayList<>();

        boolean installed = installOnAWorker(heard, options("com.google.android.packageinstaller"));

        assertTrue(installed);
        assertEquals(Collections.singletonList(STATUS_SUCCESS), heard);
    }

    @NonNull
    private static InstallerOptions options(@NonNull String installerName) {
        InstallerOptions options = InstallerOptions.getDefault();
        options.setUserId(UserHandleHidden.myUserId());
        options.setInstallerName(installerName);
        return options;
    }

    /** What a restore does: the extracted APKs (none are needed to script the sessions) as paths. */
    private static boolean installOnAWorker(@NonNull List<Integer> heard, @NonNull InstallerOptions options)
            throws Throwable {
        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
        installer.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
            @Override
            public void onStartInstall(int sessionId, String packageName) {
            }

            @Override
            public void onFinishedInstall(int sessionId, String packageName, int result,
                                          @Nullable String blockingPackage, @Nullable String statusMessage) {
                heard.add(result);
            }
        });
        AtomicBoolean installed = new AtomicBoolean();
        AtomicReference<Throwable> crash = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                installed.set(installer.install(new Path[0], PACKAGE, options));
            } catch (Throwable th) {
                crash.set(th);
            }
        });
        worker.start();
        worker.join(60_000);
        if (crash.get() != null) {
            throw crash.get();
        }
        assertNull("install() didn't return", worker.isAlive() ? "alive" : null);
        return installed.get();
    }

    /**
     * Sessions that end as scripted. A commit reports its result the way a privileged one does:
     * it sets the status and, unless the session is already done, tells installCompleted.
     */
    @Implements(PackageInstallerCompat.class)
    public static class ScriptedSessions {
        static final List<String> sInstallerNames = new CopyOnWriteArrayList<>();
        static final Deque<Object[]> sResults = new ArrayDeque<>();
        static volatile boolean sLateReportOfTheFirstSession;

        @RealObject
        private PackageInstallerCompat mRealInstaller;

        @Implementation
        protected boolean openSession(int userId, int installFlags, String installerName, int installLocation,
                                      @Nullable String originatingPackage, @Nullable Uri originatingUri,
                                      int installScenario, int packageSource, boolean requestUpdateOwnership,
                                      boolean disableVerification, long sessionSizeBytes) throws Exception {
            sInstallerNames.add(installerName);
            set("mSessionId", sInstallerNames.size());
            return true;
        }

        @Implementation
        protected boolean commit(int userId) throws Exception {
            int sessionId = (int) get("mSessionId");
            if (sLateReportOfTheFirstSession && sessionId > 1) {
                Intent lateReport = new Intent(PackageInstallerCompat.ACTION_INSTALL_COMPLETED);
                lateReport.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, PACKAGE);
                lateReport.putExtra(PackageInstaller.EXTRA_STATUS, STATUS_FAILURE_ABORTED);
                lateReport.putExtra(PackageInstaller.EXTRA_SESSION_ID, 1);
                ((BroadcastReceiver) get("mBroadcastReceiver")).onReceive(RuntimeEnvironment.getApplication(),
                        lateReport);
            }
            Object[] result = sResults.remove();
            int status = (int) result[0];
            set("mFinalStatus", status);
            set("mStatusMessage", result[1]);
            if (!(boolean) get("mInstallCompleted")) {
                set("mInstallCompleted", true);
                Method installCompleted = PackageInstallerCompat.class.getDeclaredMethod("installCompleted",
                        int.class, int.class, String.class, String.class);
                installCompleted.setAccessible(true);
                installCompleted.invoke(mRealInstaller, sessionId, status, null, result[1]);
            }
            return (int) get("mFinalStatus") == STATUS_SUCCESS;
        }

        private Object get(@NonNull String name) throws Exception {
            Field field = PackageInstallerCompat.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(mRealInstaller);
        }

        private void set(@NonNull String name, @Nullable Object value) throws Exception {
            Field field = PackageInstallerCompat.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(mRealInstaller, value);
        }
    }

    @Implements(MiuiUtils.class)
    public static class AffectedRom {
        static volatile boolean sMiui;
        static volatile boolean sHyperOs;

        @Implementation
        protected static boolean isActualMiuiVersionAtLeast(@NonNull String targetVersion,
                                                            @NonNull String targetVersionBeta) {
            if (sHyperOs) {
                // What reading a HyperOS build's version as a MIUI one does (MiuiUtils.getMiuiVersionInfo)
                throw new IllegalStateException("Stable version must begin with `V`");
            }
            return sMiui;
        }

        @Implementation
        protected static boolean isHyperOs() {
            return sHyperOs;
        }
    }
}
