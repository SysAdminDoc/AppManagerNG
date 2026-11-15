// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.BackoffPolicy;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.impl.Processor;
import androidx.work.impl.Scheduler;
import androidx.work.impl.StartStopToken;
import androidx.work.impl.WorkDatabase;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.WorkManagerImplExtKt;
import androidx.work.impl.constraints.trackers.Trackers;
import androidx.work.impl.model.SystemIdInfo;
import androidx.work.impl.model.WorkGenerationalId;
import androidx.work.impl.model.WorkSpec;
import androidx.work.impl.model.WorkSpecKt;
import androidx.work.impl.utils.ForceStopRunnable;
import androidx.work.impl.utils.SerialExecutorImpl;
import androidx.work.impl.utils.taskexecutor.SerialExecutor;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;

/**
 * Runs the scheduled backup through WorkManager 2.10.5 itself, over a database file that outlives
 * each WorkManager the way it outlives a process. Only the backup is replaced, by a count.
 */
@RunWith(RobolectricTestRunner.class)
public class ScheduledBackupNetworkTest {
    private static final long ONE_DAY = TimeUnit.DAYS.toMillis(1);
    private static final String DB_NAME = "scheduled-backup-network-test.db";

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final ConnectivityManager mCm = mContext.getSystemService(ConnectivityManager.class);
    private final AtomicLong mNow = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger mBackups = new AtomicInteger();
    // What each counted run returns
    private final AtomicReference<ListenableWorker.Result> mRunResult =
            new AtomicReference<>(ListenableWorker.Result.success());
    private final List<String> mScheduled = new CopyOnWriteArrayList<>();
    private QueueExecutor mExecutor;
    private WorkDatabase mDb;
    private WorkManagerImpl mWm;
    private boolean mSavedEnabled;
    private int mSavedNetworkType;
    private long mSavedLastRun;
    private String mSavedLastResult;

    @Before
    public void setUp() {
        mSavedEnabled = Prefs.BackupRestore.isScheduledAutoBackupEnabled();
        mSavedNetworkType = Prefs.BackupRestore.getScheduledBackupNetworkType();
        mSavedLastRun = Prefs.BackupRestore.getScheduledBackupLastRun();
        mSavedLastResult = Prefs.BackupRestore.getScheduledBackupLastResult();
        Prefs.BackupRestore.setScheduledAutoBackupEnabled(true);
        Prefs.BackupRestore.setScheduledBackupNetworkType(AutoBackupScheduler.NETWORK_CONNECTED);
    }

    @After
    public void tearDown() {
        WorkManagerImpl.setDelegate(null);
        if (mExecutor != null) {
            mExecutor.shutdown();
        }
        if (mDb != null) {
            mDb.close();
        }
        mContext.deleteDatabase(DB_NAME);
        Prefs.BackupRestore.setScheduledAutoBackupEnabled(mSavedEnabled);
        Prefs.BackupRestore.setScheduledBackupNetworkType(mSavedNetworkType);
        Prefs.BackupRestore.setScheduledBackupLastRun(mSavedLastRun);
        Prefs.BackupRestore.setScheduledBackupLastResult(mSavedLastResult != null ? mSavedLastResult : "");
    }

