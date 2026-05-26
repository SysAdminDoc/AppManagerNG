// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AttentionBadgeCalculatorTest {

    @Test
    public void zeroSignalsProduceNoBadge() {
        AttentionBadgeCalculator.Badge b = AttentionBadgeCalculator.compute(
                new AttentionBadgeCalculator.Signals(0, 0, 0));
        assertTrue(b.isNone());
        assertEquals(AttentionBadgeCalculator.Kind.NONE, b.kind);
        assertEquals(0, b.count);
    }

    @Test
    public void osRevertWinsAgainstEverythingElse() {
        AttentionBadgeCalculator.Badge b = AttentionBadgeCalculator.compute(
                new AttentionBadgeCalculator.Signals(5, 10, 2));
        assertEquals(AttentionBadgeCalculator.Kind.OS_REVERT, b.kind);
        assertEquals(2, b.count);
        assertEquals(AttentionBadgeCalculator.Severity.WARN, b.severity);
    }

    @Test
    public void dangerousPermissionWinsOverDisabledComponents() {
        AttentionBadgeCalculator.Badge b = AttentionBadgeCalculator.compute(
                new AttentionBadgeCalculator.Signals(3, 17, 0));
        assertEquals(AttentionBadgeCalculator.Kind.DANGEROUS_PERMISSION, b.kind);
        assertEquals(3, b.count);
        assertEquals(AttentionBadgeCalculator.Severity.INFO, b.severity);
    }

    @Test
    public void disabledComponentsSurfaceWhenAlone() {
        AttentionBadgeCalculator.Badge b = AttentionBadgeCalculator.compute(
                new AttentionBadgeCalculator.Signals(0, 4, 0));
        assertEquals(AttentionBadgeCalculator.Kind.DISABLED_COMPONENT, b.kind);
        assertEquals(4, b.count);
        assertEquals(AttentionBadgeCalculator.Severity.INFO, b.severity);
    }

    @Test
    public void signalsClampNegativeInputs() {
        AttentionBadgeCalculator.Signals s = new AttentionBadgeCalculator.Signals(-1, -10, -2);
        assertEquals(0, s.dangerousPermissionsRequestedNotGranted);
        assertEquals(0, s.userDisabledComponentCount);
        assertEquals(0, s.recentOsRevertCount);
        // And the badge they produce is NONE, not a negative-count crash.
        AttentionBadgeCalculator.Badge b = AttentionBadgeCalculator.compute(s);
        assertTrue(b.isNone());
    }

    @Test
    public void formatZeroOrNegativeIsEmpty() {
        assertEquals("", AttentionBadgeCalculator.formatCount(0));
        assertEquals("", AttentionBadgeCalculator.formatCount(-3));
    }

    @Test
    public void formatBelowOrAtOneHundredIsLiteral() {
        assertEquals("1", AttentionBadgeCalculator.formatCount(1));
        assertEquals("42", AttentionBadgeCalculator.formatCount(42));
        assertEquals("99", AttentionBadgeCalculator.formatCount(99));
    }

    @Test
    public void formatAboveOneHundredCollapses() {
        assertEquals("99+", AttentionBadgeCalculator.formatCount(100));
        assertEquals("99+", AttentionBadgeCalculator.formatCount(9999));
    }
}
