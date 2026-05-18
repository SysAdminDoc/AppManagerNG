// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.automation;

import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_BACKUP;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_DISABLE_COMPONENT;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_ENABLE_COMPONENT;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_INSTALL_FROM_URI;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_RESTORE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_RUN_PROFILE;
import static io.github.muntashirakon.AppManager.automation.AutomationIntents.ACTION_SCAN_TRACKERS;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;

import java.io.IOException;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
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
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;

public class AutomationUriActivity extends BaseActivity {
    private static final String TAG = AutomationUriActivity.class.getSimpleName();

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    private void handleIntent(@Nullable Intent intent) {
        if (intent == null) {
            finish();
            return;
        }
        AutomationRequest request;
        try {
            request = AutomationRequest.fromIntent(intent);
        } catch (Throwable th) {
            Log.w(TAG, "Rejected public automation request", th);
            UIUtils.displayShortToast(R.string.automation_request_invalid);
            finish();
            return;
        }
        if (request == null) {
            UIUtils.displayShortToast(R.string.automation_request_invalid);
            finish();
            return;
        }
        if (request.dryRun) {
            UIUtils.displayShortToast(R.string.automation_request_dry_run_valid);
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.automation_request_confirm_title)
                .setMessage(getConfirmationMessage(request))
                .setPositiveButton(R.string.apply_now, (dialog, which) -> dispatch(request))
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(dialog -> finish())
                .show();
    }

    @NonNull
    private String getConfirmationMessage(@NonNull AutomationRequest request) {
        String message = getString(R.string.automation_request_confirm_message,
                getActionTitle(request),
                getTargetLabel(request),
                getUserLabel(request));
        if (request.profileOverrides != null) {
            message += "\n" + getString(R.string.automation_request_profile_overrides_present);
        }
        return message;
    }

    @NonNull
    private String getActionTitle(@NonNull AutomationRequest request) {
        if (ACTION_RUN_PROFILE.equals(request.action)) {
            return getString(R.string.profiles);
        }
        if (ACTION_INSTALL_FROM_URI.equals(request.action)) {
            return getString(R.string.install);
        }
        if (ACTION_SCAN_TRACKERS.equals(request.action)) {
            return getString(R.string.trackers);
        }
        Integer op = AutomationIntents.getBatchOpForAction(request.action);
        return op != null ? BatchOpsService.getDesiredOpTitle(this, op) : getString(R.string.batch_ops);
    }

    @NonNull
    private String getTargetLabel(@NonNull AutomationRequest request) {
        if (ACTION_RUN_PROFILE.equals(request.action)) {
            return getString(R.string.automation_request_profile_target, request.profileId);
        }
        if (ACTION_INSTALL_FROM_URI.equals(request.action)) {
            return getString(R.string.automation_request_install_target, request.uri);
        }
        if ((ACTION_DISABLE_COMPONENT.equals(request.action) || ACTION_ENABLE_COMPONENT.equals(request.action))
                && request.component != null && !request.packages.isEmpty()) {
            return getString(R.string.automation_request_component_target,
                    request.packages.get(0), request.component);
        }
        if (request.packages.size() == 1) {
            return request.packages.get(0);
        }
        return getResources().getQuantityString(R.plurals.automation_request_package_count,
                request.packages.size(), request.packages.size());
    }

    @NonNull
    private String getUserLabel(@NonNull AutomationRequest request) {
        if (request.users.isEmpty()) {
            return getString(R.string.automation_request_user_current);
        }
        return TextUtils.join(", ", request.users);
    }

    private void dispatch(@NonNull AutomationRequest request) {
        try {
            if (ACTION_RUN_PROFILE.equals(request.action)) {
                dispatchProfile(request);
            } else if (ACTION_INSTALL_FROM_URI.equals(request.action)) {
                dispatchInstall(request);
            } else if (ACTION_SCAN_TRACKERS.equals(request.action)) {
                dispatchTrackerScan(request);
            } else {
                dispatchBatchOperation(request);
            }
        } catch (Throwable th) {
            Log.w(TAG, "Could not dispatch public automation request", th);
            UIUtils.displayShortToast(R.string.failed);
        }
    }

    private void dispatchBatchOperation(@NonNull AutomationRequest request) {
        Integer op = AutomationIntents.getBatchOpForAction(request.action);
        if (op == null) {
            throw new IllegalArgumentException("Unsupported batch automation action: " + request.action);
        }
        IBatchOpOptions options = getOptionsForRequest(request);
        BatchQueueItem queueItem = BatchQueueItem.getBatchOpQueue(op, request.packages, request.users, options);
        Intent serviceIntent = BatchOpsService.getServiceIntent(this, queueItem);
        ContextCompat.startForegroundService(this, serviceIntent);
        UIUtils.displayShortToast(R.string.automation_request_queued);
    }

    private void dispatchProfile(@NonNull AutomationRequest request) {
        Context appContext = getApplicationContext();
        String profileId = ProfileManager.getProfileIdCompat(Objects.requireNonNull(request.profileId));
        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                Path profilePath = ProfileManager.findProfilePathById(profileId);
                BaseProfile profile = BaseProfile.fromPath(profilePath);
                ProfileQueueItem queueItem = ProfileQueueItem.fromProfile(profile,
                        request.profileState, request.profileOverrides);
                Intent serviceIntent = ProfileApplierService.getIntent(appContext, queueItem, true);
                ThreadUtils.postOnMainThread(() -> {
                    ContextCompat.startForegroundService(appContext, serviceIntent);
                    UIUtils.displayShortToast(R.string.automation_request_queued);
                });
            } catch (IOException | JSONException | RuntimeException e) {
                Log.w(TAG, "Could not dispatch public profile automation request", e);
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed));
            }
        });
    }

    private void dispatchInstall(@NonNull AutomationRequest request) {
        Intent installerIntent = new Intent(this, PackageInstallerActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(Objects.requireNonNull(request.uri)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(installerIntent);
    }

    private void dispatchTrackerScan(@NonNull AutomationRequest request) {
        Intent detailsIntent = AppDetailsActivity.getIntentForTrackers(this,
                request.packages.get(0), request.users.get(0))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(detailsIntent);
    }

    @Nullable
    private IBatchOpOptions getOptionsForRequest(@NonNull AutomationRequest request) {
        if (ACTION_DISABLE_COMPONENT.equals(request.action) || ACTION_ENABLE_COMPONENT.equals(request.action)) {
            return new BatchComponentOptions(new String[]{
                    AutomationIntents.normalizeComponentName(request.packages.get(0),
                            Objects.requireNonNull(request.component))
            });
        }
        if (ACTION_BACKUP.equals(request.action) || ACTION_RESTORE.equals(request.action)) {
            int flags = request.hasBackupFlags ? request.backupFlags : BackupFlags.fromPref().getFlags();
            String[] backupNames = request.backupName != null ? new String[]{request.backupName} : null;
            return new BatchBackupOptions(flags, backupNames, null);
        }
        return null;
    }
}
