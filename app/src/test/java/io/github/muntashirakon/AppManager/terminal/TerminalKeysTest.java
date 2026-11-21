// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The extra keys row sends the same bytes a terminal keyboard would (#55).
 */
public class TerminalKeysTest {
    private static final String ESC = String.valueOf((char) 27);

    @Test
    public void escapeAndTabSendTheirOwnBytes() {
        assertEquals(ESC, TerminalKeys.encodeKey(TerminalKeys.KEY_ESC, false, false));
        assertEquals("\t", TerminalKeys.encodeKey(TerminalKeys.KEY_TAB, false, false));
        // Alt prefixes ESC, the way xterm's metaSendsEscape does
        assertEquals(ESC + ESC, TerminalKeys.encodeKey(TerminalKeys.KEY_ESC, false, true));
        assertEquals(ESC + "\t", TerminalKeys.encodeKey(TerminalKeys.KEY_TAB, false, true));
    }

    @Test
    public void arrowsSendCursorSequences() {
        assertEquals(ESC + "[A", TerminalKeys.encodeKey(TerminalKeys.KEY_UP, false, false));
        assertEquals(ESC + "[B", TerminalKeys.encodeKey(TerminalKeys.KEY_DOWN, false, false));
        assertEquals(ESC + "[C", TerminalKeys.encodeKey(TerminalKeys.KEY_RIGHT, false, false));
        assertEquals(ESC + "[D", TerminalKeys.encodeKey(TerminalKeys.KEY_LEFT, false, false));
    }

    @Test
    public void modifiedArrowsCarryTheXtermModifierNumber() {
        assertEquals(ESC + "[1;5A", TerminalKeys.encodeKey(TerminalKeys.KEY_UP, true, false));
        assertEquals(ESC + "[1;3D", TerminalKeys.encodeKey(TerminalKeys.KEY_LEFT, false, true));
        assertEquals(ESC + "[1;7C", TerminalKeys.encodeKey(TerminalKeys.KEY_RIGHT, true, true));
    }

    @Test
    public void ctrlWithALetterSendsItsControlCode() {
        assertEquals("" + (char) 3, TerminalKeys.encodeChar('c', true, false));
        assertEquals("" + (char) 3, TerminalKeys.encodeChar('C', true, false));
        assertEquals("" + (char) 1, TerminalKeys.encodeChar('a', true, false));
        assertEquals("" + (char) 26, TerminalKeys.encodeChar('z', true, false));
        assertEquals("" + (char) 4, TerminalKeys.encodeChar('d', true, false));
    }

    @Test
    public void ctrlWithPunctuationFollowsTheAsciiTable() {
        assertEquals(0, TerminalKeys.controlCode('@'));
        assertEquals(0, TerminalKeys.controlCode(' '));
        assertEquals(0, TerminalKeys.controlCode('2'));
        assertEquals(27, TerminalKeys.controlCode('['));
        assertEquals(27, TerminalKeys.controlCode('3'));
        assertEquals(28, TerminalKeys.controlCode('\\'));
        assertEquals(28, TerminalKeys.controlCode('4'));
        assertEquals(29, TerminalKeys.controlCode(']'));
        assertEquals(29, TerminalKeys.controlCode('5'));
        assertEquals(30, TerminalKeys.controlCode('^'));
        assertEquals(30, TerminalKeys.controlCode('6'));
        assertEquals(31, TerminalKeys.controlCode('_'));
        assertEquals(31, TerminalKeys.controlCode('7'));
        assertEquals(31, TerminalKeys.controlCode('/'));
        assertEquals(127, TerminalKeys.controlCode('?'));
        assertEquals(127, TerminalKeys.controlCode('8'));
        assertEquals(-1, TerminalKeys.controlCode('-'));
        assertEquals(-1, TerminalKeys.controlCode('|'));
        assertEquals(-1, TerminalKeys.controlCode(0x00E9));
    }

    @Test
    public void ctrlWithAKeyThatHasNoControlCodeSendsTheKey() {
        assertEquals("-", TerminalKeys.encodeChar('-', true, false));
        assertEquals("|", TerminalKeys.encodeChar('|', true, false));
    }

    @Test
    public void altPrefixesEscape() {
        assertEquals(ESC + "b", TerminalKeys.encodeChar('b', false, true));
        assertEquals(ESC + (char) 2, TerminalKeys.encodeChar('b', true, true));
        assertEquals(ESC + "|", TerminalKeys.encodeChar('|', false, true));
    }

    @Test
    public void plainCharactersPassThroughWholeCodePoints() {
        assertEquals("-", TerminalKeys.encodeChar('-', false, false));
        assertEquals("/", TerminalKeys.encodeChar('/', false, false));
        assertEquals("|", TerminalKeys.encodeChar('|', false, false));
        String face = new String(Character.toChars(0x1F600));
        assertEquals(face, TerminalKeys.encodeChar(0x1F600, false, false));
        assertEquals(ESC + face, TerminalKeys.encodeChar(0x1F600, true, true));
    }

    @Test
    public void echoNamesControlCharactersTheCaretWay() {
        assertEquals("^C", TerminalKeys.describe((char) 3 + ""));
        assertEquals("^[", TerminalKeys.describe(ESC));
        assertEquals("^?", TerminalKeys.describe((char) 127 + ""));
        assertEquals("^[b", TerminalKeys.describe(ESC + "b"));
        assertEquals("^[[A", TerminalKeys.describe(ESC + "[A"));
        assertEquals("ls", TerminalKeys.describe("ls"));
    }
}
