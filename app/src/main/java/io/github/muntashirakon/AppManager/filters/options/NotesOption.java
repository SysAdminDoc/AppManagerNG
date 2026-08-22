// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.tags.AppNoteStore;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/** Finder predicates for user-authored per-app notes. */
public final class NotesOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("any", TYPE_NONE);
        put("none", TYPE_NONE);
        put("contains", TYPE_STR_SINGLE);
    }};

    public NotesOption() {
        super("notes");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        String note = new AppNoteStore(ContextUtils.getContext()).getNote(info.getPackageName());
        boolean matched;
        switch (key) {
            case "any":
                matched = note != null;
                break;
            case "none":
                matched = note == null;
                break;
            case "contains":
                matched = note != null && value != null && containsIgnoreCase(note, value);
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
        SpannableStringBuilder sb = new SpannableStringBuilder("Notes ");
        switch (key) {
            case "any":
                sb.append("any saved");
                break;
            case "none":
                sb.append("none saved");
                break;
            case "contains":
                sb.append("contains: ").append(value == null ? "" : value);
                break;
            default:
                sb.append("(any)");
                break;
        }
        return sb;
    }

    static boolean containsIgnoreCase(@NonNull String note, @NonNull String needle) {
        return note.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
