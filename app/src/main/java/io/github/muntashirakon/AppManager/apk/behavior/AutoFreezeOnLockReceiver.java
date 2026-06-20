// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.List;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.rules.struct.FreezeRule;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;

public class AutoFreezeOnLockReceiver extends BroadcastReceiver {
    private static final String TAG = AutoFreezeOnLockReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) return;
        if (!Prefs.Blocking.isAutoFreezeOnLockEnabled()) return;

        int delaySeconds = Prefs.Blocking.getAutoFreezeDelaySeconds();
        if (delaySeconds > 0) {
            ThreadUtils.postOnBackgroundThread(() -> {
                try {
                    Thread.sleep(delaySeconds * 1000L);
                } catch (InterruptedException e) {
                    return;
                }
                freezeAllRuledPackages(context);
            });
        } else {
            ThreadUtils.postOnBackgroundThread(() -> freezeAllRuledPackages(context));
        }
    }

    @VisibleForTesting
    static void freezeAllRuledPackages(@NonNull Context context) {
        try {
            List<FreezeRule> rules = RulesStorageManager.getAllFreezeRules();
            int frozen = 0;
            for (FreezeRule rule : rules) {
                try {
                    FreezeUtils.freeze(rule.packageName, rule.getFreezeType());
                    frozen++;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to freeze %s", e, rule.packageName);
                }
            }
            if (frozen > 0) {
                Log.i(TAG, "Auto-freeze on lock: froze %d/%d packages", frozen, rules.size());
            }
        } catch (Exception e) {
            Log.e(TAG, "Auto-freeze on lock failed", e);
        }
    }
}