    @Test
    @Config(sdk = 35)
    public void aBlockedScheduledBackupRunsOnceTheNetworkIsUsableAndKeepsItsTimeOfDay() throws Exception {
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        WorkSpec spec = backupSpec();
        assertEquals(NetworkType.CONNECTED, spec.constraints.getRequiredNetworkType());
        long slot = spec.calculateNextRunTime();

        // The job fires because WorkManager thinks the constraint is met.
        mNow.set(slot);
        runNow(spec);
        spec = backupSpec();
        assertEquals("no backup without a usable network", 0, mBackups.get());
        assertEquals(WorkInfo.State.ENQUEUED, spec.state);
        assertTrue(spec.isPeriodic());
        assertEquals(ONE_DAY, spec.intervalDuration);
        assertEquals(0, spec.runAttemptCount);
        assertEquals("the schedule keeps its time of day", slot + ONE_DAY, spec.calculateNextRunTime());
        assertEquals("settings shows the wait", mContext.getString(R.string.auto_backup_result_waiting_for_network),
                Prefs.BackupRestore.getScheduledBackupLastResult());
        WorkSpec catchUp = catchUpSpec();
        assertEquals(WorkInfo.State.ENQUEUED, catchUp.state);
        assertFalse(catchUp.isPeriodic());
        assertEquals(NetworkType.CONNECTED, catchUp.constraints.getRequiredNetworkType());

        // Still blocked when the catch-up fires, so it tries again later.
        mNow.set(slot + TimeUnit.MINUTES.toMillis(1));
        runNow(catchUp);
        catchUp = catchUpSpec();
        assertEquals(0, mBackups.get());
        assertEquals(WorkInfo.State.ENQUEUED, catchUp.state);
        assertEquals(1, catchUp.runAttemptCount);

        setNetwork(connected());
        mNow.set(slot + TimeUnit.HOURS.toMillis(7));
        runNow(catchUp);
        assertEquals(1, mBackups.get());
        assertEquals(WorkInfo.State.SUCCEEDED, catchUpSpec().state);
        assertEquals("the backup didn't move the schedule", slot + ONE_DAY, backupSpec().calculateNextRunTime());

        // Starting the schedule again before its time doesn't back up again.
        runNow(backupSpec());
        assertEquals(1, mBackups.get());

        // The next day it backs up at its usual time.
        mNow.set(slot + ONE_DAY);
        runNow(backupSpec());
        assertEquals(2, mBackups.get());
        assertEquals(slot + 2 * ONE_DAY, backupSpec().calculateNextRunTime());
    }

    @Test
    @Config(sdk = 35)
    public void aWaitingBackupSurvivesProcessDeathAndReboot() throws Exception {
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        long slot = backupSpec().calculateNextRunTime();
        mNow.set(slot);
        runNow(backupSpec());
        WorkSpec waiting = catchUpSpec();
        assertEquals(WorkInfo.State.ENQUEUED, waiting.state);

        // The process dies. Nothing gets to clean up, and the next one opens the same database.
        mDb.close();
        startProcess();
        WorkSpec afterDeath = catchUpSpec();
        assertEquals(waiting.id, afterDeath.id);
        assertEquals(WorkInfo.State.ENQUEUED, afterDeath.state);
        assertEquals(slot + ONE_DAY, backupSpec().calculateNextRunTime());

        // A reboot leaves JobScheduler without the jobs. The startup check WorkManager runs after
        // one finds them missing and hands the work back to its scheduler.
        mScheduled.clear();
        new ForceStopRunnable(mContext, mWm).forceStopRunnable();
        drain();
        assertTrue("the catch-up is rescheduled after the reboot", mScheduled.contains(afterDeath.id));
        assertTrue("so is the schedule", mScheduled.contains(backupSpec().id));
        assertEquals(slot + ONE_DAY, backupSpec().calculateNextRunTime());

        setNetwork(connected());
        mNow.set(slot + TimeUnit.HOURS.toMillis(3));
        runNow(catchUpSpec());
        assertEquals(1, mBackups.get());
        assertEquals(WorkInfo.State.SUCCEEDED, catchUpSpec().state);
        assertEquals(slot + ONE_DAY, backupSpec().calculateNextRunTime());
    }

    @Test
    @Config(sdk = 35)
    public void aScheduledRunThatBacksUpDropsTheCatchUpStillWaiting() throws Exception {
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        long slot = backupSpec().calculateNextRunTime();
        mNow.set(slot);
        runNow(backupSpec());
        assertEquals(WorkInfo.State.ENQUEUED, catchUpSpec().state);

        // The catch-up never got to run before the next day's slot, which has a usable network.
        setNetwork(connected());
        mNow.set(slot + ONE_DAY);
        runNow(backupSpec());
        assertEquals(1, mBackups.get());
        assertEquals(WorkInfo.State.CANCELLED, catchUpSpec().state);
    }

