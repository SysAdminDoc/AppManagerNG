// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.pm.IPackageInstaller;
import android.content.pm.PackageInstaller;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.test.shadows.ShadowPackageManagerCompat;

/**
 * Restoring a backup asks for the installer it recorded, often an app store. Android only lets a
 * caller create a session in a package it owns, so naming the store there threw a
 * SecurityException without root (upstream App Manager #1286). The session belongs to this app,
 * and the recorded installer goes in the session's params, which only a privileged install may set.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {PackageInstallerSessionInstallerTest.RealInstall.class,
        PackageInstallerSessionInstallerTest.RecordingInstallerService.class})
public class PackageInstallerSessionInstallerTest {
    private static final String BACKUP_INSTALLER = "com.google.android.packageinstaller";

    @After
    public void forgetTheSession() {
        RecordingInstallerService.sSessionOwner.set(null);
        RecordingInstallerService.sParams.set(null);
    }

    @Test
    public void anUnprivilegedRestoreOpensTheSessionInItsOwnName() throws Throwable {
        restoreWithTheBackupsInstaller();

        assertEquals(BuildConfig.APPLICATION_ID, RecordingInstallerService.sSessionOwner.get());
        assertNull(requestedInstaller());
    }

    @Test
    public void aPrivilegedRestoreAsksForTheBackupsInstallerInTheParams() throws Throwable {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.INSTALL_PACKAGES);

        restoreWithTheBackupsInstaller();

        assertEquals(BuildConfig.APPLICATION_ID, RecordingInstallerService.sSessionOwner.get());
        assertEquals(BACKUP_INSTALLER, requestedInstaller());
    }

    private static void restoreWithTheBackupsInstaller() throws Throwable {
        InstallerOptions options = InstallerOptions.getDefault();
        options.setUserId(UserHandleHidden.myUserId());
        options.setInstallerName(BACKUP_INSTALLER);
        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
        AtomicBoolean installed = new AtomicBoolean(true);
        AtomicReference<Throwable> crash = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                installed.set(installer.install(new Path[0], "com.example.restored", options));
            } catch (Throwable th) {
                crash.set(th);
            }
        });
        worker.start();
        worker.join(60_000);
        if (crash.get() != null) {
            throw crash.get();
        }
        assertFalse("install() didn't return", worker.isAlive());
        // The fake refuses the session once it has seen it, so the install stops there
        assertFalse(installed.get());
    }

    private static String requestedInstaller() throws Exception {
        Field field = PackageInstaller.SessionParams.class.getDeclaredField("installerPackageName");
        field.setAccessible(true);
        return (String) field.get(RecordingInstallerService.sParams.get());
    }

    /** Takes the place of the suite's faked install, so the real one runs. */
    @Implements(PackageInstallerCompat.class)
    public static class RealInstall {
    }

    /** Android's installer service, reduced to remembering the session it was asked for. */
    @Implements(PackageManagerCompat.class)
    public static class RecordingInstallerService extends ShadowPackageManagerCompat {
        static final AtomicReference<String> sSessionOwner = new AtomicReference<>();
        static final AtomicReference<PackageInstaller.SessionParams> sParams = new AtomicReference<>();

        @Implementation
        protected static IPackageInstaller getPackageInstaller() {
            return (IPackageInstaller) Proxy.newProxyInstance(IPackageInstaller.class.getClassLoader(),
                    new Class<?>[]{IPackageInstaller.class}, (proxy, method, args) -> {
                        if ("createSession".equals(method.getName())) {
                            sParams.set((PackageInstaller.SessionParams) args[0]);
                            sSessionOwner.set((String) args[1]);
                            throw new RemoteException("Recorded the session");
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(@NonNull Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
