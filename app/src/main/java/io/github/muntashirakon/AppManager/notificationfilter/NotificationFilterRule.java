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
    // Compiled once. A pattern that can't be used matches nothing.
    @NonNull
    private final TextQuery mQuery;

    public NotificationFilterRule(@NonNull String id, @NonNull String name, @Nullable String packageName,
                                  @NonNull Field field, @NonNull String pattern, boolean regex,
                                  @NonNull Action action) {
        this.id = id;
        this.name = name;
        this.packageName = packageName == null || packageName.isEmpty() ? null : packageName;
        this.field = field;
        this.pattern = pattern;
        this.regex = regex;
        this.action = action;
        mQuery = TextQuery.of(pattern, regex);
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
        return json;
    }

    /** @return The rule, or {@code null} if the saved form is missing something or names an unknown choice. */
    @Nullable
    public static NotificationFilterRule fromJson(@NonNull JSONObject json) {
        try {
            String packageName = json.optString("package", "");
            return new NotificationFilterRule(json.getString("id"), json.getString("name"),
                    packageName.isEmpty() ? null : packageName, Field.valueOf(json.getString("field")),
                    json.getString("pattern"), json.getBoolean("regex"), Action.valueOf(json.getString("action")));
        } catch (JSONException | IllegalArgumentException e) {
            return null;
        }
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
                && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, packageName, field, pattern, regex, action);
    }
}
