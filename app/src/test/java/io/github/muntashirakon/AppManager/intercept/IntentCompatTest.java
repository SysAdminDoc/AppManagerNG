// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.SpannableString;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    public void unflattenFromString_rejectsMalformedNumericFields() {
        assertNull(IntentCompat.unflattenFromString("VERSION\tnot-a-version\n"));
        assertNull(IntentCompat.unflattenFromString("VERSION\t1\nFLAGS\tnot-flags\n"));
        assertNull(IntentCompat.unflattenFromString("VERSION\t1\nEXTRA\tanswer\tnot-a-type\t42\n"));
        assertNull(IntentCompat.unflattenFromString("VERSION\t1\nEXTRA\tanswer\t"
                + AddIntentExtraFragment.TYPE_INTEGER + "\tnot-an-int\n"));
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
    public void flattenToString_roundTripsStringArrayValuesContainingBackslashes() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        // A value ending in a backslash used to make the following real delimiter look
        // escaped, collapsing two elements into one on parse. Also cover an escaped comma
        // adjacent to a backslash and a literal backslash-comma sequence.
        input.putExtra("labels", new String[]{"a\\", "b", "c\\,d", "e\\\\"});

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        assertEquals(Arrays.asList("a\\", "b", "c\\,d", "e\\\\"),
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
    public void flattenToString_roundTripsEmptyPrimitiveArrayExtras() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("ints", new int[0]);
        input.putExtra("longs", new long[0]);
        input.putExtra("floats", new float[0]);

        Intent parsed = IntentCompat.unflattenFromString(IntentCompat.flattenToString(input));

        assertNotNull(parsed);
        assertArrayEquals(new int[0], parsed.getIntArrayExtra("ints"));
        assertArrayEquals(new long[0], parsed.getLongArrayExtra("longs"));
        assertArrayEquals(new float[0], parsed.getFloatArrayExtra("floats"), 0);
    }

    @Test
    public void unflattenFromString_roundTripsDecodedLongExtra() {
        Intent parsed = IntentCompat.unflattenFromString(
                "VERSION\t1\nEXTRA\tanswer\t" + AddIntentExtraFragment.TYPE_LONG + "\t0x2a\n");

        assertNotNull(parsed);
        assertEquals(42L, parsed.getLongExtra("answer", 0));
    }

    @Test
    public void parseExtraValue_decodesLongLiterals() {
        assertEquals(42L, IntentCompat.parseExtraValue(AddIntentExtraFragment.TYPE_LONG, "0x2a"));
        assertEquals(42L, IntentCompat.parseExtraValue(AddIntentExtraFragment.TYPE_LONG, " 42 "));
        assertThrows(NumberFormatException.class,
                () -> IntentCompat.parseExtraValue(AddIntentExtraFragment.TYPE_LONG, "not-a-long"));
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
    public void getUnsupportedExtras_reportsParcelableBinderAndNestedBundle() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("parcelable", new UnsupportedParcelable("value"));
        Bundle nestedBundle = new Bundle();
        nestedBundle.putString("inner", "value");
        input.putExtra("nested", nestedBundle);
        Bundle binderBundle = new Bundle();
        binderBundle.putBinder("binder", new Binder());
        input.putExtras(binderBundle);

        List<IntentCompat.UnsupportedExtra> unsupportedExtras = IntentCompat.getUnsupportedExtras(input);

        assertEquals(3, unsupportedExtras.size());
        assertUnsupportedExtra(unsupportedExtras.get(0), "binder", Binder.class.getName());
        assertUnsupportedExtra(unsupportedExtras.get(1), "nested", Bundle.class.getName());
        assertUnsupportedExtra(unsupportedExtras.get(2), "parcelable", UnsupportedParcelable.class.getName());
    }

    @Test
    public void describeUnsupportedExtras_listsCountAndKeyTypesWithPrefix() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("parcelable", new UnsupportedParcelable("value"));
        Bundle binderBundle = new Bundle();
        binderBundle.putBinder("binder", new Binder());
        input.putExtras(binderBundle);

        String description = IntentCompat.describeUnsupportedExtras(input, "#");

        assertTrue(description.contains("# UNSUPPORTED EXTRAS\t2\n"));
        assertTrue(description.contains("# UNSUPPORTED EXTRA\tbinder\t" + Binder.class.getName() + "\n"));
        assertTrue(description.contains("# UNSUPPORTED EXTRA\tparcelable\t"
                + UnsupportedParcelable.class.getName() + "\n"));
    }

    @Test
    public void describeIntent_reportsUnsupportedExtras() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("label", "supported");
        input.putExtra("parcelable", new UnsupportedParcelable("value"));

        String description = IntentCompat.describeIntent(input, "RESULT");

        assertTrue(description.contains("RESULT EXTRA\tlabel\t" + AddIntentExtraFragment.TYPE_STRING
                + "\tsupported\n"));
        assertTrue(description.contains("RESULT UNSUPPORTED EXTRAS\t1\n"));
        assertTrue(description.contains("RESULT UNSUPPORTED EXTRA\tparcelable\t"
                + UnsupportedParcelable.class.getName() + "\n"));
    }

    @Test
    public void unsupportedExtraWarningLinesDoNotBreakUnflattening() {
        Intent input = new Intent(Intent.ACTION_VIEW);
        input.putExtra("label", "supported");
        input.putExtra("parcelable", new UnsupportedParcelable("value"));

        String flattened = IntentCompat.flattenToString(input);
        Intent parsed = IntentCompat.unflattenFromString(flattened
                + IntentCompat.describeUnsupportedExtras(input, ""));

        assertFalse(flattened.contains("UNSUPPORTED EXTRA"));
        assertNotNull(parsed);
        assertEquals("supported", parsed.getStringExtra("label"));
        assertFalse(parsed.hasExtra("parcelable"));
    }

    @Test
    public void toUriSafely_preservesBaseFieldsAndReadableExtrasAfterParcelableFailure() throws Exception {
        Bundle extras = new Bundle();
        extras.putString("safe", "value");
        extras.putParcelable("parcelable", new UnsupportedParcelable("unreadable"));
        Intent input = new ThrowingToUriIntent(extras);
        input.setAction(Intent.ACTION_VIEW);
        input.setData(Uri.parse("content://example/items/42"));
        input.setComponent(new ComponentName("com.example", "com.example.TargetActivity"));
        input.addCategory(Intent.CATEGORY_BROWSABLE);

        IntentCompat.IntentUriSerializationResult result = IntentCompat.toUriSafely(
                input, Intent.URI_INTENT_SCHEME);
        Intent parsed = Intent.parseUri(result.getUri(), Intent.URI_INTENT_SCHEME);

        assertEquals(Intent.ACTION_VIEW, parsed.getAction());
        assertEquals(Uri.parse("content://example/items/42"), parsed.getData());
        assertEquals(new ComponentName("com.example", "com.example.TargetActivity"), parsed.getComponent());
        assertTrue(parsed.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertEquals("value", parsed.getStringExtra("safe"));
        assertEquals(1, result.getSkippedExtras().size());
        assertUnsupportedExtra(result.getSkippedExtras().get(0),
                "parcelable", UnsupportedParcelable.class.getName());
    }

    @Test
    public void toUriSafely_reportsBundleWhenExtraNamesCannotBeRead() throws Exception {
        Intent input = new ThrowingExtrasIntent();
        input.setAction(Intent.ACTION_SEND);

        IntentCompat.IntentUriSerializationResult result = IntentCompat.toUriSafely(
                input, Intent.URI_INTENT_SCHEME);
        Intent parsed = Intent.parseUri(result.getUri(), Intent.URI_INTENT_SCHEME);

        assertEquals(Intent.ACTION_SEND, parsed.getAction());
        assertEquals(1, result.getSkippedExtras().size());
        assertUnsupportedExtra(result.getSkippedExtras().get(0),
                "<unreadable extras>", BadParcelableException.class.getName());
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

    private static void assertUnsupportedExtra(IntentCompat.UnsupportedExtra unsupportedExtra,
                                               String expectedKey, String expectedTypeName) {
        assertEquals(expectedKey, unsupportedExtra.getKey());
        assertEquals(expectedTypeName, unsupportedExtra.getTypeName());
    }

    private static final class UnsupportedParcelable implements Parcelable {
        private final String value;

        private UnsupportedParcelable(String value) {
            this.value = value;
        }

        private UnsupportedParcelable(Parcel in) {
            value = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(value);
        }

        public static final Creator<UnsupportedParcelable> CREATOR = new Creator<UnsupportedParcelable>() {
            @Override
            public UnsupportedParcelable createFromParcel(Parcel in) {
                return new UnsupportedParcelable(in);
            }

            @Override
            public UnsupportedParcelable[] newArray(int size) {
                return new UnsupportedParcelable[size];
            }
        };
    }

    private static class ThrowingToUriIntent extends Intent {
        private final Bundle extras;

        private ThrowingToUriIntent(Bundle extras) {
            this.extras = extras;
        }

        @Override
        public String toUri(int flags) {
            throw new BadParcelableException("unknown Parcelable class");
        }

        @Override
        public Bundle getExtras() {
            return extras;
        }
    }

    private static final class ThrowingExtrasIntent extends ThrowingToUriIntent {
        private ThrowingExtrasIntent() {
            super(null);
        }

        @Override
        public Bundle getExtras() {
            throw new BadParcelableException("unreadable extras Bundle");
        }
    }
}
