// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;

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

    @Test
    public void flattenToString_roundTripsStringArrayValuesContainingCommas() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("labels", new String[]{"alpha,beta", "gamma"});

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        assertEquals(Arrays.asList("alpha,beta", "gamma"),
                Arrays.asList(parsed.getStringArrayExtra("labels")));
    }

    @Test
    public void flattenToString_roundTripsUriListValuesContainingCommas() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(Uri.parse("content://example/items/alpha,beta"));
        uris.add(Uri.parse("content://example/items/gamma"));
        input.putParcelableArrayListExtra("uris", uris);

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        ArrayList<Uri> parsedUris = IntentCompat.getParcelableArrayListExtra(parsed, "uris", Uri.class);
        assertEquals(uris, parsedUris);
    }

    @Test
    public void valueToParsableString_formatsArrayValuesForParser() {
        String rawValue = IntentCompat.valueToParsableString(AddIntentExtraFragment.TYPE_INT_ARR,
                new int[]{1, 2, 3});

        assertEquals("1,2,3", rawValue);
        assertTrue(Arrays.equals(new int[]{1, 2, 3},
                (int[]) IntentCompat.parseExtraValue(AddIntentExtraFragment.TYPE_INT_ARR, rawValue)));
    }

    @Test
    public void valueToParsableString_formatsEscapedListValuesForParser() {
        ArrayList<String> labels = new ArrayList<>();
        labels.add("alpha,beta");
        labels.add("gamma");

        String rawValue = IntentCompat.valueToParsableString(AddIntentExtraFragment.TYPE_STRING_AL, labels);

        assertEquals("alpha\\,beta,gamma", rawValue);
        assertEquals(labels, IntentCompat.parseExtraValue(AddIntentExtraFragment.TYPE_STRING_AL, rawValue));
    }
}
