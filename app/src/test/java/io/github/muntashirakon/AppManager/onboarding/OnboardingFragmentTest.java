// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.onboarding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
