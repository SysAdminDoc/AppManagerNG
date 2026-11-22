// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.backup.convert.ImportType;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupImportOptions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.test.CachedAppContext;
import io.github.muntashirakon.test.shadows.ShadowCpuUtils;

@RunWith(RobolectricTestRunner.class)
public class BackupDestinationGrantsTest {
    private static final int MODES = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private static final Uri FIRST = tree("first");
    private static final Uri SECOND = tree("second");
    private static final Uri IMPORTS = tree("imports");
    private static final int TYPE = ImportType.OAndBackup;
    private static final int OTHER_TYPE = ImportType.SwiftBackup;

    private Context mContext;
    private FakeGrantStore mStore;
    private final Set<String> mHoldingBackups = new HashSet<>();
    private boolean mBackupRunning;
    private String mPreviousVolume;

    @Before
    public void setUp() {
        // Settings and the file they're saved to both follow this test's context. A language test that ran earlier
        // can leave the settings on an older one, so a check of the file on disk would look in the wrong folder.
        CachedAppContext.forgetWithPrefs();
        mContext = ApplicationProvider.getApplicationContext();
        mContext.getSharedPreferences(BackupDestinationGrants.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        mContext.getSharedPreferences(BackupTagPolicyStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        mPreviousVolume = Prefs.Storage.getVolumePath().toString();
        Prefs.Storage.setVolumePath("file:///storage/emulated/0/AppManager");
        mStore = new FakeGrantStore();
        BackupDestinationGrants.setStoreForTesting(mStore);
        BackupDestinationGrants.setBackupProbeForTesting(uri -> mHoldingBackups.contains(uri.toString()));
        BackupDestinationGrants.setBackupRunningForTesting(() -> mBackupRunning);
        BackupDestinationGrants.startNewProcessForTesting();
    }

    @After
    public void tearDown() {
        BackupDestinationGrants.setStoreForTesting(null);
        BackupDestinationGrants.setBackupProbeForTesting(null);
        BackupDestinationGrants.setBackupRunningForTesting(null);
        BackupDestinationGrants.setClockForTesting(null);
        BackupDestinationGrants.startNewProcessForTesting();
        Prefs.Storage.setVolumePath(mPreviousVolume);
    }

    @Test
    public void anEmptyFolderIsLetGoOnceAnotherReplacesIt() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);

        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);
        BackupDestinationGrants.releaseIfUnused(mContext, previous);

