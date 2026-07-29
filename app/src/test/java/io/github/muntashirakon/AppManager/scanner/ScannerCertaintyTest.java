// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The scanner runs one detector and it is easy to over-read. These tests pin what a result is
 * allowed to claim: a confirmed signature that matched code, a tentative one that did not, and
 * never a clean bill of health.
 */
public class ScannerCertaintyTest {
    @Test
    public void aConfirmedSignatureWithMatchedClassesIsConfirmed() {
        SignatureInfo info = new SignatureInfo("com.example.analytics.", "Example Analytics");
        info.setCount(4);
        assertEquals(ScannerCertainty.Confidence.CONFIRMED, ScannerCertainty.confidenceOf(info));
        assertFalse(ScannerCertainty.isTentative(info.label));
    }

    @Test
    public void aSecondDegreeSignatureIsNeverConfirmed() {
        SignatureInfo info = new SignatureInfo("com.example.maybe.", "²Example Maybe");
        info.setCount(9);
        assertTrue(ScannerCertainty.isTentative(info.label));
        assertEquals(ScannerCertainty.Confidence.TENTATIVE, ScannerCertainty.confidenceOf(info));
    }

    @Test
    public void aSignatureThatMatchedNothingSupportsNothing() {
        SignatureInfo info = new SignatureInfo("com.example.analytics.", "Example Analytics");
        info.setCount(0);
        assertEquals(ScannerCertainty.Confidence.TENTATIVE, ScannerCertainty.confidenceOf(info));
    }

    @Test
    public void aNegativeCountIsNotTreatedAsEvidence() {
        assertEquals(ScannerCertainty.Confidence.TENTATIVE,
                ScannerCertainty.confidenceOf("Example Analytics", -1));
    }

    @Test
    public void theSecondDegreeMarkerIsStrippedForDisplayButNotForgotten() {
        assertEquals("Example Maybe", ScannerCertainty.displayLabel("²Example Maybe"));
        assertEquals("Example Maybe", ScannerCertainty.displayLabel("  ²  Example Maybe  "));
        assertEquals("Example Analytics", ScannerCertainty.displayLabel("Example Analytics"));
        // Stripping must not lose the fact that it was tentative.
        assertTrue(ScannerCertainty.isTentative("²Example Maybe"));
    }

    @Test
    public void aLabelThatMerelyContainsTheMarkerIsNotTentative() {
        assertFalse(ScannerCertainty.isTentative("Example² Analytics"));
    }

    @Test
    public void everyConfidenceLevelCarriesAnIdAndALabel() {
        for (ScannerCertainty.Confidence confidence : ScannerCertainty.Confidence.values()) {
            assertFalse(confidence.id.isEmpty());
            assertTrue(confidence.labelRes != 0);
        }
    }
}
