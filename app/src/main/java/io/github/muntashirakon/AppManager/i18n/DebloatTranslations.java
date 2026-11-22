// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * The Debloater descriptions a language file translates. A translation is only shown while it
 * was made from the description the app has now, so an updated debloat list never shows text
 * that no longer matches it.
 */
public final class DebloatTranslations {
    @NonNull
    private final Map<String, LanguagePack.DebloatEntry> mEntries;

    DebloatTranslations(@NonNull Map<String, LanguagePack.DebloatEntry> entries) {
        mEntries = Collections.unmodifiableMap(entries);
    }

    /** The translated description, or {@code null} to show {@code english}. */
    @Nullable
    public String description(@NonNull String packageName, @Nullable String english) {
        LanguagePack.DebloatEntry entry = mEntries.get(packageName);
        if (entry == null) {
            return null;
        }
        return current(entry.description, entry.originalDescription, english);
    }

    /** The translated warning, or {@code null} to show {@code english}. */
    @Nullable
    public String warning(@NonNull String packageName, @Nullable String english) {
        LanguagePack.DebloatEntry entry = mEntries.get(packageName);
        if (entry == null) {
            return null;
        }
        return current(entry.warning, entry.originalWarning, english);
    }

    @NonNull
    Map<String, LanguagePack.DebloatEntry> entries() {
        return mEntries;
    }

    @Nullable
    private static String current(@Nullable String translation, @Nullable String original, @Nullable String english) {
        if (translation == null || english == null) {
            return null;
        }
        return original == null || original.equals(english) ? translation : null;
    }
}