        assertEquals(StorageUtils.getFixedTreeUri(FIRST), previous);
        assertEquals(StorageUtils.getFixedTreeUri(SECOND), Prefs.Storage.getVolumePath());
        assertEquals(Collections.singletonList(SECOND), mStore.uris());
        assertEquals(Collections.singleton(SECOND.toString()), owned().keySet());
    }

    @Test
    public void aFolderThatHoldsBackupsIsKept() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        mHoldingBackups.add(StorageUtils.getFixedTreeUri(FIRST).toString());

        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);
        BackupDestinationGrants.releaseIfUnused(mContext, previous);

        assertEquals(List.of(FIRST, SECOND), mStore.uris());
    }

    @Test
    public void aFolderABackupTagPolicyUsesIsKept() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        new BackupTagPolicyStore(mContext).add(new BackupTagPolicyStore.Policy("work",
                BackupFlags.BACKUP_APK_FILES, CryptoUtils.MODE_NO_ENCRYPTION, 1, 0,
                StorageUtils.getFixedTreeUri(FIRST)));

        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);
        BackupDestinationGrants.releaseIfUnused(mContext, previous);

        assertEquals(List.of(FIRST, SECOND), mStore.uris());
    }

    @Test
    public void accessTheFileManagerAlreadyHeldIsNeverLetGo() {
        mStore.grant(FIRST);

        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);
        BackupDestinationGrants.releaseIfUnused(mContext, previous);

        assertEquals(List.of(FIRST, SECOND), mStore.uris());
        assertFalse(owned().containsKey(FIRST.toString()));
    }

    @Test
    public void aFolderTheFileManagerTakesLaterIsLeftToIt() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        BackupDestinationGrants.disown(mContext, FIRST);

        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);
        BackupDestinationGrants.releaseIfUnused(mContext, previous);
        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);

        assertEquals(List.of(FIRST, SECOND), mStore.uris());
    }

    @Test
    public void aRevokedProviderKeepsTheLastBackupFolder() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        mStore.revoked.add(SECOND);

        assertThrows(SecurityException.class,
                () -> BackupDestinationGrants.selectDestination(mContext, SECOND, MODES));

        assertEquals(StorageUtils.getFixedTreeUri(FIRST), Prefs.Storage.getVolumePath());
        assertEquals(Collections.singletonList(FIRST), mStore.uris());
        assertEquals(Collections.singleton(FIRST.toString()), owned().keySet());
    }

    @Test
    public void accessAndroidDidntKeepKeepsTheLastBackupFolder() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        mStore.dropped.add(SECOND);

        assertThrows(SecurityException.class,
                () -> BackupDestinationGrants.selectDestination(mContext, SECOND, MODES));

        assertEquals(StorageUtils.getFixedTreeUri(FIRST), Prefs.Storage.getVolumePath());
        assertEquals(Collections.singleton(FIRST.toString()), owned().keySet());
    }

    @Test
    public void readOnlyAccessIsRefusedForABackupFolder() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        mStore.readOnly.add(SECOND);

        assertThrows(SecurityException.class,
                () -> BackupDestinationGrants.selectDestination(mContext, SECOND, MODES));

        assertEquals(StorageUtils.getFixedTreeUri(FIRST), Prefs.Storage.getVolumePath());
        assertEquals(Collections.singletonList(FIRST), mStore.uris());
        assertEquals(Collections.singleton(FIRST.toString()), owned().keySet());
    }

    @Test
    public void changingTheFolderOverAndOverHoldsOneGrant() {
        // Android drops the oldest grant past its limit, so the count has to stay flat
        mStore.limit = 3;
        for (int i = 0; i < 50; ++i) {
            Uri previous = BackupDestinationGrants.selectDestination(mContext, tree("folder" + i), MODES);
            BackupDestinationGrants.releaseIfUnused(mContext, previous);
        }

        assertEquals(Collections.singletonList(tree("folder49")), mStore.uris());
        assertEquals(1, owned().size());
        assertFalse(mStore.evicted);
    }

    @Test
    public void aFolderPickedJustBeforeTheProcessDiedIsLetGoOnTheNextStart() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        // Taken, but the app died before the folder was saved as the backup folder
        BackupDestinationGrants.acquire(mContext, SECOND, MODES, BackupDestinationGrants.PURPOSE_DESTINATION);

        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);

        assertEquals(Collections.singletonList(FIRST), mStore.uris());
        assertEquals(Collections.singleton(FIRST.toString()), owned().keySet());
    }

    @Test
    public void aRecordLeftBeforeTheGrantWasTakenIsForgottenOnTheNextStart() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        mStore.dieOnTake = true;

        assertThrows(ProcessDeath.class,
                () -> BackupDestinationGrants.selectDestination(mContext, SECOND, MODES));
        assertTrue(owned().containsKey(SECOND.toString()));

        mStore.dieOnTake = false;
        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);

        assertEquals(Collections.singleton(FIRST.toString()), owned().keySet());
        assertEquals(StorageUtils.getFixedTreeUri(FIRST), Prefs.Storage.getVolumePath());
    }

    @Test
    public void aFolderPickedThisRunIsLeftForTheFlowThatPickedIt() {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);

        BackupDestinationGrants.pruneStale(mContext);

        assertEquals(Collections.singletonList(IMPORTS), mStore.uris());
    }

    @Test
    public void aRunningBackupHoldsTheOldFolderUntilTheNextStart() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);
        mBackupRunning = true;

        BackupDestinationGrants.releaseIfUnused(mContext, previous);
        assertEquals(List.of(FIRST, SECOND), mStore.uris());

        mBackupRunning = false;
        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);
        assertEquals(Collections.singletonList(SECOND), mStore.uris());
    }

    @Test
    public void anImportFolderIsLetGoOnceTheImportEnds() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);

        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);

        assertEquals(Collections.singletonList(FIRST), mStore.uris());
        assertFalse(owned().containsKey(IMPORTS.toString()));
    }

    @Test
    public void importingFromTheBackupFolderKeepsIt() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        BackupDestinationGrants.acquire(mContext, FIRST, MODES, BackupDestinationGrants.PURPOSE_IMPORT);

        BackupDestinationGrants.releaseAfterImport(mContext, FIRST, TYPE);

        assertEquals(Collections.singletonList(FIRST), mStore.uris());
        assertEquals(Integer.valueOf(BackupDestinationGrants.PURPOSE_DESTINATION), owned().get(FIRST.toString()));
    }

    @Test
    public void anImportFromAFolderTheFileManagerHoldsLeavesIt() {
        mStore.grant(IMPORTS);

        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);

        assertEquals(Collections.singletonList(IMPORTS), mStore.uris());
    }

    @Test
    public void aGrantAndroidAlreadyDroppedIsForgotten() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);
        mHoldingBackups.add(previous.toString());
        BackupDestinationGrants.releaseIfUnused(mContext, previous);
        // The folder's app was removed, which took the grant with it
        mStore.grants.remove(FIRST);

        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);

        assertEquals(Collections.singleton(SECOND.toString()), owned().keySet());
    }

    @Test
    public void foldersAddedToTheListDontPileUp() {
        // The volume list's add button takes the folder, then the list makes it the backup folder
        mStore.limit = 3;
        for (int i = 0; i < 20; ++i) {
            Uri added = tree("added" + i);
            BackupDestinationGrants.acquire(mContext, added, MODES, BackupDestinationGrants.PURPOSE_DESTINATION);
            Uri previous = BackupDestinationGrants.useDestination(mContext, StorageUtils.getFixedTreeUri(added));
            BackupDestinationGrants.releaseIfUnused(mContext, previous);
        }

        assertEquals(Collections.singletonList(tree("added19")), mStore.uris());
        assertFalse(mStore.evicted);
    }

    @Test
    public void anImportFolderMadeTheBackupFolderIsKeptWhileItHoldsBackups() {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.useDestination(mContext, StorageUtils.getFixedTreeUri(IMPORTS));
        assertEquals(Integer.valueOf(BackupDestinationGrants.PURPOSE_DESTINATION), owned().get(IMPORTS.toString()));
        mHoldingBackups.add(StorageUtils.getFixedTreeUri(IMPORTS).toString());

        // The import ending doesn't take it either
        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);
        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);
        BackupDestinationGrants.releaseIfUnused(mContext, previous);

        assertEquals(List.of(IMPORTS, SECOND), mStore.uris());
    }

    @Test
    public void twoImportsFromOneFolderKeepItUntilBothEnd() {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.importQueued(IMPORTS);
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.importQueued(IMPORTS);

        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);
        assertEquals(Collections.singletonList(IMPORTS), mStore.uris());

        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);
        assertEquals(Collections.emptyList(), mStore.uris());
        assertFalse(owned().containsKey(IMPORTS.toString()));
    }

    @Test
    public void anotherImportEndingCleanlyKeepsTheFolderForTheFailedOnesRetry() {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.importQueued(IMPORTS);
        BackupDestinationGrants.importQueued(IMPORTS);

        BackupDestinationGrants.importStarted(mContext, IMPORTS, TYPE);
        BackupDestinationGrants.importFailed(mContext, IMPORTS, TYPE);
        BackupDestinationGrants.importStarted(mContext, IMPORTS, OTHER_TYPE);
        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, OTHER_TYPE);

        assertEquals(Collections.singletonList(IMPORTS), mStore.uris());
    }

    @Test
    public void aFailedImportKeepsTheFolderAcrossRestartsUntilTheRetryWindowPasses() {
        long[] now = {1_000_000_000L};
        BackupDestinationGrants.setClockForTesting(() -> now[0]);
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.importQueued(IMPORTS);
        BackupDestinationGrants.importStarted(mContext, IMPORTS, TYPE);
        BackupDestinationGrants.importFailed(mContext, IMPORTS, TYPE);

        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);
        assertEquals(Collections.singletonList(IMPORTS), mStore.uris());

        now[0] += BackupDestinationGrants.RETRY_WINDOW_MILLIS - TimeUnit.MINUTES.toMillis(1);
        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);
        assertEquals(Collections.singletonList(IMPORTS), mStore.uris());

        now[0] += TimeUnit.MINUTES.toMillis(2);
        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);
        assertEquals(Collections.emptyList(), mStore.uris());
        assertFalse(owned().containsKey(IMPORTS.toString()));
        assertFalse(grantPrefs().contains("unfinished_imports"));
    }

    @Test
    public void aRetryThatSucceedsAfterARestartLetsTheFolderGo() {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.importQueued(IMPORTS);
        BackupDestinationGrants.importStarted(mContext, IMPORTS, TYPE);
        BackupDestinationGrants.importFailed(mContext, IMPORTS, TYPE);
        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);

        BackupDestinationGrants.importQueued(IMPORTS);
        BackupDestinationGrants.importStarted(mContext, IMPORTS, TYPE);
        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);

        assertEquals(Collections.emptyList(), mStore.uris());
        assertFalse(owned().containsKey(IMPORTS.toString()));
        assertFalse(grantPrefs().contains("unfinished_imports"));
    }

    @Test
    public void anImportCutShortWhenTheAppDiedKeepsTheFolderForARetry() {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.importQueued(IMPORTS);
        BackupDestinationGrants.importStarted(mContext, IMPORTS, TYPE);

        // Killed mid-import, so neither end was reached
        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);

        assertEquals(Collections.singletonList(IMPORTS), mStore.uris());
    }

    @Test
    public void aRetryRecordForAFolderAndroidNoLongerHoldsIsForgotten() {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.importQueued(IMPORTS);
        BackupDestinationGrants.importStarted(mContext, IMPORTS, TYPE);
        BackupDestinationGrants.importFailed(mContext, IMPORTS, TYPE);
        // The folder's app was removed, which took the grant with it
        mStore.grants.remove(IMPORTS);

        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);

        assertFalse(grantPrefs().contains("unfinished_imports"));
    }

    @Test
    @Config(shadows = ShadowCpuUtils.class)
    public void everyImportTheBatchServiceTakesKeepsTheFolderUntilTheLastEnds() throws Exception {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        ServiceController<BatchOpsService> controller = Robolectric.buildService(BatchOpsService.class).create();
        BatchOpsService service = controller.get();
        Looper looper = serviceLooper(service);
        // Keeps both imports waiting, so only the ends below count
        CountDownLatch gate = holdThread(looper);
        try {
            // One from the settings page and one replayed from history or retried reach the service the same way
            service.onStartCommand(importIntent(IMPORTS), 0, 1);
            service.onStartCommand(importIntent(IMPORTS), 0, 2);

            BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);
            assertEquals(Collections.singletonList(IMPORTS), mStore.uris());

            BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);
            assertEquals(Collections.emptyList(), mStore.uris());
        } finally {
            controller.destroy();
            gate.countDown();
            looper.getThread().join(5000);
        }
    }

    @Test
    public void aQueuedImportKeepsTheOldBackupFolderWhenAnotherIsPicked() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        BackupDestinationGrants.acquire(mContext, FIRST, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        Intent intent = importIntent(FIRST);
        BatchOpsService.start(mContext, intent);
        // Android was only asked to start the service, which hasn't run
        Intent started = Shadows.shadowOf((Application) mContext).getNextStartedService();
        assertTrue(intent.filterEquals(started));
        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);

        // The import hasn't reached the batch service yet, so nothing counts as running
        BackupDestinationGrants.releaseIfUnused(mContext, previous);
        assertEquals(List.of(FIRST, SECOND), mStore.uris());

        BackupDestinationGrants.importStarted(mContext, FIRST, TYPE);
        BackupDestinationGrants.releaseAfterImport(mContext, FIRST, TYPE);
        BackupDestinationGrants.startNewProcessForTesting();
        BackupDestinationGrants.pruneStale(mContext);
        assertEquals(Collections.singletonList(SECOND), mStore.uris());
    }

    @Test
    @Config(shadows = ShadowCpuUtils.class)
    public void anImportCountedWhenItWasHandedOverIsntCountedAgainByTheService() throws Exception {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        Intent intent = importIntent(IMPORTS);
        BatchOpsService.start(mContext, intent);
        ServiceController<BatchOpsService> controller = Robolectric.buildService(BatchOpsService.class).create();
        BatchOpsService service = controller.get();
        Looper looper = serviceLooper(service);
        CountDownLatch gate = holdThread(looper);
        try {
            service.onStartCommand(intent, 0, 1);

            BackupDestinationGrants.importStarted(mContext, IMPORTS, TYPE);
            BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);
            assertEquals(Collections.emptyList(), mStore.uris());
        } finally {
            controller.destroy();
            gate.countDown();
            looper.getThread().join(5000);
        }
    }

    @Test
    @Config(shadows = ShadowCpuUtils.class)
    public void importsDroppedWhenTheServiceStopsGiveBackTheirCount() throws Exception {
        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        ServiceController<BatchOpsService> controller = Robolectric.buildService(BatchOpsService.class).create();
        BatchOpsService service = controller.get();
        Looper looper = serviceLooper(service);
        CountDownLatch gate = holdThread(looper);
        try {
            service.onStartCommand(importIntent(IMPORTS), 0, 1);
            service.onStartCommand(importIntent(IMPORTS), 0, 2);
            // Stopped from outside before either ran
            controller.destroy();
        } finally {
            gate.countDown();
            looper.getThread().join(5000);
        }

        // So a later import that ends cleanly lets the folder go
        BackupDestinationGrants.importQueued(IMPORTS);
        BackupDestinationGrants.importStarted(mContext, IMPORTS, TYPE);
        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS, TYPE);
        assertEquals(Collections.emptyList(), mStore.uris());
    }

    @Test
    public void anImportWhoseServiceCouldntStartGivesBackItsCount() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        BackupDestinationGrants.acquire(mContext, FIRST, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        Context refusing = new ContextWrapper(mContext) {
            @Override
            public ComponentName startForegroundService(Intent service) {
                // What Android throws for a start it won't allow from the background
                throw new IllegalStateException("Not allowed to start service");
            }
        };
        assertThrows(IllegalStateException.class, () -> BatchOpsService.start(refusing, importIntent(FIRST)));
        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);

        BackupDestinationGrants.releaseIfUnused(mContext, previous);

        assertEquals(Collections.singletonList(SECOND), mStore.uris());
    }

    @Test
    public void aFailedImportKeepsTheOldBackupFolderForARetryWhenAnotherIsPicked() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        BackupDestinationGrants.acquire(mContext, FIRST, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.importQueued(FIRST);
        BackupDestinationGrants.importStarted(mContext, FIRST, TYPE);
        BackupDestinationGrants.importFailed(mContext, FIRST, TYPE);
        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);

        BackupDestinationGrants.releaseIfUnused(mContext, previous);

        assertEquals(List.of(FIRST, SECOND), mStore.uris());
    }

    @Test
    public void aFolderPickedAgainWhileTheStartupSweepRunsIsKept() {
        BackupDestinationGrants.acquire(mContext, FIRST, MODES, BackupDestinationGrants.PURPOSE_DESTINATION);
        BackupDestinationGrants.startNewProcessForTesting();
        // The sweep asks a network folder whether it holds backups, and meanwhile the user picks it for an import
        BackupDestinationGrants.setBackupProbeForTesting(uri -> {
            BackupDestinationGrants.acquire(mContext, FIRST, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
            return false;
        });

        BackupDestinationGrants.pruneStale(mContext);

        assertEquals(Collections.singletonList(FIRST), mStore.uris());
    }

    @Test
    public void anotherFolderPickedWhileTheStartupSweepRunsIsKept() {
        BackupDestinationGrants.acquire(mContext, FIRST, MODES, BackupDestinationGrants.PURPOSE_DESTINATION);
        BackupDestinationGrants.acquire(mContext, SECOND, MODES, BackupDestinationGrants.PURPOSE_DESTINATION);
        BackupDestinationGrants.startNewProcessForTesting();
        // While the sweep asks the first folder it reaches, the user picks the other one for an import
        List<Uri> picked = new ArrayList<>();
        BackupDestinationGrants.setBackupProbeForTesting(uri -> {
            if (picked.isEmpty()) {
                Uri other = uri.equals(StorageUtils.getFixedTreeUri(FIRST)) ? SECOND : FIRST;
                BackupDestinationGrants.acquire(mContext, other, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
                picked.add(other);
            }
            return false;
        });

        BackupDestinationGrants.pruneStale(mContext);

        assertEquals(1, picked.size());
        assertEquals(picked, mStore.uris());
    }

    @Test
    public void theNewBackupFolderIsOnDiskBeforeTheOldOneIsLetGo() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        Uri previous = BackupDestinationGrants.selectDestination(mContext, SECOND, MODES);
        String saved = StorageUtils.getFixedTreeUri(SECOND).toString();
        List<Boolean> onDiskAtRelease = new ArrayList<>();
        mStore.onRelease = () -> onDiskAtRelease.add(savedOnDisk(saved));

        BackupDestinationGrants.releaseIfUnused(mContext, previous);

        assertEquals(Collections.singletonList(true), onDiskAtRelease);
    }

    @NonNull
    private Intent importIntent(@NonNull Uri directory) {
        BatchBackupImportOptions options = new BatchBackupImportOptions(ImportType.OAndBackup, directory, false);
        return BatchOpsService.getServiceIntent(mContext, BatchQueueItem.getBatchOpQueue(
                BatchOpsManager.OP_IMPORT_BACKUPS, new ArrayList<>(), new ArrayList<>(), options));
    }

    @NonNull
    private static Looper serviceLooper(@NonNull ForegroundService service) throws ReflectiveOperationException {
        Field field = ForegroundService.class.getDeclaredField("mServiceLooper");
        field.setAccessible(true);
        return (Looper) field.get(service);
    }

    /** Keeps the service's thread busy until the returned latch opens, so starts sent meanwhile wait in its queue. */
    @NonNull
    private static CountDownLatch holdThread(@NonNull Looper looper) throws InterruptedException {
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch holding = new CountDownLatch(1);
        new Handler(looper).post(() -> {
            holding.countDown();
            try {
                gate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue("the service's thread picked up the hold", holding.await(5, TimeUnit.SECONDS));
        return gate;
    }

    private boolean savedOnDisk(@NonNull String value) {
        // Where AppPref writes, which setUp binds to this test's context
        File[] files = new File(ContextUtils.getContext().getDataDir(), "shared_prefs").listFiles();
        if (files == null) {
            return false;
        }
        try {
            for (File file : files) {
                if (new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains(value)) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return false;
    }

    @NonNull
    private Map<String, Integer> owned() {
        return BackupDestinationGrants.ownedForTesting(mContext);
    }

    @NonNull
    private SharedPreferences grantPrefs() {
        return mContext.getSharedPreferences(BackupDestinationGrants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static Uri tree(@NonNull String folder) {
        return Uri.parse("content://com.example.documents/tree/primary%3A" + folder);
    }

    private static final class ProcessDeath extends Error {
    }

    private static final class FakeGrantStore implements BackupDestinationGrants.GrantStore {
        final Map<Uri, Integer> grants = new LinkedHashMap<>();
        final Set<Uri> revoked = new HashSet<>();
        final Set<Uri> dropped = new HashSet<>();
        final Set<Uri> readOnly = new HashSet<>();
        int limit = Integer.MAX_VALUE;
        boolean evicted;
        boolean dieOnTake;
        @Nullable
        Runnable onRelease;

        void grant(@NonNull Uri uri) {
            grants.put(uri, MODES);
        }

        @NonNull
        List<Uri> uris() {
            return new ArrayList<>(grants.keySet());
        }

        @Override
        public void take(@NonNull Uri uri, int modes) {
            if (dieOnTake) {
                throw new ProcessDeath();
            }
            if (revoked.contains(uri)) {
                throw new SecurityException("No persistable permission granted for " + uri);
            }
            if (dropped.contains(uri)) {
                return;
            }
            grants.put(uri, readOnly.contains(uri) ? Intent.FLAG_GRANT_READ_URI_PERMISSION : modes);
            while (grants.size() > limit) {
                Iterator<Uri> oldest = grants.keySet().iterator();
                oldest.next();
                oldest.remove();
                evicted = true;
            }
        }

        @Override
        public void release(@NonNull Uri uri, int modes) {
            if (onRelease != null) {
                onRelease.run();
            }
            if (grants.remove(uri) == null) {
                throw new SecurityException("No permission grant found for " + uri);
            }
        }

        @NonNull
        @Override
        public List<UriPermission> persisted() {
            List<UriPermission> permissions = new ArrayList<>();
            for (Map.Entry<Uri, Integer> grant : grants.entrySet()) {
                permissions.add(ReflectionHelpers.callConstructor(UriPermission.class,
                        ClassParameter.from(Uri.class, grant.getKey()),
                        ClassParameter.from(int.class, grant.getValue()),
                        ClassParameter.from(long.class, 0L)));
            }
            return permissions;
        }
    }
}
