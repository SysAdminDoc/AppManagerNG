// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A language file as read from disk: its header plus one entry per key. Nothing here has been
 * checked against the app's strings yet; {@link LanguagePackValidator} does that.
 */
public final class LanguagePack {
    public static final int SCHEMA_VERSION = 1;

    public static final class Entry {
        /** The English text the translator saw, if the file recorded it. */
        @Nullable
        public final TextValue original;
        @NonNull
        public final TextValue translation;

        public Entry(@Nullable TextValue original, @NonNull TextValue translation) {
            this.original = original;
            this.translation = translation;
        }
    }

    /**
     * A Debloater description and warning in the file's language. They come from the debloat list,
     * not the app's strings, so they sit in an optional section of their own, keyed by package.
     */
    public static final class DebloatEntry {
        @Nullable
        public final String description;
        /** The English description the translator saw, if the file recorded it. */
        @Nullable
        public final String originalDescription;
        @Nullable
        public final String warning;
        @Nullable
        public final String originalWarning;

        public DebloatEntry(@Nullable String description, @Nullable String originalDescription,
                            @Nullable String warning, @Nullable String originalWarning) {
            this.description = description;
            this.originalDescription = originalDescription;
            this.warning = warning;
            this.originalWarning = originalWarning;
        }
    }

    /** Normalized BCP 47 code: language, optional script, optional region. */
    @NonNull
    public final String code;
    @NonNull
    public final String name;
    public final int version;
    @NonNull
    public final List<String> translators;
    @NonNull
    public final Map<String, Entry> entries;
    /** Entries that were present but malformed (wrong JSON types, bad key names). */
    public final int malformedEntries;
    /** Translated Debloater descriptions by package name, empty when the file has none. */
    @NonNull
    public final Map<String, DebloatEntry> debloat;

    public LanguagePack(@NonNull String code, @NonNull String name, int version, @NonNull List<String> translators,
                        @NonNull Map<String, Entry> entries, int malformedEntries) {
        this(code, name, version, translators, entries, malformedEntries, Collections.emptyMap());
    }

    public LanguagePack(@NonNull String code, @NonNull String name, int version, @NonNull List<String> translators,
                        @NonNull Map<String, Entry> entries, int malformedEntries,
                        @NonNull Map<String, DebloatEntry> debloat) {
        this.code = code;
        this.name = name;
        this.version = version;
        this.translators = Collections.unmodifiableList(translators);
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.malformedEntries = malformedEntries;
        this.debloat = Collections.unmodifiableMap(new LinkedHashMap<>(debloat));
    }

    @NonNull
    public LanguagePack withCode(@NonNull String newCode) {
        return new LanguagePack(newCode, name, version, translators, entries, malformedEntries, debloat);
    }
}
