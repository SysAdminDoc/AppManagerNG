// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;

public final class RoutineScheduler {
    public static final String WORK_TAG = "routine_trigger";
    public static final long DAILY_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1);
    public static final long MIN_PERIODIC_INTERVAL_MILLIS = PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS;

    private static final String UNIQUE_WORK_PREFIX = "routine_trigger_";
    private static final String PREFS_NAME = "profile_trigger_runs";
    private static final String KEY_LAST_RUN_PREFIX = "last_run_";
    private static final String KEY_LAST_RESULT_PREFIX = "last_result_";
    private static final String KEY_LAST_DIAGNOSTICS_PREFIX = "last_diagnostics_";

    private RoutineScheduler() {
    }

    public static void applyAll(@NonNull Context context) {
        ProfileTriggerStore store = new ProfileTriggerStore(context);
        for (ProfileTrigger trigger : store.all()) {
            scheduleOrCancel(context, trigger);
        }
    }

    public static void scheduleOrCancel(@NonNull Context context, @NonNull ProfileTrigger trigger) {
        if (trigger.enabled && trigger.type != ProfileTrigger.TYPE_ON_BOOT) {
            schedule(context, trigger);
        } else {
            cancel(context, trigger);
        }
    }

    public static void schedule(@NonNull Context context, @NonNull ProfileTrigger trigger) {
        if (trigger.type == ProfileTrigger.TYPE_ON_BOOT) {
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                RoutineWorker.class, getRepeatIntervalMillis(trigger), TimeUnit.MILLISECONDS)
                .setInitialDelay(getInitialDelayMillis(trigger, System.currentTimeMillis()), TimeUnit.MILLISECONDS)
                .setInputData(inputData(trigger.id))
                .setConstraints(buildConstraints(trigger))
                .addTag(WORK_TAG)
                .addTag(workTagForTrigger(trigger.id))
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                uniqueWorkName(trigger.id), ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void enqueueBootTriggers(@NonNull Context context) {
        ProfileTriggerStore store = new ProfileTriggerStore(context);
        for (ProfileTrigger trigger : store.all()) {
            if (trigger.enabled && trigger.type == ProfileTrigger.TYPE_ON_BOOT) {
                enqueueOneTime(context, trigger);
            }
        }
    }

    public static void enqueueOneTime(@NonNull Context context, @NonNull ProfileTrigger trigger) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RoutineWorker.class)
                .setInputData(inputData(trigger.id))
                .addTag(WORK_TAG)
                .addTag(workTagForTrigger(trigger.id))
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                uniqueWorkName(trigger.id), ExistingWorkPolicy.REPLACE, request);
    }

    public static void cancel(@NonNull Context context, @NonNull ProfileTrigger trigger) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(uniqueWorkName(trigger.id));
    }

    public static void recordRunResult(@NonNull Context context,
                                       @NonNull String triggerId,
                                       @NonNull String result) {
        SharedPreferences prefs = getRunPrefs(context);
        prefs.edit()
                .putLong(KEY_LAST_RUN_PREFIX + triggerId, System.currentTimeMillis())
                .putString(KEY_LAST_RESULT_PREFIX + triggerId, result)
                .apply();
    }

    public static void recordDiagnostics(@NonNull Context context,
                                         @NonNull String triggerId,
                                         @NonNull String diagnostics) {
        getRunPrefs(context).edit()
                .putString(KEY_LAST_DIAGNOSTICS_PREFIX + triggerId, diagnostics)
                .apply();
    }

    public static void clearStoredState(@NonNull Context context, @NonNull String triggerId) {
        getRunPrefs(context).edit()
                .remove(KEY_LAST_RUN_PREFIX + triggerId)
                .remove(KEY_LAST_RESULT_PREFIX + triggerId)
                .remove(KEY_LAST_DIAGNOSTICS_PREFIX + triggerId)
                .apply();
    }

    public static long getLastRunMillis(@NonNull Context context, @NonNull String triggerId) {
        return getRunPrefs(context).getLong(KEY_LAST_RUN_PREFIX + triggerId, 0L);
    }

    @Nullable
    public static String getLastResult(@NonNull Context context, @NonNull String triggerId) {
        return getRunPrefs(context).getString(KEY_LAST_RESULT_PREFIX + triggerId, null);
    }

    @Nullable
    public static String getLastDiagnostics(@NonNull Context context, @NonNull String triggerId) {
        return getRunPrefs(context).getString(KEY_LAST_DIAGNOSTICS_PREFIX + triggerId, null);
    }

    @NonNull
    public static String refreshDiagnostics(@NonNull Context context, @NonNull ProfileTrigger trigger) {
        try {
            String diagnostics = RoutineDiagnostics.collect(context, trigger);
            recordDiagnostics(context, trigger.id, diagnostics);
            return diagnostics;
        } catch (Throwable th) {
            if (th instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String diagnostics = context.getString(R.string.profile_trigger_diagnostics_unavailable);
            recordDiagnostics(context, trigger.id, diagnostics);
            return diagnostics;
        }
    }

    @NonNull
    public static String formatTriggerTitle(@NonNull Context context, @NonNull ProfileTrigger trigger) {
        switch (trigger.type) {
            case ProfileTrigger.TYPE_ON_CHARGING:
                return context.getString(R.string.profile_trigger_on_charging);
            case ProfileTrigger.TYPE_ON_NETWORK_WIFI:
                return context.getString(R.string.profile_trigger_on_network_wifi);
            case ProfileTrigger.TYPE_ON_NETWORK_ANY:
                return context.getString(R.string.profile_trigger_on_network_any);
            case ProfileTrigger.TYPE_ON_BOOT:
                return context.getString(R.string.profile_trigger_on_boot);
            case ProfileTrigger.TYPE_TIME_OF_DAY:
            default:
                return context.getString(R.string.profile_trigger_time_of_day,
                        formatTriggerTime(trigger.hourOfDay, trigger.minuteOfHour));
        }
    }

    @NonNull
    public static String formatTriggerSummary(@NonNull Context context, @NonNull ProfileTrigger trigger) {
        String state = context.getString(trigger.enabled ? R.string.enabled : R.string.disabled_app);
        String last = formatLastRun(context, trigger.id);
        String diagnostics = getLastDiagnostics(context, trigger.id);
        if (diagnostics == null || diagnostics.isEmpty()) {
            diagnostics = trigger.type == ProfileTrigger.TYPE_ON_BOOT
                    ? context.getString(R.string.profile_trigger_diagnostics_boot)
                    : context.getString(R.string.profile_trigger_diagnostics_unknown);
        }
        return context.getString(R.string.profile_trigger_summary, state, last,
                context.getString(R.string.profile_trigger_diagnostics, diagnostics));
    }

    @NonNull
    public static String formatLastRun(@NonNull Context context, @NonNull String triggerId) {
        String lastResult = getLastResult(context, triggerId);
        long lastRun = getLastRunMillis(context, triggerId);
        if (lastRun <= 0 || lastResult == null) {
            return context.getString(R.string.profile_trigger_last_run_never);
        }
        String time = DateUtils.formatDateTime(context, lastRun,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH);
        return context.getString(R.string.profile_trigger_last_run_format, time, lastResult);
    }

    @NonNull
    public static String formatTriggerTime(int hourOfDay, int minuteOfHour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hourOfDay)));
        calendar.set(Calendar.MINUTE, Math.max(0, Math.min(59, minuteOfHour)));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.getTime());
    }

    @NonNull
    public static Constraints buildConstraints(@NonNull ProfileTrigger trigger) {
        Constraints.Builder builder = new Constraints.Builder();
        switch (trigger.type) {
            case ProfileTrigger.TYPE_ON_CHARGING:
                builder.setRequiresCharging(true);
                break;
            case ProfileTrigger.TYPE_ON_NETWORK_WIFI:
                builder.setRequiredNetworkType(NetworkType.UNMETERED);
                break;
            case ProfileTrigger.TYPE_ON_NETWORK_ANY:
                builder.setRequiredNetworkType(NetworkType.CONNECTED);
                break;
            case ProfileTrigger.TYPE_TIME_OF_DAY:
            case ProfileTrigger.TYPE_ON_BOOT:
            default:
                builder.setRequiredNetworkType(NetworkType.NOT_REQUIRED);
        }
        return builder.build();
    }

    @VisibleForTesting
    static long getRepeatIntervalMillis(@NonNull ProfileTrigger trigger) {
        return trigger.type == ProfileTrigger.TYPE_TIME_OF_DAY
                ? DAILY_INTERVAL_MILLIS : MIN_PERIODIC_INTERVAL_MILLIS;
    }

    @VisibleForTesting
    static long getInitialDelayMillis(@NonNull ProfileTrigger trigger, long nowMillis) {
        return getInitialDelayMillis(trigger, nowMillis, TimeZone.getDefault());
    }

    @VisibleForTesting
    static long getInitialDelayMillis(@NonNull ProfileTrigger trigger,
                                      long nowMillis,
                                      @NonNull TimeZone timeZone) {
        if (trigger.type != ProfileTrigger.TYPE_TIME_OF_DAY) {
            return 0L;
        }
        Calendar next = Calendar.getInstance(timeZone);
        next.setTimeInMillis(nowMillis);
        next.set(Calendar.HOUR_OF_DAY, trigger.hourOfDay);
        next.set(Calendar.MINUTE, trigger.minuteOfHour);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= nowMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis() - nowMillis;
    }

    @VisibleForTesting
    @NonNull
    static String uniqueWorkName(@NonNull String triggerId) {
        return UNIQUE_WORK_PREFIX + triggerId;
    }

    @VisibleForTesting
    @NonNull
    static Data inputData(@NonNull String triggerId) {
        return new Data.Builder().putString(RoutineWorker.KEY_TRIGGER_ID, triggerId).build();
    }

    @NonNull
    private static String workTagForTrigger(@NonNull String triggerId) {
        return WORK_TAG + '_' + triggerId.toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static SharedPreferences getRunPrefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
