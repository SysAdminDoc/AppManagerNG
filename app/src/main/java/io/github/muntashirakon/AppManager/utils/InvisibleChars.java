// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds characters that draw nothing (zero-width and direction marks, odd spaces, control and
 * format characters) so they can be shown as a small {@code [NAME]} badge instead of hiding in a
 * file name, a component label or a text file. Text is read a whole code point at a time, so a
 * surrogate pair is never split and a lone surrogate passes through untouched.
 */
public final class InvisibleChars {
    public static final class Occurrence {
        /** Index of the first char in the text. */
        public final int start;
        /** Index just past the last char (two past {@link #start} for a supplementary code point). */
        public final int end;
        public final int codePoint;
        @NonNull
        public final String name;

        Occurrence(int start, int end, int codePoint, @NonNull String name) {
            this.start = start;
            this.end = end;
            this.codePoint = codePoint;
            this.name = name;
        }
    }

    private static final Map<Integer, String> NAMES = new HashMap<>();
    private static final Map<Integer, String> WHITESPACE_NAMES = new HashMap<>();
    /**
     * Format characters that do draw something: number signs and marks that sit in front of
     * digits, as in Arabic, Syriac and Kaithi text.
     */
    private static final Set<Integer> VISIBLE_FORMAT_CHARS = new HashSet<>();

    static {
        String[] c0 = {"NUL", "SOH", "STX", "ETX", "EOT", "ENQ", "ACK", "BEL", "BS", null, null, "VT", "FF", null,
                "SO", "SI", "DLE", "DC1", "DC2", "DC3", "DC4", "NAK", "SYN", "ETB", "CAN", "EM", "SUB", "ESC", "FS",
                "GS", "RS", "US"};
        for (int i = 0; i < c0.length; ++i) {
            if (c0[i] != null) {
                NAMES.put(i, c0[i]);
            }
        }
        NAMES.put(0x7F, "DEL");
        NAMES.put(0x00A0, "NBSP");
        NAMES.put(0x00AD, "SHY");
        NAMES.put(0x034F, "CGJ");
        NAMES.put(0x061C, "ALM");
        // Hangul fillers render as nothing in most fonts
        NAMES.put(0x115F, "HF");
        NAMES.put(0x1160, "HF");
        NAMES.put(0x3164, "HF");
        NAMES.put(0xFFA0, "HF");
        NAMES.put(0x180E, "MVS");
        String[] spaces = {"NQSP", "MQSP", "ENSP", "EMSP", "3/MSP", "4/MSP", "6/MSP", "FSP", "PSP", "THSP", "HSP"};
        for (int i = 0; i < spaces.length; ++i) {
            NAMES.put(0x2000 + i, spaces[i]);
        }
        NAMES.put(0x200B, "ZWSP");
        NAMES.put(0x200C, "ZWNJ");
        NAMES.put(0x200D, "ZWJ");
        NAMES.put(0x200E, "LRM");
        NAMES.put(0x200F, "RLM");
        NAMES.put(0x2028, "LSEP");
        NAMES.put(0x2029, "PSEP");
        NAMES.put(0x202A, "LRE");
        NAMES.put(0x202B, "RLE");
        NAMES.put(0x202C, "PDF");
        NAMES.put(0x202D, "LRO");
        NAMES.put(0x202E, "RLO");
        NAMES.put(0x202F, "NNBSP");
        NAMES.put(0x205F, "MMSP");
        NAMES.put(0x2060, "WJ");
        NAMES.put(0x2061, "FA");
        NAMES.put(0x2062, "IT");
        NAMES.put(0x2063, "IS");
        NAMES.put(0x2064, "IP");
        NAMES.put(0x2066, "LRI");
        NAMES.put(0x2067, "RLI");
        NAMES.put(0x2068, "FSI");
        NAMES.put(0x2069, "PDI");
        NAMES.put(0x206A, "ISS");
        NAMES.put(0x206B, "ASS");
        NAMES.put(0x206C, "IAFS");
        NAMES.put(0x206D, "AAFS");
        NAMES.put(0x206E, "NADS");
        NAMES.put(0x206F, "NODS");
        NAMES.put(0x3000, "IDSP");
        NAMES.put(0xFEFF, "BOM");
        NAMES.put(0xFFF9, "IAA");
        NAMES.put(0xFFFA, "IAS");
        NAMES.put(0xFFFB, "IAT");
        NAMES.put(0xE0001, "TAG");
        NAMES.put(0xE0020, "TAG SP");
        for (int cp = 0xE0021; cp <= 0xE007E; ++cp) {
            NAMES.put(cp, "TAG " + (char) (cp - 0xE0000));
        }
        NAMES.put(0xE007F, "TAG END");

        WHITESPACE_NAMES.put((int) ' ', "SP");
        WHITESPACE_NAMES.put((int) '\t', "TAB");
        WHITESPACE_NAMES.put((int) '\n', "LF");
        WHITESPACE_NAMES.put((int) '\r', "CR");

        for (int cp = 0x0600; cp <= 0x0605; ++cp) {
            VISIBLE_FORMAT_CHARS.add(cp);
        }
        VISIBLE_FORMAT_CHARS.add(0x06DD);
        VISIBLE_FORMAT_CHARS.add(0x070F);
        VISIBLE_FORMAT_CHARS.add(0x0890);
        VISIBLE_FORMAT_CHARS.add(0x0891);
        VISIBLE_FORMAT_CHARS.add(0x08E2);
        VISIBLE_FORMAT_CHARS.add(0x110BD);
        VISIBLE_FORMAT_CHARS.add(0x110CD);
    }

