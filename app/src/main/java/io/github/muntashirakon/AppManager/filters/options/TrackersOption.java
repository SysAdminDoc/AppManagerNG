// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.content.pm.ComponentInfo;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class TrackersOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("eq", TYPE_INT);
        put("le", TYPE_INT);
        put("ge", TYPE_INT);
        // Class-name predicates — closes the iter-X "Finder: Tracker Name Search" and
        // "Finder: Regex Support" rows. Operate on the fully-qualified ComponentInfo.name
        // of each tracker component the scanner attributed to the app.
        put("name_eq", TYPE_STR_SINGLE);
        put("name_contains", TYPE_STR_SINGLE);
        put("name_starts_with", TYPE_STR_SINGLE);
        put("name_ends_with", TYPE_STR_SINGLE);
        put("name_regex", TYPE_REGEX);
    }};

    public TrackersOption() {
        super("trackers");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "eq":
                return result.setMatched(info.getTrackerComponents().size() == intValue);
            case "le":
                return result.setMatched(info.getTrackerComponents().size() <= intValue);
            case "ge":
                return result.setMatched(info.getTrackerComponents().size() >= intValue);
            case "name_eq":
                Objects.requireNonNull(value);
                return matchByName(info, result, name -> name.equals(value));
            case "name_contains":
                Objects.requireNonNull(value);
                return matchByName(info, result, name -> name.contains(value));
            case "name_starts_with":
                Objects.requireNonNull(value);
                return matchByName(info, result, name -> name.startsWith(value));
            case "name_ends_with":
                Objects.requireNonNull(value);
                return matchByName(info, result, name -> name.endsWith(value));
            case "name_regex":
                Objects.requireNonNull(value);
                return matchByName(info, result, name -> regexValue.matcher(name).matches());
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    private TestResult matchByName(@NonNull IFilterableAppInfo info, @NonNull TestResult result,
                                   @NonNull NamePredicate predicate) {
        Map<ComponentInfo, Integer> trackers = info.getTrackerComponents();
        Map<ComponentInfo, Integer> filtered = new LinkedHashMap<>();
        for (Map.Entry<ComponentInfo, Integer> e : trackers.entrySet()) {
            if (predicate.matches(e.getKey().name)) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        return result.setMatched(!filtered.isEmpty()).setMatchedTrackers(filtered);
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Trackers");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "eq":
                return sb.append(" = ").append(Integer.toString(intValue));
            case "le":
                return sb.append(" ≤ ").append(Integer.toString(intValue));
            case "ge":
                return sb.append(" ≥ ").append(Integer.toString(intValue));
            case "name_eq":
                return sb.append(" name = '").append(value).append("'");
            case "name_contains":
                return sb.append(" name contains '").append(value).append("'");
            case "name_starts_with":
                return sb.append(" name starts with '").append(value).append("'");
            case "name_ends_with":
                return sb.append(" name ends with '").append(value).append("'");
            case "name_regex":
                return sb.append(" name matches '").append(value).append("'");
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    private interface NamePredicate {
        boolean matches(@NonNull String name);
    }
}
