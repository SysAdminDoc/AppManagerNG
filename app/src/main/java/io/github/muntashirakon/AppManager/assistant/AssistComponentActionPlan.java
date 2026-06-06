// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.assistant;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.details.components.ReceiverBroadcastUtils;
import io.github.muntashirakon.AppManager.details.components.ServiceActionUtils;

final class AssistComponentActionPlan {
    enum ActionType {
        SERVICE_START,
        SERVICE_STOP,
        RECEIVER_BROADCAST,
    }

    enum DispatchRoute {
        UNPRIVILEGED,
        PRIVILEGED,
    }

    private AssistComponentActionPlan() {
    }

    @NonNull
    static List<Action> forService(@NonNull ServiceInfo serviceInfo,
                                   boolean serviceRunning,
                                   boolean disabledOrBlocked,
                                   boolean hasDeclaredPermission,
                                   boolean privilegedAvailable,
                                   int targetUserId,
                                   int currentUserId) {
        if (disabledOrBlocked) {
            return Collections.emptyList();
        }
        List<Action> actions = new ArrayList<>(serviceRunning ? 2 : 1);
        Action startAction = buildServiceAction(ActionType.SERVICE_START, serviceInfo, hasDeclaredPermission,
                privilegedAvailable, targetUserId, currentUserId);
        if (startAction != null) {
            actions.add(startAction);
        }
        if (serviceRunning) {
            Action stopAction = buildServiceAction(ActionType.SERVICE_STOP, serviceInfo, hasDeclaredPermission,
                    privilegedAvailable, targetUserId, currentUserId);
            if (stopAction != null) {
                actions.add(stopAction);
            }
        }
        return actions;
    }

    @NonNull
    static List<Action> forReceiver(@NonNull ActivityInfo receiverInfo,
                                    @NonNull List<String> declaredActions,
                                    boolean disabledOrBlocked,
                                    boolean privilegedAvailable,
                                    int targetUserId,
                                    int currentUserId) {
        if (disabledOrBlocked || declaredActions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Action> actions = new ArrayList<>(declaredActions.size());
        for (String declaredAction : declaredActions) {
            String action = trimToNull(declaredAction);
            if (action == null) {
                continue;
            }
            boolean privileged = ReceiverBroadcastUtils.needsPrivilegedDispatch(action, receiverInfo.exported,
                    targetUserId, currentUserId);
            if (privileged && !privilegedAvailable) {
                continue;
            }
            actions.add(new Action(ActionType.RECEIVER_BROADCAST, route(privileged), receiverInfo.packageName,
                    receiverInfo.name, targetUserId, action));
        }
        return actions;
    }

    @Nullable
    private static Action buildServiceAction(@NonNull ActionType actionType,
                                             @NonNull ServiceInfo serviceInfo,
                                             boolean hasDeclaredPermission,
                                             boolean privilegedAvailable,
                                             int targetUserId,
                                             int currentUserId) {
        boolean privileged = ServiceActionUtils.needsPrivilegedDispatch(serviceInfo.exported, serviceInfo.permission,
                hasDeclaredPermission, targetUserId, currentUserId);
        if (privileged && !privilegedAvailable) {
            return null;
        }
        return new Action(actionType, route(privileged), serviceInfo.packageName, serviceInfo.name, targetUserId, null);
    }

    @NonNull
    private static DispatchRoute route(boolean privileged) {
        return privileged ? DispatchRoute.PRIVILEGED : DispatchRoute.UNPRIVILEGED;
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static final class Action {
        @NonNull
        final ActionType type;
        @NonNull
        final DispatchRoute route;
        @NonNull
        final String packageName;
        @NonNull
        final String componentName;
        final int userId;
        @Nullable
        final String broadcastAction;

        private Action(@NonNull ActionType type, @NonNull DispatchRoute route, @NonNull String packageName,
                       @NonNull String componentName, int userId, @Nullable String broadcastAction) {
            this.type = type;
            this.route = route;
            this.packageName = packageName;
            this.componentName = componentName;
            this.userId = userId;
            this.broadcastAction = broadcastAction;
        }

        @NonNull
        @VisibleForTesting
        Intent buildIntent() {
            if (type == ActionType.RECEIVER_BROADCAST) {
                return ReceiverBroadcastUtils.buildBroadcastIntent(packageName, componentName, broadcastAction,
                        Collections.emptyList(), null, true);
            }
            return ServiceActionUtils.buildServiceIntent(packageName, componentName);
        }
    }
}
