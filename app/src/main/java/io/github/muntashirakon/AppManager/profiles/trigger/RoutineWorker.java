// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.profiles.ProfileApplierService;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.ProfileQueueItem;
import io.github.muntashirakon.AppManager.profiles.struct.BaseProfile;
import io.github.muntashirakon.io.Path;

public class RoutineWorker extends Worker {
    static final String KEY_TRIGGER_ID = "trigger_id";
    private static final String TAG = RoutineWorker.class.getSimpleName();

    public RoutineWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String triggerId = getInputData().getString(KEY_TRIGGER_ID);
        return executeTrigger(context, new ProfileTriggerStore(context), triggerId, profile -> {
            Intent intent = ProfileApplierService.getIntent(context,
                    ProfileQueueItem.fromProfile(profile, BaseProfile.STATE_ON), true);
            ContextCompat.startForegroundService(context, intent);
        });
    }

    @VisibleForTesting
    @NonNull
    static Result executeTrigger(@NonNull Context context,
                                 @NonNull ProfileTriggerStore store,
                                 String triggerId,
                                 @NonNull ProfileStarter starter) {
        if (triggerId == null || triggerId.isEmpty()) {
            return Result.success();
        }
        ProfileTrigger trigger = store.find(triggerId);
        if (trigger == null) {
            return Result.success();
        }
        if (!trigger.enabled) {
            RoutineScheduler.recordRunResult(context, trigger.id,
                    context.getString(R.string.profile_trigger_result_disabled));
            return Result.success();
        }
        try {
            Path profilePath = ProfileManager.findProfilePathById(trigger.profileId);
            if (profilePath == null || !profilePath.exists()) {
                store.setEnabled(trigger.id, false);
                // Also tear down the periodic WorkManager job: setEnabled() only
                // flips the persisted flag, so without this the now-disabled
                // trigger keeps waking the device on every interval, re-runs this
                // worker, hits the !enabled branch, and rewrites the same result
                // forever — store state and scheduled work drift permanently.
                cancelQuietly(context, trigger);
                RoutineScheduler.recordRunResult(context, trigger.id,
                        context.getString(R.string.profile_trigger_result_profile_missing));
                return Result.success();
            }
            BaseProfile profile = BaseProfile.fromPath(profilePath);
            starter.start(profile);
            RoutineScheduler.recordRunResult(context, trigger.id,
                    context.getString(R.string.profile_trigger_result_started, profile.name));
            return Result.success();
        } catch (Throwable th) {
            if (th instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.w(TAG, "Routine trigger failed", th);
            store.setEnabled(trigger.id, false);
            cancelQuietly(context, trigger);
            String message = th.getMessage() != null ? th.getMessage() : th.getClass().getSimpleName();
            RoutineScheduler.recordRunResult(context, trigger.id,
                    context.getString(R.string.profile_trigger_result_failed_disabled, message));
            return Result.success();
        }
    }

    /**
     * Cancel the trigger's scheduled work as a best-effort cleanup step. This
     * runs while a trigger is being force-disabled (missing profile, or an
     * exception while applying it), so a failure here must never prevent the
     * trigger from actually being disabled and its result recorded — those are
     * the operation's real outcome, the cancel is only cleanup.
     * {@code WorkManager.getInstance()} can throw (e.g. if the WorkManager
     * singleton is unavailable in the current process state), so swallow and
     * log rather than letting it abort {@link #executeTrigger}.
     */
    private static void cancelQuietly(@NonNull Context context, @NonNull ProfileTrigger trigger) {
        try {
            RoutineScheduler.cancel(context, trigger);
        } catch (Throwable th) {
            Log.w(TAG, "Could not cancel scheduled work for trigger " + trigger.id, th);
        }
    }

    @VisibleForTesting
    interface ProfileStarter {
        void start(@NonNull BaseProfile profile) throws Exception;
    }
}
