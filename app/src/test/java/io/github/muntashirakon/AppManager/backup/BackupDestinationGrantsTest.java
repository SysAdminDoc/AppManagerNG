// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.StorageUtils;

@RunWith(RobolectricTestRunner.class)
public class BackupDestinationGrantsTest {
    private static final int MODES = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private static final Uri FIRST = tree("first");
    private static final Uri SECOND = tree("second");
    private static final Uri IMPORTS = tree("imports");

    private Context mContext;
    private FakeGrantStore mStore;
    private final Set<String> mHoldingBackups = new HashSet<>();
    private boolean mBackupRunning;
    private String mPreviousVolume;

    @Before
    public void setUp() {
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

        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS);

        assertEquals(Collections.singletonList(FIRST), mStore.uris());
        assertFalse(owned().containsKey(IMPORTS.toString()));
    }

    @Test
    public void importingFromTheBackupFolderKeepsIt() {
        BackupDestinationGrants.selectDestination(mContext, FIRST, MODES);
        BackupDestinationGrants.acquire(mContext, FIRST, MODES, BackupDestinationGrants.PURPOSE_IMPORT);

        BackupDestinationGrants.releaseAfterImport(mContext, FIRST);

        assertEquals(Collections.singletonList(FIRST), mStore.uris());
        assertEquals(Integer.valueOf(BackupDestinationGrants.PURPOSE_DESTINATION), owned().get(FIRST.toString()));
    }

    @Test
    public void anImportFromAFolderTheFileManagerHoldsLeavesIt() {
        mStore.grant(IMPORTS);

        BackupDestinationGrants.acquire(mContext, IMPORTS, MODES, BackupDestinationGrants.PURPOSE_IMPORT);
        BackupDestinationGrants.releaseAfterImport(mContext, IMPORTS);

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

    @NonNull
    private Map<String, Integer> owned() {
        return BackupDestinationGrants.ownedForTesting(mContext);
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
