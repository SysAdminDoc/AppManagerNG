// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.notificationfilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.UUID;

import io.github.muntashirakon.AppManager.misc.TextQuery;

/**
 * One line of the user's notification rules. Rules are tried in order and the first one that matches decides what
 * happens to a notification.
 */
public final class NotificationFilterRule {
    /** Which part of a notification the pattern is tried against. */
    public enum Field {
        ANY,
        TITLE,
        TEXT,
        SENDER,
    }

    /** What happens to a notification a rule matches. */
    public enum Action {
        /** Left alone, so it pops up the way the app meant it to. Quiet mode doesn't touch it either. */
        ALLOW,
        /** Moved to the shade without sound, vibration or a pop-up. */
        SILENT,
        /** Moved to the shade with vibration only. */
        VIBRATE,
        /** Taken out of the shade. It still goes in the history. */
        BLOCK,
        /** Moved to the shade without sound, vibration or a pop-up, but with its icon in the status bar. */
        KEEP_ICON,
        /** Kept in the shade as a pinned copy that comes back when swiped away, until unpinned or for a while. */
        PIN,
    }

    /** Where the filter's copies of a rule sit among each other and among other notifications. */
    public enum Priority {
        HIGH,
        NORMAL,
        LOW,
    }

    /** How long a pinned copy stays. */
    public enum PinLength {
        UNTIL_UNPINNED,
        ONE_HOUR,
        TODAY,
    }

    /** Whether the action puts a copy of its own in the shade. */
    public static boolean postsCopy(@Nullable Action action) {
        return action == Action.SILENT || action == Action.VIBRATE || action == Action.KEEP_ICON
                || action == Action.PIN;
    }

    @NonNull
    public final String id;
    @NonNull
    public final String name;
    /** The one app the rule applies to, or {@code null} for every app. */
    @Nullable
    public final String packageName;
    @NonNull
    public final Field field;
    /** An empty pattern matches every notification of the rule's app. */
    @NonNull
    public final String pattern;
    public final boolean regex;
    @NonNull
    public final Action action;
    /**
     * Whether the rule also takes ongoing notifications, the ones the app keeps up while something runs. Off by
     * default, so a rule for every notification of an app doesn't hide its download or its call in progress.
     */
    public final boolean ongoing;
    @NonNull
    public final Priority priority;
    /** Used by {@link Action#PIN} only. */
    @NonNull
    public final PinLength pinFor;
    // Compiled once. A pattern that can't be used matches nothing.
    @NonNull
    private final TextQuery mQuery;

    public NotificationFilterRule(@NonNull String id, @NonNull String name, @Nullable String packageName,
                                  @NonNull Field field, @NonNull String pattern, boolean regex,
                                  @NonNull Action action) {
        this(id, name, packageName, field, pattern, regex, action, false, Priority.NORMAL, PinLength.UNTIL_UNPINNED);
    }

    public NotificationFilterRule(@NonNull String id, @NonNull String name, @Nullable String packageName,
                                  @NonNull Field field, @NonNull String pattern, boolean regex,
                                  @NonNull Action action, boolean ongoing, @NonNull Priority priority,
                                  @NonNull PinLength pinFor) {
        this.id = id;
        this.name = name;
        this.packageName = packageName == null || packageName.isEmpty() ? null : packageName;
        this.field = field;
        this.pattern = pattern;
        this.regex = regex;
        this.action = action;
        this.ongoing = ongoing;
        this.priority = priority;
        this.pinFor = pinFor;
        mQuery = TextQuery.of(pattern, regex);
    }

    /** The same rule with the options the editor shows under the action. */
    @NonNull
    public NotificationFilterRule withOptions(boolean ongoing, @NonNull Priority priority,
                                              @NonNull PinLength pinFor) {
        return new NotificationFilterRule(id, name, packageName, field, pattern, regex, action, ongoing, priority,
                pinFor);
    }

    @NonNull
    public static NotificationFilterRule create(@NonNull String name, @Nullable String packageName,
                                                @NonNull Field field, @NonNull String pattern, boolean regex,
                                                @NonNull Action action) {
        return new NotificationFilterRule(UUID.randomUUID().toString(), name, packageName, field, pattern, regex,
                action);
    }

    /** Whether the pattern can be used. A rule with a broken pattern is kept but never matches. */
    public boolean isValid() {
        return mQuery.isValid();
    }

    public boolean matches(@NonNull String sourcePackage, @NonNull NotificationFields fields) {
        if (packageName != null && !packageName.equals(sourcePackage)) {
            return false;
        }
        switch (field) {
            case TITLE:
                return mQuery.matches(fields.title);
            case TEXT:
                return mQuery.matches(fields.text);
            case SENDER:
                return mQuery.matches(fields.sender);
            case ANY:
            default:
                return mQuery.matches(fields.title, fields.text, fields.sender);
        }
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        if (packageName != null) {
            json.put("package", packageName);
        }
        json.put("field", field.name());
        json.put("pattern", pattern);
        json.put("regex", regex);
        json.put("action", action.name());
        json.put("ongoing", ongoing);
        json.put("priority", priority.name());
        json.put("pin", pinFor.name());
        return json;
    }

    /**
     * @return The rule, or {@code null} if the saved form is missing something or names an unknown action or field.
     * The options under the action are newer: a rule saved before them gets the defaults, and an option this version
     * doesn't know falls back to its default rather than losing the rule.
     */
    @Nullable
    public static NotificationFilterRule fromJson(@NonNull JSONObject json) {
        try {
            String packageName = json.optString("package", "");
            return new NotificationFilterRule(json.getString("id"), json.getString("name"),
                    packageName.isEmpty() ? null : packageName, Field.valueOf(json.getString("field")),
                    json.getString("pattern"), json.getBoolean("regex"), Action.valueOf(json.getString("action")),
                    json.optBoolean("ongoing", false), optEnum(json, "priority", Priority.NORMAL),
                    optEnum(json, "pin", PinLength.UNTIL_UNPINNED));
        } catch (JSONException | IllegalArgumentException e) {
            return null;
        }
    }

    @NonNull
    private static <E extends Enum<E>> E optEnum(@NonNull JSONObject json, @NonNull String key,
                                                 @NonNull E fallback) {
        String name = json.optString(key, "");
        for (E value : fallback.getDeclaringClass().getEnumConstants()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return fallback;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationFilterRule)) return false;
        NotificationFilterRule that = (NotificationFilterRule) o;
        return regex == that.regex
                && id.equals(that.id)
                && name.equals(that.name)
                && Objects.equals(packageName, that.packageName)
                && field == that.field
                && pattern.equals(that.pattern)
                && action == that.action
                && ongoing == that.ongoing
                && priority == that.priority
                && pinFor == that.pinFor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, packageName, field, pattern, regex, action, ongoing, priority, pinFor);
    }
}
