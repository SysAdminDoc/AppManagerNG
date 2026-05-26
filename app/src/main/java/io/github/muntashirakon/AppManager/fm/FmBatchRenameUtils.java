// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

final class FmBatchRenameUtils {
    private static final int MAX_RENAME_ATTEMPTS = 10_000;

    enum IssueType {
        EMPTY_TARGET_NAME,
        INVALID_TARGET_NAME,
        MISSING_PARENT,
        NO_AVAILABLE_TARGET_NAME
    }

    interface ProgressListener {
        void onEntryStarted(@NonNull Entry entry, int index, int total);

        void onEntryFinished(@NonNull Entry entry, int completed, int total);
    }

    static final class Plan {
        @NonNull
        final List<Entry> entries;
        @NonNull
        final List<Issue> issues;
        final int resolvedConflictCount;

        Plan(@NonNull List<Entry> entries, @NonNull List<Issue> issues, int resolvedConflictCount) {
            this.entries = entries;
            this.issues = issues;
            this.resolvedConflictCount = resolvedConflictCount;
        }

        boolean canExecute() {
            return !entries.isEmpty() && issues.isEmpty();
        }
    }

    static final class Entry {
        @NonNull
        final Path source;
        @NonNull
        final Path parent;
        @NonNull
        final String sourceName;
        @NonNull
        final String targetName;
        final boolean resolvedConflict;

        Entry(@NonNull Path source, @NonNull Path parent, @NonNull String sourceName,
              @NonNull String targetName, boolean resolvedConflict) {
            this.source = source;
            this.parent = parent;
            this.sourceName = sourceName;
            this.targetName = targetName;
            this.resolvedConflict = resolvedConflict;
        }
    }

    static final class Issue {
        @NonNull
        final IssueType type;
        @Nullable
        final String sourceName;
        @Nullable
        final String targetName;

        Issue(@NonNull IssueType type, @Nullable String sourceName, @Nullable String targetName) {
            this.type = type;
            this.sourceName = sourceName;
            this.targetName = targetName;
        }
    }

    static final class BatchResult {
        @NonNull
        final List<Result> results;
        final boolean interrupted;

        BatchResult(@NonNull List<Result> results, boolean interrupted) {
            this.results = results;
            this.interrupted = interrupted;
        }

        int getSuccessCount() {
            int count = 0;
            for (Result result : results) {
                if (result.success) {
                    ++count;
                }
            }
            return count;
        }
    }

    static final class Result {
        @NonNull
        final Entry entry;
        final boolean success;
        @Nullable
        final String failureMessage;

        Result(@NonNull Entry entry, boolean success, @Nullable String failureMessage) {
            this.entry = entry;
            this.success = success;
            this.failureMessage = failureMessage;
        }
    }

    @NonNull
    static Plan createPlan(@NonNull List<Path> paths, @NonNull String prefix, @Nullable String extension) {
        List<Entry> entries = new ArrayList<>(paths.size());
        List<Issue> issues = new ArrayList<>();
        String normalizedExtension = normalizeExtension(extension);
        String baseDisplayName = buildDisplayName(prefix, normalizedExtension, 0);
        if (TextUtils.isEmpty(baseDisplayName)) {
            issues.add(new Issue(IssueType.EMPTY_TARGET_NAME, null, null));
            return new Plan(entries, issues, 0);
        }
        if (!isValidDisplayName(baseDisplayName)) {
            issues.add(new Issue(IssueType.INVALID_TARGET_NAME, null, baseDisplayName));
            return new Plan(entries, issues, 0);
        }

        Map<String, Set<String>> reservedNamesByParent = new HashMap<>();
        int resolvedConflictCount = 0;
        int index = 1;
        for (Path path : paths) {
            Path parent = path.getParent();
            if (parent == null) {
                issues.add(new Issue(IssueType.MISSING_PARENT, path.getName(), null));
                ++index;
                continue;
            }
            Set<String> reservedNames = reservedNamesByParent.computeIfAbsent(parent.getUri().toString(),
                    key -> new HashSet<>());
            NameCandidate nameCandidate = findAvailableName(parent, prefix, normalizedExtension, index, reservedNames);
            if (nameCandidate == null) {
                issues.add(new Issue(IssueType.NO_AVAILABLE_TARGET_NAME, path.getName(), baseDisplayName));
                ++index;
                continue;
            }
            reservedNames.add(nameCandidate.displayName);
            if (nameCandidate.resolvedConflict) {
                ++resolvedConflictCount;
            }
            entries.add(new Entry(path, parent, path.getName(), nameCandidate.displayName,
                    nameCandidate.resolvedConflict));
            ++index;
        }
        return new Plan(entries, issues, resolvedConflictCount);
    }

