// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.logcat.helper.BuildHelper;
import io.github.muntashirakon.AppManager.logcat.helper.LogcatStructuredExporter;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReader;
import io.github.muntashirakon.AppManager.logcat.reader.LogcatReaderLoader;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SavedLog;
import io.github.muntashirakon.AppManager.logcat.struct.SendLogDetails;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.self.filecache.FileCache;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

// Copyright 2022 Muntashir Al-Islam
public class LogViewerViewModel extends AndroidViewModel {
    public static final String TAG = LogViewerViewModel.class.getSimpleName();

    public interface LogLinesAvailableInterface {
        @UiThread
        void onNewLogsAvailable(@NonNull List<LogLine> logLines);

        @UiThread
        boolean isLogViewActive();
    }

    public static final class LogcatSession {
        @NonNull
        private final WeakReference<LogLinesAvailableInterface> mListener;
        private volatile boolean mActive = true;
        @Nullable
        private volatile LogcatReader mReader;
        @Nullable
        private volatile Future<?> mFuture;

        @VisibleForTesting
        LogcatSession(@NonNull LogLinesAvailableInterface listener) {
            mListener = new WeakReference<>(listener);
        }
    }

    private final Object mLock = new Object();
    private final Object mReaderSessionLock = new Object();

    private volatile boolean mPaused;
    private volatile boolean mCollapsedMode;
    private volatile int mLogLevel;
    @Nullable
    private volatile LogcatSession mReaderSession;

    private final Pattern mFilterPattern;
    private final MutableLiveData<Boolean> mExpandLogsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mLoggingFinishedLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mLoadingProgressLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mTruncatedLinesLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> mLogLevelLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<LogFilter>> mLogFiltersLiveData = new MutableLiveData<>();
    private final MutableLiveData<Path> mLogSavedLiveData = new MutableLiveData<>();
    private final MutableLiveData<SendLogDetails> mLogToBeSentLiveData = new MutableLiveData<>();
    private final MultithreadedExecutor mExecutor = MultithreadedExecutor.getNewInstance();

    public LogViewerViewModel(@NonNull Application application) {
        super(application);
        mFilterPattern = Prefs.LogViewer.getCompiledFilterPattern();
    }

    @Override
    protected void onCleared() {
        LogcatSession session;
        synchronized (mReaderSessionLock) {
            session = mReaderSession;
            mReaderSession = null;
        }
        invalidateSession(session);
        killSessionReader(session);
        mExecutor.shutdown();
        super.onCleared();
    }

    public LiveData<Boolean> observeLoggingFinished() {
        return mLoggingFinishedLiveData;
    }

    public LiveData<Integer> observeLoadingProgress() {
        return mLoadingProgressLiveData;
    }

    public LiveData<Integer> observeTruncatedLines() {
        return mTruncatedLinesLiveData;
    }

    public LiveData<List<LogFilter>> getLogFilters() {
        return mLogFiltersLiveData;
    }

    public LiveData<Path> observeLogSaved() {
        return mLogSavedLiveData;
    }

    public MutableLiveData<Integer> observeLogLevelLiveData() {
        return mLogLevelLiveData;
    }

    public LiveData<SendLogDetails> getLogsToBeSent() {
        return mLogToBeSentLiveData;
    }

    public LiveData<Boolean> getExpandLogsLiveData() {
        return mExpandLogsLiveData;
    }

