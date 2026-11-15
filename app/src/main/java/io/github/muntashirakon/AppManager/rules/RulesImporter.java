// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.internet.InternetAccess;
import io.github.muntashirakon.AppManager.rules.struct.InternetRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
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

    /**
     * Parsed rows by package, in file order. They're only added to a package's rules once its write lock is held,
     * since taking the lock reloads the saved rules and would drop anything added before it.
     */
    @NonNull
    private final LinkedHashMap<String, List<RuleEntry>> mStagedRules = new LinkedHashMap<>();
    @NonNull
    private final List<RuleType> mTypesToImport;
    @Nullable
    private List<String> mPackagesToImport;
    @NonNull
    private final int[] mUserIds;
    @NonNull
    private final List<InternetAccess.Status> mInternetRulesNotInForce = new ArrayList<>();

    public RulesImporter(@NonNull List<RuleType> typesToImport, @NonNull int[] userIds) {
        if (userIds.length == 0) {
            throw new IllegalArgumentException("Input must contain one or more user handles");
        }
        mTypesToImport = typesToImport;
        mUserIds = userIds;
    }

    public void addRulesFromUri(Uri uri) throws IOException {
        try (InputStream inputStream = ContextUtils.getContext().getContentResolver().openInputStream(uri)) {
            if (inputStream == null) throw new IOException("Content provider has crashed.");
            try (BufferedReader TSVFile = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String dataRow;
                while ((dataRow = TSVFile.readLine()) != null) {
                    importRow(dataRow);
                }
            }
        }
    }

    public void addRulesFromPath(Path path) throws IOException {
        try (InputStream inputStream = path.openInputStream()) {
            try (BufferedReader TSVFile = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String dataRow;
                while ((dataRow = TSVFile.readLine()) != null) {
                    importRow(dataRow);
                }
            }
        }
    }

    /**
     * Parse a single TSV rule line and stage it for every target user. A malformed line (unknown rule type, missing
     * fields, an entry from a newer/legacy export format) makes {@link RuleEntry#unflattenFromString} throw
     * {@link IllegalArgumentException}; previously that aborted the whole loop, silently dropping every remaining
     * (valid) rule — a fail-unsafe partial application, and on the background-thread import path an uncaught crash.
     * Skip the offending line and keep importing the rest, matching the per-entry skip pattern already used by
     * {@code ComponentUtils.readIFWRules}.
     */
    void importRow(@NonNull String dataRow) {
        RuleEntry entry;
        try {
            entry = RuleEntry.unflattenFromString(null, dataRow, true);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Skipping malformed rule line: " + dataRow, e);
            return;
        }
        if (!mTypesToImport.contains(entry.type)) {
            return;
        }
        List<RuleEntry> rules = mStagedRules.get(entry.packageName);
        if (rules == null) {
            rules = new ArrayList<>();
            mStagedRules.put(entry.packageName, rules);
        }
        rules.add(entry);
    }

    public List<String> getPackages() {
        return new ArrayList<>(mStagedRules.keySet());
    }

    public void setPackagesToImport(List<String> packageNames) {
        mPackagesToImport = packageNames;
    }

    /**
     * Internet rules the last {@link #applyRules(boolean)} saved but couldn't put into force, each with the reason.
     */
    @NonNull
    public List<InternetAccess.Status> getInternetRulesNotInForce() {
        return new ArrayList<>(mInternetRulesNotInForce);
    }

    @WorkerThread
    public void applyRules(boolean commitChanges) {
        mInternetRulesNotInForce.clear();
        if (mPackagesToImport == null) mPackagesToImport = getPackages();
        for (int userId : mUserIds) {
            for (String packageName : mPackagesToImport) {
                List<RuleEntry> staged = mStagedRules.get(packageName);
                if (staged == null) continue;
                List<RuleEntry> rules = rulesForUser(staged, userId);
                boolean hasInternetRule = false;
                ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName, userId);
                try {
                    // Take the write lock, which reloads the saved rules, and only then add the imported ones
                    cb.setMutable();
                    for (RuleEntry rule : rules) {
                        if (rule instanceof InternetRule) {
                            // The UID a saved rule was put on still has to be let go, whatever the import says
                            InternetRule saved = cb.getInternetRule(userId);
                            rule = new InternetRule(packageName, userId, ((InternetRule) rule).isBlocked(),
                                    saved != null ? saved.getUid() : InternetRule.NO_UID);
                            hasInternetRule = true;
                        }
                        cb.addEntry(rule);
                    }
                    // Apply component blocking rules
                    cb.applyRules(true);
                    // Apply app op and permissions
                    cb.applyAppOpsAndPerms();
                    // Store the changes or discard them
                    if (commitChanges) {
                        cb.commit();
                    }
                } finally {
                    // Releases the lock without saving anything more
                    cb.setReadOnly();
                }
                if (hasInternetRule && commitChanges) {
                    // Outside the rules lock, which putting the rule into force takes again
                    InternetAccess.Status status = InternetAccess.apply(packageName, userId);
                    if (!status.inForce) {
                        Log.w(TAG, "Imported internet rule for %s in user %d isn't in force: %s", packageName,
                                userId, status);
                        mInternetRulesNotInForce.add(status);
                    }
                }
            }
        }
    }

    /**
     * The rows that go to one user. Internet rules belong to a user, so that user's own row wins; without one, the
     * last row's wish is carried over. Either way the UID is dropped, since UIDs differ between users and devices.
     */
    @VisibleForTesting
    @NonNull
    static List<RuleEntry> rulesForUser(@NonNull List<RuleEntry> rules, int userId) {
        List<RuleEntry> result = new ArrayList<>(rules.size());
        InternetRule internetRule = null;
        boolean ownRow = false;
        for (RuleEntry rule : rules) {
            if (!(rule instanceof InternetRule)) {
                result.add(rule);
                continue;
            }
            boolean own = ((InternetRule) rule).getUserId() == userId;
            if (own || !ownRow) {
                internetRule = (InternetRule) rule;
                ownRow = own;
            }
        }
        if (internetRule != null) {
            result.add(internetRule.forUser(userId));
        }
        return result;
    }

    @Override
    public void close() {
        mStagedRules.clear();
    }
}
