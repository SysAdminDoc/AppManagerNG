// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

// Copyright 2017 Zheng Li
public class ParcelableUtil {
    @NonNull
    public static byte[] marshall(@NonNull Parcelable parcelable) {
        Parcel parcel = Parcel.obtain();
        try {
            parcelable.writeToParcel(parcel, 0);
            return parcel.marshall();
        } finally {
            parcel.recycle();
        }
    }

    @Contract("!null,_ -> !null")
    @Nullable
    public static <T extends Parcelable> T unmarshall(@Nullable byte[] bytes, @NonNull Parcelable.Creator<T> creator) {
        if (bytes == null) {
            return null;
        }
        Parcel parcel = unmarshall(bytes);
        return creator.createFromParcel(parcel);
    }

    @Contract("!null -> !null")
    @Nullable
    public static Parcel unmarshall(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0);
        return parcel;
    }

    // Deliberately no readValue(byte[]) helper: Parcel.readValue resolves the type from the
    // bytes themselves, so peer-controlled input would choose which class is instantiated.
    // Every reply on the privileged channel is read through the concrete type's own creator.
}