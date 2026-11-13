// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permissions;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.muntashirakon.AppManager.R;

public class ReferenceActionTest {
    @Test
    public void noReferencePins() {
        assertEquals(ReferenceAction.PIN, ReferenceAction.next(false, false, true));
        assertEquals(ReferenceAction.PIN, ReferenceAction.next(false, false, false));
    }

    @Test
    public void aPinnedReferenceUnpinsOnTheNextTap() {
        // The bug: this used to pin again and say "Reference pinned"
        assertEquals(ReferenceAction.UNPIN, ReferenceAction.next(true, false, true));
        assertEquals(ReferenceAction.UNPIN, ReferenceAction.next(true, false, false));
    }

    @Test
    public void aDriftedReferenceRestoresWhenItCan() {
        assertEquals(ReferenceAction.RESTORE, ReferenceAction.next(true, true, true));
    }

    @Test
    public void aDriftedReferenceThatCantBeRestoredUnpinsInsteadOfLockingTheButton() {
        assertEquals(ReferenceAction.UNPIN, ReferenceAction.next(true, true, false));
    }

    @Test
    public void eachActionReportsItsOwnOutcome() {
        assertEquals(R.string.permission_reference_pinned, ReferenceAction.doneMessage(ReferenceAction.PIN));
        assertEquals(R.string.permission_reference_unpinned, ReferenceAction.doneMessage(ReferenceAction.UNPIN));
        assertEquals(R.string.permission_reference_restored, ReferenceAction.doneMessage(ReferenceAction.RESTORE));
        assertEquals(R.string.permission_reference_unpin_failed,
                ReferenceAction.failedMessage(ReferenceAction.UNPIN));
        assertEquals(R.string.permission_reference_unpin, ReferenceAction.label(ReferenceAction.UNPIN));
    }

    @Test
    public void thePinnedFlagIsFilled() {
        assertEquals(R.drawable.ic_flag_filled, ReferenceAction.icon(ReferenceAction.UNPIN, true));
        assertEquals(R.drawable.ic_flag, ReferenceAction.icon(ReferenceAction.PIN, false));
        assertEquals(R.drawable.ic_restore, ReferenceAction.icon(ReferenceAction.RESTORE, true));
    }
}
