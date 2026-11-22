// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity.InstalledAppOffer;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity.offerFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.test.TestApks;

/**
 * The installer offers to uninstall an app and install the new copy when Android won't take it over
 * the old one. For this app that ends the app halfway, leaving neither copy (#45), so it explains
 * what to do instead and never runs the uninstall.
 */
@RunWith(RobolectricTestRunner.class)
public class SelfReplaceGuardTest {
    private static KeyPair sKey;

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    @BeforeClass
    public static void makeKey() throws Exception {
        sKey = TestApks.newKey("Self replace");
    }

    @Test
    public void onlyThisAppsOwnPackageIsRefused() {
        Context context = ContextUtils.getContext();

        assertTrue(SelfReplaceGuard.refuses(context, context.getPackageName()));
        assertFalse(SelfReplaceGuard.refuses(context, context.getPackageName() + ".other"));
        assertFalse(SelfReplaceGuard.refuses(context, "io.github.muntashirakon.AppManager"));
        assertFalse(SelfReplaceGuard.refuses(context, null));
    }

    @Test
    public void theInstallerExplainsInsteadOfUninstallingItself() {
        // A downgrade Android won't take in place
        assertEquals(InstalledAppOffer.UNINSTALL_TO_DOWNGRADE, offerFor(false, 34, 33, false, false, false));
        assertEquals(InstalledAppOffer.EXPLAIN_SELF_DOWNGRADE, offerFor(true, 34, 33, false, false, false));
        // Signed by another key
        assertEquals(InstalledAppOffer.UNINSTALL_FOR_OTHER_SIGNER, offerFor(false, 34, 35, false, true, false));
        assertEquals(InstalledAppOffer.EXPLAIN_SELF_OTHER_SIGNER, offerFor(true, 34, 35, false, true, false));
        assertEquals(InstalledAppOffer.EXPLAIN_SELF_OTHER_SIGNER, offerFor(true, 34, 34, true, true, false));
        assertEquals(InstalledAppOffer.EXPLAIN_SELF_OTHER_SIGNER, offerFor(true, 34, 33, false, true, false));
        assertEquals(InstalledAppOffer.INSTALL_ONLY, offerFor(false, 34, 35, false, true, true));
        // What Android takes in place still goes ahead, this app's own updates included
        assertEquals(InstalledAppOffer.INSTALL, offerFor(true, 34, 35, false, false, false));
        assertEquals(InstalledAppOffer.INSTALL, offerFor(true, 34, 34, false, false, false));
        assertEquals(InstalledAppOffer.INSTALL, offerFor(true, 34, 33, true, false, false));
        assertEquals(InstalledAppOffer.INSTALL, offerFor(false, 34, 33, true, false, false));
    }

    @Test
    public void theUninstallStepNeverRunsForThisApp() throws Throwable {
        Context context = ContextUtils.getContext();
        ApkFile self = resolve(TestApks.signed(new File(mTemp.newFolder(), "base.apk"), context.getPackageName(),
                21, 30, sKey, false, true, true, null));
        AtomicBoolean uninstalled = new AtomicBoolean();
        try {
            String refusal = onWorker(() -> PackageInstallerViewModel.uninstallUnlessSignerPinRefuses(context, self,
                    Collections.singletonList(self.getBaseEntry().id), false, () -> uninstalled.set(true)));

            assertFalse("This app stays installed", uninstalled.get());
            assertEquals(SelfReplaceGuard.explanation(context), refusal);
        } finally {
            self.close();
        }

        ApkFile other = resolve(TestApks.signed(new File(mTemp.newFolder(), "base.apk"), "com.example.other",
                21, 30, sKey, false, true, true, null));
        try {
            String refusal = onWorker(() -> PackageInstallerViewModel.uninstallUnlessSignerPinRefuses(context, other,
                    Collections.singletonList(other.getBaseEntry().id), false, () -> uninstalled.set(true)));

            assertNull(refusal);
            assertTrue("Any other app is uninstalled as before", uninstalled.get());
        } finally {
            other.close();
        }
    }

    @NonNull
    private static ApkFile resolve(@NonNull File apk) throws Throwable {
        return ApkSource.getApkSource(Uri.fromFile(apk), "application/vnd.android.package-archive").resolve();
    }

    @Nullable
    private static <T> T onWorker(@NonNull Callable<T> task) throws Throwable {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> crash = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                result.set(task.call());
            } catch (Throwable th) {
                crash.set(th);
            }
        });
        worker.start();
        worker.join(60_000);
        if (crash.get() != null) {
            throw crash.get();
        }
        return result.get();
    }
}
