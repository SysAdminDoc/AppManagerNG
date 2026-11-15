// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Service;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.convert.ImportType;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupImportOptions;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

/**
 * An import that stops before every backup in the folder was tried is reported as failed, so the folder keeps its
 * access for a retry and the user sees it didn't finish.
 */
@RunWith(RobolectricTestRunner.class)
public class BatchOpsImportResultTest {
    private Path mFolder;

    @Before
    public void setUp() throws IOException {
        mFolder = Paths.get(RoboUtils.getTestBaseDir()).createNewDirectory("import-result-" + UUID.randomUUID());
    }

    @After
    public void tearDown() {
        mFolder.delete();
    }

    @Test
    public void aFolderWithNothingToImportIsAFinishedImport() {
        BatchOpsManager.Result result = new BatchOpsManager(null).performOp(importInfo(), null);

        assertTrue(result.isSuccessful());
    }

    @Test
    public void anImportThatStopsBeforeItsBackupsAreTriedFails() {
        StoppingProgressHandler progress = new StoppingProgressHandler();

        BatchOpsManager.Result result = new BatchOpsManager(null).performOp(importInfo(), progress);

        assertTrue("the folder was read before the import stopped", progress.reached);
        assertFalse(result.isSuccessful());
        assertEquals(Collections.emptyList(), result.getFailedPackages());
    }

    @Test
    public void aFailureWithNoBackupTriedSaysTheFolderCouldntBeRead() {
        Context context = RuntimeEnvironment.getApplication();

        assertEquals(context.getString(R.string.import_backups_folder_unreadable),
                BatchOpsService.getDesiredErrorString(context, BatchOpsManager.OP_IMPORT_BACKUPS, 0));
        assertEquals(context.getResources().getQuantityString(R.plurals.alert_failed_to_import_backups, 2, 2),
                BatchOpsService.getDesiredErrorString(context, BatchOpsManager.OP_IMPORT_BACKUPS, 2));
    }

    @NonNull
    private BatchOpsManager.BatchOpsInfo importInfo() {
        BatchBackupImportOptions options = new BatchBackupImportOptions(ImportType.OAndBackup, mFolder.getUri(),
                false);
        return BatchOpsManager.BatchOpsInfo.getInstance(BatchOpsManager.OP_IMPORT_BACKUPS, new ArrayList<>(),
                new ArrayList<>(), options);
    }

    /** Fails the first time the import reads the progress, which it does right after listing the folder. */
    private static final class StoppingProgressHandler extends ProgressHandler {
        boolean reached;

        @Override
        public void onAttach(@Nullable Service service, @NonNull Object message) {
        }

        @Override
        public void onProgressStart(int max, float current, @Nullable Object message) {
        }

        @Override
        public void onProgressUpdate(int max, float current, @Nullable Object message) {
        }

        @Override
        public void onResult(@Nullable Object message) {
        }

        @Override
        public void onDetach(@Nullable Service service) {
        }

        @NonNull
        @Override
        public ProgressHandler newSubProgressHandler() {
            return new StoppingProgressHandler();
        }

        @Nullable
        @Override
        public Object getLastMessage() {
            return null;
        }

        @Override
        public int getLastMax() {
            reached = true;
            throw new IllegalStateException("The progress couldn't be read");
        }

        @Override
        public float getLastProgress() {
            return 0;
        }
    }
}
