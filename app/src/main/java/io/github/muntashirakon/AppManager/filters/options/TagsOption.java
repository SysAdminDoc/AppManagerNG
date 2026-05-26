// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.tags.AppTagStore;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * NF-08 — Finder predicate over the multi-tag store. Supports:
 *
 * <ul>
 *     <li>{@code any} — match apps that carry at least one tag.</li>
 *     <li>{@code none} — match apps that carry no tags.</li>
 *     <li>{@code has_all} — value is a newline-separated list of tags; match
 *         only when every tag is attached.</li>
 *     <li>{@code has_any} — value is a newline-separated list of tags; match
 *         when at least one is attached.</li>
 *     <li>{@code missing_all} — match when none of the supplied tags are
 *         attached.</li>
 * </ul>
 */
public class TagsOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("any", TYPE_NONE);
        put("none", TYPE_NONE);
        put("has_all", TYPE_STR_MULTIPLE);
        put("has_any", TYPE_STR_MULTIPLE);
        put("missing_all", TYPE_STR_MULTIPLE);
    }};

    public TagsOption() {
        super("tags");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        AppTagStore store = new AppTagStore(ContextUtils.getContext());
        String pkg = info.getPackageName();
        List<String> needles = stringValues != null
                ? new ArrayList<>(Arrays.asList(stringValues))
                : new ArrayList<>();
        boolean matched;
        switch (key) {
            case "any":
                matched = store.hasAnyTag(pkg);
                break;
            case "none":
                matched = !store.hasAnyTag(pkg);
                break;
            case "has_all":
                matched = store.hasAllTags(pkg, needles);
                break;
            case "has_any":
                matched = !needles.isEmpty() && store.hasAnyTagIn(pkg, needles);
                break;
            case "missing_all":
                matched = needles.isEmpty() || !store.hasAnyTagIn(pkg, needles);
                break;
            case KEY_ALL:
            default:
                matched = true;
                break;
        }
        return result.setMatched(matched);
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder("Tags ");
        switch (key) {
            case "any":
                sb.append("any present");
                break;
            case "none":
                sb.append("none attached");
                break;
            case "has_all":
                sb.append("has all of: ").append(joined());
                break;
            case "has_any":
                sb.append("has any of: ").append(joined());
                break;
            case "missing_all":
                sb.append("missing all of: ").append(joined());
                break;
            default:
                sb.append("(any)");
                break;
        }
        return sb;
    }

    @NonNull
    private String joined() {
        if (stringValues == null || stringValues.length == 0) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stringValues.length; ++i) {
            if (i > 0) sb.append(", ");
            sb.append(stringValues[i]);
        }
        return sb.toString();
    }
}
