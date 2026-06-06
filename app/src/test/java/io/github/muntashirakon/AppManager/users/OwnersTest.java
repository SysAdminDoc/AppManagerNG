// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.UserHandleHidden;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import io.github.muntashirakon.AppManager.compat.ProcessCompat;

@RunWith(RobolectricTestRunner.class)
public class OwnersTest {
    @Test
    public void parseUid_parsesFormattedApplicationUid() {
        assertEquals(UserHandleHidden.getUid(10, ProcessCompat.FIRST_APPLICATION_UID + 42),
                Owners.parseUid("u10_a42"));
        assertEquals(UserHandleHidden.getUid(10, ProcessCompat.FIRST_APPLICATION_UID + 42),
                Owners.parseUid("u10a42"));
    }

    @Test
    public void parseUid_parsesFormattedIsolatedUids() {
        assertEquals(UserHandleHidden.getUid(10, ProcessCompat.FIRST_ISOLATED_UID + 7),
                Owners.parseUid("u10_i7"));
        assertEquals(UserHandleHidden.getUid(10, ProcessCompat.FIRST_APP_ZYGOTE_ISOLATED_UID + 3),
                Owners.parseUid("u10_ai3"));
    }

    @Test
    public void parseUid_parsesFormattedSystemUid() {
        assertEquals(UserHandleHidden.getUid(10, 2000), Owners.parseUid("u10_s2000"));
    }

    @Test
    public void parseUid_rejectsMalformedFormattedUid() {
        String[] uidStrings = {"", "u0", "u0_", "u0_a", "u0_ai", "u0_b12", "u0_a12x"};
        for (String uidString : uidStrings) {
            assertThrows(IllegalArgumentException.class, () -> Owners.parseUid(uidString));
        }
    }
}