    @AnyThread
    @NonNull
    public LogcatSession startLogcat(@NonNull LogLinesAvailableInterface logLinesAvailableInterface) {
        LogcatSession session = new LogcatSession(logLinesAvailableInterface);
        LogcatSession previous;
        synchronized (mReaderSessionLock) {
            previous = mReaderSession;
            mReaderSession = session;
        }
        invalidateSession(previous);
        killSessionReader(previous);
        session.mFuture = mExecutor.submit(() -> {
            try {
                LogcatReader reader = LogcatReaderLoader.create(true).loadReader();
                synchronized (session) {
                    if (!session.mActive) {
                        reader.killQuietly();
                        return;
                    }
                    session.mReader = reader;
                }

                int maxLines = Prefs.LogViewer.getDisplayLimit();

                String line;
                LinkedList<LogLine> initialLines = new LinkedList<>();
                while (session.mActive && !ThreadUtils.isInterrupted()) {
                    synchronized (session) {
                        reader = session.mReader;
                    }
                    if (reader == null) {
                        break;
                    }
                    try {
                        line = reader.readLine();
                    } catch (Exception e) {
                        if (continueWithReplacementReader(session, reader)) {
                            continue;
                        }
                        throw e;
                    }
                    if (line == null) {
                        if (continueWithReplacementReader(session, reader)) {
                            continue;
                        }
                        break;
                    }
                    if (mPaused) {
                        synchronized (mLock) {
                            if (mPaused && session.mActive) {
                                mLock.wait();
                            }
                        }
                    }
                    if (!session.mActive) {
                        break;
                    }
                    LogLine logLine = LogLine.newLogLine(line, !mCollapsedMode, mFilterPattern);
                    if (logLine == null) {
                        if (reader.readyToRecord()) {
                            // Logcat is ready
                        }
                    } else if (!reader.readyToRecord()) {
                        // "ready to record" in this case means all the initial lines have been flushed from the reader
                        initialLines.add(logLine);
                        if (initialLines.size() > maxLines) {
                            initialLines.removeFirst();
                        }
                    } else if (!initialLines.isEmpty()) {
                        // flush all the initial lines we've loaded
                        initialLines.add(logLine);
                        sendNewLogs(initialLines, session.mListener);
                        initialLines.clear();
                    } else {
                        // just proceed as normal
                        sendNewLogs(Collections.singletonList(logLine), session.mListener);
                    }
                }
            } catch (Exception e) {
                if (session.mActive) {
                    Log.e(TAG, e);
                }
            } finally {
                synchronized (session) {
                    session.mActive = false;
                    session.mListener.clear();
                }
                killSessionReader(session);
                boolean wasCurrent;
                synchronized (mReaderSessionLock) {
                    wasCurrent = mReaderSession == session;
                    if (wasCurrent) {
                        mReaderSession = null;
                    }
                }
                if (wasCurrent) {
                    mLoggingFinishedLiveData.postValue(true);
                }
            }
        });
        return session;
    }

    @AnyThread
    public void restartLogcat() {
        LogcatSession session = mReaderSession;
        if (session == null || !session.mActive) {
            return;
        }
        mExecutor.submit(() -> {
            synchronized (mLock) {
                // Pause -> reload reader -> resume
                mPaused = true;
                LogcatReader replacement = null;
                try {
                    replacement = LogcatReaderLoader.create(true).loadReader();
                    if (!session.mActive) {
                        replacement.killQuietly();
                        return;
                    }
                    LogcatReader previous;
                    synchronized (session) {
                        if (!session.mActive) {
                            replacement.killQuietly();
                            return;
                        }
                        previous = session.mReader;
                        session.mReader = replacement;
                    }
                    if (previous != null) {
                        previous.killQuietly();
                    }
                } catch (Exception e) {
                    // Errors do not matter
                    Log.e(TAG, e);
                } finally {
                    mPaused = false;
                    mLock.notifyAll();
                }
            }
        });
    }

    /**
     * Atomically decides whether an ended reader was replaced or owns the session shutdown.
     * Marking the session inactive under the same monitor prevents a concurrent restart from
     * installing a replacement after the read loop has already decided to exit.
     */
    @VisibleForTesting
    static boolean continueWithReplacementReader(@NonNull LogcatSession session,
                                                 @NonNull LogcatReader endedReader) {
        synchronized (session) {
            if (session.mActive && session.mReader != null && session.mReader != endedReader) {
                return true;
            }
            session.mActive = false;
            return false;
        }
    }

    @VisibleForTesting
    static void setSessionReader(@NonNull LogcatSession session, @Nullable LogcatReader reader) {
        synchronized (session) {
            session.mReader = reader;
        }
    }

    @VisibleForTesting
    static boolean isSessionActive(@NonNull LogcatSession session) {
        return session.mActive;
    }

    static void sendNewLogs(@NonNull List<LogLine> logLines,
                            @Nullable WeakReference<LogLinesAvailableInterface> logLinesAvailableInterface) {
        if (logLinesAvailableInterface != null) {
            List<LogLine> logLines1 = new ArrayList<>(logLines);
            ThreadUtils.postOnMainThread(() -> {
                LogLinesAvailableInterface listener = logLinesAvailableInterface.get();
                if (listener != null && listener.isLogViewActive()) {
                    listener.onNewLogsAvailable(logLines1);
                }
            });
        }
    }

    @AnyThread
    public void pauseLogcat() {
        mExecutor.submit(() -> {
            synchronized (mLock) {
                mPaused = true;
            }
        });
    }

