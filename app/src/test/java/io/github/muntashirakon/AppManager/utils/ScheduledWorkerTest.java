// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.impl.Processor;
import androidx.work.impl.Scheduler;
import androidx.work.impl.StartStopToken;
import androidx.work.impl.WorkDatabase;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.impl.WorkManagerImplExtKt;
import androidx.work.impl.constraints.trackers.Trackers;
import androidx.work.impl.model.WorkSpec;
import androidx.work.impl.model.WorkSpecKt;
import androidx.work.impl.utils.SerialExecutorImpl;
import androidx.work.impl.utils.taskexecutor.SerialExecutor;
import androidx.work.impl.utils.taskexecutor.TaskExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.backup.schedule.AutoBackupWorker;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryPruneWorker;
import io.github.muntashirakon.AppManager.permission.watcher.PermissionWatchWorker;
import io.github.muntashirakon.AppManager.profiles.trigger.RoutineWorker;
import io.github.muntashirakon.AppManager.self.PackageEventSyncWorker;

/**
 * Runs periodic work through WorkManager 2.10.5 itself. Each periodic worker is stood in for by a subclass whose run
 * throws, so what's tested is the {@code doWork} the worker inherits.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ScheduledWorkerTest {
    private static final long ONE_DAY = TimeUnit.DAYS.toMillis(1);
    private static final String DB_NAME = "scheduled-worker-test.db";
    private static final AtomicInteger sRuns = new AtomicInteger();
    private static final AtomicReference<Throwable> sThrown = new AtomicReference<>();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final AtomicLong mNow = new AtomicLong(System.currentTimeMillis());
    private QueueExecutor mExecutor;
    private WorkDatabase mDb;
    private WorkManagerImpl mWm;

    @Before
    public void setUp() throws InterruptedException {
        sRuns.set(0);
        sThrown.set(null);
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
                .build();
        // Built here rather than by WorkManager so the test thread can query it
        mDb =Room.databaseBuilder(mContext, WorkDatabase.class, DB_NAME).allowMainThreadQueries().build();
        Scheduler none = new Scheduler() {
            @Override
            public void schedule(@NonNull WorkSpec... workSpecs) {
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
                WorkManagerImplExtKt.schedulers(none));
        WorkManagerImpl.setDelegate(mWm);
        mExecutor.drain();
    }

    @After
    public void tearDown() {
        WorkManagerImpl.setDelegate(null);
        mExecutor.shutdown();
        mDb.close();
        mContext.deleteDatabase(DB_NAME);
    }

    @Test
    public void onThisWorkManagerAPeriodicWorkerThatThrowsNeverRunsAgain() throws InterruptedException {
        WorkSpec spec = enqueueDaily(ThrowingPlainWorker.class);
        mNow.set(spec.calculateNextRunTime());
        runNow(spec);
        assertEquals(1, sRuns.get());
        assertEquals(WorkInfo.State.FAILED, specOf(spec).state);
    }

    @Test
    public void theScheduledBackupKeepsItsScheduleAfterAnError() throws InterruptedException {
        assertKeepsItsSchedule(ThrowingAutoBackup.class);
    }

    @Test
    public void theHistoryPruneKeepsItsScheduleAfterAnError() throws InterruptedException {
        assertKeepsItsSchedule(ThrowingOpHistoryPrune.class);
    }

    @Test
    public void thePermissionWatchKeepsItsScheduleAfterAnError() throws InterruptedException {
        assertKeepsItsSchedule(ThrowingPermissionWatch.class);
    }

    @Test
    public void aRoutineKeepsItsScheduleAfterAnError() throws InterruptedException {
        assertKeepsItsSchedule(ThrowingRoutine.class);
    }

    @Test
    public void theAppChangeSyncKeepsItsScheduleAfterAnException() throws InterruptedException {
        assertKeepsItsSchedule(ThrowingPackageEventSync.class);
    }

    private void assertKeepsItsSchedule(@NonNull Class<? extends ScheduledWorker> workerClass)
            throws InterruptedException {
        WorkSpec spec = enqueueDaily(workerClass);
        long slot = spec.calculateNextRunTime();
        mNow.set(slot);
        runNow(spec);
        assertEquals(1, sRuns.get());
        WorkSpec after = specOf(spec);
        assertEquals(WorkInfo.State.ENQUEUED, after.state);
        assertTrue(after.isPeriodic());
        assertEquals(ONE_DAY, after.intervalDuration);
        assertEquals("the next run keeps its time", slot + ONE_DAY, after.calculateNextRunTime());
        boolean logged = false;
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag(workerClass.getSimpleName())) {
            if (item.type == Log.ERROR && item.throwable == sThrown.get()) {
                logged = true;
            }
        }
        assertTrue("the error is logged under the worker's name", logged);

        mNow.set(slot + ONE_DAY);
        runNow(after);
        assertEquals("it runs again the next day", 2, sRuns.get());
        assertEquals(WorkInfo.State.ENQUEUED, specOf(spec).state);
    }

    @NonNull
    private WorkSpec enqueueDaily(@NonNull Class<? extends ListenableWorker> workerClass)
            throws InterruptedException {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(workerClass, 1, TimeUnit.DAYS).build();
        mWm.enqueue(request);
        mExecutor.drain();
        WorkSpec spec = mDb.workSpecDao().getWorkSpec(request.getStringId());
        assertSame(WorkInfo.State.ENQUEUED, spec.state);
        return spec;
    }

    @NonNull
    private WorkSpec specOf(@NonNull WorkSpec spec) {
        return mDb.workSpecDao().getWorkSpec(spec.id);
    }

    /** What JobScheduler does when it starts the job. */
    private void runNow(@NonNull WorkSpec spec) throws InterruptedException {
        mWm.getProcessor().startWork(new StartStopToken(WorkSpecKt.generationalId(spec)));
        mExecutor.drain();
    }

    @NonNull
    private static RuntimeException countAndThrow(@NonNull Throwable th) {
        sRuns.incrementAndGet();
        sThrown.set(th);
        if (th instanceof Error) {
            throw (Error) th;
        }
        return (RuntimeException) th;
    }

    /** The worker as WorkManager sees it without the base class. */
    public static class ThrowingPlainWorker extends Worker {
        public ThrowingPlainWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            throw countAndThrow(new OutOfMemoryError("Test"));
        }
    }

    public static class ThrowingAutoBackup extends AutoBackupWorker {
        public ThrowingAutoBackup(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        protected Result doScheduledWork() {
            throw countAndThrow(new OutOfMemoryError("Test"));
        }
    }

    public static class ThrowingOpHistoryPrune extends OpHistoryPruneWorker {
        public ThrowingOpHistoryPrune(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        protected Result doScheduledWork() {
            throw countAndThrow(new NoClassDefFoundError("Test"));
        }
    }

    public static class ThrowingPermissionWatch extends PermissionWatchWorker {
        public ThrowingPermissionWatch(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        protected Result doScheduledWork() {
            throw countAndThrow(new LinkageError("Test"));
        }
    }

    public static class ThrowingRoutine extends RoutineWorker {
        public ThrowingRoutine(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        protected Result doScheduledWork() {
            throw countAndThrow(new StackOverflowError("Test"));
        }
    }

    public static class ThrowingPackageEventSync extends PackageEventSyncWorker {
        public ThrowingPackageEventSync(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        protected Result doScheduledWork() {
            throw countAndThrow(new IllegalStateException("Test"));
        }
    }

    /**
     * Runs WorkManager's tasks on the test thread. Coroutine timers post from their own thread, so the queue waits a
     * moment before it calls itself empty. Workers get a thread of their own, as WorkManager gives them.
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
}
