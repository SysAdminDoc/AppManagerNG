// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.muntashirakon.AppManager.db.entity.App;

public class AttentionBadgeSourceTest {

    private static App app(int dangerousTotal, int dangerousGranted, int rulesCount) {
        App row = new App();
        row.packageName = "com.example";
        row.userId = 0;
        row.dangerousPermTotal = dangerousTotal;
        row.dangerousPermGranted = dangerousGranted;
        row.rulesCount = rulesCount;
        return row;
    }

    @Test
    public void deriveUngrantedDangerousPermissions() {
        AttentionBadgeCalculator.Signals s = AttentionBadgeSource.forApp(app(8, 5, 0));
        assertEquals(3, s.dangerousPermissionsRequestedNotGranted);
        assertEquals(0, s.userDisabledComponentCount);
        assertEquals(0, s.recentOsRevertCount);
    }

    @Test
    public void deriveRulesCountAsDisabledComponentProxy() {
        AttentionBadgeCalculator.Signals s = AttentionBadgeSource.forApp(app(2, 2, 4));
        // All dangerous perms granted -> zero ungranted.
        assertEquals(0, s.dangerousPermissionsRequestedNotGranted);
        assertEquals(4, s.userDisabledComponentCount);
    }

    @Test
    public void negativeOrInvertedFieldsClampToZero() {
        // Defensive: a stale row could have granted > total during a refresh.
        AttentionBadgeCalculator.Signals s = AttentionBadgeSource.forApp(app(2, 5, -3));
        assertEquals(0, s.dangerousPermissionsRequestedNotGranted);
        assertEquals(0, s.userDisabledComponentCount);
    }

    @Test
    public void explicitOsRevertCountFlowsThrough() {
        AttentionBadgeCalculator.Signals s = AttentionBadgeSource.forApp(app(0, 0, 0), 2);
        assertEquals(2, s.recentOsRevertCount);
    }

    @Test
    public void negativeOsRevertCountClampsToZero() {
        AttentionBadgeCalculator.Signals s = AttentionBadgeSource.forApp(app(0, 0, 0), -7);
        assertEquals(0, s.recentOsRevertCount);
    }

    @Test
    public void nullAppRowProducesZeroSignals() {
        AttentionBadgeCalculator.Signals s = AttentionBadgeSource.forApp(null);
        assertEquals(0, s.dangerousPermissionsRequestedNotGranted);
        assertEquals(0, s.userDisabledComponentCount);
        assertEquals(0, s.recentOsRevertCount);
    }

    @Test
    public void nullAppRowStillCarriesOsRevertCount() {
        AttentionBadgeCalculator.Signals s = AttentionBadgeSource.forApp(null, 5);
        assertEquals(5, s.recentOsRevertCount);
    }

    @Test
    public void badgeForResolvesViaCalculatorPriority() {
        // OS revert wins over everything else even when other signals are
        // higher in count - validates the adapter wires through compute().
        AttentionBadgeCalculator.Badge badge = AttentionBadgeSource.badgeFor(app(10, 0, 99), 1);
        assertEquals(AttentionBadgeCalculator.Kind.OS_REVERT, badge.kind);
        assertEquals(AttentionBadgeCalculator.Severity.WARN, badge.severity);
        assertEquals(1, badge.count);
    }

    @Test
    public void badgeForPicksDangerousPermissionWhenNoOsRevert() {
        AttentionBadgeCalculator.Badge badge = AttentionBadgeSource.badgeFor(app(6, 4, 2), 0);
        assertEquals(AttentionBadgeCalculator.Kind.DANGEROUS_PERMISSION, badge.kind);
        assertEquals(2, badge.count);
        assertEquals(AttentionBadgeCalculator.Severity.INFO, badge.severity);
    }

    @Test
    public void badgeForFallsBackToDisabledComponentBucket() {
        AttentionBadgeCalculator.Badge badge = AttentionBadgeSource.badgeFor(app(0, 0, 3), 0);
        assertEquals(AttentionBadgeCalculator.Kind.DISABLED_COMPONENT, badge.kind);
        assertEquals(3, badge.count);
    }

    @Test
    public void badgeForReturnsNoneOnEmptyRow() {
        AttentionBadgeCalculator.Badge badge = AttentionBadgeSource.badgeFor(app(0, 0, 0), 0);
        assertEquals(AttentionBadgeCalculator.Kind.NONE, badge.kind);
        assertEquals(0, badge.count);
    }
}
