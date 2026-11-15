// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.utils.StorageUtils;

/**
 * A backup folder picked through the system picker is read from its provider. When the provider can't answer, as for
 * an unplugged drive or an offline network folder, the folder counts as holding backups so its access is kept.
 */
@RunWith(RobolectricTestRunner.class)
public class BackupFolderProviderTest {
    private static final String AUTHORITY = "io.github.muntashirakon.AppManager.test.documents";
    private static final Uri TREE = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:Backups");

    @Before
    public void setUp() {
        FolderProvider.reset();
        Robolectric.buildContentProvider(FolderProvider.class).create(AUTHORITY);
    }

    @After
    public void tearDown() {
        FolderProvider.reset();
    }

    @Test
    public void aFolderWithOnlyLogsAndSavedApksHoldsNoBackups() {
        FolderProvider.sChildren = children(
                folder(SaveLogHelper.SAVED_LOGS_DIR), folder("apks"), folder(".tmp"), file("notes.txt"));

        assertFalse(BackupItems.holdsBackups(StorageUtils.getFixedTreeUri(TREE)));
        assertEquals(DocumentsContract.buildChildDocumentsUriUsingTree(TREE, "primary:Backups"),
                FolderProvider.sQueried);
    }

    @Test
    public void aFolderWithABackupHoldsBackups() {
        FolderProvider.sChildren = children(folder("apks"), folder(BackupItems.BACKUP_DIRECTORY));

        assertTrue(BackupItems.holdsBackups(StorageUtils.getFixedTreeUri(TREE)));
    }

    @Test
    public void thePickedTreeItselfIsReadToo() {
        FolderProvider.sChildren = children(folder("apks"));

        assertFalse(BackupItems.holdsBackups(TREE));
        assertEquals(DocumentsContract.buildChildDocumentsUriUsingTree(TREE, "primary:Backups"),
                FolderProvider.sQueried);
    }

    @Test
    public void aFolderOnADriveThatIsntThereCountsAsHoldingBackups() {
        // What a documents provider throws for a folder it can't open
        FolderProvider.sFailure = new IllegalArgumentException("Failed during query");

        assertTrue(BackupItems.holdsBackups(StorageUtils.getFixedTreeUri(TREE)));
    }

    @Test
    public void aProviderThatGivesNoAnswerCountsAsHoldingBackups() {
        FolderProvider.sChildren = null;

        assertTrue(BackupItems.holdsBackups(StorageUtils.getFixedTreeUri(TREE)));
    }

    @Test
    public void aNetworkFolderStillListingCountsAsHoldingBackups() {
        MatrixCursor partial = children(folder("apks"));
        Bundle extras = new Bundle();
        extras.putBoolean(DocumentsContract.EXTRA_LOADING, true);
        partial.setExtras(extras);
        FolderProvider.sChildren = partial;

        assertTrue(BackupItems.holdsBackups(StorageUtils.getFixedTreeUri(TREE)));
    }

    @Test
    public void aUriThatIsntAFolderCountsAsHoldingBackups() {
        assertTrue(BackupItems.holdsBackups(DocumentsContract.buildDocumentUri(AUTHORITY, "primary:Backups")));
    }

    @NonNull
    private static MatrixCursor children(@NonNull Object[]... rows) {
        MatrixCursor cursor = new MatrixCursor(new String[]{Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE});
        for (Object[] row : rows) {
            cursor.addRow(row);
        }
        return cursor;
    }

    @NonNull
    private static Object[] folder(@NonNull String name) {
        return new Object[]{name, Document.MIME_TYPE_DIR};
    }

    @NonNull
    private static Object[] file(@NonNull String name) {
        return new Object[]{name, "text/plain"};
    }

    public static final class FolderProvider extends ContentProvider {
        @Nullable
        static MatrixCursor sChildren;
        @Nullable
        static RuntimeException sFailure;
        @Nullable
        static Uri sQueried;

        static void reset() {
            sChildren = null;
            sFailure = null;
            sQueried = null;
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Nullable
        @Override
        public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
            sQueried = uri;
            if (sFailure != null) {
                throw sFailure;
            }
            return sChildren;
        }

        @Nullable
        @Override
        public String getType(@NonNull Uri uri) {
            return null;
        }

        @Nullable
        @Override
        public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
            return null;
        }

        @Override
        public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                          @Nullable String[] selectionArgs) {
            return 0;
        }
    }
}
