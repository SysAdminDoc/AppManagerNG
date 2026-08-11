// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Map;


/**
 * The Finder editor builds the checkbox list for a {@code TYPE_INT_FLAGS} key by calling
 * {@link FilterOption#getFlags(String)}, whose base implementation throws. A key declared as
 * flag-typed without a matching flag map therefore fails as soon as the user selects it, rather
 * than merely rendering an empty list — which is what these tests exist to prevent.
 */
@RunWith(RobolectricTestRunner.class)
public class AppOpsOptionTest {
    @Test
    public void everyFlagTypedKeyExposesItsFlags() {
        AppOpsOption option = new AppOpsOption();

        for (Map.Entry<String, Integer> entry : option.getKeysWithType().entrySet()) {
            if (entry.getValue() != FilterOption.TYPE_INT_FLAGS) {
                continue;
            }
            Map<Integer, CharSequence> flags = option.getFlags(entry.getKey());
            assertFalse("key '" + entry.getKey() + "' is flag-typed but exposes no flags, so"
                    + " selecting it in the editor throws", flags.isEmpty());
        }
    }

    @Test
    public void modeFlagsCoverEveryModeTheFilterCanMatch() {
        Map<Integer, CharSequence> flags = new AppOpsOption().getFlags("with_mode");

        assertTrue(flags.containsKey(AppOpsOption.MODE_FLAG_ALLOWED));
        assertTrue(flags.containsKey(AppOpsOption.MODE_FLAG_IGNORED));
        assertTrue(flags.containsKey(AppOpsOption.MODE_FLAG_ERRORED));
        assertTrue(flags.containsKey(AppOpsOption.MODE_FLAG_DEFAULT));
        assertTrue(flags.containsKey(AppOpsOption.MODE_FLAG_FOREGROUND));
        assertEquals(5, flags.size());
    }

    @Test
    public void aFlagBitIsOneShiftedByTheModeItRepresents() {
        // The predicate tests (1 << mode) against the selected bits, so the constants have to line
        // up with the platform's mode values or the filter silently matches the wrong ops.
        assertEquals(1 << AppOpsManager.MODE_ALLOWED, AppOpsOption.MODE_FLAG_ALLOWED);
        assertEquals(1 << AppOpsManager.MODE_IGNORED, AppOpsOption.MODE_FLAG_IGNORED);
        assertEquals(1 << AppOpsManager.MODE_ERRORED, AppOpsOption.MODE_FLAG_ERRORED);
        assertEquals(1 << AppOpsManager.MODE_DEFAULT, AppOpsOption.MODE_FLAG_DEFAULT);
        assertEquals(1 << AppOpsManager.MODE_FOREGROUND, AppOpsOption.MODE_FLAG_FOREGROUND);
    }

    @Test
    public void aModeInTheSelectedSetMatches() {
        assertTrue(AppOpsOption.matchesMode(AppOpsManager.MODE_IGNORED, AppOpsOption.MODE_FLAG_IGNORED));
    }

    @Test
    public void aModeOutsideTheSelectedSetDoesNotMatch() {
        assertFalse(AppOpsOption.matchesMode(AppOpsManager.MODE_ALLOWED, AppOpsOption.MODE_FLAG_IGNORED));
    }

    @Test
    public void anyOfSeveralSelectedModesMatches() {
        int selected = AppOpsOption.MODE_FLAG_IGNORED | AppOpsOption.MODE_FLAG_FOREGROUND;
        assertTrue(AppOpsOption.matchesMode(AppOpsManager.MODE_FOREGROUND, selected));
        assertTrue(AppOpsOption.matchesMode(AppOpsManager.MODE_IGNORED, selected));
        assertFalse(AppOpsOption.matchesMode(AppOpsManager.MODE_ALLOWED, selected));
    }

    @Test
    public void anUnreportedModeNeverMatches() {
        // A negative mode means the platform reported nothing; matching it would make the filter
        // claim knowledge it does not have.
        assertFalse(AppOpsOption.matchesMode(-1, AppOpsOption.MODE_FLAG_ALLOWED));
        assertFalse(AppOpsOption.matchesMode(-1, ~0));
    }

    @Test
    public void selectingNoModeMatchesNothing() {
        assertFalse(AppOpsOption.matchesMode(AppOpsManager.MODE_ALLOWED, 0));
        assertFalse(AppOpsOption.matchesMode(AppOpsManager.MODE_IGNORED, 0));
    }
}
