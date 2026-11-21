// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

/**
 * Characters that draw nothing are shown as small named badges so a spoofed or blank-looking
 * name can't hide what it holds (#53).
 */
@RunWith(RobolectricTestRunner.class)
public class InvisibleCharsTest {
    private static final int ZWSP = 0x200B;
    private static final int ZWNJ = 0x200C;
    private static final int ZWJ = 0x200D;
    private static final int NBSP = 0x00A0;
    private static final int LRM = 0x200E;
    private static final int RLM = 0x200F;
    private static final int RLO = 0x202E;
    private static final int BOM = 0xFEFF;
    private static final int TAG_A = 0xE0061;
    private static final int TAG_CANCEL = 0xE007F;
    private static final int FACE = 0x1F600;

    @Test
    public void everyCharacterTheIssueNamesGetsItsShortName() {
        assertName("ZWSP", ZWSP);
        assertName("ZWNJ", ZWNJ);
        assertName("ZWJ", ZWJ);
        assertName("NBSP", NBSP);
        assertName("LRM", LRM);
        assertName("RLM", RLM);
        assertName("RLO", RLO);
    }

    @Test
    public void otherZeroWidthAndFormatCharactersAreNamed() {
        assertName("SHY", 0x00AD);
        assertName("CGJ", 0x034F);
        assertName("ALM", 0x061C);
        assertName("MVS", 0x180E);
        assertName("LSEP", 0x2028);
        assertName("PSEP", 0x2029);
        assertName("LRE", 0x202A);
        assertName("RLE", 0x202B);
        assertName("PDF", 0x202C);
        assertName("LRO", 0x202D);
        assertName("NNBSP", 0x202F);
        assertName("MMSP", 0x205F);
        assertName("WJ", 0x2060);
        assertName("FA", 0x2061);
        assertName("IT", 0x2062);
        assertName("IS", 0x2063);
        assertName("IP", 0x2064);
        assertName("LRI", 0x2066);
        assertName("RLI", 0x2067);
        assertName("FSI", 0x2068);
        assertName("PDI", 0x2069);
        assertName("ISS", 0x206A);
        assertName("ASS", 0x206B);
        assertName("IAFS", 0x206C);
        assertName("AAFS", 0x206D);
        assertName("NADS", 0x206E);
        assertName("NODS", 0x206F);
        assertName("IDSP", 0x3000);
        assertName("BOM", BOM);
        assertName("IAA", 0xFFF9);
        assertName("IAS", 0xFFFA);
        assertName("IAT", 0xFFFB);
    }

    @Test
    public void oddWidthSpacesAreNamed() {
        String[] names = {"NQSP", "MQSP", "ENSP", "EMSP", "3/MSP", "4/MSP", "6/MSP", "FSP", "PSP", "THSP",
                "HSP"};
        for (int i = 0; i < names.length; ++i) {
            assertName(names[i], 0x2000 + i);
        }
    }

    @Test
    public void hangulFillersAndTheBlankBrailleCellAreCaught() {
        assertName("HF", 0x115F);
        assertName("HF", 0x1160);
        assertName("HF", 0x3164);
        assertName("HF", 0xFFA0);
        assertName("U+2800", 0x2800);
    }

    @Test
    public void controlCharactersOtherThanTabAndLineBreaksAreNamed() {
        assertName("NUL", 0x00);
        assertName("BEL", 0x07);
        assertName("BS", 0x08);
        assertName("VT", 0x0B);
        assertName("FF", 0x0C);
        assertName("ESC", 0x1B);
        assertName("US", 0x1F);
        assertName("DEL", 0x7F);
        assertName("U+0085", 0x85);
        assertName("U+009B", 0x9B);
        assertFalse(InvisibleChars.isInvisible('\t'));
        assertFalse(InvisibleChars.isInvisible('\n'));
        assertFalse(InvisibleChars.isInvisible('\r'));
    }

    @Test
    public void tagCharactersAndOtherSupplementaryOnesAreReadAsWholeCodePoints() {
        assertName("TAG", 0xE0001);
        assertName("TAG a", TAG_A);
        assertName("TAG SP", 0xE0020);
        assertName("TAG END", TAG_CANCEL);
        assertName("U+1D173", 0x1D173);
        assertName("U+1D17A", 0x1D17A);
        // Not listed by name, still a format character that draws nothing
        assertName("U+1BCA0", 0x1BCA0);
    }

    @Test
    public void visibleTextIsLeftAlone() {
        for (int cp : new int[]{'a', 'Z', '0', ' ', '-', '/', 0x00E9, 0x4E2D, 0x0628, FACE, 0xFE0F, 0x0600, 0x06DD,
                0x110BD}) {
            assertFalse(Integer.toHexString(cp), InvisibleChars.isInvisible(cp));
            assertNull(Integer.toHexString(cp), InvisibleChars.getName(cp));
        }
        assertFalse(InvisibleChars.containsInvisible("plain name.txt"));
        assertFalse(InvisibleChars.containsInvisible(null));
        assertEquals("plain name.txt", InvisibleChars.toMarkedText("plain name.txt"));
    }

    @Test
    public void markedTextShowsEveryHiddenCharacterInPlace() {
        assertEquals("a[ZWSP]b", InvisibleChars.toMarkedText(text('a', ZWSP, 'b')));
        assertEquals("[ZWNJ][ZWJ]x[NBSP]", InvisibleChars.toMarkedText(text(ZWNJ, ZWJ, 'x', NBSP)));
        // The classic extension spoof: the name reads "photogpj.exe" backwards past the override
        assertEquals("photo[RLO]gpj.exe", InvisibleChars.toMarkedText("photo" + text(RLO) + "gpj.exe"));
        assertTrue(InvisibleChars.containsInvisible("photo" + text(RLO) + "gpj.exe"));
    }