    @NonNull
    static BatchResult execute(@NonNull Plan plan, @Nullable ProgressListener progressListener) {
        List<Result> results = new ArrayList<>(plan.entries.size());
        int total = plan.entries.size();
        for (int i = 0; i < total; ++i) {
            if (ThreadUtils.isInterrupted()) {
                return new BatchResult(results, true);
            }
            Entry entry = plan.entries.get(i);
            if (progressListener != null) {
                progressListener.onEntryStarted(entry, i, total);
            }
            String failureMessage = null;
            boolean success = false;
            try {
                success = entry.source.renameTo(entry.targetName);
            } catch (RuntimeException e) {
                failureMessage = e.getLocalizedMessage();
            }
            if (!success && TextUtils.isEmpty(failureMessage)) {
                failureMessage = null;
            }
            results.add(new Result(entry, success, failureMessage));
            if (progressListener != null) {
                progressListener.onEntryFinished(entry, i + 1, total);
            }
        }
        return new BatchResult(results, ThreadUtils.isInterrupted());
    }

    @Nullable
    private static NameCandidate findAvailableName(@NonNull Path parent, @NonNull String prefix,
                                                   @Nullable String extension, int initialIndex,
                                                   @NonNull Set<String> reservedNames) {
        String displayName = buildDisplayName(prefix, extension, 0);
        int index = initialIndex;
        int attempts = 0;
        boolean resolvedConflict = false;
        while (parent.hasFile(displayName) || reservedNames.contains(displayName)) {
            resolvedConflict = true;
            displayName = buildDisplayName(prefix, extension, index);
            ++index;
            ++attempts;
            if (attempts > MAX_RENAME_ATTEMPTS || !isValidDisplayName(displayName)) {
                return null;
            }
        }
        return new NameCandidate(displayName, resolvedConflict);
    }

    @NonNull
    private static String buildDisplayName(@NonNull String prefix, @Nullable String extension, int suffixIndex) {
        String normalizedExtension = normalizeExtension(extension);
        String suffix = TextUtils.isEmpty(normalizedExtension) ? "" : "." + normalizedExtension;
        if (suffixIndex <= 0) {
            return prefix + suffix;
        }
        return String.format(Locale.ROOT, "%s (%d)%s", prefix, suffixIndex, suffix);
    }

    @Nullable
    private static String normalizeExtension(@Nullable String extension) {
        if (TextUtils.isEmpty(extension)) {
            return null;
        }
        while (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        return TextUtils.isEmpty(extension) ? null : extension;
    }

    private static boolean isValidDisplayName(@NonNull String displayName) {
        if (displayName.equals(".") || displayName.equals("..") || displayName.contains(Paths.PATH_SEPARATOR)) {
            return false;
        }
        String sanitized = Paths.sanitize(displayName, true);
        return displayName.equals(sanitized);
    }

    private FmBatchRenameUtils() {
    }

    private static final class NameCandidate {
        @NonNull
        final String displayName;
        final boolean resolvedConflict;

        NameCandidate(@NonNull String displayName, boolean resolvedConflict) {
            this.displayName = displayName;
            this.resolvedConflict = resolvedConflict;
        }
    }
}
