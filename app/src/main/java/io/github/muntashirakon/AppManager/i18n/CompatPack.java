// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A language file's usable text by resource ID, for Android 10 and older, which can't lay a
 * resources table over the app's own. {@link LanguagePackManager} writes one per language file
 * and app build, and {@link CompatResources} answers from it.
 */
public final class CompatPack {
    /** Bumped whenever the stored form changes, so stored copies are made again. */
    static final int FORMAT = 1;
    private static final int MAGIC = 0x414d4e47;
    private static final String[] QUANTITY_CODES = {"zero", "one", "two", "few", "many", "other"};

    @NonNull
    final String code;
    @NonNull
    final Locale locale;
    private final SparseArray<CharSequence> mTexts;
    private final SparseArray<Map<String, CharSequence>> mForms;
    @Nullable
    private volatile Object mPluralRules;

    private CompatPack(@NonNull String code, @NonNull SparseArray<CharSequence> texts,
                       @NonNull SparseArray<Map<String, CharSequence>> forms) {
        this.code = code;
        this.locale = Locale.forLanguageTag(code);
        mTexts = texts;
        mForms = forms;
    }

    /** The file's text for a string, or {@code null} when it has none. */
    @Nullable
    CharSequence text(int resId) {
        return mTexts.get(resId);
    }

    /** The file's text for a plural and {@code quantity}, or {@code null} when it has none. */
    @Nullable
    CharSequence quantityText(int resId, int quantity) {
        Map<String, CharSequence> forms = mForms.get(resId);
        if (forms == null) {
            return null;
        }
        CharSequence text = forms.get(keyword(quantity));
        return text != null ? text : forms.get("other");
    }

    /**
     * Whether {@code config} shows this file's language. A context made for another language, such
     * as one that writes English for a log, keeps its own text.
     */
    boolean appliesTo(@NonNull Configuration config) {
        Locale current = primaryLocale(config);
        if (current == null || !locale.getLanguage().equals(current.getLanguage())) {
            return false;
        }
        String country = locale.getCountry();
        String script = locale.getScript();
        return (country.isEmpty() || country.equals(current.getCountry()))
                && (script.isEmpty() || script.equals(current.getScript()));
    }

