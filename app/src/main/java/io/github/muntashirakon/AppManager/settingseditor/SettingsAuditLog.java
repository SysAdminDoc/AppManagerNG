// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settingseditor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONException;
import org.json.JSONObject;

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

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * Every change made in the settings editor, one JSON object per line in a private file, so a change can be looked up
 * and put back later. Holds the newest {@link #MAX_ENTRIES}.
 */
public final class SettingsAuditLog {
    private static final String TAG = SettingsAuditLog.class.getSimpleName();
    @VisibleForTesting
    static final String FILE_NAME = "settings_editor_changes.jsonl";
    @VisibleForTesting
    static final int MAX_ENTRIES = 500;

    @NonNull
    public static SettingsAuditLog getInstance(@NonNull Context context) {
        return new SettingsAuditLog(new File(context.getApplicationContext().getFilesDir(), FILE_NAME));
    }

    @NonNull
    private final File mFile;

    @VisibleForTesting
    SettingsAuditLog(@NonNull File file) {
        mFile = file;
    }

    @WorkerThread
    public synchronized void record(@NonNull SettingsChange change) throws IOException {
        List<SettingsChange> changes = readOldestFirst();
        if (changes.size() >= MAX_ENTRIES) {
            changes = new ArrayList<>(changes.subList(changes.size() - MAX_ENTRIES + 1, changes.size()));
            changes.add(change);
            write(changes, false);
        } else {
            write(Collections.singletonList(change), true);
        }
    }

    /** Newest first. A line that can't be read is left out. */
    @WorkerThread
    @NonNull
    public synchronized List<SettingsChange> readNewestFirst() {
        List<SettingsChange> changes = readOldestFirst();
        Collections.reverse(changes);
        return changes;
    }

    @NonNull
    private List<SettingsChange> readOldestFirst() {
        List<SettingsChange> changes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(mFile),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    changes.add(SettingsChange.fromJson(new JSONObject(line)));
                } catch (JSONException e) {
                    Log.w(TAG, "Skipping a damaged change record.", e);
                }
            }
        } catch (FileNotFoundException ignore) {
            // Nothing changed yet.
        } catch (IOException e) {
            Log.w(TAG, "Could not read the settings changes.", e);
        }
        return changes;
    }

    private void write(@NonNull List<SettingsChange> changes, boolean append) throws IOException {
        File parent = mFile.getParentFile();
        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create " + parent);
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(mFile, append), StandardCharsets.UTF_8)) {
            for (SettingsChange change : changes) {
                // JSON escapes line breaks, so one change is always one line.
                writer.write(change.toJson().toString());
                writer.write('\n');
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }
}