    private InvisibleChars() {
    }

    public static boolean isInvisible(int codePoint) {
        return getName(codePoint) != null;
    }

    /**
     * The short name shown in a badge, or {@code null} for a character that draws something (an
     * ordinary space, tab or line break included).
     */
    @Nullable
    public static String getName(int codePoint) {
        if (codePoint >= 0x20 && codePoint < 0x7F) {
            // Printable ASCII: the common case, answered without a lookup
            return null;
        }
        String name = NAMES.get(codePoint);
        if (name != null) {
            return name;
        }
        if ((codePoint >= 0x80 && codePoint <= 0x9F) // C1 controls
                || codePoint == 0x2800 // Braille pattern blank
                || (codePoint >= 0x1D173 && codePoint <= 0x1D17A) // Musical beam and phrase controls
                || (Character.getType(codePoint) == Character.FORMAT && !VISIBLE_FORMAT_CHARS.contains(codePoint))) {
            return String.format(Locale.ROOT, "U+%04X", codePoint);
        }
        return null;
    }

    public static boolean containsInvisible(@Nullable CharSequence text) {
        if (text == null) {
            return false;
        }
        for (int i = 0, length = text.length(); i < length; ) {
            int codePoint = Character.codePointAt(text, i);
            if (isInvisible(codePoint)) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    /**
     * Whether the text has something in it but nothing that draws: only spaces, tabs, line
     * breaks and invisible characters. A name like that looks empty, so its spaces get badges too.
     */
    public static boolean isBlank(@Nullable CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        for (int i = 0, length = text.length(); i < length; ) {
            int codePoint = Character.codePointAt(text, i);
            if (!WHITESPACE_NAMES.containsKey(codePoint) && !isInvisible(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    /**
     * Every invisible character in the text, in order. With {@code includeWhitespace}, ordinary
     * spaces, tabs and line breaks are listed too.
     */
    @NonNull
    public static List<Occurrence> find(@Nullable CharSequence text, boolean includeWhitespace) {
        if (text == null) {
            return Collections.emptyList();
        }
        List<Occurrence> occurrences = new ArrayList<>();
        for (int i = 0, length = text.length(); i < length; ) {
            int codePoint = Character.codePointAt(text, i);
            int next = i + Character.charCount(codePoint);
            String name = getName(codePoint);
            if (name == null && includeWhitespace) {
                name = WHITESPACE_NAMES.get(codePoint);
            }
            if (name != null) {
                occurrences.add(new Occurrence(i, next, codePoint, name));
            }
            i = next;
        }
        return occurrences;
    }

    /**
     * The text with each invisible character replaced by {@code [NAME]}. Spaces, tabs and line
     * breaks are marked only when the text has nothing else that draws.
     */
    @NonNull
    public static String toMarkedText(@Nullable CharSequence text) {
        if (text == null) {
            return "";
        }
        List<Occurrence> occurrences = find(text, isBlank(text));
        if (occurrences.isEmpty()) {
            return text.toString();
        }
        StringBuilder builder = new StringBuilder(text.length() + occurrences.size() * 6);
        int last = 0;
        for (Occurrence occurrence : occurrences) {
            builder.append(text, last, occurrence.start).append('[').append(occurrence.name).append(']');
            last = occurrence.end;
        }
        return builder.append(text, last, text.length()).toString();
    }

    /**
     * Same as {@link #toMarkedText(CharSequence)}, with each badge drawn in the given colors and
     * the text's own styling kept. Text with nothing to mark comes back as it was given.
     */
    @NonNull
    public static CharSequence annotate(@Nullable CharSequence text, @ColorInt int foreground,
                                        @ColorInt int background) {
        if (text == null) {
            return "";
        }
        List<Occurrence> occurrences = find(text, isBlank(text));
        if (occurrences.isEmpty()) {
            return text;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        // From the end, so earlier positions stay where find() saw them
        for (int i = occurrences.size() - 1; i >= 0; --i) {
            Occurrence occurrence = occurrences.get(i);
            String badge = "[" + occurrence.name + "]";
            builder.replace(occurrence.start, occurrence.end, badge);
            int end = occurrence.start + badge.length();
            builder.setSpan(new BackgroundColorSpan(background), occurrence.start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(foreground), occurrence.start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    /**
     * {@link #annotate(CharSequence, int, int)} in the theme's tertiary container colors.
     */
    @NonNull
    public static CharSequence annotate(@NonNull Context context, @Nullable CharSequence text) {
        if (text == null) {
            return "";
        }
        if (!containsInvisible(text) && !isBlank(text)) {
            return text;
        }
        int background = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiaryContainer,
                0xFFFFD8E4);
        int foreground = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnTertiaryContainer, 0xFF31111D);
        return annotate(text, foreground, background);
    }
}
