// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import androidx.annotation.NonNull;

/**
 * The bytes the extra keys row sends, the same ones an xterm-style keyboard would: control codes
 * for Ctrl, an ESC prefix for Alt, and CSI sequences for the arrows.
 */
public final class TerminalKeys {
    public static final int KEY_ESC = 0;
    public static final int KEY_TAB = 1;
    public static final int KEY_UP = 2;
    public static final int KEY_DOWN = 3;
    public static final int KEY_RIGHT = 4;
    public static final int KEY_LEFT = 5;

    private static final char ESC = 27;

    private TerminalKeys() {
    }

    /**
     * The control code Ctrl plus this character sends, or -1 when it has none.
     */
    public static int controlCode(int codePoint) {
        if (codePoint >= 'a' && codePoint <= 'z') {
            return codePoint - 'a' + 1;
        }
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return codePoint - 'A' + 1;
        }
        switch (codePoint) {
            case '@':
            case ' ':
            case '2':
                return 0;
            case '[':
            case '3':
                return 27;
            case '\\':
            case '4':
                return 28;
            case ']':
            case '5':
                return 29;
            case '^':
            case '6':
                return 30;
            case '_':
            case '7':
            case '/':
                return 31;
            case '?':
            case '8':
                return 127;
            default:
                return -1;
        }
    }

    /**
     * A typed character with the modifiers applied. Ctrl turns it into its control code when it
     * has one; Alt puts ESC in front.
     */
    @NonNull
    public static String encodeChar(int codePoint, boolean ctrl, boolean alt) {
        String text = null;
        if (ctrl) {
            int code = controlCode(codePoint);
            if (code >= 0) {
                text = String.valueOf((char) code);
            }
        }
        if (text == null) {
            text = new String(Character.toChars(codePoint));
        }
        return alt ? ESC + text : text;
    }

    /**
     * A special key with the modifiers applied. Modified arrows use the xterm form ESC [ 1 ; m X,
     * where m is 1 plus 2 for Alt plus 4 for Ctrl.
     */
    @NonNull
    public static String encodeKey(int key, boolean ctrl, boolean alt) {
        switch (key) {
            case KEY_ESC:
                return alt ? "" + ESC + ESC : String.valueOf(ESC);
            case KEY_TAB:
                return alt ? ESC + "\t" : "\t";
            case KEY_UP:
                return arrow('A', ctrl, alt);
            case KEY_DOWN:
                return arrow('B', ctrl, alt);
            case KEY_RIGHT:
                return arrow('C', ctrl, alt);
            case KEY_LEFT:
                return arrow('D', ctrl, alt);
            default:
                throw new IllegalArgumentException("Unknown key " + key);
        }
    }

    @NonNull
    private static String arrow(char direction, boolean ctrl, boolean alt) {
        if (!ctrl && !alt) {
            return ESC + "[" + direction;
        }
        int modifier = 1 + (alt ? 2 : 0) + (ctrl ? 4 : 0);
        return ESC + "[1;" + modifier + direction;
    }

    /**
     * How sent bytes are echoed in the output: control characters in caret form (^C, ^[), the
     * rest as is.
     */
    @NonNull
    public static String describe(@NonNull String sent) {
        StringBuilder builder = new StringBuilder(sent.length() + 4);
        for (int i = 0; i < sent.length(); ++i) {
            char c = sent.charAt(i);
            if (c < 0x20) {
                builder.append('^').append((char) (c + '@'));
            } else if (c == 127) {
                builder.append("^?");
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
