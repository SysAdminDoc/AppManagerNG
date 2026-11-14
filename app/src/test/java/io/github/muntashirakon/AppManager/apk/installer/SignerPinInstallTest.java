// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SIGNER_PIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.signing.ShadowFixtureSigner;
import io.github.muntashirakon.AppManager.apk.signing.SignerPin;
import io.github.muntashirakon.AppManager.apk.signing.SignerPinCheck;
import io.github.muntashirakon.AppManager.apk.signing.SignerPinStore;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.FailingClosePaths;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.test.shadows.ShadowCpuUtils;

/**
 * The installer itself consults the signer pins: a pinned app's APK signed by another key, or any
 * install while the pin list is damaged, ends before Android is asked for a session. Getting as far
 * as a session would throw here, since Robolectric has no package service to open one with. A
 * reinstall or restore that has to uninstall the app first asks the same question before the
 * uninstall, so a refusal leaves the app and its data in place. A restore whose APKs can't be read
 * for their package could be any pinned app, so it's refused while one is pinned.
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowCpuUtils.class)
public class SignerPinInstallTest {
    private static final String BASE_APK = "oandbackups/ademar.textlauncher/base.apk";
    private static final String PACKAGE = "ademar.textlauncher";
    private static final String APK_SIGNER = "4ca3d35b3ae3e8c7a56d96f715cd1f5165b8103c8d238dd19d0d26ed3eef0c3d";
    private static final String OTHER_APK = "oandbackups/dnsfilter.android/base.apk";
    private static final String OTHER_SIGNER = "d7d98dc22da624e1bb36815b3c426d3555c23c53acd3e6585ab383de874c9f35";

    private Context mContext;
    private SignerPinStore mStore;

    @Before
    public void setUp() {
        // The installer's own context: it's cached for the whole run, and each test's app gets its
        // own files folder, so the pins have to go where the installer looks
        mContext = ContextUtils.getContext();
        mStore = new SignerPinStore(mContext);
        mStore.clear();
        // Robolectric reports external storage as unmounted, which would put FileCache's shared folder
        // in the private cache. Mount it the way a device has it, so the two caches differ.
        for (File externalCache : mContext.getExternalCacheDirs()) {
            if (externalCache != null) {
                ShadowEnvironment.setExternalStorageState(externalCache, Environment.MEDIA_MOUNTED);
            }
        }
    }

    @After
    public void tearDown() {
        mStore.clear();
    }

    @Test
    public void anApkSignedByAnotherKeyThanThePinIsRefusedBeforeASessionOpens() throws Throwable {
        String pinned = repeat('b');
        mStore.put(PACKAGE, new SignerPin(Collections.singletonList(pinned), 1));

        Outcome outcome = install();

        assertFalse(outcome.installed);
        assertEquals(STATUS_FAILURE_SIGNER_PIN, outcome.status);
        assertTrue(outcome.message, outcome.message.startsWith("STATUS_FAILURE_SIGNER_PIN: "));
        assertTrue(outcome.message, outcome.message.contains(SignerPin.format(pinned)));
        assertTrue(outcome.message, outcome.message.contains(SignerPin.format(APK_SIGNER)));
    }

    @Test
    public void aDamagedPinListRefusesAnInstallOfAnyApp() throws Throwable {
        Files.write(new File(mContext.getFilesDir(), "signer_pins.json").toPath(),
                "{not json".getBytes(StandardCharsets.UTF_8));

        Outcome outcome = install();

        assertFalse(outcome.installed);
        assertEquals(STATUS_FAILURE_SIGNER_PIN, outcome.status);
        assertTrue(outcome.message, outcome.message.contains("Settings > Privacy > Signer pins"));
    }

    @Test
    public void aReinstallThatThePinRefusesNeverUninstallsTheApp() throws Throwable {
        String pinned = repeat('b');
        mStore.put(PACKAGE, new SignerPin(Collections.singletonList(pinned), 1));
        ApkFile apkFile = resolve();
        AtomicBoolean uninstalled = new AtomicBoolean();

        String refusal = onWorker(() -> PackageInstallerViewModel.uninstallUnlessSignerPinRefuses(mContext, apkFile,
                Collections.singletonList(apkFile.getBaseEntry().id), false, () -> uninstalled.set(true)));

        assertFalse(uninstalled.get());
        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains(SignerPin.format(pinned)));
        assertTrue(refusal, refusal.contains(SignerPin.format(APK_SIGNER)));
    }

    @Test
    public void aReinstallThePinAllowsUninstallsTheAppFirst() throws Throwable {
        mStore.put(PACKAGE, new SignerPin(Collections.singletonList(APK_SIGNER), 1));
        ApkFile apkFile = resolve();
        AtomicBoolean uninstalled = new AtomicBoolean();

        String refusal = onWorker(() -> PackageInstallerViewModel.uninstallUnlessSignerPinRefuses(mContext, apkFile,
                Collections.singletonList(apkFile.getBaseEntry().id), false, () -> uninstalled.set(true)));

        assertNull(refusal);
        assertTrue(uninstalled.get());
    }

    @Test
    public void aRestoreIsCheckedAgainstThePinOfThePackageItsBaseApkDeclares() throws Throwable {
        Path[] apks = new Path[]{Paths.get(resource(BASE_APK))};
        assertNull(onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, "named.in.backup")));
        mStore.put(PACKAGE, new SignerPin(Collections.singletonList(repeat('b')), 1));

        String refusal = onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, "named.in.backup"));

        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains(PACKAGE + " is pinned to"));
    }

    @Test
    public void filesTheInstallerWillSignAreCheckedAsSignedCopies() throws Throwable {
        Path[] apks = new Path[]{Paths.get(resource(BASE_APK))};
        mStore.put(PACKAGE, new SignerPin(Collections.singletonList(APK_SIGNER), 1));
        // As they are, the APK's own key matches its pin
        assertNull(onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, PACKAGE, false)));
        File cacheFiles = new File(FileUtils.getCachePath(), "files");
        File privateCopies = new File(mContext.getCacheDir(), SignerPinCheck.COPY_FOLDER);
        int before = countFiles(cacheFiles);
        int beforePrivate = countFiles(privateCopies);

        // Signed first it's the copy that has to match, and with no signing key here there's no copy
        String refusal = onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, PACKAGE, true));

        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains("Could not read base.apk"));
        assertEquals(before, countFiles(cacheFiles));
        assertEquals(beforePrivate, countFiles(privateCopies));
    }

    @Test
    @Config(shadows = {ShadowCpuUtils.class, ShadowCopyOnlyArchiveParser.class})
    public void aRestoreApkThatHasToBeCopiedToBeParsedIsParsedFromAPrivateCopy() throws Throwable {
        // The backup claims an unpinned app, but its APK declares the pinned one, signed by another key
        ShadowCopyOnlyArchiveParser.expect(Files.readAllBytes(resource(BASE_APK).toPath()), PACKAGE);
        Path[] apks = new Path[]{Paths.get(resource(BASE_APK))};
        mStore.put(PACKAGE, new SignerPin(Collections.singletonList(OTHER_SIGNER), 1));
        // The cached installer context keeps the package manager an earlier test made, with that
        // test's shadows. The pins stay on it, and the parse goes through one made under this test's.
        Context context = new ContextWrapper(mContext) {
            @Override
            public PackageManager getPackageManager() {
                return RuntimeEnvironment.getApplication().getPackageManager();
            }
        };
        File privateCopies = new File(mContext.getCacheDir(), SignerPinCheck.COPY_FOLDER);
        int beforePrivate = countFiles(privateCopies);

        String refusal = onWorker(() -> PackageInstallerCompat.signerPinRefusal(context, apks, "named.in.backup"));

        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains("base.apk is signed by"));
        assertTrue(ShadowCopyOnlyArchiveParser.sParsed.toString(), ShadowCopyOnlyArchiveParser.sParsed.stream()
                .anyMatch(parsed -> privateCopies.equals(new File(parsed).getParentFile())));
        assertEquals(beforePrivate, countFiles(privateCopies));
    }

    @Test
    @Config(shadows = {ShadowCpuUtils.class, ShadowFixtureSigner.class})
    public void aSignedInstallIsCheckedAgainstTheKeyItWillBeSignedWith() throws Throwable {
        Path[] apks = new Path[]{Paths.get(resource(BASE_APK))};
        // Signing hands back an APK another key signed, the way this app's own key would
        ShadowFixtureSigner.signWith(resource(OTHER_APK));
        try {
            mStore.put(PACKAGE, new SignerPin(Collections.singletonList(APK_SIGNER), 1));
            assertNull(onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, PACKAGE, false)));
            String refusal = onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, PACKAGE, true));
            assertNotNull(refusal);
            assertTrue(refusal, refusal.contains("base.apk is signed by"));

            mStore.clear();
            mStore.put(PACKAGE, new SignerPin(Collections.singletonList(OTHER_SIGNER), 1));
            assertNull(onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, PACKAGE, true)));
            assertNotNull(onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, PACKAGE, false)));
        } finally {
            ShadowFixtureSigner.signWith(null);
        }
    }

    @Test
    public void aCacheFolderThatCantBeMadeRefusesTheSignedCheckInsteadOfCrashing() throws Throwable {
        Path[] apks = new Path[]{Paths.get(resource(BASE_APK))};
        mStore.put(PACKAGE, new SignerPin(Collections.singletonList(APK_SIGNER), 1));
        // A plain file where the shared cache folder goes, so FileCache can't make its folder. The pin
        // check's own copy goes in the private cache, which has to stay usable for this to reach it.
        File cachePath = FileUtils.getCachePath();
        assertNotEquals(mContext.getCacheDir().getCanonicalFile(), cachePath.getCanonicalFile());
        File aside = new File(cachePath.getParentFile(), cachePath.getName() + ".aside");
        assertTrue("could not move " + cachePath + " aside", cachePath.renameTo(aside));
        try {
            assertTrue(cachePath.createNewFile());

            String refusal = onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, PACKAGE, true));

            assertNotNull(refusal);
            assertTrue(refusal, refusal.contains("Could not read base.apk"));
        } finally {
            assertTrue(cachePath.delete());
            assertTrue(aside.renameTo(cachePath));
        }
    }

    @Test
    public void aRestoreWhosePackageCantBeReadIsRefusedWhileAnotherAppIsPinned() throws Throwable {
        File unparsable = File.createTempFile("restore", ".apk", mContext.getCacheDir());
        try {
            Files.write(unparsable.toPath(), "not an apk".getBytes(StandardCharsets.UTF_8));
            Path[] apks = new Path[]{Paths.get(unparsable)};
            assertNull(onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, "named.in.backup")));
            mStore.put(PACKAGE, new SignerPin(Collections.singletonList(APK_SIGNER), 1));
            File privateCopies = new File(mContext.getCacheDir(), SignerPinCheck.COPY_FOLDER);
            int beforePrivate = countFiles(privateCopies);

            String refusal = onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, "named.in.backup"));

            assertNotNull(refusal);
            assertTrue(refusal, refusal.contains("Could not read which app"));
            // The copy made to parse it is gone even though the parse found nothing
            assertEquals(beforePrivate, countFiles(privateCopies));
        } finally {
            assertTrue(unparsable.delete());
        }
    }

    @Test
    public void aRestoreCopyWhoseSourceFailsToCloseIsDeleted() throws Throwable {
        File unparsable = File.createTempFile("restore", ".apk", mContext.getCacheDir());
        try {
            Files.write(unparsable.toPath(), "not an apk".getBytes(StandardCharsets.UTF_8));
            // It can't be parsed in place, so it's copied, and closing it fails once the copy is whole
            Path[] apks = new Path[]{FailingClosePaths.of(unparsable)};
            mStore.put(PACKAGE, new SignerPin(Collections.singletonList(APK_SIGNER), 1));
            File privateCopies = new File(mContext.getCacheDir(), SignerPinCheck.COPY_FOLDER);
            int beforePrivate = countFiles(privateCopies);

            String refusal = onWorker(() -> PackageInstallerCompat.signerPinRefusal(mContext, apks, "named.in.backup"));

            assertNotNull(refusal);
            assertTrue(refusal, refusal.contains("Could not read which app"));
            assertEquals(beforePrivate, countFiles(privateCopies));
        } finally {
            assertTrue(unparsable.delete());
        }
    }

    @NonNull
    private static ApkFile resolve() throws Throwable {
        return ApkSource.getApkSource(Uri.fromFile(resource(BASE_APK)),
                "application/vnd.android.package-archive").resolve();
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
        assertFalse(worker.isAlive());
        return result.get();
    }

    @NonNull
    private static Outcome install() throws Throwable {
        ApkFile apkFile = resolve();
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
        AtomicBoolean installed = new AtomicBoolean(true);
        AtomicReference<Throwable> crash = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                installed.set(installer.install(apkFile,
                        Collections.singletonList(apkFile.getBaseEntry().id), InstallerOptions.getDefault(), null));
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
        assertNotNull(heardMessage.get());
        return new Outcome(installed.get(), heardStatus.get(), heardMessage.get());
    }

    private static final class Outcome {
        final boolean installed;
        final int status;
        @NonNull
        final String message;

        Outcome(boolean installed, int status, @NonNull String message) {
            this.installed = installed;
            this.status = status;
            this.message = message;
        }
    }

    @NonNull
    private static File resource(String path) throws Exception {
        URL url = SignerPinInstallTest.class.getClassLoader().getResource(path);
        assertNotNull("Missing test resource " + path, url);
        return new File(url.toURI());
    }

    private static int countFiles(@NonNull File dir) {
        String[] names = dir.list();
        return names != null ? names.length : 0;
    }

    @NonNull
    private static String repeat(char c) {
        char[] chars = new char[64];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
