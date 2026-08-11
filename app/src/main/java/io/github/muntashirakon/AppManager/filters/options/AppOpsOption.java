// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.app.AppOpsManager;
import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

/**
 * Finder filter over the apps that declare or hold a given app op. Mirrors {@link
 * PermissionsOption} — same `eq` / `contains` / `starts_with` / `ends_with` / `regex` predicate
 * shape, applied to the {@code getName()} of each {@link AppOpsManagerCompat.OpEntry} the app
 * reports.
 *
 * <p>The {@code with_mode} key additionally matches on the mode each op is in (allowed, ignored,
 * errored, default, foreground), read from {@link AppOpsManagerCompat.OpEntry#getMode()}.
 *
 * <p>Shipped under the v0.x roadmap row "Finder: AppOps".
 */
public class AppOpsOption extends FilterOption {
    /** Mode-flag bits (used with the {@code with_mode} key). The bit position equals the
     *  {@code AppOpsManager.MODE_*} value, so a flag is just {@code 1 << mode}. */
    public static final int MODE_FLAG_ALLOWED = 1 << AppOpsManager.MODE_ALLOWED;
    public static final int MODE_FLAG_IGNORED = 1 << AppOpsManager.MODE_IGNORED;
    public static final int MODE_FLAG_ERRORED = 1 << AppOpsManager.MODE_ERRORED;
    public static final int MODE_FLAG_DEFAULT = 1 << AppOpsManager.MODE_DEFAULT;
    public static final int MODE_FLAG_FOREGROUND = 1 << AppOpsManager.MODE_FOREGROUND;

    /** Selectable modes, in the order the editor shows them. */
    private static final Map<Integer, CharSequence> MODE_FLAG_LABELS =
            Collections.unmodifiableMap(new LinkedHashMap<Integer, CharSequence>() {{
                put(MODE_FLAG_ALLOWED, "Allowed");
                put(MODE_FLAG_IGNORED, "Ignored");
                put(MODE_FLAG_ERRORED, "Errored");
                put(MODE_FLAG_DEFAULT, "Default");
                put(MODE_FLAG_FOREGROUND, "Foreground");
            }});

    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("eq", TYPE_STR_SINGLE);
        put("contains", TYPE_STR_SINGLE);
        put("starts_with", TYPE_STR_SINGLE);
        put("ends_with", TYPE_STR_SINGLE);
        put("regex", TYPE_REGEX);
        // Match apps that hold at least one op in any of the selected modes. The intValue is an
        // OR of MODE_FLAG_* constants (bit position = AppOpsManager.MODE_*).
        put("with_mode", TYPE_INT_FLAGS);
    }};

    public AppOpsOption() {
        super("app_ops");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    /**
     * The editor renders a checkbox per flag from this map. Returning nothing here is not a
     * cosmetic omission: {@link FilterOption#getFlags(String)} throws by default, so a key
     * declared as {@code TYPE_INT_FLAGS} without an entry here fails the moment the user selects
     * it.
     */
    @NonNull
    @Override
    public Map<Integer, CharSequence> getFlags(@NonNull String key) {
        if ("with_mode".equals(key)) {
            return MODE_FLAG_LABELS;
        }
        return super.getFlags(key);
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        List<AppOpsManagerCompat.OpEntry> ops = info.getAppOps();
        switch (key) {
            case KEY_ALL:
                return result.setMatched(!ops.isEmpty());
            case "eq": {
                Objects.requireNonNull(value);
                return result.setMatched(matchesAny(ops, name -> name.equals(value)));
            }
            case "contains": {
                Objects.requireNonNull(value);
                return result.setMatched(matchesAny(ops, name -> name.contains(value)));
            }
            case "starts_with": {
                Objects.requireNonNull(value);
                return result.setMatched(matchesAny(ops, name -> name.startsWith(value)));
            }
            case "ends_with": {
                Objects.requireNonNull(value);
                return result.setMatched(matchesAny(ops, name -> name.endsWith(value)));
            }
            case "regex": {
                Objects.requireNonNull(value);
                return result.setMatched(matchesAny(ops, name -> regexValue.matcher(name).matches()));
            }
            case "with_mode": {
                for (AppOpsManagerCompat.OpEntry op : ops) {
                    if (matchesMode(op.getMode(), intValue)) {
                        return result.setMatched(true);
                    }
                }
                return result.setMatched(false);
            }
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    /**
     * Whether a single op's mode falls in the selected set.
     *
     * <p>A negative mode means the platform did not report one. That is not the same as any
     * particular mode, so it never matches — otherwise the filter would assert something about
     * an op it knows nothing about.
     */
    @VisibleForTesting
    static boolean matchesMode(int mode, int selectedModeFlags) {
        if (mode < 0) {
            return false;
        }
        return (selectedModeFlags & (1 << mode)) != 0;
    }

    private static boolean matchesAny(@NonNull List<AppOpsManagerCompat.OpEntry> ops,
                                      @NonNull NamePredicate predicate) {
        for (AppOpsManagerCompat.OpEntry op : ops) {
            String name = op.getName();
            if (name == null) continue;
            if (predicate.matches(name)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("App ops");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "eq":
                return sb.append(" = '").append(value).append("'");
            case "contains":
                return sb.append(" contains '").append(value).append("'");
            case "starts_with":
                return sb.append(" starts with '").append(value).append("'");
            case "ends_with":
                return sb.append(" ends with '").append(value).append("'");
            case "regex":
                return sb.append(" matches '").append(value).append("'");
            case "with_mode":
                return sb.append(" mode ∈ {").append(describeModeFlags(intValue)).append("}");
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    /** Build a comma-separated label for the selected mode flags, e.g. "Allowed, Foreground". */
    @NonNull
    private static String describeModeFlags(int intValue) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, CharSequence> entry : MODE_FLAG_LABELS.entrySet()) {
            if ((intValue & entry.getKey()) == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(entry.getValue());
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    private interface NamePredicate {
        boolean matches(@NonNull String name);
    }
}
