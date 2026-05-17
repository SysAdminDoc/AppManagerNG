// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_CAUTION;
import static io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_REPLACE;
import static io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_SAFE;
import static io.github.muntashirakon.AppManager.debloat.DebloatObject.REMOVAL_UNSAFE;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_AOSP;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_CARRIER;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_GOOGLE;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_MISC;
import static io.github.muntashirakon.AppManager.debloat.DebloaterListOptions.FILTER_LIST_OEM;

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

import io.github.muntashirakon.AppManager.debloat.DebloatObject;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.LangUtils;

public class BloatwareOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("type", TYPE_INT_FLAGS);
        put("removal", TYPE_INT_FLAGS);
        put("description_eq", TYPE_STR_SINGLE);
        put("description_contains", TYPE_STR_SINGLE);
        put("description_starts_with", TYPE_STR_SINGLE);
        put("description_ends_with", TYPE_STR_SINGLE);
        put("description_regex", TYPE_REGEX);
    }};

    private final Map<Integer, CharSequence> mBloatwareTypeFlags = new LinkedHashMap<Integer, CharSequence>() {{
        put(FILTER_LIST_AOSP, "AOSP");
        put(FILTER_LIST_CARRIER, "Carrier");
        put(FILTER_LIST_GOOGLE, "Google");
        put(FILTER_LIST_MISC, "Misc");
        put(FILTER_LIST_OEM, "OEM");
    }};

    private final Map<Integer, CharSequence> mRemovalFlags = new LinkedHashMap<Integer, CharSequence>() {{
        put(REMOVAL_SAFE, "Safe");
        put(REMOVAL_REPLACE, "Replace");
        put(REMOVAL_CAUTION, "Caution");
        put(REMOVAL_UNSAFE, "Unsafe");
    }};

    public BloatwareOption() {
        super("bloatware");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @Override
    public Map<Integer, CharSequence> getFlags(@NonNull String key) {
        if (key.equals("type")) {
            return mBloatwareTypeFlags;
        } else if (key.equals("removal")) {
            return mRemovalFlags;
        }
        return super.getFlags(key);
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        DebloatObject object = info.getBloatwareInfo();
        if (object == null) {
            return result.setMatched(false);
        }
        // Must be a bloatware
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true);
            case "type":
                return result.setMatched((typeToFlag(object.type) & intValue) != 0);
            case "removal":
                return result.setMatched((object.getRemoval() & intValue) != 0);
            case "description_eq":
            case "description_contains":
            case "description_starts_with":
            case "description_ends_with":
            case "description_regex":
                return result.setMatched(matchesDescription(object.getDescription(), key, value, regexValue));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    public int typeToFlag(@NonNull String type) {
        switch (type) {
            case "aosp":
                return FILTER_LIST_AOSP;
            case "carrier":
                return FILTER_LIST_CARRIER;
            case "google":
                return FILTER_LIST_GOOGLE;
            case "misc":
                return FILTER_LIST_MISC;
            case "oem":
                return FILTER_LIST_OEM;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Bloatware");
        switch (key) {
            case KEY_ALL:
                return sb.append(LangUtils.getSeparatorString()).append("any");
            case "type":
                return sb.append(" with type: ").append(flagsToString("type", intValue));
            case "removal":
                return sb.append(" with removal: ").append(flagsToString("removal", intValue));
            case "description_eq":
                return sb.append(" description = '").append(value).append("'");
            case "description_contains":
                return sb.append(" description contains '").append(value).append("'");
            case "description_starts_with":
                return sb.append(" description starts with '").append(value).append("'");
            case "description_ends_with":
                return sb.append(" description ends with '").append(value).append("'");
            case "description_regex":
                return sb.append(" description matches '").append(value).append("'");
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }

    @VisibleForTesting
    static boolean matchesDescription(@Nullable String description, @NonNull String key,
                                      @Nullable String value, @Nullable Pattern regexValue) {
        if (description == null) {
            return false;
        }
        switch (key) {
            case "description_eq":
                return description.trim().equalsIgnoreCase(Objects.requireNonNull(value).trim());
            case "description_contains": {
                String needle = Objects.requireNonNull(value).toLowerCase(Locale.ROOT);
                return description.toLowerCase(Locale.ROOT).contains(needle);
            }
            case "description_starts_with": {
                String needle = Objects.requireNonNull(value).toLowerCase(Locale.ROOT);
                return description.trim().toLowerCase(Locale.ROOT).startsWith(needle);
            }
            case "description_ends_with": {
                String needle = Objects.requireNonNull(value).toLowerCase(Locale.ROOT);
                return description.trim().toLowerCase(Locale.ROOT).endsWith(needle);
            }
            case "description_regex":
                return Objects.requireNonNull(regexValue).matcher(description).matches();
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
