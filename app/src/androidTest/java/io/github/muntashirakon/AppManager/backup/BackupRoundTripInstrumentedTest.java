// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import io.github.muntashirakon.AppManager.backup.struct.BackupOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.DeleteOpOptions;
import io.github.muntashirakon.AppManager.backup.struct.RestoreOpOptions;
import io.github.muntashirakon.AppManager.crypto.AESCrypto;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreManager;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreTestAccess;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.io.Path;

/**
 * Backs up a real app with its data, uninstalls it, restores it and checks the data came back,
 * once without encryption and once with AES.
 *
 * <p>Needs a root shell: run it on a google_apis emulator after {@code adb root}, which makes the
 * instrumentation's shell uid 0. The test starts App Manager's own server from that shell, the
 * same server Root mode runs through su, so the backup engine works as it does on a rooted phone.
 * The fixture is the signal test app that {@code connectedFlossDebugAndroidTest} installs.
 */
@RunWith(AndroidJUnit4.class)
public class BackupRoundTripInstrumentedTest {
    private static final String FIXTURE = "io.github.sysadmindoc.appmanagerng.signaltestapp";
    private static final String STASHED_APK = "/data/local/tmp/amng-roundtrip-fixture.apk";
    private static final int FLAGS = BackupFlags.BACKUP_APK_FILES | BackupFlags.BACKUP_INT_DATA;
    private static final long SERVER_TIMEOUT_MILLIS = 20_000L;

