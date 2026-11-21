// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.AppManager.utils.InvisibleChars;
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer;
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentLine;

/**
 * Marks invisible characters in the code editor with inlay badges. A badge is drawn next to its
 * character and is never part of the text, so saving, copying and searching see the file as it is.
 */
final class EditorInvisibleChars {
    /** Enough to show a file is full of them without slowing a huge one down. */
    static final int MAX_HINTS = 5000;

    private EditorInvisibleChars() {
    }

    /**
     * One badge per invisible character, at most {@code limit} of them, or {@code null} when the
     * text has none. Tabs and line breaks are ordinary text here and get no badge.
     */
    @Nullable
    static InlayHintsContainer buildHints(@NonNull Content content, int limit) {
        InlayHintsContainer hints = null;
        int count = 0;
        for (int line = 0, lineCount = content.getLineCount(); line < lineCount; ++line) {
            ContentLine text = content.getLine(line);
            for (int column = 0, length = text.length(); column < length; ) {
                int codePoint = Character.codePointAt(text, column);
                String name = InvisibleChars.getName(codePoint);
                if (name != null) {
                    if (count >= limit) {
                        return hints;
                    }
                    if (hints == null) {
                        hints = new InlayHintsContainer();
                    }
                    hints.add(new TextInlayHint(line, column, name));
                    ++count;
                }
                column += Character.charCount(codePoint);
            }
        }
        return hints;
    }
}
