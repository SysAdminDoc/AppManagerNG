// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.compontents;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.rules.struct.AppOpRule;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.rules.struct.PermissionRule;
import io.github.muntashirakon.AppManager.rules.struct.RuleEntry;

/** Immutable snapshot of resettable rules before any user-specific mutation starts. */
public final class ComponentRuleResetPlan {
    @NonNull
    private final String mPackageName;
    @NonNull
    private final List<Target> mTargets;

    private ComponentRuleResetPlan(@NonNull String packageName, @NonNull List<Target> targets) {
        mPackageName = packageName;
        mTargets = Collections.unmodifiableList(new ArrayList<>(targets));
    }

    @NonNull
    static ComponentRuleResetPlan fromRules(@NonNull String packageName, @NonNull int[] userIds,
                                            @NonNull List<RuleEntry> entries) {
        List<RuleSpec> rules = new ArrayList<>();
        for (RuleEntry entry : entries) {
            RuleSpec rule = RuleSpec.from(entry);
            if (rule != null) {
                rules.add(rule);
            }
        }
        List<Target> targets = new ArrayList<>(rules.size() * userIds.length);
        for (int userId : userIds) {
            for (RuleSpec rule : rules) {
                targets.add(new Target(packageName, userId, rule));
            }
        }
        return new ComponentRuleResetPlan(packageName, targets);
    }

    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    @NonNull
    public List<Target> getTargets() {
        return mTargets;
    }

    public int size() {
        return mTargets.size();
    }

    @NonNull
    ComponentRuleResetPlan retainTargets(@NonNull Set<String> targetIds) {
        List<Target> retained = new ArrayList<>();
        for (Target target : mTargets) {
            if (targetIds.contains(target.getId())) {
                retained.add(target);
            }
        }
        return new ComponentRuleResetPlan(mPackageName, retained);
    }

    @NonNull
    Set<RuleSpec> getUniqueRules() {
        Set<RuleSpec> rules = new LinkedHashSet<>();
        for (Target target : mTargets) {
            rules.add(target.getRule());
        }
        return rules;
    }

    public static final class Target {
        @NonNull
        private final String mPackageName;
        private final int mUserId;
        @NonNull
        private final RuleSpec mRule;

        private Target(@NonNull String packageName, int userId, @NonNull RuleSpec rule) {
            mPackageName = packageName;
            mUserId = userId;
            mRule = rule;
        }

        @NonNull
        public String getId() {
            return mPackageName + ':' + mUserId + ':' + mRule.getId();
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        public int getUserId() {
            return mUserId;
        }

        @NonNull
        public RuleSpec getRule() {
            return mRule;
        }

        @NonNull
        public String getLabel() {
            return mRule.getLabel();
        }
    }

    public static final class RuleSpec {
        private static final int KIND_COMPONENT = 1;
        private static final int KIND_APP_OP = 2;
        private static final int KIND_PERMISSION = 3;

        private final int mKind;
        @NonNull
        private final String mName;
        @NonNull
        private final RuleType mType;
        @NonNull
        private final String mComponentStatus;
        private final int mAppOpMode;
        private final boolean mPermissionGranted;
        private final int mPermissionFlags;

        private RuleSpec(int kind, @NonNull String name, @NonNull RuleType type,
                         @NonNull String componentStatus, int appOpMode,
                         boolean permissionGranted, int permissionFlags) {
            mKind = kind;
            mName = name;
            mType = type;
            mComponentStatus = componentStatus;
            mAppOpMode = appOpMode;
            mPermissionGranted = permissionGranted;
            mPermissionFlags = permissionFlags;
        }

        static RuleSpec from(@NonNull RuleEntry entry) {
            if (entry instanceof ComponentRule) {
                ComponentRule component = (ComponentRule) entry;
                return new RuleSpec(KIND_COMPONENT, component.name, component.type,
                        component.getComponentStatus(), 0, false, 0);
            }
            if (entry instanceof AppOpRule) {
                AppOpRule appOp = (AppOpRule) entry;
                return new RuleSpec(KIND_APP_OP, appOp.name, appOp.type, "",
                        appOp.getMode(), false, 0);
            }
            if (entry instanceof PermissionRule) {
                PermissionRule permission = (PermissionRule) entry;
                return new RuleSpec(KIND_PERMISSION, permission.name, permission.type, "", 0,
                        permission.isGranted(), permission.getFlags());
            }
            return null;
        }

        boolean isComponent() {
            return mKind == KIND_COMPONENT;
        }

        boolean isAppOp() {
            return mKind == KIND_APP_OP;
        }

        boolean isPermission() {
            return mKind == KIND_PERMISSION;
        }

        boolean isIfw() {
            return isComponent() && new ComponentRule("snapshot", mName, mType, mComponentStatus).isIfw();
        }

        int getAppOp() {
            return Integer.parseInt(mName);
        }

        int getAppOpMode() {
            return mAppOpMode;
        }

        @NonNull
        String getPermissionName() {
            return mName;
        }

        void restoreTo(@NonNull ComponentsBlocker blocker) {
            if (isComponent()) {
                blocker.addComponent(mName, mType, mComponentStatus);
            } else if (isAppOp()) {
                blocker.setAppOp(getAppOp(), mAppOpMode);
            } else if (isPermission()) {
                blocker.setPermission(mName, mPermissionGranted, mPermissionFlags);
            }
        }

        @NonNull
        String getId() {
            return mKind + ":" + mType.name() + ":" + mName;
        }

        @NonNull
        public String getLabel() {
            if (isAppOp()) {
                return "app-op " + mName;
            }
            if (isPermission()) {
                return "permission " + mName;
            }
            return mName;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof RuleSpec)) return false;
            RuleSpec rule = (RuleSpec) object;
            return getId().equals(rule.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getId());
        }
    }
}
