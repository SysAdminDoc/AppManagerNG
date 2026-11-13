// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.accessibility.topapp;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * The saved list of what the top-app bubble saw, one {@link TopAppTransition} per line in a private file. Every
 * read and write runs on one worker thread, so they never overlap and need no locks.
 */
public final class TopAppHistory {
    private static final String TAG = TopAppHistory.class.getSimpleName();
    @VisibleForTesting
    static final String FILE_NAME = "top_app_history.tsv";
    public static final int MAX_ENTRIES = 1_000;
    /** How far past the limit the file grows before it's cut back, so it isn't rewritten on every change. */
    @VisibleForTesting
    static final int TRIM_SLACK = 200;

    @Nullable
    private static volatile TopAppHistory sInstance;

    @NonNull
    public static TopAppHistory getInstance(@NonNull Context context) {
        TopAppHistory instance = sInstance;
        if (instance == null) {
            synchronized (TopAppHistory.class) {
                instance = sInstance;
                if (instance == null) {
                    ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>());
                    worker.allowCoreThreadTimeOut(true);
                    instance = new TopAppHistory(new File(context.getApplicationContext().getFilesDir(), FILE_NAME),
                            worker);
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    @VisibleForTesting
    static void setInstanceForTesting(@Nullable TopAppHistory instance) {
        sInstance = instance;
    }

    @NonNull
    private final File mFile;
    @NonNull
    private final Executor mWorker;
    private final MutableLiveData<Long> mChanges = new MutableLiveData<>();
    /** Worker only. Lines in the file, or -1 until they've been counted. */
    private int mLineCount = -1;
    /** Worker only. */
    private long mChangeCount;

    @VisibleForTesting
    TopAppHistory(@NonNull File file, @NonNull Executor worker) {
        mFile = file;
        mWorker = worker;
    }

    /** Changes each time the saved list does. */
    @NonNull
    public LiveData<Long> getChanges() {
        return mChanges;
    }

    @AnyThread
    public void record(@NonNull List<TopAppTransition> transitions) {
        if (transitions.isEmpty()) {
            return;
        }
        List<TopAppTransition> copy = new ArrayList<>(transitions);
        mWorker.execute(() -> {
            try {
                appendNow(copy);
                announceChange();
            } catch (IOException e) {
                Log.w(TAG, "Could not save the top app history.", e);
            }
        });
    }

    /** @param onRead Gets everything saved, newest first. Called on the worker. */
    @AnyThread
    public void read(@NonNull Consumer<List<TopAppTransition>> onRead) {
        mWorker.execute(() -> onRead.accept(readNewestFirstNow()));
    }

    /**
     * Removes everything, including changes still waiting to be saved when this was called.
     *
     * @param onDone Gets what was removed, newest first, or {@code null} if it failed. Called on the worker.
     */
    @AnyThread
    public void clear(@NonNull Consumer<List<TopAppTransition>> onDone) {
        mWorker.execute(() -> {
            List<TopAppTransition> removed = readNewestFirstNow();
            if (mFile.exists() && !mFile.delete()) {
                Log.w(TAG, "Could not clear the top app history.");
                onDone.accept(null);
                return;
            }
            mLineCount = 0;
            announceChange();
            onDone.accept(removed);
        });
    }

    /** Puts back what {@link #clear(Consumer)} removed, around anything saved since. The limit still applies. */
    @AnyThread
    public void restore(@NonNull List<TopAppTransition> removedNewestFirst) {
        List<TopAppTransition> removed = new ArrayList<>(removedNewestFirst);
        mWorker.execute(() -> {
            List<TopAppTransition> merged = new ArrayList<>(removed);
            Collections.reverse(merged);
            merged.addAll(readOldestFirstNow());
            // Stable, so changes from the same moment keep their order.
            Collections.sort(merged, (a, b) -> Long.compare(a.timestampMillis, b.timestampMillis));
            try {
                rewriteNow(newest(merged, MAX_ENTRIES));
                announceChange();
            } catch (IOException e) {
                Log.w(TAG, "Could not restore the top app history.", e);
            }
        });
    }

    @WorkerThread
    private void appendNow(@NonNull List<TopAppTransition> transitions) throws IOException {
        if (mLineCount < 0) {
            mLineCount = readOldestFirstNow().size();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(mFile, true), StandardCharsets.UTF_8)) {
            for (TopAppTransition transition : transitions) {
                writer.write(transition.toLine());
                writer.write('\n');
            }
        }
        mLineCount += transitions.size();
        if (mLineCount > MAX_ENTRIES + TRIM_SLACK) {
            rewriteNow(newest(readOldestFirstNow(), MAX_ENTRIES));
        }
    }

    /** Written beside the file and then moved over it, so a crash halfway leaves the old list. */
    @WorkerThread
    private void rewriteNow(@NonNull List<TopAppTransition> oldestFirst) throws IOException {
        File temp = new File(mFile.getPath() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            for (TopAppTransition transition : oldestFirst) {
                writer.write(transition.toLine());
                writer.write('\n');
            }
        }
        if (!temp.renameTo(mFile)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Could not replace " + mFile);
        }
        mLineCount = oldestFirst.size();
    }

    @WorkerThread
    @NonNull
    private List<TopAppTransition> readNewestFirstNow() {
        List<TopAppTransition> transitions = readOldestFirstNow();
        Collections.reverse(transitions);
        return transitions;
    }

    @WorkerThread
    @NonNull
    private List<TopAppTransition> readOldestFirstNow() {
        List<TopAppTransition> transitions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(mFile),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                TopAppTransition transition = TopAppTransition.parse(line);
                if (transition != null) {
                    transitions.add(transition);
                }
            }
        } catch (FileNotFoundException e) {
            // Nothing recorded yet.
        } catch (IOException e) {
            Log.w(TAG, "Could not read the top app history.", e);
        }
        return transitions;
    }

    @NonNull
    private static List<TopAppTransition> newest(@NonNull List<TopAppTransition> oldestFirst, int limit) {
        int size = oldestFirst.size();
        return size <= limit ? oldestFirst : new ArrayList<>(oldestFirst.subList(size - limit, size));
    }

    private void announceChange() {
        mChanges.postValue(++mChangeCount);
    }
}