    @Test
    @Config(sdk = 35)
    public void aBackupStartedByHandDropsTheCatchUpStillWaiting() throws Exception {
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        mNow.set(backupSpec().calculateNextRunTime());
        runNow(backupSpec());
        assertEquals(WorkInfo.State.ENQUEUED, catchUpSpec().state);

        AutoBackupScheduler.enqueueManualRun(mContext, AutoBackupWorker.ORIGIN_SETTINGS);
        drain();
        runNow(manualSpec());
        assertEquals(1, mBackups.get());
        assertEquals("the backup covered what the catch-up would have", WorkInfo.State.CANCELLED,
                catchUpSpec().state);
    }

    @Test
    @Config(sdk = 35)
    public void aCatchUpTriesAgainAQuarterHourLaterAndALittleLongerEachTime() throws Exception {
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        long slot = backupSpec().calculateNextRunTime();
        mNow.set(slot);
        runNow(backupSpec());
        WorkSpec catchUp = catchUpSpec();
        assertEquals(BackoffPolicy.LINEAR, catchUp.backoffPolicy);
        assertEquals(TimeUnit.MINUTES.toMillis(15), catchUp.backoffDelayDuration);

        long firstTry = slot + TimeUnit.MINUTES.toMillis(1);
        mNow.set(firstTry);
        runNow(catchUp);
        assertEquals(firstTry + TimeUnit.MINUTES.toMillis(15), catchUpSpec().calculateNextRunTime());
        long secondTry = firstTry + TimeUnit.MINUTES.toMillis(15);
        mNow.set(secondTry);
        runNow(catchUpSpec());
        assertEquals(2, catchUpSpec().runAttemptCount);
        assertEquals(secondTry + TimeUnit.MINUTES.toMillis(30), catchUpSpec().calculateNextRunTime());
        assertEquals(0, mBackups.get());
    }

    @Test
    @Config(sdk = 35)
    public void aScheduledRunWhileAnotherBacksUpKeepsItsTimeOfDay() throws Exception {
        setNetwork(connected());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        long slot = backupSpec().calculateNextRunTime();
        assertTrue(AutoBackupWorker.claimRun(AutoBackupWorker.ORIGIN_SETTINGS));
        try {
            mNow.set(slot);
            runNow(backupSpec());
            WorkSpec spec = backupSpec();
            assertEquals(0, mBackups.get());
            assertEquals(WorkInfo.State.ENQUEUED, spec.state);
            assertEquals(0, spec.runAttemptCount);
            assertEquals("the schedule keeps its time of day", slot + ONE_DAY, spec.calculateNextRunTime());
            assertEquals(WorkInfo.State.ENQUEUED, catchUpSpec().state);

            // The catch-up waits for the run in flight too
            mNow.set(slot + TimeUnit.MINUTES.toMillis(1));
            runNow(catchUpSpec());
            assertEquals(0, mBackups.get());
            assertEquals(WorkInfo.State.ENQUEUED, catchUpSpec().state);
            assertEquals(1, catchUpSpec().runAttemptCount);
        } finally {
            AutoBackupWorker.releaseRun();
        }
        // The run in flight ended without backing up, so the catch-up does it
        mNow.set(slot + TimeUnit.MINUTES.toMillis(20));
        runNow(catchUpSpec());
        assertEquals(1, mBackups.get());
        assertEquals(WorkInfo.State.SUCCEEDED, catchUpSpec().state);
        assertEquals(slot + ONE_DAY, backupSpec().calculateNextRunTime());
    }

