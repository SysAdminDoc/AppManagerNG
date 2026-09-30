// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.Objects;

public final class LiveDataChanges {
    private LiveDataChanges() {
    }

    /**
     * Runs {@code onChange} whenever the value moves away from the one it held when this was
     * called. LiveData hands every new observer the current value, which a screen that just drew
     * itself from that same state doesn't need.
     */
    @MainThread
    public static <T> void observe(@NonNull LiveData<T> data, @NonNull LifecycleOwner owner,
                                   @NonNull Runnable onChange) {
        data.observe(owner, new Observer<T>() {
            private T mLast = data.getValue();

            @Override
            public void onChanged(T value) {
                if (!Objects.equals(mLast, value)) {
                    mLast = value;
                    onChange.run();
                }
            }
        });
    }
}
