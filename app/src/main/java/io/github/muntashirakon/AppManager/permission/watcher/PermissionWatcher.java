// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission.watcher;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compares rules with what apps actually have and, when allowed to, puts mismatches back. Reading and changing
 * the system goes through a {@link Backend}, so the decisions here can be checked without a device.
 */
public final class PermissionWatcher {
    public static final int ACTION_NONE = 0;
    public static final int ACTION_GRANT = 1;
    public static final int ACTION_REVOKE = 2;

    @IntDef({ACTION_NONE, ACTION_GRANT, ACTION_REVOKE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Action {
    }

    /** The mismatch was put back. */
    public static final int OUTCOME_ENFORCED = 0;
    /** Putting it back was tried and didn't work. */
    public static final int OUTCOME_FAILED = 1;
    /** No root or ADB mode was running, so nothing was tried. */
    public static final int OUTCOME_NEEDS_PRIVILEGE = 2;
    /** The user turned enforcement off, so the mismatch is only reported. */
    public static final int OUTCOME_REPORTED = 3;

    @IntDef({OUTCOME_ENFORCED, OUTCOME_FAILED, OUTCOME_NEEDS_PRIVILEGE, OUTCOME_REPORTED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Outcome {
    }

    public interface Backend {
        /**
         * @return whether the permission is granted or the app op allowed, or {@code null} when the app is gone or
         * no longer asks for the permission
         */
        @WorkerThread
        @Nullable
        Boolean isAllowed(@NonNull WatchRule rule);

        /** Whether a privileged mode that can change this rule's permission or app op is running right now. */
        @WorkerThread
        boolean canEnforce(@NonNull WatchRule rule);

        /** Grants or allows when {@code allow} is set, revokes or denies otherwise. Throws when that didn't happen. */
        @WorkerThread
        void apply(@NonNull WatchRule rule, boolean allow) throws Exception;
    }

    public static final class Drift {
        @NonNull
        public final WatchRule rule;
        /** What the app had when the check found it. */
        public final boolean wasAllowed;
        @Outcome
        public final int outcome;
        @Nullable
        public final String failure;

        Drift(@NonNull WatchRule rule, boolean wasAllowed, @Outcome int outcome, @Nullable String failure) {
            this.rule = rule;
            this.wasAllowed = wasAllowed;
            this.outcome = outcome;
            this.failure = failure;
        }
    }

    public static final class Report {
        /** Rules whose app and permission were there to compare. */
        public final int checked;
        /** Rules for an app that's gone, or a permission the app no longer asks for. */
        public final int skipped;
        @NonNull
        public final List<Drift> drifts;

        Report(int checked, int skipped, @NonNull List<Drift> drifts) {
            this.checked = checked;
            this.skipped = skipped;
            this.drifts = Collections.unmodifiableList(drifts);
        }

        public int count(@Outcome int outcome) {
            int count = 0;
            for (Drift drift : drifts) {
                if (drift.outcome == outcome) {
                    ++count;
                }
            }
            return count;
        }
    }

    private PermissionWatcher() {
    }

    /** What a rule asks for, given whether the permission or app op is allowed now. */
    @Action
    public static int evaluate(@WatchRule.State int state, boolean allowed) {
        switch (state) {
            case WatchRule.STATE_ALLOW:
                return allowed ? ACTION_NONE : ACTION_GRANT;
            case WatchRule.STATE_DENY:
                return allowed ? ACTION_REVOKE : ACTION_NONE;
            case WatchRule.STATE_IGNORE:
            default:
                return ACTION_NONE;
        }
    }

    /**
     * @param enforce whether the user lets the watcher change permissions back. Off, every mismatch is only
     *                reported.
     */
    @WorkerThread
    @NonNull
    public static Report check(@NonNull List<WatchRule> rules, @NonNull Backend backend, boolean enforce) {
        int checked = 0;
        int skipped = 0;
        List<Drift> drifts = new ArrayList<>();
        for (WatchRule rule : rules) {
            if (rule.state == WatchRule.STATE_IGNORE) {
                // Not even read: the user asked for this one to be left alone.
                continue;
            }
            Boolean allowed = backend.isAllowed(rule);
            if (allowed == null) {
                ++skipped;
                continue;
            }
            ++checked;
            int action = evaluate(rule.state, allowed);
            if (action == ACTION_NONE) {
                continue;
            }
            if (!enforce) {
                drifts.add(new Drift(rule, allowed, OUTCOME_REPORTED, null));
            } else if (!backend.canEnforce(rule)) {
                drifts.add(new Drift(rule, allowed, OUTCOME_NEEDS_PRIVILEGE, null));
            } else {
                try {
                    backend.apply(rule, action == ACTION_GRANT);
                    drifts.add(new Drift(rule, allowed, OUTCOME_ENFORCED, null));
                } catch (Exception e) {
                    drifts.add(new Drift(rule, allowed, OUTCOME_FAILED, e.getMessage()));
                }
            }
        }
        return new Report(checked, skipped, drifts);
    }
}
