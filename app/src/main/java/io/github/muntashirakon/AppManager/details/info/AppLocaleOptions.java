// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AppLocaleOptions {
    static final String SYSTEM_DEFAULT_LANGUAGE_TAG = "";

    private AppLocaleOptions() {
    }

    @NonNull
    static List<Option> buildOptions(@NonNull Locale[] availableLocales,
                                     @NonNull Locale displayLocale,
                                     @NonNull String systemDefaultLabel) {
        Map<String, Option> optionsByTag = new LinkedHashMap<>();
        for (Locale locale : availableLocales) {
            if (locale == null || locale.getLanguage().isEmpty()) {
                continue;
            }
            String languageTag = locale.stripExtensions().toLanguageTag();
            if (languageTag.isEmpty() || "und".equals(languageTag) || optionsByTag.containsKey(languageTag)) {
                continue;
            }
            String displayName = locale.getDisplayName(displayLocale);
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = languageTag;
            }
            optionsByTag.put(languageTag, new Option(languageTag, displayName + " (" + languageTag + ")"));
        }
        List<Option> options = new ArrayList<>(optionsByTag.values());
        Collator collator = Collator.getInstance(displayLocale);
        options.sort((option1, option2) -> collator.compare(option1.label, option2.label));
        options.add(0, new Option(SYSTEM_DEFAULT_LANGUAGE_TAG, systemDefaultLabel));
        return options;
    }

    @NonNull
    static CharSequence describeLanguageTags(@Nullable String languageTags,
                                             @NonNull String systemDefaultLabel,
                                             @NonNull Locale displayLocale) {
        if (languageTags == null || languageTags.trim().isEmpty()) {
            return systemDefaultLabel;
        }
        String[] tags = languageTags.split(",");
        List<String> labels = new ArrayList<>(tags.length);
        for (String rawTag : tags) {
            String tag = rawTag.trim();
            if (tag.isEmpty()) {
                continue;
            }
            Locale locale = Locale.forLanguageTag(tag);
            String displayName = locale.getDisplayName(displayLocale);
            labels.add((displayName == null || displayName.trim().isEmpty() ? tag : displayName)
                    + " (" + locale.toLanguageTag() + ")");
        }
        return labels.isEmpty() ? systemDefaultLabel : String.join(", ", labels);
    }

    static final class Option {
        @NonNull
        final String languageTag;
        @NonNull
        final CharSequence label;

        Option(@NonNull String languageTag, @NonNull CharSequence label) {
            this.languageTag = languageTag;
            this.label = label;
        }
    }
}
