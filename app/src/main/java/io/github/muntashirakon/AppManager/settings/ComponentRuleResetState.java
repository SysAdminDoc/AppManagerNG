// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.rules.compontents.ComponentRuleResetResult;

public final class ComponentRuleResetState {
    public enum Phase { PREPARING, RUNNING, FINISHED }

    @NonNull
    public final Phase phase;
    public final int completed;
    public final int total;
    @Nullable
    public final String packageName;
    public final int userId;
    @Nullable
    public final String targetLabel;
    @Nullable
    public final ComponentRuleResetResult result;

    private ComponentRuleResetState(@NonNull Phase phase, int completed, int total,
                                    @Nullable String packageName, int userId,
                                    @Nullable String targetLabel,
                                    @Nullable ComponentRuleResetResult result) {
        this.phase = phase;
        this.completed = completed;
        this.total = total;
        this.packageName = packageName;
        this.userId = userId;
        this.targetLabel = targetLabel;
        this.result = result;
    }

    @NonNull
    static ComponentRuleResetState preparing() {
        return new ComponentRuleResetState(Phase.PREPARING, 0, 0, null, 0, null, null);
    }

    @NonNull
    static ComponentRuleResetState running(int completed, int total, @Nullable String packageName,
                                           int userId, @Nullable String targetLabel) {
        return new ComponentRuleResetState(Phase.RUNNING, completed, total, packageName, userId,
                targetLabel, null);
    }

    @NonNull
    static ComponentRuleResetState finished(@NonNull ComponentRuleResetResult result) {
        return new ComponentRuleResetState(Phase.FINISHED, result.getCompleted(), result.getTotal(),
                null, 0, null, result);
    }
}
