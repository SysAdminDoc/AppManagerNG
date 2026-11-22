// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Search by meaning without a model: a small table of English words that go together, and a plain stemmer for plurals,
 * -ing and -ed, so "connecting" finds "connection" and "internet" finds "Wi-Fi is off". English only.
 * <p>
 * To add a word, put it in the group it goes with, or add a group. A word may sit in more than one group. Phrases are
 * fine ("battery saver"), and so are any of a word's forms, since both sides are stemmed before they're compared.
 */
@AnyThread
public final class RelatedWords {
    /** Words that mean much the same in a short Android message. The first one names the group. */
    private static final String[][] GROUPS = {
            {"internet", "network", "wifi", "wi-fi", "connection", "online", "offline", "data", "mobile data",
                    "signal", "dns", "proxy", "vpn"},
            {"storage", "disk", "space", "memory", "sd card", "sdcard", "files", "folders"},
            {"battery", "power", "charging", "battery saver"},
            {"permission", "access", "allowed", "denied", "granted"},
            {"error", "failed", "failure", "exception", "crash", "couldn't", "could not", "unable", "can't",
                    "cannot"},
            {"update", "upgrade", "new version", "install"},
            {"download", "fetch", "get", "transfer"},
    };

    /** Each group's words, stemmed, as word sequences. */
    private static final List<List<List<String>>> STEMMED_GROUPS;

    static {
        List<List<List<String>>> groups = new ArrayList<>(GROUPS.length);
        for (String[] group : GROUPS) {
            List<List<String>> phrases = new ArrayList<>(group.length);
            for (String phrase : group) {
                List<String> words = words(phrase);
                if (!words.isEmpty() && !phrases.contains(words)) {
                    phrases.add(Collections.unmodifiableList(words));
                }
            }
            groups.add(Collections.unmodifiableList(phrases));
        }
        STEMMED_GROUPS = Collections.unmodifiableList(groups);
    }

    private RelatedWords() {
    }