    @Test
    public void surrogatePairsAreNeverSplit() {
        String face = text(FACE);
        assertEquals(face + "[TAG a][TAG END]" + face, InvisibleChars.toMarkedText(face + text(TAG_A, TAG_CANCEL) + face));

        List<InvisibleChars.Occurrence> found = InvisibleChars.find(face + text(TAG_A), false);
        assertEquals(1, found.size());
        assertEquals(2, found.get(0).start);
        assertEquals(4, found.get(0).end);
        assertEquals(TAG_A, found.get(0).codePoint);
        assertEquals("TAG a", found.get(0).name);
    }

    @Test
    public void aLoneSurrogateIsPassedThroughUntouched() {
        String broken = "a" + (char) 0xD83D + "b" + (char) 0xDE00;
        assertEquals(broken, InvisibleChars.toMarkedText(broken));
        assertFalse(InvisibleChars.containsInvisible(broken));
    }

    @Test
    public void aBlankNameShowsItsSpacesSoTwoBlankNamesDiffer() {
        assertTrue(InvisibleChars.isBlank("   "));
        assertTrue(InvisibleChars.isBlank(text(NBSP)));
        assertTrue(InvisibleChars.isBlank(text(ZWSP, ' ')));
        assertFalse(InvisibleChars.isBlank(""));
        assertFalse(InvisibleChars.isBlank(null));
        assertFalse(InvisibleChars.isBlank(" a "));

        assertEquals("[SP][SP][SP]", InvisibleChars.toMarkedText("   "));
        assertEquals("[SP]", InvisibleChars.toMarkedText(" "));
        assertEquals("[TAB][LF][CR]", InvisibleChars.toMarkedText("\t\n\r"));
        assertEquals("[ZWSP][SP]", InvisibleChars.toMarkedText(text(ZWSP, ' ')));
        // Ordinary spaces between words stay spaces
        assertEquals("my[ZWSP] file", InvisibleChars.toMarkedText("my" + text(ZWSP) + " file"));
    }

    @Test
    public void findReportsPositionsInTheOriginalText() {
        List<InvisibleChars.Occurrence> found = InvisibleChars.find("ab" + text(ZWSP) + "c" + text(NBSP), false);
        assertEquals(2, found.size());
        assertEquals(2, found.get(0).start);
        assertEquals(3, found.get(0).end);
        assertEquals("ZWSP", found.get(0).name);
        assertEquals(4, found.get(1).start);
        assertEquals("NBSP", found.get(1).name);

        assertEquals(0, InvisibleChars.find("a b", false).size());
        assertEquals(1, InvisibleChars.find("a b", true).size());
        assertEquals(0, InvisibleChars.find(null, true).size());
    }

    @Test
    public void annotateReplacesEachHiddenCharacterWithAColoredBadge() {
        CharSequence annotated = InvisibleChars.annotate("a" + text(ZWSP) + "b", Color.BLACK, Color.YELLOW);

        assertEquals("a[ZWSP]b", annotated.toString());
        Spanned spanned = (Spanned) annotated;
        BackgroundColorSpan[] backgrounds = spanned.getSpans(0, spanned.length(), BackgroundColorSpan.class);
        assertEquals(1, backgrounds.length);
        assertEquals(1, spanned.getSpanStart(backgrounds[0]));
        assertEquals(7, spanned.getSpanEnd(backgrounds[0]));
        assertEquals(Color.YELLOW, backgrounds[0].getBackgroundColor());
        ForegroundColorSpan[] foregrounds = spanned.getSpans(0, spanned.length(), ForegroundColorSpan.class);
        assertEquals(1, foregrounds.length);
        assertEquals(Color.BLACK, foregrounds[0].getForegroundColor());
    }

    @Test
    public void annotateMarksTheSpacesOfABlankLabel() {
        assertEquals("[SP][SP]", InvisibleChars.annotate("  ", Color.BLACK, Color.YELLOW).toString());
    }

    @Test
    public void annotateHandsBackOrdinaryTextAsIs() {
        SpannableString label = new SpannableString("Settings");
        label.setSpan(new UnderlineSpan(), 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        assertSame(label, InvisibleChars.annotate(label, Color.BLACK, Color.YELLOW));
        assertEquals("", InvisibleChars.annotate(null, Color.BLACK, Color.YELLOW).toString());
        assertEquals("", InvisibleChars.annotate("", Color.BLACK, Color.YELLOW).toString());
    }

    @Test
    public void annotateKeepsTheLabelsOwnStyling() {
        SpannableString label = new SpannableString("ab" + text(ZWSP) + "cd");
        label.setSpan(new UnderlineSpan(), 3, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        Spanned annotated = (Spanned) InvisibleChars.annotate(label, Color.BLACK, Color.YELLOW);

        assertEquals("ab[ZWSP]cd", annotated.toString());
        UnderlineSpan[] underlines = annotated.getSpans(0, annotated.length(), UnderlineSpan.class);
        assertEquals(1, underlines.length);
        assertEquals("cd", annotated.subSequence(annotated.getSpanStart(underlines[0]),
                annotated.getSpanEnd(underlines[0])).toString());
    }

    private static void assertName(String expected, int codePoint) {
        String hex = Integer.toHexString(codePoint);
        assertTrue(hex, InvisibleChars.isInvisible(codePoint));
        assertEquals(hex, expected, InvisibleChars.getName(codePoint));
        assertEquals(hex, "x[" + expected + "]y", InvisibleChars.toMarkedText("x" + text(codePoint) + "y"));
    }

    private static String text(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }
}
