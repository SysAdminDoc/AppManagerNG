// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class ComponentRuleResetRunner {
    public interface CancellationChecker {
        boolean isCancelled();
    }

    public interface TargetExecutor {
        @NonNull
        ComponentRuleResetResult.Outcome execute(@NonNull ComponentRuleResetPlan.Target target);
    }

    public interface ProgressListener {
        void onProgress(int completed, int total, @NonNull ComponentRuleResetPlan.Target target);
    }

    private ComponentRuleResetRunner() {
    }

    @NonNull
    public static ComponentRuleResetResult run(@NonNull List<ComponentRuleResetPlan> plans,
                                               @NonNull CancellationChecker cancellationChecker,
                                               @NonNull TargetExecutor executor,
                                               @NonNull ProgressListener progressListener) {
        int total = 0;
        for (ComponentRuleResetPlan plan : plans) {
            total += plan.size();
        }
        List<ComponentRuleResetResult.Outcome> outcomes = new ArrayList<>(total);
        boolean cancelled = false;
        for (ComponentRuleResetPlan plan : plans) {
            for (ComponentRuleResetPlan.Target target : plan.getTargets()) {
                if (cancellationChecker.isCancelled()) {
                    cancelled = true;
                    break;
                }
                outcomes.add(executor.execute(target));
                progressListener.onProgress(outcomes.size(), total, target);
            }
            if (cancelled) break;
        }
        return new ComponentRuleResetResult(outcomes, total, cancelled);
    }
}
