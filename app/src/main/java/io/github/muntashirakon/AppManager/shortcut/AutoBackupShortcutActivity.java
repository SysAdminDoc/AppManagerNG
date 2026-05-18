// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.schedule.AutoBackupScheduler;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class AutoBackupShortcutActivity extends BaseActivity {
    private static final String TAG = AutoBackupShortcutActivity.class.getSimpleName();

    public static final String ACTION_RUN_SCHEDULED_BACKUP =
            "io.github.muntashirakon.AppManager.shortcut.action.RUN_SCHEDULED_BACKUP";

    private static final String SHORTCUT_ID = "scheduled_auto_backup:run_now";

    @NonNull
    public static Intent getIntent(@NonNull Context context) {
        Intent intent = new Intent(context, AutoBackupShortcutActivity.class);
        intent.setAction(ACTION_RUN_SCHEDULED_BACKUP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static boolean requestPinShortcut(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(appContext)) {
            return false;
        }
        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(appContext, SHORTCUT_ID)
                .setShortLabel(appContext.getString(R.string.shortcut_auto_backup_short))
                .setLongLabel(appContext.getString(R.string.shortcut_auto_backup_long))
                .setIcon(IconCompat.createWithResource(appContext, R.drawable.ic_backup_restore))
                .setIntent(getIntent(appContext))
                .build();
        return ShortcutManagerCompat.requestPinShortcut(appContext, shortcut, null);
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        if (!ACTION_RUN_SCHEDULED_BACKUP.equals(action)) {
            Log.w(TAG, "Ignoring unknown scheduled-backup shortcut action: " + action);
            finish();
            return;
        }
        AutoBackupScheduler.enqueueManualRun(this);
        UIUtils.displayShortToast(R.string.pref_backup_schedule_run_now_queued);
        finish();
    }

    @Override
    public boolean getTransparentBackground() {
        return true;
    }
}