    /**
     * The word's stem: lower case, without a plural, -ing or -ed ending, and with -ion and a silent e taken off, so
     * every form of a word gives the same stem. Stems aren't always words ("updat"), they're only compared.
     */
    @NonNull
    public static String stem(@NonNull String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.length() <= 3) {
            return w;
        }
        // Plurals
        if (w.endsWith("ies") && w.length() > 4) {
            w = w.substring(0, w.length() - 3) + "y";
        } else if (w.endsWith("sses")) {
            w = w.substring(0, w.length() - 2);
        } else if (w.endsWith("xes") || w.endsWith("zes") || w.endsWith("ches") || w.endsWith("shes")) {
            w = w.substring(0, w.length() - 2);
        } else if (w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is")) {
            w = w.substring(0, w.length() - 1);
        }
        // -ing and -ed
        if (w.endsWith("ing")) {
            w = withoutEnding(w, 3);
        } else if (w.endsWith("ied") && w.length() > 4) {
            w = w.substring(0, w.length() - 3) + "y";
        } else if (w.endsWith("ed") && !w.endsWith("eed")) {
            w = withoutEnding(w, 2);
        }
        // -ation, -tion and -sion
        if (w.endsWith("ation") && w.length() - 5 >= 3) {
            w = w.substring(0, w.length() - 5);
        } else if ((w.endsWith("tion") || w.endsWith("sion")) && w.length() - 3 >= 3) {
            w = w.substring(0, w.length() - 3);
        }
        // A silent e, so "charge" and "charging" meet
        if (w.length() > 3 && w.endsWith("e") && !w.endsWith("ee")) {
            w = w.substring(0, w.length() - 1);
        }
        return w;
    }

    /**
     * Takes off an -ing or -ed ending when what's left still has a vowel and at least three letters ("string" and
     * "red" stay), and undoes a doubled last letter ("stopped" to "stop", but "installed" keeps "install").
     */
    @NonNull
    private static String withoutEnding(@NonNull String word, int ending) {
        String base = word.substring(0, word.length() - ending);
        if (base.length() < 3 || !hasVowel(base)) {
            return word;
        }
        int n = base.length();
        if (n >= 4) {
            char last = base.charAt(n - 1);
            if (last == base.charAt(n - 2) && !isVowel(last) && last != 'l' && last != 's' && last != 'z') {
                base = base.substring(0, n - 1);
            }
        }
        return base;
    }

    private static boolean hasVowel(@NonNull String s) {
        for (int i = 0; i < s.length(); ++i) {
            if (isVowel(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
    }

    /**
     * The text's words, stemmed, in order. Apostrophes are dropped ("couldn't" and "couldnt" are one word), a hyphen
     * inside a word joins it ("Wi-Fi" is "wifi"), and anything else that isn't a letter or a digit splits words.
     */
    @NonNull
    public static List<String> words(@Nullable CharSequence text) {
        if (text == null || text.length() == 0) {
            return Collections.emptyList();
        }
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; ) {
            int c = Character.codePointAt(text, i);
            int next = i + Character.charCount(c);
            if (Character.isLetterOrDigit(c)) {
                word.appendCodePoint(c);
            } else if (isApostrophe(c) && word.length() > 0) {
                // Part of the word, but left out of it.
            } else if (isHyphen(c) && word.length() > 0 && next < length
                    && Character.isLetterOrDigit(Character.codePointAt(text, next))) {
                // Joins the two halves.
            } else if (word.length() > 0) {
                words.add(stem(word.toString()));
                word.setLength(0);
            }
            i = next;
        }
        if (word.length() > 0) {
            words.add(stem(word.toString()));
        }
        return words;
    }

    private static boolean isApostrophe(int c) {
        return c == '\'' || c == 0x2019 || c == 0x2018 || c == 0x02BC;
    }

    private static boolean isHyphen(int c) {
        return c == '-' || c == 0x2010 || c == 0x2011;
    }

    /** A query worked out once: each of its words or phrases, with the words related to it. */
    @NonNull
    public static Query parse(@NonNull String query) {
        List<String> words = words(query);
        List<List<List<String>>> concepts = new ArrayList<>();
        int i = 0;
        while (i < words.size()) {
            // The longest phrase from the table that starts here.
            int longest = 0;
            for (List<List<String>> group : STEMMED_GROUPS) {
                for (List<String> phrase : group) {
                    if (phrase.size() > longest && startsWith(words, i, phrase)) {
                        longest = phrase.size();
                    }
                }
            }
            if (longest == 0) {
                concepts.add(Collections.singletonList(Collections.singletonList(words.get(i))));
                ++i;
                continue;
            }
            List<String> found = words.subList(i, i + longest);
            Set<List<String>> alternatives = new LinkedHashSet<>();
            alternatives.add(new ArrayList<>(found));
            for (List<List<String>> group : STEMMED_GROUPS) {
                if (group.contains(found)) {
                    alternatives.addAll(group);
                }
            }
            concepts.add(new ArrayList<>(alternatives));
            i += longest;
        }
        return new Query(concepts);
    }

    private static boolean startsWith(@NonNull List<String> words, int from, @NonNull List<String> phrase) {
        if (from + phrase.size() > words.size()) {
            return false;
        }
        for (int j = 0; j < phrase.size(); ++j) {
            if (!words.get(from + j).equals(phrase.get(j))) {
                return false;
            }
        }
        return true;
    }

    public static final class Query {
        /** Each word or phrase of the query, as the word sequences any of which will do. */
        @NonNull
        private final List<List<List<String>>> mConcepts;

        private Query(@NonNull List<List<List<String>>> concepts) {
            mConcepts = concepts;
        }

        /**
         * @return Whether every word of the query, or one related to it, is in one of the fields. A query with no
         * words matches nothing.
         */
        public boolean matches(@Nullable CharSequence... fields) {
            if (mConcepts.isEmpty() || fields == null) {
                return false;
            }
            List<List<String>> fieldWords = new ArrayList<>(fields.length);
            for (CharSequence field : fields) {
                if (field != null && field.length() > 0) {
                    fieldWords.add(words(field));
                }
            }
            for (List<List<String>> alternatives : mConcepts) {
                if (!anyFound(alternatives, fieldWords)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean anyFound(@NonNull List<List<String>> alternatives,
                                        @NonNull List<List<String>> fieldWords) {
            for (List<String> words : fieldWords) {
                for (int i = 0; i < words.size(); ++i) {
                    for (List<String> phrase : alternatives) {
                        if (startsWith(words, i, phrase)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "RelatedWords.Query" + Arrays.toString(mConcepts.toArray());
        }
    }
}
