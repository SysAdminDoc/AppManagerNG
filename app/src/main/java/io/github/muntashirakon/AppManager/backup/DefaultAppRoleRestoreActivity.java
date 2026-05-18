// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.DefaultAppRoleBackupHelper.RoleRebindRequest;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class DefaultAppRoleRestoreActivity extends BaseActivity {
    private static final String EXTRA_ROLE_REBIND_REQUESTS = "role_rebind_requests";

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull List<RoleRebindRequest> requests) {
        Intent intent = new Intent(context, DefaultAppRoleRestoreActivity.class);
        intent.putParcelableArrayListExtra(EXTRA_ROLE_REBIND_REQUESTS, new ArrayList<>(requests));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.setIdentifier(String.valueOf(System.currentTimeMillis()));
        }
        return intent;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        if (getIntent() == null) {
            finish();
            return;
        }
        ArrayList<RoleRebindRequest> requests = IntentCompat.getParcelableArrayListExtra(
                getIntent(), EXTRA_ROLE_REBIND_REQUESTS, RoleRebindRequest.class);
        if (requests == null || requests.isEmpty()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore_default_apps_title)
                .setMessage(buildMessage(requests))
                .setNegativeButton(R.string.close, (dialog, which) -> finish())
                .setPositiveButton(R.string.restore_default_apps_open_settings, (dialog, which) -> openDefaultAppsSettings())
                .setOnDismissListener(dialog -> finish())
                .show();
    }

    @NonNull
    private CharSequence buildMessage(@NonNull List<RoleRebindRequest> requests) {
        Map<String, List<String>> roleLabelsByPackage = new LinkedHashMap<>();
        for (RoleRebindRequest request : requests) {
            String packageName = request.getPackageName();
            List<String> roleLabels = roleLabelsByPackage.get(packageName);
            if (roleLabels == null) {
                roleLabels = new ArrayList<>();
                roleLabelsByPackage.put(packageName, roleLabels);
            }
            roleLabels.add(DefaultAppRoleBackupHelper.getRoleLabel(this, request.getRoleName()).toString());
        }
        StringBuilder message = new StringBuilder(getString(R.string.restore_default_apps_message));
        for (Map.Entry<String, List<String>> entry : roleLabelsByPackage.entrySet()) {
            CharSequence appLabel = PackageUtils.getPackageLabel(getPackageManager(), entry.getKey(),
                    findUserIdForPackage(requests, entry.getKey()));
            message.append("\n\n")
                    .append(appLabel)
                    .append(" (")
                    .append(entry.getKey())
                    .append(")\n")
                    .append(TextUtils.join(", ", entry.getValue()));
        }
        return message;
    }

    private int findUserIdForPackage(@NonNull List<RoleRebindRequest> requests, @NonNull String packageName) {
        for (RoleRebindRequest request : requests) {
            if (packageName.equals(request.getPackageName())) {
                return request.getUserId();
            }
        }
        return 0;
    }

    private void openDefaultAppsSettings() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                UIUtils.displayShortToast(R.string.failed);
            }
        } finally {
            finish();
        }
    }
}
