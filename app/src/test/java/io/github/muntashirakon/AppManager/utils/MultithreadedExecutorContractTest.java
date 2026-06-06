// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MultithreadedExecutorContractTest {
    @Test
    public void factorySynchronizesExecutorCache() throws NoSuchMethodException {
        Method factory = MultithreadedExecutor.class.getDeclaredMethod("getNewInstance");

        int modifiers = factory.getModifiers();
        assertTrue("Factory must stay static", Modifier.isStatic(modifiers));
        assertTrue("Factory must synchronize access to the executor cache", Modifier.isSynchronized(modifiers));
    }

    @Test
    public void renewedExecutorDelegateIsVisibleAcrossThreads() throws NoSuchFieldException {
        Field delegate = MultithreadedExecutor.class.getDeclaredField("mExecutor");

        assertTrue("Renewed executor delegate must be visible across threads",
                Modifier.isVolatile(delegate.getModifiers()));
    }
}
