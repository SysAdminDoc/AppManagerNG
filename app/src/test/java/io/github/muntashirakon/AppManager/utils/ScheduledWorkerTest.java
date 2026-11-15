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
import androidx.work.WorkQuery;
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.backup.schedule.AutoBackupScheduler;
import io.github.muntashirakon.AppManager.backup.schedule.AutoBackupWorker;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryPruneScheduler;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryPruneWorker;
import io.github.muntashirakon.AppManager.permission.watcher.PermissionWatchScheduler;
import io.github.muntashirakon.AppManager.permission.watcher.PermissionWatchWorker;
import io.github.muntashirakon.AppManager.profiles.trigger.ProfileTrigger;
import io.github.muntashirakon.AppManager.profiles.trigger.ProfileTriggerStore;
import io.github.muntashirakon.AppManager.profiles.trigger.RoutineScheduler;
import io.github.muntashirakon.AppManager.profiles.trigger.RoutineWorker;
import io.github.muntashirakon.AppManager.self.FailedScheduleRevival;
import io.github.muntashirakon.AppManager.self.PackageEventSyncWorker;
import io.github.muntashirakon.AppManager.settings.Prefs;

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

    @Test
    public void aWorkerWhoseErrorCantBeLoggedStillKeepsItsSchedule() throws InterruptedException {
        PrintStream stream = ShadowLog.stream;
        // Prints each logged throwable as android.util.Log does on a device, so the log call reads the broken message
        ShadowLog.stream = new PrintStream(new ByteArrayOutputStream());
        try {
            WorkSpec spec = enqueueDaily(UnloggableErrorWorker.class);
            long slot = spec.calculateNextRunTime();
            mNow.set(slot);
            runNow(spec);
            assertEquals(1, sRuns.get());
            assertEquals(WorkInfo.State.ENQUEUED, specOf(spec).state);
            boolean noted = false;
            for (ShadowLog.LogItem item : ShadowLog.getLogsForTag(UnloggableErrorWorker.class.getSimpleName())) {
                if (item.type == Log.ERROR && item.msg.contains("couldn't be logged")) {
                    noted = true;
                }
            }
            assertTrue("the stop is still noted", noted);

            mNow.set(slot + ONE_DAY);
            runNow(specOf(spec));
            assertEquals("it runs again the next day", 2, sRuns.get());
            assertEquals(WorkInfo.State.ENQUEUED, specOf(spec).state);
        } finally {
            ShadowLog.stream = stream;
        }
    }

    @Test
    public void aScheduledBackupThatFailedBeforeComesBackAtStart() throws Exception {
        boolean enabled = Prefs.BackupRestore.isScheduledAutoBackupEnabled();
        Prefs.BackupRestore.setScheduledAutoBackupEnabled(true);
        try {
            assertRevivedAtStart(AutoBackupScheduler.WORK_TAG, () -> AutoBackupScheduler.schedule(mContext),
                    AutoBackupWorker.class);
        } finally {
            Prefs.BackupRestore.setScheduledAutoBackupEnabled(enabled);
        }
    }

    @Test
    public void aHistoryPruneThatFailedBeforeComesBackAtStart() throws Exception {
        int days = Prefs.Privacy.getOpHistoryRetentionDays();
        Prefs.Privacy.setOpHistoryRetentionDays(30);
        try {
            assertRevivedAtStart(OpHistoryPruneScheduler.WORK_TAG, () -> OpHistoryPruneScheduler.schedule(mContext),
                    OpHistoryPruneWorker.class);
        } finally {
            Prefs.Privacy.setOpHistoryRetentionDays(days);
        }
    }

    @Test
    public void aPermissionWatchThatFailedBeforeComesBackAtStart() throws Exception {
        int minutes = Prefs.Privacy.getPermissionWatcherIntervalMinutes();
        Prefs.Privacy.setPermissionWatcherIntervalMinutes(60);
        try {
            assertRevivedAtStart(PermissionWatchScheduler.WORK_TAG,
                    () -> PermissionWatchScheduler.scheduleOrCancel(mContext), PermissionWatchWorker.class);
        } finally {
            Prefs.Privacy.setPermissionWatcherIntervalMinutes(minutes);
        }
    }

    @Test
    public void aRoutineThatFailedBeforeComesBackAtStart() throws Exception {
        ProfileTriggerStore store = new ProfileTriggerStore(mContext);
        ProfileTrigger trigger = new ProfileTrigger.Builder("profile-1", ProfileTrigger.TYPE_TIME_OF_DAY)
                .id("Revived-Routine")
                .enabled(true)
                .timeOfDay(3, 0)
                .build();
        store.put(trigger);
        try {
            assertRevivedAtStart(RoutineScheduler.WORK_TAG, () -> RoutineScheduler.schedule(mContext, trigger),
                    RoutineWorker.class);
        } finally {
            store.remove(trigger.id);
        }
    }

    @Test
    public void aScheduleThatsOffStaysStoppedAtStart() throws Exception {
        boolean enabled = Prefs.BackupRestore.isScheduledAutoBackupEnabled();
        Prefs.BackupRestore.setScheduledAutoBackupEnabled(true);
        try {
            AutoBackupScheduler.schedule(mContext);
            mExecutor.drain();
            WorkInfo scheduled = onlyPeriodic(AutoBackupScheduler.WORK_TAG);
            mDb.workSpecDao().setState(WorkInfo.State.FAILED, scheduled.getId().toString());
            Prefs.BackupRestore.setScheduledAutoBackupEnabled(false);

            reviveAtStart();

            assertEquals(WorkInfo.State.FAILED, onlyPeriodic(AutoBackupScheduler.WORK_TAG).getState());
        } finally {
            Prefs.BackupRestore.setScheduledAutoBackupEnabled(enabled);
        }
    }

    /**
     * Stands in for a schedule that ended FAILED before {@link ScheduledWorker}: saving it again leaves it stopped,
     * and the pass at start puts it back.
     */
    private void assertRevivedAtStart(@NonNull String tag, @NonNull Runnable schedule,
                                      @NonNull Class<? extends ListenableWorker> workerClass) throws Exception {
        schedule.run();
        mExecutor.drain();
        WorkInfo scheduled = onlyPeriodic(tag);
        assertEquals(WorkInfo.State.ENQUEUED, scheduled.getState());
        mDb.workSpecDao().setState(WorkInfo.State.FAILED, scheduled.getId().toString());

        // What Settings and every start do, and what kept the schedule stopped
        schedule.run();
        mExecutor.drain();
        assertEquals(WorkInfo.State.FAILED, onlyPeriodic(tag).getState());

        reviveAtStart();

        WorkInfo revived = onlyPeriodic(tag);
        assertEquals(WorkInfo.State.ENQUEUED, revived.getState());
        assertEquals(workerClass.getName(), mDb.workSpecDao().getWorkSpec(revived.getId().toString()).workerClassName);
    }

    private void reviveAtStart() throws Exception {
        Future<List<WorkInfo>> failed = mWm.getWorkInfos(WorkQuery.fromStates(WorkInfo.State.FAILED));
        mExecutor.drain();
        FailedScheduleRevival.revive(mContext, failed.get());
        mExecutor.drain();
    }

    @NonNull
    private WorkInfo onlyPeriodic(@NonNull String tag) throws Exception {
        Future<List<WorkInfo>> infos = mWm.getWorkInfosByTag(tag);
        mExecutor.drain();
        List<WorkInfo> periodic = new ArrayList<>();
        for (WorkInfo info : infos.get()) {
            if (info.getPeriodicityInfo() != null) {
                periodic.add(info);
            }
        }
        assertEquals("one schedule under " + tag, 1, periodic.size());
        return periodic.get(0);
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

    public static class UnloggableErrorWorker extends ScheduledWorker {
        public UnloggableErrorWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        protected Result doScheduledWork() {
            throw countAndThrow(new UnprintableError());
        }
    }

    /** An error whose message can't be read, so printing its stack trace throws. */
    private static final class UnprintableError extends Error {
        @Override
        public String getMessage() {
            throw new IllegalStateException("No message");
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