    @Test
    @Config(sdk = 35)
    public void aScheduledRunThatCantStartKeepsItsTimeOfDay() throws Exception {
        // As when Android won't let the run become a foreground service
        mRunResult.set(ListenableWorker.Result.retry());
        setNetwork(connected());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        long slot = backupSpec().calculateNextRunTime();
        mNow.set(slot);
        runNow(backupSpec());
        WorkSpec spec = backupSpec();
        assertEquals(WorkInfo.State.ENQUEUED, spec.state);
        assertEquals(0, spec.runAttemptCount);
        assertEquals(slot + ONE_DAY, spec.calculateNextRunTime());
        assertEquals(WorkInfo.State.ENQUEUED, catchUpSpec().state);

        mRunResult.set(ListenableWorker.Result.success());
        mNow.set(slot + TimeUnit.MINUTES.toMillis(1));
        runNow(catchUpSpec());
        assertEquals(2, mBackups.get());
        assertEquals(WorkInfo.State.SUCCEEDED, catchUpSpec().state);
        assertEquals(slot + ONE_DAY, backupSpec().calculateNextRunTime());
    }

    @Test
    @Config(sdk = 35)
    public void aChangedNetworkSettingReachesTheWaitingCatchUp() throws Exception {
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        mNow.set(backupSpec().calculateNextRunTime());
        runNow(backupSpec());
        WorkSpec waiting = catchUpSpec();
        assertEquals(NetworkType.CONNECTED, waiting.constraints.getRequiredNetworkType());

        Prefs.BackupRestore.setScheduledBackupNetworkType(AutoBackupScheduler.NETWORK_NOT_REQUIRED);
        AutoBackupScheduler.scheduleOrCancel(mContext);
        drain();
        WorkSpec refreshed = catchUpSpec();
        assertEquals(waiting.id, refreshed.id);
        assertEquals(WorkInfo.State.ENQUEUED, refreshed.state);
        assertEquals(NetworkType.NOT_REQUIRED, refreshed.constraints.getRequiredNetworkType());
        assertEquals(BackoffPolicy.LINEAR, refreshed.backoffPolicy);

        runNow(refreshed);
        assertEquals("the blocked network no longer holds it back", 1, mBackups.get());
        assertEquals(WorkInfo.State.SUCCEEDED, catchUpSpec().state);
    }

    @Test
    @Config(sdk = 35)
    public void turningTheScheduleOffEndsTheWait() throws Exception {
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        mNow.set(backupSpec().calculateNextRunTime());
        runNow(backupSpec());
        assertEquals(mContext.getString(R.string.auto_backup_result_waiting_for_network),
                Prefs.BackupRestore.getScheduledBackupLastResult());

        Prefs.BackupRestore.setScheduledAutoBackupEnabled(false);
        AutoBackupScheduler.scheduleOrCancel(mContext);
        drain();
        assertEquals(WorkInfo.State.CANCELLED, catchUpSpec().state);
        assertEquals("settings no longer says it's waiting",
                mContext.getString(R.string.auto_backup_result_disabled),
                Prefs.BackupRestore.getScheduledBackupLastResult());
    }

