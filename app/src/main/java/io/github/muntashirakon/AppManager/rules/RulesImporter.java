// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

/**
 * Rules importer is used to import internal rules to App Manager. Rules should only be imported
 * from settings and app data restore sections (although can be exported from various places).
 * <br>
 * Format: <code>package_name component_name type mode|is_applied|is_granted</code>
 *
 * @see RulesExporter
 * @see RuleType
 */
public class RulesImporter implements Closeable {
    private static final String TAG = RulesImporter.class.getSimpleName();

    @NonNull
    private final HashMap<String, ComponentsBlocker>[] mComponentsBlockers;
    @NonNull
    private final List<RuleType> mTypesToImport;
    @Nullable
    private List<String> mPackagesToImport;
    @NonNull
    private final int[] mUserIds;

    public RulesImporter(@NonNull List<RuleType> typesToImport, @NonNull int[] userIds) {
        if (userIds.length == 0) {
            throw new IllegalArgumentException("Input must contain one or more user handles");
        }
        // Init CBs
        //noinspection unchecked
        mComponentsBlockers = new HashMap[userIds.length];
        for (int i = 0; i < userIds.length; ++i) {
            mComponentsBlockers[i] = new HashMap<>();
        }
        mTypesToImport = typesToImport;
        mUserIds = userIds;
    }

    public void addRulesFromUri(Uri uri) throws IOException {
        try (InputStream inputStream = ContextUtils.getContext().getContentResolver().openInputStream(uri)) {
            if (inputStream == null) throw new IOException("Content provider has crashed.");
            try (BufferedReader TSVFile = new BufferedReader(new InputStreamReader(inputStream))) {
                String dataRow;
                while ((dataRow = TSVFile.readLine()) != null) {
                    importRow(dataRow);
                }
            }
        }
    }

    public void addRulesFromPath(Path path) throws IOException {
        try (InputStream inputStream = path.openInputStream()) {
            try (BufferedReader TSVFile = new BufferedReader(new InputStreamReader(inputStream))) {
                String dataRow;
                while ((dataRow = TSVFile.readLine()) != null) {
                    importRow(dataRow);
                }
            }
        }
    }

    /**
     * Parse a single TSV rule line and stage it into every target user's
     * {@link ComponentsBlocker}. A malformed line (unknown rule type, missing
     * fields, an entry from a newer/legacy export format) makes
     * {@link RuleEntry#unflattenFromString} throw {@link IllegalArgumentException};
     * previously that aborted the whole loop, silently dropping every remaining
     * (valid) rule — a fail-unsafe partial application, and on the
     * background-thread import path an uncaught crash. Skip the offending line
     * and keep importing the rest, matching the per-entry skip pattern already
     * used by {@code ComponentUtils.readIFWRules}.
     */
    private void importRow(@NonNull String dataRow) {
        RuleEntry entry;
        try {
            entry = RuleEntry.unflattenFromString(null, dataRow, true);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Skipping malformed rule line: " + dataRow, e);
            return;
        }
        for (int i = 0; i < mUserIds.length; ++i) {
            if (mComponentsBlockers[i].get(entry.packageName) == null) {
                // Get a read-only instance, commit will be called manually
                mComponentsBlockers[i].put(entry.packageName, ComponentsBlocker.getInstance(entry.packageName, mUserIds[i]));
            }
            if (mTypesToImport.contains(entry.type)) {
                //noinspection ConstantConditions Returned ComponentsBlocker will never be null here
                mComponentsBlockers[i].get(entry.packageName).addEntry(entry);
            }
        }
    }

    public List<String> getPackages() {
        return new ArrayList<>(mComponentsBlockers[0].keySet());
    }

    public void setPackagesToImport(List<String> packageNames) {
        mPackagesToImport = packageNames;
    }

    @WorkerThread
    public void applyRules(boolean commitChanges) {
        if (mPackagesToImport == null) mPackagesToImport = getPackages();
        // When #setPackagesToImport(List<String>) is used, ComponentBlocker can be null
        @Nullable ComponentsBlocker cb;
        for (int i = 0; i < mUserIds.length; ++i) {
            for (String packageName : mPackagesToImport) {
                cb = mComponentsBlockers[i].get(packageName);
                if (cb == null) continue;
                // Set mutable, otherwise changes may not be applied properly
                cb.setMutable();
                // Apply component blocking rules
                cb.applyRules(true);
                // Apply app op and permissions
                cb.applyAppOpsAndPerms();
                // Store the changes or discard them
                if (commitChanges) {
                    // Commit changes
                    cb.commit();
                } else {
                    // Don't commit changes, discard the rules
                    cb.setReadOnly();
                }
            }
        }
    }

    @Override
    public void close() {
        // When #setPackagesToImport(List<String>) is used, ComponentBlocker can be null
        for (int i = 0; i < mUserIds.length; ++i) {
            for (ComponentsBlocker cb : mComponentsBlockers[i].values()) {
                IoUtils.closeQuietly(cb);
            }
        }
    }
}
