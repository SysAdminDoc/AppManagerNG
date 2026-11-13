// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sync.lan;

import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_A;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_B;
import static io.github.muntashirakon.AppManager.sync.lan.SyncFixtures.SIGNER_C;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class ReceivedApkStoreTest {
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private File mDir;
    private ReceivedApkStore mStore;
    private LanSyncSettings mSettings;

    @Before
    public void setUp() {
        mDir = new File(mTemp.getRoot(), LanSyncDownloader.DIR_NAME);
        mStore = new ReceivedApkStore(mDir);
        mSettings = new LanSyncSettings(ApplicationProvider.getApplicationContext());
    }

    @After
    public void tearDown() {
        ApplicationProvider.getApplicationContext().getSharedPreferences(LanSyncSettings.PREFS_NAME,
                Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void theKeyNamesExactlyOneListing() {
        SyncApp app = app("com.example.app", 5, Arrays.asList(SIGNER_A, SIGNER_B), file("base.apk", 100));
        String key = ReceivedApkStore.keyOf(app);
        assertEquals(32, key.length());
        assertEquals("Signer order doesn't matter", key, ReceivedApkStore.keyOf(
                app("com.example.app", 5, Arrays.asList(SIGNER_B, SIGNER_A), file("base.apk", 100))));
        assertNotEquals(key, ReceivedApkStore.keyOf(
                app("com.example.app", 6, Arrays.asList(SIGNER_A, SIGNER_B), file("base.apk", 100))));
        assertNotEquals(key, ReceivedApkStore.keyOf(
                app("com.example.app", 5, Arrays.asList(SIGNER_A, SIGNER_B), file("base.apk", 101))));
        assertNotEquals(key, ReceivedApkStore.keyOf(
                app("com.example.app", 5, Arrays.asList(SIGNER_A, SIGNER_B), file("other.apk", 100))));
        assertNotEquals(key, ReceivedApkStore.keyOf(
                app("com.example.app", 5, Arrays.asList(SIGNER_A, SIGNER_C), file("base.apk", 100))));
        assertNotEquals(key, ReceivedApkStore.keyOf(
                app("com.example.other", 5, Arrays.asList(SIGNER_A, SIGNER_B), file("base.apk", 100))));
    }

    @Test
    public void anEntryReadsBackAndStaysFinishedWhenOpenedAgain() throws IOException {
        SyncApp app = app("com.example.app", 5, Collections.singletonList(SIGNER_A), file("base.apk", 100),
                file("split_config.en.apk", 20));
        ReceivedApkStore.Entry entry = mStore.open(app, 1000);
        assertFalse(entry.complete);
        mStore.markComplete(entry, 2000);

        ReceivedApkStore.Entry again = mStore.open(app, 3000);

        assertTrue(again.complete);
        ReceivedApkStore.Entry found = mStore.find(entry.key);
        assertNotNull(found);
        assertTrue(found.complete);
        assertEquals(3000, found.updatedAt);
        assertEquals(app.packageName, found.app.packageName);
        assertEquals(app.label, found.app.label);
        assertEquals(app.versionName, found.app.versionName);
        assertEquals(app.versionCode, found.app.versionCode);
        assertEquals(app.signers, found.app.signers);
        assertEquals(2, found.app.files.size());
        assertEquals("split_config.en.apk", found.app.files.get(1).name);
        assertEquals(20, found.app.files.get(1).size);
        assertTrue(found.isBundle());
    }

    @Test
    public void bytesCountFinishedFilesAndParts() throws IOException {
        SyncApp app = app("com.example.app", 5, Collections.singletonList(SIGNER_A), file("base.apk", 100),
                file("split_config.en.apk", 20));
        ReceivedApkStore.Entry entry = mStore.open(app, 1000);
        Files.write(entry.file("base.apk").toPath(), new byte[100]);
        Files.write(entry.part("split_config.en.apk").toPath(), new byte[7]);

        assertEquals(107, entry.bytesHere());
        assertFalse(entry.hasAllFiles());
        Files.write(entry.file("split_config.en.apk").toPath(), new byte[20]);
        assertTrue(entry.hasAllFiles());
    }

    @Test
    public void theListIsNewestFirstAndDropsWhatItCannotTrust() throws IOException {
        ReceivedApkStore.Entry older = mStore.open(app("com.example.old", 1), 1000);
        ReceivedApkStore.Entry newer = mStore.open(app("com.example.new", 1), 2000);
        File root = new File(mDir, ReceivedApkStore.DIR_NAME);
        // A folder that isn't an entry at all
        File junk = new File(root, "junk");
        assertTrue(junk.mkdirs());
        // A meta file moved under another listing's name
        File moved = new File(root, ReceivedApkStore.keyOf(app("com.example.moved", 1)));
        assertTrue(moved.mkdirs());
        Files.copy(new File(older.dir, "meta.json").toPath(), new File(moved, "meta.json").toPath());
        // A meta file naming a path outside its folder
        SyncApp escaping = app("com.example.escape", 1);
        ReceivedApkStore.Entry tampered = mStore.open(escaping, 1500);
        File meta = new File(tampered.dir, "meta.json");
        String json = new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8);
        Files.write(meta.toPath(), json.replace("\"base.apk\"", "\"..\\/..\\/x.apk\"")
                .getBytes(StandardCharsets.UTF_8));

        List<ReceivedApkStore.Entry> entries = mStore.list();

        assertEquals(2, entries.size());
        assertEquals(newer.key, entries.get(0).key);
        assertEquals(older.key, entries.get(1).key);
        assertFalse(junk.exists());
        assertFalse(moved.exists());
        assertFalse(tampered.dir.exists());
        assertNull(mStore.find("../" + older.key));
    }

    @Test
    public void purgeRemovesOnlyEntriesPastTheirTime() throws IOException {
        mStore.open(app("com.example.old", 1), 1000);
        ReceivedApkStore.Entry fresh = mStore.open(app("com.example.fresh", 1), 9000);

        assertEquals(1, mStore.purge(5000, 10_000));

        List<ReceivedApkStore.Entry> entries = mStore.list();
        assertEquals(1, entries.size());
        assertEquals(fresh.key, entries.get(0).key);
    }

    @Test
    public void removeInstalledKeepsWhatCouldStillBeInstalled() throws IOException {
        mStore.open(app("com.example.same", 5), 1000);
        ReceivedApkStore.Entry newer = mStore.open(app("com.example.newer", 7), 1000);
        ReceivedApkStore.Entry otherKey = mStore.open(app("com.example.key", 5), 1000);
        ReceivedApkStore.Entry missing = mStore.open(app("com.example.missing", 5), 1000);
        Map<String, SyncApp> installed = new HashMap<>();
        installed.put("com.example.same", app("com.example.same", 5));
        installed.put("com.example.newer", app("com.example.newer", 5));
        installed.put("com.example.key", SyncFixtures.app("com.example.key", 5, SIGNER_B));

        assertEquals(1, mStore.removeInstalled(installed));

        List<ReceivedApkStore.Entry> entries = mStore.list();
        assertEquals(3, entries.size());
        assertTrue(contains(entries, newer.key));
        assertTrue(contains(entries, otherKey.key));
        assertTrue(contains(entries, missing.key));
    }

    @Test
    public void cleanUpFollowsTheSettings() throws IOException {
        long now = 100 * HOUR;
        ReceivedApkStore.Entry threeHoursOld = mStore.open(app("com.example.old", 1), now - 3 * HOUR);
        ReceivedApkStore.Entry installed = mStore.open(app("com.example.installed", 1), now);
        ReceivedApkStore.Entry waiting = mStore.open(app("com.example.waiting", 1), now);
        Map<String, SyncApp> local = Collections.singletonMap("com.example.installed",
                app("com.example.installed", 1));
        File strayBundle = new File(mDir, "sync-1.apks");
        Files.write(strayBundle.toPath(), new byte[1]);
        assertTrue(strayBundle.setLastModified(now - TimeUnit.DAYS.toMillis(2)));

        // The defaults: a day, and nothing kept after installing
        LanSyncViewModel.cleanUp(mDir, mSettings, () -> local, now);
        List<ReceivedApkStore.Entry> entries = mStore.list();
        assertEquals(2, entries.size());
        assertTrue(contains(entries, threeHoursOld.key));
        assertTrue(contains(entries, waiting.key));
        assertFalse(strayBundle.exists());

        mStore.open(installed.app, now);
        mSettings.setKeepAfterInstall(true);
        mSettings.setRetention(LanSyncSettings.RETENTION_2_HOURS);
        LanSyncViewModel.cleanUp(mDir, mSettings, () -> local, now);
        entries = mStore.list();
        assertEquals(2, entries.size());
        assertTrue("Kept on request", contains(entries, installed.key));
        assertTrue(contains(entries, waiting.key));
    }

    @Test
    public void settingsDefaultToADayAndClampBadValues() {
        assertEquals(LanSyncSettings.RETENTION_24_HOURS, mSettings.getRetention());
        assertEquals(24 * HOUR, mSettings.getRetentionMillis());
        assertFalse(mSettings.isKeepAfterInstall());
        mSettings.setRetention(LanSyncSettings.RETENTION_7_DAYS);
        assertEquals(TimeUnit.DAYS.toMillis(7), new LanSyncSettings(ApplicationProvider.getApplicationContext())
                .getRetentionMillis());
        mSettings.setRetention(42);
        assertEquals(LanSyncSettings.RETENTION_24_HOURS, mSettings.getRetention());
    }

    // Helpers

    private static boolean contains(@NonNull List<ReceivedApkStore.Entry> entries, @NonNull String key) {
        for (ReceivedApkStore.Entry entry : entries) {
            if (entry.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static SyncApp.ApkFileInfo file(@NonNull String name, long size) {
        return new SyncApp.ApkFileInfo(name, size);
    }

    @NonNull
    private static SyncApp app(@NonNull String packageName, long versionCode) {
        return SyncFixtures.app(packageName, versionCode, SIGNER_A);
    }

    @NonNull
    private static SyncApp app(@NonNull String packageName, long versionCode, @NonNull List<String> signers,
                               @NonNull SyncApp.ApkFileInfo... files) {
        return SyncFixtures.app(packageName, versionCode, signers, Collections.<String>emptyList(),
                Arrays.asList(files));
    }
}
