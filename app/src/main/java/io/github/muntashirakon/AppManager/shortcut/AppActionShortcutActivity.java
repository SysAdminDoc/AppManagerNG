// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class AppActionShortcutActivity extends BaseActivity {
    private static final String TAG = AppActionShortcutActivity.class.getSimpleName();

    private static final String EXTRA_PACKAGE_NAME = "pkg";
    private static final String EXTRA_USER_ID = "user";
    private static final String EXTRA_ACTION = "action";

    @NonNull
    public static Intent getIntent(@NonNull Context context,
                                   @NonNull String packageName,
                                   int userId,
                                   @NonNull @AppActionShortcutInfo.ShortcutAction String action) {
        Intent intent = new Intent(context, AppActionShortcutActivity.class);
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(EXTRA_USER_ID, userId);
        intent.putExtra(EXTRA_ACTION, action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        String packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        String action = getIntent().getStringExtra(EXTRA_ACTION);
        int userId = getIntent().getIntExtra(EXTRA_USER_ID, UserHandleHidden.myUserId());
        if (packageName == null || action == null) {
            finish();
            return;
        }
        ThreadUtils.postOnBackgroundThread(() -> {
            int result = runAction(packageName, userId, action);
            ThreadUtils.postOnMainThread(() -> {
                if (result != 0) {
                    UIUtils.displayShortToast(result);
                }
                finish();
            });
        });
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }

    private int runAction(@NonNull String packageName,
                          int userId,
                          @NonNull String action) {
        try {
            switch (action) {
                case AppActionShortcutInfo.ACTION_FREEZE:
                    if (!SelfPermissions.canFreezeUnfreezePackages()) {
                        return R.string.only_works_in_root_or_adb_mode;
                    }
                    int freezeType = java.util.Optional.ofNullable(FreezeUtils.loadFreezeMethod(packageName))
                            .orElse(Prefs.Blocking.getDefaultFreezingMethod());
                    FreezeUtils.freeze(packageName, userId, freezeType);
                    return R.string.done;
                case AppActionShortcutInfo.ACTION_FORCE_STOP:
                    if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES)) {
                        return R.string.only_works_in_root_or_adb_mode;
                    }
                    PackageManagerCompat.forceStopPackage(packageName, userId);
                    return R.string.done;
                case AppActionShortcutInfo.ACTION_CLEAR_CACHE:
                    if (!SelfPermissions.canClearAppCache()) {
                        return R.string.only_works_in_root_or_adb_mode;
                    }
                    return PackageManagerCompat.deleteApplicationCacheFilesAsUser(packageName, userId)
                            ? R.string.done : R.string.failed;
                default:
                    Log.w(TAG, "Unknown app action shortcut: " + action);
                    return 0;
            }
        } catch (Throwable th) {
            Log.e(TAG, th);
            return R.string.failed;
        }
    }
}
