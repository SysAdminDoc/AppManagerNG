// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.assistant;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_DISABLED_COMPONENTS;
import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.apk.parser.ManifestComponent;
import io.github.muntashirakon.AppManager.apk.parser.ManifestIntentFilter;
import io.github.muntashirakon.AppManager.apk.parser.ManifestParser;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.UsageStatsManagerCompat;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.history.ops.OpHistoryManager;
import io.github.muntashirakon.AppManager.history.ops.OperationJournalMetadata;
import io.github.muntashirakon.AppManager.history.ops.SingleAppActionHistoryItem;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.rules.struct.ComponentRule;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class AssistActionActivity extends BaseActivity {
    private static final String TAG = AssistActionActivity.class.getSimpleName();

    private static final int ACTION_FORCE_STOP = 1;
    private static final int ACTION_FREEZE_TOGGLE = 2;
    private static final int ACTION_OPEN_APP_INFO = 3;
    private static final int ACTION_COMPONENT = 4;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        ThreadUtils.postOnBackgroundThread(() -> {
            TargetInfo targetInfo = loadTargetInfo();
            ThreadUtils.postOnMainThread(() -> {
                if (targetInfo == null) {
                    showNoTargetDialog();
                    return;
                }
                showActionDialog(targetInfo);
            });
        });
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    @Nullable
    private TargetInfo loadTargetInfo() {
        AssistTargetResolver.Target target = AssistTargetResolver.resolve(getIntent(), UserHandleHidden.myUserId(),
                System.currentTimeMillis(), this::queryUsageEvents);
        if (target == null) {
            return null;
        }
        try {
            ApplicationInfo applicationInfo = PackageManagerCompat.getApplicationInfo(target.packageName,
                    PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
                            | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, target.userId);
            CharSequence label = applicationInfo.loadLabel(getPackageManager());
            return new TargetInfo(target, label, FreezeUtils.isFrozen(applicationInfo), loadComponentActions(target));
        } catch (Throwable th) {
            Log.w(TAG, "Could not resolve assist target " + target.packageName, th);
            return null;
        }
    }

    @NonNull
    @WorkerThread
    private List<AssistComponentActionPlan.Action> loadComponentActions(@NonNull AssistTargetResolver.Target target) {
        List<AssistComponentActionPlan.Action> actions = new ArrayList<>();
        boolean privilegedAvailable = SelfPermissions.isSystemOrRootOrShell();
        try {
            PackageInfo packageInfo = PackageManagerCompat.getPackageInfo(target.packageName,
                    PackageManager.GET_META_DATA | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                            | MATCH_DISABLED_COMPONENTS | MATCH_UNINSTALLED_PACKAGES
                            | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES,
                    target.userId);
            Set<String> blockedComponents = getBlockedComponents(target.packageName, target.userId);
            if (packageInfo.services != null) {
                List<ActivityManager.RunningServiceInfo> runningServices = getRunningServices(target);
                for (ServiceInfo serviceInfo : packageInfo.services) {
                    boolean running = isServiceRunning(serviceInfo, runningServices);
                    boolean disabledOrBlocked = !serviceInfo.isEnabled() || blockedComponents.contains(serviceInfo.name);
                    boolean hasPermission = serviceInfo.permission == null
                            || SelfPermissions.checkSelfOrRemotePermission(serviceInfo.permission);
                    actions.addAll(AssistComponentActionPlan.forService(serviceInfo, running, disabledOrBlocked,
                            hasPermission, privilegedAvailable, target.userId, UserHandleHidden.myUserId()));
                }
            }
            if (packageInfo.receivers != null) {
                Map<String, ReceiverIntentDetails> receiverIntentDetails = collectReceiverIntentDetails(packageInfo);
                for (ActivityInfo receiverInfo : packageInfo.receivers) {
                    ReceiverIntentDetails intentDetails = receiverIntentDetails.get(receiverInfo.name);
                    List<String> declaredActions = intentDetails != null ? new ArrayList<>(intentDetails.actions)
                            : Collections.emptyList();
                    List<String> declaredCategories = intentDetails != null ? new ArrayList<>(intentDetails.categories)
                            : Collections.emptyList();
                    boolean disabledOrBlocked = !receiverInfo.isEnabled() || blockedComponents.contains(receiverInfo.name);
                    actions.addAll(AssistComponentActionPlan.forReceiver(receiverInfo, declaredActions,
                            declaredCategories, disabledOrBlocked, privilegedAvailable, target.userId,
                            UserHandleHidden.myUserId()));
                }
            }
        } catch (Throwable th) {
            Log.w(TAG, "Could not load assist component actions for " + target.packageName, th);
        }
        return actions;
    }

    @NonNull
    private List<AssistTargetResolver.ActivityEvent> queryUsageEvents(long beginTime, long endTime, int userId) {
        if (!SelfPermissions.checkUsageStatsPermission()) {
            return Collections.emptyList();
        }
        UsageEvents events;
        try {
            events = UsageStatsManagerCompat.queryEvents(beginTime, endTime, userId);
        } catch (Throwable th) {
            Log.w(TAG, "Could not query usage events for assist target.", th);
            return Collections.emptyList();
        }
        if (events == null) {
            return Collections.emptyList();
        }
        List<AssistTargetResolver.ActivityEvent> activityEvents = new ArrayList<>();
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String packageName = event.getPackageName();
            if (packageName != null) {
                activityEvents.add(new AssistTargetResolver.ActivityEvent(packageName, event.getEventType(),
                        event.getTimeStamp()));
            }
        }
        return activityEvents;
    }

    private void showActionDialog(@NonNull TargetInfo targetInfo) {
        List<ActionItem> actionItems = new ArrayList<>(3 + targetInfo.componentActions.size());
        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
            actionItems.add(new ActionItem(ACTION_FORCE_STOP, getString(R.string.force_stop)));
        }
        if (SelfPermissions.canFreezeUnfreezePackages()) {
            actionItems.add(new ActionItem(ACTION_FREEZE_TOGGLE,
                    getString(targetInfo.frozen ? R.string.unfreeze : R.string.freeze)));
        }
        actionItems.add(new ActionItem(ACTION_OPEN_APP_INFO, getString(R.string.running_apps_open_app_info)));
        for (AssistComponentActionPlan.Action componentAction : targetInfo.componentActions) {
            actionItems.add(new ActionItem(ACTION_COMPONENT, getComponentActionLabel(componentAction), componentAction));
        }
        CharSequence[] labels = new CharSequence[actionItems.size()];
        for (int i = 0; i < actionItems.size(); ++i) {
            labels[i] = actionItems.get(i).label;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.quick_assist_title_for_app, targetInfo.label))
                .setMessage(getString(R.string.quick_assist_target_message, targetInfo.target.packageName))
                .setItems(labels, (dialog, which) -> handleAction(targetInfo, actionItems.get(which)))
                .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showNoTargetDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.quick_assist_no_target_title)
                .setMessage(R.string.quick_assist_no_target_message)
                .setPositiveButton(R.string.running_apps, (dialog, which) -> {
                    startActivity(new Intent(this, RunningAppsActivity.class));
                    finish();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void handleAction(@NonNull TargetInfo targetInfo, @NonNull ActionItem actionItem) {
        if (actionItem.componentAction != null) {
            confirmComponentAction(actionItem.componentAction);
            return;
        }
        switch (actionItem.action) {
            case ACTION_OPEN_APP_INFO:
                startActivity(AppDetailsActivity.getIntent(this, targetInfo.target.packageName,
                        targetInfo.target.userId, true));
                finish();
                return;
            case ACTION_FORCE_STOP:
            case ACTION_FREEZE_TOGGLE:
                runPrivilegedAction(targetInfo, actionItem.action);
        }
    }

    private void confirmComponentAction(@NonNull AssistComponentActionPlan.Action action) {
        String route = getRouteLabel(action);
        String permission = action.requiredPermission == null ? getString(R.string.require_no_permission)
                : action.requiredPermission;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setNegativeButton(R.string.cancel, null);
        switch (action.type) {
            case SERVICE_START:
            case SERVICE_STOP:
                boolean start = action.type == AssistComponentActionPlan.ActionType.SERVICE_START;
                builder.setTitle(start ? R.string.start_service : R.string.stop_service)
                        .setMessage(getString(R.string.quick_assist_service_action_confirm_message,
                                action.componentName, action.userId, route, permission))
                        .setPositiveButton(start ? R.string.start_service : R.string.stop_service,
                                (dialog, which) -> dispatchComponentAction(action))
                        .show();
                return;
            case RECEIVER_BROADCAST:
                String declaredCategories = action.declaredCategories.isEmpty()
                        ? getString(R.string.receiver_broadcast_no_declared_categories)
                        : TextUtils.join(", ", action.declaredCategories);
                builder.setTitle(R.string.receiver_broadcast_title)
                        .setMessage(getString(R.string.receiver_broadcast_confirm_message, action.componentName,
                                action.broadcastAction, action.userId, route, permission, declaredCategories, 0, 0))
                        .setPositiveButton(R.string.send, (dialog, which) -> dispatchComponentAction(action))
                        .show();
        }
    }

    private void dispatchComponentAction(@NonNull AssistComponentActionPlan.Action action) {
        ThreadUtils.postOnBackgroundThread(() -> {
            int toastRes = R.string.done;
            String toastText = null;
            boolean success = false;
            Throwable failure = null;
            try {
                switch (action.type) {
                    case SERVICE_START:
                        if (action.route == AssistComponentActionPlan.DispatchRoute.PRIVILEGED) {
                            ActivityManagerCompat.startService(action.buildIntent(), action.userId, true);
                        } else {
                            startService(action.buildIntent());
                        }
                        toastRes = R.string.service_action_started;
                        success = true;
                        break;
                    case SERVICE_STOP:
                        boolean stopped;
                        if (action.route == AssistComponentActionPlan.DispatchRoute.PRIVILEGED) {
                            stopped = ActivityManagerCompat.stopService(action.buildIntent(), action.userId) != 0;
                        } else {
                            stopped = stopService(action.buildIntent());
                        }
                        toastRes = stopped ? R.string.service_action_stopped : R.string.service_action_stop_no_match;
                        success = true;
                        break;
                    case RECEIVER_BROADCAST:
                        if (action.route == AssistComponentActionPlan.DispatchRoute.PRIVILEGED) {
                            ActivityManagerCompat.sendBroadcast(action.buildIntent(), action.userId);
                        } else {
                            sendBroadcast(action.buildIntent());
                        }
                        toastRes = R.string.receiver_broadcast_sent;
                        success = true;
                        break;
                }
            } catch (Throwable th) {
                failure = th;
                Log.e(TAG, th);
                toastText = getComponentActionFailureMessage(this, action, th);
            }
            recordComponentActionHistory(action, success, failure);
            int finalToastRes = toastRes;
            String finalToastText = toastText;
            ThreadUtils.postOnMainThread(() -> {
                if (finalToastText != null) {
                    UIUtils.displayLongToast(finalToastText);
                } else {
                    UIUtils.displayShortToast(finalToastRes);
                }
                finish();
            });
        });
    }

    private void runPrivilegedAction(@NonNull TargetInfo targetInfo, int action) {
        ThreadUtils.postOnBackgroundThread(() -> {
            int result = runPrivilegedActionInternal(targetInfo, action);
            ThreadUtils.postOnMainThread(() -> {
                if (result != 0) {
                    UIUtils.displayShortToast(result);
                }
                finish();
            });
        });
    }

    private int runPrivilegedActionInternal(@NonNull TargetInfo targetInfo, int action) {
        try {
            switch (action) {
                case ACTION_FORCE_STOP:
                    if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                        return R.string.only_works_in_root_or_adb_mode;
                    }
                    PackageManagerCompat.forceStopPackage(targetInfo.target.packageName, targetInfo.target.userId);
                    return R.string.done;
                case ACTION_FREEZE_TOGGLE:
                    if (!SelfPermissions.canFreezeUnfreezePackages()) {
                        return R.string.only_works_in_root_or_adb_mode;
                    }
                    if (targetInfo.frozen) {
                        FreezeUtils.unfreeze(targetInfo.target.packageName, targetInfo.target.userId);
                    } else {
                        int freezeType = Optional.ofNullable(FreezeUtils.loadFreezeMethod(targetInfo.target.packageName))
                                .orElse(Prefs.Blocking.getDefaultFreezingMethod());
                        FreezeUtils.freeze(targetInfo.target.packageName, targetInfo.target.userId, freezeType);
                    }
                    return R.string.done;
                default:
                    return 0;
            }
        } catch (Throwable th) {
            Log.e(TAG, th);
            return R.string.failed;
        }
    }

    @NonNull
    private String getComponentActionLabel(@NonNull AssistComponentActionPlan.Action action) {
        String componentName = shortenComponentName(action.packageName, action.componentName);
        switch (action.type) {
            case SERVICE_START:
                return getString(R.string.quick_assist_start_service, componentName);
            case SERVICE_STOP:
                return getString(R.string.quick_assist_stop_service, componentName);
            case RECEIVER_BROADCAST:
                return getString(R.string.quick_assist_send_broadcast,
                        componentName + " (" + action.broadcastAction + ")");
            default:
                return componentName;
        }
    }

    @NonNull
    private String getRouteLabel(@NonNull AssistComponentActionPlan.Action action) {
        return getString(action.route == AssistComponentActionPlan.DispatchRoute.PRIVILEGED
                ? R.string.receiver_broadcast_route_privileged
                : R.string.receiver_broadcast_route_unprivileged);
    }

    @NonNull
    private static String getComponentActionFailureMessage(@NonNull Context context,
                                                          @NonNull AssistComponentActionPlan.Action action,
                                                          @NonNull Throwable throwable) {
        String message = throwable.getLocalizedMessage();
        if (TextUtils.isEmpty(message)) {
            message = throwable.getClass().getSimpleName();
        }
        if (action.type == AssistComponentActionPlan.ActionType.RECEIVER_BROADCAST) {
            return context.getString(R.string.receiver_broadcast_failed, message);
        }
        if (action.type == AssistComponentActionPlan.ActionType.SERVICE_START
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getString(R.string.service_action_start_failed_android_o, message);
        }
        return context.getString(R.string.service_action_failed,
                context.getString(action.type == AssistComponentActionPlan.ActionType.SERVICE_START
                        ? R.string.start_service
                        : R.string.stop_service),
                message);
    }

    @WorkerThread
    private void recordComponentActionHistory(@NonNull AssistComponentActionPlan.Action action,
                                              boolean success,
                                              @Nullable Throwable failure) {
        try {
            String operationLabel = getComponentOperationLabel(action);
            SingleAppActionHistoryItem item = new SingleAppActionHistoryItem(
                    SingleAppActionHistoryItem.ACTION_COMPONENT_ACTION,
                    operationLabel,
                    action.packageName,
                    action.userId,
                    action.componentName,
                    getComponentActionHistoryDetail(action));
            OpHistoryManager.addHistoryItem(OpHistoryManager.HISTORY_TYPE_SINGLE_APP_ACTION, item, success,
                    OperationJournalMetadata.forSingleAppAction(this, item, success,
                            OperationJournalMetadata.RISK_MEDIUM, false, failure));
        } catch (Throwable th) {
            Log.e(TAG, "Could not record assistant component action history.", th);
        }
    }

    @NonNull
    private String getComponentOperationLabel(@NonNull AssistComponentActionPlan.Action action) {
        switch (action.type) {
            case SERVICE_START:
                return getString(R.string.quick_assist_op_history_start_service);
            case SERVICE_STOP:
                return getString(R.string.quick_assist_op_history_stop_service);
            case RECEIVER_BROADCAST:
                return getString(R.string.quick_assist_op_history_send_broadcast);
            default:
                return getString(R.string.quick_assist_title);
        }
    }

    @NonNull
    private String getComponentActionHistoryDetail(@NonNull AssistComponentActionPlan.Action action) {
        StringBuilder detail = new StringBuilder()
                .append(getString(R.string.user)).append(": ").append(action.userId)
                .append("; ").append(getString(R.string.quick_assist_history_route))
                .append(": ").append(getRouteLabel(action));
        if (action.broadcastAction != null) {
            detail.append("; ").append(getString(R.string.action)).append(": ").append(action.broadcastAction);
        }
        if (action.requiredPermission != null) {
            detail.append("; ").append(getString(R.string.quick_assist_history_permission))
                    .append(": ").append(action.requiredPermission);
        }
        return detail.toString();
    }

    @NonNull
    private static String shortenComponentName(@NonNull String packageName, @NonNull String componentName) {
        if (componentName.startsWith(packageName)) {
            return componentName.substring(packageName.length());
        }
        return componentName;
    }

    @NonNull
    @WorkerThread
    private static List<ActivityManager.RunningServiceInfo> getRunningServices(@NonNull AssistTargetResolver.Target target) {
        try {
            return ActivityManagerCompat.getRunningServices(target.packageName, target.userId);
        } catch (Throwable th) {
            Log.w(TAG, "Could not query running services for assist target.", th);
            return Collections.emptyList();
        }
    }

    private static boolean isServiceRunning(@NonNull ServiceInfo serviceInfo,
                                            @NonNull List<ActivityManager.RunningServiceInfo> runningServices) {
        for (ActivityManager.RunningServiceInfo runningService : runningServices) {
            if (runningService.service != null && serviceInfo.name.equals(runningService.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @WorkerThread
    private static Set<String> getBlockedComponents(@NonNull String packageName, int userId) {
        try (ComponentsBlocker blocker = ComponentsBlocker.getInstance(packageName, userId)) {
            Set<String> blocked = new HashSet<>();
            for (ComponentRule rule : blocker.getAllComponents()) {
                if (rule.isBlocked()) {
                    blocked.add(rule.name);
                }
            }
            return blocked;
        } catch (Throwable th) {
            Log.w(TAG, "Could not read assistant target component rules.", th);
            return Collections.emptySet();
        }
    }

    @NonNull
    private static Map<String, ReceiverIntentDetails> collectReceiverIntentDetails(@NonNull PackageInfo packageInfo) {
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        if (applicationInfo == null || applicationInfo.publicSourceDir == null) {
            return Collections.emptyMap();
        }
        Map<String, ReceiverIntentDetails> receiverIntentDetails = new HashMap<>();
        try {
            ManifestParser parser = new ManifestParser(
                    ApkUtils.getManifestFromApk(new File(applicationInfo.publicSourceDir)));
            for (ManifestComponent component : parser.parseComponents()) {
                if (!ManifestComponent.TYPE_RECEIVER.equals(component.type)) {
                    continue;
                }
                ReceiverIntentDetails details = new ReceiverIntentDetails();
                for (ManifestIntentFilter filter : component.intentFilters) {
                    details.actions.addAll(filter.actions);
                    details.categories.addAll(filter.categories);
                }
                receiverIntentDetails.put(component.cn.getClassName(), details);
            }
        } catch (Throwable th) {
            Log.w(TAG, "Could not parse receiver intent filters for assistant target.", th);
        }
        return receiverIntentDetails;
    }

    private static final class TargetInfo {
        @NonNull
        final AssistTargetResolver.Target target;
        @NonNull
        final CharSequence label;
        final boolean frozen;
        @NonNull
        final List<AssistComponentActionPlan.Action> componentActions;

        TargetInfo(@NonNull AssistTargetResolver.Target target, @NonNull CharSequence label, boolean frozen,
                   @NonNull List<AssistComponentActionPlan.Action> componentActions) {
            this.target = target;
            this.label = label;
            this.frozen = frozen;
            this.componentActions = componentActions;
        }
    }

    private static final class ActionItem {
        final int action;
        @NonNull
        final CharSequence label;
        @Nullable
        final AssistComponentActionPlan.Action componentAction;

        ActionItem(int action, @NonNull CharSequence label) {
            this(action, label, null);
        }

        ActionItem(int action, @NonNull CharSequence label,
                   @Nullable AssistComponentActionPlan.Action componentAction) {
            this.action = action;
            this.label = label;
            this.componentAction = componentAction;
        }
    }

    private static final class ReceiverIntentDetails {
        final Set<String> actions = new LinkedHashSet<>();
        final Set<String> categories = new LinkedHashSet<>();
    }
}
