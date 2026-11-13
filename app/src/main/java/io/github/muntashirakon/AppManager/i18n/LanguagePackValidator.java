// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks each language file entry against the running app's English. Entries that pass are safe
 * to hand to the resources table: same placeholders as the English (so formatting can't throw),
 * only the tags the English uses, an {@code other} form for every plural, and plain text only.
 * Entries that fail stay in the file and come back marked when it's exported for editing.
 */
public final class LanguagePackValidator {
    public enum Status {
        OK,
        /** Usable, but the English changed since it was translated. */
        OUTDATED,
        UNKNOWN_KEY,
        WRONG_KIND,
        BAD_PLACEHOLDERS,
        BAD_MARKUP,
        BAD_TEXT,
        NO_OTHER_FORM,
    }

    public static final class Checked {
        @NonNull
        public final String key;
        @NonNull
        public final Status status;
        /** Why a rejected entry can't be used, in words a translator can act on. */
        @Nullable
        public final String problem;
        @Nullable
        public final StyledText text;
        @Nullable
        public final Map<String, StyledText> forms;

        Checked(@NonNull String key, @NonNull Status status, @Nullable String problem,
                @Nullable StyledText text, @Nullable Map<String, StyledText> forms) {
            this.key = key;
            this.status = status;
            this.problem = problem;
            this.text = text;
            this.forms = forms;
        }

        public boolean isUsable() {
            return status == Status.OK || status == Status.OUTDATED;
        }
    }

    public static final class Report {
        @NonNull
        public final Map<String, Checked> entries;
        public final int sourceCount;
        public final int usable;
        public final int outdated;
        /** Entries for keys this app has whose text can't be used as written. */
        public final int needsFix;
        /** Entries for keys this app version doesn't have, plus malformed ones. */
        public final int unknown;

        Report(@NonNull Map<String, Checked> entries, int sourceCount, int malformed) {
            this.entries = Collections.unmodifiableMap(entries);
            this.sourceCount = sourceCount;
            int usable = 0;
            int outdated = 0;
            int needsFix = 0;
            int unknown = malformed;
            for (Checked c : entries.values()) {
                if (c.isUsable()) {
                    ++usable;
                    if (c.status == Status.OUTDATED) {
                        ++outdated;
                    }
                } else if (c.status == Status.UNKNOWN_KEY) {
                    ++unknown;
                } else {
                    ++needsFix;
                }
            }
            this.usable = usable;
            this.outdated = outdated;
            this.needsFix = needsFix;
            this.unknown = unknown;
        }
    }

    private LanguagePackValidator() {
    }

    @NonNull
    public static Report check(@NonNull LanguagePack pack, @NonNull SourceStrings source) {
        Map<String, Checked> out = new LinkedHashMap<>();
        for (Map.Entry<String, LanguagePack.Entry> e : pack.entries.entrySet()) {
            out.put(e.getKey(), checkEntry(e.getKey(), e.getValue(), source.get(e.getKey())));
        }
        return new Report(out, source.keys().size(), pack.malformedEntries);
    }

    @NonNull
    public static Checked checkEntry(@NonNull String key, @NonNull LanguagePack.Entry entry,
                                     @Nullable SourceString src) {
        if (src == null) {
            return new Checked(key, Status.UNKNOWN_KEY, "This app version has no string with this key", null, null);
        }
        TextValue translation = entry.translation;
        if (src.isPlural() != translation.isForms()) {
            return new Checked(key, Status.WRONG_KIND, src.isPlural()
                    ? "Needs plural forms like the original"
                    : "Needs a single string, not plural forms", null, null);
        }
        boolean styled = src.isStyled();
        Set<String> links = src.links();
        if (!src.isPlural()) {
            String raw = translation.string();
            String textProblem = textProblem(raw);
            if (textProblem != null) {
                return new Checked(key, Status.BAD_TEXT, textProblem, null, null);
            }
            StyledText parsed;
            try {
                parsed = styled ? StyledText.parseMarkup(raw, links) : StyledText.plain(raw);
            } catch (StyledText.MarkupException ex) {
                return new Checked(key, Status.BAD_MARKUP, ex.getMessage(), null, null);
            }
            String english = src.text().text;
            String problem = placeholderProblem(english.indexOf('%') >= 0, FormatSpecs.parse(english),
                    parsed.text, false);
            if (problem != null) {
                return new Checked(key, Status.BAD_PLACEHOLDERS, problem, null, null);
            }
            return new Checked(key, isOutdated(entry, src) ? Status.OUTDATED : Status.OK, null, parsed, null);
        }
        Map<String, String> rawForms = translation.forms();
        if (!rawForms.containsKey("other") || rawForms.get("other").trim().isEmpty()) {
            return new Checked(key, Status.NO_OTHER_FORM, "Plurals need an \"other\" form", null, null);
        }
        Set<String> allowed = new TreeSet<>();
        boolean englishHasPercent = false;
        for (StyledText form : src.forms().values()) {
            englishHasPercent |= form.text.indexOf('%') >= 0;
            Set<String> specs = FormatSpecs.parse(form.text);
            if (specs == null) {
                allowed = null;
                break;
            }
            allowed.addAll(specs);
        }
        Map<String, StyledText> parsedForms = new LinkedHashMap<>();
        for (Map.Entry<String, String> form : rawForms.entrySet()) {
            String raw = form.getValue();
            if (raw.trim().isEmpty()) {
                // An empty form falls back to "other" at runtime.
                continue;
            }
            String textProblem = textProblem(raw);
            if (textProblem != null) {
                return new Checked(key, Status.BAD_TEXT, form.getKey() + ": " + textProblem, null, null);
            }
            StyledText parsed;
            try {
                parsed = styled ? StyledText.parseMarkup(raw, links) : StyledText.plain(raw);
            } catch (StyledText.MarkupException ex) {
                return new Checked(key, Status.BAD_MARKUP, form.getKey() + ": " + ex.getMessage(), null, null);
            }
            String problem = placeholderProblem(englishHasPercent, allowed, parsed.text, true);
            if (problem != null) {
                return new Checked(key, Status.BAD_PLACEHOLDERS, form.getKey() + ": " + problem, null, null);
            }
            parsedForms.put(form.getKey(), parsed);
        }
        return new Checked(key, isOutdated(entry, src) ? Status.OUTDATED : Status.OK, null, null,
                Collections.unmodifiableMap(parsedForms));
    }

    private static boolean isOutdated(@NonNull LanguagePack.Entry entry, @NonNull SourceString src) {
        if (entry.original == null) {
            return false;
        }
        TextValue current = src.toMarkupValue();
        return current != null && !current.equals(entry.original);
    }

    @Nullable
    private static String textProblem(@NonNull String raw) {
        if (raw.length() > LanguagePackReader.MAX_TEXT_CHARS) {
            return "Longer than " + LanguagePackReader.MAX_TEXT_CHARS + " characters";
        }
        for (int i = 0; i < raw.length(); ++i) {
            char c = raw.charAt(i);
            if ((c < 0x20 && c != '\n' && c != '\t') || (c >= 0x7F && c <= 0x9F)) {
                return "Has a control character";
            }
        }
        return null;
    }

    /**
     * Returns why {@code translated} can't stand in for the English, or {@code null} when it can.
     * English without any {@code %} isn't formatted, so a literal percent sign is fine there but a
     * real placeholder isn't. English whose {@code %} doesn't parse can't be formatted either, so
     * anything goes. Otherwise the translation has to take the same arguments ({@code subset}
     * relaxes that to "no others" for plural forms, which often leave the number out).
     */
    @Nullable
    static String placeholderProblem(boolean englishHasPercent, @Nullable Set<String> expected,
                                     @NonNull String translated, boolean subset) {
        Set<String> actual = FormatSpecs.parse(translated);
        if (!englishHasPercent) {
            return actual == null || actual.isEmpty() ? null
                    : "The original has no placeholders, so this can't have any either. A % followed by a"
                    + " letter or a space and a letter counts as one.";
        }
        if (expected == null) {
            return null;
        }
        if (actual == null) {
            return "A % sign here isn't a valid placeholder (write %% for a percent sign)";
        }
        boolean ok = subset ? expected.containsAll(actual) : expected.equals(actual);
        return ok ? null : "Placeholders have to match the original";
    }
}
