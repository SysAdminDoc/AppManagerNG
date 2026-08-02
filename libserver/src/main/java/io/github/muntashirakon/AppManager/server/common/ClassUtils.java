// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.server.common;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.os.WorkSource;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the parameter-type names carried by a {@link Caller} back to classes.
 * <p>
 * The names travel over the privileged channel, so resolution is restricted to the explicit
 * allowlist below. An unrecognised name is refused rather than handed to {@code Class.forName},
 * which would let the wire decide which class gets loaded.
 */
// Copyright 2017 Zheng Li
public class ClassUtils {
    private static final Map<String, Class<?>> sDefaultClassMap = new HashMap<>();

    static {
        // Primitive types
        defCacheClass(byte.class);
        defCacheClass(boolean.class);
        defCacheClass(short.class);
        defCacheClass(char.class);
        defCacheClass(int.class);
        defCacheClass(float.class);
        defCacheClass(long.class);
        defCacheClass(double.class);
        // Non-primitive types
        defCacheClass(String.class);
        defCacheClass(Bundle.class);
        defCacheClass(ComponentName.class);
        defCacheClass(Message.class);
        defCacheClass(ParcelFileDescriptor.class);
        defCacheClass(ResultReceiver.class);
        defCacheClass(WorkSource.class);
        defCacheClass(Intent.class);
        defCacheClass(IntentFilter.class);
        defCacheClass(UserHandle.class);
        // Arrays
        defCacheClass(byte[].class);
        defCacheClass(int[].class);
        defCacheClass(String[].class);
        defCacheClass(Intent[].class);
    }

    private static void defCacheClass(Class<?> clazz) {
        sDefaultClassMap.put(clazz.getName(), clazz);
    }

    @Nullable
    public static Class<?>[] string2Class(String... names) {
        if (names != null) {
            Class<?>[] ret = new Class[names.length];
            for (int i = 0; i < names.length; i++) {
                ret[i] = string2Class(names[i]);
            }
            return ret;
        }
        return null;
    }

    /**
     * @return The allowlisted class for {@code name}, or {@code null} when the name is not one
     * the privileged channel is permitted to resolve.
     */
    @Nullable
    public static Class<?> string2Class(String name) {
        Class<?> clazz = sDefaultClassMap.get(name);
        if (clazz == null) {
            FLog.log("Refusing to resolve non-allowlisted class name: " + name);
        }
        return clazz;
    }
}
