// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MultithreadedExecutorContractTest {
    @Test
    public void factoryIsStaticWithNoSharedCache() throws NoSuchMethodException {
        Method factory = MultithreadedExecutor.class.getDeclaredMethod("getNewInstance");
        assertTrue("Factory must stay static", Modifier.isStatic(factory.getModifiers()));

        // The cache-and-renew scheme was removed: getNewInstance() now hands out a fresh
        // executor each call, so a finished operation can never have its delegate swapped
        // out from under it. Guard against the static instance cache being reintroduced.
        for (Field field : MultithreadedExecutor.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                assertFalse("No static executor cache may be reintroduced (caused a renew race): "
                                + field.getName(),
                        MultithreadedExecutor.class.isAssignableFrom(field.getType())
                                || java.util.Collection.class.isAssignableFrom(field.getType()));
            }
        }
    }

    @Test
    public void executorDelegateIsFinal() throws NoSuchFieldException {
        Field delegate = MultithreadedExecutor.class.getDeclaredField("mExecutor");

        // A final delegate cannot be swapped, which removes the in-place-renew race; final
        // fields also publish safely without needing volatile.
        assertTrue("Executor delegate must be final to prevent in-place swap races",
                Modifier.isFinal(delegate.getModifiers()));
        assertFalse("A final delegate does not need to be volatile",
                Modifier.isVolatile(delegate.getModifiers()));
    }
}
