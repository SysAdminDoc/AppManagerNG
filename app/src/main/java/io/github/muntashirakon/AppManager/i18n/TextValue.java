// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A language file value: one string, or plural forms keyed by quantity. */
public final class TextValue {
    @Nullable
    private final String mString;
    @Nullable
    private final Map<String, String> mForms;

    private TextValue(@Nullable String string, @Nullable Map<String, String> forms) {
        mString = string;
        mForms = forms;
    }

    @NonNull
    public static TextValue ofString(@NonNull String value) {
        return new TextValue(value, null);
    }

    @NonNull
    public static TextValue ofForms(@NonNull Map<String, String> forms) {
        return new TextValue(null, Collections.unmodifiableMap(new LinkedHashMap<>(forms)));
    }

    public boolean isForms() {
        return mForms != null;
    }

    @NonNull
    public String string() {
        if (mString == null) {
            throw new IllegalStateException("Plural value");
        }
        return mString;
    }

    @NonNull
    public Map<String, String> forms() {
        if (mForms == null) {
            throw new IllegalStateException("String value");
        }
        return mForms;
    }

    /** True when nothing in it has visible text, which a file uses to mean "not translated yet". */
    public boolean isBlank() {
        if (mString != null) {
            return mString.trim().isEmpty();
        }
        for (String form : mForms.values()) {
            if (!form.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextValue)) return false;
        TextValue that = (TextValue) o;
        return Objects.equals(mString, that.mString) && Objects.equals(mForms, that.mForms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mString, mForms);
    }

    @NonNull
    @Override
    public String toString() {
        return mString != null ? mString : String.valueOf(mForms);
    }
}
