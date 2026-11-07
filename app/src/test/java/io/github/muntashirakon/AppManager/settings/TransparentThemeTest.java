// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;

/**
 * Activities with a transparent background show the same layouts as every other screen, so their
 * themes have to answer every attribute those layouts read.
 */
@RunWith(RobolectricTestRunner.class)
public class TransparentThemeTest {
    private static final int NORMAL_THEME = 0;
    private static final int FULL_BLACK_THEME = 1;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @After
    public void tearDown() {
        AppPref.set(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT, NORMAL_THEME);
    }

    @Test
    public void theProfileApplyReviewOpensUnderTheNormalTransparentTheme() {
        assertReviewInflates(NORMAL_THEME);
    }

    @Test
    public void theProfileApplyReviewOpensUnderTheFullBlackTransparentTheme() {
        assertReviewInflates(FULL_BLACK_THEME);
    }

    @Test
    @Config(qualifiers = "night")
    public void theProfileApplyReviewOpensUnderTheFullBlackTransparentThemeAtNight() {
        assertReviewInflates(FULL_BLACK_THEME);
    }

    @Test
    public void theFullBlackTransparentThemeKeepsTheBlackSurfaceAndTheDarkWarning() {
        Context themed = transparentThemed(FULL_BLACK_THEME);
        assertEquals(ContextCompat.getColor(mContext, R.color.premium_surface_0_amoled),
                resolveColor(themed, com.google.android.material.R.attr.colorSurface));
        assertEquals(ContextCompat.getColor(mContext, R.color.premium_warning_content_dark),
                resolveColor(themed, R.attr.premiumWarningContent));
    }

    @Test
    public void theTransparentThemesStayTransparent() {
        for (int theme : new int[]{NORMAL_THEME, FULL_BLACK_THEME}) {
            TypedValue value = new TypedValue();
            assertTrue(transparentThemed(theme).getTheme()
                    .resolveAttribute(android.R.attr.windowIsTranslucent, value, true));
            assertTrue(value.data != 0);
        }
    }

    private void assertReviewInflates(int customTheme) {
        View view = LayoutInflater.from(transparentThemed(customTheme))
                .inflate(R.layout.view_profile_apply_review, null, false);
        assertNotNull(view.findViewById(R.id.profile_apply_review_warning));
    }

    private Context transparentThemed(int customTheme) {
        AppPref.set(AppPref.PrefKey.PREF_APP_THEME_CUSTOM_INT, customTheme);
        return new ContextThemeWrapper(mContext, Prefs.Appearance.getTransparentAppTheme());
    }

    private static int resolveColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        assertTrue(context.getTheme().resolveAttribute(attr, value, true));
        return value.data;
    }
}
