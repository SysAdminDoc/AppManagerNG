// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.muntashirakon.AppManager.details.info.DomainLinkConflictDetector;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class DomainLinksOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("has_domains", TYPE_NONE);
        put("conflicted", TYPE_NONE);
        put("host_eq", TYPE_STR_SINGLE);
        put("host_contains", TYPE_STR_SINGLE);
        put("host_regex", TYPE_REGEX);
        put("conflict_package_contains", TYPE_STR_SINGLE);
    }};

    public DomainLinksOption() {
        super("domain_links");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        return result.setMatched(matches(info.getDomainVerificationHosts(), info.getDomainLinkConflicts(),
                key, value, regexValue));
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Domain links");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "has_domains":
                return sb.append(" claims at least one domain");
            case "conflicted":
                return sb.append(" has conflicts");
            case "host_eq":
                return sb.append(" host = '").append(value).append("'");
            case "host_contains":
                return sb.append(" host contains '").append(value).append("'");
            case "host_regex":
                return sb.append(" host matches '").append(value).append("'");
            case "conflict_package_contains":
                return sb.append(" conflict package contains '").append(value).append("'");
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @VisibleForTesting
    static boolean matches(@NonNull Map<String, Integer> hostStates,
                           @NonNull Map<String, java.util.List<DomainLinkConflictDetector.Conflict>> conflicts,
                           @NonNull String key, @Nullable String value, @Nullable Pattern regexValue) {
        switch (key) {
            case KEY_ALL:
            case "has_domains":
                return !hostStates.isEmpty();
            case "conflicted":
                return !conflicts.isEmpty();
            case "host_eq":
            case "host_contains":
            case "host_regex":
                return matchesHost(hostStates, key, value, regexValue);
            case "conflict_package_contains":
                return matchesConflictPackage(conflicts, value);
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    private static boolean matchesHost(@NonNull Map<String, Integer> hostStates, @NonNull String key,
                                       @Nullable String value, @Nullable Pattern regexValue) {
        for (String host : hostStates.keySet()) {
            switch (key) {
                case "host_eq":
                    if (host.equalsIgnoreCase(Objects.requireNonNull(value).trim())) {
                        return true;
                    }
                    break;
                case "host_contains": {
                    String needle = Objects.requireNonNull(value).toLowerCase(Locale.ROOT);
                    if (host.toLowerCase(Locale.ROOT).contains(needle)) {
                        return true;
                    }
                    break;
                }
                case "host_regex":
                    if (Objects.requireNonNull(regexValue).matcher(host).matches()) {
                        return true;
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("Invalid key " + key);
            }
        }
        return false;
    }

    private static boolean matchesConflictPackage(
            @NonNull Map<String, java.util.List<DomainLinkConflictDetector.Conflict>> conflicts,
            @Nullable String value) {
        String needle = Objects.requireNonNull(value).toLowerCase(Locale.ROOT);
        for (java.util.List<DomainLinkConflictDetector.Conflict> hostConflicts : conflicts.values()) {
            for (DomainLinkConflictDetector.Conflict conflict : hostConflicts) {
                if (conflict.packageName.toLowerCase(Locale.ROOT).contains(needle)
                        || conflict.label.toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }
}
