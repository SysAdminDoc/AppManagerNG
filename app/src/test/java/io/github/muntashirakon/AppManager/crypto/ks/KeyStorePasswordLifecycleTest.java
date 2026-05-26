// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.crypto.ks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * NF-18 invariant: keystore-password flow uses {@code char[]} end-to-end so
 * {@code Utils.clearChars(char[])} can actually zero the buffer afterwards.
 *
 * <p>Earlier hot-fix work (T3, v0.3.0) moved KeyStoreUtils away from
 * {@code String} for the keystore password because {@code String} is interned
 * in the heap and cannot be reliably cleared. This test pins that contract
 * against {@link KeyStoreManager} so a drive-by refactor cannot reintroduce
 * a {@code String}-typed password parameter or field without a deliberate
 * test failure.</p>
 *
 * <p>The check is reflection-based and pure-JVM so it runs in {@code
 * tests.yml} without requiring Android or Robolectric.</p>
 */
public class KeyStorePasswordLifecycleTest {

    @Test
    public void keyStoreManagerMethodsCarryPasswordAsCharArrayNotString() {
        Class<?> cls = KeyStoreManager.class;
        for (Method method : cls.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!isPasswordRelated(name)) continue;
            Class<?>[] params = method.getParameterTypes();
            for (int i = 0; i < params.length; ++i) {
                Class<?> param = params[i];
                if (param == String.class) {
                    fail("KeyStoreManager." + method.getName()
                            + " takes String at parameter " + i
                            + "; password-carrying methods must use char[] so Utils.clearChars can zero them.");
                }
            }
        }
    }

    @Test
    public void keyStoreManagerFieldsDoNotCachePasswordsAsStrings() {
        Class<?> cls = KeyStoreManager.class;
        for (Field field : cls.getDeclaredFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("pass") && !name.contains("secret") && !name.contains("pwd")) continue;
            if (Modifier.isFinal(field.getModifiers()) && Modifier.isStatic(field.getModifiers())) {
                // SharedPreference key constants and pref-alias prefixes are allowed to be String.
                if (name.startsWith("pref_") || name.endsWith("_prefix") || name.endsWith("_alias")
                        || name.endsWith("_name") || name.endsWith("_key")) {
                    continue;
                }
            }
            assertNotEquals("Field " + field.getName()
                            + " in KeyStoreManager looks like a cached password but is typed as String. "
                            + "Use char[] instead so it can be zeroed.",
                    String.class, field.getType());
        }
    }

    @Test
    public void savePassRetrieveAroundtripIsCharArrayOnly() throws NoSuchMethodException {
        // The narrowest contract worth pinning: savePass(Context, String, char[])
        // exists and accepts char[] for the password parameter.
        Method savePass = KeyStoreManager.class.getDeclaredMethod(
                "savePass", android.content.Context.class, String.class, char[].class);
        assertEquals(char[].class, savePass.getParameterTypes()[2]);
    }

    private static boolean isPasswordRelated(String lowerCaseName) {
        return lowerCaseName.contains("pass")
                || lowerCaseName.contains("pwd")
                || lowerCaseName.contains("secret");
    }

    @Test
    public void atLeastOneMethodIsActuallyPasswordRelatedSoCoverageIsMeaningful() {
        // Guardrail: if a future refactor renames every method away from
        // 'password', the other tests would vacuously pass. Pin that the file
        // still has at least one matching method.
        int matched = 0;
        for (Method method : KeyStoreManager.class.getDeclaredMethods()) {
            if (isPasswordRelated(method.getName().toLowerCase(Locale.ROOT))) {
                matched++;
            }
        }
        assertTrue("Expected at least one password-related method in KeyStoreManager", matched >= 1);
    }
}
