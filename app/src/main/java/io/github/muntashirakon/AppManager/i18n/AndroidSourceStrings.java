// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;

/**
 * The running app's translatable English strings. Which strings are translatable comes from an
 * asset the build writes from {@code values/strings.xml} (skipping {@code translatable="false"}),
 * and IDs are looked up by name, so strings the release build dropped are simply left out.
 */
public final class AndroidSourceStrings implements SourceStrings {
    static final String KEYS_ASSET = "language_pack_keys.txt";

    @NonNull
    private final Resources mResources;
    @NonNull
    private final Resources mEnglish;
    @NonNull
    private final String mPackage;
    /** Translatable keys in file order, mapped to {@code true} for plurals. */
    @NonNull
    private final Map<String, Boolean> mDeclared;
    private final Map<String, Integer> mIds = new HashMap<>();
    private final Map<String, SourceString> mStrings = new HashMap<>();
    @Nullable
    private List<String> mKeys;

    public AndroidSourceStrings(@NonNull Context context) throws IOException {
        mResources = context.getResources();
        mPackage = mResources.getResourcePackageName(R.string.app_name);
        mEnglish = localized(context, Locale.ENGLISH);
        mDeclared = readKeys(context);
    }

    @NonNull
    static Resources localized(@NonNull Context context, @NonNull Locale locale) {
        Configuration conf = new Configuration(context.getResources().getConfiguration());
        conf.setLocale(locale);
        return context.createConfigurationContext(conf).getResources();
    }

