// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.logs.Log;

/**
 * The rules and switches, in a file of their own. Android's backup rules leave it out, so the patterns, which can name
 * people, stay on the device.
 */
public final class NotificationFilterSettings {
    private static final String TAG = "NotificationFilterSettings";
    public static final String FILE_NAME = "notification_filter";

    private static final String KEY_RULES = "rules";
    private static final String KEY_QUIET_MANUAL = "quiet_manual";
    private static final String KEY_QUIET_SCHEDULED = "quiet_scheduled";
    private static final String KEY_QUIET_START = "quiet_start_minute";
    private static final String KEY_QUIET_END = "quiet_end_minute";
    private static final String KEY_QUIET_SKIPPED_UNTIL = "quiet_skipped_until";
    private static final String KEY_QUIET_ACTION = "quiet_action";
    private static final String KEY_RECORD_ALL = "record_all";
    private static final String KEY_RETENTION_DAYS = "retention_days";

    public static final int[] RETENTION_CHOICES = {1, 7, 30};
    public static final int DEFAULT_RETENTION_DAYS = 7;
    private static final int DEFAULT_QUIET_START = 22 * 60;
    private static final int DEFAULT_QUIET_END = 7 * 60;

    @Nullable
    private static volatile NotificationFilterSettings sInstance;

    @NonNull
    public static NotificationFilterSettings getInstance(@NonNull Context context) {
        NotificationFilterSettings instance = sInstance;
        if (instance == null) {
            synchronized (NotificationFilterSettings.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new NotificationFilterSettings(context.getApplicationContext()
                            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE));
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    @VisibleForTesting
    static void resetForTesting() {
        sInstance = null;
    }

    @NonNull
    private final SharedPreferences mPrefs;
    // The listener reads the rules for every notification, so they're parsed once per change.
    private final Object mRulesLock = new Object();
    @Nullable
    private String mParsedFrom;
    @NonNull
    private List<NotificationFilterRule> mParsed = Collections.emptyList();

    @VisibleForTesting
    NotificationFilterSettings(@NonNull SharedPreferences prefs) {
        mPrefs = prefs;
    }

    /** @return The rules in the order they're tried. */
    @NonNull
    public List<NotificationFilterRule> getRules() {
        String raw = mPrefs.getString(KEY_RULES, "[]");
        synchronized (mRulesLock) {
            if (!raw.equals(mParsedFrom)) {
                mParsed = Collections.unmodifiableList(parse(raw));
                mParsedFrom = raw;
            }
            return mParsed;
        }
    }

    public void setRules(@NonNull List<NotificationFilterRule> rules) {
        JSONArray array = new JSONArray();
        try {
            for (NotificationFilterRule rule : rules) {
                array.put(rule.toJson());
            }
        } catch (JSONException e) {
            // Only strings, booleans and enum names go in, so this can't happen.
            throw new IllegalStateException(e);
        }
        mPrefs.edit().putString(KEY_RULES, array.toString()).apply();
    }

    /** Replaces the rule with the same id, or adds it at the end. */
    public void saveRule(@NonNull NotificationFilterRule rule) {
        List<NotificationFilterRule> rules = new ArrayList<>(getRules());
        int index = indexOf(rules, rule.id);
        if (index >= 0) {
            rules.set(index, rule);
        } else {
            rules.add(rule);
        }
        setRules(rules);
    }

    @Nullable
    public NotificationFilterRule findRule(@NonNull String id) {
        List<NotificationFilterRule> rules = getRules();
        int index = indexOf(rules, id);
        return index >= 0 ? rules.get(index) : null;
    }

    private static int indexOf(@NonNull List<NotificationFilterRule> rules, @NonNull String id) {
        for (int i = 0; i < rules.size(); ++i) {
            if (rules.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    public QuietMode getQuietMode() {
        return new QuietMode(mPrefs.getBoolean(KEY_QUIET_MANUAL, false),
                mPrefs.getBoolean(KEY_QUIET_SCHEDULED, false),
                mPrefs.getInt(KEY_QUIET_START, DEFAULT_QUIET_START),
                mPrefs.getInt(KEY_QUIET_END, DEFAULT_QUIET_END),
                mPrefs.getLong(KEY_QUIET_SKIPPED_UNTIL, 0));
    }

    public void setQuietMode(@NonNull QuietMode quiet) {
        mPrefs.edit()
                .putBoolean(KEY_QUIET_MANUAL, quiet.manual)
                .putBoolean(KEY_QUIET_SCHEDULED, quiet.scheduled)
                .putInt(KEY_QUIET_START, quiet.startMinute)
                .putInt(KEY_QUIET_END, quiet.endMinute)
                .putLong(KEY_QUIET_SKIPPED_UNTIL, quiet.scheduleSkippedUntilMillis)
                .apply();
    }

    /** Flips quiet mode, as the switch and the tile do. @return Whether it's now on. */
    public boolean toggleQuietMode(long nowMillis) {
        TimeZone zone = TimeZone.getDefault();
        QuietMode toggled = getQuietMode().toggled(nowMillis, zone);
        setQuietMode(toggled);
        return toggled.isActive(nowMillis, zone);
    }

    public boolean isQuietModeActive(long nowMillis) {
        return getQuietMode().isActive(nowMillis, TimeZone.getDefault());
    }

    /** What quiet mode does to a notification no rule allows: {@link NotificationFilterRule.Action#SILENT} or vibrate. */
    @NonNull
    public NotificationFilterRule.Action getQuietAction() {
        String name = mPrefs.getString(KEY_QUIET_ACTION, NotificationFilterRule.Action.SILENT.name());
        return NotificationFilterRule.Action.VIBRATE.name().equals(name) ? NotificationFilterRule.Action.VIBRATE
                : NotificationFilterRule.Action.SILENT;
    }

    public void setQuietAction(@NonNull NotificationFilterRule.Action action) {
        if (action != NotificationFilterRule.Action.SILENT && action != NotificationFilterRule.Action.VIBRATE) {
            throw new IllegalArgumentException("Quiet mode only silences or vibrates: " + action);
        }
        mPrefs.edit().putString(KEY_QUIET_ACTION, action.name()).apply();
    }

    /** Whether the history keeps every notification, not just the ones a rule or quiet mode acted on. */
    public boolean isRecordAll() {
        return mPrefs.getBoolean(KEY_RECORD_ALL, false);
    }

    public void setRecordAll(boolean recordAll) {
        mPrefs.edit().putBoolean(KEY_RECORD_ALL, recordAll).apply();
    }

    public int getRetentionDays() {
        int days = mPrefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS);
        for (int choice : RETENTION_CHOICES) {
            if (choice == days) {
                return days;
            }
        }
        return DEFAULT_RETENTION_DAYS;
    }

    public void setRetentionDays(int days) {
        mPrefs.edit().putInt(KEY_RETENTION_DAYS, days).apply();
    }

    public long getRetentionMillis() {
        return TimeUnit.DAYS.toMillis(getRetentionDays());
    }

    @NonNull
    private static List<NotificationFilterRule> parse(@NonNull String raw) {
        List<NotificationFilterRule> rules = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); ++i) {
                JSONObject json = array.optJSONObject(i);
                NotificationFilterRule rule = json != null ? NotificationFilterRule.fromJson(json) : null;
                if (rule != null) {
                    rules.add(rule);
                } else {
                    Log.w(TAG, "Skipping a saved rule that can't be read.");
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "The saved rules can't be read.", e);
        }
        return rules;
    }
}
