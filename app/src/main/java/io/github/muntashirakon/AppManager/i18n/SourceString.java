// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One translatable English string or plural as the running app has it. Language files are checked
 * against this, never against the {@code original} a file carries, because a file can be older
 * than the app or simply wrong.
 */
public final class SourceString {
    /** Plural quantities in the order a template lists them. */
    public static final String[] QUANTITIES = {"zero", "one", "two", "few", "many", "other"};

    @NonNull
    public final String key;
    /** The resource ID, or 0 where there's no running app (tests, fuzzing). */
    public final int resId;
    @Nullable
    private final StyledText mText;
    @Nullable
    private final Map<String, StyledText> mForms;

    private SourceString(@NonNull String key, int resId, @Nullable StyledText text,
                         @Nullable Map<String, StyledText> forms) {
        this.key = key;
        this.resId = resId;
        mText = text;
        mForms = forms;
    }

    @NonNull
    public static SourceString ofString(@NonNull String key, int resId, @NonNull StyledText text) {
        return new SourceString(key, resId, text, null);
    }

    @NonNull
    public static SourceString ofPlural(@NonNull String key, int resId, @NonNull Map<String, StyledText> forms) {
        return new SourceString(key, resId, null, Collections.unmodifiableMap(new LinkedHashMap<>(forms)));
    }

    public boolean isPlural() {
        return mForms != null;
    }

    @NonNull
    public StyledText text() {
        if (mText == null) {
            throw new IllegalStateException(key + " is a plural");
        }
        return mText;
    }

    @NonNull
    public Map<String, StyledText> forms() {
        if (mForms == null) {
            throw new IllegalStateException(key + " isn't a plural");
        }
        return mForms;
    }

    /** True when any English form carries spans, so translations are read as markup. */
    public boolean isStyled() {
        if (mText != null) {
            return mText.isStyled();
        }
        for (StyledText form : mForms.values()) {
            if (form.isStyled()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public Set<String> links() {
        Set<String> out = new LinkedHashSet<>();
        if (mText != null) {
            out.addAll(mText.links());
        } else {
            for (StyledText form : mForms.values()) {
                out.addAll(form.links());
            }
        }
        return out;
    }

    /** The English text as a language file shows it, or {@code null} if it can't be written. */
    @Nullable
    public TextValue toMarkupValue() {
        if (mText != null) {
            String markup = mText.toMarkup();
            return markup == null ? null : TextValue.ofString(markup);
        }
        Map<String, String> forms = new LinkedHashMap<>();
        for (Map.Entry<String, StyledText> e : mForms.entrySet()) {
            String markup = e.getValue().toMarkup();
            if (markup == null) {
                return null;
            }
            forms.put(e.getKey(), markup);
        }
        return TextValue.ofForms(forms);
    }

    public static boolean isQuantity(@NonNull String name) {
        for (String q : QUANTITIES) {
            if (q.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
