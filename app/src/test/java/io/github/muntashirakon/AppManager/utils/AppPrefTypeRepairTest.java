// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AppPrefTypeRepairTest {
    @Test
    public void initRepairsTypeMismatchedKey() {
        Context context = ApplicationProvider.getApplicationContext();
        // Poison a registered integer key with a String value, as a corrupt or maliciously crafted
        // snapshot-imported "preferences" file could. Without repair the strongly-typed getter
        // throws ClassCastException on every read and bricks the affected settings screen.
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
                .edit().putString("app_theme", "not-an-int").commit();

        // Constructing AppPref runs init(), which must detect and reset the mismatched key.
        AppPref appPref = AppPref.getNewInstance(context);
        Object value = appPref.get("app_theme"); // would throw ClassCastException if not repaired
        assertTrue("Repaired key must read back as an Integer", value instanceof Integer);
    }
}
