// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The preference keys clicked to reach the settings page on screen, one per back stack level, so
 * a recreated {@link SettingsActivity} can open the same page again.
 */
final class SettingsKeyPath {
    @NonNull
    private final ArrayList<String> mKeys = new ArrayList<>();
    private int mDepth;

    /** Starts from the keys a recreated activity saved. Its back stack is empty at this point. */
    void restore(@Nullable List<String> saved) {
        mKeys.clear();
        if (saved != null) {
            mKeys.addAll(saved);
        }
        mDepth = 0;
    }

    @Nullable
    String get(int level) {
        return level >= 0 && level < mKeys.size() ? mKeys.get(level) : null;
    }

    /**
     * Records the key clicked at {@code level}. A different key starts a new path, so the deeper
     * keys, which belonged to the old one, go. The same key keeps them: that's a recreated
     * activity walking back down the path it saved.
     */
    void save(int level, @Nullable String key) {
        while (mKeys.size() <= level) {
            mKeys.add(null);
        }
        if (!Objects.equals(mKeys.get(level), key)) {
            mKeys.subList(level + 1, mKeys.size()).clear();
            mKeys.set(level, key);
        }
    }

    /**
     * Records a page the user tapped at {@code level}. That starts a new path even when it's the
     * page already saved there: the pages below it were closed, and keeping their keys sent a
     * second tap on a parent page straight back into the child (dual pane has no back stack to
     * drop them).
     */
    void pick(int level, @Nullable String key) {
        save(level, key);
        mKeys.subList(level + 1, mKeys.size()).clear();
    }

    /**
     * Follows the back stack. Only a shallower stack drops keys, for the pages that were closed.
     * A deeper one can't: the fragment manager reports a push after the new page has started,
     * and a page being restored saves the next key on the path when it starts. Dropping keys
     * there lost that key, so a second recreate in a row (choosing a language does two) went
     * back one page too far.
     */
    void onBackStackChanged(int depth) {
        if (depth < mDepth && depth < mKeys.size()) {
            mKeys.subList(Math.max(depth, 0), mKeys.size()).clear();
        }
        mDepth = depth;
    }

    @NonNull
    ArrayList<String> toList() {
        return new ArrayList<>(mKeys);
    }
}
