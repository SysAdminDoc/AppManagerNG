// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@link java.util.Formatter} placeholders of a string so a translation can be checked
 * against its English original before it reaches {@code getString(id, args)}. A translation whose
 * placeholders disagree with the original can throw at format time (a {@code %d} handed a String,
 * or a {@code %3$s} with only two arguments), so a mismatch keeps that string in English.
 */
public final class FormatSpecs {
    private static final Pattern SPEC = Pattern.compile(
            "%(?:(\\d+)\\$)?([-#+ 0,(<]*)(\\d+)?(?:\\.\\d+)?([tT][a-zA-Z]|[bBhHsScCdoxXeEfgGaA%n])");

    private FormatSpecs() {
    }

    /**
     * Returns the arguments a string consumes as {@code "<index>:<kind>"} items, or {@code null}
     * when a {@code %} in it isn't a valid placeholder. {@code %%} and {@code %n} take no argument.
     */
    @Nullable
    public static Set<String> parse(@NonNull String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = SPEC.matcher(text);
        int ordinary = 0;
        int previous = -1;
        int i = 0;
        while ((i = text.indexOf('%', i)) >= 0) {
            m.region(i, text.length());
            if (!m.lookingAt()) {
                return null;
            }
            String conversion = m.group(4);
            char c = conversion.charAt(0);
            if (c == '%' || c == 'n') {
                i = m.end();
                continue;
            }
            int index;
            String flags = m.group(2);
            if (m.group(1) != null) {
                index = parseIndex(m.group(1));
                if (index <= 0) {
                    return null;
                }
            } else if (flags != null && flags.indexOf('<') >= 0) {
                if (previous < 0) {
                    return null;
                }
                index = previous;
            } else {
                index = ++ordinary;
            }
            previous = index;
            out.add(index + ":" + kindOf(c));
            i = m.end();
        }
        return out;
    }

    /** True when {@code text} contains at least one {@code %} sequence, valid or not. */
    public static boolean hasPercent(@NonNull String text) {
        return text.indexOf('%') >= 0;
    }

    private static int parseIndex(@NonNull String digits) {
        if (digits.length() > 3) {
            return -1;
        }
        return Integer.parseInt(digits);
    }

    private static char kindOf(char conversion) {
        switch (conversion) {
            case 'd':
            case 'o':
            case 'x':
            case 'X':
                return 'd';
            case 'e':
            case 'E':
            case 'f':
            case 'g':
            case 'G':
            case 'a':
            case 'A':
                return 'f';
            case 'c':
            case 'C':
                return 'c';
            case 't':
            case 'T':
                return 't';
            default:
                // s, S, b, B, h, H accept any argument.
                return 's';
        }
    }
}
