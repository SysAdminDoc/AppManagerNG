// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class IntentCompatTest {
    @Test
    public void unflattenFromString_roundTripsNullExtra() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("nullable", (String) null);

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        Bundle extras = parsed.getExtras();
        assertNotNull(extras);
        assertTrue(extras.containsKey("nullable"));
        assertNull(extras.getString("nullable"));
    }

    @Test
    public void unflattenFromString_roundTripsTypedExtraAfterNullExtra() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("nullable", (String) null);
        input.putExtra("answer", 42);

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        Bundle extras = parsed.getExtras();
        assertNotNull(extras);
        assertTrue(extras.containsKey("nullable"));
        assertNull(extras.getString("nullable"));
        assertEquals(42, extras.getInt("answer"));
    }

    @Test
    public void unflattenFromString_rejectsNonNullExtraWithoutValue() {
        Intent parsed = IntentCompat.unflattenFromString("VERSION\t1\nEXTRA\tanswer\t5\n");

        assertNull(parsed);
    }
}
