// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.batchops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

@RunWith(RobolectricTestRunner.class)
public class BatchOpsJournalTest {
    @Test
    public void interruptedOperationReturnsPersistedQueueWhenServiceIsNotRunning() {
        Context context = RuntimeEnvironment.getApplication();
        BatchOpsJournal.dismissInterrupted(context);
        BatchQueueItem queueItem = createQueueItem();

        BatchOpsJournal.recordIntent(context, queueItem);
        BatchOpsJournal.recordExecuting(context, queueItem);

        assertNull(BatchOpsJournal.getInterruptedOperation(context, true));
        BatchOpsJournal.Entry entry = BatchOpsJournal.getInterruptedOperation(context, false);

        assertNotNull(entry);
        assertEquals(BatchOpsManager.OP_FREEZE, entry.getQueueItem().getOp());
        assertEquals(2, entry.getTargetCount());
    }

    @Test
    public void completedOperationClearsJournal() {
        Context context = RuntimeEnvironment.getApplication();
        BatchOpsJournal.dismissInterrupted(context);

        BatchOpsJournal.recordExecuting(context, createQueueItem());
        BatchOpsJournal.markCompleted(context);

        assertNull(BatchOpsJournal.getInterruptedOperation(context, false));
    }

    @Test
    public void interruptedOperationPreservesFailureReason() {
        Context context = RuntimeEnvironment.getApplication();
        BatchOpsJournal.dismissInterrupted(context);

        BatchOpsJournal.recordExecuting(context, createQueueItem());
        BatchOpsJournal.markInterrupted(context, new IllegalStateException("binder died\nwhile running"));
        BatchOpsJournal.markInterrupted(context, null);
        BatchOpsJournal.Entry entry = BatchOpsJournal.getInterruptedOperation(context, false);

        assertNotNull(entry);
        assertEquals("IllegalStateException: binder died while running", entry.getReason());
    }

    @Test
    public void interruptedOperationBuildsRetryQueueFromUnfinishedTargets() {
        Context context = RuntimeEnvironment.getApplication();
        BatchOpsJournal.dismissInterrupted(context);

        BatchOpsJournal.recordExecuting(context, createQueueItem());
        BatchOpsJournal.markInterrupted(context, null, new BatchOpsManager.Result(
                Collections.singletonList(new UserPackagePair("com.example.two", 0)), false));
        BatchOpsJournal.Entry entry = BatchOpsJournal.getInterruptedOperation(context, false);

        assertNotNull(entry);
        assertEquals(1, entry.getFailedTargetCount());
        assertEquals(1, entry.getCompletedTargetCount());
        assertEquals(1, entry.getRetryTargetCount());
        BatchQueueItem retryQueue = entry.getRetryQueueItem();
        assertEquals(1, retryQueue.getPackages().size());
        assertEquals("com.example.two", retryQueue.getPackages().get(0));
        assertEquals(Integer.valueOf(0), retryQueue.getUsers().get(0));
    }

    @Test
    public void recordedTargetProgressSurvivesInterruptionWithoutResult() {
        Context context = RuntimeEnvironment.getApplication();
        BatchOpsJournal.dismissInterrupted(context);

        BatchOpsJournal.recordExecuting(context, createQueueItem());
        BatchOpsJournal.recordTargetFinished(context, new UserPackagePair("com.example.one", 0), false);
        BatchOpsJournal.markInterrupted(context, null);
        BatchOpsJournal.Entry entry = BatchOpsJournal.getInterruptedOperation(context, false);

        assertNotNull(entry);
        assertEquals(1, entry.getCompletedTargetCount());
        assertEquals(0, entry.getFailedTargetCount());
        assertEquals(1, entry.getRetryTargetCount());
        BatchQueueItem retryQueue = entry.getRetryQueueItem();
        assertEquals("com.example.two", retryQueue.getPackages().get(0));
    }

    @Test
    public void noResultInterruptionRetriesOriginalQueue() {
        Context context = RuntimeEnvironment.getApplication();
        BatchOpsJournal.dismissInterrupted(context);
        BatchQueueItem queueItem = createQueueItem();

        BatchOpsJournal.recordExecuting(context, queueItem);
        BatchOpsJournal.markInterrupted(context, null);
        BatchOpsJournal.Entry entry = BatchOpsJournal.getInterruptedOperation(context, false);

        assertNotNull(entry);
        assertEquals(0, entry.getCompletedTargetCount());
        assertEquals(0, entry.getFailedTargetCount());
        assertEquals(queueItem.getPackages().size(), entry.getRetryTargetCount());
    }

    private static BatchQueueItem createQueueItem() {
        ArrayList<String> packages = new ArrayList<>();
        packages.add("com.example.one");
        packages.add("com.example.two");
        ArrayList<Integer> users = new ArrayList<>();
        users.add(0);
        users.add(0);
        return BatchQueueItem.getBatchOpQueue(BatchOpsManager.OP_FREEZE, packages, users, null);
    }
}
