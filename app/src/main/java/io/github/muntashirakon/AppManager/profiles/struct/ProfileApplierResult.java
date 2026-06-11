// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import androidx.annotation.NonNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class ProfileApplierResult {
    public static final ProfileApplierResult EMPTY_RESULT = new ProfileApplierResult();

    private boolean mRequiresRestart;
    private boolean mFailed;
    @NonNull
    private final Set<String> mFailedPackages = new LinkedHashSet<>();
    @NonNull
    private final Set<Integer> mSkippedOperations = new LinkedHashSet<>();

    public void setRequiresRestart(boolean requiresRestart) {
        mRequiresRestart = requiresRestart;
    }

    public boolean requiresRestart() {
        return mRequiresRestart;
    }

    /**
     * Folds a batch sub-operation's outcome into the overall profile result. A profile is only
     * "successful" when every sub-operation succeeded for every target — previously failures were
     * merely logged and the profile always reported success.
     */
    public void recordFailedPackages(@NonNull Iterable<String> failedPackages) {
        for (String packageName : failedPackages) {
            mFailed = true;
            if (packageName != null) {
                mFailedPackages.add(packageName);
            }
        }
    }

    public void markFailed() {
        mFailed = true;
    }

    public void recordSkippedOperations(@NonNull Iterable<Integer> skippedOperations) {
        for (Integer operation : skippedOperations) {
            if (operation != null) {
                mSkippedOperations.add(operation);
            }
        }
    }

    public boolean hasSkippedOperations() {
        return !mSkippedOperations.isEmpty();
    }

    @NonNull
    public Set<Integer> getSkippedOperations() {
        return mSkippedOperations;
    }

    public boolean isSuccessful() {
        return !mFailed && mSkippedOperations.isEmpty();
    }

    @NonNull
    public Set<String> getFailedPackages() {
        return mFailedPackages;
    }
}