    @Nullable
    private static Locale primaryLocale(@NonNull Configuration config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return config.getLocales().isEmpty() ? null : config.getLocales().get(0);
        }
        //noinspection deprecation
        return config.locale;
    }

    /** The plural form this language uses for {@code quantity}, the way {@code Resources} picks it. */
    @NonNull
    String keyword(int quantity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Object rules = mPluralRules;
            if (rules == null) {
                rules = android.icu.text.PluralRules.forLocale(locale);
                mPluralRules = rules;
            }
            return ((android.icu.text.PluralRules) rules).select(quantity);
        }
        try {
            // What Android 5 to 6 use for plurals themselves
            Object rules = mPluralRules;
            if (rules == null) {
                Class<?> nativeRules = Class.forName("libcore.icu.NativePluralRules");
                rules = nativeRules.getMethod("forLocale", Locale.class).invoke(null, locale);
                mPluralRules = rules;
            }
            Method quantityForInt = rules.getClass().getMethod("quantityForInt", int.class);
            Object code = quantityForInt.invoke(rules, quantity);
            if (code instanceof Integer && (Integer) code >= 0 && (Integer) code < QUANTITY_CODES.length) {
                return QUANTITY_CODES[(Integer) code];
            }
        } catch (ReflectiveOperationException | RuntimeException ignore) {
        }
        return quantity == 1 ? "one" : "other";
    }

    /** Takes every entry that passes {@link LanguagePackValidator}, each under the app's ID for it. */
    @NonNull
    static CompatPack build(@NonNull AndroidSourceStrings source, @NonNull LanguagePack pack)
            throws LanguagePackException {
        SparseArray<CharSequence> texts = new SparseArray<>();
        SparseArray<Map<String, CharSequence>> forms = new SparseArray<>();
        for (Map.Entry<String, LanguagePack.Entry> e : pack.entries.entrySet()) {
            SourceString src = source.get(e.getKey());
            if (src == null || src.resId == 0) {
                continue;
            }
            LanguagePackValidator.Checked checked = LanguagePackValidator.checkEntry(e.getKey(), e.getValue(), src);
            if (!checked.isUsable()) {
                continue;
            }
            if (src.isPlural()) {
                if (checked.forms == null || checked.forms.isEmpty()) {
                    continue;
                }
                Map<String, CharSequence> map = new HashMap<>();
                for (Map.Entry<String, StyledText> form : checked.forms.entrySet()) {
                    map.put(form.getKey(), toCharSequence(form.getValue()));
                }
                forms.put(src.resId, map);
            } else if (checked.text != null) {
                texts.put(src.resId, toCharSequence(checked.text));
            }
        }
        if (texts.size() == 0 && forms.size() == 0) {
            throw new LanguagePackException(LanguagePackException.Reason.NOTHING_USABLE, "No usable entries");
        }
        return new CompatPack(pack.code, texts, forms);
    }

    @NonNull
    byte[] toBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(MAGIC);
        out.writeInt(FORMAT);
        out.writeUTF(code);
        out.writeInt(mTexts.size());
        for (int i = 0; i < mTexts.size(); ++i) {
            out.writeInt(mTexts.keyAt(i));
            writeText(out, mTexts.valueAt(i));
        }
        out.writeInt(mForms.size());
        for (int i = 0; i < mForms.size(); ++i) {
            out.writeInt(mForms.keyAt(i));
            Map<String, CharSequence> forms = mForms.valueAt(i);
            out.writeByte(forms.size());
            for (Map.Entry<String, CharSequence> form : forms.entrySet()) {
                out.writeUTF(form.getKey());
                writeText(out, form.getValue());
            }
        }
        out.flush();
        return bytes.toByteArray();
    }

    @NonNull
    static CompatPack fromBytes(@NonNull byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readInt() != MAGIC || in.readInt() != FORMAT) {
            throw new IOException("Not a prepared language file");
        }
        String code = in.readUTF();
        int count = in.readInt();
        checkCount(count);
        SparseArray<CharSequence> texts = new SparseArray<>(count);
        for (int i = 0; i < count; ++i) {
            int id = in.readInt();
            texts.put(id, readText(in));
        }
        count = in.readInt();
        checkCount(count);
        SparseArray<Map<String, CharSequence>> forms = new SparseArray<>(count);
        for (int i = 0; i < count; ++i) {
            int id = in.readInt();
            int n = in.readUnsignedByte();
            Map<String, CharSequence> map = new HashMap<>();
            for (int j = 0; j < n; ++j) {
                String keyword = in.readUTF();
                map.put(keyword, readText(in));
            }
            forms.put(id, map);
        }
        return new CompatPack(code, texts, forms);
    }

    private static void checkCount(int count) throws IOException {
        if (count < 0 || count > LanguagePackReader.MAX_ENTRIES) {
            throw new IOException("Bad entry count " + count);
        }
    }

    private static void writeText(@NonNull DataOutputStream out, @NonNull CharSequence text) throws IOException {
        out.writeUTF(text.toString());
        if (!(text instanceof Spanned)) {
            out.writeShort(0);
            return;
        }
        Spanned spanned = (Spanned) text;
        Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
        List<String> names = new ArrayList<>(spans.length);
        List<int[]> ranges = new ArrayList<>(spans.length);
        for (Object span : spans) {
            String name = spanName(span);
            if (name != null) {
                names.add(name);
                ranges.add(new int[]{spanned.getSpanStart(span), spanned.getSpanEnd(span)});
            }
        }
        out.writeShort(names.size());
        for (int i = 0; i < names.size(); ++i) {
            out.writeUTF(names.get(i));
            out.writeInt(ranges.get(i)[0]);
            out.writeInt(ranges.get(i)[1]);
        }
    }

    @NonNull
    private static CharSequence readText(@NonNull DataInputStream in) throws IOException {
        String text = in.readUTF();
        int n = in.readUnsignedShort();
        if (n == 0) {
            return text;
        }
        List<StyledText.Span> spans = new ArrayList<>(n);
        for (int i = 0; i < n; ++i) {
            String name = in.readUTF();
            int start = in.readInt();
            int end = in.readInt();
            if (start < 0 || end > text.length() || start >= end) {
                throw new IOException("Bad span");
            }
            spans.add(new StyledText.Span(name, start, end));
        }
        return toCharSequence(new StyledText(text, spans));
    }

    /** The text with the spans Android's own string block gives the same markup. */
    @NonNull
    static CharSequence toCharSequence(@NonNull StyledText styled) {
        if (!styled.isStyled()) {
            return styled.text;
        }
        SpannableString out = new SpannableString(styled.text);
        for (StyledText.Span span : styled.spans) {
            Object what = span(span.name);
            if (what != null) {
                out.setSpan(what, span.start, span.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return new SpannedString(out);
    }

    @Nullable
    private static Object span(@NonNull String name) {
        switch (name) {
            case StyledText.BOLD:
                return new StyleSpan(Typeface.BOLD);
            case StyledText.ITALIC:
                return new StyleSpan(Typeface.ITALIC);
            case StyledText.UNDERLINE:
                return new UnderlineSpan();
            case StyledText.MONOSPACE:
                return new TypefaceSpan("monospace");
            default:
                return name.startsWith(StyledText.LINK_PREFIX)
                        ? new URLSpan(name.substring(StyledText.LINK_PREFIX.length())) : null;
        }
    }

    @Nullable
    private static String spanName(@NonNull Object span) {
        if (span instanceof URLSpan) {
            return StyledText.LINK_PREFIX + ((URLSpan) span).getURL();
        }
        if (span instanceof StyleSpan) {
            int style = ((StyleSpan) span).getStyle();
            return style == Typeface.BOLD ? StyledText.BOLD : style == Typeface.ITALIC ? StyledText.ITALIC : null;
        }
        if (span instanceof UnderlineSpan) {
            return StyledText.UNDERLINE;
        }
        if (span instanceof TypefaceSpan) {
            return StyledText.MONOSPACE;
        }
        return null;
    }
}
