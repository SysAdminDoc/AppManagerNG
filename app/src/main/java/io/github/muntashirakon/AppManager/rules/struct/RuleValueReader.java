// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.NoSuchElementException;

/** Sequential reader for TSV value fields that preserves empty positions. */
public final class RuleValueReader {
    @NonNull
    private final String[] mValues;
    private int mPosition;

    public RuleValueReader(@NonNull String[] fields, int startIndex) {
        if (startIndex < 0 || startIndex > fields.length) {
            throw new IndexOutOfBoundsException("Invalid value start index " + startIndex);
        }
        mValues = Arrays.copyOfRange(fields, startIndex, fields.length);
    }

    public boolean hasNext() {
        return mPosition < mValues.length;
    }

    @NonNull
    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No rule value remains");
        }
        return mValues[mPosition++];
    }
}
