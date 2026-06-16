// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.onboarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Ops;

public class OnboardingFragmentTest {
    @Test
    public void isSecurityPatchBeforeComparesIsoPatchDates() {
        assertTrue(OnboardingFragment.isSecurityPatchBefore("2026-04-01", "2026-05-01"));
        assertFalse(OnboardingFragment.isSecurityPatchBefore("2026-05-01", "2026-05-01"));
        assertFalse(OnboardingFragment.isSecurityPatchBefore("2026-06-01", "2026-05-01"));
    }

    @Test
    public void isSecurityPatchBeforeIgnoresUnknownPatchDates() {
        assertFalse(OnboardingFragment.isSecurityPatchBefore("", "2026-05-01"));
        assertFalse(OnboardingFragment.isSecurityPatchBefore(null, "2026-05-01"));
        assertFalse(OnboardingFragment.isSecurityPatchBefore("2026-5-1", "2026-05-01"));
        assertFalse(OnboardingFragment.isSecurityPatchBefore("2026-04-01", "unknown"));
    }

    @Test
    public void capabilityProbeRunsOnlyForMissingSnapshotOrForcedRefresh() {
        Object cachedSnapshot = new Object();

        assertTrue(OnboardingFragment.shouldRunCapabilityProbe(false, null));
        assertTrue(OnboardingFragment.shouldRunCapabilityProbe(true, cachedSnapshot));
        assertFalse(OnboardingFragment.shouldRunCapabilityProbe(false, cachedSnapshot));
    }

    @Test
    public void notificationPermissionPromptIsAndroid13OnlyAndOneShot() {
        assertTrue(OnboardingFragment.shouldPromptForNotificationPermission(33, false, false));
        assertFalse(OnboardingFragment.shouldPromptForNotificationPermission(32, false, false));
        assertFalse(OnboardingFragment.shouldPromptForNotificationPermission(33, true, false));
        assertFalse(OnboardingFragment.shouldPromptForNotificationPermission(33, false, true));
    }

    @Test
    public void confidenceModeStatusReflectsCurrentModeReadiness() {
        assertEquals(R.string.onboarding_confidence_mode_root_ready,
                OnboardingFragment.getConfidenceModeStatusRes(Ops.MODE_ROOT, true, false, false));
        assertEquals(R.string.onboarding_confidence_mode_root_pending,
                OnboardingFragment.getConfidenceModeStatusRes(Ops.MODE_ROOT, false, false, false));
        assertEquals(R.string.onboarding_confidence_mode_shizuku_ready,
                OnboardingFragment.getConfidenceModeStatusRes(Ops.MODE_SHIZUKU, false, true, false));
        assertEquals(R.string.onboarding_confidence_mode_adb_pending,
                OnboardingFragment.getConfidenceModeStatusRes(Ops.MODE_ADB_WIFI, false, false, false));
        assertEquals(R.string.onboarding_confidence_mode_no_root,
                OnboardingFragment.getConfidenceModeStatusRes(Ops.MODE_NO_ROOT, false, false, false));
        assertEquals(R.string.onboarding_confidence_mode_auto_ready,
                OnboardingFragment.getConfidenceModeStatusRes(Ops.MODE_AUTO, false, false, true));
        assertEquals(R.string.onboarding_confidence_mode_auto_pending,
                OnboardingFragment.getConfidenceModeStatusRes(Ops.MODE_AUTO, false, false, false));
    }
}
