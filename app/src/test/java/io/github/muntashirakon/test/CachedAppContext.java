// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test;

import java.lang.reflect.Field;

import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

/**
 * {@link ContextUtils#getContext()} keeps the first application context it sees for the life of the Robolectric
 * sandbox. A test run in another language (say {@code @Config(qualifiers = "ru")}) clears it before and after, so it
 * neither reads an English context left by an earlier test nor leaves its Russian one to the tests after it (a later
 * installer test saw "Добавлено в очередь" in its toast).
 */
public final class CachedAppContext {
    private CachedAppContext() {
    }

    public static void forget() {
        try {
            Field field = ContextUtils.class.getDeclaredField("sContext");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Also forgets the preferences {@link AppPref#set} writes to, which belong to the first application context
     * too. A test that changes a setting and reads it back through a new context (the app's language, say) needs it.
     */
    public static void forgetWithPrefs() {
        forget();
        try {
            Field field = AppPref.class.getDeclaredField("sAppPref");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