    @AnyThread
    public void resumeLogcat() {
        mExecutor.submit(() -> {
            synchronized (mLock) {
                mPaused = false;
                mLock.notifyAll();
            }
        });
    }

    public boolean isLogcatPaused() {
        return mPaused;
    }

    public boolean isLogcatKilled() {
        LogcatSession session = mReaderSession;
        return session == null || !session.mActive;
    }

    public boolean isCollapsedMode() {
        return mCollapsedMode;
    }

    public void setCollapsedMode(boolean collapsedMode) {
        mCollapsedMode = collapsedMode;
        mExpandLogsLiveData.postValue(collapsedMode);
    }

    public int getLogLevel() {
        return mLogLevel;
    }

    public void setLogLevel(int logLevel) {
        mLogLevel = logLevel;
        mLogLevelLiveData.postValue(mLogLevel);
    }

    @AnyThread
    public void killLogcatReader() {
        stopLogcat(null);
    }

    @AnyThread
    public void stopLogcat(@Nullable LogcatSession expectedSession) {
        LogcatSession session;
        synchronized (mReaderSessionLock) {
            session = mReaderSession;
            if (session == null || (expectedSession != null && session != expectedSession)) {
                return;
            }
            mReaderSession = null;
        }
        invalidateSession(session);
        killSessionReader(session);
    }

    private void invalidateSession(@Nullable LogcatSession session) {
        if (session == null) {
            return;
        }
        Future<?> future;
        synchronized (session) {
            session.mActive = false;
            session.mListener.clear();
            future = session.mFuture;
        }
        if (future != null) {
            future.cancel(true);
        }
        synchronized (mLock) {
            mPaused = false;
            mLock.notifyAll();
        }
    }

    @AnyThread
    private static void killSessionReader(@Nullable LogcatSession session) {
        if (session == null) {
            return;
        }
        LogcatReader reader;
        synchronized (session) {
            reader = session.mReader;
            session.mReader = null;
        }
        if (reader != null) {
            reader.killQuietly();
        }
    }

    @AnyThread
    public void openLogsFromFile(@NonNull Uri filename,
                                 @NonNull LogLinesAvailableInterface logLinesAvailableInterface) {
        WeakReference<LogLinesAvailableInterface> listener = new WeakReference<>(logLinesAvailableInterface);
        mExecutor.submit(() -> {
            // remove any lines at the beginning if necessary
            final int maxLines = Prefs.LogViewer.getDisplayLimit();
            SavedLog savedLog;
            savedLog = SaveLogHelper.openLog(filename, maxLines);
            List<String> lines = savedLog.getLogLines();
            List<LogLine> logLines = new ArrayList<>();
            for (int lineNumber = 0, linesSize = lines.size(); lineNumber < linesSize; lineNumber++) {
                String line = lines.get(lineNumber);
                LogLine logLine = LogLine.newLogLine(line, !mCollapsedMode, mFilterPattern);
                if (logLine != null) {
                    logLines.add(logLine);
                }
                mLoadingProgressLiveData.postValue(lineNumber * 100 / linesSize);
            }
            sendNewLogs(logLines, listener);
            if (savedLog.isTruncated()) {
                mTruncatedLinesLiveData.postValue(maxLines);
            }
        });
    }

    @AnyThread
    public void loadFilters() {
        mExecutor.submit(() -> {
            final List<LogFilter> filters = AppsDb.getInstance().logFilterDao().getAll();
            Collections.sort(filters);
            mLogFiltersLiveData.postValue(filters);
        });
    }

    @AnyThread
    public void saveLogs(String filename, @NonNull List<String> logLines) {
        mExecutor.submit(() -> {
            SaveLogHelper.deleteLogIfExists(filename);
            mLogSavedLiveData.postValue(SaveLogHelper.saveLog(logLines, filename));
        });
    }

    @AnyThread
    public void saveLogs(@NonNull Path path, @NonNull SendLogDetails sendLogDetails) {
        mExecutor.submit(() -> {
            if (sendLogDetails.getAttachmentType() == null || sendLogDetails.getAttachment() == null) {
                mLogSavedLiveData.postValue(null);
                return;
            }
            try (OutputStream output = path.openOutputStream()) {
                try (InputStream input = sendLogDetails.getAttachment().openInputStream()) {
                    IoUtils.copy(input, output);
                }
                mLogSavedLiveData.postValue(path);
            } catch (IOException e) {
                mLogSavedLiveData.postValue(null);
                Log.w(TAG, e);
            }
        });
    }

