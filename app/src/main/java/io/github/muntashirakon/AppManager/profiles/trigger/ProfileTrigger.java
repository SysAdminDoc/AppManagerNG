// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.trigger;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.UUID;

/**
 * NF-09 — value object describing a profile-execution trigger.
 *
 * <p>This is the immutable data contract that {@link ProfileTriggerStore}
 * persists and {@link RoutineScheduler} reads. It is intentionally
 * decoupled from WorkManager details: the {@link #type} field is a stable
 * enum; the optional {@link #hourOfDay} / {@link #minuteOfHour} fields apply
 * only to {@link #TYPE_TIME_OF_DAY}; the {@link #profileId} is opaque to the
 * trigger and resolved by the executor.</p>
 *
 * <p>Trigger types are defined up-front and are all handled by the
 * scheduler executor.</p>
 */
public final class ProfileTrigger {
    public static final int TYPE_TIME_OF_DAY = 0;
    public static final int TYPE_ON_CHARGING = 1;
    public static final int TYPE_ON_NETWORK_WIFI = 2;
    public static final int TYPE_ON_NETWORK_ANY = 3;
    public static final int TYPE_ON_BOOT = 4;
    public static final int TYPE_ON_APP_INSTALL = 5;
    public static final int TYPE_ON_APP_UPDATE = 6;
    public static final int TYPE_ON_APP_UNINSTALL = 7;

    @IntDef({TYPE_TIME_OF_DAY, TYPE_ON_CHARGING, TYPE_ON_NETWORK_WIFI,
            TYPE_ON_NETWORK_ANY, TYPE_ON_BOOT, TYPE_ON_APP_INSTALL,
            TYPE_ON_APP_UPDATE, TYPE_ON_APP_UNINSTALL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}

    @NonNull public final String id;
    @NonNull public final String profileId;
    @Type public final int type;
    public final boolean enabled;
    public final int hourOfDay;
    public final int minuteOfHour;
    public final long createdAt;

    private ProfileTrigger(@NonNull String id, @NonNull String profileId, @Type int type,
                           boolean enabled, int hourOfDay, int minuteOfHour, long createdAt) {
        this.id = id;
        this.profileId = profileId;
        this.type = type;
        this.enabled = enabled;
        this.hourOfDay = hourOfDay;
        this.minuteOfHour = minuteOfHour;
        this.createdAt = createdAt;
    }

    /** Builder for callers; trips trip {@link IllegalArgumentException} on invalid input. */
    public static final class Builder {
        @Nullable private String id;
        @NonNull private final String profileId;
        @Type private final int type;
        private boolean enabled = true;
        private int hourOfDay = 0;
        private int minuteOfHour = 0;
        private long createdAt = System.currentTimeMillis();

        public Builder(@NonNull String profileId, @Type int type) {
            this.profileId = profileId;
            this.type = type;
        }

        public Builder id(@NonNull String id) { this.id = id; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder createdAt(long ms) { this.createdAt = ms; return this; }

        /** Set {@code hour:minute} for {@link #TYPE_TIME_OF_DAY} triggers. */
        public Builder timeOfDay(int hourOfDay, int minuteOfHour) {
            if (hourOfDay < 0 || hourOfDay > 23) {
                throw new IllegalArgumentException("hourOfDay out of range: " + hourOfDay);
            }
            if (minuteOfHour < 0 || minuteOfHour > 59) {
                throw new IllegalArgumentException("minuteOfHour out of range: " + minuteOfHour);
            }
            this.hourOfDay = hourOfDay;
            this.minuteOfHour = minuteOfHour;
            return this;
        }

        @NonNull
        public ProfileTrigger build() {
            if (profileId.isEmpty()) {
                throw new IllegalArgumentException("profileId must not be empty");
            }
            if (!isValidType(type)) {
                throw new IllegalArgumentException("unknown trigger type: " + type);
            }
            String resolvedId = id != null ? id : UUID.randomUUID().toString();
            return new ProfileTrigger(resolvedId, profileId, type, enabled,
                    hourOfDay, minuteOfHour, createdAt);
        }
    }

    @VisibleForTesting
    static boolean isValidType(int type) {
        return type == TYPE_TIME_OF_DAY
                || type == TYPE_ON_CHARGING
                || type == TYPE_ON_NETWORK_WIFI
                || type == TYPE_ON_NETWORK_ANY
                || type == TYPE_ON_BOOT
                || type == TYPE_ON_APP_INSTALL
                || type == TYPE_ON_APP_UPDATE
                || type == TYPE_ON_APP_UNINSTALL;
    }

    @NonNull
    public ProfileTrigger withEnabled(boolean enabled) {
        if (this.enabled == enabled) return this;
        return new ProfileTrigger(id, profileId, type, enabled, hourOfDay, minuteOfHour, createdAt);
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("profile", profileId);
        json.put("type", typeAsString(type));
        json.put("enabled", enabled);
        if (type == TYPE_TIME_OF_DAY) {
            json.put("hour", hourOfDay);
            json.put("minute", minuteOfHour);
        }
        json.put("createdAt", createdAt);
        return json;
    }

    @NonNull
    static ProfileTrigger fromJson(@NonNull JSONObject json) throws JSONException {
        String id = json.optString("id", "");
        String profileId = json.optString("profile", "");
        int type = parseTypeString(json.optString("type", "time_of_day"));
        boolean enabled = json.optBoolean("enabled", true);
        int hour = json.optInt("hour", 0);
        int minute = json.optInt("minute", 0);
        long createdAt = json.optLong("createdAt", System.currentTimeMillis());
        if (id.isEmpty() || profileId.isEmpty()) {
            throw new JSONException("trigger JSON missing id or profile");
        }
        if (!isValidType(type)) {
            throw new JSONException("trigger JSON has unknown type");
        }
        if (type == TYPE_TIME_OF_DAY) {
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                throw new JSONException("trigger JSON has time-of-day out of range");
            }
        } else {
            hour = 0;
            minute = 0;
        }
        return new ProfileTrigger(id, profileId, type, enabled, hour, minute, createdAt);
    }

    @NonNull
    @VisibleForTesting
    static String typeAsString(@Type int type) {
        switch (type) {
            case TYPE_ON_CHARGING: return "on_charging";
            case TYPE_ON_NETWORK_WIFI: return "on_network_wifi";
            case TYPE_ON_NETWORK_ANY: return "on_network_any";
            case TYPE_ON_BOOT: return "on_boot";
            case TYPE_ON_APP_INSTALL: return "on_app_install";
            case TYPE_ON_APP_UPDATE: return "on_app_update";
            case TYPE_ON_APP_UNINSTALL: return "on_app_uninstall";
            case TYPE_TIME_OF_DAY:
            default: return "time_of_day";
        }
    }

    @VisibleForTesting
    static int parseTypeString(@NonNull String raw) {
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "on_charging": return TYPE_ON_CHARGING;
            case "on_network_wifi": return TYPE_ON_NETWORK_WIFI;
            case "on_network_any": return TYPE_ON_NETWORK_ANY;
            case "on_boot": return TYPE_ON_BOOT;
            case "on_app_install": return TYPE_ON_APP_INSTALL;
            case "on_app_update": return TYPE_ON_APP_UPDATE;
            case "on_app_uninstall": return TYPE_ON_APP_UNINSTALL;
            case "time_of_day": return TYPE_TIME_OF_DAY;
            default: return -1;
        }
    }
}
