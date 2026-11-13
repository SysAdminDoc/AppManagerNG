// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes language files people edit by hand: a template with every English string, or an installed
 * file with each entry marked when it's missing, out of date or can't be used as written. The JSON
 * is written here rather than through org.json so it keeps a stable key order, readable
 * indentation and unescaped slashes in links.
 */
public final class LanguagePackWriter {
    public static final String STATUS_MISSING = "missing";
    public static final String STATUS_OUTDATED = "outdated";
    public static final String STATUS_NEEDS_FIX = "needs_fix";
    public static final String STATUS_UNUSED = "unused";

    static final String INSTRUCTIONS = "Fill in each \"translation\" and leave \"original\" as it is. "
            + "Placeholders like %s or %1$d have to stay exactly as they are in the original, and tags "
            + "like <b> should wrap the matching words. An empty translation keeps the English. For "
            + "plurals, fill in the forms your language uses and leave any others empty. Set "
            + "language_code (like de or pt-BR) and "
            + "language_name, then import the file in Settings, Appearance, Language files.";

    private LanguagePackWriter() {
    }

    /**
     * A file with every English string and an empty translation, or the matching text from
     * {@code prefill} when there is some.
     *
     * @param pluralKeywords the plural forms the target language uses, from its plural rules
     */
    @NonNull
    public static String template(@NonNull String code, @NonNull String name, @NonNull SourceStrings source,
                                  @NonNull Map<String, TextValue> prefill, @NonNull Collection<String> pluralKeywords,
                                  @Nullable String madeWith) {
        List<String> quantities = orderedQuantities(pluralKeywords);
        StringBuilder sb = new StringBuilder(source.keys().size() * 96);
        header(sb, code, name, 1, Collections.emptyList(), madeWith);
        boolean first = true;
        for (String key : source.keys()) {
            SourceString src = source.get(key);
            TextValue original = src == null ? null : src.toMarkupValue();
            if (original == null) {
                continue;
            }
            TextValue translation = withForms(prefill.get(key), src.isPlural(), quantities);
            first = entry(sb, first, key, original, translation, null, null, null);
        }
        footer(sb);
        return sb.toString();
    }

    /** {@code pack} as a file to keep working on, with every string the app has in the app's order. */
    @NonNull
    public static String forEditing(@NonNull LanguagePack pack, @NonNull LanguagePackValidator.Report report,
                                    @NonNull SourceStrings source, @NonNull Collection<String> pluralKeywords,
                                    @Nullable String madeWith) {
        List<String> quantities = orderedQuantities(pluralKeywords);
        StringBuilder sb = new StringBuilder(source.keys().size() * 112);
        header(sb, pack.code, pack.name, pack.version, pack.translators, madeWith);
        boolean first = true;
        for (String key : source.keys()) {
            SourceString src = source.get(key);
            TextValue original = src == null ? null : src.toMarkupValue();
            if (original == null) {
                continue;
            }
            LanguagePack.Entry entry = pack.entries.get(key);
            LanguagePackValidator.Checked checked = report.entries.get(key);
            if (entry == null || checked == null) {
                first = entry(sb, first, key, original, withForms(null, src.isPlural(), quantities),
                        STATUS_MISSING, null, null);
                continue;
            }
            // Keep the translator's text even when it's the wrong kind, so nothing they wrote is lost.
            TextValue translation = entry.translation.isForms() == src.isPlural()
                    ? withForms(entry.translation, src.isPlural(), quantities)
                    : entry.translation;
            String status = null;
            TextValue previous = null;
            String problem = null;
            if (!checked.isUsable()) {
                status = STATUS_NEEDS_FIX;
                problem = checked.problem;
            } else if (checked.status == LanguagePackValidator.Status.OUTDATED) {
                status = STATUS_OUTDATED;
                previous = entry.original;
            }
            first = entry(sb, first, key, original, translation, status, previous, problem);
        }
        // Strings this app version doesn't have stay at the end, so going back to an older version
        // or forward to a newer one doesn't lose them.
        for (Map.Entry<String, LanguagePack.Entry> e : pack.entries.entrySet()) {
            if (source.get(e.getKey()) == null) {
                LanguagePack.Entry entry = e.getValue();
                first = entry(sb, first, e.getKey(), entry.original, entry.translation, STATUS_UNUSED, null,
                        "This app version has no string with this key");
            }
        }
        footer(sb);
        return sb.toString();
    }

    /** Plural forms in the usual order, always with {@code other}. */
    @NonNull
    static List<String> orderedQuantities(@NonNull Collection<String> keywords) {
        List<String> out = new ArrayList<>(SourceString.QUANTITIES.length);
        for (String q : SourceString.QUANTITIES) {
            if (keywords.contains(q) || q.equals("other")) {
                out.add(q);
            }
        }
        return out;
    }

