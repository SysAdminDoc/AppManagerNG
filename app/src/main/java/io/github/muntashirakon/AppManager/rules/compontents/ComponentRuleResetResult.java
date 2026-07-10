// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ComponentRuleResetResult {
    @NonNull
    private final List<Outcome> mOutcomes;
    private final int mTotal;
    private final boolean mCancelled;

    ComponentRuleResetResult(@NonNull List<Outcome> outcomes, int total, boolean cancelled) {
        mOutcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
        mTotal = total;
        mCancelled = cancelled;
    }

    @NonNull
    public List<Outcome> getOutcomes() {
        return mOutcomes;
    }

    public int getTotal() {
        return mTotal;
    }

    public int getCompleted() {
        return mOutcomes.size();
    }

    public int getSucceeded() {
        int succeeded = 0;
        for (Outcome outcome : mOutcomes) {
            if (outcome.isSuccess()) ++succeeded;
        }
        return succeeded;
    }

    public int getFailed() {
        return getCompleted() - getSucceeded();
    }

    public int getPending() {
        return mTotal - getCompleted();
    }

    public boolean isCancelled() {
        return mCancelled;
    }

    public boolean isSuccessful() {
        return !mCancelled && getCompleted() == mTotal && getFailed() == 0;
    }

    @NonNull
    Set<String> getSuccessfulTargetIds() {
        Set<String> ids = new HashSet<>();
        for (Outcome outcome : mOutcomes) {
            if (outcome.isSuccess()) {
                ids.add(outcome.getTarget().getId());
            }
        }
        return ids;
    }

    public static final class Outcome {
        @NonNull
        private final ComponentRuleResetPlan.Target mTarget;
        private final boolean mSuccess;
        @Nullable
        private final String mError;

        private Outcome(@NonNull ComponentRuleResetPlan.Target target, boolean success,
                        @Nullable String error) {
            mTarget = target;
            mSuccess = success;
            mError = error;
        }

        static Outcome success(@NonNull ComponentRuleResetPlan.Target target) {
            return new Outcome(target, true, null);
        }

        static Outcome failure(@NonNull ComponentRuleResetPlan.Target target, @NonNull String error) {
            return new Outcome(target, false, error);
        }

        @NonNull
        public ComponentRuleResetPlan.Target getTarget() {
            return mTarget;
        }

        public boolean isSuccess() {
            return mSuccess;
        }

        @Nullable
        public String getError() {
            return mError;
        }
    }
}
