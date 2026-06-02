// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_BACKUP;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_DISABLE_COMPONENT;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_ENABLE_COMPONENT;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_INSTALL_FROM_URI;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_RESTORE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_RUN_PROFILE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_SCAN_TRACKERS;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_BACKUP_FLAGS;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_BACKUP_NAME;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_COMPONENT;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_DRY_RUN;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PACKAGES;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PACKAGE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_ID;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_OVERRIDES;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_PROFILE_STATE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_URI;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_USER;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.EXTRA_USERS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerActivity;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptions;
import io.github.muntashirakon.AppManager.batchops.struct.BatchComponentOptions;
import io.github.muntashirakon.AppManager.batchops.struct.IBatchOpOptions;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierService;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.ProfileQueueItem;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.io.Path;

public class AutomationReceiver extends BroadcastReceiver {
    private static final String TAG = AutomationReceiver.class.getSimpleName();

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null || !AutomationIntents.isAutomationAction(intent.getAction())) {
            return;
        }
        try {
            dispatch(context, intent);
        } catch (Throwable th) {
            Log.w(TAG, "Rejected automation intent " + intent.getAction(), th);
        }
    }

    private void dispatch(@NonNull Context context, @NonNull Intent intent) throws IOException, JSONException {
        String action = Objects.requireNonNull(intent.getAction());
        if (ACTION_RUN_PROFILE.equals(action)) {
            dispatchProfile(context, intent);
            return;
        }
        if (ACTION_INSTALL_FROM_URI.equals(action)) {
            dispatchInstallFromUri(context, intent);
            return;
        }
        if (ACTION_SCAN_TRACKERS.equals(action)) {
            dispatchTrackerScan(context, intent);
            return;
        }
        if (ACTION_BACKUP.equals(action) && !intent.hasExtra(EXTRA_PACKAGE) && !intent.hasExtra(EXTRA_PACKAGES)
                && intent.hasExtra(EXTRA_PROFILE_ID)) {
            dispatchProfile(context, intent);
            return;
        }
        dispatchBatchOperation(context, intent, action);
    }

    private void dispatchBatchOperation(@NonNull Context context, @NonNull Intent intent,
                                        @NonNull String action) {
        Integer op = AutomationIntents.getBatchOpForAction(action);
        if (op == null) {
            throw new IllegalArgumentException("Unsupported batch automation action: " + action);
        }
        ArrayList<String> packages = requirePackages(intent);
        ArrayList<Integer> users = getUsers(intent, packages.size());
        if ((ACTION_DISABLE_COMPONENT.equals(action) || ACTION_ENABLE_COMPONENT.equals(action))
                && packages.size() != 1) {
            throw new IllegalArgumentException("Component automation expects exactly one package");
        }
        IBatchOpOptions options = getOptionsForIntent(intent, action, packages.get(0));
        if (AutomationIntents.readBooleanExtra(intent, EXTRA_DRY_RUN, false)) {
            return;
        }
        BatchQueueItem queueItem = BatchQueueItem.getBatchOpQueue(op, packages, users, options);
        Intent serviceIntent = BatchOpsService.getServiceIntent(context, queueItem);
        startForegroundService(context, serviceIntent);
    }

    private void dispatchProfile(@NonNull Context context, @NonNull Intent intent) throws IOException, JSONException {
        String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
        if (profileId == null || profileId.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + EXTRA_PROFILE_ID);
        }
        String normalizedProfileId = ProfileManager.getProfileIdCompat(profileId.trim());
        String state = intent.getStringExtra(EXTRA_PROFILE_STATE);
        JSONObject profileOverrides = getProfileOverrides(intent.getStringExtra(EXTRA_PROFILE_OVERRIDES));
        if (AutomationIntents.readBooleanExtra(intent, EXTRA_DRY_RUN, false)) {
            return;
        }
        PendingResult pendingResult = goAsync();
        try {
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    Path profilePath = ProfileManager.findProfilePathById(normalizedProfileId);
                    BaseProfile profile = BaseProfile.fromPath(profilePath);
                    Intent serviceIntent = ProfileApplierService.getIntent(context,
                            ProfileQueueItem.fromProfile(profile, state, profileOverrides), true);
                    startForegroundService(context, serviceIntent);
                } catch (IOException | JSONException e) {
                    Log.w(TAG, "Could not dispatch profile automation for " + normalizedProfileId, e);
                } finally {
                    pendingResult.finish();
                }
            });
        } catch (Throwable th) {
            // If the task could never be enqueued (executor rejected/shut down) the
            // Runnable's finally never runs, so finish the PendingResult here to
            // avoid leaking the receiver past its allowed window.
            Log.w(TAG, "Could not post profile automation for " + normalizedProfileId, th);
            pendingResult.finish();
        }
    }

    @Nullable
    private JSONObject getProfileOverrides(@Nullable String value) throws JSONException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new JSONObject(value.trim());
    }

    private void dispatchInstallFromUri(@NonNull Context context, @NonNull Intent intent) {
        String uriString = intent.getStringExtra(EXTRA_URI);
        if (uriString == null || uriString.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing " + EXTRA_URI);
        }
        Uri uri = Uri.parse(uriString.trim());
        if (AutomationIntents.readBooleanExtra(intent, EXTRA_DRY_RUN, false)) {
            return;
        }
        Intent installerIntent = new Intent(context, PackageInstallerActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(installerIntent);
    }

    private void dispatchTrackerScan(@NonNull Context context, @NonNull Intent intent) {
        ArrayList<String> packages = requirePackages(intent);
        ArrayList<Integer> users = getUsers(intent, packages.size());
        if (AutomationIntents.readBooleanExtra(intent, EXTRA_DRY_RUN, false)) {
            return;
        }
        Intent detailsIntent = AppDetailsActivity.getIntentForTrackers(context, packages.get(0), users.get(0))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(detailsIntent);
    }

    @Nullable
    private IBatchOpOptions getOptionsForIntent(@NonNull Intent intent, @NonNull String action,
                                                @NonNull String firstPackageName) {
        if (ACTION_DISABLE_COMPONENT.equals(action) || ACTION_ENABLE_COMPONENT.equals(action)) {
            String componentName = intent.getStringExtra(EXTRA_COMPONENT);
            if (componentName == null || componentName.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing " + EXTRA_COMPONENT);
            }
            return new BatchComponentOptions(new String[]{
                    AutomationIntents.normalizeComponentName(firstPackageName, componentName)
            });
        }
        if (ACTION_BACKUP.equals(action) || ACTION_RESTORE.equals(action)) {
            int flags = intent.hasExtra(EXTRA_BACKUP_FLAGS)
                    ? AutomationIntents.readIntExtra(intent, EXTRA_BACKUP_FLAGS, BackupFlags.BACKUP_NOTHING)
                    : BackupFlags.fromPref().getFlags();
            String backupName = intent.getStringExtra(EXTRA_BACKUP_NAME);
            String[] backupNames = backupName != null && !backupName.trim().isEmpty()
                    ? new String[]{backupName.trim()}
                    : null;
            return new BatchBackupOptions(flags, backupNames, null);
        }
        return null;
    }

    @NonNull
    private ArrayList<String> requirePackages(@NonNull Intent intent) {
        ArrayList<String> packages = intent.getStringArrayListExtra(EXTRA_PACKAGES);
        if (packages == null) {
            String[] packageArray = intent.getStringArrayExtra(EXTRA_PACKAGES);
            if (packageArray != null) {
                packages = new ArrayList<>(Arrays.asList(packageArray));
            } else {
                // Many automation tools can only send string extras, so accept a
                // comma/newline-separated EXTRA_PACKAGES string too (matching the
                // am:// URI path) instead of silently ignoring it.
                packages = new ArrayList<>(AutomationIntents.splitValues(
                        intent.getStringExtra(EXTRA_PACKAGES)));
            }
        }
        String packageName = intent.getStringExtra(EXTRA_PACKAGE);
        if (packageName != null) {
            packages.add(packageName);
        }
        packages.removeAll(Collections.singleton(null));
        for (int i = 0; i < packages.size(); ++i) {
            String normalizedPackageName = packages.get(i).trim();
            if (!PackageUtils.validateName(normalizedPackageName)) {
                throw new IllegalArgumentException("Invalid package name: " + normalizedPackageName);
            }
            packages.set(i, normalizedPackageName);
        }
        if (packages.isEmpty()) {
            throw new IllegalArgumentException("Missing " + EXTRA_PACKAGE);
        }
        return packages;
    }

    @NonNull
    private ArrayList<Integer> getUsers(@NonNull Intent intent, int packageCount) {
        ArrayList<Integer> users = intent.getIntegerArrayListExtra(EXTRA_USERS);
        if (users == null) {
            int[] userArray = intent.getIntArrayExtra(EXTRA_USERS);
            users = new ArrayList<>();
            if (userArray != null) {
                for (int userId : userArray) {
                    users.add(userId);
                }
            } else {
                // Accept a comma/newline-separated EXTRA_USERS string too.
                for (String token : AutomationIntents.splitValues(intent.getStringExtra(EXTRA_USERS))) {
                    try {
                        users.add(Integer.parseInt(token));
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        }
        if (users.isEmpty()) {
            int userId = AutomationIntents.readIntExtra(intent, EXTRA_USER, UserHandleHidden.myUserId());
            for (int i = 0; i < packageCount; ++i) {
                users.add(userId);
            }
        } else if (users.size() == 1 && packageCount > 1) {
            int userId = users.get(0);
            while (users.size() < packageCount) {
                users.add(userId);
            }
        }
        if (users.size() != packageCount) {
            throw new IllegalArgumentException(EXTRA_USERS + " size must match package count");
        }
        return users;
    }

    private void startForegroundService(@NonNull Context context, @NonNull Intent serviceIntent) {
        try {
            ContextCompat.startForegroundService(context, serviceIntent);
        } catch (Throwable th) {
            Log.w(TAG, "Could not start automation service", th);
        }
    }
}
