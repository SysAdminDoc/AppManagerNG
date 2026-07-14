// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class IntentCompatUnsupportedExtrasTest {
    @Test
    public void emptyIntentReturnsNoUnsupportedExtras() {
        assertTrue(IntentCompat.getUnsupportedExtras(new Intent()).isEmpty());
    }

    @Test
    public void supportedExtrasAreNotReported() {
        Intent intent = new Intent();
        intent.putExtra("s", "value");
        intent.putExtra("i", 42);
        intent.putExtra("b", true);
        assertTrue(IntentCompat.getUnsupportedExtras(intent).isEmpty());
    }

    @Test
    public void unreadableParcelableExtraIsReportedNotThrown() {
        Intent intent = new Intent();
        intent.putExtra("bad", new ThrowingParcelable());
        // Marshal + unmarshal so the extras bundle is Parcel-backed and unparcels lazily,
        // reproducing a foreign Parcelable whose reader throws BadParcelableException.
        Parcel parcel = Parcel.obtain();
        intent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Intent restored = Intent.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        List<IntentCompat.UnsupportedExtra> extras = IntentCompat.getUnsupportedExtras(restored);
        // Must not throw; a marker is returned instead.
        assertFalse(extras.isEmpty());
        boolean reported = false;
        for (IntentCompat.UnsupportedExtra extra : extras) {
            if ("<unreadable extras>".equals(extra.key) || "bad".equals(extra.key)) {
                reported = true;
                break;
            }
        }
        assertTrue("The unreadable extra must be reported", reported);
    }

    public static class ThrowingParcelable implements Parcelable {
        public ThrowingParcelable() {
        }

        protected ThrowingParcelable(@NonNull Parcel in) {
            throw new IllegalStateException("cannot unparcel");
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(1);
        }

        public static final Creator<ThrowingParcelable> CREATOR = new Creator<ThrowingParcelable>() {
            @Override
            public ThrowingParcelable createFromParcel(Parcel in) {
                return new ThrowingParcelable(in);
            }

            @Override
            public ThrowingParcelable[] newArray(int size) {
                return new ThrowingParcelable[size];
            }
        };
    }
}