    @NonNull
    private static Map<String, Boolean> readKeys(@NonNull Context context) throws IOException {
        Map<String, Boolean> out = new LinkedHashMap<>();
        try (InputStream in = context.getAssets().open(KEYS_ASSET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 2 && line.charAt(1) == ' ' && (line.charAt(0) == 's' || line.charAt(0) == 'p')) {
                    out.put(line.substring(2), line.charAt(0) == 'p');
                }
            }
        }
        return out;
    }

    @NonNull
    @Override
    public synchronized List<String> keys() {
        if (mKeys == null) {
            List<String> keys = new ArrayList<>(mDeclared.size());
            for (String key : mDeclared.keySet()) {
                if (resId(key) != 0) {
                    keys.add(key);
                }
            }
            mKeys = Collections.unmodifiableList(keys);
        }
        return mKeys;
    }

    @Nullable
    @Override
    public synchronized SourceString get(@NonNull String key) {
        if (mStrings.containsKey(key)) {
            return mStrings.get(key);
        }
        SourceString src = load(key);
        mStrings.put(key, src);
        return src;
    }

    /** The resource ID of a translatable string or plural, or 0. */
    public synchronized int resId(@NonNull String key) {
        Integer cached = mIds.get(key);
        if (cached != null) {
            return cached;
        }
        Boolean plural = mDeclared.get(key);
        int id = plural == null ? 0 : mResources.getIdentifier(key, plural ? "plurals" : "string", mPackage);
        mIds.put(key, id);
        return id;
    }

    public boolean isPlural(@NonNull String key) {
        return Boolean.TRUE.equals(mDeclared.get(key));
    }

    @NonNull
    public String packageName() {
        return mPackage;
    }

    @Nullable
    private SourceString load(@NonNull String key) {
        int id = resId(key);
        if (id == 0) {
            return null;
        }
        try {
            if (!isPlural(key)) {
                StyledText text = toStyledText(mEnglish.getText(id));
                return text == null ? null : SourceString.ofString(key, id, text);
            }
            // English only has "one" and "other".
            StyledText one = toStyledText(mEnglish.getQuantityText(id, 1));
            StyledText other = toStyledText(mEnglish.getQuantityText(id, 2));
            if (one == null || other == null) {
                return null;
            }
            Map<String, StyledText> forms = new LinkedHashMap<>();
            forms.put("one", one);
            forms.put("other", other);
            return SourceString.ofPlural(key, id, forms);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    /**
     * The built-in translation of every string that has one, as a language file writes it. Text
     * that's the same as the English is left out, since that's usually a string nobody translated.
     */
    @NonNull
    public Map<String, TextValue> builtInTranslation(@NonNull Context context, @NonNull Locale locale) {
        Resources res = localized(context, locale);
        Collection<String> quantities = pluralKeywords(locale);
        Map<String, TextValue> out = new LinkedHashMap<>();
        for (String key : keys()) {
            SourceString src = get(key);
            TextValue english = src == null ? null : src.toMarkupValue();
            if (english == null) {
                continue;
            }
            try {
                TextValue value = src.isPlural()
                        ? pluralForms(res, src.resId, locale, quantities)
                        : markup(res.getText(src.resId));
                if (value != null && !value.equals(english) && !value.isBlank()) {
                    out.put(key, value);
                }
            } catch (Resources.NotFoundException ignore) {
            }
        }
        return out;
    }

    @Nullable
    private static TextValue markup(@NonNull CharSequence cs) {
        StyledText text = toStyledText(cs);
        String markup = text == null ? null : text.toMarkup();
        return markup == null ? null : TextValue.ofString(markup);
    }

    @Nullable
    private static TextValue pluralForms(@NonNull Resources res, int id, @NonNull Locale locale,
                                         @NonNull Collection<String> quantities) {
        Map<String, String> forms = new LinkedHashMap<>();
        for (String q : quantities) {
            Integer sample = integerSample(locale, q);
            if (sample == null) {
                continue;
            }
            StyledText text = toStyledText(res.getQuantityText(id, sample));
            String markup = text == null ? null : text.toMarkup();
            if (markup == null) {
                return null;
            }
            forms.put(q, markup);
        }
        return forms.containsKey("other") ? TextValue.ofForms(forms) : null;
    }

    @Nullable
    private static Integer integerSample(@NonNull Locale locale, @NonNull String keyword) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.icu.text.PluralRules rules = android.icu.text.PluralRules.forLocale(locale);
            for (int n = 0; n <= 1000; ++n) {
                if (keyword.equals(rules.select(n))) {
                    return n;
                }
            }
            return null;
        }
        switch (keyword) {
            case "one":
                return 1;
            case "other":
                return 2;
            default:
                return null;
        }
    }

    /** The plural forms {@code locale} uses, in the usual order. */
    @NonNull
    public static Collection<String> pluralKeywords(@NonNull Locale locale) {
        Set<String> keywords = new LinkedHashSet<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            keywords.addAll(android.icu.text.PluralRules.forLocale(locale).getKeywords());
        } else {
            keywords.add("one");
            keywords.add("other");
        }
        return LanguagePackWriter.orderedQuantities(keywords);
    }

    /**
     * Converts text from resources into {@link StyledText}, or {@code null} when it carries a
     * style a language file can't write (sizes, colors, annotations and the like).
     */
    @Nullable
    static StyledText toStyledText(@Nullable CharSequence cs) {
        if (cs == null) {
            return null;
        }
        String text = cs.toString();
        if (!(cs instanceof Spanned)) {
            return StyledText.plain(text);
        }
        Spanned spanned = (Spanned) cs;
        List<StyledText.Span> spans = new ArrayList<>();
        for (Object span : spanned.getSpans(0, spanned.length(), Object.class)) {
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            String name = spanName(span);
            if (name == null) {
                return null;
            }
            if (end > start) {
                spans.add(new StyledText.Span(name, start, end));
            }
        }
        return new StyledText(text, spans);
    }

    /** The string pool span name each span the platform's string block creates comes from. */
    @Nullable
    private static String spanName(@NonNull Object span) {
        if (span instanceof URLSpan) {
            return StyledText.LINK_PREFIX + ((URLSpan) span).getURL();
        }
        if (span.getClass() == StyleSpan.class) {
            int style = ((StyleSpan) span).getStyle();
            if (style == Typeface.BOLD) {
                return StyledText.BOLD;
            }
            if (style == Typeface.ITALIC) {
                return StyledText.ITALIC;
            }
            return null;
        }
        if (span.getClass() == UnderlineSpan.class) {
            return StyledText.UNDERLINE;
        }
        if (span.getClass() == TypefaceSpan.class && "monospace".equals(((TypefaceSpan) span).getFamily())) {
            return StyledText.MONOSPACE;
        }
        return null;
    }
}
