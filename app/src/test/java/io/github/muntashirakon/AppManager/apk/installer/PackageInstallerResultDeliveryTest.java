// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INVALID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.os.UserHandleHidden;

import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.test.shadows.ShadowCpuUtils;

/**
 * An install that fails before Android ever sees a session must still tell its listener why,
 * and before {@code install()} returns: the caller unregisters its receivers on the way out, so
 * a result sent as a broadcast after that point reaches nobody.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowCpuUtils.class)
public class PackageInstallerResultDeliveryTest {
    private static final String BASE_APK = "oandbackups/ademar.textlauncher/base.apk";

    @Test
    public void anEarlyFailureReachesTheListenerWithItsReasonBeforeInstallReturns() throws Throwable {
        ApkFile apkFile = ApkSource.getApkSource(Uri.fromFile(resource(BASE_APK)),
                "application/vnd.android.package-archive").resolve();
        PackageInstallerCompat installer = PackageInstallerCompat.getNewInstance();
        AtomicInteger heardStatus = new AtomicInteger(Integer.MIN_VALUE);
        AtomicReference<String> heardMessage = new AtomicReference<>();
        installer.setOnInstallListener(new PackageInstallerCompat.OnInstallListener() {
            @Override
            public void onStartInstall(int sessionId, String packageName) {
            }

            @Override
            public void onFinishedInstall(int sessionId, String packageName, int result,
                                          @Nullable String blockingPackage, @Nullable String statusMessage) {
                heardStatus.set(result);
                heardMessage.set(statusMessage);
            }
        });
        InstallerOptions options = InstallerOptions.getDefault();
        // No user to install for: the installer gives up before it opens a session
        options.setUserId(UserHandleHidden.USER_NULL);

        AtomicBoolean installed = new AtomicBoolean(true);
        AtomicInteger statusAtReturn = new AtomicInteger(Integer.MIN_VALUE);
        AtomicReference<String> messageAtReturn = new AtomicReference<>();
        AtomicReference<Throwable> crash = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                installed.set(installer.install(apkFile,
                        Collections.singletonList(apkFile.getBaseEntry().id), options, null));
                statusAtReturn.set(heardStatus.get());
                messageAtReturn.set(heardMessage.get());
            } catch (Throwable th) {
                crash.set(th);
            }
        });
        worker.start();
        worker.join(60_000);
        if (crash.get() != null) {
            throw crash.get();
        }

        assertFalse(worker.isAlive());
        assertFalse(installed.get());
        assertEquals(STATUS_FAILURE_INVALID, statusAtReturn.get());
        assertNotNull(messageAtReturn.get());
        assertTrue(messageAtReturn.get(), messageAtReturn.get().startsWith("STATUS_FAILURE_INVALID: "));
    }

    private static File resource(String path) throws Exception {
        URL url = PackageInstallerResultDeliveryTest.class.getClassLoader().getResource(path);
        assertNotNull("Missing test resource " + path, url);
        return new File(url.toURI());
    }
}