    @AnyThread
    public void prepareLogsToBeSent(boolean includeDeviceInfo, boolean includeDmesg, @NonNull Collection<String> logLines) {
        mExecutor.submit(() -> {
            SendLogDetails sendLogDetails = new SendLogDetails();
            sendLogDetails.setSubject(getApplication().getString(R.string.subject_log_report));
            // either zip up multiple files or just attach the one file
            String deviceInfo = null;
            if (includeDeviceInfo) {
                deviceInfo = BuildHelper.getBuildInformationAsString();
            }
            String dmesg = null;
            if (includeDmesg) {
                Runner.Result result = Runner.runCommand(new String[]{"dmesg"});
                if (result.isSuccessful()) {
                    dmesg = result.getOutput();
                    if (dmesg.length() == 0) {
                        dmesg = null;
                    }
                }
            }
            int exportCount = 0;
            if (!logLines.isEmpty()) {
                ++exportCount;
            }
            if (deviceInfo != null) {
                ++exportCount;
            }
            if (dmesg != null) {
                ++exportCount;
            }

            if (exportCount == 0) {
                sendLogDetails.setAttachmentType(null);
            } else if (exportCount == 1) {
                Path tempFile;
                if (!logLines.isEmpty()) {
                    tempFile = SaveLogHelper.saveTemporaryFile("log", null, logLines);
                } else if (dmesg != null) {
                    tempFile = SaveLogHelper.saveTemporaryFile("txt", dmesg, null);
                } else { // Device info
                    tempFile = SaveLogHelper.saveTemporaryFile("txt", deviceInfo, null);
                }
                sendLogDetails.setAttachmentType("text/plain");
                sendLogDetails.setAttachment(tempFile);
            } else { // Multiple attachments, make zip first
                try {
                    Path zipFile = Paths.get(FileCache.getGlobalFileCache().createCachedFile("zip"));
                    try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(zipFile.openOutputStream(), 0x1000))) {
                        if (!logLines.isEmpty()) {
                            output.putNextEntry(new ZipEntry(SaveLogHelper.LOG_FILENAME));
                            for (String logLine : logLines) {
                                output.write(logLine.getBytes(StandardCharsets.UTF_8));
                                output.write("\n".getBytes(StandardCharsets.UTF_8));
                            }
                        }
                        if (deviceInfo != null) {
                            output.putNextEntry(new ZipEntry(SaveLogHelper.DEVICE_INFO_FILENAME));
                            output.write(deviceInfo.getBytes(StandardCharsets.UTF_8));
                        }
                        if (dmesg != null) {
                            output.putNextEntry(new ZipEntry(SaveLogHelper.DMESG_FILENAME));
                            output.write(dmesg.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                    sendLogDetails.setAttachmentType("application/zip");
                    sendLogDetails.setAttachment(zipFile);
                } catch (Exception th) {
                    Log.w(TAG, th);
                    sendLogDetails.setAttachmentType(null);
                }
            }
            mLogToBeSentLiveData.postValue(sendLogDetails);
        });
    }

    @AnyThread
    public void prepareStructuredLogsToBeSent(@NonNull LogcatStructuredExporter.Format format,
                                              @NonNull List<LogLine> logLines) {
        mExecutor.submit(() -> {
            SendLogDetails sendLogDetails = new SendLogDetails();
            sendLogDetails.setSubject(getApplication().getString(R.string.subject_log_report));
            try {
                String exportedLogs = format == LogcatStructuredExporter.Format.JSON
                        ? LogcatStructuredExporter.toJson(logLines)
                        : LogcatStructuredExporter.toCsv(logLines);
                Path tempFile = SaveLogHelper.saveTemporaryFile(format.extension, exportedLogs, null);
                if (tempFile == null) {
                    sendLogDetails.setAttachmentType(null);
                } else {
                    sendLogDetails.setAttachmentType(format.mimeType);
                    sendLogDetails.setAttachment(tempFile);
                    sendLogDetails.setAttachmentName(LogcatStructuredExporter.createExportFilename(format));
                }
            } catch (JSONException e) {
                Log.w(TAG, e);
                sendLogDetails.setAttachmentType(null);
            }
            mLogToBeSentLiveData.postValue(sendLogDetails);
        });
    }
}
