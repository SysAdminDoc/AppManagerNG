// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.editor;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.text.LineSeparator;

final class CodeEditorLineSeparatorUtils {
    @NonNull
    static String convert(@NonNull CharSequence text, @NonNull LineSeparator targetSeparator) {
        String replacement = targetSeparator.getContent();
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ++i) {
            char ch = text.charAt(i);
            if (ch == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    ++i;
                }
                builder.append(replacement);
            } else if (ch == '\n') {
                builder.append(replacement);
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private CodeEditorLineSeparatorUtils() {
    }
}
