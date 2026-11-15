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
import org.robolectric.shadows.ShadowNetworkInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    public void aScheduledBackupWaitsForAUsableNetworkAndThenRunsOnce() throws Exception {
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        WorkSpec spec = backupSpec();
        assertEquals(NetworkType.CONNECTED, spec.constraints.getRequiredNetworkType());
        long lastRun = Prefs.BackupRestore.getScheduledBackupLastRun();

        // The job fires because WorkManager thinks the constraint is met.
        mNow.set(spec.calculateNextRunTime());
        runNow(spec);
        spec = backupSpec();
        assertEquals("no backup without a usable network", 0, mBackups.get());
        assertEquals(WorkInfo.State.ENQUEUED, spec.state);
        assertTrue(spec.isPeriodic());
        assertEquals(ONE_DAY, spec.intervalDuration);
        assertEquals("it waits as a retry", 1, spec.runAttemptCount);
        assertEquals(0, spec.getPeriodCount());
        assertEquals("a wait isn't recorded as a run", lastRun, Prefs.BackupRestore.getScheduledBackupLastRun());

        setNetwork(connected());
        mNow.set(spec.calculateNextRunTime());
        runNow(spec);
        spec = backupSpec();
        assertEquals(1, mBackups.get());
        assertEquals(WorkInfo.State.ENQUEUED, spec.state);
        assertEquals(0, spec.runAttemptCount);
        assertEquals(1, spec.getPeriodCount());
        assertEquals("the next run is a full period away", mNow.get() + ONE_DAY, spec.calculateNextRunTime());

        // Starting it again before that period ends doesn't back up again.
        runNow(spec);
        assertEquals(1, mBackups.get());
        assertEquals(1, backupSpec().getPeriodCount());
    }

    @Test
    @Config(sdk = 35)
    public void aWaitingBackupKeepsItsPeriodAcrossProcessDeathAndReboot() throws Exception {
        setNetwork(blocked());
        startProcess();
        AutoBackupScheduler.schedule(mContext);
        drain();
        WorkSpec spec = backupSpec();
        mNow.set(spec.calculateNextRunTime());
        runNow(spec);
        WorkSpec waiting = backupSpec();
        assertEquals(1, waiting.runAttemptCount);

        // The process dies. Nothing gets to clean up, and the next one opens the same database.
        mDb.close();
        startProcess();
        WorkSpec afterDeath = backupSpec();
        assertEquals(waiting.id, afterDeath.id);
        assertEquals(WorkInfo.State.ENQUEUED, afterDeath.state);
        assertTrue(afterDeath.isPeriodic());
        assertEquals(ONE_DAY, afterDeath.intervalDuration);
        assertEquals(1, afterDeath.runAttemptCount);
        assertEquals(waiting.calculateNextRunTime(), afterDeath.calculateNextRunTime());

        // A reboot leaves JobScheduler without the job. The startup check WorkManager runs after
        // one finds it missing and hands the work back to its scheduler.
        mScheduled.clear();
        new ForceStopRunnable(mContext, mWm).forceStopRunnable();
        drain();
        assertTrue("rescheduled after the reboot", mScheduled.contains(afterDeath.id));
        WorkSpec afterReboot = backupSpec();
        assertEquals(ONE_DAY, afterReboot.intervalDuration);
        assertEquals(waiting.calculateNextRunTime(), afterReboot.calculateNextRunTime());

        setNetwork(connected());
        mNow.set(afterReboot.calculateNextRunTime());
        runNow(afterReboot);
        assertEquals(1, mBackups.get());
        assertEquals(1, backupSpec().getPeriodCount());
    }

    @Test
    @Config(sdk = 23)
    public void belowAndroid15TheScheduledBackupStillGoesByTheConstraint() throws Exception {
        setNetwork(blocked());
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
        assertEquals("the run goes ahead as it did before", 1, mBackups.get());
        assertEquals(1, backupSpec().getPeriodCount());
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
                .setExecutor(executor)
                .setTaskExecutor(executor)
                .setClock(mNow::get)
                .setWorkerFactory(new CountingWorkerFactory(mBackups))
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

    /**
     * Runs WorkManager's tasks on the test thread. Coroutine timers post from their own thread, so
     * the queue waits a moment before it calls itself empty.
     */
    private static final class QueueExecutor implements Executor {
        private final LinkedBlockingQueue<Runnable> mTasks = new LinkedBlockingQueue<>();

        @Override
        public void execute(@NonNull Runnable command) {
            mTasks.add(command);
        }

        void drain() throws InterruptedException {
            while (true) {
                shadowOf(Looper.getMainLooper()).idle();
                Runnable task = mTasks.poll(100, TimeUnit.MILLISECONDS);
                if (task == null) {
                    return;
                }
                task.run();
            }
        }
    }

    private static final class CountingWorkerFactory extends WorkerFactory {
        private final AtomicInteger mBackups;

        CountingWorkerFactory(@NonNull AtomicInteger backups) {
            mBackups = backups;
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
                    return Result.success();
                }
            };
        }
    }
}
