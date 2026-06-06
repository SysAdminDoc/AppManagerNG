// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;

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
    public void flattenToString_roundTripsEmptyStringExtra() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("empty", "");

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        assertEquals("", parsed.getStringExtra("empty"));
    }

    @Test
    public void flattenToString_roundTripsStringExtraContainingTabs() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("label", "alpha\tbeta\tgamma");

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        assertEquals("alpha\tbeta\tgamma", parsed.getStringExtra("label"));
    }

    @Test
    public void flattenToString_roundTripsCharSequenceExtraAsString() {
        Intent input = new Intent(Intent.ACTION_SEND);
        input.putExtra(Intent.EXTRA_TEXT, new SpannableString("Styled body"));

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        assertEquals("Styled body", parsed.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test
    public void flattenToCommand_exportsCharSequenceExtraAsString() {
        Intent input = new Intent(Intent.ACTION_SEND);
        input.putExtra(Intent.EXTRA_TEXT, new SpannableString("Styled body"));

        ArrayList<String> args = new ArrayList<>(IntentCompat.flattenToCommand(input));

        assertTrue(args.contains("--es"));
        assertTrue(args.contains(Intent.EXTRA_TEXT));
        assertTrue(args.contains("Styled body"));
    }

    @Test
    public void flattenToString_roundTripsCharSequenceArrayExtraAsStringArray() {
        Intent input = new Intent(Intent.ACTION_SEND_MULTIPLE);
        input.putExtra("labels", new CharSequence[]{
                new SpannableString("alpha,beta"),
                new SpannableString("gamma")
        });

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        assertEquals(Arrays.asList("alpha,beta", "gamma"),
                Arrays.asList(parsed.getStringArrayExtra("labels")));
    }

    @Test
    public void flattenToString_roundTripsCharSequenceListExtraAsStringList() {
        Intent input = new Intent(Intent.ACTION_SEND_MULTIPLE);
        ArrayList<CharSequence> labels = new ArrayList<>();
        labels.add(new SpannableString("alpha,beta"));
        labels.add(new SpannableString("gamma"));
        input.putCharSequenceArrayListExtra("labels", labels);

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        assertEquals(Arrays.asList("alpha,beta", "gamma"), parsed.getStringArrayListExtra("labels"));
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
    public void flattenToString_skipsEmptyListExtraInsteadOfNullExtra() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putIntegerArrayListExtra("numbers", new ArrayList<>());
        input.putExtra("nullable", (String) null);

        String flattened = IntentCompat.flattenToString(input);
        Intent parsed = IntentCompat.unflattenFromString(flattened);

        assertFalse(flattened.contains("EXTRA\tnumbers\t" + AddIntentExtraFragment.TYPE_NULL));
        assertNotNull(parsed);
        Bundle extras = parsed.getExtras();
        assertNotNull(extras);
        assertFalse(extras.containsKey("numbers"));
        assertTrue(extras.containsKey("nullable"));
        assertNull(extras.getString("nullable"));
    }

    @Test
    public void flattenToCommand_skipsEmptyListExtraInsteadOfNullExtra() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putStringArrayListExtra("labels", new ArrayList<>());
        input.putExtra("nullable", (String) null);

        ArrayList<String> args = new ArrayList<>(IntentCompat.flattenToCommand(input));

        assertFalse(args.contains("labels"));
        assertTrue(args.contains("--esn"));
        assertTrue(args.contains("nullable"));
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