    private Context mContext;
    private int mUserId;
    private String mDataDir;
    private File mScratch;
    private String mOldVolume;
    private File mVolume;
    private boolean mStashed;
    private boolean mAddedAesKey;
    private boolean mCreatedKeyStore;
    private boolean mServerStarted;
    private final List<String> mBackupNames = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        assumeTrue("Needs a root shell (adb root on a google_apis emulator)",
                shell("id").contains("uid=0("));
        // Each user gets a range of 100000 uids
        mUserId = Process.myUid() / 100_000;
        mDataDir = "/data/user/" + mUserId + "/" + FIXTURE;
        mScratch = new File(mContext.getCacheDir(), "backup-roundtrip");
        if (!mScratch.mkdirs() && !mScratch.isDirectory()) {
            fail("Could not make " + mScratch);
        }
        assertTrue("The signal test app must be installed", isFixtureInstalled());
        // A failed restore must not leave the fixture missing for the other instrumented tests
        String apk = mContext.getPackageManager().getApplicationInfo(FIXTURE, 0).sourceDir;
        assertEquals("", rootScript("stash", "cp " + apk + " " + STASHED_APK + " || echo failed\n"));
        mStashed = true;
        // Back up into this app's own folder, never the AppManager folder a person uses
        mOldVolume = Prefs.Storage.getVolumePath().toString();
        File external = mContext.getExternalFilesDir(null);
        assertTrue(external != null);
        mVolume = new File(external, "backup-roundtrip");
        Prefs.Storage.setVolumePath(Uri.fromFile(mVolume).toString());
        startServerAsRoot();
    }

    @After
    public void tearDown() throws Throwable {
        // Every step runs even when one before it fails, and only what setUp got to is undone
        Cleanup cleanup = new Cleanup();
        for (String name : mBackupNames) {
            cleanup.run(() -> deleteBackups(name));
        }
        if (mAddedAesKey && !mCreatedKeyStore) {
            cleanup.run(() -> KeyStoreManager.getInstance().removeItem(AESCrypto.AES_KEY_ALIAS));
        }
        if (mCreatedKeyStore) {
            cleanup.run(KeyStoreTestAccess::deleteKeyStore);
        }
        if (mStashed) {
            cleanup.run(() -> {
                if (!isFixtureInstalled()) {
                    assertEquals("", rootScript("reinstall",
                            "pm install -r " + STASHED_APK + " > /dev/null || echo failed\n"));
                }
            });
            cleanup.run(() -> assertEquals("", root("rm -f " + STASHED_APK)));
        }
        if (mVolume != null) {
            cleanup.run(() -> assertEquals("", root("rm -rf " + mVolume)));
        }
        if (mOldVolume != null) {
            cleanup.run(() -> Prefs.Storage.setVolumePath(mOldVolume));
        }
        if (mServerStarted) {
            cleanup.run(this::stopServer);
        }
        if (mScratch != null) {
            cleanup.run(() -> deleteRecursively(mScratch));
        }
        cleanup.rethrow();
    }

    private void deleteBackups(@NonNull String name) throws Throwable {
        List<String> dirs = new ArrayList<>();
        for (Backup backup : BackupUtils.retrieveBackupFromDb(mUserId, name, FIXTURE)) {
            dirs.add(backup.relativeDir);
        }
        if (!dirs.isEmpty()) {
            new BackupManager().deleteBackup(new DeleteOpOptions(FIXTURE, mUserId, dirs.toArray(new String[0])));
        }
    }

    @Test
    public void plainBackupBringsBackTheAppAndItsData() throws Throwable {
        Backup backup = roundTrip("amng_roundtrip_plain", CryptoUtils.MODE_NO_ENCRYPTION);
        for (String name : dataArchiveNames(backup)) {
            assertFalse(name, name.endsWith(AESCrypto.AES_EXT));
        }
    }

    @Test
    public void aesBackupBringsBackTheAppAndItsData() throws Throwable {
        useAesKey();
        Backup backup = roundTrip("amng_roundtrip_aes", CryptoUtils.MODE_AES);
        for (String name : dataArchiveNames(backup)) {
            assertTrue(name, name.endsWith(AESCrypto.AES_EXT));
        }
    }

    @NonNull
    private Backup roundTrip(@NonNull String backupName, @NonNull String cryptoMode) throws Throwable {
        String token = UUID.randomUUID().toString();
        String prefs = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
                + "    <string name=\"token\">" + token + "</string>\n</map>\n";
        writeFixtureData(token, prefs);
        assertEquals(token, root("cat " + mDataDir + "/files/roundtrip.txt"));

        mBackupNames.add(backupName);
        new BackupManager().backup(new BackupOpOptions(FIXTURE, mUserId, FLAGS, backupName, true,
                null, false, null, cryptoMode, 0, 0, null), null);
        List<Backup> backups = BackupUtils.retrieveBackupFromDb(mUserId, backupName, FIXTURE);
        assertEquals(1, backups.size());
        Backup backup = backups.get(0);
        assertEquals(cryptoMode, backup.crypto);
        assertFalse("The backup holds no data archive", dataArchiveNames(backup).isEmpty());

        assertEquals("", rootScript("uninstall", "pm uninstall " + FIXTURE + " > /dev/null || echo failed\n"));
        assertFalse(isFixtureInstalled());
        assertEquals("gone", rootScript("gone", "[ -e " + mDataDir + " ] || echo gone\n").trim());

        new BackupManager().restore(new RestoreOpOptions(FIXTURE, mUserId, backup.relativeDir, FLAGS), null);

        assertTrue("Restore did not install the app again", isFixtureInstalled());
        int uid = mContext.getPackageManager().getApplicationInfo(FIXTURE, 0).uid;
        String file = mDataDir + "/files/roundtrip.txt";
        String sharedPrefs = mDataDir + "/shared_prefs/roundtrip.xml";
        assertEquals(token, root("cat " + file));
        assertEquals(prefs, root("cat " + sharedPrefs));
        assertEquals(uid + ":" + uid + "\n" + uid + ":" + uid + "\n",
                root("stat -c %u:%g " + file + " " + sharedPrefs));
        String expectedLabel = expectedDataLabel(uid);
        for (String path : new String[]{file, sharedPrefs}) {
            assertEquals(path, expectedLabel, labelOf(path));
        }
        return backup;
    }

    /**
     * The label installd gives the app's data, from seapp_contexts: app_data_file, with
     * categories from the user (levelFrom=user, Android 6 to 8 and older targets) or from the app
     * and the user (levelFrom=all, apps targeting API 28 and up on Android 9 and later).
     */
    @NonNull
    private String expectedDataLabel(int uid) throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return "u:object_r:app_data_file:s0";
        }
        int targetSdk = mContext.getPackageManager().getApplicationInfo(FIXTURE, 0).targetSdkVersion;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || targetSdk < Build.VERSION_CODES.P) {
            return "u:object_r:app_data_file:s0:c" + (512 + (mUserId & 0xff)) + ",c" + (768 + (mUserId >> 8 & 0xff));
        }
        int appId = uid % 100_000 - Process.FIRST_APPLICATION_UID;
        return "u:object_r:app_data_file:s0:c" + (appId & 0xff) + ",c" + (256 + (appId >> 8 & 0xff))
                + ",c" + (512 + (mUserId & 0xff)) + ",c" + (768 + (mUserId >> 8 & 0xff));
    }

    private void writeFixtureData(@NonNull String token, @NonNull String prefs) throws IOException {
        File tokenFile = writeScratch("roundtrip.txt", token);
        File prefsFile = writeScratch("roundtrip.xml", prefs);
        String script = "D=" + mDataDir + "\n"
                + "mkdir -p $D/files $D/shared_prefs || echo failed\n"
                + "cp " + tokenFile + " $D/files/roundtrip.txt || echo failed\n"
                + "cp " + prefsFile + " $D/shared_prefs/roundtrip.xml || echo failed\n"
                + "U=$(stat -c %u $D)\n"
                + "chown -R $U:$U $D/files $D/shared_prefs || echo failed\n"
                + "restorecon -R $D || echo failed\n";
        assertEquals("", rootScript("fixture", script));
    }

    private void startServerAsRoot() throws Exception {
        ServerConfig.init(mContext);
        mServerStarted = true;
        // A server an earlier build left running would answer in place of this build's server
        killServer();
        // The launcher leaves the server running with the shell's output open, so the read would
        // never end unless the output goes somewhere else.
        String started = rootScript("server", ServerConfig.getServerRunnerCommand(0)
                + " > /dev/null 2>&1 < /dev/null\necho started:$?\n");
        assertEquals("started:0", started.trim());
        long deadline = SystemClock.elapsedRealtime() + SERVER_TIMEOUT_MILLIS;
        while (!LocalServer.alive(mContext)) {
            assertTrue("The server never started", SystemClock.elapsedRealtime() < deadline);
            SystemClock.sleep(250);
        }
        assertEquals(Ops.STATUS_SUCCESS, Ops.init(mContext, false, Ops.MODE_ADB_OVER_TCP));
        assertEquals(Ops.ROOT_UID, Users.getSelfOrRemoteUid());
        assertTrue(Ops.isWorkingUidRoot());
    }

    private void stopServer() throws Exception {
        LocalServer.closeIfConnected();
        Ops.init(mContext, true, Ops.MODE_NO_ROOT);
        killServer();
    }

    private void killServer() throws Exception {
        rootScript("kill", "killall " + Constants.SERVER_NAME + " 2> /dev/null\n");
        long deadline = SystemClock.elapsedRealtime() + SERVER_TIMEOUT_MILLIS;
        while (LocalServer.alive(mContext)) {
            assertTrue("The server would not stop", SystemClock.elapsedRealtime() < deadline);
            SystemClock.sleep(250);
        }
    }

    private void useAesKey() throws Exception {
        boolean hadPassword = KeyStoreManager.hasSavedKeyStorePassword();
        // A new password could not open a KeyStore made with another one
        assertTrue("This install has a KeyStore with no saved password", hadPassword || !KeyStoreManager.hasKeyStore());
        if (!hadPassword) {
            mCreatedKeyStore = true;
            KeyStoreTestAccess.savePassword(mContext);
        }
        KeyStoreManager.reloadKeyStore();
        KeyStoreManager keyStoreManager = KeyStoreManager.getInstance();
        if (!keyStoreManager.containsKey(AESCrypto.AES_KEY_ALIAS)) {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            keyStoreManager.addSecretKey(AESCrypto.AES_KEY_ALIAS, new SecretKeySpec(key, "AES"), false);
            mAddedAesKey = true;
        }
        assertTrue(CryptoUtils.isAvailable(CryptoUtils.MODE_AES));
    }

    @NonNull
    private static List<String> dataArchiveNames(@NonNull Backup backup) throws IOException {
        List<String> names = new ArrayList<>();
        BackupItems.BackupItem item = BackupItems.findBackupItem(backup.relativeDir);
        try {
            for (Path path : item.getBackupPath().listFiles()) {
                if (path.getName().startsWith(BackupManager.DATA_PREFIX)) {
                    names.add(path.getName());
                }
            }
        } finally {
            item.close();
        }
        return names;
    }

    private boolean isFixtureInstalled() {
        try {
            mContext.getPackageManager().getApplicationInfo(FIXTURE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @NonNull
    private File writeScratch(@NonNull String name, @NonNull String content) throws IOException {
        File file = new File(mScratch, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    /**
     * Runs a script in the root shell. The shell takes one command with no quoting or redirection,
     * so anything more goes through a script file in this app's cache.
     */
    @NonNull
    private String rootScript(@NonNull String name, @NonNull String script) throws IOException {
        File file = writeScratch(name + ".sh", script);
        return root("sh " + file);
    }

    @NonNull
    private static String root(@NonNull String command) throws IOException {
        return shell(command);
    }

    @NonNull
    private static String shell(@NonNull String command) throws IOException {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ParcelFileDescriptor output = automation.executeShellCommand(command);
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(output)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            return bytes.toString("UTF-8");
        }
    }

    /** The SELinux context in ls -Z output, which puts it in a different column on older releases. */
    @NonNull
    private static String labelOf(@NonNull String path) throws IOException {
        String listing = root("ls -Z " + path);
        for (String token : listing.trim().split("\\s+")) {
            if (token.startsWith("u:object_r:")) {
                return token;
            }
        }
        throw new AssertionError("No SELinux context in: " + listing);
    }

    private static void deleteRecursively(@NonNull File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (file.exists() && !file.delete()) {
            throw new AssertionError("Could not delete " + file);
        }
    }

    private interface Step {
        void run() throws Throwable;
    }

    /** Runs every cleanup step and reports the first failure, with the later ones attached. */
    private static final class Cleanup {
        private Throwable mFailure;

        void run(@NonNull Step step) {
            try {
                step.run();
            } catch (Throwable t) {
                if (mFailure == null) {
                    mFailure = t;
                } else {
                    mFailure.addSuppressed(t);
                }
            }
        }

        void rethrow() throws Throwable {
            if (mFailure != null) {
                throw mFailure;
            }
        }
    }
}
