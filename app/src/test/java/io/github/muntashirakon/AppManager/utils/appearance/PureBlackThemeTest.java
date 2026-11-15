// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.Prefs;

/**
 * Pure black is a night palette. In the day the normal light theme is used, so text and icons never end up in day
 * colors on a black surface.
 */
@RunWith(RobolectricTestRunner.class)
public class PureBlackThemeTest {
    private static final int BLACK = 0xFF000000;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private int mNightMode;
    private boolean mPureBlack;

    @Before
    public void setUp() {
        mNightMode = Prefs.Appearance.getNightMode();
        mPureBlack = Prefs.Appearance.isPureBlackTheme();
    }

    @After
    public void tearDown() {
        Prefs.Appearance.setNightMode(mNightMode);
        Prefs.Appearance.setPureBlackTheme(mPureBlack);
        setPowerSaveMode(false);
    }

    @Test
    public void nightThenPureBlackThenDayUsesTheNormalLightTheme() {
        Prefs.Appearance.setNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        Prefs.Appearance.setPureBlackTheme(true);
        Prefs.Appearance.setNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        assertFalse(AppearanceUtils.isPureBlackInEffect(mContext));
        assertEquals(R.style.AppTheme_V2, Prefs.Appearance.getAppTheme());
        assertEquals(R.style.AppTheme_TransparentBackground, Prefs.Appearance.getTransparentAppTheme());
        assertNotEquals(BLACK, resolveColor(screen(false), android.R.attr.colorBackground));
        // Kept for the next time the app is dark
        assertTrue(Prefs.Appearance.isPureBlackTheme());
    }

    @Test
    public void nightWithPureBlackUsesTrueBlack() {
        Prefs.Appearance.setNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        Prefs.Appearance.setPureBlackTheme(true);

        assertTrue(AppearanceUtils.isPureBlackInEffect(mContext));
        assertEquals(R.style.AppTheme_V2_Amoled, Prefs.Appearance.getAppTheme());
        assertEquals(R.style.AppTheme_TransparentBackground_Black, Prefs.Appearance.getTransparentAppTheme());
        assertEquals(BLACK, resolveColor(screen(false), android.R.attr.colorBackground));
        assertEquals(BLACK, resolveColor(screen(true), android.R.attr.colorBackground));
    }

    @Test
    public void followSystemFollowsTheSystemsNightSetting() {
        Prefs.Appearance.setPureBlackTheme(true);
        Prefs.Appearance.setNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        RuntimeEnvironment.setQualifiers("+night");
        assertTrue(AppearanceUtils.isPureBlackInEffect(mContext));
        RuntimeEnvironment.setQualifiers("+notnight");
        assertFalse(AppearanceUtils.isPureBlackInEffect(mContext));
    }

    @Test
    public void batterySaverModeFollowsBatterySaver() {
        Prefs.Appearance.setPureBlackTheme(true);
        Prefs.Appearance.setNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);

