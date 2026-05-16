// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

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
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("eq", TYPE_STR_SINGLE);
        put("contains", TYPE_STR_SINGLE);
        put("starts_with", TYPE_STR_SINGLE);
        put("ends_with", TYPE_STR_SINGLE);
        put("regex", TYPE_REGEX);
        // TODO: mode-based filtering (allowed / ignored / errored / default / foreground)
        //       and "name + mode" composition. Op mode is already on AppOpsManagerCompat.OpEntry
        //       via getMode(); wiring a TYPE_INT_FLAGS key is the open follow-up.
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
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    private interface NamePredicate {
        boolean matches(@NonNull String name);
    }
}