    @Test
    @Config(sdk = 35)
    public void anUnmeteredSettingWaitsOffAMeteredNetwork() {
        setNetwork(mobile());
        assertFalse(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_UNMETERED));
        assertTrue(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_CONNECTED));
        setNetwork(connected());
        assertTrue(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_UNMETERED));
    }

    @Test
    @Config(sdk = {23, 28})
    public void olderVersionsWaitForAUsableNetworkToo() throws Exception {
        // WorkManager 2.10.5 reads the network from its capabilities from Android 9 on, which
        // don't show the block, so the run checks for itself on every version.
        setNetwork(blocked());
        assertFalse(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_CONNECTED));
        setNetwork(connected());
        assertTrue(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_CONNECTED));
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        WorkSpec spec = backupSpec();
        assertTrue(spec.isPeriodic());
        assertEquals(ONE_DAY, spec.intervalDuration);
        assertEquals(NetworkType.CONNECTED, spec.constraints.getRequiredNetworkType());

        mNow.set(spec.calculateNextRunTime());
        runNow(spec);
        assertEquals("a usable network backs up as before", 1, mBackups.get());
        assertEquals(1, backupSpec().getPeriodCount());
    }

    @Test
    @Config(sdk = 35, shadows = RefusingConnectivityManager.class)
    public void aRefusedNetworkReadLeavesItToTheConstraint() {
        assertTrue(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_CONNECTED));
    }

    @Test
    @Config(sdk = 35)
    public void onlyAScheduledRunThatNeedsANetworkWaitsForOne() throws Exception {
        setNetwork(blocked());
        assertFalse(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_CONNECTED));
        assertFalse(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_UNMETERED));
        assertTrue(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_NOT_REQUIRED));
        setNetwork(null);
        assertFalse(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_CONNECTED));
        setNetwork(connected());
        assertTrue(AutoBackupWorker.hasUsableNetwork(mContext, AutoBackupScheduler.NETWORK_CONNECTED));

        // A run someone starts by hand isn't held back.
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.enqueueManualRun(mContext, AutoBackupWorker.ORIGIN_SETTINGS);
        drain();
        List<String> ids = mDb.workNameDao().getWorkSpecIdsWithName(AutoBackupScheduler.MANUAL_WORK_NAME);
        assertEquals(1, ids.size());
        runNow(mDb.workSpecDao().getWorkSpec(ids.get(0)));
        assertEquals(1, mBackups.get());
    }

    /** Starts a WorkManager the way a new process would, over the database file left behind. */
    private void startProcess() throws InterruptedException {
        if (mExecutor != null) {
            mExecutor.shutdown();
        }
        mExecutor = new QueueExecutor();
        SerialExecutor serial = new SerialExecutorImpl(mExecutor);
        Executor executor = mExecutor;
        TaskExecutor taskExecutor = new TaskExecutor() {
            @NonNull
            @Override
            public Executor getMainThreadExecutor() {
                return executor;
            }

            @NonNull
            @Override
            public SerialExecutor getSerialTaskExecutor() {
                return serial;
            }
        };
        Configuration configuration = new Configuration.Builder()
                .setExecutor(mExecutor.workers)
                .setTaskExecutor(executor)
                .setClock(mNow::get)
                .setWorkerFactory(new CountingWorkerFactory(mBackups, mRunResult))
                .build();
        // Only an in-memory WorkDatabase allows queries on the test thread, and this one has to
        // outlive the WorkManager that writes it.
        mDb = Room.databaseBuilder(mContext, WorkDatabase.class, DB_NAME).allowMainThreadQueries().build();
        WorkDatabase db = mDb;
        Scheduler recording = new Scheduler() {
            @Override
            public void schedule(@NonNull WorkSpec... workSpecs) {
                for (WorkSpec workSpec : workSpecs) {
                    mScheduled.add(workSpec.id);
                    // Recorded as SystemJobScheduler records a job, which is what the startup check
                    // compares with the jobs JobScheduler still holds
                    WorkGenerationalId id = WorkSpecKt.generationalId(workSpec);
                    if (db.systemIdInfoDao().getSystemIdInfo(id) == null) {
                        db.systemIdInfoDao().insertSystemIdInfo(
                                new SystemIdInfo(id.getWorkSpecId(), id.getGeneration(), mScheduled.size()));
                    }
                }
            }

            @Override
            public void cancel(@NonNull String workSpecId) {
            }

            @Override
            public boolean hasLimitedSchedulingSlots() {
                return true;
            }
        };
        Trackers trackers = new Trackers(mContext, taskExecutor);
        Processor processor = new Processor(mContext, configuration, taskExecutor, mDb);
        mWm = WorkManagerImplExtKt.createWorkManager(mContext, configuration, taskExecutor, mDb, trackers, processor,
                WorkManagerImplExtKt.schedulers(recording));
        WorkManagerImpl.setDelegate(mWm);
        drain();
    }

    /** What JobScheduler does when it starts the job. */
    private void runNow(@NonNull WorkSpec spec) throws InterruptedException {
        mWm.getProcessor().startWork(new StartStopToken(WorkSpecKt.generationalId(spec)));
        drain();
    }

    @NonNull
    private WorkSpec backupSpec() {
        List<String> ids = mDb.workNameDao().getWorkSpecIdsWithName(AutoBackupScheduler.UNIQUE_WORK_NAME);
        assertEquals(1, ids.size());
        return mDb.workSpecDao().getWorkSpec(ids.get(0));
    }

    @NonNull
    private WorkSpec catchUpSpec() {
        List<String> ids = mDb.workNameDao().getWorkSpecIdsWithName(AutoBackupScheduler.CATCH_UP_WORK_NAME);
        assertEquals(1, ids.size());
        return mDb.workSpecDao().getWorkSpec(ids.get(0));
    }

    @NonNull
    private WorkSpec manualSpec() {
        List<String> ids = mDb.workNameDao().getWorkSpecIdsWithName(AutoBackupScheduler.MANUAL_WORK_NAME);
        assertEquals(1, ids.size());
        return mDb.workSpecDao().getWorkSpec(ids.get(0));
    }

    private void drain() throws InterruptedException {
        mExecutor.drain();
    }

    private void setNetwork(@Nullable NetworkInfo info) {
        shadowOf(mCm).setActiveNetworkInfo(info);
    }

    @NonNull
    private static NetworkInfo blocked() {
        return WorkManagerBlockedNetworkReproTest.blocked();
    }

    @SuppressWarnings("deprecation")
    @NonNull
    private static NetworkInfo connected() {
        return ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI,
                0, true, NetworkInfo.State.CONNECTED);
    }

    /** Robolectric reports a mobile network as the metered one. */
    @SuppressWarnings("deprecation")
    @NonNull
    private static NetworkInfo mobile() {
        return ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_MOBILE,
                0, true, NetworkInfo.State.CONNECTED);
    }

    /** A build that refuses to say what the network is. */
    @Implements(ConnectivityManager.class)
    public static class RefusingConnectivityManager extends ShadowConnectivityManager {
        @Implementation
        @Override
        protected NetworkInfo getActiveNetworkInfo() {
            throw new SecurityException("Refused");
        }
    }

    /**
     * Runs WorkManager's tasks on the test thread. Coroutine timers post from their own thread, so
     * the queue waits a moment before it calls itself empty. Workers get a thread of their own, as
     * WorkManager gives them, since a worker can wait on WorkManager's own queries.
     */
    private static final class QueueExecutor implements Executor {
        private final LinkedBlockingQueue<Runnable> mTasks = new LinkedBlockingQueue<>();
        private final ExecutorService mWorkerThread = Executors.newSingleThreadExecutor();
        private final AtomicInteger mWorkersRunning = new AtomicInteger();
        final Executor workers = command -> {
            mWorkersRunning.incrementAndGet();
            mWorkerThread.execute(() -> {
                try {
                    command.run();
                } finally {
                    mWorkersRunning.decrementAndGet();
                }
            });
        };

        @Override
        public void execute(@NonNull Runnable command) {
            mTasks.add(command);
        }

        void drain() throws InterruptedException {
            while (true) {
                shadowOf(Looper.getMainLooper()).idle();
                Runnable task = mTasks.poll(100, TimeUnit.MILLISECONDS);
                if (task == null) {
                    // A worker queues what it does before it counts itself done
                    if (mWorkersRunning.get() > 0 || !mTasks.isEmpty()) {
                        continue;
                    }
                    return;
                }
                task.run();
            }
        }

        void shutdown() {
            mWorkerThread.shutdownNow();
        }
    }

    private static final class CountingWorkerFactory extends WorkerFactory {
        private final AtomicInteger mBackups;
        private final AtomicReference<ListenableWorker.Result> mResult;

        CountingWorkerFactory(@NonNull AtomicInteger backups, @NonNull AtomicReference<ListenableWorker.Result> result) {
            mBackups = backups;
            mResult = result;
        }

        @Nullable
        @Override
        public ListenableWorker createWorker(@NonNull Context appContext, @NonNull String workerClassName,
                                             @NonNull WorkerParameters workerParameters) {
            if (!AutoBackupWorker.class.getName().equals(workerClassName)) {
                return null;
            }
            return new AutoBackupWorker(appContext, workerParameters) {
                @NonNull
                @Override
                Result runBackup(@NonNull Context context, boolean manual, @NonNull String origin) {
                    mBackups.incrementAndGet();
                    return mResult.get();
                }
            };
        }
    }
}