        setPowerSaveMode(true);
        assertTrue(AppearanceUtils.isPureBlackInEffect(mContext));
        setPowerSaveMode(false);
        assertFalse(AppearanceUtils.isPureBlackInEffect(mContext));
    }

    @Test
    public void pureBlackOffNeverApplies() {
        Prefs.Appearance.setPureBlackTheme(false);
        Prefs.Appearance.setNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        assertFalse(AppearanceUtils.isPureBlackInEffect(mContext));
        assertEquals(R.style.AppTheme_V2, Prefs.Appearance.getAppTheme());
    }

    @Test
    public void textIconsAndControlsStayReadableInEveryMode() {
        List<String> failures = new ArrayList<>();
        for (boolean pureBlack : new boolean[]{false, true}) {
            for (int nightMode : new int[]{AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES}) {
                for (boolean dynamic : new boolean[]{false, true}) {
                    Prefs.Appearance.setPureBlackTheme(pureBlack);
                    Prefs.Appearance.setNightMode(nightMode);
                    String mode = String.format(Locale.ROOT, "pureBlack=%s night=%s dynamic=%s", pureBlack,
                            nightMode == AppCompatDelegate.MODE_NIGHT_YES, dynamic);
                    Context screen = screen(dynamic);
                    int background = resolveColor(screen, android.R.attr.colorBackground);
                    int surface = resolveColor(screen, com.google.android.material.R.attr.colorSurface);
                    // Cards, sheets and filled fields sit on the containers
                    int container = resolveColor(screen,
                            com.google.android.material.R.attr.colorSurfaceContainer);
                    int highest = resolveColor(screen,
                            com.google.android.material.R.attr.colorSurfaceContainerHighest);
                    for (int ground : new int[]{background, surface, container, highest}) {
                        check(failures, mode, "preference title", textColor(screen,
                                android.R.attr.textAppearanceListItem), ground, 4.5);
                        check(failures, mode, "preference summary", textColor(screen,
                                android.R.attr.textAppearanceListItemSecondary), ground, 4.5);
                        check(failures, mode, "colorOnSurface", resolveColor(screen,
                                com.google.android.material.R.attr.colorOnSurface), ground, 4.5);
                        check(failures, mode, "colorOnSurfaceVariant", resolveColor(screen,
                                com.google.android.material.R.attr.colorOnSurfaceVariant), ground, 4.5);
                        check(failures, mode, "usage chart labels", chartLabelColor(screen), ground, 4.5);
                        check(failures, mode, "colorControlNormal", resolveColor(screen,
                                androidx.appcompat.R.attr.colorControlNormal), ground, 3);
                        check(failures, mode, "colorPrimary", resolveColor(screen,
                                androidx.appcompat.R.attr.colorPrimary), ground, 3);
                        check(failures, mode, "colorOutline", resolveColor(screen,
                                com.google.android.material.R.attr.colorOutline), ground, 3);
                    }
                }
            }
        }
        assertTrue(String.join("\n", failures), failures.isEmpty());
    }

    /** A screen as an activity gets it: the app theme, dynamic colors if on, then the black surfaces if in effect. */
    @NonNull
    private Context screen(boolean dynamic) {
        boolean night = AppearanceUtils.isNightInEffect(mContext);
        Configuration config = new Configuration(mContext.getResources().getConfiguration());
        config.uiMode = (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO)
                | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        ContextThemeWrapper screen = new ContextThemeWrapper(mContext.createConfigurationContext(config),
                Prefs.Appearance.getAppTheme());
        if (dynamic) {
            screen.getTheme().applyStyle(
                    com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_DayNight, true);
        }
        if (AppearanceUtils.isPureBlackInEffect(mContext)) {
            screen.getTheme().applyStyle(R.style.ThemeOverlay_AppTheme_V2_AmoledSurfaces, true);
        }
        return screen;
    }

    private void setPowerSaveMode(boolean enabled) {
        shadowOf((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).setIsPowerSaveMode(enabled);
    }

    private static void check(@NonNull List<String> failures, @NonNull String mode, @NonNull String what,
                              int foreground, int ground, double minimum) {
        double ratio = contrast(over(foreground, ground), ground);
        if (ratio < minimum) {
            failures.add(String.format(Locale.ROOT, "%s: %s #%08X on #%08X is %.2f:1, needs %.1f:1", mode, what,
                    foreground, ground, ratio, minimum));
        }
    }

    /** The color the usage chart draws its axis labels in, read the way BarChartView reads it. */
    private static int chartLabelColor(@NonNull Context context) {
        TypedArray a = context.obtainStyledAttributes(null, R.styleable.BarChartView, R.attr.barChartViewStyle,
                R.style.Widget_AppTheme_BarChartView);
        try {
            return a.getColor(R.styleable.BarChartView_textColor, 0);
        } finally {
            a.recycle();
        }
    }

    private static int textColor(@NonNull Context context, int textAppearanceAttr) {
        TypedValue value = new TypedValue();
        assertTrue(context.getTheme().resolveAttribute(textAppearanceAttr, value, true));
        TypedArray array = context.obtainStyledAttributes(value.resourceId, new int[]{android.R.attr.textColor});
        try {
            ColorStateList colors = array.getColorStateList(0);
            assertTrue("No text color in " + context.getResources().getResourceName(value.resourceId),
                    colors != null);
            return colors.getDefaultColor();
        } finally {
            array.recycle();
        }
    }

    private static int resolveColor(@NonNull Context context, int attr) {
        TypedArray array = context.obtainStyledAttributes(new int[]{attr});
        try {
            ColorStateList colors = array.getColorStateList(0);
            assertTrue("Attribute not set: " + context.getResources().getResourceName(attr), colors != null);
            return colors.getDefaultColor();
        } finally {
            array.recycle();
        }
    }

    /** The color seen when a translucent foreground sits on an opaque ground. */
    private static int over(int foreground, int ground) {
        float alpha = Color.alpha(foreground) / 255f;
        return Color.rgb(
                Math.round(Color.red(foreground) * alpha + Color.red(ground) * (1 - alpha)),
                Math.round(Color.green(foreground) * alpha + Color.green(ground) * (1 - alpha)),
                Math.round(Color.blue(foreground) * alpha + Color.blue(ground) * (1 - alpha)));
    }

    private static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color))
                + 0.0722 * channel(Color.blue(color));
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
