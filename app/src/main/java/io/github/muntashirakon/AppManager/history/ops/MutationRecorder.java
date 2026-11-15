// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.history.ops;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.github.muntashirakon.AppManager.db.entity.MutationImage;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * Reads a target's state before and after a write and stores both with the value asked for. Recording never stops
 * the write: a failure to read leaves the value null, and a failure to store is logged.
 */
@WorkerThread
public final class MutationRecorder {
    public static final String TAG = MutationRecorder.class.getSimpleName();

    /** Where finished images go. */
    public interface Sink {
        void append(@NonNull MutationImage image);
    }

    /** One write in progress. */
    public final class Pending {
        @NonNull
        private final MutationImage mImage;
        private boolean mFinished;

        Pending(@NonNull MutationImage image) {
            mImage = image;
        }

        /** Reads the value back and stores the image. Safe to call more than once; only the first call counts. */
        public void finish(boolean success) {
            if (mFinished) {
                return;
            }
            mFinished = true;
            try {
                mImage.postValue = read(mImage.kind, mImage.target, mImage.packageName, mImage.userId);
                mImage.result = resultOf(success, mImage.kind, mImage.requestedValue, mImage.postValue);
                mImage.time = System.currentTimeMillis();
                mSink.append(mImage);
            } catch (Throwable th) {
                Log.w(TAG, "Could not record the change to " + mImage.kind + " " + mImage.target + " of "
                        + mImage.packageName, th);
            }
        }
    }

    @NonNull
    private final String mExecutionId;
    @NonNull
    private final String mOperation;
    @NonNull
    private final MutationState.Reader mReader;
    @NonNull
    private final Sink mSink;
    @Nullable
    private String mExecutor;

    @VisibleForTesting
    public MutationRecorder(@NonNull String executionId, @NonNull String operation, @Nullable String executor,
                            @NonNull MutationState.Reader reader, @NonNull Sink sink) {
        mExecutionId = executionId;
        mOperation = operation;
        mExecutor = executor;
        mReader = reader;
        mSink = sink;
    }

    /** A recorder that reads the system and writes the journal. */
    @NonNull
    public static MutationRecorder forSystem(@NonNull String executionId, @NonNull String operation) {
        return new MutationRecorder(executionId, operation, null, MutationState.SYSTEM,
                image -> MutationJournal.getInstance().append(image));
    }

    @NonNull
    public static String newExecutionId() {
        return UUID.randomUUID().toString();
    }

    @NonNull
    public static String batchOperation(int op) {
        return "batch:" + op;
    }

    @NonNull
    public static String singleOperation(@NonNull String action) {
        return "single:" + action;
    }

    @NonNull
    public String getExecutionId() {
        return mExecutionId;
    }

    /** Reads the value before the write. Call {@link Pending#finish} once the write is done or has failed. */
    @NonNull
    public Pending begin(@NonNull String packageName, int userId, @NonNull String kind, @NonNull String target,
                         @NonNull String requestedValue) {
        MutationImage image = new MutationImage();
        image.executionId = mExecutionId;
        image.executor = executor();
        image.operation = mOperation;
        image.packageName = packageName;
        image.userId = userId;
        image.kind = kind;
        image.target = target;
        image.requestedValue = requestedValue;
        image.beforeValue = read(kind, target, packageName, userId);
        return new Pending(image);
    }

    /** {@link #begin} for several targets of one kind that are written together. */
    @NonNull
    public List<Pending> beginAll(@NonNull String packageName, int userId, @NonNull String kind,
                                  @NonNull List<String> targets, @NonNull String requestedValue) {
        List<Pending> pending = new ArrayList<>(targets.size());
        for (String target : targets) {
            pending.add(begin(packageName, userId, kind, target, requestedValue));
        }
        return pending;
    }

    public static void finishAll(@NonNull List<Pending> pending, boolean success) {
        for (Pending p : pending) {
            p.finish(success);
        }
    }

    /** A state write that can fail. */
    public interface Write<E extends Exception> {
        void run() throws E;
    }

    /**
     * Runs {@code write} and finishes every image begun for it: as written when it returns, as failed when it
     * throws, which it still does.
     */
    public static <E extends Exception> void around(@NonNull List<Pending> pending, @NonNull Write<E> write)
            throws E {
        boolean written = false;
        try {
            write.run();
            written = true;
        } finally {
            finishAll(pending, written);
        }
    }

    @NonNull
    public static List<String> targetsOf(@NonNull int[] values) {
        List<String> targets = new ArrayList<>(values.length);
        for (int value : values) {
            targets.add(String.valueOf(value));
        }
        return targets;
    }

    @NonNull
    public static List<String> targetsOf(@NonNull String[] values) {
        return Arrays.asList(values);
    }

    @Nullable
    private String read(@NonNull String kind, @NonNull String target, @NonNull String packageName, int userId) {
        try {
            return mReader.read(kind, target, packageName, userId);
        } catch (Throwable th) {
            return null;
        }
    }

    @NonNull
    private String executor() {
        if (mExecutor == null) {
            try {
                mExecutor = Ops.getInferredMode(ContextUtils.getContext()).toString();
            } catch (Throwable th) {
                mExecutor = "";
            }
        }
        return mExecutor;
    }

    @VisibleForTesting
    @NonNull
    static String resultOf(boolean success, @NonNull String kind, @NonNull String requested, @Nullable String post) {
        if (!success) {
            return MutationJournal.RESULT_FAILED;
        }
        if (post == null) {
            return MutationJournal.RESULT_UNCONFIRMED;
        }
        if (post.equals(requested)) {
            return MutationJournal.RESULT_APPLIED;
        }
        if (MutationState.KIND_FREEZE.equals(kind) && !MutationState.UNFROZEN.equals(requested)) {
            // A freeze asked for one method; another one already holding the app doesn't undo it
            return Arrays.asList(post.split("\\+")).contains(requested)
                    ? MutationJournal.RESULT_APPLIED : MutationJournal.RESULT_UNCONFIRMED;
        }
        return MutationJournal.RESULT_UNCONFIRMED;
    }
}
