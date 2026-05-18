// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.assistant;

import android.app.usage.UsageEvents;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.UsageStatsManagerCompat;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
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
            return new TargetInfo(target, label, FreezeUtils.isFrozen(applicationInfo));
        } catch (Throwable th) {
            Log.w(TAG, "Could not resolve assist target " + target.packageName, th);
            return null;
        }
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
        List<ActionItem> actionItems = new ArrayList<>(3);
        if (SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
            actionItems.add(new ActionItem(ACTION_FORCE_STOP, getString(R.string.force_stop)));
        }
        if (SelfPermissions.canFreezeUnfreezePackages()) {
            actionItems.add(new ActionItem(ACTION_FREEZE_TOGGLE,
                    getString(targetInfo.frozen ? R.string.unfreeze : R.string.freeze)));
        }
        actionItems.add(new ActionItem(ACTION_OPEN_APP_INFO, getString(R.string.running_apps_open_app_info)));
        CharSequence[] labels = new CharSequence[actionItems.size()];
        for (int i = 0; i < actionItems.size(); ++i) {
            labels[i] = actionItems.get(i).label;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.quick_assist_title_for_app, targetInfo.label))
                .setMessage(getString(R.string.quick_assist_target_message, targetInfo.target.packageName))
                .setItems(labels, (dialog, which) -> handleAction(targetInfo, actionItems.get(which).action))
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

    private void handleAction(@NonNull TargetInfo targetInfo, int action) {
        switch (action) {
            case ACTION_OPEN_APP_INFO:
                startActivity(AppDetailsActivity.getIntent(this, targetInfo.target.packageName,
                        targetInfo.target.userId, true));
                finish();
                return;
            case ACTION_FORCE_STOP:
            case ACTION_FREEZE_TOGGLE:
                runPrivilegedAction(targetInfo, action);
        }
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

    private static final class TargetInfo {
        @NonNull
        final AssistTargetResolver.Target target;
        @NonNull
        final CharSequence label;
        final boolean frozen;

        TargetInfo(@NonNull AssistTargetResolver.Target target, @NonNull CharSequence label, boolean frozen) {
            this.target = target;
            this.label = label;
            this.frozen = frozen;
        }
    }

    private static final class ActionItem {
        final int action;
        @NonNull
        final CharSequence label;

        ActionItem(int action, @NonNull CharSequence label) {
            this.action = action;
            this.label = label;
        }
    }
}
