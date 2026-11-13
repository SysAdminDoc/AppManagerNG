// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A search a user typed, worked out once and then tried against many rows. Plain text matches anywhere in a field,
 * ignoring case. A regular expression is compiled once, also ignoring case, and matches anywhere unless it anchors
 * itself. A pattern that doesn't compile matches nothing, and {@link #isValid()} says so, so the screen can tell the
 * user instead of showing an empty list with no reason.
 */
@AnyThread
public final class TextQuery {
    private static final TextQuery EMPTY = new TextQuery("", false, null, true);

    /** As typed, so a screen can put it back in the search box. */
    @NonNull
    private final String mQuery;
    /** Lower case, for plain text. */
    @NonNull
    private final String mNeedle;
    private final boolean mRegex;
    @Nullable
    private final Pattern mPattern;
    private final boolean mValid;

    private TextQuery(@NonNull String query, boolean regex, @Nullable Pattern pattern, boolean valid) {
        mQuery = query;
        mNeedle = query.toLowerCase(Locale.ROOT);
        mRegex = regex;
        mPattern = pattern;
        mValid = valid;
    }

    /** A query that matches every row. */
    @NonNull
    public static TextQuery empty() {
        return EMPTY;
    }

    @NonNull
    public static TextQuery of(@Nullable String query, boolean regex) {
        if (query == null || query.isEmpty()) {
            return EMPTY;
        }
        if (!regex) {
            return new TextQuery(query, false, null, true);
        }
        if (couldStall(query)) {
            return new TextQuery(query, true, null, false);
        }
        try {
            return new TextQuery(query, true, Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                    true);
        } catch (PatternSyntaxException e) {
            return new TextQuery(query, true, null, false);
        }
    }

    /** @return Whether the regular expression compiles and can't run away on a long text. */
    public static boolean isUsableRegex(@NonNull String pattern) {
        if (couldStall(pattern)) {
            return false;
        }
        try {
            Pattern.compile(pattern);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * Android's regex engine has no time limit, so a pattern that backtracks without end would hang whatever thread
     * runs it. This turns away the usual shapes before they run: a repeated group that repeats inside, such as
     * {@code (a+)+}, and more than three open-ended repeats in one pattern. It errs on the side of refusing.
     */
    public static boolean couldStall(@NonNull String pattern) {
        // One flag per open group: whether something inside it repeats.
        Deque<boolean[]> groups = new ArrayDeque<>();
        boolean inClass = false;
        boolean afterQuantifier = false;
        boolean closedRepeatingGroup = false;
        int openEnded = 0;
        for (int i = 0; i < pattern.length(); ++i) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                ++i;
                afterQuantifier = false;
                closedRepeatingGroup = false;
                continue;
            }
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                continue;
            }
            boolean quantifier = c == '*' || c == '+' || c == '?' || c == '{';
            if (quantifier && afterQuantifier && (c == '?' || c == '+')) {
                // A lazy or possessive mark on the repeat before it, not a repeat of its own.
                afterQuantifier = false;
                continue;
            }
            if (quantifier) {
                boolean bounded = c == '?';
                if (c == '{') {
                    int close = pattern.indexOf('}', i);
                    if (close < 0) {
                        // Not a repeat at all, the compiler decides what it is.
                        afterQuantifier = false;
                        closedRepeatingGroup = false;
                        continue;
                    }
                    String body = pattern.substring(i + 1, close);
                    bounded = !body.endsWith(",");
                    i = close;
                }
                if (closedRepeatingGroup && c != '?') {
                    return true;
                }
                if (!bounded) {
                    ++openEnded;
                }
                if (!groups.isEmpty()) {
                    groups.peek()[0] = true;
                }
                afterQuantifier = true;
                closedRepeatingGroup = false;
                continue;
            }
            afterQuantifier = false;
            closedRepeatingGroup = false;
            if (c == '[') {
                inClass = true;
                // A leading ] is part of the class.
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '^') {
                    ++i;
                }
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == ']') {
                    ++i;
                }
            } else if (c == '(') {
                groups.push(new boolean[]{false});
                // (?:, (?i), (?= and the rest: that ? opens a construct, it doesn't repeat anything.
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '?') {
                    ++i;
                }
            } else if (c == ')' && !groups.isEmpty()) {
                boolean repeats = groups.pop()[0];
                if (repeats && !groups.isEmpty()) {
                    groups.peek()[0] = true;
                }
                closedRepeatingGroup = repeats;
            }
        }
        return openEnded > 3;
    }

    public boolean isEmpty() {
        return mQuery.isEmpty();
    }

    public boolean isRegex() {
        return mRegex;
    }

    /** {@code false} for a regular expression that doesn't compile. Such a query matches nothing. */
    public boolean isValid() {
        return mValid;
    }

    @NonNull
    public String getQuery() {
        return mQuery;
    }

    /** @return Whether any of the fields matches. An empty query matches every row, a broken one none. */
    public boolean matches(@Nullable CharSequence... fields) {
        if (!mValid) {
            return false;
        }
        if (isEmpty()) {
            return true;
        }
        if (fields == null) {
            return false;
        }
        for (CharSequence field : fields) {
            if (field != null && matchesOne(field)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOne(@NonNull CharSequence field) {
        if (mPattern != null) {
            return mPattern.matcher(field).find();
        }
        return field.toString().toLowerCase(Locale.ROOT).contains(mNeedle);
    }
}
