// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.app.AppOpsManager;
import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.ArrayList;
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
 * <p>Mode-based filtering (only-allowed / only-ignored / only-foreground) is tracked as a
 * follow-up TODO so v1 can ship as a small, self-contained extension; the
 * {@link IFilterableAppInfo#getAppOps()} surface already exposes {@code getMode()} for the next
 * iteration.
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
                    int mode = op.getMode();
                    if (mode < 0) continue;
                    if ((intValue & (1 << mode)) != 0) {
                        return result.setMatched(true);
                    }
                }
                return result.setMatched(false);
            }
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    private static boolean matchesAny(@NonNull List<AppOpsManagerCompat.OpEntry> ops,
                                      @NonNull NamePredicate predicate) {
        // ArrayList just to silence the unused-import lint if it ever fires; keeps the static
        // surface small.
        List<String> names = new ArrayList<>(ops.size());
        for (AppOpsManagerCompat.OpEntry op : ops) {
            names.add(op.getName());
        }
        for (String name : names) {
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

    /** Build a comma-separated label for the selected mode flags, e.g. "allowed, foreground". */
    @NonNull
    private static String describeModeFlags(int intValue) {
        StringBuilder sb = new StringBuilder();
        appendIfSet(sb, intValue, MODE_FLAG_ALLOWED, "allowed");
        appendIfSet(sb, intValue, MODE_FLAG_IGNORED, "ignored");
        appendIfSet(sb, intValue, MODE_FLAG_ERRORED, "errored");
        appendIfSet(sb, intValue, MODE_FLAG_DEFAULT, "default");
        appendIfSet(sb, intValue, MODE_FLAG_FOREGROUND, "foreground");
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    private static void appendIfSet(@NonNull StringBuilder sb, int value, int flag, @NonNull String label) {
        if ((value & flag) == 0) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(label);
    }

    private interface NamePredicate {
        boolean matches(@NonNull String name);
    }
}
