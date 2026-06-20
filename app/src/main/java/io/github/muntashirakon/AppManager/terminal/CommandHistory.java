// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.logs.Log;

final class CommandHistory {
    private static final String TAG = CommandHistory.class.getSimpleName();
    private static final String HISTORY_FILE = "terminal_history";
    private static final int MAX_ENTRIES = 500;

    private final List<String> mEntries = new ArrayList<>();
    private final File mHistoryFile;
    private final ExecutorService mSaveExecutor = Executors.newSingleThreadExecutor();
    private int mPosition;

    CommandHistory(@NonNull Context context) {
        mHistoryFile = new File(context.getFilesDir(), HISTORY_FILE);
        load();
        mPosition = mEntries.size();
    }

    synchronized void add(@NonNull String command) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return;
        if (!mEntries.isEmpty() && mEntries.get(mEntries.size() - 1).equals(trimmed)) {
            mPosition = mEntries.size();
            return;
        }
        mEntries.add(trimmed);
        if (mEntries.size() > MAX_ENTRIES) {
            mEntries.remove(0);
        }
        mPosition = mEntries.size();
        List<String> snapshot = new ArrayList<>(mEntries);
        mSaveExecutor.execute(() -> save(snapshot));
    }

    @Nullable
    synchronized String navigateUp() {
        if (mEntries.isEmpty() || mPosition <= 0) return null;
        mPosition--;
        return mEntries.get(mPosition);
    }

    @Nullable
    synchronized String navigateDown() {
        if (mEntries.isEmpty() || mPosition >= mEntries.size()) return null;
        mPosition++;
        if (mPosition >= mEntries.size()) return "";
        return mEntries.get(mPosition);
    }

    synchronized void resetPosition() {
        mPosition = mEntries.size();
    }

    synchronized int size() {
        return mEntries.size();
    }

    void flush() {
        try {
            mSaveExecutor.submit(() -> {}).get();
        } catch (Exception ignored) {
        }
    }

    private synchronized void load() {
        if (!mHistoryFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(mHistoryFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    mEntries.add(line);
                }
            }
            while (mEntries.size() > MAX_ENTRIES) {
                mEntries.remove(0);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to load terminal history", e);
        }
    }

    private void save(@NonNull List<String> entries) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mHistoryFile))) {
            int start = Math.max(0, entries.size() - MAX_ENTRIES);
            for (int i = start; i < entries.size(); i++) {
                writer.write(entries.get(i));
                writer.newLine();
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to save terminal history", e);
        }
    }
}