    @NonNull
    private static TextValue withForms(@Nullable TextValue value, boolean plural, @NonNull List<String> quantities) {
        if (!plural) {
            return value != null && !value.isForms() ? value : TextValue.ofString("");
        }
        Map<String, String> forms = new LinkedHashMap<>();
        Map<String, String> existing = value != null && value.isForms() ? value.forms() : Collections.emptyMap();
        for (String q : SourceString.QUANTITIES) {
            String form = existing.get(q);
            if (form != null) {
                forms.put(q, form);
            } else if (quantities.contains(q)) {
                forms.put(q, "");
            }
        }
        return TextValue.ofForms(forms);
    }

    private static void header(@NonNull StringBuilder sb, @NonNull String code, @NonNull String name, int version,
                               @NonNull List<String> translators, @Nullable String madeWith) {
        sb.append("{\n");
        key(sb, 1, "schema_version").append(LanguagePack.SCHEMA_VERSION).append(",\n");
        key(sb, 1, "language_code");
        string(sb, code).append(",\n");
        key(sb, 1, "language_name");
        string(sb, name).append(",\n");
        key(sb, 1, "translation_version").append(version).append(",\n");
        key(sb, 1, "translators").append('[');
        for (int i = 0; i < translators.size(); ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            string(sb, translators.get(i));
        }
        sb.append("],\n");
        if (madeWith != null) {
            key(sb, 1, "made_with");
            string(sb, madeWith).append(",\n");
        }
        key(sb, 1, "instructions");
        string(sb, INSTRUCTIONS).append(",\n");
        key(sb, 1, "translations").append('{');
    }

    private static void footer(@NonNull StringBuilder sb) {
        sb.append("\n  }\n}\n");
    }

    private static boolean entry(@NonNull StringBuilder sb, boolean first, @NonNull String key,
                                 @Nullable TextValue original, @NonNull TextValue translation,
                                 @Nullable String status, @Nullable TextValue previous, @Nullable String problem) {
        sb.append(first ? "\n" : ",\n");
        key(sb, 2, key).append("{\n");
        if (original != null) {
            key(sb, 3, "original");
            value(sb, original, 3);
            sb.append(",\n");
        }
        key(sb, 3, "translation");
        value(sb, translation, 3);
        if (status != null) {
            sb.append(",\n");
            key(sb, 3, "status");
            string(sb, status);
        }
        if (previous != null) {
            sb.append(",\n");
            key(sb, 3, "previous_original");
            value(sb, previous, 3);
        }
        if (problem != null) {
            sb.append(",\n");
            key(sb, 3, "problem");
            string(sb, problem);
        }
        sb.append('\n');
        indent(sb, 2).append('}');
        return false;
    }

    private static void value(@NonNull StringBuilder sb, @NonNull TextValue value, int level) {
        if (!value.isForms()) {
            string(sb, value.string());
            return;
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> form : value.forms().entrySet()) {
            sb.append(first ? "\n" : ",\n");
            first = false;
            key(sb, level + 1, form.getKey());
            string(sb, form.getValue());
        }
        sb.append('\n');
        indent(sb, level).append('}');
    }

    @NonNull
    private static StringBuilder key(@NonNull StringBuilder sb, int level, @NonNull String key) {
        indent(sb, level);
        return string(sb, key).append(": ");
    }

    @NonNull
    private static StringBuilder indent(@NonNull StringBuilder sb, int level) {
        for (int i = 0; i < level; ++i) {
            sb.append("  ");
        }
        return sb;
    }

    /** Appends {@code s} as a JSON string. Characters JSON or a text editor could trip on are escaped. */
    @NonNull
    static StringBuilder string(@NonNull StringBuilder sb, @NonNull String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append('\\').append('"');
                    break;
                case '\\':
                    sb.append('\\').append('\\');
                    break;
                case '\n':
                    sb.append('\\').append('n');
                    break;
                case '\r':
                    sb.append('\\').append('r');
                    break;
                case '\t':
                    sb.append('\\').append('t');
                    break;
                default:
                    if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                        sb.append(c).append(s.charAt(++i));
                    } else if (c < 0x20 || c == 0x2028 || c == 0x2029 || Character.isSurrogate(c)) {
                        hexEscape(sb, c);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"');
    }

    private static void hexEscape(@NonNull StringBuilder sb, char c) {
        String hex = Integer.toHexString(c);
        sb.append('\\').append('u');
        for (int pad = hex.length(); pad < 4; ++pad) {
            sb.append('0');
        }
        sb.append(hex);
    }
}
